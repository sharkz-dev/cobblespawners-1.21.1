package com.cobblespawners.utils

import com.everlastingutils.command.CommandManager
import com.everlastingutils.gui.setCustomName
import com.cobblemon.mod.common.api.pokedex.entry.DexEntries
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblespawners.CobbleSpawners
import com.cobblespawners.utils.gui.SpawnerListGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider
import com.cobblemon.mod.common.api.pokemon.aspect.SingleConditionalAspectProvider
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblespawners.api.SpawnerNBTManager
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import org.slf4j.LoggerFactory
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.util.math.Box
import java.util.concurrent.CompletableFuture

/**
 * Refactored to use BlanketUtils' [CommandManager] with minimal Brigadier argument usage.
 * Debug commands have been removed to reduce size. This is a simpler approach:
 * each subcommand parses arguments manually from the raw input string.
 */
object CommandRegistrar {

    private val logger = LoggerFactory.getLogger("CobbleSpawners-CommandRegistrar")
    private val cmdManager = CommandManager("cobblespawners", defaultPermissionLevel = 2, defaultOpLevel = 2)

    fun registerCommands() {
        cmdManager.command("cobblespawners", permission = "cobblespawners.base", aliases = listOf("bcs")) {
            // "rename" subcommand: /cobblespawners rename <oldName> <newName>
    // "rename" subcommand: /cobblespawners rename <oldName> <newName>
            subcommand("rename", permission = "CobbleSpawners.Rename") {
                then(
                    argument("oldName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            builder.buildFuture()
                        }
                        .then(
                            argument("newName", StringArgumentType.string())
                                .executes { ctx -> handleRenameCommand(ctx) }
                        )
                        .executes { ctx -> handleRenameCommand(ctx) }
                )
            }

// "inspectnearest" subcommand (updated)
// "inspectnearest" subcommand (updated for aspect inspection)
// "inspectnearest" subcommand (updated to log features)
            subcommand("inspectnearest", permission = "CobbleSpawners.InspectNearest") {
                executes { ctx ->
                    val player = ctx.source.player as? ServerPlayerEntity
                    if (player == null) {
                        sendError(ctx, "Only players can use /cobblespawners inspectnearest.")
                        return@executes 0
                    }
                    val pos: Vec3d = player.pos
                    val world = player.world
                    // Search within a 50-block radius.
                    val searchBox = Box(pos, pos).expand(50.0)
                    val pokemonEntities = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { true }
                    if (pokemonEntities.isEmpty()) {
                        sendError(ctx, "No Pokémon found nearby.")
                        return@executes 0
                    }
                    // Get the nearest Pokémon.
                    val nearest = pokemonEntities.minByOrNull { it.squaredDistanceTo(player) }!!
                    val species = nearest.pokemon.species
                    val currentForm = nearest.pokemon.form
                    val aspects = nearest.pokemon.aspects
                    val features = nearest.pokemon.features
                    logger.info("===== Inspect Nearest Pokémon =====")
                    logger.info("Species: ${species.name}")
                    logger.info("Current Form: ${currentForm.name}")
                    logger.info("Aspects: $aspects")
                    logger.info("Features: $features")

                    // Compute what the form would be if the "shulker" aspect were applied.
                    val shulkerForm = species.getForm(setOf("shulker"))
                    if (shulkerForm != species.standardForm) {
                        logger.info("Computed 'Shulker' Form: ${shulkerForm.name}")
                    } else {
                        logger.info("No 'shulker' form available (returns standard form).")
                    }

                    // Look up the Dex entry for this species.
                    val dexEntry = DexEntries.entries[species.resourceIdentifier]
                    if (dexEntry != null) {
                        logger.info("=== Dex Entry ===")
                        logger.info("ID: ${dexEntry.id}")
                        logger.info("Species ID: ${dexEntry.speciesId}")
                        logger.info("Display Aspects: ${dexEntry.displayAspects}")
                        logger.info("Condition Aspects: ${dexEntry.conditionAspects}")
                        logger.info("Forms: ${dexEntry.forms}")
                        logger.info("Variations: ${dexEntry.variations}")
                    } else {
                        logger.info("No Dex entry found for species ${species.resourceIdentifier}")
                    }

                    sendSuccess(ctx, "Inspected nearest Pokémon. Check console for details.")
                    1
                }
            }


// "addmon" subcommand with suggestions
            subcommand("addmon", permission = "CobbleSpawners.Addmon") {
                executes { ctx ->
                    sendError(ctx, "Usage: /cobblespawners addmon <spawnerName> <pokemonName> [formName]")
                    0
                }

                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            builder.buildFuture()
                        }
                        .then(
                            argument("pokemonName", StringArgumentType.string())
                                .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                                    // Suggest all implemented Pokémon names with proper capitalization.
                                    PokemonSpecies.implemented.forEach { species ->
                                        builder.suggest(species.name.replaceFirstChar { it.uppercase() })
                                    }
                                    builder.buildFuture()
                                }
                                .then(
                                    argument("formName", StringArgumentType.string())
                                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                                            // Manually parse the input to retrieve the pokemonName.
                                            val args = context.input.trim().split("\\s+".toRegex())
                                            if (args.size < 4) return@suggests builder.buildFuture()
                                            val pokemonName = args[3].replaceFirstChar { it.uppercase() }
                                            val species = PokemonSpecies.getByName(pokemonName.lowercase())
                                            // Suggest "Normal" as default and all available forms (capitalized).
                                            builder.suggest("Normal")
                                            species?.forms?.forEach { form ->
                                                builder.suggest(form.name.replaceFirstChar { it.uppercase() })
                                            }
                                            builder.buildFuture()
                                        }
                                        .executes { ctx -> handleAddMonCommand(ctx) }
                                )
                                .executes { ctx -> handleAddMonCommand(ctx) }
                        )
                        .executes { ctx ->
                            sendError(ctx, "Please specify a Pokemon name")
                            0
                        }
                )
            }



