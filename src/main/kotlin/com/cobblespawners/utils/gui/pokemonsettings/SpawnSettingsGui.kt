// File: SpawnSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.everlastingutils.utils.logDebug
import com.cobblespawners.utils.*
import com.cobblespawners.utils.gui.PokemonEditSubGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.ClickType
import org.slf4j.LoggerFactory

object SpawnSettingsGui {
    private val logger = LoggerFactory.getLogger(SpawnSettingsGui::class.java)

    // Data class for chance buttons
    data class ChanceButton(
        val slotIndex: Int,
        val action: String, // "increase" or "decrease"
        val leftDelta: Double,
        val rightDelta: Double
    )

    // Define the chance buttons with their respective increments/decrements
    private val chanceButtonMap = mapOf(
        // Spawn Chance Buttons
        10 to ChanceButton(10, "decrease", -0.01, -0.05),
        11 to ChanceButton(11, "decrease", -0.1, -0.5),
        12 to ChanceButton(12, "decrease", -1.0, -5.0),
        14 to ChanceButton(14, "increase", 0.01, 0.05),
        15 to ChanceButton(15, "increase", 0.1, 0.5),
        16 to ChanceButton(16, "increase", 1.0, 5.0)
    )

    // Data class for level buttons
    data class LevelButton(
        val slotIndex: Int,
        val isMinLevel: Boolean, // true for Min Level, false for Max Level
        val action: String // "increase" or "decrease"
    )

    // Define the level adjustment buttons
    private val levelButtonMap = mapOf(
        // Min Level Adjustment Buttons
        28 to LevelButton(28, true, "decrease"),
        30 to LevelButton(30, true, "increase"),
        // Max Level Adjustment Buttons
        32 to LevelButton(32, false, "decrease"),
        34 to LevelButton(34, false, "increase")
    )

    // Display slots for current values and toggle button (moved one slot over)
    private object DisplaySlots {
        const val SPAWN_CHANCE = 13
        const val SPAWN_CHANCE_TYPE = 22  // Moved over one slot (was 21)
        const val MIN_LEVEL = 29
        const val MAX_LEVEL = 33
        const val BACK_BUTTON = 49
    }

