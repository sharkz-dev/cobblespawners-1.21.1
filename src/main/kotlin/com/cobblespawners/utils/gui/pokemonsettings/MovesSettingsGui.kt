// File: MovesSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import kotlin.math.ceil
import kotlin.math.min

object MovesSettingsGui {
    private val logger = LoggerFactory.getLogger(MovesSettingsGui::class.java)

    // Track which page the player is viewing
    private val playerPages = mutableMapOf<ServerPlayerEntity, Int>()

    // Cache default moves by Pokémon to avoid repeated lookups
    private val cachedDefaultMovesByPokemon = mutableMapOf<String, List<LeveledMove>>()

    // Track current Pokémon name for move validation
    private var currentPokemonName: String = ""

    // Constants for GUI layout
    private const val TOGGLE_CUSTOM_MOVES_SLOT = 48 // Bottom left
    private const val BACK_BUTTON_SLOT = 49 // Bottom center
    private const val ADD_CUSTOM_MOVE_SLOT = 50 // Bottom middle-right
    private const val PREVIOUS_PAGE_SLOT = 45 // Bottom left corner
    private const val NEXT_PAGE_SLOT = 53 // Bottom right corner
    private const val MOVES_PER_PAGE = 36
    private const val HELP_BUTTON_SLOT = 4 // Top middle row

    // Move button slots - the middle area of the GUI (rows 1-4, including side slots)
    private val MOVE_SLOTS = (9..44).toList()

