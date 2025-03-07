// File: SearchGui.kt
package com.cobblespawners.utils.gui

import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory

object SearchGui {
    private val logger = LoggerFactory.getLogger(SearchGui::class.java)

    // Texture for cancel button head
    private const val CANCEL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    // Texture for search button head (player head)
    private const val SEARCH_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="

    /**
     * Opens a search GUI using the fully modular anvil interface.
     *
     * @param player The player to open the GUI for
     * @param spawnerPos The position of the spawner being configured
     */
    fun openSortGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        // Track that this spawner's GUI is open
        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        // Create the cancel button
        val cancelButton = createCancelButton()
        // Create a stained glass item to block the right input slot
        val blockedInput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
        // Create a placeholder for the output when nothing is typed
        val placeholderOutput = createPlaceholderOutput()

        // Open the fully modular anvil GUI
        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "pokemon_search_${spawnerPos.toShortString()}",
            title = "Search Pokémon",
            initialText = "",  // Start with empty text field
            leftItem = cancelButton,
            rightItem = blockedInput, // Block the right slot
            resultItem = placeholderOutput,  // Initially display the placeholder
            onLeftClick = { context ->
                // Cancel button was clicked
                player.sendMessage(Text.literal("§7Search cancelled."), false)
                goBackToPreviousGui(player, spawnerPos)
            },
            onRightClick = null,  // No interaction on the blocked slot
            onResultClick = { context ->
                // Only process click if there's text entered; otherwise, do nothing.
                if (context.handler.currentText.isNotBlank()) {
                    handleSearch(player, spawnerPos, context.handler.currentText)
                }
            },
            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                if (text.isNotEmpty()) {
                    val updatedSearchButton = createDynamicSearchButton(text)
                    handler?.updateSlot(2, updatedSearchButton)
                } else {
                    handler?.updateSlot(2, createPlaceholderOutput())
                }
            },
            onClose = {
                // If the GUI is closed via ESC key, just go back.
                player.server.execute {
                    if (player.currentScreenHandler !is FullyModularAnvilScreenHandler) {
                        goBackToPreviousGui(player, spawnerPos)
                    }
                }
            }
        )

        // Force clear the text field after GUI opens to avoid any leakage.
        player.server.execute {
            (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.clearTextField()
        }

        player.sendMessage(Text.literal("Enter a Pokémon name to search, or click the X to cancel..."), false)
    }

    /**
     * Creates a player head cancel button.
     * Note: The title set here will be removed in the anvil GUI to ensure it doesn't auto-fill the text field.
     */
    private fun createCancelButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "CancelButton",
            title = Text.literal("§cCancel Search").styled { it.withBold(true).withItalic(false) },
            lore = listOf(
                Text.literal("§7Click to return to Pokémon selection"),
                Text.literal("§7without searching")
            ),
            textureValue = CANCEL_TEXTURE
        )
    }

    /**
     * Creates a dynamic search button as a player head with the query text.
     * This button appears in the output slot when text is entered.
     */
    private fun createDynamicSearchButton(searchText: String): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "SearchButton",
            title = Text.literal("§aSearch: §f$searchText").styled { it.withBold(true).withItalic(false) },
            lore = listOf(
                Text.literal("§aClick to search for this term"),
                Text.literal("§7Enter different text to change search")
            ),
            textureValue = SEARCH_TEXTURE
        )
    }

    /**
     * Creates a placeholder output using stained glass for when no search text is entered.
     */
    private fun createPlaceholderOutput(): ItemStack {
        return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
    }

    /**
     * Handles the search result when the player confirms their search.
     *
     * @param player The player who performed the search
     * @param spawnerPos The spawner position being configured
     * @param searchQuery The search query entered by the player
     */
    private fun handleSearch(player: ServerPlayerEntity, spawnerPos: BlockPos, searchQuery: String) {
        // If the search is empty, do nothing.
        if (searchQuery.isBlank()) return

        // Set the custom search method.
        SpawnerPokemonSelectionGui.searchTerm = searchQuery.trim()
        SpawnerPokemonSelectionGui.sortMethod = SortMethod.SEARCH

        player.sendMessage(Text.literal("§aSearching for Pokémon containing: §f'$searchQuery'"), false)

        // Return to the spawner GUI with the search results.
        goBackToPreviousGui(player, spawnerPos)
    }

    /**
     * Returns to the spawner selection GUI.
     */
    private fun goBackToPreviousGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        player.server.execute {
            SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos)
        }
    }
}