    /**
     * Opens the Spawn Settings Editor GUI for a specific Pokémon and form.
     */
    fun openSpawnShinyEditorGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        val standardFormName = formName ?: "Standard"
        // Determine effective aspects: if none were provided, fetch from the first matching entry.
        val effectiveAspects = if (additionalAspects.isEmpty()) {
            CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, standardFormName)?.aspects ?: emptySet()
        } else {
            additionalAspects
        }
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, standardFormName, effectiveAspects)

        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '$standardFormName' and aspects " +
                        "${if (effectiveAspects.isEmpty()) "none" else effectiveAspects.joinToString(", ")} not found in spawner."),
                false
            )
            logDebug("Pokémon '$pokemonName' with form '$standardFormName' with aspects ${effectiveAspects.joinToString(", ")} not found in spawner at $spawnerPos.", "cobblespawners")
            return
        }

        val layout = generateSpawnEditorLayout(selectedEntry)
        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (effectiveAspects.isNotEmpty()) effectiveAspects.joinToString(", ") else ""
        val guiTitle = if (aspectsDisplay.isNotEmpty())
            "Edit Spawn Settings for $pokemonName (${selectedEntry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Edit Spawn Settings for $pokemonName (${selectedEntry.formName ?: "Standard"})"

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, effectiveAspects) },
            { handleClose(it, spawnerPos, pokemonName, formName) }
        )
    }

    /**
     * Handles button interactions in the GUI.
     */
    private fun handleInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        val slotIndex = context.slotIndex
        val clickType = context.clickType

        // Handle Chance Buttons
        chanceButtonMap[slotIndex]?.let { button ->
            val delta = when (clickType) {
                ClickType.LEFT -> button.leftDelta
                ClickType.RIGHT -> button.rightDelta
                else -> 0.0
            }
            if (delta != 0.0) {
                updateSpawnChance(spawnerPos, pokemonName, formName, delta, player)
            }
            return
        }

        // Handle Level Adjustment Buttons
        levelButtonMap[slotIndex]?.let { button ->
            val delta = when (clickType) {
                ClickType.LEFT -> if (button.action == "increase") 1 else -1
                ClickType.RIGHT -> if (button.action == "increase") 5 else -5
                else -> 0
            }
            if (delta != 0) {
                adjustLevel(spawnerPos, pokemonName, formName, player, button.isMinLevel, delta)
            }
            return
        }

        // Handle Spawn Chance Type Toggle Button
        if (slotIndex == DisplaySlots.SPAWN_CHANCE_TYPE) {
            toggleSpawnChanceType(spawnerPos, pokemonName, formName, player)
            return
        }

        // Handle Back Button
        if (slotIndex == DisplaySlots.BACK_BUTTON) {
            CustomGui.closeGui(player)
            player.sendMessage(Text.literal("Returning to Edit Pokémon menu"), false)
            PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
        }
    }

    /**
     * Handles GUI close event.
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
     * Generates the layout for the Spawn Editor GUI.
     */
    private fun generateSpawnEditorLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }

        // Add chance buttons as head items
        chanceButtonMap.forEach { (slot, button) ->
            layout[slot] = createChanceButtonHead(button, selectedEntry)
        }

        // Add level adjustment buttons as head items
        levelButtonMap.forEach { (slot, button) ->
            layout[slot] = createLevelAdjustmentHead(button, selectedEntry)
        }

        // Add current value displays as head items
        layout[DisplaySlots.SPAWN_CHANCE] = createCurrentValueHead("Current Spawn Chance", "Spawn Chance", selectedEntry.spawnChance, "%")
        layout[DisplaySlots.MIN_LEVEL] = createCurrentValueHead("Current Min Level", "Min Level", selectedEntry.minLevel.toDouble(), "")
        layout[DisplaySlots.MAX_LEVEL] = createCurrentValueHead("Current Max Level", "Max Level", selectedEntry.maxLevel.toDouble(), "")

        // Add Toggle Spawn Chance Type button (head item)
        layout[DisplaySlots.SPAWN_CHANCE_TYPE] = createToggleSpawnChanceTypeHead(selectedEntry)

        // Add Back Button as a head item
        layout[DisplaySlots.BACK_BUTTON] = createBackHead()

        // Fill remaining slots with filler stained glass panes
        val usedSlots = chanceButtonMap.keys +
                levelButtonMap.keys +
                listOf(DisplaySlots.SPAWN_CHANCE, DisplaySlots.SPAWN_CHANCE_TYPE, DisplaySlots.MIN_LEVEL, DisplaySlots.MAX_LEVEL, DisplaySlots.BACK_BUTTON)
        for (i in 0 until 54) {
            if (i !in usedSlots) {
                layout[i] = createFillerPane()
            }
        }
        return layout
    }

    /**
     * Creates a filler pane using a gray stained glass pane.
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    /**
     * Creates a current value display as a head item.
     */
    private fun createCurrentValueHead(title: String, label: String, value: Double, unit: String): ItemStack {
        val displayValue = if (unit == "%") "%.2f".format(value) else "${value.toInt()}"
        return CustomGui.createPlayerHeadButton(
            title.replace(" ", ""),
            Text.literal(title).styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(Text.literal("§a$label: §f$displayValue$unit")),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZkZDg5MTlmZThmNzUwN2I0NjQxYmYzYWE3MmIwNTZlMDg1N2NjMjAyYThlNWViNjZjOWMyMWFhNzNjMzg3NiJ9fX0="
        )
    }

    /**
     * Creates a Chance Button head item.
     */
    private fun createChanceButtonHead(button: ChanceButton, selectedEntry: PokemonSpawnEntry): ItemStack {
        val (_, action, leftDelta, rightDelta) = button
        val itemName = "${action.capitalize()} Spawn Chance"
        val currentChance = selectedEntry.spawnChance
        return CustomGui.createPlayerHeadButton(
            "ChanceButton${button.slotIndex}",
            Text.literal(itemName).styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent Spawn Chance: §f${"%.2f".format(currentChance)}%"),
                Text.literal("§eLeft-click: ${if (leftDelta > 0) "+" else ""}${"%.2f".format(leftDelta)}%"),
                Text.literal("§eRight-click: ${if (rightDelta > 0) "+" else ""}${"%.2f".format(rightDelta)}%")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjZmZjBhYTQ4NTQ0N2JiOGRjZjQ1OTkyM2I0OWY5MWM0M2IwNDBiZDU2ZTYzMTVkYWE4YjZmODNiNGMzZWI1MSJ9fX0="
        )
    }

    /**
     * Creates a Level Adjustment Button head item.
     */
    private fun createLevelAdjustmentHead(button: LevelButton, selectedEntry: PokemonSpawnEntry): ItemStack {
        val (_, isMinLevel, action) = button
        val levelType = if (isMinLevel) "Min Level" else "Max Level"
        val itemName = "${action.capitalize()} $levelType"
        val currentLevel = if (isMinLevel) selectedEntry.minLevel else selectedEntry.maxLevel
        return CustomGui.createPlayerHeadButton(
            "LevelButton${button.slotIndex}",
            Text.literal(itemName).styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent $levelType: §f$currentLevel"),
                Text.literal("§eLeft-click: Adjust by ${if (action == "increase") "+" else ""}1"),
                Text.literal("§eRight-click: Adjust by ${if (action == "increase") "+" else ""}5")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E4Yjg3ZTQ2Y2ZlOGEyZGMzNTI1YzFjNjdkOGE2OWEyNWZkMGE3ZjcyNGE2ZmE5MTFhZDc0YWRiNmQ4MmMyIn19fQ=="
        )
    }

    /**
     * Creates the Toggle Spawn Chance Type head item.
     */
    private fun createToggleSpawnChanceTypeHead(selectedEntry: PokemonSpawnEntry): ItemStack {
        val typeName = when (selectedEntry.spawnChanceType) {
            SpawnChanceType.COMPETITIVE -> "Competitive"
            SpawnChanceType.INDEPENDENT -> "Independent"
        }
        val description = "Competitive: Spawn chance is calculated relative to other entries in the spawner. Independent: Uses an absolute percentage chance regardless of other entries."
        return CustomGui.createPlayerHeadButton(
            "ToggleSpawnChanceType",
            Text.literal("Spawn Chance Type: $typeName").styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§eClick to toggle chance type"),
                Text.literal("§7$description")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdkZmE4ZjBjYzkxYjVkODE0YTE4NWM1ZTgwYjVkYzVjYWMxOTgxMTNiMWU5ZWQ4NzM4NmM5OTgzMzk5OWYifX19"
        )
    }



    /**
     * Creates the Back Button head item.
     */
    private fun createBackHead(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE) },
            listOf(Text.literal("§eClick to return")),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )
    }

    /**
     * Updates the spawn chance value for the selected Pokémon.
     */
    private fun updateSpawnChance(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        delta: Double,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.spawnChance = (entry.spawnChance + delta).coerceIn(0.0, 100.0)
        } ?: run {
            player.sendMessage(Text.literal("Failed to update spawn chance."), false)
            return
        }

        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)?.let { updatedEntry ->
            updateSingleItem(player, DisplaySlots.SPAWN_CHANCE, createCurrentValueHead("Current Spawn Chance", "Spawn Chance", updatedEntry.spawnChance, "%"))
            chanceButtonMap.forEach { (slot, button) ->
                updateSingleItem(player, slot, createChanceButtonHead(button, updatedEntry))
            }
            logDebug("Updated spawnChance to ${updatedEntry.spawnChance}% for $pokemonName (${formName ?: "Standard"}) at spawner $spawnerPos.", "cobblespawners")
            player.sendMessage(Text.literal("Spawn Chance set to ${"%.2f".format(updatedEntry.spawnChance)}% for $pokemonName."), false)
        }
    }

    /**
     * Toggles the Spawn Chance Type for the selected Pokémon.
     */
    private fun toggleSpawnChanceType(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.spawnChanceType = when (entry.spawnChanceType) {
                SpawnChanceType.COMPETITIVE -> SpawnChanceType.INDEPENDENT
                SpawnChanceType.INDEPENDENT -> SpawnChanceType.COMPETITIVE
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle spawn chance type."), false)
            return
        }

        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)?.let { updatedEntry ->
            updateSingleItem(player, DisplaySlots.SPAWN_CHANCE_TYPE, createToggleSpawnChanceTypeHead(updatedEntry))
            logDebug("Toggled spawnChanceType to ${updatedEntry.spawnChanceType} for $pokemonName (${formName ?: "Standard"}) at spawner $spawnerPos.", "cobblespawners")
            player.sendMessage(Text.literal("Spawn Chance Type set to ${updatedEntry.spawnChanceType} for $pokemonName."), false)
        }
    }

    /**
     * Adjusts the Min or Max Level.
     */
    private fun adjustLevel(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        isMinLevel: Boolean,
        delta: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            if (isMinLevel) {
                val newMin = (entry.minLevel + delta).coerceAtLeast(1).coerceAtMost(entry.maxLevel)
                entry.minLevel = newMin
            } else {
                val newMax = (entry.maxLevel + delta).coerceAtLeast(entry.minLevel).coerceAtMost(100)
                entry.maxLevel = newMax
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to adjust level."), false)
            return
        }

        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)?.let { updatedEntry ->
            val displaySlot = if (isMinLevel) DisplaySlots.MIN_LEVEL else DisplaySlots.MAX_LEVEL
            val displayValue = if (isMinLevel) updatedEntry.minLevel.toDouble() else updatedEntry.maxLevel.toDouble()
            val displayType = if (isMinLevel) "Min Level" else "Max Level"
            updateSingleItem(player, displaySlot, createCurrentValueHead("Current $displayType", displayType, displayValue, ""))
            levelButtonMap.filter { it.value.isMinLevel == isMinLevel }.forEach { (slot, button) ->
                updateSingleItem(player, slot, createLevelAdjustmentHead(button, updatedEntry))
            }
            logger.info("Adjusted ${if (isMinLevel) "min" else "max"} level for $pokemonName (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos to ${if (isMinLevel) updatedEntry.minLevel else updatedEntry.maxLevel}.")
            player.sendMessage(Text.literal("Set ${if (isMinLevel) "Min" else "Max"} Level to ${if (isMinLevel) updatedEntry.minLevel else updatedEntry.maxLevel} for $pokemonName."), false)
        }
    }

    /**
     * Updates a single slot in the GUI.
     */
    private fun updateSingleItem(player: ServerPlayerEntity, slot: Int, item: ItemStack) {
        val screenHandler = player.currentScreenHandler
        if (slot < screenHandler.slots.size) {
            screenHandler.slots[slot].stack = item
            screenHandler.sendContentUpdates()
        }
    }
}
