package com.cobblespawners

import com.everlastingutils.utils.logDebug
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblespawners.mixin.ServerWorldAccessor
import com.cobblespawners.api.SpawnerNBTManager
import com.cobblespawners.mixin.MobEntityAccessor
import com.cobblespawners.utils.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.World
import net.minecraft.world.chunk.ChunkStatus
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min


object CobbleSpawners : ModInitializer {

	private val logger = LoggerFactory.getLogger("cobblespawners")
	val random = Random.create()
	private val battleTracker = BattleTracker()
	private val catchingTracker = CatchingTracker()

	// Cache of valid positions for each spawner, keyed by spawner's BlockPos.
	val spawnerValidPositions = ConcurrentHashMap<BlockPos, Map<String, List<BlockPos>>>()

	// Added: Store scheduler reference to shut it down on server stop.
	private var spawnScheduler: ScheduledExecutorService? = null

	override fun onInitialize() {
		logger.info("Initializing CobbleSpawners")

		// Load configuration and spawner data
		CobbleSpawnersConfig.initializeAndLoad()

		// Register commands and event listeners
		CommandRegistrar.registerCommands()
		battleTracker.registerEvents()
		catchingTracker.registerEvents()
		SpawnerBlockEvents.registerEvents()

		registerServerLifecycleEvents()

		// Instead of using ServerTickEvents, schedule a recurring task using system time.
		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			registerSpawnScheduler(server)
			battleTracker.startCleanupScheduler(server)
		}

		ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
			if (entity is PokemonEntity) {
				val spawnerInfo = SpawnerNBTManager.getPokemonInfo(entity)
				if (spawnerInfo != null) {
					// Use getSpawner which returns nullable SpawnerData
					val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerInfo.spawnerPos)
					// Safely access wanderingSettings using ?. let
					spawnerData?.wanderingSettings?.let { wanderingSettings ->
						if (wanderingSettings.enabled) { // Check if enabled
							val goalSelector = (entity as MobEntityAccessor).getGoalSelector()
							val alreadyHasWander = goalSelector.goals.any { it.goal is WanderBackToSpawnerGoal }
							if (!alreadyHasWander) {
								val wanderGoal = WanderBackToSpawnerGoal(
									entity,
									Vec3d.ofCenter(spawnerInfo.spawnerPos),
									1.0, // Speed
									wanderingSettings // Pass the whole settings object
									// tickDelay defaults to 10
								)
								goalSelector.add(0, wanderGoal) // Priority 0 is high
							}
						}
					}
				}
			}
		}


	}

	private fun registerServerLifecycleEvents() {
		ServerLifecycleEvents.SERVER_STOPPING.register { server ->
			if (CobbleSpawnersConfig.config.globalConfig.cullSpawnerPokemonOnServerStop) {
				CobbleSpawnersConfig.spawners.forEach { (pos, data) ->
					val worldKey = parseDimension(data.dimension)
					server.getWorld(worldKey)?.let { cullSpawnerPokemon(it, pos) }
				}
			}
			// Added: Shut down the scheduler to ensure it doesn't linger.
			spawnScheduler?.shutdownNow()
			spawnScheduler = null
		}
	}

	private fun cullSpawnerPokemon(world: ServerWorld, spawnerPos: BlockPos) {
		SpawnerNBTManager.getUUIDsForSpawner(world, spawnerPos).forEach { uuid ->
			val entity = world.getEntity(uuid)
			if (entity is PokemonEntity) {
				entity.discard()
				logDebug("Despawned Pokémon with UUID $uuid from spawner at $spawnerPos", "cobblespawners")
			}
		}
	}

	/**
	 * Instead of using ServerTickEvents, this schedules a recurring task based on system time.
	 * The spawn delay is computed by converting the configured spawnTimerTicks (ticks) to milliseconds.
	 */
	private fun registerSpawnScheduler(server: MinecraftServer) {
		spawnScheduler = Executors.newSingleThreadScheduledExecutor()
		spawnScheduler?.scheduleAtFixedRate({
			// Ensure we run spawn logic on the main server thread.
			server.executeSync {
				processSpawnerSpawns(server)
			}
		}, 0, 1000, TimeUnit.MILLISECONDS)
	}

	private fun processSpawnerSpawns(server: MinecraftServer) {
		val currentTime = System.currentTimeMillis()
		logDebug("processSpawnerSpawns: currentTime = $currentTime", "cobblespawners")

		for ((pos, data) in CobbleSpawnersConfig.spawners) {
			val dimensionKey = parseDimension(data.dimension)
			val world = server.getWorld(dimensionKey)
			if (world == null) {
				logDebug("processSpawnerSpawns: world is null for spawner at $pos with dimension ${data.dimension}", "cobblespawners")
				continue
			}
			if (world.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false) == null) {
				logDebug("processSpawnerSpawns: chunk not loaded for spawner '${data.spawnerName}' at $pos", "cobblespawners")
				continue
			}

			val spawnDelayMillis = data.spawnTimerTicks * 50
			var lastSpawnTime = CobbleSpawnersConfig.lastSpawnTicks[pos]
			if (lastSpawnTime == null) {
				// Initialize lastSpawnTime so that the spawner waits for the delay from now on.
				lastSpawnTime = currentTime
				CobbleSpawnersConfig.lastSpawnTicks[pos] = lastSpawnTime
				logDebug("processSpawnerSpawns: initializing lastSpawnTime for spawner at $pos to $currentTime", "cobblespawners")
				continue
			}

			logDebug("processSpawnerSpawns: spawner at $pos, lastSpawnTime = $lastSpawnTime, spawnDelayMillis = $spawnDelayMillis", "cobblespawners")

			// Check if the spawn delay has elapsed
			if (currentTime < lastSpawnTime + spawnDelayMillis) {
				logDebug("processSpawnerSpawns: spawn delay not elapsed for spawner at $pos", "cobblespawners")
				continue
			}
			// Check if the spawner GUI is closed
			if (SpawnerPokemonSelectionGui.isSpawnerGuiOpen(pos)) {
				logDebug("processSpawnerSpawns: spawner GUI is open for spawner at $pos", "cobblespawners")
				continue
			}

			val currentCount = SpawnerNBTManager.getPokemonCountForSpawner(world, pos)
			logDebug("processSpawnerSpawns: current Pokemon count = $currentCount for spawner at $pos (limit: ${data.spawnLimit})", "cobblespawners")
			if (currentCount < data.spawnLimit) {
				logDebug("processSpawnerSpawns: Spawning Pokémon at spawner '${data.spawnerName}' at $pos", "cobblespawners")
				spawnPokemon(world, data)
				// Update the last spawn time only when a spawn occurs.
				CobbleSpawnersConfig.lastSpawnTicks[pos] = currentTime
			} else {
				logDebug("processSpawnerSpawns: Spawn limit reached for spawner '${data.spawnerName}' at $pos", "cobblespawners")
			}
		}
	}

	private fun spawnPokemon(serverWorld: ServerWorld, spawnerData: SpawnerData) {
		val spawnerPos = spawnerData.spawnerPos
		val currentSpawned = SpawnerNBTManager.getPokemonCountForSpawner(serverWorld, spawnerPos)

		if (currentSpawned >= spawnerData.spawnLimit) {
			logDebug("Safety check: Spawn limit reached at $spawnerPos", "cobblespawners")
			return
		}
		if (SpawnerPokemonSelectionGui.isSpawnerGuiOpen(spawnerPos)) {
			logDebug("GUI is open for spawner at $spawnerPos. Skipping spawn.", "cobblespawners")
			return
		}

		// Added check: ensure the spawner's own chunk is still loaded.
		if (serverWorld.getChunk(spawnerPos.x shr 4, spawnerPos.z shr 4, ChunkStatus.FULL, false) == null) {
			logDebug("spawnPokemon: spawner chunk not loaded for spawner at $spawnerPos, aborting spawn.", "cobblespawners")
			return
		}

		val cachedPositions = spawnerValidPositions[spawnerPos]
		if (cachedPositions.isNullOrEmpty()) {
			val computed = computeValidSpawnPositions(serverWorld, spawnerData)
			if (computed.isNotEmpty()) {
				spawnerValidPositions[spawnerPos] = computed
			} else {
				logDebug("No valid spawn positions found for $spawnerPos", "cobblespawners")
				return
			}
		}

		val allPositions = spawnerValidPositions[spawnerPos] ?: emptyMap()
		val eligible = spawnerData.selectedPokemon.filter { checkBasicSpawnConditions(serverWorld, it) == null }
		if (eligible.isEmpty()) {
			logDebug("No eligible Pokémon for spawner '${spawnerData.spawnerName}' at $spawnerPos", "cobblespawners")
			return
		}

		val totalWeight = eligible.sumOf { it.spawnChance }
		if (totalWeight <= 0) {
			logger.warn("Total spawn chance <= 0 at $spawnerPos")
			return
		}

		val maxSpawnable = spawnerData.spawnLimit - currentSpawned
		if (maxSpawnable <= 0) {
			logDebug("Spawn limit reached for '${spawnerData.spawnerName}'", "cobblespawners")
			return
		}
		val spawnAmount = min(spawnerData.spawnAmountPerSpawn, maxSpawnable)
		var spawnedCount = 0

		repeat(spawnAmount) {
			val picked = selectPokemonByWeight(eligible, totalWeight) ?: return@repeat
			val locationType = picked.spawnSettings.spawnLocation.uppercase()
			val validPositionsForType = when (locationType) {
				"SURFACE", "UNDERGROUND", "WATER" -> allPositions[locationType]
				"ALL" -> allPositions.values.flatten()
				else -> allPositions.values.flatten()
			} ?: return@repeat

			if (validPositionsForType.isEmpty()) return@repeat

			// Filter the list to only include positions whose chunks are loaded.
			val loadedPositions = validPositionsForType.filter { pos ->
				serverWorld.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false) != null
			}

			if (loadedPositions.isEmpty()) {
				logDebug("No valid loaded spawn positions available for spawner at $spawnerPos, skipping this spawn attempt.", "cobblespawners")
				return@repeat
			}

			// Pick a random loaded spawn position.
			val spawnPos = loadedPositions[random.nextInt(loadedPositions.size)]



			// Forcefully spawn the Pokémon without regard to claim protections.
			if (attemptSpawnSinglePokemon(serverWorld, spawnPos, picked, spawnerPos, spawnerData.lowLevelEntitySpawn)) {
				spawnedCount++
			}

		}

		if (spawnedCount > 0) {
			logDebug("Spawned $spawnedCount Pokémon(s) for '${spawnerData.spawnerName}' at $spawnerPos", "cobblespawners")
		}
	}

	fun getWorldUUID(serverWorld: ServerWorld): UUID {
		// Create a UUID based on the world’s registry key, which uniquely identifies the world
		return UUID.nameUUIDFromBytes(serverWorld.registryKey.value.toString().toByteArray())
	}

	private fun attemptSpawnSinglePokemon(
		serverWorld: ServerWorld,
		spawnPos: BlockPos,
		entry: PokemonSpawnEntry,
		spawnerPos: BlockPos,
		lowLevel: Boolean
	): Boolean {
		val sanitized = entry.pokemonName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
		val species = PokemonSpecies.getByName(sanitized) ?: run {
			logger.warn("Species '$sanitized' not found at $spawnerPos")
			return false
		}
		val level = entry.minLevel + random.nextInt(entry.maxLevel - entry.minLevel + 1)
		val isShiny = entry.aspects.any { it.equals("shiny", ignoreCase = true) }
		val propertiesString = buildPropertiesString(sanitized, level, isShiny, entry)
		val properties = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(propertiesString)
		val pokemonEntity = properties.createEntity(serverWorld)
		val pokemon = pokemonEntity.pokemon

		// Safely handle moves being null
		val moves = entry.moves ?: MovesSettings()

		if (moves.allowCustomInitialMoves) {
			// Clear the default moveset
			pokemon.moveSet.clear()

			// First collect any forced moves
			val forcedMoves = moves.selectedMoves
				.filter { it.forced }
				.map { it.moveId }

			// Calculate remaining move slots after forced moves
			val remainingSlots = 4 - forcedMoves.size
			val selectedMoves = forcedMoves.toMutableList()

			// Only add non-forced moves if we have slots left
			if (remainingSlots > 0) {
				// Get eligible non-forced moves (level <= Pokemon's level)
				val eligibleMoves = moves.selectedMoves
					.filter { !it.forced && it.level <= level }

				// Group moves by level
				val movesByLevel = eligibleMoves.groupBy { it.level }
					.toSortedMap(compareByDescending { it }) // Sort by level descending

				// Process each level group to fill remaining slots
				for ((_, movesAtLevel) in movesByLevel) {
					if (selectedMoves.size >= 4) break

					// Add moves for this level in original order
					val movesToAdd = minOf(4 - selectedMoves.size, movesAtLevel.size)

					// For the lowest level (level 1), we want to prioritize the later moves in the list
					// For other levels, we keep the original order
					val selectedMovesForLevel = if (movesAtLevel.firstOrNull()?.level == 1) {
						// Take the last movesToAdd entries
						movesAtLevel.takeLast(movesToAdd)
					} else {
						// Take the first movesToAdd entries
						movesAtLevel.take(movesToAdd)
					}

					selectedMoves.addAll(selectedMovesForLevel.map { it.moveId })
				}
			}

			// If we have more than 4 moves, trim to the first 4
			// Forced moves stay at the beginning
			if (selectedMoves.size > 4) {
				selectedMoves.subList(4, selectedMoves.size).clear()
			}

			// Apply the selected moves
			for (i in selectedMoves.indices) {
				val moveName = selectedMoves[i].lowercase()
				val moveTemplate = com.cobblemon.mod.common.api.moves.Moves.getByName(moveName)
				if (moveTemplate != null) {
					pokemon.moveSet.setMove(i, moveTemplate.create())
				} else {
					logger.warn("Invalid move '$moveName' for Pokémon '${entry.pokemonName}'")
				}
			}

			// Debug log
			logDebug("Level: $level, Selected moves: $selectedMoves", "cobblespawners")
		}

		pokemon.level = level
		pokemon.shiny = isShiny
		applyCustomIVs(pokemon, entry)
		applyCustomSize(pokemon, entry)
		applyHeldItems(pokemon, entry)

		pokemonEntity.refreshPositionAndAngles(
			spawnPos.x + 0.5,
			spawnPos.y.toDouble(),
			spawnPos.z + 0.5,
			pokemonEntity.yaw,
			pokemonEntity.pitch
		)

		// --- New: Initialize the wander goal (Experimental) ---
		val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
		if (spawnerData != null) {
			val wanderingSettings = spawnerData.wanderingSettings
			// Check the specific spawner's wandering setting before adding the goal
			if (wanderingSettings != null) {
				if (wanderingSettings.enabled) { // Check if enabled before adding
					val spawnerCenter = Vec3d.ofCenter(spawnerPos)
					val speed = 1.0 // Or configure speed if needed
					val wanderGoal = WanderBackToSpawnerGoal(
						pokemonEntity,
						spawnerCenter,
						speed,
						wanderingSettings // Pass the whole settings object
						// tickDelay defaults to 10
					)
					// Check if goal already exists (less likely here, but safe)
					val goalSelector = (pokemonEntity as MobEntityAccessor).getGoalSelector()
					if (goalSelector.goals.none { it.goal is WanderBackToSpawnerGoal }) {
						goalSelector.add(0, wanderGoal) // Priority 0 is high
					}
				}
			}
		} else {
			logger.warn("Spawner data not found for spawner at $spawnerPos, skipping wander goal initialization.")
		}



		val success = if (lowLevel) {
			try {
				(serverWorld as com.cobblespawners.mixin.ServerWorldAccessor).invokeAddFreshEntity(pokemonEntity)
			} catch (e: Exception) {
				logger.warn("Error using forced spawn method, falling back to spawnEntity", e)
				serverWorld.spawnEntity(pokemonEntity)
			}
		} else {
			serverWorld.spawnEntity(pokemonEntity)
		}

		return if (success) {
			SpawnerNBTManager.addPokemon(pokemonEntity, spawnerPos, entry.pokemonName)
			logDebug("Spawned '${pokemon.species.name}' @ $spawnPos (UUID ${pokemonEntity.uuid})", "cobblespawners")
			true
		} else {
			logger.warn("Failed to spawn '${pokemon.species.name}' at $spawnPos")
			false
		}
	}


	private fun buildPropertiesString(
		sanitizedName: String,
		level: Int,
		isShiny: Boolean, // no longer used; aspects from the config are used instead
		entry: PokemonSpawnEntry
	): String {
		val builder = StringBuilder(sanitizedName).append(" level=$level")

		// Append each aspect from the config using the aspect=value format
		entry.aspects.forEach { aspect ->
			// Check if the aspect already has a special format (contains "=")
			if (aspect.contains("=")) {
				builder.append(" ${aspect.lowercase()}")
			} else {
				// Use the aspect=value format instead of value=true
				builder.append(" aspect=${aspect.lowercase()}")
			}
		}

		val formName = entry.formName
		if (!formName.isNullOrEmpty()
			&& !formName.equals("normal", ignoreCase = true)
			&& !formName.equals("default", ignoreCase = true)
		) {
			val normalizedFormName = formName.lowercase().replace(Regex("[^a-z0-9]"), "")
			val species = PokemonSpecies.getByName(sanitizedName)
			if (species != null) {
				val matchedForm = species.forms.find { form ->
					form.formOnlyShowdownId().lowercase().replace(Regex("[^a-z0-9]"), "") == normalizedFormName
				}
				if (matchedForm != null) {
					if (matchedForm.aspects.isNotEmpty()) {
						matchedForm.aspects.forEach { aspect ->
							// Apply form aspects using the aspect=value format
							builder.append(" aspect=${aspect.lowercase()}")
						}
					} else {
						builder.append(" form=${matchedForm.formOnlyShowdownId()}")
					}
				} else {
					logger.warn("Form '$formName' not found for species '${species.name}'. Defaulting to normal form.")
				}
			} else {
				logger.warn("Species '$sanitizedName' not found while resolving form '$formName'.")
			}
		}
		return builder.toString()
	}



	private fun applyCustomIVs(pokemon: Pokemon, entry: PokemonSpawnEntry) {
		if (entry.ivSettings.allowCustomIvs) {
			val ivs = entry.ivSettings
			pokemon.setIV(Stats.HP, random.nextBetween(ivs.minIVHp, ivs.maxIVHp))
			pokemon.setIV(Stats.ATTACK, random.nextBetween(ivs.minIVAttack, ivs.maxIVAttack))
			pokemon.setIV(Stats.DEFENCE, random.nextBetween(ivs.minIVDefense, ivs.maxIVDefense))
			pokemon.setIV(Stats.SPECIAL_ATTACK, random.nextBetween(ivs.minIVSpecialAttack, ivs.maxIVSpecialAttack))
			pokemon.setIV(Stats.SPECIAL_DEFENCE, random.nextBetween(ivs.minIVSpecialDefense, ivs.maxIVSpecialDefense))
			pokemon.setIV(Stats.SPEED, random.nextBetween(ivs.minIVSpeed, ivs.maxIVSpeed))
			logDebug("Custom IVs for '${pokemon.species.name}': ${pokemon.ivs}", "cobblespawners")
		}
	}

	private fun applyCustomSize(pokemon: Pokemon, entry: PokemonSpawnEntry) {
		if (entry.sizeSettings.allowCustomSize) {
			val sizeSettings = entry.sizeSettings
			val randomSize = random.nextFloat() * (sizeSettings.maxSize - sizeSettings.minSize) + sizeSettings.minSize
			pokemon.scaleModifier = randomSize
		}
	}

	private fun applyHeldItems(pokemon: Pokemon, entry: PokemonSpawnEntry) {
		val heldItemsSettings = entry.heldItemsOnSpawn
		if (heldItemsSettings.allowHeldItemsOnSpawn) {
			heldItemsSettings.itemsWithChance.forEach { (itemName, chance) ->
				val itemId = Identifier.tryParse(itemName)
				if (itemId != null) {
					val item = net.minecraft.registry.Registries.ITEM.get(itemId)
					if (item != Items.AIR && random.nextDouble() * 100 <= chance) {
						pokemon.swapHeldItem(net.minecraft.item.ItemStack(item))
						logDebug("Assigned '$itemName' @ $chance% to '${pokemon.species.name}'", "cobblespawners")
						return@forEach
					}
				}
			}
		}
	}

	private fun selectPokemonByWeight(eligible: List<PokemonSpawnEntry>, totalWeight: Double): PokemonSpawnEntry? {
		// Wrap the net.minecraft random instance in a kotlin.random.Random adapter.
		val kotlinRandom = object : kotlin.random.Random() {
			override fun nextBits(bitCount: Int): Int = random.nextInt() and ((1 shl bitCount) - 1)
			override fun nextDouble(): Double = random.nextDouble()
		}

		// Partition eligible Pokémon into independent and competitive groups.
		val independentEntries = eligible.filter { it.spawnChanceType == SpawnChanceType.INDEPENDENT }
		val competitiveEntries = eligible.filter { it.spawnChanceType == SpawnChanceType.COMPETITIVE }

		// For independent entries, each spawns with an absolute chance.
		val independentSuccesses = independentEntries.filter { entry ->
			random.nextDouble() * 100 <= entry.spawnChance
		}
		if (independentSuccesses.isNotEmpty()) {
			// Randomly pick one from the successful independent entries.
			return independentSuccesses.random(kotlinRandom)
		}

		// Otherwise, use weighted selection for competitive entries.
		val competitiveTotalWeight = competitiveEntries.sumOf { it.spawnChance }
		if (competitiveTotalWeight <= 0) return null
		val randValue = kotlinRandom.nextDouble() * competitiveTotalWeight
		var cumulative = 0.0
		for (entry in competitiveEntries) {
			cumulative += entry.spawnChance
			if (randValue <= cumulative) return entry
		}
		return null
	}


	private fun checkBasicSpawnConditions(world: ServerWorld, entry: PokemonSpawnEntry): String? {
		val timeOfDay = world.timeOfDay % 24000
		when (entry.spawnSettings.spawnTime.uppercase()) {
			"DAY" -> if (timeOfDay !in 0..12000) return "Not daytime"
			"NIGHT" -> if (timeOfDay in 0..12000) return "Not nighttime"
			"ALL" -> {}
			else -> logger.warn("Invalid spawn time '${entry.spawnSettings.spawnTime}' for ${entry.pokemonName}")
		}
		when (entry.spawnSettings.spawnWeather.uppercase()) {
			"CLEAR" -> if (world.isRaining) return "Not clear weather"
			"RAIN" -> if (!world.isRaining || world.isThundering) return "Not raining"
			"THUNDER" -> if (!world.isThundering) return "Not thundering"
			"ALL" -> {}
			else -> logger.warn("Invalid weather '${entry.spawnSettings.spawnWeather}' for ${entry.pokemonName}")
		}
		return null
	}

	fun computeValidSpawnPositions(world: ServerWorld, data: SpawnerData): Map<String, List<BlockPos>> {
		val spawnerPos = data.spawnerPos
		val spawnRadius = data.spawnRadius ?: SpawnRadius() // Use default if null
		val map = mutableMapOf<String, MutableList<BlockPos>>()
		SpawnLocationType.values().forEach { map[it.name] = mutableListOf() }

		val chunkX = spawnerPos.x shr 4
		val chunkZ = spawnerPos.z shr 4
		if (world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) return emptyMap()

		val minX = spawnerPos.x - spawnRadius.width
		val maxX = spawnerPos.x + spawnRadius.width
		val minY = spawnerPos.y - spawnRadius.height
		val maxY = spawnerPos.y + spawnRadius.height
		val minZ = spawnerPos.z - spawnRadius.width
		val maxZ = spawnerPos.z + spawnRadius.width

		for (x in minX..maxX) {
			for (y in minY..maxY) {
				for (z in minZ..maxZ) {
					val pos = BlockPos(x, y, z)
					if (world.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false) == null) continue

					if (isPositionSafeForSpawn(world, pos)) {
						val location = determineSpawnLocationType(world, pos)
						map[location]?.add(pos)
					}
				}
			}
		}
		map.entries.removeIf { it.value.isEmpty() }
		return map
	}

	private fun determineSpawnLocationType(world: ServerWorld, pos: BlockPos): String {
		return when {
			checkWater(world, pos) -> SpawnLocationType.WATER.name
			checkSkyAccess(world, pos) -> SpawnLocationType.SURFACE.name
			else -> {
				if (!checkWater(world, pos)) SpawnLocationType.UNDERGROUND.name
				else SpawnLocationType.ALL.name
			}
		}
	}

	private fun checkWater(world: ServerWorld, pos: BlockPos) =
		world.getBlockState(pos).block == Blocks.WATER

	private fun checkSkyAccess(world: ServerWorld, pos: BlockPos): Boolean {
		var current = pos.up()
		while (current.y < world.topY) {
			if (!world.getBlockState(current).getCollisionShape(world, current).isEmpty) {
				return false
			}
			current = current.up()
		}
		return true
	}

	private fun isPositionSafeForSpawn(world: World, pos: BlockPos): Boolean {
		val below = pos.down()
		val belowState = world.getBlockState(below)
		val shape = belowState.getCollisionShape(world, below)
		if (shape.isEmpty) return false

		val box = shape.boundingBox
		val solidEnough = box.maxY >= 0.9
		val blockBelow = belowState.block

		if (
			!belowState.isSideSolidFullSquare(world, below, net.minecraft.util.math.Direction.UP)
			&& blockBelow !is net.minecraft.block.SlabBlock
			&& blockBelow !is net.minecraft.block.StairsBlock
			&& !solidEnough
		) {
			return false
		}

		val blockAtPos = world.getBlockState(pos)
		val blockAbove = world.getBlockState(pos.up())
		return (blockAtPos.isAir || blockAtPos.getCollisionShape(world, pos).isEmpty) &&
				(blockAbove.isAir || blockAbove.getCollisionShape(world, pos.up()).isEmpty)
	}

	fun parseDimension(str: String): RegistryKey<World> {
		val parts = str.split(":")
		if (parts.size != 2) {
			logger.warn("Invalid dimension '$str', using 'minecraft:overworld'")
			return RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"))
		}
		return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(parts[0], parts[1]))
	}

	enum class SpawnLocationType {
		SURFACE, UNDERGROUND, WATER, ALL
	}

	/**
	 * Recalculate spawn positions for all spawners.
	 */
	fun recalculateAllSpawnPositions(server: MinecraftServer) {
		for ((pos, data) in CobbleSpawnersConfig.spawners) {
			val world = server.getWorld(parseDimension(data.dimension)) ?: continue
			val newPositions = computeValidSpawnPositions(world, data)
			spawnerValidPositions[pos] = newPositions
		}
	}
}