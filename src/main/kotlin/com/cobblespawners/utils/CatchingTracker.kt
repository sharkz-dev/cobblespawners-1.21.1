package com.cobblespawners.utils

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblespawners.api.SpawnerNBTManager
import com.everlastingutils.utils.logDebug
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.ItemEntity // Correct ItemEntity import
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID // Correct UUID import
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue // Import ConcurrentLinkedQueue

class CatchingTracker {

    data class PokeballTrackingInfo(
        val pokeBallUuid: UUID,
        val pokeBallEntity: EmptyPokeBallEntity // Store the entity ref to get itemstack later
    )

    // Use a Queue to handle multiple balls per player
    private val playerTrackingMap = ConcurrentHashMap<ServerPlayerEntity, ConcurrentLinkedQueue<PokeballTrackingInfo>>()

    fun registerEvents() {
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { event ->
            handlePokeBallCaptureCalculated(event)
        }

        // Server Tick Event to check and return balls
        ServerTickEvents.END_SERVER_TICK.register { server ->
            // Use iterator for safe removal from the map itself if a player's queue becomes empty
            val mapIterator = playerTrackingMap.entries.iterator()
            while (mapIterator.hasNext()) {
                val entry = mapIterator.next()
                val player = entry.key
                val queue = entry.value
                val world = player.world as? ServerWorld ?: continue // Safe cast and skip if world is wrong type

                // Use iterator for safe removal from the queue
                val queueIterator = queue.iterator()
                while (queueIterator.hasNext()) {
                    val trackingInfo = queueIterator.next()
                    // Check if the PokeBall entity associated with the UUID no longer exists
                    if (world.getEntity(trackingInfo.pokeBallUuid) == null) {
                        logDebug("Pokeball entity ${trackingInfo.pokeBallUuid} not found for player ${player.name.string}. Returning item.", "cobblespawners")
                        returnPokeballToPlayer(player, trackingInfo.pokeBallEntity)
                        // Remove the processed info from the queue
                        queueIterator.remove()
                    } else {
                        logDebug("Pokeball entity ${trackingInfo.pokeBallUuid} still exists for player ${player.name.string}. Waiting.", "cobblespawners")
                    }
                }

                // If the player's queue is now empty, remove the player's entry from the map
                if (queue.isEmpty()) {
                    logDebug("Player ${player.name.string}'s tracking queue is empty. Removing from map.", "cobblespawners")
                    mapIterator.remove()
                }
            }
        }
    }

