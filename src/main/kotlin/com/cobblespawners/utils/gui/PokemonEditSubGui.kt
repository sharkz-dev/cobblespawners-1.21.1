package com.cobblespawners.utils.gui

import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.gui.pokemonsettings.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory

object PokemonEditSubGui {
    private val logger = LoggerFactory.getLogger(PokemonEditSubGui::class.java)

    // Sub-GUI slots
    object Slots {
        const val IV_SETTINGS = 11
        const val EV_SETTINGS = 13
        const val SPAWN_SETTINGS = 15
        const val SIZE_SETTINGS = 20
        const val MOVES_SETTINGS = 22
        const val CAPTURE_SETTINGS = 24
        const val PERSISTENCE_SETTINGS = 26  // NUEVO SLOT AGREGADO
        const val OTHER_SETTINGS = 31
        const val BACK = 49
    }

    // Common textures
    object Textures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val IV_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDg4M2Q2NTZlNDljMzhjNmI1Mzc4NTcyZjMxYzYzYzRjN2E1ZGQ0Mzc1YjZlY2JjYTQzZjU5NzFjMmNjNGZmIn19fQ=="
        const val EV_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM0NTI5NjRmMWNiYjg5MTQ2Njg0YWE1NTYzOTBhOThjZjM0MmNhOTdjZWZhNmE5Mjk0YTVkMzZlZGQ5MzBmOSJ9fX0="
        const val SPAWN_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjdkNmJlMWRjYTUzNTJhNTY5M2UyOWVhMzVkODA2YjJhMjdjNGE5N2I2NGVlYmJmNjMyYzk5OGQ1OTQ4ZjFjNCJ9fX0="
        const val SIZE_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmI5MmFiZWI0NGMzNGI5OThhMDE4ZWM1YjYwMjJlOGZjMTU4ZWU4YjEzNDA0YzBmZTZkZDA5MTdmZWQ4NDRlYiJ9fX0="
        const val CAPTURE_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY0YzQ0ODZmOTIzNmY5YTFmYjRiMjFiZjgyM2M1NTZkNmUxNWJmNjg4Yzk2ZDZlZjBkMTc1NTNkYjUwNWIifX19"
        const val OTHER_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWEwMWQxNTZiMTcyMTVjZWYzMzZhZjRjNDRlNmNjOGNjYjI4NWZiMDViYzNmZWI2MmQzMzdmZWIxZjA5MjkwYSJ9fX0="
        const val MOVES_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzJlYmJkYjE4ZDc0NzI4MWI1NDYyZjg1N2VlOTg0Njc1YTM5ZDVhMDI3NDQ0NmEyMmY2NjI2NGE1M2QyYjAzNCJ9fX0="
        const val PERSISTENCE_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
    }

    fun openPokemonEditSubGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        CustomGui.closeGui(player)

        // Get the Pokémon entry
        val standardFormName = formName ?: "Normal"
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(
            spawnerPos, pokemonName, standardFormName, additionalAspects
        )

        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokemon '$pokemonName' with form '$standardFormName' and aspects ${if (additionalAspects.isEmpty()) "\"\"" else additionalAspects.joinToString(", ")} not found in spawner."),
                false
            )
            return
        }

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        // Build the title with aspects
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) additionalAspects.joinToString(", ") else ""
        val subGuiTitle = if (aspectsDisplay.isNotEmpty())
            "Edit Pokémon: ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Normal"}, $aspectsDisplay)"
        else
            "Edit Pokémon: ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Normal"})"

        // Open the sub-GUI
        CustomGui.openGui(
            player,
            subGuiTitle,
            generateSubGuiLayout(),
            { context -> handleSubGuiInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleSubGuiInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        when (context.slotIndex) {
            Slots.IV_SETTINGS -> {
                CustomGui.closeGui(player)
                IVSettingsGui.openIVEditorGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.EV_SETTINGS -> {
                CustomGui.closeGui(player)
                EVSettingsGui.openEVEditorGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.SPAWN_SETTINGS -> {
                CustomGui.closeGui(player)
                SpawnSettingsGui.openSpawnShinyEditorGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.SIZE_SETTINGS -> {
                CustomGui.closeGui(player)
                SizeSettingsGui.openSizeEditorGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.MOVES_SETTINGS -> {
                CustomGui.closeGui(player)
                MovesSettingsGui.openMovesSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.CAPTURE_SETTINGS -> {
                CustomGui.closeGui(player)
                CaptureSettingsGui.openCaptureSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.PERSISTENCE_SETTINGS -> {  // NUEVO CASO AGREGADO
                CustomGui.closeGui(player)
                PersistenceSettingsGui.openPersistenceSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.OTHER_SETTINGS -> {
                CustomGui.closeGui(player)
                OtherSettingsGui.openOtherEditableGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            Slots.BACK -> {
                CustomGui.closeGui(player)
                player.sendMessage(Text.literal("Returning to Pokémon List"), false)
                SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos, SpawnerPokemonSelectionGui.playerPages[player] ?: 0)
            }
            // Do nothing for filler panes - GUI stays open
        }
    }

    private fun generateSubGuiLayout(): List<ItemStack> {
        val subGuiButtons = mapOf(
            Slots.IV_SETTINGS to Triple(
                "Edit IVs", Formatting.GREEN,
                listOf(
                    "§7Fine-tune each stat's Individual Values (IVs)",
                    "§7to maximize overall performance."
                )
            ),
            Slots.EV_SETTINGS to Triple(
                "Edit EVs", Formatting.BLUE,
                listOf(
                    "§7Optimize Effort Values (EVs)",
                    "§7gained from battle encounters."
                )
            ),
            Slots.SPAWN_SETTINGS to Triple(
                "Edit Spawn/Level Chances", Formatting.DARK_AQUA,
                listOf(
                    "§7Customize spawn probabilities",
                    "§7define minimum/maximum level thresholds."
                )
            ),
            Slots.SIZE_SETTINGS to Triple(
                "Edit Size", Formatting.GOLD,
                listOf(
                    "§7Adjust the Pokémon's dimensions",
                    "§7within the spawner for the desired scale."
                )
            ),
            Slots.MOVES_SETTINGS to Triple(
                "Edit Moves", Formatting.YELLOW,
                listOf(
                    "§7Configure the initial moves",
                    "§7that the Pokémon will have when spawned."
                )
            ),
            Slots.CAPTURE_SETTINGS to Triple(
                "Edit Catchable Settings", Formatting.AQUA,
                listOf(
                    "§7Configure catchability parameters",
                    "§7to refine capture mechanics."
                )
            ),
            Slots.PERSISTENCE_SETTINGS to Triple(  // NUEVO BOTÓN AGREGADO
                "Edit Persistence Settings", Formatting.LIGHT_PURPLE,
                listOf(
                    "§7Configure if Pokémon should be persistent",
                    "§7and never despawn naturally."
                )
            ),
            Slots.OTHER_SETTINGS to Triple(
                "Edit Other Stats", Formatting.LIGHT_PURPLE,
                listOf(
                    "§7Modify additional attributes such as level",
                    "§7and miscellaneous performance parameters."
                )
            ),
            Slots.BACK to Triple(
                "Back", Formatting.RED,
                listOf("§7Returns to the spawner Pokémon selection.")
            )
        )

        val textures = mapOf(
            Slots.IV_SETTINGS to Textures.IV_SETTINGS,
            Slots.EV_SETTINGS to Textures.EV_SETTINGS,
            Slots.SPAWN_SETTINGS to Textures.SPAWN_SETTINGS,
            Slots.SIZE_SETTINGS to Textures.SIZE_SETTINGS,
            Slots.MOVES_SETTINGS to Textures.MOVES_SETTINGS,
            Slots.CAPTURE_SETTINGS to Textures.CAPTURE_SETTINGS,
            Slots.PERSISTENCE_SETTINGS to Textures.PERSISTENCE_SETTINGS,  // NUEVA TEXTURA
            Slots.OTHER_SETTINGS to Textures.OTHER_SETTINGS,
            Slots.BACK to Textures.BACK
        )

        return List(54) { slot ->
            if (subGuiButtons.containsKey(slot)) {
                val (text, color, lore) = subGuiButtons[slot]!!
                val textureValue = textures[slot] ?: Textures.BACK
                createButton(text, color, lore, textureValue)
            } else {
                createFillerPane()
            }
        }
    }

    // BUTTON CREATION FUNCTIONS
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