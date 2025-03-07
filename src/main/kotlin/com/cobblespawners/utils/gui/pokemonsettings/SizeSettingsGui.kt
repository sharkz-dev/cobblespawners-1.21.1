// File: SizeSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.everlastingutils.utils.logDebug
import com.cobblespawners.utils.gui.PokemonEditSubGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

object SizeSettingsGui {
    private val logger = LoggerFactory.getLogger(SizeSettingsGui::class.java)

    // GUI Slot configuration
    private object Slots {
        const val TOGGLE_CUSTOM_SIZE = 19
        const val MIN_SIZE = 23
        const val MAX_SIZE = 25
        const val BACK_BUTTON = 49
    }

    // Size adjustment configuration
    private const val SIZE_ADJUSTMENT_VALUE = 0.1f
    private const val MIN_SIZE_BOUND = 0.5f
    private const val MAX_SIZE_BOUND = 3.0f

    // Button configuration
    private data class SizeButton(
        val slot: Int,
        val label: String,
        val textureValue: String,
        val isMinSize: Boolean,
        val formatting: Formatting
    )

    // Map of size buttons
    private val sizeButtons = listOf(
        SizeButton(
            Slots.MIN_SIZE,
            "Min Size",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0=",
            true,
            Formatting.GREEN
        ),
        SizeButton(
            Slots.MAX_SIZE,
            "Max Size",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0=",
            false,
            Formatting.BLUE
        )
    )

    // Lookup map for size buttons by slot
    private val sizeButtonsBySlot = sizeButtons.associateBy { it.slot }