    private fun handlePokeBallCaptureCalculated(event: PokeBallCaptureCalculatedEvent) {
        val pokeBallEntity: EmptyPokeBallEntity = event.pokeBallEntity
        val pokemonEntity: PokemonEntity = event.pokemonEntity
        // Ensure thrower is ServerPlayerEntity, otherwise logic can't apply
        val thrower: ServerPlayerEntity = pokeBallEntity.owner as? ServerPlayerEntity ?: return

        logDebug("Capture event for Pokémon: ${pokemonEntity.pokemon.species.name} thrown by ${thrower.name.string}", "cobblespawners")

        val spawnerInfo = SpawnerNBTManager.getPokemonInfo(pokemonEntity)
        if (spawnerInfo != null) {
            logDebug("Pokémon is from spawner at ${spawnerInfo.spawnerPos}", "cobblespawners")
            // Use the safe getter from config object
            val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerInfo.spawnerPos)
            if (spawnerData != null) {
                val speciesName = pokemonEntity.pokemon.species.name
                // Handle form name variations consistently
                val formName = pokemonEntity.pokemon.form.name.let { if (it.equals("Standard", ignoreCase = true)) "Normal" else it }
                val pokemonAspects = pokemonEntity.pokemon.aspects.toSet()

                logDebug("Pokémon details: species=$speciesName, form=$formName, aspects=$pokemonAspects", "cobblespawners")

                val genderAspects = setOf("male", "female")

                val pokemonSpawnEntry = spawnerData.selectedPokemon.find { entry ->
                    val configAspectsLower = entry.aspects.map { it.lowercase() }.toSet()
                    val pokemonAspectsLower = pokemonAspects.map { it.lowercase() }.toSet()
                    // Filter out gender aspects from the Pokemon being caught *before* comparison
                    val pokemonAspectsWithoutGender = pokemonAspectsLower.filter { it !in genderAspects }.toSet()

                    val speciesMatch = entry.pokemonName.equals(speciesName, ignoreCase = true)
                    // Handle null formName in config gracefully (treat as "Normal") and compare case-insensitively
                    val formMatch = (entry.formName?.equals(formName, ignoreCase = true) ?: (formName.equals("Normal", ignoreCase = true)))
                    // Compare the config aspects with the Pokemon's aspects (excluding gender)
                    val aspectsMatch = configAspectsLower == pokemonAspectsWithoutGender

                    logDebug("Matching entry '${entry.pokemonName}': Species=${speciesMatch}, Form=${formMatch} (Config: ${entry.formName}, Actual: $formName), Aspects=${aspectsMatch} (Config: $configAspectsLower, Actual Non-Gender: $pokemonAspectsWithoutGender)", "cobblespawners")

                    speciesMatch && formMatch && aspectsMatch
                }


                if (pokemonSpawnEntry != null) {
                    val captureSettings = pokemonSpawnEntry.captureSettings
                    logDebug("Matched entry ${pokemonSpawnEntry.pokemonName} with captureSettings: isCatchable=${captureSettings.isCatchable}", "cobblespawners")

                    var blockCapture = false // Flag to indicate if we need to block and track the ball

                    if (!captureSettings.isCatchable) {
                        logDebug("Capture failed: Pokémon is not catchable.", "cobblespawners")
                        thrower.sendMessage(Text.literal("This Pokémon cannot be captured!").formatted(Formatting.RED), false)
                        blockCapture = true
                    } else if (captureSettings.restrictCaptureToLimitedBalls) {
                        val usedPokeBallName = pokeBallEntity.pokeBall.name.toString()
                        val allowedPokeBalls = prepareAllowedPokeBallList(captureSettings.requiredPokeBalls)
                        val usedPokeBallIdentifier = pokeBallEntity.pokeBall.item().getRegistryEntry().registryKey().value.toString() // Get full identifier like "cobblemon:poke_ball"

                        logDebug("Checking ball restriction. Used: '$usedPokeBallIdentifier' (or '$usedPokeBallName'). Allowed: $allowedPokeBalls", "cobblespawners")

                        // Check against full identifier OR simple name if "cobblemon:" prefix is missing in config
                        if (!allowedPokeBalls.contains("ALL") && !allowedPokeBalls.any { allowed -> allowed.equals(usedPokeBallIdentifier, ignoreCase = true) || allowed.equals(usedPokeBallName, ignoreCase = true) }) {
                            logDebug("Capture failed: Invalid Poké Ball used.", "cobblespawners")
                            val allowedBallsDisplay = allowedPokeBalls.joinToString { it.substringAfter(":") } // Show cleaner names
                            thrower.sendMessage(Text.literal("Only specific Poké Balls work! Allowed: $allowedBallsDisplay").formatted(Formatting.RED), false)
                            blockCapture = true
                        } else {
                            logDebug("Capture allowed: Valid Poké Ball used or restriction not applicable.", "cobblespawners")
                        }
                    } else {
                        logDebug("Capture allowed: No ball restrictions for this entry.", "cobblespawners")
                    }

                    // If capture needs to be blocked and ball returned
                    if (blockCapture) {
                        event.captureResult = CaptureContext(
                            numberOfShakes = 0,
                            isSuccessfulCapture = false,
                            isCriticalCapture = false
                        )
                        // Get or create the queue for the player and add the ball info
                        val queue = playerTrackingMap.computeIfAbsent(thrower) { ConcurrentLinkedQueue() }
                        queue.add(PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity))
                        logDebug("Added ball ${pokeBallEntity.uuid} to return queue for player ${thrower.name.string}. Queue size: ${queue.size}", "cobblespawners")
                        // No need to return here, let Cobblemon handle the failed animation
                    }
                } else {
                    logDebug("No matching spawner entry found for this specific Pokémon variant. Capture allowed by default.", "cobblespawners")
                }
            } else {
                logDebug("Spawner data not found for position ${spawnerInfo.spawnerPos}. Capture allowed by default.", "cobblespawners")
            }
        } else {
            logDebug("Pokémon not from a spawner. Capture allowed by default.", "cobblespawners")
        }
    }

    // Helper to prepare list, ensuring namespace consistency (prefer full identifier)
    private fun prepareAllowedPokeBallList(allowedPokeBalls: List<String>): List<String> {
        return allowedPokeBalls.map {
            val lower = it.lowercase()
            when {
                lower == "all" -> "ALL" // Handle "ALL" case-insensitively
                !lower.contains(":") -> "cobblemon:$lower" // Add default namespace if missing
                else -> lower // Keep existing namespace
            }
        }
    }


    // Return the Poké Ball item to the player
    private fun returnPokeballToPlayer(player: ServerPlayerEntity, pokeBallEntity: EmptyPokeBallEntity) {
        // Get the ItemStack *before* discarding the entity potentially
        val pokeBallStack = pokeBallEntity.pokeBall.item().defaultStack
        if (pokeBallStack.isEmpty) {
            logDebug("Cannot return Poké Ball for ${player.name.string}, ItemStack is empty.", "cobblespawners")
            return
        }

        // Discard the original ball entity if it somehow still exists (unlikely based on tick check)
        if (!pokeBallEntity.isRemoved) {
            pokeBallEntity.discard()
        }

        val ballPos = pokeBallEntity.blockPos // Position where the ball landed/failed

        // Try adding to inventory first
        if (!player.inventory.insertStack(pokeBallStack)) {
            // If inventory is full, spawn item entity at the ball's last location
            logDebug("Player ${player.name.string}'s inventory full. Spawning item entity at $ballPos.", "cobblespawners")
            val itemEntity = ItemEntity(player.world, ballPos.x + 0.5, ballPos.y + 0.5, ballPos.z + 0.5, pokeBallStack)
            itemEntity.setToDefaultPickupDelay() // Allow pickup quickly
            player.world.spawnEntity(itemEntity)
        } else {
            logDebug("Returned Poké Ball directly to ${player.name.string}'s inventory.", "cobblespawners")
        }
    }
}