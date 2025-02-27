// File: IVSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.*
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.blanketutils.utils.logDebug
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

object IVSettingsGui {
    private val logger = LoggerFactory.getLogger(IVSettingsGui::class.java)

    // Slot configuration
    private const val TOGGLE_CUSTOM_IVS_SLOT = 31
    private const val BACK_BUTTON_SLOT = 49

    // Stat button configuration
    private data class StatButton(
        val slot: Int,
        val name: String,
        val textureValue: String,
        val getter: (IVSettings) -> Int,
        val setter: (IVSettings, Int) -> Unit
    )

    // Map of stat buttons with their configurations
    private val statButtons = listOf(
        StatButton(
            0, "HP Min",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0=",
            { it.minIVHp },
            { ivs, value -> ivs.minIVHp = value.coerceIn(0, 31) }
        ),
        StatButton(
            1, "HP Max",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0=",
            { it.maxIVHp },
            { ivs, value -> ivs.maxIVHp = value.coerceIn(0, 31) }
        ),
        StatButton(
            2, "Attack Min",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0=",
            { it.minIVAttack },
            { ivs, value -> ivs.minIVAttack = value.coerceIn(0, 31) }
        ),
        StatButton(
            3, "Attack Max",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0=",
            { it.maxIVAttack },
            { ivs, value -> ivs.maxIVAttack = value.coerceIn(0, 31) }
        ),
        StatButton(
            4, "Defense Min",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0=",
            { it.minIVDefense },
            { ivs, value -> ivs.minIVDefense = value.coerceIn(0, 31) }
        ),
        StatButton(
            5, "Defense Max",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0=",
            { it.maxIVDefense },
            { ivs, value -> ivs.maxIVDefense = value.coerceIn(0, 31) }
        ),
        StatButton(
            6, "Special Attack Min",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0=",
            { it.minIVSpecialAttack },
            { ivs, value -> ivs.minIVSpecialAttack = value.coerceIn(0, 31) }
        ),
        StatButton(
            7, "Special Attack Max",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0=",
            { it.maxIVSpecialAttack },
            { ivs, value -> ivs.maxIVSpecialAttack = value.coerceIn(0, 31) }
        ),
        StatButton(
            8, "Special Defense Min",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0=",
            { it.minIVSpecialDefense },
            { ivs, value -> ivs.minIVSpecialDefense = value.coerceIn(0, 31) }
        ),
        StatButton(
            9, "Special Defense Max",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0=",
            { it.maxIVSpecialDefense },
            { ivs, value -> ivs.maxIVSpecialDefense = value.coerceIn(0, 31) }
        ),
        StatButton(
            10, "Speed Min",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0=",
            { it.minIVSpeed },
            { ivs, value -> ivs.minIVSpeed = value.coerceIn(0, 31) }
        ),
        StatButton(
            11, "Speed Max",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0=",
            { it.maxIVSpeed },
            { ivs, value -> ivs.maxIVSpeed = value.coerceIn(0, 31) }
        )
    )

    // Lookup map for stat buttons by slot
    private val statButtonsBySlot = statButtons.associateBy { it.slot }

    // Lookup map for stat buttons by name
    private val statButtonsByName = statButtons.associateBy { it.name }

