package com.cobblespawners.utils.gui

import com.everlastingutils.command.CommandManager
import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object SpawnerListGui {
    private const val ITEMS_PER_PAGE = 45
    private val playerPages: ConcurrentHashMap<ServerPlayerEntity, Int> = ConcurrentHashMap() // Track the page per player
    private val playerSortMethods: ConcurrentHashMap<ServerPlayerEntity, SortMethod> = ConcurrentHashMap() // Track sort method per player
    private val playerSearchTerms: ConcurrentHashMap<ServerPlayerEntity, String> = ConcurrentHashMap() // Track search term per player

    // Define sort methods
    private enum class SortMethod {
        ALPHABETICAL,
        NUMERICAL,
        SEARCH
    }

    // Texture constants
    private object Textures {
        const val SORT_METHOD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1ZWU0MTlhZDljMDYwYzE2Y2I1M2IxZGNmZmFjOGJhY2EwYjJhMjI2NWIxYjZjN2U4ZTc4MGMzN2IxMDRjMCJ9fX0="
        const val CANCEL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val SEARCH_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="
    }

    /**
     * Opens the spawner list GUI for the player.
     */
    fun openSpawnerListGui(player: ServerPlayerEntity) {
        if (!CommandManager.hasPermissionOrOp(player.commandSource, "CobbleSpawners.gui", 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false)
            return
        }

        val filteredSpawnerList = getFilteredSpawnerList(player)
        if (filteredSpawnerList.isEmpty() && playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL) == SortMethod.SEARCH) {
            player.sendMessage(Text.literal("No spawners found matching '${playerSearchTerms[player]}'."), false)
        } else if (filteredSpawnerList.isEmpty()) {
            player.sendMessage(Text.literal("No spawners available."), false)
            return
        }

        val currentPage = playerPages.getOrDefault(player, 0)
        val totalPages = if (filteredSpawnerList.isEmpty()) 0 else (filteredSpawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val adjustedPage = currentPage.coerceIn(0, maxOf(0, totalPages - 1))
        playerPages[player] = adjustedPage

        val layout = generateFullGuiLayout(filteredSpawnerList, adjustedPage, player)

        CustomGui.openGui(
            player,
            "Available Spawners",
            layout,
            { context -> handleButtonClick(context, player, filteredSpawnerList) },
            { playerPages.remove(player) }
        )
    }

    /**
     * Generates the full GUI layout with spawner items and navigation/sort buttons.
     */
    private fun generateFullGuiLayout(spawnerList: List<Pair<BlockPos, SpawnerData>>, page: Int, player: ServerPlayerEntity): List<ItemStack> {
        val layout = generateSpawnerItemsForGui(spawnerList, page).toMutableList()

        val previousPageSlot = 45
        val sortSlot = 49
        val nextPageSlot = 53

        // Fill the bottom row with filler panes
        for (i in 45..53) {
            layout[i] = createFillerPane()
        }

        // Add Previous Page button if applicable
        if (page > 0) {
            layout[previousPageSlot] = createPreviousPageButton()
        }

        // Add Next Page button if applicable
        val totalPages = (spawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        if (page < totalPages - 1) {
            layout[nextPageSlot] = createNextPageButton()
        }

        // Add Sort button
        layout[sortSlot] = createSortButton(player)

        return layout
    }

    /**
     * Generates the GUI items for spawners based on the current page.
     */
    private fun generateSpawnerItemsForGui(spawnerList: List<Pair<BlockPos, SpawnerData>>, page: Int): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }
        val start = page * ITEMS_PER_PAGE
        val end = minOf(start + ITEMS_PER_PAGE, spawnerList.size)

        for (i in start until end) {
            val (pos, spawnerData) = spawnerList[i]
            val lore = listOf(
                Text.literal("Location: ${pos.x}, ${pos.y}, ${pos.z}").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Dimension: ${spawnerData.dimension}").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Left-click to open GUI, right-click to teleport").styled { it.withColor(Formatting.GRAY).withItalic(false) }
            )
            val spawnerHeadItem = CustomGui.createPlayerHeadButton(
                "SpawnerTexture",
                Text.literal(spawnerData.spawnerName).styled { it.withColor(Formatting.WHITE).withBold(false).withItalic(false) },
                lore,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ3ZTJlNWQ1NWI2ZDA0OTQzNTE5YmVkMjU1N2M2MzI5ZTMzYjYwYjkwOWRlZTg5MjNjZDg4YjExNTIxMCJ9fX0="
            )
            layout[i - start] = spawnerHeadItem
        }

        return layout
    }

    /**
     * Handles button clicks in the GUI.
     */
    private fun handleButtonClick(
        context: InteractionContext,
        player: ServerPlayerEntity,
        filteredSpawnerList: List<Pair<BlockPos, SpawnerData>>
    ) {
        val currentPage = playerPages.getOrDefault(player, 0)

        when {
            context.slotIndex == 45 -> { // Previous Page
                if (currentPage > 0) {
                    playerPages[player] = currentPage - 1
                    refreshGuiItems(player)
                }
            }
            context.slotIndex == 53 -> { // Next Page
                val totalPages = (filteredSpawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
                if (currentPage < totalPages - 1) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player)
                }
            }
            context.slotIndex == 49 -> { // Sort Button
                if (context.button == 0) { // Left-click: Cycle sort methods
                    val currentSort = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
                    val nextSort = when (currentSort) {
                        SortMethod.ALPHABETICAL -> SortMethod.NUMERICAL
                        SortMethod.NUMERICAL -> SortMethod.ALPHABETICAL
                        SortMethod.SEARCH -> SortMethod.ALPHABETICAL
                    }
                    playerSortMethods[player] = nextSort
                    playerSearchTerms[player] = ""
                    refreshGuiItems(player)
                } else if (context.button == 1) { // Right-click: Open search GUI
                    openSearchGui(player)
                }
            }
            context.slotIndex < 45 -> { // Spawner Selection
                val start = currentPage * ITEMS_PER_PAGE
                val index = start + context.slotIndex
                if (index < filteredSpawnerList.size) {
                    val (pos, data) = filteredSpawnerList[index]
                    if (context.button == 0) { // Left-click: Open spawner GUI
                        SpawnerPokemonSelectionGui.openSpawnerGui(player, pos)
                    } else if (context.button == 1) { // Right-click: Teleport
                        player.requestTeleport(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
                        player.sendMessage(Text.literal("Teleported to spawner at (${pos.x}, ${pos.y}, ${pos.z})"), false)
                    }
                }
            }
        }
    }

    /**
     * Refreshes the GUI items without closing the GUI.
     */
    private fun refreshGuiItems(player: ServerPlayerEntity) {
        val filteredSpawnerList = getFilteredSpawnerList(player)
        val totalPages = if (filteredSpawnerList.isEmpty()) 0 else (filteredSpawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = playerPages.getOrDefault(player, 0).coerceIn(0, maxOf(0, totalPages - 1))
        playerPages[player] = currentPage
        val layout = generateFullGuiLayout(filteredSpawnerList, currentPage, player)
        CustomGui.refreshGui(player, layout)
    }

    /**
     * Gets the filtered and sorted spawner list based on the player's sort method and search term.
     */
    private fun getFilteredSpawnerList(player: ServerPlayerEntity): List<Pair<BlockPos, SpawnerData>> {
        val sortMethod = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
        val searchTerm = playerSearchTerms.getOrDefault(player, "")
        val spawnerList = CobbleSpawnersConfig.spawners.map { (pos, data) -> Pair(pos, data) }

        return when (sortMethod) {
            SortMethod.ALPHABETICAL -> spawnerList.sortedBy { it.second.spawnerName }
            SortMethod.NUMERICAL -> spawnerList.sortedWith(compareBy<Pair<BlockPos, SpawnerData>> { extractNumber(it.second.spawnerName) ?: Int.MAX_VALUE }.thenByDescending { it.second.spawnerName })
            SortMethod.SEARCH -> spawnerList.filter { it.second.spawnerName.contains(searchTerm, ignoreCase = true) }
                .sortedBy { it.second.spawnerName }
        }
    }

    /**
     * Extracts the leading number from a string for numerical sorting.
     */
    private fun extractNumber(name: String): Int? {
        val pattern = Pattern.compile("^\\d+")
        val matcher = pattern.matcher(name)
        return if (matcher.find()) matcher.group().toIntOrNull() else null
    }

    /**
     * Creates the sort button based on the player's current sort method and search term.
     */
    private fun createSortButton(player: ServerPlayerEntity): ItemStack {
        val sortMethod = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
        val searchTerm = playerSearchTerms.getOrDefault(player, "")
        val buttonText = when (sortMethod) {
            SortMethod.ALPHABETICAL -> "Sort: Alphabetical"
            SortMethod.NUMERICAL -> "Sort: Numerical"
            SortMethod.SEARCH -> "Sort: Search '${if (searchTerm.length > 10) searchTerm.take(7) + "..." else searchTerm}'"
        }
        val lore = listOf(
            "Current Sort: ${sortMethod.name.lowercase().replaceFirstChar { it.uppercase() }}",
            "Left-click to cycle sort methods",
            "Right-click to search by name"
        )

        return CustomGui.createPlayerHeadButton(
            textureName = "SortButton",
            title = Text.literal(buttonText).styled { it.withColor(Formatting.AQUA).withBold(false).withItalic(false) },
            lore = lore.map { Text.literal(it).styled { style -> style.withColor(Formatting.GRAY).withItalic(false) } },
            textureValue = Textures.SORT_METHOD
        )
    }

    /**
     * Opens the anvil GUI for searching spawners.
     */
    private fun openSearchGui(player: ServerPlayerEntity) {
        val cancelButton = createCancelButton()
        val blockedInput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
        val placeholderOutput = createPlaceholderOutput()

        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "spawner_search",
            title = "Search Spawners",
            initialText = "",
            leftItem = cancelButton,
            rightItem = blockedInput,
            resultItem = placeholderOutput,
            onLeftClick = { _ ->
                player.sendMessage(Text.literal("§7Search cancelled."), false)
                openSpawnerListGui(player)
            },
            onRightClick = null,
            onResultClick = { context ->
                val searchText = context.handler.currentText
                if (searchText.isNotBlank()) {
                    handleSearch(player, searchText)
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
                player.server.execute {
                    openSpawnerListGui(player)
                }
            }
        )

        player.sendMessage(Text.literal("Enter a spawner name to search, or click the X to cancel..."), false)
    }

    /**
     * Creates the cancel button for the search GUI.
     */
    private fun createCancelButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "CancelButton",
            title = Text.literal("§cCancel Search").styled { it.withBold(true).withItalic(false) },
            lore = listOf(
                Text.literal("§7Click to return to spawner list"),
                Text.literal("§7without searching")
            ),
            textureValue = Textures.CANCEL_TEXTURE
        )
    }

    /**
     * Creates a dynamic search button for the search GUI.
     */
    private fun createDynamicSearchButton(searchText: String): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "SearchButton",
            title = Text.literal("§aSearch: §f$searchText").styled { it.withBold(true).withItalic(false) },
            lore = listOf(
                Text.literal("§aClick to search for this term"),
                Text.literal("§7Enter different text to change search")
            ),
            textureValue = Textures.SEARCH_TEXTURE
        )
    }

    /**
     * Creates a placeholder output for the search GUI when no text is entered.
     */
    private fun createPlaceholderOutput(): ItemStack {
        return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
    }

    /**
     * Handles the search confirmation from the anvil GUI.
     */
    private fun handleSearch(player: ServerPlayerEntity, searchQuery: String) {
        playerSearchTerms[player] = searchQuery.trim()
        playerSortMethods[player] = SortMethod.SEARCH
        player.sendMessage(Text.literal("§aSearching for spawners containing: §f'$searchQuery'"), false)
        openSpawnerListGui(player)
    }

    // Helper methods for navigation buttons and filler panes
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun createNextPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "NextPageButton",
            Text.literal("Next Page").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Click to go to the next page").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        )
    }

    private fun createPreviousPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "PreviousPageButton",
            Text.literal("Previous Page").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Click to go to the previous page").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        )
    }
}