    /**
     * Opens the Moves Settings GUI, combining selected and available moves
     */
    fun openMovesSettingsGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>,
        page: Int = 0
    ) {
        val standardFormName = formName ?: "Standard"
        val effectiveAspects = if (additionalAspects.isEmpty()) {
            CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, standardFormName)?.aspects ?: emptySet()
        } else {
            additionalAspects
        }

        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, standardFormName, effectiveAspects)
        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '$standardFormName' not found in spawner."),
                false
            )
            return
        }

        // Initialize moves if it's null
        if (selectedEntry.moves == null) {
            selectedEntry.moves = MovesSettings()
        }

        // Update the current Pokémon name and cache its default moves
        currentPokemonName = pokemonName
        if (!cachedDefaultMovesByPokemon.containsKey(pokemonName)) {
            val species = PokemonSpecies.getByName(pokemonName.lowercase())
            if (species != null) {
                cachedDefaultMovesByPokemon[pokemonName] = CobbleSpawnersConfig.getDefaultInitialMoves(species)
            }
        }

        playerPages[player] = page
        spawnerGuisOpen[spawnerPos] = player

        val layout = generateMovesLayout(selectedEntry, page)
        val aspectsDisplay = if (effectiveAspects.isNotEmpty()) effectiveAspects.joinToString(", ") else ""
        val guiTitle = if (aspectsDisplay.isNotEmpty())
            "Moves Settings - $pokemonName (${selectedEntry.formName ?: "Standard"}, $aspectsDisplay)"
        else
            "Moves Settings - $pokemonName (${selectedEntry.formName ?: "Standard"})"

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, effectiveAspects) },
            { handleClose(it, spawnerPos, player) }
        )
    }

    /**
     * Generates the layout combining selected moves (paper) and available moves (blue glass)
     */
    private fun generateMovesLayout(selectedEntry: PokemonSpawnEntry, page: Int): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }

        // Ensure moves are initialized
        if (selectedEntry.moves == null) {
            selectedEntry.moves = MovesSettings()
        }

        // Add control buttons
        layout[TOGGLE_CUSTOM_MOVES_SLOT] = createToggleButton(selectedEntry.moves!!.allowCustomInitialMoves)
        layout[BACK_BUTTON_SLOT] = createBackButton()
        layout[ADD_CUSTOM_MOVE_SLOT] = createAddCustomMoveButton()
        layout[HELP_BUTTON_SLOT] = createHelpButton()

        // Get selected and available moves
        val selectedMoves = selectedEntry.moves!!.selectedMoves
        val allPossibleMoves = getAllPossibleMoves(selectedEntry.pokemonName)
        val availableMoves = allPossibleMoves.filterNot { possibleMove ->
            selectedMoves.any { it.level == possibleMove.level && it.moveId.equals(possibleMove.moveId, ignoreCase = true) }
        }

        // Combine moves: selected first, then available, sorted by level and name
        val combinedMoves = (selectedMoves + availableMoves).sortedWith(compareBy<LeveledMove> { it.level }.thenBy { it.moveId })
        val totalPages = ceil(combinedMoves.size.toDouble() / MOVES_PER_PAGE).toInt()

        // Add pagination buttons
        if (page > 0) {
            layout[PREVIOUS_PAGE_SLOT] = createPrevPageButton()
        }
        if (page < totalPages - 1 && combinedMoves.isNotEmpty()) {
            layout[NEXT_PAGE_SLOT] = createNextPageButton()
        }

        // Populate move slots
        val startIndex = page * MOVES_PER_PAGE
        val endIndex = min(startIndex + MOVES_PER_PAGE, combinedMoves.size)
        if (startIndex < combinedMoves.size) {
            val pageMoves = combinedMoves.subList(startIndex, endIndex)
            pageMoves.forEachIndexed { index, moveInfo ->
                if (index < MOVE_SLOTS.size) {
                    val isSelected = selectedMoves.any {
                        it.level == moveInfo.level && it.moveId.equals(moveInfo.moveId, ignoreCase = true)
                    }
                    layout[MOVE_SLOTS[index]] = if (isSelected) {
                        createSelectedMoveButton(moveInfo) // Paper for selected moves
                    } else {
                        createAvailableMoveButton(moveInfo) // Blue glass for available moves
                    }
                }
            }
        }

        return layout
    }

    /**
     * Creates a help button explaining how moves work
     */
    private fun createHelpButton(): ItemStack {
        return ItemStack(Items.BOOK).apply {
            setCustomName(Text.literal("Moves Information").styled { it.withColor(Formatting.GOLD).withBold(true) })
            val lore = listOf(
                Text.literal("§7§lHow Moves Work:"),
                Text.literal(""),
                Text.literal("§7• Moves are selected from highest level"),
                Text.literal("§7  down to lowest level, up to 4 moves total."),
                Text.literal(""),
                Text.literal("§7• Selected moves (§fpaper§7) will be added"),
                Text.literal("§7  to spawned Pokémon's movesets."),
                Text.literal(""),
                Text.literal("§7• Available moves (§9blue glass§7) can be"),
                Text.literal("§7  added to the selection."),
                Text.literal(""),
                Text.literal("§7• §dForced§7 moves will always be selected"),
                Text.literal("§7  regardless of level requirements."),
                Text.literal(""),
                Text.literal("§7• Right-click a selected move to toggle"),
                Text.literal("§7  the §dForced§7 status.")
            )
            CustomGui.setItemLore(this, lore)
        }
    }

    /**
     * Handles interactions in the Moves Settings GUI
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
        val page = playerPages[player] ?: 0
        val isRightClick = context.button == 1

        when (slotIndex) {
            TOGGLE_CUSTOM_MOVES_SLOT -> {
                CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
                    // Initialize moves if null
                    if (entry.moves == null) {
                        entry.moves = MovesSettings()
                    }

                    entry.moves = MovesSettings(
                        allowCustomInitialMoves = !entry.moves!!.allowCustomInitialMoves,
                        selectedMoves = entry.moves!!.selectedMoves
                    )
                }
                refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects, page)
            }
            BACK_BUTTON_SLOT -> {
                CustomGui.closeGui(player)
                SpawnSettingsGui.openSpawnShinyEditorGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            ADD_CUSTOM_MOVE_SLOT -> {
                CustomGui.closeGui(player)
                openAddCustomMoveGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            PREVIOUS_PAGE_SLOT -> {
                if (page > 0) {
                    playerPages[player] = page - 1
                    refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects, page - 1)
                }
            }
            NEXT_PAGE_SLOT -> {
                val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
                if (selectedEntry != null) {
                    // Initialize moves if null
                    if (selectedEntry.moves == null) {
                        selectedEntry.moves = MovesSettings()
                    }

                    val totalMoves = (selectedEntry.moves!!.selectedMoves + getAllPossibleMoves(pokemonName)).distinctBy { it.level to it.moveId }.size
                    val totalPages = ceil(totalMoves.toDouble() / MOVES_PER_PAGE).toInt()
                    if (page < totalPages - 1) {
                        playerPages[player] = page + 1
                        refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects, page + 1)
                    }
                }
            }
            in MOVE_SLOTS -> {
                val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
                if (selectedEntry != null) {
                    // Initialize moves if null
                    if (selectedEntry.moves == null) {
                        selectedEntry.moves = MovesSettings()
                    }

                    val combinedMoves = (selectedEntry.moves!!.selectedMoves + getAllPossibleMoves(pokemonName))
                        .distinctBy { it.level to it.moveId }
                        .sortedWith(compareBy<LeveledMove> { it.level }.thenBy { it.moveId })
                    val startIndex = page * MOVES_PER_PAGE
                    val endIndex = min(startIndex + MOVES_PER_PAGE, combinedMoves.size)
                    if (startIndex < combinedMoves.size) {
                        val pageMoves = combinedMoves.subList(startIndex, endIndex)
                        val slotPosition = MOVE_SLOTS.indexOf(slotIndex)
                        if (slotPosition in pageMoves.indices) {
                            val moveInfo = pageMoves[slotPosition]
                            val isSelected = selectedEntry.moves!!.selectedMoves.any {
                                it.level == moveInfo.level && it.moveId.equals(moveInfo.moveId, ignoreCase = true)
                            }
                            if (isSelected) {
                                if (isRightClick) {
                                    // Toggle forced status
                                    CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
                                        // Initialize moves if null
                                        if (entry.moves == null) {
                                            entry.moves = MovesSettings()
                                        }

                                        val movesList = entry.moves!!.selectedMoves.toMutableList()
                                        val idx = movesList.indexOfFirst { it.level == moveInfo.level && it.moveId.equals(moveInfo.moveId, ignoreCase = true) }
                                        if (idx >= 0) {
                                            val move = movesList[idx]
                                            movesList[idx] = LeveledMove(move.level, move.moveId, !move.forced)
                                            entry.moves = MovesSettings(entry.moves!!.allowCustomInitialMoves, movesList)
                                            player.sendMessage(
                                                Text.literal("Set ${moveInfo.moveId.capitalize()} (Lv. ${moveInfo.level}) to ${if (!move.forced) "forced" else "not forced"} for $pokemonName."),
                                                false
                                            )
                                        }
                                    }
                                } else {
                                    // Remove move
                                    CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
                                        // Initialize moves if null
                                        if (entry.moves == null) {
                                            entry.moves = MovesSettings()
                                        }

                                        val movesList = entry.moves!!.selectedMoves.toMutableList()
                                        val idx = movesList.indexOfFirst { it.level == moveInfo.level && it.moveId.equals(moveInfo.moveId, ignoreCase = true) }
                                        if (idx >= 0) {
                                            movesList.removeAt(idx)
                                            entry.moves = MovesSettings(entry.moves!!.allowCustomInitialMoves, movesList)
                                            player.sendMessage(
                                                Text.literal("Removed ${moveInfo.moveId.capitalize()} (Lv. ${moveInfo.level}) from ${pokemonName}'s moveset."),
                                                false
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Add move
                                CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
                                    // Initialize moves if null
                                    if (entry.moves == null) {
                                        entry.moves = MovesSettings()
                                    }

                                    val movesList = entry.moves!!.selectedMoves.toMutableList()
                                    if (!movesList.any { it.level == moveInfo.level && it.moveId.equals(moveInfo.moveId, ignoreCase = true) }) {
                                        movesList.add(LeveledMove(moveInfo.level, moveInfo.moveId, false))
                                        entry.moves = MovesSettings(entry.moves!!.allowCustomInitialMoves, movesList)
                                        player.sendMessage(
                                            Text.literal("Added ${moveInfo.moveId.capitalize()} (Lv. ${moveInfo.level}) to ${pokemonName}'s moveset."),
                                            false
                                        )
                                    }
                                }
                            }
                            refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects, page)
                        }
                    }
                }
            }
        }
    }

    /**
     * Refreshes the GUI with the current page
     */
    private fun refreshGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>, page: Int) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (selectedEntry != null) {
            val layout = generateMovesLayout(selectedEntry, page)
            CustomGui.refreshGui(player, layout)
        }
    }

    /**
     * Opens the Add Custom Move GUI using the anvil
     */
    private fun openAddCustomMoveGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        spawnerGuisOpen[spawnerPos] = player

        val cancelButton = ItemStack(Items.BARRIER).apply {
            setCustomName(Text.literal("Cancel").styled { it.withColor(Formatting.RED) })
        }
        val placeholder = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
        val initialOutputButton = ItemStack(Items.PAPER).apply {
            setCustomName(Text.literal("Type a move name...").styled { it.withColor(Formatting.GRAY) })
        }

        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "add_custom_move_${spawnerPos.toShortString()}",
            title = "Enter Move Name",
            initialText = "",
            leftItem = cancelButton,
            rightItem = placeholder,
            resultItem = initialOutputButton,
            onLeftClick = { _ ->
                player.server.execute {
                    openMovesSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects, playerPages[player] ?: 0)
                }
            },
            onRightClick = null,
            onResultClick = { context ->
                if (context.handler.currentText.isNotBlank()) {
                    val moveName = context.handler.currentText
                    val formattedMoveName = moveName.trim().lowercase().replace(Regex("\\s+"), "_")
                    val moveTemplate = Moves.getByName(formattedMoveName)
                    if (moveTemplate == null) {
                        player.sendMessage(Text.literal("§cCouldn't find move: §f$formattedMoveName§c. Please check the spelling."), false)
                        return@openAnvilGui
                    }

                    CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName, additionalAspects) { entry ->
                        // Initialize moves if null
                        if (entry.moves == null) {
                            entry.moves = MovesSettings()
                        }

                        val movesList = entry.moves!!.selectedMoves.toMutableList()
                        if (!movesList.any { it.moveId.equals(formattedMoveName, ignoreCase = true) }) {
                            movesList.add(LeveledMove(1, formattedMoveName, false))
                            entry.moves = MovesSettings(entry.moves!!.allowCustomInitialMoves, movesList)
                            player.sendMessage(Text.literal("Added custom move ${formattedMoveName.capitalize()} (Lv. 1) to ${pokemonName}'s moveset."), false)
                            // Close the anvil GUI and reopen the Moves Settings GUI
                            player.closeHandledScreen()
                            player.server.execute {
                                openMovesSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects, playerPages[player] ?: 0)
                            }
                        } else {
                            player.sendMessage(Text.literal("§c${formattedMoveName.capitalize()} is already in the moveset."), false)
                        }
                    }
                }
            },
            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                if (handler != null) {
                    if (text.isNotBlank()) {
                        val outputButton = ItemStack(Items.PAPER).apply {
                            setCustomName(Text.literal("Add: $text").styled { it.withColor(Formatting.GREEN).withBold(true) })
                            val lore = listOf(
                                Text.literal("§7Click to add this move"),
                                Text.literal("§7Level will be set to 1")
                            )
                            CustomGui.setItemLore(this, lore)
                        }
                        handler.updateSlot(2, outputButton)
                    } else {
                        handler.updateSlot(2, initialOutputButton)
                    }
                }
            },
            onClose = {
                player.server.execute {
                    openMovesSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects, playerPages[player] ?: 0)
                }
            }
        )
    }

    /**
     * Creates a button for a selected move (paper for default moves, map for custom moves)
     */
    private fun createSelectedMoveButton(moveInfo: LeveledMove): ItemStack {
        val (level, moveId, forced) = moveInfo
        val displayName = moveId.replace("_", " ").split(" ").joinToString(" ") { it.capitalize() }

        // Check if this is a custom move (not in the default list for this Pokémon)
        val isCustomMove = !isDefaultMove(moveInfo)

        // Use a map for custom moves, paper for default moves
        val item = ItemStack(if (isCustomMove) Items.FILLED_MAP else Items.PAPER)

        // Add a prefix for custom moves
        val namePrefix = if (isCustomMove) "§d[Custom] " else "§f"
        item.setCustomName(Text.literal("$namePrefix$displayName${if (forced) " (Forced)" else ""}").styled { it.withBold(true) })

        val lore = mutableListOf(
            Text.literal("§7Level: §f$level"),
            Text.literal("§eLeft-click to remove"),
            Text.literal("§eRight-click to toggle forced: §f${if (forced) "ON" else "OFF"}")
        )

        CustomGui.setItemLore(item, lore)
        return item
    }

    /**
     * Checks if a move is in the default moveset for the Pokémon
     */
    private fun isDefaultMove(moveInfo: LeveledMove): Boolean {
        val cachedDefaultMoves = cachedDefaultMovesByPokemon.getOrDefault(currentPokemonName, null)
        if (cachedDefaultMoves != null) {
            return cachedDefaultMoves.any { it.moveId.equals(moveInfo.moveId, ignoreCase = true) }
        }

        // Should never reach here if caching is working properly
        val species = PokemonSpecies.getByName(currentPokemonName.lowercase()) ?: return false
        val defaultMoves = CobbleSpawnersConfig.getDefaultInitialMoves(species)
        return defaultMoves.any { it.moveId.equals(moveInfo.moveId, ignoreCase = true) }
    }

    /**
     * Creates a button for an available move (blue glass)
     */
    private fun createAvailableMoveButton(moveInfo: LeveledMove): ItemStack {
        val (level, moveId, _) = moveInfo
        val displayName = moveId.replace("_", " ").split(" ").joinToString(" ") { it.capitalize() }
        val item = ItemStack(Items.BLUE_STAINED_GLASS_PANE)
        item.setCustomName(Text.literal("§9$displayName"))
        val lore = listOf(
            Text.literal("§7Level: §f$level"),
            Text.literal("§eClick to add")
        )
        CustomGui.setItemLore(item, lore)
        return item
    }

    /**
     * Creates a toggle button for custom moves
     */
    private fun createToggleButton(isEnabled: Boolean): ItemStack {
        return ItemStack(if (isEnabled) Items.LIME_CONCRETE else Items.RED_CONCRETE).apply {
            setCustomName(
                Text.literal(if (isEnabled) "Custom Moves: ON" else "Custom Moves: OFF")
                    .styled { it.withColor(if (isEnabled) Formatting.GREEN else Formatting.RED).withBold(true) }
            )
            val lore = listOf(Text.literal("§7Click to ${if (isEnabled) "disable" else "enable"} custom moves"))
            CustomGui.setItemLore(this, lore)
        }
    }

    /**
     * Creates an "Add Custom Move" button
     */
    private fun createAddCustomMoveButton(): ItemStack {
        return ItemStack(Items.NAME_TAG).apply {
            setCustomName(Text.literal("Add Custom Move").styled { it.withColor(Formatting.YELLOW).withBold(true) })
            val lore = listOf(Text.literal("§7Click to manually enter a move name"))
            CustomGui.setItemLore(this, lore)
        }
    }

    /**
     * Creates a back button using a player head
     */
    private fun createBackButton(): ItemStack {
        return createBackHead()
    }

    /**
     * Creates a back button with a custom player head texture
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
     * Creates a previous page button
     */
    private fun createPrevPageButton(): ItemStack {
        return ItemStack(Items.ARROW).apply {
            setCustomName(Text.literal("Previous Page").styled { it.withColor(Formatting.WHITE) })
        }
    }

    /**
     * Creates a next page button
     */
    private fun createNextPageButton(): ItemStack {
        return ItemStack(Items.ARROW).apply {
            setCustomName(Text.literal("Next Page").styled { it.withColor(Formatting.WHITE) })
        }
    }

    /**
     * Creates a basic filler pane
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    /**
     * Gets all possible moves for a Pokémon with their levels
     */
    private fun getAllPossibleMoves(pokemonName: String): List<LeveledMove> {
        val species = PokemonSpecies.getByName(pokemonName.lowercase()) ?: return emptyList()
        return CobbleSpawnersConfig.getDefaultInitialMoves(species)
    }

    /**
     * Handles GUI close event
     */
    private fun handleClose(inventory: Inventory, spawnerPos: BlockPos, player: ServerPlayerEntity) {
        spawnerGuisOpen.remove(spawnerPos)
        playerPages.remove(player)
    }
}