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

    private object Slots {
        const val TOGGLE_CUSTOM_SIZE = 40
        const val MIN_SIZE_DISPLAY = 13
        const val MAX_SIZE_DISPLAY = 22
        const val BACK_BUTTON = 49
    }

    private const val MIN_SIZE_BOUND = 0.0005f
    private const val MAX_SIZE_BOUND = 3000.0f

    data class SizeAdjustmentButton(
        val slotIndex: Int,
        val isMinSize: Boolean,
        val action: String, // "increase" or "decrease"
        val leftDelta: Float,
        val rightDelta: Float
    )

    private val minSizeButtons = listOf(
        SizeAdjustmentButton(11, true, "decrease", -0.1f, -0.5f),
        SizeAdjustmentButton(12, true, "decrease", -1.0f, -5.0f),
        SizeAdjustmentButton(14, true, "increase", 0.1f, 0.5f),
        SizeAdjustmentButton(15, true, "increase", 1.0f, 5.0f)
    )

    private val maxSizeButtons = listOf(
        SizeAdjustmentButton(20, false, "decrease", -0.1f, -0.5f),
        SizeAdjustmentButton(21, false, "decrease", -1.0f, -5.0f),
        SizeAdjustmentButton(23, false, "increase", 0.1f, 0.5f),
        SizeAdjustmentButton(24, false, "increase", 1.0f, 5.0f)
    )

    private val sizeAdjustmentButtons = (minSizeButtons + maxSizeButtons).associateBy { it.slotIndex }

    fun openSizeEditorGui(
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

        val layout = generateSizeEditorLayout(selectedEntry)
        spawnerGuisOpen[spawnerPos] = player

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
            Slots.TOGGLE_CUSTOM_SIZE -> {
                toggleAllowCustomSize(spawnerPos, pokemonName, formName, player, additionalAspects)
            }
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                player.sendMessage(Text.literal("Returning to Edit Pokémon menu."), false)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            else -> {
                sizeAdjustmentButtons[slotIndex]?.let { button ->
                    val delta = when (context.clickType) {
                        ClickType.LEFT -> button.leftDelta
                        ClickType.RIGHT -> button.rightDelta
                        else -> 0f
                    }
                    if (delta != 0f) {
                        adjustSize(spawnerPos, pokemonName, formName, player, button.isMinSize, delta, additionalAspects)
                    }
                }
            }
        }
    }

    private fun handleClose(
        inventory: Inventory,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        spawnerGuisOpen.remove(spawnerPos)
    }

    private fun generateSizeEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val sizeSettings = entry.sizeSettings

        (minSizeButtons + maxSizeButtons).forEach { button ->
            layout[button.slotIndex] = createSizeAdjustmentButton(button, sizeSettings, entry)
        }

        layout[Slots.MIN_SIZE_DISPLAY] = createCurrentValueHead("Current Min Size", "Min Size", sizeSettings.minSize)
        layout[Slots.MAX_SIZE_DISPLAY] = createCurrentValueHead("Current Max Size", "Max Size", sizeSettings.maxSize)

        layout[Slots.TOGGLE_CUSTOM_SIZE] = createToggleCustomSizeButton(sizeSettings.allowCustomSize)
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    private fun createSizeAdjustmentButton(
        button: SizeAdjustmentButton,
        sizeSettings: SizeSettings,
        entry: PokemonSpawnEntry
    ): ItemStack {
        val isMinSize = button.isMinSize
        val currentSize = if (isMinSize) sizeSettings.minSize else sizeSettings.maxSize
        val label = if (isMinSize) "Min Size" else "Max Size"
        val actionText = if (button.action == "increase") "Increase" else "Decrease"
        val deltaTextLeft = "%.1f".format(button.leftDelta)
        val deltaTextRight = "%.1f".format(button.rightDelta)
        val itemName = "$actionText $label"

        val lore = listOf(
            "Current $label: %.2f".format(currentSize),
            "Left-click: $actionText by $deltaTextLeft",
            "Right-click: $actionText by $deltaTextRight"
        )

        val textureValue = if (button.action == "increase") {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        }

        return CustomGui.createPlayerHeadButton(
            itemName.replace(" ", ""),
            Text.literal(itemName).styled { it.withColor(if (isMinSize) Formatting.GREEN else Formatting.BLUE).withBold(true) },
            lore.map { Text.literal(it) },
            textureValue
        )
    }

    private fun createCurrentValueHead(title: String, label: String, value: Float): ItemStack {
        return CustomGui.createPlayerHeadButton(
            title.replace(" ", ""),
            Text.literal(title).styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(Text.literal("§a$label: §f%.2f".format(value))),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZkZDg5MTlmZThmNzUwN2I0NjQxYmYzYWE3MmIwNTZlMDg1N2NjMjAyYThlNWViNjZjOWMyMWFhNzNjMzg3NiJ9fX0="
        )
    }

    private fun createToggleCustomSizeButton(allowCustomSize: Boolean): ItemStack {
        val textureValue = if (allowCustomSize) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        }

        return CustomGui.createPlayerHeadButton(
            "ToggleCustomSizes",
            Text.literal("Allow Custom Sizes: ${if (allowCustomSize) "ON" else "OFF"}").styled {
                it.withColor(if (allowCustomSize) Formatting.GREEN else Formatting.RED).withBold(true)
            },
            listOf(Text.literal("§eClick to toggle")),
            textureValue
        )
    }

    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.BLUE).withBold(true) },
            listOf(Text.literal("§7Click to return to the previous menu.")),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
            CustomGui.setItemLore(this, listOf(" "))
        }
    }

    private fun toggleAllowCustomSize(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        additionalAspects: Set<String> = emptySet()
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(
            spawnerPos,
            pokemonName,
            formName,
            additionalAspects
        ) { entry ->
            entry.sizeSettings.allowCustomSize = !entry.sizeSettings.allowCustomSize
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle custom size setting."), false)
            return
        }

        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)?.let { entry ->
            val toggleButton = createToggleCustomSizeButton(entry.sizeSettings.allowCustomSize)
            updateSingleItem(player, Slots.TOGGLE_CUSTOM_SIZE, toggleButton)

            val status = if (entry.sizeSettings.allowCustomSize) "enabled" else "disabled"
            player.sendMessage(Text.literal("Custom size settings $status for ${entry.pokemonName}."), false)
            logDebug(
                "Toggled allowCustomSize to $status for ${entry.pokemonName} (${entry.formName ?: "Standard"}) with aspects ${additionalAspects.joinToString(", ")} at spawner $spawnerPos.",
                "cobblespawners"
            )
        }
    }

    private fun adjustSize(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        isMinSize: Boolean,
        delta: Float,
        additionalAspects: Set<String> = emptySet()
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(
            spawnerPos,
            pokemonName,
            formName,
            additionalAspects
        ) { entry ->
            val currentSize = if (isMinSize) entry.sizeSettings.minSize else entry.sizeSettings.maxSize
            val newSize = currentSize + delta
            val minBound = if (isMinSize) MIN_SIZE_BOUND else entry.sizeSettings.minSize
            val maxBound = if (isMinSize) entry.sizeSettings.maxSize else MAX_SIZE_BOUND
            val adjustedSize = newSize.coerceIn(minBound, maxBound)
            val roundedSize = roundToTwoDecimals(adjustedSize)
            if (isMinSize) {
                entry.sizeSettings.minSize = roundedSize
            } else {
                entry.sizeSettings.maxSize = roundedSize
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to adjust size."), false)
            return
        }

        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)?.let { entry ->
            val sizeSettings = entry.sizeSettings
            val displaySlot = if (isMinSize) Slots.MIN_SIZE_DISPLAY else Slots.MAX_SIZE_DISPLAY
            val currentSize = if (isMinSize) sizeSettings.minSize else sizeSettings.maxSize
            val displayItem = createCurrentValueHead("Current ${if (isMinSize) "Min" else "Max"} Size", "${if (isMinSize) "Min" else "Max"} Size", currentSize)
            updateSingleItem(player, displaySlot, displayItem)

            val buttonsToUpdate = if (isMinSize) minSizeButtons else maxSizeButtons
            buttonsToUpdate.forEach { button ->
                val buttonItem = createSizeAdjustmentButton(button, sizeSettings, entry)
                updateSingleItem(player, button.slotIndex, buttonItem)
            }

            logger.info(
                "Adjusted ${if (isMinSize) "min" else "max"} size for ${entry.pokemonName} (${entry.formName ?: "Standard"}) " +
                        "with aspects ${additionalAspects.joinToString(", ")} at spawner $spawnerPos to $currentSize."
            )
            player.sendMessage(
                Text.literal("Set ${if (isMinSize) "minimum" else "maximum"} size to $currentSize for ${entry.pokemonName}."),
                false
            )
        }
    }

    private fun updateSingleItem(player: ServerPlayerEntity, slot: Int, item: ItemStack) {
        val screenHandler = player.currentScreenHandler
        if (slot < screenHandler.slots.size) {
            screenHandler.slots[slot].stack = item
            screenHandler.sendContentUpdates()
        }
    }

    private fun roundToTwoDecimals(value: Float): Float {
        return (value * 100).roundToInt() / 100f
    }
}