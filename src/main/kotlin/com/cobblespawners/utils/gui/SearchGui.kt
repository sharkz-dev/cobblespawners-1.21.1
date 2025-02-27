// File: SortGui.kt
package com.cobblespawners.utils.gui

import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.setCustomName
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory

object SearchGui {
    private val logger = LoggerFactory.getLogger(SearchGui::class.java)

    /**
     * Opens the search anvil GUI for sorting Pokemon by name
     *
     * @param player The player to open the GUI for
     * @param spawnerPos The position of the spawner being configured
     */
    fun openSortGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        // Track that this spawner's GUI is open
        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        // Open the search anvil GUI
        CustomGui.openSearchGui(
            player = player,
            id = "pokemon_search_${spawnerPos.toShortString()}",
            title = "Search Pokémon",
            onSearch = { searchQuery ->
                handleSearch(player, spawnerPos, searchQuery)
            },
            // Create a custom cancel button
            setupInput = { inputInventory ->
                // Create a paper with empty name but informative lore
                val cancelPaper = ItemStack(Items.PAPER)
                cancelPaper.setCustomName(Text.literal("")) // Empty name so it doesn't appear in the input field

                val lore = listOf(
                    Text.literal("§cClick to Cancel"),
                    Text.literal("§7Return to Pokémon selection without searching"),
                    Text.literal("§7Or type a search term and press Enter")
                )
                cancelPaper.set(DataComponentTypes.LORE, LoreComponent(lore))

                // Replace the default input paper
                inputInventory.setStack(0, cancelPaper)
            },
            // Handle clicks on the input (cancel button)
            onInputClick = {
                player.sendMessage(Text.literal("§7Search cancelled."), false)
                goBackToPreviousGui(player, spawnerPos)
            }
        )

        player.sendMessage(Text.literal("Enter a Pokémon name to search, or click paper to cancel..."), false)
    }

    /**
     * Handles the search result when the player confirms their search
     *
     * @param player The player who performed the search
     * @param spawnerPos The spawner position being configured
     * @param searchQuery The search query entered by the player
     */
    private fun handleSearch(player: ServerPlayerEntity, spawnerPos: BlockPos, searchQuery: String) {
        // If the search is empty, just return to the Pokemon selection GUI without applying a search
        if (searchQuery.isBlank()) {
            // Go back without applying search
            player.sendMessage(Text.literal("§7Returning to Pokémon selection without search."), false)
            goBackToPreviousGui(player, spawnerPos)
            return
        }

        // Set the custom search method
        SpawnerPokemonSelectionGui.searchTerm = searchQuery.trim()
        SpawnerPokemonSelectionGui.sortMethod = SortMethod.SEARCH

        player.sendMessage(Text.literal("§aSearching for Pokémon containing: §f'${searchQuery}'"), false)

        // Return to the spawner GUI with the search results
        goBackToPreviousGui(player, spawnerPos)
    }

    /**
     * Returns to the spawner selection GUI
     */
    private fun goBackToPreviousGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        // Make sure we're using the main thread for GUI operations
        player.server.execute {
            SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos)
        }
    }
}