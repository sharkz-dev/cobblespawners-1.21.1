package com.cobblespawners.utils.gui

import com.everlastingutils.command.CommandManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

enum class SortMethod {
    ALPHABETICAL,   // Sort by name alphabetically
    TYPE,           // Sort by Pokemon type
    SELECTED,       // Show selected Pokemon first
    SEARCH          // Show Pokemon matching search term
}

object SpawnerPokemonSelectionGui {
    private val logger = LoggerFactory.getLogger(SpawnerPokemonSelectionGui::class.java)
    var sortMethod = SortMethod.ALPHABETICAL
    var searchTerm = ""  // Store the current search term
    val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    val spawnerGuisOpen = ConcurrentHashMap<BlockPos, ServerPlayerEntity>()

    // UI constants
    private object Slots {
        const val PREV_PAGE = 45
        const val SORT_METHOD = 48
        const val SPAWNER_MENU = 49
        const val SPAWNER_SETTINGS = 50
        const val NEXT_PAGE = 53
    }

    // Common textures
    private object Textures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val SORT_METHOD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1ZWU0MTlhZDljMDYwYzE2Y2I1M2IxZGNmZmFjOGJhY2EwYjJhMjI2NWIxYjZjN2U4ZTc4MGMzN2IxMDRjMCJ9fX0="
        const val SPAWNER_MENU = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGQ4YjUxZGM5NTljMzNjMjUxNWJhZDY1ODk5N2Y2Y2VlOWY4NmRmMGU3ODdiNmM2ZjhkNTA3MDY0N2JkYyJ9fX0="
        const val SPAWNER_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTU5NDRiMzJkYjBlNzE0NjdkYzEzOWNmYzY0NjIwNWIxY2I3NGU4MjlkMmI3M2U1MzA5MzI1YzVkMTRlMDVmYSJ9fX0="
    }

    // Variant info data class
    data class SpeciesFormVariant(val species: Species, val form: FormData, val additionalAspects: Set<String>) {
        fun toKey(): String = "${species.showdownId()}_${if (form.name.equals("Standard", ignoreCase = true))
            "normal" else form.name.lowercase()}_${additionalAspects.map { it.lowercase() }.sorted().joinToString(",")}"
    }

    fun isSpawnerGuiOpen(spawnerPos: BlockPos): Boolean = spawnerGuisOpen.containsKey(spawnerPos)

    // MAIN GUI FUNCTIONS

    fun openSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos, page: Int = 0) {
        if (!CommandManager.hasPermissionOrOp(player.commandSource, "CobbleSpawners.Edit", 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false); return
        }

        val currentSpawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: run {
            player.sendMessage(Text.literal("Spawner data not found"), false); return
        }

        spawnerGuisOpen[spawnerPos] = player
        playerPages[player] = page

        CustomGui.openGui(
            player,
            "Select Pokémon for ${currentSpawnerData.spawnerName}",
            generateFullGuiLayout(currentSpawnerData.selectedPokemon, page),
            { handleMainGuiInteraction(it, player, spawnerPos, currentSpawnerData.selectedPokemon, page) },
            { inventory ->
                spawnerGuisOpen.remove(spawnerPos)
                playerPages.remove(player)
                CobbleSpawnersConfig.saveSpawnerData()
                player.sendMessage(Text.literal("Spawner data saved and GUI closed"), false)
            }
        )
    }

    private fun handleMainGuiInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        selectedPokemon: List<PokemonSpawnEntry>,
        page: Int
    ) {
        val clickedSlot = context.slotIndex
        val currentPage = playerPages[player] ?: 0
        val speciesListSize = getSortedSpeciesList(selectedPokemon).size

        when (clickedSlot) {
            Slots.PREV_PAGE -> if (currentPage > 0) {
                playerPages[player] = currentPage - 1
                refreshGuiItems(player, selectedPokemon, currentPage - 1)
            }
            Slots.SORT_METHOD -> when (context.clickType) {
                ClickType.LEFT -> {
                    // Cycle through the basic sort methods
                    sortMethod = when (sortMethod) {
                        SortMethod.ALPHABETICAL -> SortMethod.TYPE
                        SortMethod.TYPE -> SortMethod.SELECTED
                        SortMethod.SELECTED -> SortMethod.ALPHABETICAL
                        SortMethod.SEARCH -> SortMethod.ALPHABETICAL  // Reset search when changing sort
                    }

                    // Clear the search term if we're not in search mode
                    if (sortMethod != SortMethod.SEARCH) {
                        searchTerm = ""
                    }

                    CobbleSpawnersConfig.saveSpawnerData()
                    refreshGuiItems(player, selectedPokemon, currentPage)
                    player.sendMessage(Text.literal("Sort method changed to ${sortMethod.name}"), false)
                }
                ClickType.RIGHT -> {
                    // Open the search GUI
                    SearchGui.openSortGui(player, spawnerPos)
                }
                else -> {}
            }

            Slots.SPAWNER_MENU -> when (context.clickType) {
                ClickType.LEFT -> {
                    // Left click opens Global Settings - Pass the spawner position
                    GlobalSettingsGui.openGlobalSettingsGui(player, spawnerPos)
                }
                ClickType.RIGHT -> {
                    // Right click opens Spawner List
                    SpawnerListGui.openSpawnerListGui(player)
                }
                else -> {}
            }

            Slots.SPAWNER_SETTINGS -> SpawnerSettingsGui.openSpawnerSettingsGui(player, spawnerPos)
            Slots.NEXT_PAGE -> if ((currentPage + 1) * 45 < speciesListSize) {
                playerPages[player] = currentPage + 1
                refreshGuiItems(player, selectedPokemon, currentPage + 1)
            }
            else -> handlePokemonItemClick(context, player, spawnerPos, selectedPokemon, currentPage)
        }
    }

    private fun handlePokemonItemClick(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        selectedPokemon: List<PokemonSpawnEntry>,
        currentPage: Int
    ) {
        val clickedItem = context.clickedStack
        if (clickedItem.item !is PokemonItem) return

        val parsed = parsePokemonName(CustomGui.stripFormatting(clickedItem.name?.string ?: "")) ?: return
        val (species, formName, additionalAspects) = parsed

        when (context.clickType) {
            ClickType.LEFT -> {
                val existingEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(
                    spawnerPos, species.showdownId(), formName, additionalAspects
                )

                if (existingEntry == null) {
                    // Add Pokémon to spawner
                    if (CobbleSpawnersConfig.addDefaultPokemonToSpawner(
                            spawnerPos, species.showdownId(), formName, additionalAspects
                        )) {
                        val displaySuffix = createDisplaySuffix(formName, additionalAspects)
                        player.sendMessage(
                            Text.literal("Added ${species.name}$displaySuffix to the spawner."), false
                        )
                    }
                } else {
                    // Remove Pokémon from spawner
                    if (CobbleSpawnersConfig.removeAndSavePokemonFromSpawner(
                            spawnerPos, species.showdownId(), formName, additionalAspects
                        )) {
                        val displaySuffix = createDisplaySuffix(formName, additionalAspects)
                        player.sendMessage(
                            Text.literal("Removed ${species.name}$displaySuffix from the spawner."), false
                        )
                    }
                }

                // Refresh GUI
                CobbleSpawnersConfig.getSpawner(spawnerPos)?.let { updatedData ->
                    refreshGuiItems(player, updatedData.selectedPokemon, currentPage)
                }
            }
            ClickType.RIGHT -> {
                val existingEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(
                    spawnerPos, species.showdownId(), formName, additionalAspects
                )
                if (existingEntry != null) {
                    PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, species.showdownId(), formName, additionalAspects)
                }
            }
            else -> {}
        }
    }

    private fun parsePokemonName(clickedItemName: String): Triple<Species, String, Set<String>>? {
        val regex = Regex("(.*) \\((.*)\\)")
        val matchResult = regex.find(clickedItemName)

        val (speciesDisplayName, formAndAspectsStr) = if (matchResult != null) {
            matchResult.groupValues[1] to matchResult.groupValues[2]
        } else {
            clickedItemName to ""
        }

        val species = getSpeciesByDisplayName(speciesDisplayName) ?: return null

        val formAndAspects = formAndAspectsStr.split(", ").map { it.trim() }
        val formName = if (formAndAspects.isNotEmpty()) formAndAspects.first() else "Normal"
        val additionalAspects = formAndAspects.drop(1).toSet()

        return Triple(species, formName, additionalAspects)
    }

    private fun getSpeciesByDisplayName(displayName: String): Species? {
        return PokemonSpecies.species.find { it.name.equals(displayName, ignoreCase = true) }
    }

    private fun createDisplaySuffix(formName: String, additionalAspects: Set<String>): String {
        return if (formName != "Normal" || additionalAspects.isNotEmpty()) {
            " (" + listOfNotNull(if (formName != "Normal") formName else null)
                .plus(additionalAspects)
                .joinToString(", ") + ")"
        } else ""
    }

    private fun refreshGuiItems(player: ServerPlayerEntity, selectedPokemon: List<PokemonSpawnEntry>, page: Int) {
        CustomGui.refreshGui(player, generateFullGuiLayout(selectedPokemon, page))
    }

    private fun generateFullGuiLayout(selectedPokemon: List<PokemonSpawnEntry>, page: Int): List<ItemStack> {
        val layout = generatePokemonItemsForGui(selectedPokemon, page).toMutableList()
        val speciesListSize = getSortedSpeciesList(selectedPokemon).size

        // Add navigation buttons
        layout[Slots.PREV_PAGE] = if (page > 0) createButton(
            "Previous", Formatting.YELLOW, "Click to go to the previous page", Textures.PREV_PAGE
        ) else createFillerPane()

        // Update sort method button text to show search info if in search mode
        val sortMethodText = if (sortMethod == SortMethod.SEARCH) {
            "Searching: ${if (searchTerm.length > 10) searchTerm.take(7) + "..." else searchTerm}"
        } else {
            "Sort Method"
        }

        val sortMethodLore = if (sortMethod == SortMethod.SEARCH) {
            listOf(
                "Current Search: \"$searchTerm\"",
                "Left-click to clear search",
                "Right-click to search again"
            )
        } else {
            listOf(
                "Current Sort: ${sortMethod.name.lowercase().replaceFirstChar { it.uppercase() }}",
                "Left-click to change sorting method",
                "Right-click to search by name"
            )
        }

        layout[Slots.SORT_METHOD] = createButton(
            sortMethodText, Formatting.AQUA,
            sortMethodLore,
            Textures.SORT_METHOD
        )

        layout[Slots.SPAWNER_MENU] = createButton(
            "Global Settings", Formatting.GREEN,
            listOf(
                "§eLeft-click§r to open Global Settings",
                "§eRight-click§r to open Spawner List menu"
            ),
            Textures.SPAWNER_MENU
        )

        layout[Slots.SPAWNER_SETTINGS] = createButton(
            "Edit This Spawner's Settings", Formatting.RED,
            "Click to edit the current spawner's settings", Textures.SPAWNER_SETTINGS
        )

        layout[Slots.NEXT_PAGE] = if ((page + 1) * 45 < speciesListSize) createButton(
            "Next", Formatting.GREEN, "Click to go to the next page", Textures.NEXT_PAGE
        ) else createFillerPane()

        // Fill empty slots
        listOf(46, 47, 51, 52).forEach { layout[it] = createFillerPane() }

        return layout
    }

    // POKEMON ITEMS GENERATION

    private fun generatePokemonItemsForGui(
        selectedPokemon: List<PokemonSpawnEntry>,
        page: Int
    ): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }
        val pageSize = 45

        val variantsList = getSortedSpeciesList(selectedPokemon)
        val start = page * pageSize
        val end = minOf(start + pageSize, variantsList.size)

        for (i in start until end) {
            val variant = variantsList[i]
            val slotIndex = i - start

            if (slotIndex in 0 until pageSize) {
                val isSelected = isPokemonSelected(variant, selectedPokemon)
                layout[slotIndex] = if (isSelected) {
                    createSelectedPokemonItem(variant, selectedPokemon)
                } else {
                    createUnselectedPokemonItem(variant)
                }
            }
        }

        return layout
    }

    private fun isPokemonSelected(variant: SpeciesFormVariant, selectedPokemon: List<PokemonSpawnEntry>): Boolean {
        return selectedPokemon.any {
            it.pokemonName.equals(variant.species.showdownId(), ignoreCase = true) &&
                    (it.formName?.equals(variant.form.name, ignoreCase = true)
                        ?: (variant.form.name == "Standard" || variant.form.name == "Normal")) &&
                    it.aspects.map { a -> a.lowercase() }.toSet() ==
                    variant.additionalAspects.map { a -> a.lowercase() }.toSet()
        }
    }

    private fun createSelectedPokemonItem(
        variant: SpeciesFormVariant,
        selectedPokemon: List<PokemonSpawnEntry>
    ): ItemStack {
        val (species, form, additionalAspects) = variant
        val showFormsInGui = CobbleSpawnersConfig.config.globalConfig.showFormsInGui

        // Find the entry and create the item
        val entry = selectedPokemon.find {
            it.pokemonName.equals(species.showdownId(), ignoreCase = true) &&
                    (it.formName?.equals(form.name, ignoreCase = true)
                        ?: (form.name == "Standard" || form.name == "Normal")) &&
                    it.aspects.map { a -> a.lowercase() }.toSet() ==
                    additionalAspects.map { a -> a.lowercase() }.toSet()
        }

        val properties = PokemonProperties.parse(buildPropertiesString(species, form, additionalAspects, showFormsInGui))
        val pokemon = properties.create()
        val selectedItem = PokemonItem.from(pokemon, tint = Vector4f(1.0f, 1.0f, 1.0f, 1.0f))
        CustomGui.addEnchantmentGlint(selectedItem)

        // Extract values from the entry
        val displayName = buildDisplayName(species, form, additionalAspects, showFormsInGui)
        val displayFormName = if (form.name == "Standard") "Normal" else form.name

        // Build lore info
        val formLore = if (showFormsInGui || form.name != "Standard") "§2Form: §a$displayFormName" else ""
        val aspectsLore = if (additionalAspects.isNotEmpty())
            "§2Aspects: §a${additionalAspects.joinToString(", ") { it.replaceFirstChar { ch -> ch.uppercase() } }}"
        else ""

        // Add stats to lore
        val pokemonLore = listOf(
            "§2Type: §a${species.primaryType.name}",
            species.secondaryType?.let { "§2Secondary Type: §a${it.name}" } ?: "",
            formLore,
            aspectsLore,
            "----------------",
            "§6Spawn Chance: §e${entry?.spawnChance ?: 50.0}%",
            "§dMin Level: §f${entry?.minLevel ?: 1}",
            "§dMax Level: §f${entry?.maxLevel ?: 100}",
            "§9Spawn Time: §b${entry?.spawnSettings?.spawnTime ?: "ALL"}",
            "§3Spawn Weather: §b${entry?.spawnSettings?.spawnWeather ?: "ALL"}",
            "----------------",
            "§e§lLeft-click§r to §cDeselect",
            "§e§lRight-click§r to §aEdit stats and properties"
        ).filter { it.isNotEmpty() }

        selectedItem.setCustomName(Text.literal("§f§n$displayName"))
        CustomGui.setItemLore(selectedItem, pokemonLore)

        return selectedItem
    }

    private fun createUnselectedPokemonItem(variant: SpeciesFormVariant): ItemStack {
        val (species, form, additionalAspects) = variant
        val showFormsInGui = CobbleSpawnersConfig.config.globalConfig.showFormsInGui

        // Create item
        val properties = PokemonProperties.parse(buildPropertiesString(species, form, additionalAspects, showFormsInGui))
        val pokemon = properties.create()
        val unselectedItem = PokemonItem.from(pokemon, tint = Vector4f(0.3f, 0.3f, 0.3f, 1f))

        // Build display name and lore
        val displayName = buildDisplayName(species, form, additionalAspects, showFormsInGui)
        val displayFormName = if (form.name == "Standard") "Normal" else form.name

        val formLore = if (showFormsInGui || form.name != "Standard") "§aForm: §f$displayFormName" else ""
        val aspectsLore = if (additionalAspects.isNotEmpty())
            "§aAspects: §f${additionalAspects.joinToString(", ") { it.replaceFirstChar { it.uppercase() } }}"
        else ""

        val pokemonLore = listOf(
            "§aType: §f${species.primaryType.name}",
            species.secondaryType?.let { "§aSecondary Type: §f${it.name}" } ?: "",
            formLore,
            aspectsLore,
            "----------------",
            "§e§lLeft-click§r to §aSelect"
        ).filter { it.isNotEmpty() }

        unselectedItem.setCustomName(Text.literal("§f$displayName"))
        CustomGui.setItemLore(unselectedItem, pokemonLore)

        return unselectedItem
    }

    // UTILITY FUNCTIONS

    private fun buildPropertiesString(
        species: Species,
        form: FormData,
        additionalAspects: Set<String>,
        showFormsInGui: Boolean
    ): String {
        val propertiesStringBuilder = StringBuilder(species.showdownId())

        if (showFormsInGui && form.name != "Standard") {
            if (form.aspects.isNotEmpty()) {
                for (aspect in form.aspects) {
                    propertiesStringBuilder.append(" ").append("$aspect=true")
                }
            } else {
                propertiesStringBuilder.append(" form=${form.formOnlyShowdownId()}")
            }
        }

        for (aspect in additionalAspects) {
            propertiesStringBuilder.append(" ").append("$aspect=true")
        }

        return propertiesStringBuilder.toString()
    }

    private fun buildDisplayName(
        species: Species,
        form: FormData,
        additionalAspects: Set<String>,
        showFormsInGui: Boolean
    ): String {
        val formAndAspects = mutableListOf<String>()
        val displayFormName = if (form.name == "Standard") "Normal" else form.name

        if (showFormsInGui || form.name != "Standard") {
            formAndAspects.add(displayFormName)
        }

        formAndAspects.addAll(additionalAspects.map { it.replaceFirstChar { it.uppercase() } })

        val formAndAspectsStr = if (formAndAspects.isNotEmpty()) {
            " (" + formAndAspects.joinToString(", ") + ")"
        } else ""

        return species.name + formAndAspectsStr
    }

    // SORTING FUNCTIONS

    fun getSortedSpeciesList(selectedPokemon: List<PokemonSpawnEntry>): List<SpeciesFormVariant> {
        val showUnimplemented = CobbleSpawnersConfig.config.globalConfig.showUnimplementedPokemonInGui
        val showFormsInGui = CobbleSpawnersConfig.config.globalConfig.showFormsInGui

        // Get and filter species
        val speciesList = PokemonSpecies.species.filter { showUnimplemented || it.implemented }

        // Create variants list
        val variantsList = speciesList.flatMap { species ->
            val forms = if (showFormsInGui && species.forms.isNotEmpty())
                species.forms else listOf(species.standardForm)

            forms.flatMap { form ->
                listOf(
                    SpeciesFormVariant(species, form, emptySet()),
                    SpeciesFormVariant(species, form, setOf("shiny"))
                ) + getAdditionalAspectSets(species).map { SpeciesFormVariant(species, form, it) }
            }
        }.distinctBy { it.toKey() }

        // Apply sorting
        return when (sortMethod) {
            SortMethod.ALPHABETICAL ->
                variantsList.sortedBy { it.species.name + it.form.name + it.additionalAspects.joinToString() }

            SortMethod.TYPE ->
                variantsList.sortedBy { it.species.primaryType.name }

            SortMethod.SELECTED -> {
                // Build keys for selected Pokémon
                val selectedSet = selectedPokemon.map { it.toKey() }.toSet()

                // Separate selected and unselected
                variantsList.sortedWith(compareBy(
                    { !selectedSet.contains(it.toKey()) }, // Selected first
                    { it.species.name } // Then by name
                ))
            }

            SortMethod.SEARCH -> {
                if (searchTerm.isBlank()) {
                    // If search term is empty, revert to alphabetical
                    variantsList.sortedBy { it.species.name }
                } else {
                    // Filter by search term and prioritize exact matches
                    val searchTermLower = searchTerm.lowercase()
                    val exactMatches = variantsList.filter {
                        it.species.name.lowercase() == searchTermLower
                    }
                    val startsWithMatches = variantsList.filter {
                        it.species.name.lowercase().startsWith(searchTermLower) &&
                                !exactMatches.contains(it)
                    }
                    val containsMatches = variantsList.filter {
                        it.species.name.lowercase().contains(searchTermLower) &&
                                !exactMatches.contains(it) &&
                                !startsWithMatches.contains(it)
                    }
                    val otherMatches = variantsList.filter {
                        !exactMatches.contains(it) &&
                                !startsWithMatches.contains(it) &&
                                !containsMatches.contains(it)
                    }

                    // Combine all the matches in order of relevance
                    exactMatches.sortedBy { it.species.name } +
                            startsWithMatches.sortedBy { it.species.name } +
                            containsMatches.sortedBy { it.species.name } +
                            otherMatches.sortedBy { it.species.name }
                }
            }
        }
    }

    private fun getAdditionalAspectSets(species: Species): List<Set<String>> {
        return when (species.name.lowercase()) {
            "forretress" -> listOf(setOf("shulker"), setOf("shulker", "shiny"))
            else -> emptyList()
        }
    }

    // Helper function to convert a PokemonSpawnEntry to a key for matching
    private fun PokemonSpawnEntry.toKey(): String {
        val formName = (this.formName ?: "normal").lowercase()
        return "${this.pokemonName.lowercase()}_${formName}_${this.aspects.map { it.lowercase() }.sorted().joinToString(",")}"
    }

    // BUTTON CREATION FUNCTIONS

    private fun createButton(
        text: String,
        color: Formatting,
        loreText: String,
        textureValue: String
    ): ItemStack = createButton(text, color, listOf(loreText), textureValue)

    private fun createButton(
        text: String,
        color: Formatting,
        loreText: List<String>,
        textureValue: String
    ): ItemStack {
        val formattedLore = loreText.map {
            if (it.startsWith("§")) Text.literal(it)
            else Text.literal(it).styled { style -> style.withColor(Formatting.GRAY).withItalic(false) }
        }

        return CustomGui.createPlayerHeadButton(
            "${text.replace(" ", "")}Texture",
            Text.literal(text).styled { it.withColor(color).withBold(false).withItalic(false) },
            formattedLore,
            textureValue
        )
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }
}