package com.cobblespawners.utils.gui

import com.everlastingutils.command.CommandManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

enum class SortMethod {
    ALPHABETICAL,   // Sort by name alphabetically
    TYPE,           // Sort by Pokémon type
    SELECTED,       // Show only selected Pokémon
    SEARCH          // Show Pokémon matching search term
}

object SpawnerPokemonSelectionGui {
    private val logger = LoggerFactory.getLogger(SpawnerPokemonSelectionGui::class.java)
    var sortMethod = SortMethod.ALPHABETICAL
    var searchTerm = ""  // Store the current search term
    val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    val spawnerGuisOpen = ConcurrentHashMap<BlockPos, ServerPlayerEntity>()

    // Caching for full variants (avoids repeated heavy computations)
    private var cachedVariants: List<SpeciesFormVariant>? = null
    private var cachedSortMethod: SortMethod? = null
    private var cachedSearchTerm: String? = null
    private var cachedConfigKey: String? = null  // New cache key for config values

    // Cache for additional aspect sets per species name (keyed in lowercase)
    private val additionalAspectsCache = mutableMapOf<String, List<Set<String>>>()

    // Cache for full GUI layouts based on state. Now includes a configKey.
    private data class GuiLayoutKey(
        val page: Int,
        val sortMethod: SortMethod,
        val searchTerm: String,
        val selectedPokemonKey: String,
        val configKey: String
    )
    private val guiLayoutCache = ConcurrentHashMap<GuiLayoutKey, List<ItemStack>>()

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
        fun toKey(): String = "${species.showdownId()}_${if (form.name.equals("Standard", ignoreCase = true)) "normal" else form.name.lowercase()}_${additionalAspects.map { it.lowercase() }.sorted().joinToString(",")}"
    }

    // Track ongoing computations per player
    private val playerComputations = ConcurrentHashMap<ServerPlayerEntity, CompletableFuture<Void>>()

    fun isSpawnerGuiOpen(spawnerPos: BlockPos): Boolean = spawnerGuisOpen.containsKey(spawnerPos)

    // ### Main GUI Functions

    fun openSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos, page: Int = 0) {
        if (!CommandManager.hasPermissionOrOp(player.commandSource, "CobbleSpawners.Edit", 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false)
            return
        }

        val currentSpawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: run {
            player.sendMessage(Text.literal("Spawner data not found"), false)
            return
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

                    // Invalidate caches when sort method changes (unless in SELECTED mode)
                    if (sortMethod != SortMethod.SELECTED) {
                        invalidateCaches()
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

            Slots.SPAWNER_SETTINGS -> GlobalSettingsGui.openGlobalSettingsGui(player, spawnerPos)
            Slots.NEXT_PAGE -> {
                val totalVariants = getTotalVariantsCount(selectedPokemon)
                if ((currentPage + 1) * 45 < totalVariants) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player, selectedPokemon, currentPage + 1)
                }
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
        // Cancel any ongoing computation
        playerComputations[player]?.cancel(false)

        // Start a new computation asynchronously
        val future = CompletableFuture.runAsync {
            try {
                val items = generateFullGuiLayout(selectedPokemon, page)
                // Update GUI on the main thread
                player.server.execute {
                    CustomGui.refreshGui(player, items)
                    playerComputations.remove(player) // Clean up after completion
                }
            } catch (e: Exception) {
                logger.error("Error computing GUI items for player ${player.name.string}", e)
            }
        }
        playerComputations[player] = future
    }

    // Modified generateFullGuiLayout to use caching.
    private fun generateFullGuiLayout(selectedPokemon: List<PokemonSpawnEntry>, page: Int): List<ItemStack> {
        val selectedKey = selectedPokemon.map { it.toKey() }.sorted().joinToString(",")
        // Include config values in the key.
        val globalConfig = CobbleSpawnersConfig.config.globalConfig
        val configKey = "${globalConfig.showUnimplementedPokemonInGui}_${globalConfig.showFormsInGui}_${globalConfig.showAspectsInGui}"
        val key = GuiLayoutKey(page, sortMethod, searchTerm, selectedKey, configKey)
        guiLayoutCache[key]?.let { return it }

        val layout = generatePokemonItemsForGui(selectedPokemon, page).toMutableList()
        val totalVariants = getTotalVariantsCount(selectedPokemon)

        layout[Slots.PREV_PAGE] = if (page > 0) createButton(
            "Previous", Formatting.YELLOW, "Click to go to the previous page", Textures.PREV_PAGE
        ) else createFillerPane()

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
        layout[Slots.SORT_METHOD] = createButton(sortMethodText, Formatting.AQUA, sortMethodLore, Textures.SORT_METHOD)

        // Global Settings button
        layout[Slots.SPAWNER_MENU] = createButton(
            "Global Settings", Formatting.GREEN,
            listOf("Left-click to open Global Settings", "Right-click to open Spawner List menu"),
            Textures.SPAWNER_MENU
        )

        // Spawner Settings button
        layout[Slots.SPAWNER_SETTINGS] = createButton(
            "Edit This Spawner's Settings", Formatting.BLUE,
            "Click to edit the current spawner’s settings", Textures.SPAWNER_SETTINGS
        )

        layout[Slots.NEXT_PAGE] = if ((page + 1) * 45 < totalVariants) createButton(
            "Next", Formatting.GREEN, "Click to go to the next page", Textures.NEXT_PAGE
        ) else createFillerPane()

        listOf(46, 47, 51, 52).forEach { layout[it] = createFillerPane() }

        // Save the computed layout in the cache.
        guiLayoutCache[key] = layout
        return layout
    }

    // Helper function to clear caches when parameters change.
    private fun invalidateCaches() {
        cachedVariants = null
        guiLayoutCache.clear()
    }

    // ### Pokémon Items Generation

    private fun generatePokemonItemsForGui(
        selectedPokemon: List<PokemonSpawnEntry>,
        page: Int
    ): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }
        val pageSize = 45
        val variantsList = getVariantsForPage(selectedPokemon, page, pageSize)

        for (i in variantsList.indices) {
            val variant = variantsList[i]
            val isSelected = isPokemonSelected(variant, selectedPokemon)
            layout[i] = if (isSelected) {
                createSelectedPokemonItem(variant, selectedPokemon)
            } else {
                createUnselectedPokemonItem(variant)
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
        val aspectsLore = if (CobbleSpawnersConfig.config.globalConfig.showAspectsInGui && additionalAspects.isNotEmpty())
            "§2Aspects: §a${additionalAspects.joinToString(", ") { it.replaceFirstChar { ch -> ch.uppercase() } }}"
        else {
            // If aspects are disabled but shiny is present, still show "Shiny"
            if (additionalAspects.any { it.equals("shiny", ignoreCase = true) }) "§2Aspects: §aShiny" else ""
        }
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
        val aspectsLore = if (CobbleSpawnersConfig.config.globalConfig.showAspectsInGui && additionalAspects.isNotEmpty())
            "§aAspects: §f${additionalAspects.joinToString(", ") { it.replaceFirstChar { it.uppercase() } }}"
        else {
            if (additionalAspects.any { it.equals("shiny", ignoreCase = true) }) "§aAspects: §fShiny" else ""
        }
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

    // ### Utility Functions

    private fun buildPropertiesString(
        species: Species,
        form: FormData,
        additionalAspects: Set<String>,
        showFormsInGui: Boolean
    ): String {
        val propertiesStringBuilder = StringBuilder(species.showdownId())

        // Add form information if enabled
        if (showFormsInGui && form.name != "Standard") {
            if (form.aspects.isNotEmpty()) {
                for (aspect in form.aspects) {
                    // Use aspect=value format for form aspects
                    propertiesStringBuilder.append(" aspect=").append(aspect.lowercase())
                }
            } else {
                propertiesStringBuilder.append(" form=${form.formOnlyShowdownId()}")
            }
        }

        // Always add additional aspects for properties,
        // even if they are not shown in the GUI.
        for (aspect in additionalAspects) {
            if (aspect.contains("=")) {
                propertiesStringBuilder.append(" ").append(aspect.lowercase())
            } else {
                propertiesStringBuilder.append(" aspect=").append(aspect.lowercase())
            }
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
        // When aspects are enabled, add them normally.
        // Otherwise, if "shiny" is present, always add "Shiny".
        if (CobbleSpawnersConfig.config.globalConfig.showAspectsInGui) {
            formAndAspects.addAll(additionalAspects.map { it.replaceFirstChar { it.uppercase() } })
        } else {
            if (additionalAspects.any { it.equals("shiny", ignoreCase = true) }) {
                formAndAspects.add("Shiny")
            }
        }

        val formAndAspectsStr = if (formAndAspects.isNotEmpty()) {
            " (" + formAndAspects.joinToString(", ") + ")"
        } else ""
        return species.name + formAndAspectsStr
    }

    // ### Sorting Functions

    // Computes the full list of variants (with caching unless sort mode is SELECTED)
    private fun getAllVariants(selectedPokemon: List<PokemonSpawnEntry>): List<SpeciesFormVariant> {
        val globalConfig = CobbleSpawnersConfig.config.globalConfig
        val showUnimplemented = globalConfig.showUnimplementedPokemonInGui
        val showFormsInGui = globalConfig.showFormsInGui

        // Create a config key from the three config values
        val configKey = "${showUnimplemented}_${showFormsInGui}_${globalConfig.showAspectsInGui}"

        if (sortMethod != SortMethod.SELECTED) {
            if (cachedVariants != null &&
                cachedSortMethod == sortMethod &&
                cachedSearchTerm == searchTerm &&
                cachedConfigKey == configKey
            ) {
                return cachedVariants!!
            }
        }

        // Generate variants for all applicable species
        val speciesList = when (sortMethod) {
            SortMethod.ALPHABETICAL -> PokemonSpecies.species.filter { showUnimplemented || it.implemented }.sortedBy { it.name }
            SortMethod.TYPE -> PokemonSpecies.species.filter { showUnimplemented || it.implemented }.sortedBy { it.primaryType.name }
            SortMethod.SEARCH -> {
                if (searchTerm.isBlank()) {
                    PokemonSpecies.species.filter { showUnimplemented || it.implemented }.sortedBy { it.name }
                } else {
                    val searchTermLower = searchTerm.lowercase()
                    PokemonSpecies.species.filter {
                        (showUnimplemented || it.implemented) && it.name.lowercase().contains(searchTermLower)
                    }.sortedBy { it.name }
                }
            }
            // For SELECTED, still generate all species then filter variants later.
            SortMethod.SELECTED -> PokemonSpecies.species.filter { showUnimplemented || it.implemented }.sortedBy { it.name }
        }

        val variantsList = mutableListOf<SpeciesFormVariant>()
        for (species in speciesList) {
            val forms = if (showFormsInGui && species.forms.isNotEmpty()) species.forms else listOf(species.standardForm)
            val additionalAspectSets = if (CobbleSpawnersConfig.config.globalConfig.showAspectsInGui) {
                getAdditionalAspectSets(species)
            } else {
                emptyList()
            }
            val variants = forms.flatMap { form ->
                val baseVariants = listOf(
                    SpeciesFormVariant(species, form, emptySet()),
                    SpeciesFormVariant(species, form, setOf("shiny"))
                )
                if (CobbleSpawnersConfig.config.globalConfig.showAspectsInGui) {
                    baseVariants + additionalAspectSets.map { SpeciesFormVariant(species, form, it) }
                } else {
                    baseVariants
                }
            }.distinctBy { it.toKey() }
            variantsList.addAll(variants)
        }

        // When sorting by SELECTED, filter out only those variants that are selected.
        if (sortMethod == SortMethod.SELECTED) {
            return variantsList.filter { variant -> isPokemonSelected(variant, selectedPokemon) }
        }

        cachedVariants = variantsList
        cachedSortMethod = sortMethod
        cachedSearchTerm = searchTerm
        cachedConfigKey = configKey
        return variantsList
    }

    private fun getVariantsForPage(selectedPokemon: List<PokemonSpawnEntry>, page: Int, pageSize: Int): List<SpeciesFormVariant> {
        val allVariants = getAllVariants(selectedPokemon)
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, allVariants.size)
        return if (startIndex < allVariants.size) allVariants.subList(startIndex, endIndex) else emptyList()
    }

    private fun getTotalVariantsCount(selectedPokemon: List<PokemonSpawnEntry>): Int {
        return getAllVariants(selectedPokemon).size
    }

    private fun getAdditionalAspectSets(species: Species): List<Set<String>> {
        return additionalAspectsCache.getOrPut(species.name.lowercase()) {
            val aspectSets = mutableListOf<Set<String>>()
            val speciesSpecificAspects = mutableSetOf<String>()

            try {
                // Create a temporary Pokémon to access its features
                val tempPokemon = species.create()

                // Get aspects from form data directly
                for (form in species.forms) {
                    form.aspects.forEach { aspect ->
                        speciesSpecificAspects.add(aspect)
                    }
                }

                // Use reflection to access the SpeciesFeatures if direct method isn’t available
                val speciesFeatures = try {
                    val featuresClass = Class.forName("com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures")
                    val instanceField = featuresClass.getDeclaredField("INSTANCE")
                    val instance = instanceField.get(null)
                    val getFeaturesMethod = featuresClass.getDeclaredMethod("getFeaturesFor", Class.forName("com.cobblemon.mod.common.pokemon.Species"))
                    getFeaturesMethod.invoke(instance, species) as? Collection<*> ?: emptyList<Any>()
                } catch (e: Exception) {
                    logger.debug("Couldn’t access SpeciesFeatures via reflection: ${e.message}")
                    emptyList<Any>()
                }

                // Process any feature providers found
                speciesFeatures.filterIsInstance<ChoiceSpeciesFeatureProvider>().forEach { provider ->
                    try {
                        val aspects = provider.getAllAspects()
                        aspects.forEach { speciesSpecificAspects.add(it) }
                    } catch (e: Exception) {
                        logger.debug("Error accessing aspects from provider: ${e.message}")
                    }
                }

                // Check for specific hardcoded aspects (for backward compatibility)
                when (species.name.lowercase()) {
                    "forretress" -> speciesSpecificAspects.add("shulker")
                }

                // Add individual aspect sets
                for (aspect in speciesSpecificAspects) {
                    aspectSets.add(setOf(aspect))
                    // Add combined with shiny
                    aspectSets.add(setOf(aspect, "shiny"))
                }

                // Add shiny by itself
                aspectSets.add(setOf("shiny"))

                // Remove duplicates
                aspectSets.distinctBy { it.toSortedSet().joinToString(",") }
            } catch (e: Exception) {
                logger.error("Error in getAdditionalAspectSets for ${species.name}: ${e.message}")
                when (species.name.lowercase()) {
                    else -> listOf(setOf("shiny"))
                }
            }
        }
    }

    // Helper function to convert a PokémonSpawnEntry to a key for matching
    private fun PokemonSpawnEntry.toKey(): String {
        val formName = (this.formName ?: "normal").lowercase()
        return "${this.pokemonName.lowercase()}_${formName}_${this.aspects.map { it.lowercase() }.sorted().joinToString(",")}"
    }

    // ### Button Creation Functions

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
