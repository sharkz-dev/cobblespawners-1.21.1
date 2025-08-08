// File: PersistenceSettingsGui.kt
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

object PersistenceSettingsGui {
    private val logger = LoggerFactory.getLogger(PersistenceSettingsGui::class.java)

    // GUI slot configuration
    private object Slots {
        const val MAKE_PERSISTENT = 11
        const val LEGENDARY_PERSISTENT = 13
        const val ULTRA_BEAST_PERSISTENT = 15
        const val MYTHICAL_PERSISTENT = 29
        const val CUSTOM_LABELS_INFO = 31
        const val BACK_BUTTON = 49
    }

    // Texture constants
    private object Textures {
        const val PERSISTENCE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        const val LEGENDARY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjI1OTliZGM4MDY5NmM2MzQ5YzUzNjI0MzJkODAzNDVhNzMzNWRjNzMzOWFmNGY2NDA2NjQzNjYzNGY3YjEzOCJ9fX0="
        const val ULTRA_BEAST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWZkZTNiZmNlMmQ4Y2I3MjRkZTg1NTZlNWVjMjFiN2YxNWY1ODQ2ODgzNTkzNGFhOGIyOGNlMzM1MjM5NGQ0ZiJ9fX0="
        const val MYTHICAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWVkOGJlYWZkNWY1MzAyZDMzZTc4ODk1OGQzMGQ4YjFmODA1MzI2NDBhNDhlOWQyNTdkYTU5ODI4ZGU0N2FkYSJ9fX0="
        const val INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDAxYWZlOTczYzU0ODJmZGM3MWU2YWExMDY5ODgzM2M3OWM0MzdmMjEzMDhlYTlhMWEwOTU3NDZlYzI3NGEwZiJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    /**
     * Opens the Persistence Settings GUI for a specific Pokémon and form.
     */
    fun openPersistenceSettingsGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
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

        // Inicializar persistenceSettings si es null
        if (selectedEntry.persistenceSettings == null) {
            selectedEntry.persistenceSettings = PersistenceSettings()
        }

        // Track open GUI
        spawnerGuisOpen[spawnerPos] = player

        // Build the title including the aspects
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) additionalAspects.joinToString(", ") else ""
        val guiTitle = if (aspectsDisplay.isNotEmpty())
            "Persistence Settings for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Persistence Settings for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})"

