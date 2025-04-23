package com.cobblespawners.utils

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblespawners.api.SpawnerNBTManager
import com.everlastingutils.utils.logDebug
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.ConcurrentHashMap

class CatchingTracker {

    // Tracking logic remains unchanged; omitted for brevity
    data class PokeballTrackingInfo(
        val pokeBallUuid: java.util.UUID,
        val pokeBallEntity: EmptyPokeBallEntity
    )
    private val playerTrackingMap = ConcurrentHashMap<ServerPlayerEntity, PokeballTrackingInfo>()

    fun registerEvents() {
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { event ->
            handlePokeBallCaptureCalculated(event)
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            playerTrackingMap.forEach { (player, trackingInfo) ->
                val world = player.world as ServerWorld
                if (world.getEntity(trackingInfo.pokeBallUuid) == null) {
                    returnPokeballToPlayer(player, trackingInfo.pokeBallEntity)
                    playerTrackingMap.remove(player)
                }
            }
        }
    }

    private fun handlePokeBallCaptureCalculated(event: PokeBallCaptureCalculatedEvent) {
        val pokeBallEntity: EmptyPokeBallEntity = event.pokeBallEntity
        val pokemonEntity: PokemonEntity = event.pokemonEntity
        val thrower: ServerPlayerEntity? = pokeBallEntity.owner as? ServerPlayerEntity

        logDebug("Capture event for Pokémon: ${pokemonEntity.pokemon.species.name}", "cobblespawners")

        val spawnerInfo = SpawnerNBTManager.getPokemonInfo(pokemonEntity)
        if (spawnerInfo != null) {
            logDebug("Pokémon is from spawner at ${spawnerInfo.spawnerPos}", "cobblespawners")
            val spawnerData = CobbleSpawnersConfig.spawners[spawnerInfo.spawnerPos]
            if (spawnerData != null) {
                // Extract Pokémon details
                val speciesName = pokemonEntity.pokemon.species.name
                val formName = if (pokemonEntity.pokemon.form.name == "Standard") "Normal" else pokemonEntity.pokemon.form.name
                val pokemonAspects = pokemonEntity.pokemon.aspects.toSet()

                logDebug("Pokémon details: species=$speciesName, form=$formName, aspects=$pokemonAspects", "cobblespawners")

                // Define gender aspects to ignore
                val genderAspects = setOf("male", "female")

                // Find matching spawn entry, ignoring gender
                val pokemonSpawnEntry = spawnerData.selectedPokemon.find {
                    it.pokemonName.equals(speciesName, ignoreCase = true) &&
                            (it.formName?.equals(formName, ignoreCase = true) ?: (formName == "Normal")) &&
                            // Remove gender aspects from Pokémon before comparison
                            run {
                                val configAspectsLower = it.aspects.map { a -> a.lowercase() }.toSet()
                                val pokemonAspectsLower = pokemonAspects.map { a -> a.lowercase() }.toSet()
                                // Remove gender aspects from Pokémon before comparison
                                val pokemonAspectsWithoutGender =
                                    pokemonAspectsLower.filter { aspect -> aspect !in genderAspects }.toSet()
                                logDebug("Matching: configAspects=$configAspectsLower, pokemonAspectsWithoutGender=$pokemonAspectsWithoutGender", "cobblespawners")
                                // Remove gender aspects from Pokémon before comparison
                                configAspectsLower == pokemonAspectsWithoutGender
                            }
                }

                if (pokemonSpawnEntry != null) {
                    val captureSettings = pokemonSpawnEntry.captureSettings
                    logDebug("Matched entry with captureSettings: isCatchable=${captureSettings.isCatchable}", "cobblespawners")

                    if (!captureSettings.isCatchable) {
                        event.captureResult = CaptureContext(
                            numberOfShakes = 0,
                            isSuccessfulCapture = false,
                            isCriticalCapture = false
                        )
                        logDebug("Capture failed: Pokémon is not catchable.", "cobblespawners")
                        thrower?.sendMessage(Text.literal("This Pokémon cannot be captured!").formatted(Formatting.RED), false)
                        thrower?.let { playerTrackingMap[it] = PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity) }
                        return
                    }

                    val usedPokeBallName = pokeBallEntity.pokeBall.name.toString()
                    val allowedPokeBalls = prepareAllowedPokeBallList(captureSettings.requiredPokeBalls)

                    if (captureSettings.restrictCaptureToLimitedBalls) {
                        if (!allowedPokeBalls.contains("ALL") && !isValidPokeBall(allowedPokeBalls, usedPokeBallName)) {
                            event.captureResult = CaptureContext(
                                numberOfShakes = 0,
                                isSuccessfulCapture = false,
                                isCriticalCapture = false
                            )
                            logDebug("Capture failed: Invalid Poké Ball used.", "cobblespawners")
                            thrower?.sendMessage(Text.literal("Only these Poké Balls work: $allowedPokeBalls!").formatted(Formatting.RED), false)
                            thrower?.let { playerTrackingMap[it] = PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity) }
                        } else {
                            logDebug("Capture allowed with valid Poké Ball.", "cobblespawners")
                        }
                    } else {
                        logDebug("Capture allowed with any Poké Ball.", "cobblespawners")
                    }
                } else {
                    logDebug("No matching entry found. Capture allowed.", "cobblespawners")
                }
            }
        } else {
            logDebug("Pokémon not from spawner. Capture allowed.", "cobblespawners")
        }
    }

    // Helper functions remain unchanged; omitted for brevity
    private fun prepareAllowedPokeBallList(allowedPokeBalls: List<String>): List<String> {
        return allowedPokeBalls.map { if (!it.contains(":")) "cobblemon:$it" else it }
    }

    private fun isValidPokeBall(allowedPokeBalls: List<String>, usedPokeBallName: String): Boolean {
        val usedPokeBallNamespace = "cobblemon:$usedPokeBallName"
        return allowedPokeBalls.contains(usedPokeBallName) || allowedPokeBalls.contains(usedPokeBallNamespace)
    }

    private fun returnPokeballToPlayer(player: ServerPlayerEntity?, pokeBallEntity: EmptyPokeBallEntity) {
        if (player == null) return
        pokeBallEntity.discard()
        val pokeBallStack = pokeBallEntity.pokeBall.item().defaultStack
        val ballPos = pokeBallEntity.blockPos
        player.world.spawnEntity(net.minecraft.entity.ItemEntity(player.world, ballPos.x + 0.5, ballPos.y + 0.5, ballPos.z + 0.5, pokeBallStack))
    }
}