    /**
     * Opens the IV editor GUI for a specific Pokémon and form.
     *
     * @param player The player opening the GUI.
     * @param spawnerPos The spawner position.
     * @param pokemonName The Pokémon name.
     * @param formName The form of the Pokémon (or null for standard).
     * @param additionalAspects A set of additional aspects (for example, "shiny") to include in display.
     */
    fun openIVEditorGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)

        if (entry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner."),
                false
            )
            return
        }

        val layout = generateIVEditorLayout(entry)
        spawnerGuisOpen[spawnerPos] = player

        // Build the title including the aspects
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) additionalAspects.joinToString(", ") else ""
        val guiTitle = if (aspectsDisplay.isNotEmpty())
            "Edit IVs for ${entry.pokemonName} (${entry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Edit IVs for ${entry.pokemonName} (${entry.formName ?: "Standard"})"

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            { context -> handleInteraction(context, player, spawnerPos, entry.pokemonName, entry.formName, additionalAspects) },
            { handleClose(it, spawnerPos, entry.pokemonName, entry.formName) }
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
        additionalAspects: Set<String>
    ) {
        val clickedStack = context.clickedStack
        val clickedName = clickedStack.name?.string ?: ""
        val slotIndex = context.slotIndex

        // Handle stat buttons
        statButtonsBySlot[slotIndex]?.let { button ->
            val delta = when (context.clickType) {
                ClickType.LEFT -> -1
                ClickType.RIGHT -> 1
                else -> 0
            }
            if (delta != 0) {
                updateIVValue(spawnerPos, pokemonName, formName, button, delta, player)
            }
            return
        }

        // Handle toggle button
        if (slotIndex == TOGGLE_CUSTOM_IVS_SLOT) {
            toggleAllowCustomIvs(spawnerPos, pokemonName, formName, player)
            return
        }

        // Handle back button
        if (slotIndex == BACK_BUTTON_SLOT) {
            CustomGui.closeGui(player)
            player.sendMessage(Text.literal("Returning to Edit Pokémon menu"), false)
            PokemonEditSubGui.openPokemonEditSubGui(
                player, spawnerPos, pokemonName, formName, additionalAspects
            )
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
     * Generates the GUI layout for editing IV values.
     */
    private fun generateIVEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val ivSettings = entry.ivSettings

        // Add stat buttons
        statButtons.forEach { button ->
            layout[button.slot] = createIVStatButton(button, ivSettings)
        }

        // Add toggle and back buttons
        layout[TOGGLE_CUSTOM_IVS_SLOT] = createToggleCustomIvsButton(ivSettings.allowCustomIvs)
        layout[BACK_BUTTON_SLOT] = createBackButton()

        return layout
    }

    /**
     * Creates a button for IV stat adjustment
     */
    private fun createIVStatButton(button: StatButton, ivSettings: IVSettings): ItemStack {
        val currentValue = button.getter(ivSettings)

        return CustomGui.createPlayerHeadButton(
            button.name.replace(" ", "") + "Head",
            Text.literal(button.name).styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent Value:").styled { it.withColor(Formatting.GREEN) },
                Text.literal("§7Value: §f$currentValue"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            button.textureValue
        )
    }

    /**
     * Creates a toggle button for custom IVs.
     */
    private fun createToggleCustomIvsButton(isEnabled: Boolean): ItemStack {
        val textureValue = if (isEnabled) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        }

        return CustomGui.createPlayerHeadButton(
            "ToggleCustomIVs",
            Text.literal("Allow Custom IVs: ${if (isEnabled) "ON" else "OFF"}").styled {
                it.withColor(if (isEnabled) Formatting.GREEN else Formatting.RED).withBold(true)
            },
            listOf(Text.literal("§eClick to toggle")),
            textureValue
        )
    }

    /**
     * Creates a Back button.
     */
    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE).withBold(false) },
            listOf(Text.literal("§7Return to the previous menu")),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )
    }

    /**
     * Creates a filler pane.
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    /**
     * Updates the IV value for a given stat button.
     */
    private fun updateIVValue(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        button: StatButton,
        delta: Int,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            val iv = entry.ivSettings
            val currentValue = button.getter(iv)
            button.setter(iv, currentValue + delta)
        } ?: run {
            player.sendMessage(Text.literal("Failed to update IV value."), false)
            return
        }

        // Refresh just the modified button
        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)?.let { entry ->
            val updatedButton = createIVStatButton(button, entry.ivSettings)
            updateSingleItem(player, button.slot, updatedButton)

            logDebug(
                "Updated IV ${button.name} for ${entry.pokemonName} (${entry.formName ?: "Standard"}) at spawner $spawnerPos.",
                "cobblespawners"
            )
        }
    }

    /**
     * Toggles the allowCustomIvs flag.
     */
    private fun toggleAllowCustomIvs(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.ivSettings.allowCustomIvs = !entry.ivSettings.allowCustomIvs
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle allowCustomIvs."), false)
            return
        }

        // Update just the toggle button
        CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)?.let { entry ->
            val toggleButton = createToggleCustomIvsButton(entry.ivSettings.allowCustomIvs)
            updateSingleItem(player, TOGGLE_CUSTOM_IVS_SLOT, toggleButton)

            logDebug(
                "Toggled allowCustomIvs for ${entry.pokemonName} (${entry.formName ?: "Standard"}) at spawner $spawnerPos.",
                "cobblespawners"
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
}