        // Generate layout and open GUI
        CustomGui.openGui(
            player,
            guiTitle,
            generatePersistenceSettingsLayout(selectedEntry),
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
            Slots.MAKE_PERSISTENT -> {
                if (context.clickType == ClickType.LEFT) {
                    val newValue = toggleMakePersistent(spawnerPos, pokemonName, formName, additionalAspects, player)
                    updateGuiItem(context, player, "Force Persistent", newValue, Formatting.GREEN)
                }
            }
            Slots.LEGENDARY_PERSISTENT -> {
                if (context.clickType == ClickType.LEFT) {
                    val newValue = toggleLegendaryPersistent(spawnerPos, pokemonName, formName, additionalAspects, player)
                    updateGuiItem(context, player, "Legendary Persistent", newValue, Formatting.GOLD)
                }
            }
            Slots.ULTRA_BEAST_PERSISTENT -> {
                if (context.clickType == ClickType.LEFT) {
                    val newValue = toggleUltraBeastPersistent(spawnerPos, pokemonName, formName, additionalAspects, player)
                    updateGuiItem(context, player, "Ultra Beast Persistent", newValue, Formatting.DARK_PURPLE)
                }
            }
            Slots.MYTHICAL_PERSISTENT -> {
                if (context.clickType == ClickType.LEFT) {
                    val newValue = toggleMythicalPersistent(spawnerPos, pokemonName, formName, additionalAspects, player)
                    updateGuiItem(context, player, "Mythical Persistent", newValue, Formatting.LIGHT_PURPLE)
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
    }

    /**
     * Generates the layout for the Persistence Settings GUI
     */
    private fun generatePersistenceSettingsLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val persistenceSettings = selectedEntry.persistenceSettings ?: PersistenceSettings()

        // Add toggle buttons
        layout[Slots.MAKE_PERSISTENT] = createToggleButton(
            "Force Persistent",
            Formatting.GREEN,
            persistenceSettings.makePersistent,
            "Forces this Pokémon to always be persistent",
            Textures.PERSISTENCE
        )

        layout[Slots.LEGENDARY_PERSISTENT] = createToggleButton(
            "Legendary Persistent",
            Formatting.GOLD,
            persistenceSettings.legendaryPersistent,
            "Makes legendary Pokémon persistent",
            Textures.LEGENDARY
        )

        layout[Slots.ULTRA_BEAST_PERSISTENT] = createToggleButton(
            "Ultra Beast Persistent",
            Formatting.DARK_PURPLE,
            persistenceSettings.ultraBeastPersistent,
            "Makes Ultra Beast Pokémon persistent",
            Textures.ULTRA_BEAST
        )

        layout[Slots.MYTHICAL_PERSISTENT] = createToggleButton(
            "Mythical Persistent",
            Formatting.LIGHT_PURPLE,
            persistenceSettings.mythicalPersistent,
            "Makes mythical Pokémon persistent",
            Textures.MYTHICAL
        )

        // Add info button for custom labels
        layout[Slots.CUSTOM_LABELS_INFO] = createInfoButton()

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
                },
                Text.literal("Left-click to toggle").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
            ),
            textureValue
        )
    }

    /**
     * Creates an info button for custom labels
     */
    private fun createInfoButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "CustomLabelsInfo",
            Text.literal("Custom Persistent Labels").styled { it.withColor(Formatting.AQUA).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Pokémon with these aspects will be persistent:").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("• boss").styled { it.withColor(Formatting.WHITE).withItalic(false) },
                Text.literal("• rare").styled { it.withColor(Formatting.WHITE).withItalic(false) },
                Text.literal("• special").styled { it.withColor(Formatting.WHITE).withItalic(false) },
                Text.literal("These can be added as aspects when").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("selecting Pokémon for the spawner").styled { it.withColor(Formatting.GRAY).withItalic(false) }
            ),
            Textures.INFO
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

    // Toggle functions
    private fun toggleMakePersistent(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        player: ServerPlayerEntity
    ): Boolean {
        var newValue = false
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
            if (entry.persistenceSettings == null) entry.persistenceSettings = PersistenceSettings()
            entry.persistenceSettings!!.makePersistent = !entry.persistenceSettings!!.makePersistent
            newValue = entry.persistenceSettings!!.makePersistent
        }
        player.sendMessage(Text.literal("Force persistent ${if (newValue) "enabled" else "disabled"} for $pokemonName."), false)
        return newValue
    }

    private fun toggleLegendaryPersistent(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        player: ServerPlayerEntity
    ): Boolean {
        var newValue = false
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
            if (entry.persistenceSettings == null) entry.persistenceSettings = PersistenceSettings()
            entry.persistenceSettings!!.legendaryPersistent = !entry.persistenceSettings!!.legendaryPersistent
            newValue = entry.persistenceSettings!!.legendaryPersistent
        }
        player.sendMessage(Text.literal("Legendary persistent ${if (newValue) "enabled" else "disabled"} for $pokemonName."), false)
        return newValue
    }

    private fun toggleUltraBeastPersistent(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        player: ServerPlayerEntity
    ): Boolean {
        var newValue = false
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
            if (entry.persistenceSettings == null) entry.persistenceSettings = PersistenceSettings()
            entry.persistenceSettings!!.ultraBeastPersistent = !entry.persistenceSettings!!.ultraBeastPersistent
            newValue = entry.persistenceSettings!!.ultraBeastPersistent
        }
        player.sendMessage(Text.literal("Ultra Beast persistent ${if (newValue) "enabled" else "disabled"} for $pokemonName."), false)
        return newValue
    }

    private fun toggleMythicalPersistent(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        player: ServerPlayerEntity
    ): Boolean {
        var newValue = false
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
            if (entry.persistenceSettings == null) entry.persistenceSettings = PersistenceSettings()
            entry.persistenceSettings!!.mythicalPersistent = !entry.persistenceSettings!!.mythicalPersistent
            newValue = entry.persistenceSettings!!.mythicalPersistent
        }
        player.sendMessage(Text.literal("Mythical persistent ${if (newValue) "enabled" else "disabled"} for $pokemonName."), false)
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