// "removemon" subcommand with suggestions
            subcommand("removemon", permission = "CobbleSpawners.Removemon") {
                executes { ctx ->
                    sendError(ctx, "Usage: /cobblespawners removemon <spawnerName> <pokemonName> [formName]")
                    0
                }

                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            builder.buildFuture()
                        }
                        .then(
                            argument("pokemonName", StringArgumentType.string())
                                .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                                    // Manually parse the input to retrieve spawnerName from the command tokens.
                                    val args = context.input.trim().split("\\s+".toRegex())
                                    if (args.size < 3) return@suggests builder.buildFuture()
                                    val spawnerName = args[2]
                                    val spawner = CobbleSpawnersConfig.spawners.values.find { it.spawnerName.equals(spawnerName, ignoreCase = true) }
                                    // Suggest only the Pokémon that are currently selected for this spawner.
                                    spawner?.selectedPokemon?.forEach { pokemonEntry ->
                                        builder.suggest(pokemonEntry.pokemonName)
                                    }
                                    builder.buildFuture()
                                }
                                .then(
                                    argument("formName", StringArgumentType.string())
                                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                                            // Manually parse the input to retrieve spawnerName and pokemonName.
                                            val args = context.input.trim().split("\\s+".toRegex())
                                            if (args.size < 4) return@suggests builder.buildFuture()
                                            val spawnerName = args[2]
                                            val pokemonName = args[3]
                                            val spawner = CobbleSpawnersConfig.spawners.values.find { it.spawnerName.equals(spawnerName, ignoreCase = true) }
                                            val pokemonEntry = spawner?.selectedPokemon?.find { it.pokemonName.equals(pokemonName, ignoreCase = true) }
                                            // Suggest the form if available.
                                            pokemonEntry?.formName?.let { form ->
                                                builder.suggest(form)
                                            }
                                            builder.buildFuture()
                                        }
                                        .executes { ctx -> handleRemoveMonCommand(ctx) }
                                )
                                .executes { ctx -> handleRemoveMonCommand(ctx) }
                        )
                        .executes { ctx ->
                            sendError(ctx, "Please specify a Pokemon name")
                            0
                        }
                )
            }




            // "killspawned" subcommand: /cobblespawners killspawned <spawnerName>
            // Enhanced killspawned command with suggestions
            subcommand("killspawned", permission = "CobbleSpawners.Killspawned") {
                executes { ctx ->
                    // Show usage when no arguments are provided
                    sendError(ctx, "Usage: /cobblespawners killspawned <spawnerName>")
                    0
                }

                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            val completableFuture = CompletableFuture<Suggestions>()
                            // Add all existing spawner names as suggestions
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            completableFuture.complete(builder.build())
                            completableFuture
                        }
                        .executes { ctx ->
                            handleKillSpawned(ctx)
                        }
                )
            }

            // "toggleradius" subcommand: /cobblespawners toggleradius <spawnerName>
            subcommand("toggleradius", permission = "CobbleSpawners.ToggleRadius") {
                executes { ctx ->
                    sendError(ctx, "Usage: /cobblespawners toggleradius <spawnerName>")
                    0
                }

                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            val completableFuture = CompletableFuture<Suggestions>()
                            // Add all existing spawner names as suggestions
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            completableFuture.complete(builder.build())
                            completableFuture
                        }
                        .executes { ctx -> handleToggleRadius(ctx) }
                )
            }

            // "teleport" subcommand: /cobblespawners teleport <spawnerName>
            subcommand("teleport", permission = "CobbleSpawners.Teleport") {
                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx -> handleTeleportCommand(ctx) }
                )
            }



            // "gui" subcommand: /cobblespawners gui
            subcommand("gui", permission = "CobbleSpawners.GUI") {
                // If no spawner name is provided, open the global spawner list GUI.
                executes { ctx ->
                    val player = ctx.source.player as? ServerPlayerEntity
                    if (player == null) {
                        sendError(ctx, "Only players can use /cobblespawners gui.")
                        return@executes 0
                    }
                    SpawnerListGui.openSpawnerListGui(player)
                    sendSuccess(ctx, "Opened Global Spawner GUI.")
                    1
                }
                // If a spawner name is provided, open the individual spawner GUI.
                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            CompletableFuture.completedFuture(builder.build())
                        }
                        .executes { ctx -> handleGuiCommand(ctx) }
                )
            }


            subcommand("viewall", permission = "CobbleSpawners.ViewAll") {
                then(
                    argument("pokemonName", StringArgumentType.string())
                        .suggests { ctx: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            // Suggest species names using the species list.
                            PokemonSpecies.implemented.forEach { species ->
                                builder.suggest(species.name)
                            }
                            CompletableFuture.completedFuture(builder.build())
                        }
                        .executes { ctx ->
                            val inputName = StringArgumentType.getString(ctx, "pokemonName")
                            // Look up species using a case-insensitive match.
                            val species = PokemonSpecies.species.find { it.name.equals(inputName, ignoreCase = true) }
                            if (species == null) {
                                sendError(ctx, "No species found for '$inputName'.")
                                return@executes 0
                            }

                            // Get the raw features for the species via the SpeciesFeatures API.
                            val features = SpeciesFeatures.getFeaturesFor(species)

                            // Retrieve forms: if species.forms is nonempty, use that; otherwise fall back to standardForm.
                            val forms = if (species.forms.isNotEmpty()) species.forms else listOf(species.standardForm)

                            // Look up the Dex entry variations (raw JSON data) for the species.
                            val dexEntry = DexEntries.entries[species.resourceIdentifier]

                            // Build an aggregated JSON object
                            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                            val outJson = com.google.gson.JsonObject()
                            outJson.add("features", gson.toJsonTree(features))
                            outJson.add("forms", gson.toJsonTree(forms))
                            if (dexEntry != null) {
                                outJson.add("variations", gson.toJsonTree(dexEntry.variations))
                            }

                            sendSuccess(ctx, "Raw data for ${species.name}:\n" + gson.toJson(outJson))
                            1
                        }
                )
            }

            // "reload" subcommand: /cobblespawners reload
            subcommand("reload", permission = "CobbleSpawners.Reload") {
                executes { ctx -> handleReloadCommand(ctx) }
            }

            // "givespawnerblock" subcommand: /cobblespawners givespawnerblock
            subcommand("givespawnerblock", permission = "CobbleSpawners.GiveSpawnerBlock") {
                executes { ctx -> handleGiveSpawnerBlock(ctx) }
            }

            // "help" subcommand: /cobblespawners help
            subcommand("help", permission = "CobbleSpawners.Help") {
                executes { ctx -> handleHelpCommand(ctx) }
            }
        }

        cmdManager.register()
    }

    /**
     * Basic argument splitter: This reads the entire command string from Brigadier, splits by space,
     * and returns a list of tokens. e.g. "/cobblespawners rename oldName newName" -> ["cobblespawners", "rename", "oldName", "newName"]
     * We use indexes carefully to fetch subcommand arguments.
     */
    private fun getArgs(context: CommandContext<ServerCommandSource>): List<String> {
        return context.input.trim().split("\\s+".toRegex())
    }

    // --------------------------------------------------------------------------------------------
    // Subcommand Handlers
    // --------------------------------------------------------------------------------------------

    private fun handleEditCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners edit <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        val player = ctx.source.player as? ServerPlayerEntity

        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (player == null) {
            sendError(ctx, "Only players can use /cobblespawners edit.")
            return 0
        }
        SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerEntry.spawnerPos)
        sendSuccess(ctx, "GUI for spawner '$spawnerName' has been opened.")
        return 1
    }

    private fun handleRenameCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 4) {
            sendError(ctx, "Usage: /cobblespawners rename <oldName> <newName>")
            return 0
        }
        val currentName = args[2]
        val newName = args[3]
        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == currentName }

        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$currentName' not found.")
            return 0
        }
        if (CobbleSpawnersConfig.spawners.values.any { it.spawnerName == newName }) {
            sendError(ctx, "Spawner name '$newName' is already in use.")
            return 0
        }
        spawnerEntry.spawnerName = newName
        CobbleSpawnersConfig.saveSpawnerData()
        sendSuccess(ctx, "Spawner renamed from '$currentName' to '$newName'.")
        return 1
    }

    private fun handleAddMonCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 4) {
            sendError(ctx, "Usage: /cobblespawners addmon <spawnerName> <pokemonName> [formName]")
            return 0
        }
        val spawnerName = args[2]
        // Capitalize the Pokémon name (e.g. "Rattata")
        val pokemonName = args[3].replaceFirstChar { it.uppercase() }
        // Capitalize the form name if provided (e.g. "Alola"); otherwise, use empty string
        val formName = if (args.size >= 5) args[4].replaceFirstChar { it.uppercase() } else ""

        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        // Look up species using lowercase for compatibility
        val species = PokemonSpecies.getByName(pokemonName.lowercase())
        if (species == null) {
            sendError(ctx, "Pokémon '$pokemonName' not found.")
            return 0
        }

        val selectedForm = when {
            species.forms.isEmpty() -> "Normal"
            species.forms.any { it.name.equals(formName, ignoreCase = true) } -> formName
            formName.isBlank() || formName.equals("Normal", ignoreCase = true) -> "Normal"
            else -> {
                sendError(ctx, "Form '$formName' does not exist for Pokémon '$pokemonName'. Defaulting to 'Normal'.")
                "Normal"
            }
        }

        val newEntry = PokemonSpawnEntry(
            pokemonName = pokemonName,
            formName = selectedForm,
            spawnChance = 50.0,
            minLevel = 1,
            maxLevel = 100,
            captureSettings = CaptureSettings(
                isCatchable = true,
                restrictCaptureToLimitedBalls = true,
                requiredPokeBalls = listOf("safari_ball")
            ),
            ivSettings = IVSettings(
                allowCustomIvs = false,
                minIVHp = 0, maxIVHp = 31,
                minIVAttack = 0, maxIVAttack = 31,
                minIVDefense = 0, maxIVDefense = 31,
                minIVSpecialAttack = 0, maxIVSpecialAttack = 31,
                minIVSpecialDefense = 0, maxIVSpecialDefense = 31,
                minIVSpeed = 0, maxIVSpeed = 31
            ),
            evSettings = EVSettings(
                allowCustomEvsOnDefeat = false,
                evHp = 0, evAttack = 0, evDefense = 0,
                evSpecialAttack = 0, evSpecialDefense = 0, evSpeed = 0
            ),
            spawnSettings = SpawnSettings(
                spawnTime = "ALL",
                spawnWeather = "ALL",
                spawnLocation = "ALL"
            )
        )

        if (CobbleSpawnersConfig.addPokemonSpawnEntry(spawnerEntry.spawnerPos, newEntry)) {
            // Instead of adding the entry directly to the config, simply reload the configuration.
            CobbleSpawnersConfig.reloadBlocking()
            sendSuccess(ctx, "Added Pokémon '$pokemonName' (form '$selectedForm') to spawner '$spawnerName'.")
            return 1
        } else {
            sendError(ctx, "Failed to add Pokémon '$pokemonName' to spawner '$spawnerName'.")
            return 0
        }
    }


    private fun handleRemoveMonCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 4) {
            sendError(ctx, "Usage: /cobblespawners removemon <spawnerName> <pokemonName> [formName]")
            return 0
        }

        val spawnerName = args[2]
        val pokemonName = args[3]
        val formName = if (args.size >= 5) args[4] else null

        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }

        val success = CobbleSpawnersConfig.removePokemonSpawnEntry(spawnerEntry.spawnerPos, pokemonName, formName)
        return if (success) {
            sendSuccess(ctx, "Removed Pokémon '$pokemonName' (form '${formName ?: "any"}') from spawner '$spawnerName'.")
            1
        } else {
            sendError(ctx, "Failed to remove Pokémon '$pokemonName' from spawner '$spawnerName'.")
            0
        }
    }

    private fun handleKillSpawned(ctx: CommandContext<ServerCommandSource>): Int {
        val spawnerName = StringArgumentType.getString(ctx, "spawnerName")
        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }

        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }

        val spawnerPos = spawnerEntry.spawnerPos
        val server = ctx.source.server
        val worldKey = CobbleSpawners.parseDimension(spawnerEntry.dimension)
        val serverWorld = server.getWorld(worldKey)

        if (serverWorld == null) {
            sendError(ctx, "World '${worldKey.value}' not found.")
            return 0
        }

        SpawnerNBTManager.clearPokemonForSpawner(serverWorld, spawnerPos)

        sendSuccess(ctx, "All Pokémon spawned by '$spawnerName' have been removed.")
        return 1
    }

    private fun handleToggleVisibility(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners togglevisibility <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (CommandRegistrarUtil.toggleSpawnerVisibility(ctx.source.server, spawnerData.spawnerPos)) {
            sendSuccess(ctx, "Spawner '$spawnerName' visibility toggled.")
            return 1
        }
        sendError(ctx, "Failed to toggle visibility for '$spawnerName'.")
        return 0
    }

    private fun handleToggleRadius(ctx: CommandContext<ServerCommandSource>): Int {
        val spawnerName = StringArgumentType.getString(ctx, "spawnerName")
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        val player = ctx.source.player

        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (player == null) {
            sendError(ctx, "Only players can use /cobblespawners toggleradius.")
            return 0
        }
        ParticleUtils.toggleVisualization(player, spawnerData)
        sendSuccess(ctx, "Spawn radius visualization toggled for '$spawnerName'.")
        return 1
    }

    private fun handleTeleportCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners teleport <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName.equals(spawnerName, ignoreCase = true) }
        val player = ctx.source.player as? ServerPlayerEntity
        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (player == null) {
            sendError(ctx, "Only players can use /cobblespawners teleport.")
            return 0
        }
        val worldKey = CobbleSpawners.parseDimension(spawnerData.dimension)
        val world = ctx.source.server.getWorld(worldKey)
        if (world == null) {
            sendError(ctx, "Dimension '${spawnerData.dimension}' not found.")
            return 0
        }
        val pos = spawnerData.spawnerPos
        player.teleport(world, pos.x + 0.5, pos.y + 0.0, pos.z + 0.5, player.yaw, player.pitch)
        sendSuccess(ctx, "Teleported to spawner '$spawnerName'.")
        return 1
    }


    private fun handleListCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can use /cobblespawners list.")
            return 0
        }
        val spawnerList = CobbleSpawnersConfig.spawners.values.joinToString("\n") { data ->
            "${data.spawnerName}: ${data.spawnerPos.x}, ${data.spawnerPos.y}, ${data.spawnerPos.z} (${data.dimension})"
        }
        if (spawnerList.isBlank()) {
            player.sendMessage(Text.literal("No spawners found."), false)
        } else {
            player.sendMessage(Text.literal("Spawners:\n$spawnerList"), false)
        }
        return 1
    }

    private fun handleGuiCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = ctx.input.trim().split("\\s+".toRegex())
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners gui <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName.equals(spawnerName, ignoreCase = true) }
        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can use /cobblespawners gui.")
            return 0
        }
        // Open the GUI for the selected spawner.
        SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerData.spawnerPos)
        sendSuccess(ctx, "Opened spawner GUI for '$spawnerName'.")
        return 1
    }


    private fun handleReloadCommand(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleSpawnersConfig.reloadBlocking()
        sendSuccess(ctx, "CobbleSpawners configuration reloaded.")
        return 1
    }

    /**
     * **CHANGED** to include the custom model data (16666) on the spawner item,
     * so it can be properly recognized in the "use block" event for placement.
     */
    private fun handleGiveSpawnerBlock(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can use /cobblespawners givespawnerblock.")
            return 0
        }
        val customSpawnerItem = ItemStack(Items.SPAWNER).apply {
            count = 1
            // Set a custom model data of 16666 to track spawner placement
            val customModelData = CustomModelDataComponent(16666)
            set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelData)

            // Set a custom name for clarity
            setCustomName(Text.literal("Custom Cobble Spawner"))
        }
        if (player.inventory.insertStack(customSpawnerItem)) {
            sendSuccess(ctx, "A custom spawner has been added to your inventory.")
        } else {
            sendError(ctx, "Inventory full. Could not add the custom spawner.")
        }
        return 1
    }

    private fun handleHelpCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can view help.")
            return 0
        }
        val helpText = Text.literal("CobbleSpawners Commands:\n").append(
            Text.literal(
                """
                /cobblespawners reload
                /cobblespawners list
                /cobblespawners gui
                /cobblespawners edit <spawnerName>
                /cobblespawners rename <oldName> <newName>
                /cobblespawners addmon <spawnerName> <pokemonName> [formName]
                /cobblespawners removemon <spawnerName> <pokemonName> [formName]
                /cobblespawners killspawned <spawnerName>
                /cobblespawners togglevisibility <spawnerName>
                /cobblespawners toggleradius <spawnerName>
                /cobblespawners teleport <spawnerName>
                /cobblespawners givespawnerblock
                """.trimIndent()
            )
        )
        player.sendMessage(helpText, false)
        return 1
    }

    // --------------------------------------------------------------------------------------------
    // Utility methods
    // --------------------------------------------------------------------------------------------

    private fun sendSuccess(ctx: CommandContext<ServerCommandSource>, message: String) {
        CommandManager.sendSuccess(ctx.source, message, false)
    }

    private fun sendError(ctx: CommandContext<ServerCommandSource>, message: String) {
        CommandManager.sendError(ctx.source, message)
    }
}

/**
 * Just factoring out the "toggleSpawnerVisibility" logic to keep the main file shorter.
 * (Originally at the bottom of the old CommandRegistrar.)
 */
object CommandRegistrarUtil {
    private val logger = LoggerFactory.getLogger("CobbleSpawners-CmdUtil")

    fun toggleSpawnerVisibility(server: net.minecraft.server.MinecraftServer, spawnerPos: net.minecraft.util.math.BlockPos): Boolean {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
            ?: run {
                logger.error("No spawner found at $spawnerPos.")
                return false
            }
        spawnerData.visible = !spawnerData.visible
        val dimensionKey = CobbleSpawners.parseDimension(spawnerData.dimension)
        val world = server.getWorld(dimensionKey) ?: return false

        return try {
            if (spawnerData.visible) {
                world.setBlockState(spawnerPos, net.minecraft.block.Blocks.SPAWNER.defaultState)
            } else {
                world.setBlockState(spawnerPos, net.minecraft.block.Blocks.AIR.defaultState)
            }
            CobbleSpawnersConfig.saveSpawnerData()
            true
        } catch (e: Exception) {
            logger.error("Error toggling spawner at $spawnerPos: ${e.message}")
            false
        }
    }
}