    /**
     * Opens the Size Editing GUI for a specific Pokémon and form.
     */
    fun openSizeEditorGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        // Get the Pokémon entry using additionalAspects for proper lookup
        val standardFormName = formName ?: "Standard"
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, standardFormName, additionalAspects)
        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '$standardFormName' and aspects ${if (additionalAspects.isEmpty()) "none" else additionalAspects.joinToString(", ")} not found in spawner."),
                false
            )
            logger.warn("Pokémon '$pokemonName' with form '$standardFormName' with aspects ${additionalAspects.joinToString(", ")} not found in spawner at $spawnerPos.")
            return
        }

        val layout = generateSizeEditorLayout(selectedEntry)
        spawnerGuisOpen[spawnerPos] = player

        // Build the title including the aspects
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) additionalAspects.joinToString(", ") else ""
        val guiTitle = if (aspectsDisplay.isNotEmpty())
            "Edit Size Settings for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Edit Size Settings for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})"

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects, selectedEntry) },
            { handleClose(it, spawnerPos, pokemonName, formName) }
        )
    }

    /**
     * Handles GUI interactions
     */
    private fun handleInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        selectedEntry: PokemonSpawnEntry
    ) {
        val slotIndex = context.slotIndex

        when (slotIndex) {
            // Handle toggle custom size button
            Slots.TOGGLE_CUSTOM_SIZE -> {
                toggleAllowCustomSize(spawnerPos, pokemonName, formName, player, additionalAspects)
                return
            }

            // Handle size adjustment buttons
            Slots.MIN_SIZE, Slots.MAX_SIZE -> {
                sizeButtonsBySlot[slotIndex]?.let { button ->
                    val increase = when (context.clickType) {
                        ClickType.LEFT -> false
                        ClickType.RIGHT -> true
                        else -> return
                    }
                    adjustSize(spawnerPos, pokemonName, formName, player, button.isMinSize, increase, additionalAspects)
                }
                return
            }

            // Handle back button
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                player.sendMessage(Text.literal("Returning to Edit Pokémon menu."), false)
                PokemonEditSubGui.openPokemonEditSubGui(
                    player, spawnerPos, pokemonName, formName, additionalAspects
                )
            }
        }
    }

    /**
     * Handles GUI close
     */
    private fun handleClose(
        inventory: Inventory,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        spawnerGuisOpen.remove(spawnerPos)
        // No need to send message to player here as the player is probably null at this point
    }

    /**
     * Generates the layout for the Size Editing GUI.
     */
    private fun generateSizeEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val sizeSettings = entry.sizeSettings

        // Add toggle button
        layout[Slots.TOGGLE_CUSTOM_SIZE] = createToggleCustomSizeButton(sizeSettings.allowCustomSize)

        // Add size adjustment buttons
        sizeButtons.forEach { button ->
            val currentSize = if (button.isMinSize) sizeSettings.minSize else sizeSettings.maxSize
            layout[button.slot] = createSizeAdjusterButton(button, currentSize, sizeSettings.allowCustomSize)
        }

        // Add back button
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    /**
     * Creates a toggle button for custom sizes.
     */
    private fun createToggleCustomSizeButton(allowCustomSize: Boolean): ItemStack {
        val textureValue = if (allowCustomSize) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        }

        return CustomGui.createPlayerHeadButton(
            "ToggleCustomSizes",
            Text.literal("Allow Custom Sizes: ${if (allowCustomSize) "ON" else "OFF"}").styled {
                it.withColor(if (allowCustomSize) Formatting.GREEN else Formatting.RED)
                    .withBold(true)
            },
            listOf(
                Text.literal("§eClick to toggle")
            ),
            textureValue
        )
    }

    /**
     * Creates a size adjuster button.
     */
    private fun createSizeAdjusterButton(
        button: SizeButton,
        currentSize: Float,
        allowCustomSize: Boolean
    ): ItemStack {
        val displayName = "${button.label}: %.1f".format(currentSize)

        return CustomGui.createPlayerHeadButton(
            button.label.replace(" ", "") + "Head",
            Text.literal(displayName).styled {
                it.withColor(button.formatting).withBold(true)
            },
            listOf(
                Text.literal("§7Left-click to decrease by 0.1"),
                Text.literal("§7Right-click to increase by 0.1")
            ),
            button.textureValue
        )
    }

    /**
     * Creates a Back button.
     */
    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.BLUE).withBold(true) },
            listOf(Text.literal("§7Click to return to the previous menu.")),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )
    }

    /**
     * Creates a filler pane.
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
            CustomGui.setItemLore(this, listOf(" "))
        }
    }

    /**
     * Toggles the allow custom size setting.
     */
    private fun toggleAllowCustomSize(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        additionalAspects: Set<String> = emptySet()
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.sizeSettings.allowCustomSize = !entry.sizeSettings.allowCustomSize
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle custom size setting."), false)
            return
        }

        // Update the GUI with the new setting
        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)?.let { entry ->
            val toggleButton = createToggleCustomSizeButton(entry.sizeSettings.allowCustomSize)
            updateSingleItem(player, Slots.TOGGLE_CUSTOM_SIZE, toggleButton)

            // Notify the player
            val status = if (entry.sizeSettings.allowCustomSize) "enabled" else "disabled"
            player.sendMessage(
                Text.literal("Custom size settings $status for ${entry.pokemonName}."),
                false
            )

            logDebug(
                "Toggled allowCustomSize to $status for ${entry.pokemonName} (${entry.formName ?: "Standard"}) at spawner $spawnerPos.",
                "cobblespawners"
            )
        }
    }

    /**
     * Adjusts the size based on the given parameters.
     */
    private fun adjustSize(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        isMinSize: Boolean,
        increase: Boolean,
        additionalAspects: Set<String> = emptySet()
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            val currentSize = if (isMinSize) entry.sizeSettings.minSize else entry.sizeSettings.maxSize
            val newSize = if (increase) currentSize + SIZE_ADJUSTMENT_VALUE else currentSize - SIZE_ADJUSTMENT_VALUE

            // Determine bounds
            val minBound = MIN_SIZE_BOUND
            val maxBound = if (isMinSize) entry.sizeSettings.maxSize else MAX_SIZE_BOUND

            val adjustedSize = newSize.coerceIn(minBound, maxBound)
            val roundedSize = roundToOneDecimal(adjustedSize)

            // Only update if there was a change after rounding
            if (roundedSize != currentSize) {
                if (isMinSize) {
                    entry.sizeSettings.minSize = roundedSize
                } else {
                    entry.sizeSettings.maxSize = roundedSize
                }
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to adjust size."), false)
            return
        }

        // After updating, get the entry with additionalAspects for correct display
        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)?.let { entry ->
            val sizeSettings = entry.sizeSettings

            // Get button configuration
            val buttonSlot = if (isMinSize) Slots.MIN_SIZE else Slots.MAX_SIZE
            val button = sizeButtonsBySlot[buttonSlot] ?: return@let

            // Get current size value
            val currentSize = if (isMinSize) sizeSettings.minSize else sizeSettings.maxSize

            // Update the button
            val updatedButton = createSizeAdjusterButton(button, currentSize, sizeSettings.allowCustomSize)
            updateSingleItem(player, buttonSlot, updatedButton)

            // Log the adjustment
            logger.info(
                "Adjusted ${if (isMinSize) "min" else "max"} size for ${entry.pokemonName} (${entry.formName ?: "Standard"}) at spawner $spawnerPos to $currentSize."
            )

            // Notify the player
            player.sendMessage(
                Text.literal("Set ${if (isMinSize) "minimum" else "maximum"} size to $currentSize for ${entry.pokemonName}."),
                false
            )
        }
    }

    /**
     * Updates a single slot in the GUI
     */
    private fun updateSingleItem(player: ServerPlayerEntity, slot: Int, item: ItemStack) {
        val screenHandler = player.currentScreenHandler
        if (slot < screenHandler.slots.size) {
            screenHandler.slots[slot].stack = item
            screenHandler.sendContentUpdates()
        }
    }

    /**
     * Rounds a Float to one decimal place.
     */
    private fun roundToOneDecimal(value: Float): Float {
        return (value * 10).roundToInt() / 10f
    }
}