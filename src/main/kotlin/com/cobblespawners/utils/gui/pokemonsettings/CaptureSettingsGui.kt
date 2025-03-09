package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.*
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.gui.PokemonEditSubGui
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory

object CaptureSettingsGui {
    private val logger = LoggerFactory.getLogger(CaptureSettingsGui::class.java)

    // GUI slot configuration
    private object Slots {
        const val CATCHABLE_TOGGLE = 21
        const val RESTRICT_CAPTURE = 23
        const val BACK_BUTTON = 49
    }

    // Texture constants
    private object Textures {
        const val CATCHABLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNlNjg3NjhmNGZhYjgxYzk0ZGY3MzVlMjA1YzNiNDVlYzQ1YTY3YjU1OGYzODg0NDc5YTYyZGQzZjRiZGJmOCJ9fX0="
        const val RESTRICT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ0MDhjNTY5OGYyZDdhOGExNDE1ZWY5NTkyYWViNGJmNjJjOWFlN2NjZjE4ODQ5NzUzMGJmM2M4Yjk2NDhlNSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    /**
     * Opens the Capture Settings GUI for a specific Pokémon and form.
     */
    fun openCaptureSettingsGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        // Get the standardized form name and Pokémon entry
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

        // Track open GUI
        spawnerGuisOpen[spawnerPos] = player

        // Build the title including the aspects
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) additionalAspects.joinToString(", ") else ""
        val guiTitle = if (aspectsDisplay.isNotEmpty())
            "Edit Capture Settings for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Edit Capture Settings for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})"

        // Generate layout and open GUI
        CustomGui.openGui(
            player,
            guiTitle,
            generateCaptureSettingsLayout(selectedEntry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects, selectedEntry) },
            { handleClose(it, spawnerPos, pokemonName, selectedEntry.formName) }
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
        when (context.slotIndex) {
            Slots.CATCHABLE_TOGGLE -> {
                if (context.clickType == ClickType.LEFT) {
                    // Get the new value directly from the toggle function
                    val newCatchableValue = toggleIsCatchable(spawnerPos, pokemonName, formName, additionalAspects, player)
                    updateGuiItem(context, player, "Catchable", newCatchableValue, Formatting.GREEN)
                }
            }
            Slots.RESTRICT_CAPTURE -> {
                if (context.clickType == ClickType.LEFT) {
                    // Get the new value directly from the toggle function
                    val newRestrictValue = toggleRestrictCapture(spawnerPos, pokemonName, formName, additionalAspects, player)
                    updateGuiItem(context, player, "Restrict Capture To Limited Balls", newRestrictValue, Formatting.RED)
                } else if (context.clickType == ClickType.RIGHT) {
                    CaptureBallSettingsGui.openCaptureBallSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                }
            }
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
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
        // No need to send message to player here as they might be null
    }

