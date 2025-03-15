// File: GlobalSettingsGui.kt
package com.cobblespawners.utils.gui

import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.cobblespawners.utils.CobbleSpawnersConfig // Import the new config object
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object GlobalSettingsGui {

    private val logger = LoggerFactory.getLogger("GlobalSettingsGui")

    // Map to store each player's associated spawner position
    private val playerSpawnerMap = ConcurrentHashMap<ServerPlayerEntity, BlockPos>()

    // Modify the openGlobalSettingsGui function to accept and store the spawner position
    fun openGlobalSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos? = null) {
        // Store the spawner position if provided
        if (spawnerPos != null) {
            playerSpawnerMap[player] = spawnerPos
            logger.info("Stored spawner position $spawnerPos for player ${player.name.string}")
        }

        // Access globalConfig from CobbleSpawnersConfig
        val globalConfig = CobbleSpawnersConfig.config.globalConfig
        val layout = generateGlobalSettingsLayout(globalConfig)

        val onInteract: (InteractionContext) -> Unit = { context ->
            try {
                when (context.slotIndex) {
                    11 -> {
                        globalConfig.debugEnabled = !globalConfig.debugEnabled
                        logger.info("Toggled Debug Mode to ${globalConfig.debugEnabled}")
                        updateGuiItem(context, player, "Debug Mode", globalConfig.debugEnabled, Formatting.GOLD)
                        CobbleSpawnersConfig.saveConfigBlocking()
                        player.sendMessage(Text.literal("Debug Mode is now ${if (globalConfig.debugEnabled) "ON" else "OFF"}"), false)
                    }
                    13 -> {
                        globalConfig.cullSpawnerPokemonOnServerStop = !globalConfig.cullSpawnerPokemonOnServerStop
                        logger.info("Toggled Cull Spawner Pokémon on Stop to ${globalConfig.cullSpawnerPokemonOnServerStop}")
                        updateGuiItem(context, player, "Cull Spawner Pokémon on Stop", globalConfig.cullSpawnerPokemonOnServerStop, Formatting.RED)
                        CobbleSpawnersConfig.saveConfigBlocking()
                        player.sendMessage(Text.literal("Cull Spawner Pokémon on Stop is now ${if (globalConfig.cullSpawnerPokemonOnServerStop) "ON" else "OFF"}"), false)
                    }
                    15 -> {
                        globalConfig.showUnimplementedPokemonInGui = !globalConfig.showUnimplementedPokemonInGui
                        logger.info("Toggled Show Unimplemented Pokémon in GUI to ${globalConfig.showUnimplementedPokemonInGui}")
                        updateGuiItem(context, player, "Show Unimplemented Pokémon in GUI", globalConfig.showUnimplementedPokemonInGui, Formatting.BLUE)
                        CobbleSpawnersConfig.saveConfigBlocking()
                        player.sendMessage(Text.literal("Show Unimplemented Pokémon in GUI is now ${if (globalConfig.showUnimplementedPokemonInGui) "ON" else "OFF"}"), false)
                    }
                    30 -> {
                        // Show Form button moved to slot 30
                        globalConfig.showFormsInGui = !globalConfig.showFormsInGui
                        logger.info("Toggled Show Form in GUI to ${globalConfig.showFormsInGui}")
                        updateGuiItem(context, player, "Show Form in GUI", globalConfig.showFormsInGui, Formatting.GREEN)
                        CobbleSpawnersConfig.saveConfigBlocking()
                        player.sendMessage(Text.literal("Show Form in GUI is now ${if (globalConfig.showFormsInGui) "ON" else "OFF"}"), false)
                    }
                    32 -> {
                        // New button for aspects moved to slot 32
                        globalConfig.showAspectsInGui = !globalConfig.showAspectsInGui
                        logger.info("Toggled Show Aspects in GUI to ${globalConfig.showAspectsInGui}")
                        updateGuiItem(context, player, "Show Aspects in GUI", globalConfig.showAspectsInGui, Formatting.DARK_PURPLE)
                        CobbleSpawnersConfig.saveConfigBlocking()
                        player.sendMessage(Text.literal("Show Aspects in GUI is now ${if (globalConfig.showAspectsInGui) "ON" else "OFF"}"), false)
                    }
                    49 -> {
                        logger.info("Back button clicked. Saving config and returning to Pokémon Selection GUI.")
                        CobbleSpawnersConfig.saveConfigBlocking()

                        // Get the stored spawner position
                        val spawnerPos = playerSpawnerMap.remove(player)
                        if (spawnerPos != null) {
                            // Return to the Pokémon Selection GUI
                            SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos)
                        } else {
                            logger.warn("Could not find active spawner GUI for player ${player.name.string}")
                            player.sendMessage(Text.literal("Could not return to previous screen. Please try again."), false)
                        }
                    }
                    // No else clause here; clicks on unhandled slots (e.g., glass panes) are ignored
                }
            } catch (e: Exception) {
                logger.error("Error handling GUI interaction at slot ${context.slotIndex}: ${e.message}", e)
                player.sendMessage(Text.literal("An error occurred while updating settings."), false)
            }
        }

        val onClose: (Inventory) -> Unit = {
            // Clean up the stored spawner position when the GUI is closed
            playerSpawnerMap.remove(player)
            logger.info("Global settings GUI closed by player ${player.name.string}.")
            player.sendMessage(Text.literal("Global settings closed."), false)
        }

        CustomGui.openGui(
            player,
            "Edit Global Settings",
            layout,
            onInteract,
            onClose
        )
    }

    private fun generateGlobalSettingsLayout(globalConfig: GlobalConfig): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        layout[11] = CustomGui.createPlayerHeadButton(
            "DebugModeTexture",
            Text.literal("Debug Mode").styled { it.withColor(Formatting.GOLD).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Toggle Debug Mode").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (globalConfig.debugEnabled) "ON" else "OFF"}").styled {
                    it.withColor(if (globalConfig.debugEnabled) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmI2YzkyZWUwNmY2MmY0Y2YyOWIyYmY0M2I4MTI1YzI0YmI2YTJjNjU4Y2ZiNzE4ZGY5ZTk1ZjJlYzM0NTA1ZSJ9fX0="
        )

        layout[13] = CustomGui.createPlayerHeadButton(
            "CullSpawnerPokemonTexture",
            Text.literal("Cull Spawner Pokémon on Stop").styled { it.withColor(Formatting.RED).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Toggle culling of spawner Pokémon on stop").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (globalConfig.cullSpawnerPokemonOnServerStop) "ON" else "OFF"}").styled {
                    it.withColor(if (globalConfig.cullSpawnerPokemonOnServerStop) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzMzZGJmYjdkZmYyNTY0ZjZiMTc2OGQ3MmEyY2I0M2E4ZDY2YWMzZWFmYzI4MmRkOWJhZDIyN2EzYTAzMDg0In19fQ=="
        )

        layout[15] = CustomGui.createPlayerHeadButton(
            "UnimplementedPokemonTexture",
            Text.literal("Show Unimplemented Pokémon in GUI").styled { it.withColor(Formatting.BLUE).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Toggle display of unimplemented Pokémon").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (globalConfig.showUnimplementedPokemonInGui) "ON" else "OFF"}").styled {
                    it.withColor(if (globalConfig.showUnimplementedPokemonInGui) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWRlOWZjYzA1YTZmYTM3YTI4MGMzMjMwZTc2OWQyM2EwZDMwMDJjMDQ1MjM0MzU2YmQ1MWY0NzRhMjcwZTQzOSJ9fX0="
        )

        // Move the Show Form button one to the left: now at slot 30 instead of 31
        layout[30] = CustomGui.createPlayerHeadButton(
            "ShowFormTexture",
            Text.literal("Show Form in GUI").styled { it.withColor(Formatting.GREEN).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Toggle display of forms in GUI").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (globalConfig.showFormsInGui) "ON" else "OFF"}").styled {
                    it.withColor(if (globalConfig.showFormsInGui) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmFmZDU2ZWE5OThjMDhjNDcyY2IyM2VjY2RlMjE3OTk3OGE0ZTRhNGM5ZTRkM2RmOGVlYWMxYmYyZGU2MzhhYSJ9fX0="
        )

        // Add a new button for the Aspects toggle at slot 32 (moved one to the right)
        layout[32] = CustomGui.createPlayerHeadButton(
            "ShowAspectsTexture",
            Text.literal("Show Aspects in GUI").styled { it.withColor(Formatting.DARK_PURPLE).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Toggle display of additional aspects in GUI").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (globalConfig.showAspectsInGui) "ON" else "OFF"}").styled {
                    it.withColor(if (globalConfig.showAspectsInGui) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDk0ZmE0MDE5MzhmMGNkNjQzOWQ4MWE0M2Q1YTY0MjQxZjE5OWQwMmViYzdhMTk5Y2E4MDkwMzczYTY2YmNhOSJ9fX0="
        )

        // Return to Spawner List button
        layout[49] = CustomGui.createPlayerHeadButton(
            "ReturnToSpawnerListTexture",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE).withBold(false).withItalic(false) },
            listOf(Text.literal("Return to Spawner List").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )

        // Fill remaining empty slots with gray stained glass panes
        for (i in layout.indices) {
            if (layout[i] == ItemStack.EMPTY) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal(" "))
                }
            }
        }

        return layout
    }

    private fun updateGuiItem(context: InteractionContext, player: ServerPlayerEntity, settingName: String, newValue: Boolean, color: Formatting) {
        val itemStack = context.clickedStack
        // Update the name using DataComponents via setCustomName extension
        itemStack.setCustomName(
            Text.literal(settingName).styled {
                it.withColor(color).withBold(false).withItalic(false)
            }
        )

        // Update the lore. Instead of NBT, we use DataComponents.
        val oldLoreComponent = itemStack.getOrDefault(DataComponentTypes.LORE, LoreComponent(emptyList()))
        val oldLore = oldLoreComponent.lines.toMutableList()

        // Look for a line containing "Status:"
        var updatedStatus = false
        for (i in oldLore.indices) {
            val line = oldLore[i]
            if (line.string.contains("Status:", ignoreCase = true)) {
                // Replace this line with the new status line
                oldLore[i] = Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
                updatedStatus = true
                break
            }
        }

        // If no "Status:" line was found, add a new line at the end
        if (!updatedStatus) {
            oldLore.add(
                Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            )
        }

        // Set the updated lore using DataComponents
        itemStack.set(DataComponentTypes.LORE, LoreComponent(oldLore))

        // Update the item in the player's screen
        player.currentScreenHandler.slots[context.slotIndex].stack = itemStack
        player.currentScreenHandler.sendContentUpdates()
    }
}