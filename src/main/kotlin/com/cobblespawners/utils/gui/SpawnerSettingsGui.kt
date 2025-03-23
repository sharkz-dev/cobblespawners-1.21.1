package com.cobblespawners.utils.gui

import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblespawners.CobbleSpawners
import com.cobblespawners.utils.*
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos

object SpawnerSettingsGui {

    fun openSpawnerSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        if (spawnerData == null) {
            player.sendMessage(Text.literal("Spawner not found at position $spawnerPos."), false)
            return
        }

        // Ensure spawnRadius and wanderingSettings are not null by assigning defaults
        if (spawnerData.spawnRadius == null) {
            spawnerData.spawnRadius = SpawnRadius() // Default: width = 4, height = 4
            CobbleSpawnersConfig.saveSpawnerData() // Save the updated config
        }
        if (spawnerData.wanderingSettings == null) {
            spawnerData.wanderingSettings = WanderingSettings() // Default: enabled = true, wanderType = "RADIUS", wanderDistance = 6
            CobbleSpawnersConfig.saveSpawnerData() // Save the updated config
        }

        val guiTitle = "Edit Settings for ${spawnerData.spawnerName}"
        val layout = generateSpawnerSettingsLayout(spawnerData)

        val onInteract: (InteractionContext) -> Unit = { context ->
            when (context.slotIndex) {
                11 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Width", context.clickType)
                13 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Timer", context.clickType)
                15 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Height", context.clickType)
                29 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Limit", context.clickType)
                31 -> toggleSpawnerVisibility(context, player, spawnerPos)
                33 -> adjustSpawnerSetting(player, spawnerPos, "Wander Type", context.clickType)
                34 -> toggleWanderingEnabled(context, player, spawnerPos)
                38 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Amount Per Spawn", context.clickType)
                42 -> adjustSpawnerSetting(player, spawnerPos, "Max Distance", context.clickType)
                49 -> {
                    CustomGui.closeGui(player)
                    SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos, SpawnerPokemonSelectionGui.playerPages[player] ?: 0)
                }
                // Ignore clicks on filler slots.
            }
        }

        val onClose: (Inventory) -> Unit = {
            SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(Text.literal("Spawner settings GUI closed"), false)

            // Recalculate spawn positions for the updated settings
            val world = player.server?.getWorld(CobbleSpawners.parseDimension(spawnerData.dimension))
            if (world is ServerWorld) {
                val newPositions = CobbleSpawners.computeValidSpawnPositions(world, spawnerData)
                CobbleSpawners.spawnerValidPositions[spawnerPos] = newPositions
            }
        }

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            onInteract,
            onClose
        )
    }

    private fun generateSpawnerSettingsLayout(spawnerData: SpawnerData): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        // Use defaults if spawnRadius or wanderingSettings are null (though they should be initialized by now)
        val spawnRadius = spawnerData.spawnRadius ?: SpawnRadius()
        val wanderingSettings = spawnerData.wanderingSettings ?: WanderingSettings()

        // --- Row 2 (slots 9-17): Spawn Width, Spawn Timer, Spawn Height ---
        layout[11] = CustomGui.createPlayerHeadButton(
            "spawn_width",
            Text.literal("Spawn Width").styled { it.withColor(Formatting.AQUA) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnRadius.width}"),
                Text.literal("§7Sets the horizontal spawn range around the spawner."),
                Text.literal("§7Larger values increase the spawn area width."),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVhMzUxZDZlMTJiMmNmMTUxYTk3NTZhYzdkNDE5OTA1OTdhYmQzMzcyNTg1MTNkMDY2ZTZkMDkxNGU5NTNiZiJ9fX0="
        )

        layout[13] = CustomGui.createPlayerHeadButton(
            "spawn_timer",
            Text.literal("Spawn Timer (Ticks)").styled { it.withColor(Formatting.YELLOW) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnTimerTicks}"),
                Text.literal("§7Controls how often Pokémon spawn (in ticks)."),
                Text.literal("§7Lower values mean faster spawns."),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA2M2RmYTE1YzZkOGRhNTA2YTJkOTM0MTQ3NjNjYjFmODE5Mzg2ZDJjZjY1NDNjMDhlMjMyZjE2M2ZiMmMxYyJ9fX0="
        )

        layout[15] = CustomGui.createPlayerHeadButton(
            "spawn_height",
            Text.literal("Spawn Height").styled { it.withColor(Formatting.AQUA) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnRadius.height}"),
                Text.literal("§7Sets the vertical spawn range above/below the spawner."),
                Text.literal("§7Larger values increase the spawn area height."),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVhMzUxZDZlMTJiMmNmMTUxYTk3NTZhYzdkNDE5OTA1OTdhYmQzMzcyNTg1MTNkMDY2ZTZkMDkxNGU5NTNiZiJ9fX0="
        )

        // --- Row 4 (slots 27-35): Spawn Limit, Spawner Visibility, Wander Type, Wandering Enabled ---
        layout[29] = CustomGui.createPlayerHeadButton(
            "spawn_limit",
            Text.literal("Spawn Limit").styled { it.withColor(Formatting.LIGHT_PURPLE) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnLimit}"),
                Text.literal("§7Caps the total Pokémon alive at once."),
                Text.literal("§7Higher values allow more mons to be alive at the same time."),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVjNzZjNWY4NTcwOGU4NDg3NTQ2ZDlmZDcwZGM4Y2IwNWU0N2M1ZjU2ZmQyOGQ5NThkOGE0NjJhNGQ2MTUxZSJ9fX0="
        )

        layout[31] = CustomGui.createPlayerHeadButton(
            "spawner_visible",
            Text.literal("Spawner: ${if (spawnerData.visible) "Visible" else "Hidden"}").styled {
                it.withColor(if (spawnerData.visible) Formatting.GREEN else Formatting.RED)
            },
            listOf(
                Text.literal("§7Toggles spawner block visibility."),
                Text.literal("§7Visible: Shows the spawner block."),
                Text.literal("§7Hidden: Makes it invisible in-game."),
                Text.literal(""),
                Text.literal("§eClick to toggle visibility")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODVmZmI1MjMzMmNiZmNiNWJlNTM1NTNkNjdjNzI2NDNiYTJiYjUxN2Y3ZTg5ZGVkNTNkNGE5MmIwMGNlYTczZSJ9fX0="
        )

        layout[33] = CustomGui.createPlayerHeadButton(
            "wander_toggle",
            Text.literal("Wander Type").styled { it.withColor(Formatting.BLUE) },
            listOf(
                Text.literal("§aCurrent: §f${wanderingSettings.wanderType}"),
                Text.literal("§7Defines how Pokémon wandering is limited."),
                Text.literal("§7RADIUS: Stays within spawn area plus extra blocks."),
                Text.literal("§7SPAWNER: Stays near the spawner block itself."),
                Text.literal(""),
                Text.literal("§7Choose based on your control needs."),
                Text.literal("§eClick to toggle")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZmNDQ4ZTNhMzViZWRkYjI1MmVlN2IzMGZlZDY3MTUzYjhhMzI0NTA2NTU2YjRhNzFlYWZjZTVlYjg2YjQ5In19fQ=="
        )

        layout[34] = CustomGui.createPlayerHeadButton(
            "wandering_enabled",
            Text.literal("Wandering: ${if (wanderingSettings.enabled) "ON" else "OFF"}").styled {
                it.withColor(if (wanderingSettings.enabled) Formatting.GREEN else Formatting.RED)
            },
            getWanderingEnabledLore(),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjlhMjhiYTNiYTc5YmUxOTU0NzEwZDRkYjJhM2ZkMjI3NzNmNjE5ZjE4ZmVjZjU5ODIzNTNmYjdhYzE4MzkzYSJ9fX0="
        )

        // --- Row 5 (slots 36-44): Spawn Amount Per Spawn, Max Distance ---
        layout[38] = CustomGui.createPlayerHeadButton(
            "spawn_amount_per_spawn",
            Text.literal("Spawn Amount Per Spawn").styled { it.withColor(Formatting.BLUE) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnAmountPerSpawn}"),
                Text.literal("§7Sets how many Pokémon spawn each time."),
                Text.literal("§7Higher values spawn more per cycle."),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDMzZjVjYzlkZTM1ODVkOGY2NDMzMGY0NDY4ZDE1NmJhZjAzNGEyNWRjYjc3M2MwNDc5ZDdjYTUyNmExM2Q2MSJ9fX0="
        )

        layout[42] = CustomGui.createPlayerHeadButton(
            "max_distance",
            Text.literal("Max Distance").styled { it.withColor(Formatting.GOLD) },
            listOf(
                Text.literal("§aCurrent Value: §f${wanderingSettings.wanderDistance}"),
                Text.literal("§7Sets how far Pokémon can wander."),
                Text.literal("§7RADIUS: Adds blocks beyond spawn area edge."),
                Text.literal("§7SPAWNER: Max blocks from spawner location."),
                Text.literal(""),
                Text.literal("§7Larger values allow more roaming freedom."),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMzZGQ2ZDgzNDBlY2M2NWIyY2I0OGYzNGQ5NTE0YjU2ZjczY2MyZDE1YTE1YWVhNWM3MTBiOTc2YTNjMDA4ZiJ9fX0="
        )

        // --- Row 6 (slots 45-53): Back Button ---
        layout[49] = CustomGui.createPlayerHeadButton(
            "back_button",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE) },
            listOf(
                Text.literal("§7Returns to the spawner Pokémon selection.")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )

        // Fill remaining slots with gray stained glass panes
        for (i in layout.indices) {
            if (layout[i] == ItemStack.EMPTY && i !in setOf(11, 13, 15, 29, 31, 33, 34, 38, 42, 49)) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    CustomGui.setItemLore(this, listOf(" "))
                    setCustomName(Text.literal(" "))
                }
            }
        }

        return layout
    }

    private fun getWanderingEnabledLore(): List<Text> {
        return listOf(
            Text.literal("§7Controls if Pokémon can wander freely."),
            Text.literal("§7ON: Limits them to a set distance."),
            Text.literal("§7OFF: They roam without restriction."),
            Text.literal(""),
            Text.literal("§7Helps keep Pokémon near for battling or farming."),
            Text.literal("§7Prevents them from getting lost or stuck."),
            Text.literal(""),
            Text.literal("§eClick to toggle")
        )
    }

    private fun adjustSpawnerSetting(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        setting: String,
        clickType: ClickType
    ) {
        CobbleSpawnersConfig.updateSpawner(spawnerPos) { spawnerData ->
            val spawnRadius = spawnerData.spawnRadius ?: SpawnRadius().also { spawnerData.spawnRadius = it }
            val wanderingSettings = spawnerData.wanderingSettings ?: WanderingSettings().also { spawnerData.wanderingSettings = it }

            when (setting) {
                "Spawn Timer" -> {
                    spawnerData.spawnTimerTicks = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnTimerTicks - 10).coerceAtLeast(10)
                    } else {
                        spawnerData.spawnTimerTicks + 10
                    }
                }
                "Spawn Width" -> {
                    spawnRadius.width = if (clickType == ClickType.LEFT) {
                        (spawnRadius.width - 1).coerceAtLeast(1)
                    } else {
                        (spawnRadius.width + 1).coerceAtMost(20000)
                    }
                }
                "Spawn Height" -> {
                    spawnRadius.height = if (clickType == ClickType.LEFT) {
                        (spawnRadius.height - 1).coerceAtLeast(1)
                    } else {
                        (spawnRadius.height + 1).coerceAtMost(20000)
                    }
                }
                "Spawn Limit" -> {
                    spawnerData.spawnLimit = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnLimit - 1).coerceAtLeast(1)
                    } else {
                        spawnerData.spawnLimit + 1
                    }
                }
                "Spawn Amount Per Spawn" -> {
                    spawnerData.spawnAmountPerSpawn = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnAmountPerSpawn - 1).coerceAtLeast(1)
                    } else {
                        spawnerData.spawnAmountPerSpawn + 1
                    }
                }
                "Wander Type" -> {
                    wanderingSettings.wanderType =
                        if (wanderingSettings.wanderType.equals("SPAWNER", ignoreCase = true))
                            "RADIUS" else "SPAWNER"
                }
                "Max Distance" -> {
                    wanderingSettings.wanderDistance = if (clickType == ClickType.LEFT) {
                        (wanderingSettings.wanderDistance - 1).coerceAtLeast(1)
                    } else {
                        wanderingSettings.wanderDistance + 1
                    }
                }
            }
        }
        CobbleSpawnersConfig.saveSpawnerData()
        player.sendMessage(Text.literal("$setting adjusted"), false)
        refreshSpawnerGui(player, spawnerPos)
    }

    private fun toggleSpawnerVisibility(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val server = player.server ?: return
        val success = CommandRegistrarUtil.toggleSpawnerVisibility(server, spawnerPos)
        if (success) {
            val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
            if (spawnerData != null) {
                updateGuiItem(
                    context,
                    player,
                    { "Spawner: ${if (spawnerData.visible) "Visible" else "Hidden"}" },
                    spawnerData.visible,
                    if (spawnerData.visible) Formatting.GREEN else Formatting.RED,
                    listOf(
                        Text.literal("§7Toggles spawner block visibility."),
                        Text.literal("§7Visible: Shows the spawner block."),
                        Text.literal("§7Hidden: Makes it invisible in-game."),
                        Text.literal(""),
                        Text.literal("§eClick to toggle visibility")
                    )
                )
                player.sendMessage(Text.literal("Spawner visibility has been toggled."), false)
            }
        } else {
            player.sendMessage(Text.literal("Failed to toggle spawner visibility."), false)
        }
    }

    private fun toggleWanderingEnabled(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos) {
        CobbleSpawnersConfig.updateSpawner(spawnerPos) { spawnerData ->
            val wanderingSettings = spawnerData.wanderingSettings ?: WanderingSettings().also { spawnerData.wanderingSettings = it }
            wanderingSettings.enabled = !wanderingSettings.enabled
        }
        CobbleSpawnersConfig.saveSpawnerData()
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
        if (spawnerData != null) {
            val enabled = spawnerData.wanderingSettings?.enabled ?: true
            updateGuiItem(
                context,
                player,
                { "Wandering: ${if (enabled) "ON" else "OFF"}" },
                enabled,
                if (enabled) Formatting.GREEN else Formatting.RED,
                getWanderingEnabledLore()
            )
            player.sendMessage(Text.literal("Wandering enabled has been toggled."), false)
        }
    }

    private fun updateGuiItem(
        context: InteractionContext,
        player: ServerPlayerEntity,
        nameGenerator: () -> String,
        newValue: Boolean,
        color: Formatting,
        lore: List<Text>
    ) {
        val itemStack = context.clickedStack
        itemStack.setCustomName(
            Text.literal(nameGenerator()).styled {
                it.withColor(color).withBold(false).withItalic(false)
            }
        )
        itemStack.set(DataComponentTypes.LORE, LoreComponent(lore))
        player.currentScreenHandler.slots[context.slotIndex].stack = itemStack
        player.currentScreenHandler.sendContentUpdates()
    }

    private fun refreshSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
        if (spawnerData != null) {
            val layout = generateSpawnerSettingsLayout(spawnerData)
            val screenHandler = player.currentScreenHandler
            layout.forEachIndexed { index, itemStack ->
                if (index < screenHandler.slots.size) {
                    screenHandler.slots[index].stack = itemStack
                }
            }
            screenHandler.sendContentUpdates()
        }
    }
}