    /**
     * Generates the layout for the Capture Settings GUI
     */
    private fun generateCaptureSettingsLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }

        // Add toggle buttons
        layout[Slots.CATCHABLE_TOGGLE] = createToggleButton(
            "Catchable",
            Formatting.GREEN,
            selectedEntry.captureSettings.isCatchable,
            "Left-click to toggle catchable status",
            Textures.CATCHABLE
        )

        layout[Slots.RESTRICT_CAPTURE] = createRestrictCaptureButton(selectedEntry.captureSettings.restrictCaptureToLimitedBalls)

        // Add back button
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    /**
     * Creates a toggle button
     */
    private fun createToggleButton(
        text: String,
        color: Formatting,
        isEnabled: Boolean,
        description: String,
        textureValue: String
    ): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "${text.replace(" ", "")}Toggle",
            Text.literal(text).styled { it.withColor(color).withBold(false).withItalic(false) },
            listOf(
                Text.literal(description).styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (isEnabled) "ON" else "OFF"}").styled {
                    it.withColor(if (isEnabled) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            textureValue
        )
    }

    /**
     * Creates the restrict capture button
     */
    private fun createRestrictCaptureButton(restrictCapture: Boolean): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "RestrictCaptureToggle",
            Text.literal("Restrict Capture To Limited Balls").styled { it.withColor(Formatting.RED).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Left-click to toggle restricted capture").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (restrictCapture) "ON" else "OFF"}").styled {
                    it.withColor(if (restrictCapture) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                },
                Text.literal("Right-click to edit required Poké Balls").styled { it.withColor(Formatting.GRAY).withItalic(false) },
            ),
            Textures.RESTRICT
        )
    }

    /**
     * Creates a back button
     */
    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE).withBold(false).withItalic(false) },
            listOf(Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            Textures.BACK
        )
    }

    /**
     * Creates a filler pane
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    /**
     * Toggles whether the Pokémon is catchable and returns the new value
     */
    private fun toggleIsCatchable(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        player: ServerPlayerEntity
    ): Boolean {
        var newValue = false

        CobbleSpawnersConfig.updatePokemonSpawnEntry(
            spawnerPos,
            pokemonName,
            formName,
            additionalAspects
        ) { entry ->
            entry.captureSettings.isCatchable = !entry.captureSettings.isCatchable
            newValue = entry.captureSettings.isCatchable
        }

        logger.info(
            "Toggled catchable state for $pokemonName (${formName ?: "Standard"}) " +
                    "with aspects ${additionalAspects.joinToString(", ")} at spawner $spawnerPos to $newValue."
        )
        player.sendMessage(Text.literal("Toggled catchable state for $pokemonName."), false)
        return newValue
    }

    /**
     * Toggles whether capture is restricted to specific balls and returns the new value
     */
    private fun toggleRestrictCapture(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        player: ServerPlayerEntity
    ): Boolean {
        var newValue = false

        CobbleSpawnersConfig.updatePokemonSpawnEntry(
            spawnerPos,
            pokemonName,
            formName,
            additionalAspects
        ) { entry ->
            entry.captureSettings.restrictCaptureToLimitedBalls = !entry.captureSettings.restrictCaptureToLimitedBalls
            newValue = entry.captureSettings.restrictCaptureToLimitedBalls
        }

        logger.info(
            "Toggled restricted capture for $pokemonName (${formName ?: "Standard"}) " +
                    "with aspects ${additionalAspects.joinToString(", ")} at spawner $spawnerPos to $newValue."
        )
        player.sendMessage(Text.literal("Toggled restricted capture for $pokemonName."), false)
        return newValue
    }

    /**
     * Updates a GUI item to reflect a new setting value
     */
    private fun updateGuiItem(
        context: InteractionContext,
        player: ServerPlayerEntity,
        settingName: String,
        newValue: Boolean,
        color: Formatting
    ) {
        val itemStack = context.clickedStack

        // Set custom name
        itemStack.setCustomName(
            Text.literal(settingName).styled {
                it.withColor(color).withBold(false).withItalic(false)
            }
        )

        // Update lore
        val oldLoreComponent = itemStack.getOrDefault(DataComponentTypes.LORE, LoreComponent(emptyList()))
        val oldLore = oldLoreComponent.lines.toMutableList()

        // Find and update the "Status:" line
        var updatedStatus = false
        for (i in oldLore.indices) {
            val line = oldLore[i]
            if (line.string.contains("Status:", ignoreCase = true)) {
                oldLore[i] = Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
                updatedStatus = true
                break
            }
        }

        // If no "Status:" line was found, add a new one
        if (!updatedStatus) {
            oldLore.add(
                Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            )
        }

        // Update the lore
        itemStack.set(DataComponentTypes.LORE, LoreComponent(oldLore))

        // Update the item in the GUI
        player.currentScreenHandler.slots[context.slotIndex].stack = itemStack
        player.currentScreenHandler.sendContentUpdates()
    }
}