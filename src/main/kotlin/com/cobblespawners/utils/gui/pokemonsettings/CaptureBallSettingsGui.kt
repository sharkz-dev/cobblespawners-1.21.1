// File: CaptureBallSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblemon.mod.common.item.PokeBallItem
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

object CaptureBallSettingsGui {
    private const val ITEMS_PER_PAGE = 45
    private val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()

    // GUI slot configuration
    private object Slots {
        const val PREV_PAGE = 45
        const val BACK_BUTTON = 49
        const val NEXT_PAGE = 53
    }

    // Texture constants
    private object Textures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    /**
     * Opens the Poké Ball selection GUI for editing selected Poké Balls.
     */
    fun openCaptureBallSettingsGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        val currentPage = playerPages[player] ?: 0
        val standardFormName = formName ?: "Standard"

        // Get the Pokémon entry
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(
            spawnerPos, pokemonName, standardFormName, additionalAspects
        )

        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '$standardFormName' and aspects ${if (additionalAspects.isEmpty()) "none" else additionalAspects.joinToString(", ")} not found in spawner."),
                false
            )
            return
        }

        val requiredPokeBalls = selectedEntry.captureSettings.requiredPokeBalls
        val availablePokeballs = getAvailablePokeballs()

        // Build the title including the aspects
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) additionalAspects.joinToString(", ") else ""
        val title = if (aspectsDisplay.isNotEmpty())
            "Select Required Poké Balls for $pokemonName (${selectedEntry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Select Required Poké Balls for $pokemonName (${selectedEntry.formName ?: "Standard"})"

        // Set the spawner GUI as open
        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        // Open the GUI
        CustomGui.openGui(
            player,
            title,
            generateFullGuiLayout(availablePokeballs, requiredPokeBalls, currentPage),
            { context -> handleButtonClick(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            {
                playerPages.remove(player)
                SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            }
        )
    }

    /**
     * Handles button clicks in the GUI.
     */
    private fun handleButtonClick(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        val currentPage = playerPages[player] ?: 0
        val availablePokeballs = getAvailablePokeballs()
        val startIndex = currentPage * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, availablePokeballs.size)

        when (context.slotIndex) {
            Slots.PREV_PAGE -> handlePreviousPage(player, spawnerPos, pokemonName, formName, currentPage)
            Slots.BACK_BUTTON -> handleBackButton(player, spawnerPos, pokemonName, formName, additionalAspects)
            Slots.NEXT_PAGE -> handleNextPage(player, spawnerPos, pokemonName, formName, currentPage, availablePokeballs, endIndex)
            else -> handlePokeballSelection(context, player, spawnerPos, pokemonName, formName)
        }
    }

    /**
     * Handles previous page navigation
     */
    private fun handlePreviousPage(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        currentPage: Int
    ) {
        if (currentPage > 0) {
            playerPages[player] = currentPage - 1
            refreshGuiItems(player, spawnerPos, pokemonName, formName)
        }
    }

    /**
     * Handles next page navigation
     */
    private fun handleNextPage(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        currentPage: Int,
        availablePokeballs: List<ItemStack>,
        endIndex: Int
    ) {
        if (endIndex < availablePokeballs.size) {
            playerPages[player] = currentPage + 1
            refreshGuiItems(player, spawnerPos, pokemonName, formName)
        }
    }

    /**
     * Handles back button click
     */
    private fun handleBackButton(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        CustomGui.closeGui(player)
        CaptureSettingsGui.openCaptureSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
    }

    /**
     * Handles Pokéball selection
     */
    private fun handlePokeballSelection(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        val clickedBall = context.clickedStack.item as? PokeBallItem ?: return
        val ballName = clickedBall.translationKey.split(".").last()

        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        val requiredPokeBalls = selectedEntry?.captureSettings?.requiredPokeBalls ?: listOf()

        // Toggle selection state
        val newPokeBalls = if (ballName in requiredPokeBalls) {
            removeEnchantmentGlint(context.clickedStack)
            requiredPokeBalls.minus(ballName)
        } else {
            addEnchantmentGlint(context.clickedStack)
            requiredPokeBalls.plus(ballName)
        }

        // Update the config
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.captureSettings.requiredPokeBalls = newPokeBalls
        }

        // Refresh the item
        updateItemLore(context.clickedStack, ballName in newPokeBalls)
        player.currentScreenHandler.setStackInSlot(context.slotIndex, 0, context.clickedStack)
        player.currentScreenHandler.sendContentUpdates()
    }

    /**
     * Generates the full layout for the GUI, including buttons and Poké Balls.
     */
    private fun generateFullGuiLayout(
        availablePokeballs: List<ItemStack>,
        selectedPokeBalls: List<String>,
        page: Int
    ): List<ItemStack> {
        val layout = generatePokeballItemsForGui(availablePokeballs, selectedPokeBalls, page).toMutableList()

        // Add navigation buttons
        layout[Slots.PREV_PAGE] = if (page > 0)
            createNavigationButton("Previous Page", Formatting.GREEN, "Click to go to the previous page", Textures.PREV_PAGE)
        else
            createFillerPane()

        layout[Slots.BACK_BUTTON] = createBackButton()

        layout[Slots.NEXT_PAGE] = if ((page + 1) * ITEMS_PER_PAGE < availablePokeballs.size)
            createNavigationButton("Next Page", Formatting.GREEN, "Click to go to the next page", Textures.NEXT_PAGE)
        else
            createFillerPane()

        return layout
    }

    /**
     * Generates the GUI items for Poké Balls.
     */
    private fun generatePokeballItemsForGui(
        availablePokeballs: List<ItemStack>,
        selectedPokeBalls: List<String>,
        page: Int
    ): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val start = page * ITEMS_PER_PAGE
        val end = minOf(start + ITEMS_PER_PAGE, availablePokeballs.size)

        for (i in start until end) {
            val pokeball = availablePokeballs[i].copy() // Use a copy to prevent modifying the original
            val ballName = pokeball.item.translationKey.split(".").last()
            val isSelected = ballName in selectedPokeBalls

            if (isSelected) {
                addEnchantmentGlint(pokeball)
            }
            updateItemLore(pokeball, isSelected)
            layout[i - start] = pokeball
        }

        return layout
    }

    /**
     * Refreshes the GUI items without closing the GUI.
     */
    private fun refreshGuiItems(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        val requiredPokeBalls = selectedEntry?.captureSettings?.requiredPokeBalls ?: listOf()
        val availablePokeballs = getAvailablePokeballs()
        val currentPage = playerPages[player] ?: 0

        CustomGui.refreshGui(
            player,
            generateFullGuiLayout(availablePokeballs, requiredPokeBalls, currentPage)
        )
    }

    /**
     * Gets all available Poké Balls from the registry.
     */
    private fun getAvailablePokeballs(): List<ItemStack> {
        return Registries.ITEM.stream()
            .filter { it is PokeBallItem }
            .map { ItemStack(it) }
            .toList()
    }

    /**
     * Creates a navigation button for the GUI.
     */
    private fun createNavigationButton(
        text: String,
        color: Formatting,
        description: String,
        textureValue: String
    ): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "${text.replace(" ", "")}Button",
            Text.literal(text).styled { it.withColor(color) },
            listOf(Text.literal(description).styled { it.withColor(Formatting.GRAY) }),
            textureValue
        )
    }

    /**
     * Creates a back button for the GUI.
     */
    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE) },
            listOf(Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY) }),
            Textures.BACK
        )
    }

    /**
     * Creates a filler pane for empty slots.
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    /**
     * Adds an enchantment glint to an ItemStack.
     */
    private fun addEnchantmentGlint(itemStack: ItemStack) {
        itemStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    /**
     * Removes an enchantment glint from an ItemStack.
     */
    private fun removeEnchantmentGlint(itemStack: ItemStack) {
        itemStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false)
    }

    /**
     * Updates the lore of an ItemStack to show selection status.
     */
    private fun updateItemLore(itemStack: ItemStack, isSelected: Boolean) {
        val ballName = itemStack.item.translationKey.split(".").last()
        val lore = listOf(
            Text.literal("Status: ${if (isSelected) "Selected" else "Not Selected"}").styled {
                it.withColor(if (isSelected) Formatting.LIGHT_PURPLE else Formatting.RED)
            }
        )
        CustomGui.setItemLore(itemStack, lore.map { it.string })
    }
}