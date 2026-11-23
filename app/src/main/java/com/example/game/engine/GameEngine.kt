package com.example.game.engine

import com.example.game.model.*
import com.example.game.multiplayer.LocalMonsterAttackEvent
import com.example.game.multiplayer.LocalMonsterHitEvent
import com.example.game.multiplayer.LocalPlayerActionFxEvent
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RemoteMonsterAttackEvent
import com.example.game.multiplayer.RemoteMonsterHitEvent
import com.example.game.multiplayer.RemotePlayerActionFxEvent
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.SyncedMonsterSnapshot
import com.example.game.world.NpcRole
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import java.util.ArrayDeque
import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object GameEngine {
    private var nextEffectId = 1L
    private var nextMonsterId = 1000

    fun createNewWorld(
        name: String,
        vocation: Vocation = Vocation.ADVENTURER,
        tunicColorIndex: Int = 0,
        hairColorIndex: Int = 0
    ): GameWorldState {
        val cleanName = name.trim().ifEmpty { "Aldric" }.take(18)
        val equipment = when (vocation) {
            Vocation.ADVENTURER -> EquippedGear(
                weaponId = "sunbrass_dirk",
                shieldId = "wooden_shield",
                armorId = "leather_armor",
                bootsId = "leather_boots"
            )
            Vocation.KNIGHT -> EquippedGear(
                weaponId = "sunbrass_dirk",
                shieldId = "wooden_shield",
                armorId = "leather_armor",
                bootsId = "leather_boots"
            )
            Vocation.PALADIN -> EquippedGear(
                weaponId = "ashwood_shortbow",
                armorId = "leather_armor",
                bootsId = "leather_boots",
                ammoId = "ochre_shafts"
            )
            Vocation.MAGE -> EquippedGear(
                weaponId = "sunbrass_scepter",
                armorId = "mystic_robe",
                bootsId = "leather_boots"
            )
        }
        val starterTools = listOf(
            ItemStack("quarry_mattock", 1),
            ItemStack("rope", 1),
            ItemStack("fishing_rod", 1)
        )
        val starterBackpack = when (vocation) {
            Vocation.ADVENTURER -> listOf(
                ItemStack("health_potion", 4),
                ItemStack("mana_potion", 2),
                ItemStack("magic_dart_rune", 3),
                ItemStack("blank_rune", 2),
                ItemStack("cheese", 3)
            )
            Vocation.KNIGHT -> listOf(
                ItemStack("health_potion", 4),
                ItemStack("mana_potion", 1),
                ItemStack("magic_dart_rune", 2),
                ItemStack("blank_rune", 1),
                ItemStack("cheese", 3)
            )
            Vocation.PALADIN -> listOf(
                ItemStack("ochre_shafts", 45),
                ItemStack("health_potion", 3),
                ItemStack("mana_potion", 2),
                ItemStack("magic_dart_rune", 4),
                ItemStack("blank_rune", 2),
                ItemStack("cheese", 3)
            )
            Vocation.MAGE -> listOf(
                ItemStack("health_potion", 2),
                ItemStack("mana_potion", 5),
                ItemStack("magic_dart_rune", 6),
                ItemStack("blank_rune", 4),
                ItemStack("cheese", 2)
            )
        }
        val introLine = if (vocation == Vocation.ADVENTURER) {
            "Train any skill freely! Forge bows or scepters at the Smithy, and visit The Oracle at Level 20."
        } else {
            "Mine Ochre Veins in the West Quarry, smelt at the Smithy Furnace, and forge at the Anvil."
        }
        return GameWorldState(
            saveId = UUID.randomUUID().toString(),
            playerName = cleanName,
            vocation = vocation,
            tunicColorIndex = tunicColorIndex.coerceIn(0, AdventurerPalette.tunicColors.lastIndex),
            hairColorIndex = hairColorIndex.coerceIn(0, AdventurerPalette.hairColors.lastIndex),
            level = 1,
            xp = 0L,
            hp = vocation.baseHp,
            maxHp = vocation.baseHp,
            mp = vocation.baseMp,
            maxMp = vocation.baseMp,
            soul = 100,
            maxSoul = 100,
            maxCapOz = GameWorldState.maxCapForLevel(1, vocation),
            gold = 120,
            floor = 0,
            playerX = WorldMaps.TEMPLE_SPAWN_X,
            playerY = WorldMaps.TEMPLE_SPAWN_Y,
            prevPlayerX = WorldMaps.TEMPLE_SPAWN_X,
            prevPlayerY = WorldMaps.TEMPLE_SPAWN_Y,
            skills = PlayerSkills(),
            equipment = equipment,
            backpack = starterBackpack,
            toolBag = starterTools,
            depot = listOf(ItemStack("health_potion", 2), ItemStack("rope", 1)),
            monsters = WorldMaps.initialMonsters(),
            oreNodes = WorldMaps.initialOreNodes(),
            statusLog = listOf(
                "Welcome to Rookvale, $cleanName the ${vocation.displayName}!",
                introLine
            )
        )
    }

    /**
     * Loads saved data with full backward compatibility:
     * - Legacy V1 Floor 0 coordinates shift (+32, +32) into Rookvale City's anchor.
     * - Existing Knight/Paladin/Mage saves retain their vocation, stats, and classic class sprite.
     * - Any tools sitting in an older save's main backpack auto-migrate into the Tool Bag (50% Cap weight).
     * - If an older save had both a 2H weapon and a shield equipped, the shield is safely returned to backpack.
     */
    fun fromSaveData(save: PlayerSaveData): GameWorldState {
        val voc = runCatching { Vocation.valueOf(save.vocationName) }.getOrDefault(Vocation.ADVENTURER)
        val isLegacyV1 = save.mapVersion < 2
        val isLegacyV2 = save.mapVersion == 2
        val floorMap = WorldMaps.getFloor(save.floor)
        val resolvedRegionId = save.regionId.ifBlank { DEFAULT_REGION_ID }

        var targetX = save.x
        var targetY = save.y
        if (save.floor == 0) {
            if (isLegacyV1) {
                targetX += V1_SURFACE_OFFSET + V2_TO_V3_OFFSET_X
                targetY += V1_SURFACE_OFFSET + V2_TO_V3_OFFSET_Y
            } else if (isLegacyV2) {
                targetX += V2_TO_V3_OFFSET_X
                targetY += V2_TO_V3_OFFSET_Y
            }
        }
        val npcCoordsOnFloor = WorldMaps.npcs.filter { it.floor == save.floor }.map { it.x to it.y }.toSet()
        val isBlockedOrOut = targetX !in 0 until floorMap.width ||
            targetY !in 0 until floorMap.height ||
            !floorMap.tileAt(targetX, targetY).walkable ||
            (targetX to targetY) in npcCoordsOnFloor
        if (isBlockedOrOut) {
            val nudged = WorldMaps.findNearestWalkableTile(save.floor, targetX, targetY, maxRadius = 8)
            val (fx, fy) = nudged ?: WorldMaps.fallbackLandingForFloor(save.floor)
            targetX = fx
            targetY = fy
        }

        val migratedCorpses = save.playerCorpses.map { corpse ->
            var cx = corpse.x
            var cy = corpse.y
            if (corpse.floor == 0) {
                if (isLegacyV1) {
                    cx += V1_SURFACE_OFFSET + V2_TO_V3_OFFSET_X
                    cy += V1_SURFACE_OFFSET + V2_TO_V3_OFFSET_Y
                } else if (isLegacyV2) {
                    cx += V2_TO_V3_OFFSET_X
                    cy += V2_TO_V3_OFFSET_Y
                }
            }
            val cMap = WorldMaps.getFloor(corpse.floor)
            if (cx in 0 until cMap.width && cy in 0 until cMap.height && cMap.tileAt(cx, cy).walkable) {
                corpse.copy(x = cx, y = cy)
            } else {
                val (fx, fy) = WorldMaps.findNearestWalkableTile(corpse.floor, cx, cy, maxRadius = 8)
                    ?: WorldMaps.fallbackLandingForFloor(corpse.floor)
                corpse.copy(x = fx, y = fy)
            }
        }

        // Auto-migrate any tools sitting in backpack into the dedicated Tool Bag
        var migratedToolBag = save.toolBag
        var migratedBackpack = mutableListOf<ItemStack>()
        var movedToolsCount = 0
        for (stack in save.backpack) {
            if (ItemCatalog.get(stack.itemId).isTool) {
                migratedToolBag = addItemPublic(migratedToolBag, stack.itemId, stack.count)
                movedToolsCount += stack.count
            } else {
                migratedBackpack.add(stack)
            }
        }

        // Safely unequip any shield conflicting with a 2H weapon on older saves
        var migratedEquip = save.equipment
        if (migratedEquip.weaponDef()?.isTwoHanded == true && migratedEquip.shieldId != null) {
            migratedBackpack = addItemPublic(migratedBackpack, migratedEquip.shieldId!!, 1).toMutableList()
            migratedEquip = migratedEquip.copy(shieldId = null)
        }

        val hasAnyPickaxe = (migratedBackpack + migratedToolBag + save.depot)
            .any { ItemCatalog.get(it.itemId).category == ItemCategory.TOOL_PICKAXE }
        val migratedDepot = if (isLegacyV1 && !hasAnyPickaxe) {
            addItemPublic(save.depot, "quarry_mattock", 1)
        } else {
            save.depot
        }

        val notes = buildString {
            if (isLegacyV1) append(" (Migrated V1 save — a Quarry Mattock awaits in Depot!)")
            if (movedToolsCount > 0) append(" (Packed $movedToolsCount tool(s) into your Tool Bag!)")
        }
        return GameWorldState(
            saveId = save.slotId,
            playerName = save.name,
            vocation = voc,
            tunicColorIndex = save.tunicColorIndex.coerceIn(0, AdventurerPalette.tunicColors.lastIndex),
            hairColorIndex = save.hairColorIndex.coerceIn(0, AdventurerPalette.hairColors.lastIndex),
            level = save.level,
            xp = save.xp,
            hp = save.hp.coerceIn(1, save.maxHp),
            maxHp = save.maxHp,
            mp = save.mp.coerceIn(0, save.maxMp),
            maxMp = save.maxMp,
            soul = save.soul,
            maxSoul = save.maxSoul,
            maxCapOz = maxOf(save.maxCapOz, GameWorldState.maxCapForLevel(save.level, voc)),
            gold = save.gold,
            regionId = resolvedRegionId,
            floor = save.floor,
            playerX = targetX,
            playerY = targetY,
            prevPlayerX = targetX,
            prevPlayerY = targetY,
            poisonTicks = save.poisonTicks,
            fireTicks = save.fireTicks,
            skills = save.skills,
            equipment = migratedEquip,
            backpack = migratedBackpack,
            toolBag = migratedToolBag,
            depot = migratedDepot,
            monsters = WorldMaps.initialMonsters(),
            oreNodes = WorldMaps.initialOreNodes(),
            corpses = migratedCorpses,
            questFlags = save.questFlags,
            boundAttackRuneId = save.boundAttackRuneId ?: "magic_dart_rune",
            boundHealRuneId = save.boundHealRuneId ?: "healing_rune",
            customHotbarSlots = List(2) { idx -> save.customHotbarSlots.getOrNull(idx) },
            statusLog = listOf("Loaded ${save.name} (Lvl ${save.level} ${voc.displayName}).$notes")
        )
    }

    fun toSaveData(state: GameWorldState): PlayerSaveData {
        return PlayerSaveData(
            slotId = state.saveId,
            name = state.playerName,
            vocationName = state.vocation.name,
            level = state.level,
            xp = state.xp,
            hp = state.hp,
            maxHp = state.maxHp,
            mp = state.mp,
            maxMp = state.maxMp,
            soul = state.soul,
            maxSoul = state.maxSoul,
            maxCapOz = state.maxCapOz,
            gold = state.gold,
            floor = state.floor,
            x = state.playerX,
            y = state.playerY,
            poisonTicks = state.poisonTicks,
            fireTicks = state.fireTicks,
            skills = state.skills,
            equipment = state.equipment,
            backpack = state.backpack.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            toolBag = state.toolBag.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            depot = state.depot.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            playerCorpses = state.corpses.filter { it.isPlayerCorpse }.map { corpse ->
                corpse.copy(items = corpse.items.filter { it.itemId.isNotBlank() && it.count > 0 }.toList())
            }.toList(),
            mapVersion = CURRENT_MAP_VERSION,
            tunicColorIndex = state.tunicColorIndex,
            hairColorIndex = state.hairColorIndex,
            questFlags = state.questFlags.toMap(),
            boundAttackRuneId = state.boundAttackRuneId,
            boundHealRuneId = state.boundHealRuneId,
            customHotbarSlots = List(2) { idx -> state.customHotbarSlots.getOrNull(idx) },
            regionId = state.regionId.ifBlank { DEFAULT_REGION_ID }
        )
    }

    fun handleTileTap(
        state: GameWorldState,
        tx: Int,
        ty: Int,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.deathReport != null) return state
        val floorMap = WorldMaps.getFloor(state.floor)
        if (tx !in 0 until floorMap.width || ty !in 0 until floorMap.height) return state

        // Auto-close any open modal/sheet (Fallen Backpack, Equipment, Backpack, Skills, Crafting, etc.)
        // immediately on map canvas tap so moving, targeting, or interacting works in a single tap.
        val dismissedState = if (state.activePanel != ActivePanel.NONE || state.inspectingCorpseId != null) {
            state.copy(activePanel = ActivePanel.NONE, inspectingCorpseId = null)
        } else {
            state
        }

        // 1. Tap Monster -> toggle red target outline + engage melee tap-to-track Auto-Follow
        val tappedMonster = dismissedState.monsters.firstOrNull { it.floor == dismissedState.floor && it.x == tx && it.y == ty }
        if (tappedMonster != null) {
            val isUntargeting = dismissedState.targetMonsterId == tappedMonster.id
            if (isUntargeting) {
                return dismissedState.copy(
                    targetMonsterId = null,
                    autoFollowTarget = false,
                    walkPath = emptyList()
                ).addLogInternal("Target cleared.")
            }
            val engageMeleeFollow = dismissedState.isMeleeCombatSetup()
            val distToMonster = chebyshev(dismissedState.playerX, dismissedState.playerY, tappedMonster.x, tappedMonster.y)
            val msg = if (engageMeleeFollow && distToMonster > 1) {
                "Targeting ${tappedMonster.type.displayName} (${tappedMonster.hp}/${tappedMonster.maxHp} HP) — pursuing in melee."
            } else {
                "Targeting ${tappedMonster.type.displayName} (${tappedMonster.hp}/${tappedMonster.maxHp} HP)."
            }
            val targeted = dismissedState.copy(
                targetMonsterId = tappedMonster.id,
                autoFollowTarget = engageMeleeFollow,
                walkPath = emptyList(),
                playerFacing = directionToward(dismissedState.playerX, dismissedState.playerY, tappedMonster.x, tappedMonster.y)
            ).addLogInternal(msg)
            return if (engageMeleeFollow && distToMonster > 1) {
                stepAutoFollowOrPath(targeted, nowMs, remotePlayersOnFloor)
            } else {
                targeted
            }
        }

        // Any tap on a non-monster tile cancels melee auto-follow so the player can disengage freely
        val baseState = if (dismissedState.autoFollowTarget) dismissedState.copy(autoFollowTarget = false) else dismissedState

        // 1.5 Tap Remote Player -> Tibia-style Look / Inspect ("You see PlayerName (Level X Vocation).")
        val tappedPlayer = remotePlayersOnFloor.firstOrNull { it.floor == baseState.floor && it.x == tx && it.y == ty }
        if (tappedPlayer != null) {
            return baseState.copy(
                inspectedPlayerId = tappedPlayer.playerId,
                inspectedPlayerUntilMs = nowMs + 2600L,
                walkPath = emptyList()
            ).addLogInternal("You see ${tappedPlayer.name} (Level ${tappedPlayer.level} ${tappedPlayer.vocation.displayName}).")
        }

        val npc = WorldMaps.npcs.firstOrNull { it.floor == baseState.floor && it.x == tx && it.y == ty }
        val tile = floorMap.tileAt(tx, ty)
        val dist = chebyshev(baseState.playerX, baseState.playerY, tx, ty)

        // 2. NPC or Shop Counter (Brother Alden, The Oracle, Alaric)
        if (npc != null || tile == TileType.SHOP_COUNTER) {
            return if (dist <= 2) {
                when (npc?.role) {
                    NpcRole.TEMPLE_PRIEST -> blessAtTemple(baseState, nowMs)
                    NpcRole.ORACLE -> baseState.copy(activePanel = ActivePanel.ORACLE, walkPath = emptyList())
                        .addLogInternal(
                            when {
                                baseState.vocation != Vocation.ADVENTURER -> "The Oracle: 'Walk proudly on your chosen path, ${baseState.vocation.displayName}.'"
                                baseState.level < ORACLE_MIN_LEVEL -> "The Oracle: 'You are not yet ready, child of Rookvale. Return at Level $ORACLE_MIN_LEVEL.'"
                                else -> "The Oracle: 'Level ${baseState.level} achieved! Are you ready to claim your vocation?'"
                            }
                        )
                    else -> baseState.copy(activePanel = ActivePanel.SHOP, walkPath = emptyList())
                        .addLogInternal("Alaric: 'Potions, Blank Runes, Hatchets & sturdy tools — inscribe spells at the Rune Altar!'")
                }
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }

        // 3. Temple Altar
        if (tile == TileType.TEMPLE_ALTAR) {
            return if (dist <= 2) blessAtTemple(baseState, nowMs) else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }

        // 4. Depot Chest & Deep Outpost Mini-Depot
        if (tile == TileType.DEPOT_CHEST || tile == TileType.MINI_DEPOT) {
            return if (dist <= 1) {
                val label = if (tile == TileType.MINI_DEPOT) "Deep Outpost Mini-Depot" else "Rookvale Depot Chest"
                baseState.copy(activePanel = ActivePanel.DEPOT, walkPath = emptyList())
                    .addLogInternal("Opened your $label.")
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }

        // 5. Physical World-Anchored Crafting Stations (Smelting Furnace, Blacksmith Anvil & Fletching Workbench)
        if (tile == TileType.SMELTING_FURNACE) {
            return if (dist <= 1) {
                baseState.copy(activePanel = ActivePanel.FURNACE, walkPath = emptyList())
                    .addLogInternal("Stepped up to the roaring Smelting Furnace.")
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }
        if (tile == TileType.BLACKSMITH_ANVIL) {
            return if (dist <= 1) {
                baseState.copy(activePanel = ActivePanel.ANVIL, walkPath = emptyList())
                    .addLogInternal("Readied your hammer at the Blacksmith Anvil.")
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }
        if (tile == TileType.FLETCHING_BENCH) {
            return if (dist <= 1) {
                baseState.copy(activePanel = ActivePanel.FLETCHING_BENCH, walkPath = emptyList())
                    .addLogInternal("Stepped up to the Fletching Workbench.")
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }
        if (tile == TileType.RUNECRAFTING_TABLE) {
            return if (dist <= 1) {
                baseState.copy(activePanel = ActivePanel.RUNECRAFTING_TABLE, walkPath = emptyList())
                    .addLogInternal("Opened the Arcane Runecrafting Table.")
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }

        // 6. Interactive Ore Veins (Ochre, Bogstone, Brimstone, Aetherium)
        if (tile.oreVeinKind() != null) {
            return if (dist <= 1) {
                CraftingAndMiningEngine.mineOreNode(baseState.copy(walkPath = emptyList()), tx, ty, nowMs)
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }

        // 6.5 Interactive Overland Trees (Hatchet Tree Chopping)
        if (tile == TileType.TREE) {
            return if (dist <= 1) {
                CraftingAndMiningEngine.chopTree(baseState.copy(walkPath = emptyList()), tx, ty, nowMs)
            } else setWalkPathToAdjacent(baseState, tx, ty, remotePlayersOnFloor)
        }

        // 7. Lootable Corpse
        val corpse = baseState.corpses.firstOrNull { it.floor == baseState.floor && it.x == tx && it.y == ty }
        if (corpse != null && dist <= 1) {
            return baseState.copy(
                activePanel = ActivePanel.LOOT_CORPSE,
                inspectingCorpseId = corpse.id,
                walkPath = emptyList()
            )
        }

        // 8. Water -> Fishing
        if (tile == TileType.WATER) {
            return if (dist <= 2) attemptFishing(baseState, tx, ty, nowMs)
            else baseState.addLogInternal("Walk onto the wooden fishing pier to cast your line.")
        }

        // 9. Rope Spot Up
        if (tile == TileType.ROPE_SPOT_UP && dist <= 1) {
            return useRope(baseState, nowMs)
        }

        // 10. Walkable tile -> BFS path (respecting Tibia-style non-PZ player blocking)
        if (tile.walkable) {
            val path = findPath(baseState, tx, ty, remotePlayersOnFloor)
            if (path.isNotEmpty()) {
                return stepAlongPath(baseState.copy(walkPath = path), nowMs, remotePlayersOnFloor)
            }
        }
        return baseState
    }

    fun stepDirection(
        state: GameWorldState,
        dir: Direction,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.deathReport != null) return state
        return attemptMoveTo(
            state.copy(walkPath = emptyList(), autoFollowTarget = false),
            state.playerX + dir.dx,
            state.playerY + dir.dy,
            dir,
            nowMs,
            remotePlayersOnFloor
        )
    }

    /**
     * Advances either a manual tap-to-walk path or active melee Target Auto-Follow pursuit.
     * Re-pathfinds dynamically whenever the targeted monster moves out of melee range (> 1 tile).
     */
    fun stepAutoFollowOrPath(
        state: GameWorldState,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.deathReport != null) return state
        if (state.walkPath.isNotEmpty()) {
            return stepAlongPath(state, nowMs, remotePlayersOnFloor)
        }
        if (!state.autoFollowTarget) return state
        val targetId = state.targetMonsterId ?: return state.copy(autoFollowTarget = false)
        if (!state.isMeleeCombatSetup()) return state.copy(autoFollowTarget = false)

        val target = state.monsters.firstOrNull { it.id == targetId && it.floor == state.floor }
            ?: return state.copy(autoFollowTarget = false)

        val dist = chebyshev(state.playerX, state.playerY, target.x, target.y)
        if (dist <= 1) {
            val face = directionToward(state.playerX, state.playerY, target.x, target.y)
            return if (state.playerFacing != face) state.copy(playerFacing = face) else state
        }

        val path = findPathToAdjacentOfTarget(state, target.x, target.y, remotePlayersOnFloor)
        if (path.isEmpty()) return state

        val nextStep = path.first()
        val dx = nextStep.first - state.playerX
        val dy = nextStep.second - state.playerY
        val dir = when {
            dx > 0 -> Direction.EAST
            dx < 0 -> Direction.WEST
            dy > 0 -> Direction.SOUTH
            else -> Direction.NORTH
        }
        return attemptMoveTo(state, nextStep.first, nextStep.second, dir, nowMs, remotePlayersOnFloor)
    }

    fun stepAlongPath(
        state: GameWorldState,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.deathReport != null || state.walkPath.isEmpty()) return state
        val (nx, ny) = state.walkPath.first()
        val remaining = state.walkPath.drop(1)
        val dx = nx - state.playerX
        val dy = ny - state.playerY
        val dir = when {
            dx > 0 -> Direction.EAST
            dx < 0 -> Direction.WEST
            dy > 0 -> Direction.SOUTH
            else -> Direction.NORTH
        }
        val moved = attemptMoveTo(state.copy(walkPath = remaining), nx, ny, dir, nowMs, remotePlayersOnFloor)
        return if (moved.playerX == state.playerX && moved.playerY == state.playerY && moved.floor == state.floor) {
            moved.copy(walkPath = emptyList())
        } else {
            moved
        }
    }

    /**
     * Classic Tibia player interference rule:
     * - Inside Protection Zones (Temple & Town) players pass freely through one another.
     * - On floor-change portals (stairs / rope holes) players may stack so entrances cannot be trapped.
     * - Everywhere else (Wilds & Dungeons), tiles occupied by another player are solid/blocked.
     */
    internal fun isBlockedByRemotePlayer(
        floorMap: WorldFloor,
        x: Int,
        y: Int,
        remotePlayersOnFloor: List<RemotePlayerState>
    ): Boolean {
        if (remotePlayersOnFloor.isEmpty()) return false
        if (floorMap.isProtectionZone(x, y)) return false
        val tile = floorMap.tileAt(x, y)
        if (tile == TileType.STAIRS_DOWN || tile == TileType.STAIRS_UP ||
            tile == TileType.ROPE_HOLE_DOWN || tile == TileType.ROPE_SPOT_UP
        ) {
            return false
        }
        return remotePlayersOnFloor.any { it.floor == floorMap.floorId && it.x == x && it.y == y }
    }

    private fun attemptMoveTo(
        state: GameWorldState,
        nx: Int,
        ny: Int,
        dir: Direction,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        val tile = floorMap.tileAt(nx, ny)
        if (!tile.walkable) return state.copy(playerFacing = dir)

        val blockedByMonster = state.monsters.any { it.floor == state.floor && it.x == nx && it.y == ny }
        val blockedByNpc = WorldMaps.npcs.any { it.floor == state.floor && it.x == nx && it.y == ny }
        val blockedByPlayer = isBlockedByRemotePlayer(floorMap, nx, ny, remotePlayersOnFloor)
        if (blockedByMonster || blockedByNpc || blockedByPlayer) return state.copy(playerFacing = dir)

        val portal = WorldMaps.portalAt(state.floor, nx, ny, requiresRope = false)
        if (portal != null) {
            val destFloor = WorldMaps.getFloor(portal.toFloor)
            val isDescending = portal.toFloor < state.floor
            val msg = when {
                isDescending && tile == TileType.ROPE_HOLE_DOWN ->
                    "You descend to Floor ${portal.toFloor}: ${destFloor.name}. Danger Level: ${destFloor.dangerLevel}! (${destFloor.recommendedLevel}) Bring a Rope!"
                isDescending ->
                    "You descend to Floor ${portal.toFloor}: ${destFloor.name}. Danger Level: ${destFloor.dangerLevel}! (${destFloor.recommendedLevel})"
                else ->
                    "Entered ${destFloor.name} (${destFloor.subtitle})."
            }
            val banner = if (isDescending) {
                FloorWarningBanner(
                    floorId = destFloor.floorId,
                    floorName = destFloor.name,
                    dangerLevel = destFloor.dangerLevel,
                    recommendedLevel = destFloor.recommendedLevel,
                    severity = destFloor.dangerSeverity,
                    createdAtMs = nowMs
                )
            } else {
                null
            }
            val updatedFlags = if (portal.toFloor < 0) {
                state.questFlags + ("visited_floor_${portal.toFloor}" to true)
            } else {
                state.questFlags
            }
            return state.copy(
                floor = portal.toFloor,
                playerX = portal.toX,
                playerY = portal.toY,
                prevPlayerX = portal.toX,
                prevPlayerY = portal.toY,
                lastPlayerMoveMs = nowMs,
                playerFacing = dir,
                walkPath = emptyList(),
                targetMonsterId = null,
                autoFollowTarget = false,
                activePanel = ActivePanel.NONE,
                floorWarningBanner = banner,
                questFlags = updatedFlags
            ).addLogInternal(msg)
        }

        // Auto-close station panels if the player walks away from the physical station!
        val moved = state.copy(
            prevPlayerX = state.playerX,
            prevPlayerY = state.playerY,
            playerX = nx,
            playerY = ny,
            lastPlayerMoveMs = nowMs,
            playerFacing = dir
        )
        val shouldCloseStation = when (moved.activePanel) {
            ActivePanel.FURNACE -> !CraftingAndMiningEngine.isNearStation(moved, TileType.SMELTING_FURNACE)
            ActivePanel.ANVIL -> !CraftingAndMiningEngine.isNearStation(moved, TileType.BLACKSMITH_ANVIL)
            ActivePanel.FLETCHING_BENCH -> !CraftingAndMiningEngine.isNearStation(moved, TileType.FLETCHING_BENCH)
            ActivePanel.RUNECRAFTING_TABLE -> !CraftingAndMiningEngine.isNearStation(moved, TileType.RUNECRAFTING_TABLE)
            ActivePanel.DEPOT -> !CraftingAndMiningEngine.isNearStation(moved, TileType.DEPOT_CHEST) &&
                !CraftingAndMiningEngine.isNearStation(moved, TileType.MINI_DEPOT)
            ActivePanel.LOOT_CORPSE -> {
                val c = moved.corpses.firstOrNull { it.id == moved.inspectingCorpseId }
                c == null || c.floor != moved.floor || chebyshev(moved.playerX, moved.playerY, c.x, c.y) > 1
            }
            ActivePanel.ORACLE -> moved.floor != 0 || chebyshev(moved.playerX, moved.playerY, WorldMaps.ORACLE_X, WorldMaps.ORACLE_Y) > 2
            else -> false
        }
        return if (shouldCloseStation) {
            moved.copy(activePanel = ActivePanel.NONE, inspectingCorpseId = null)
        } else moved
    }

    /**
     * Classic Tibia Level 20 Oracle Vocation Ceremony.
     * Preserves all earned skill levels and grants a generous immediate weight limit (Cap) surge
     * plus boosted per-level Cap growth for the chosen vocation.
     */
    fun promoteVocation(state: GameWorldState, chosen: Vocation, nowMs: Long): GameWorldState {
        if (state.vocation != Vocation.ADVENTURER) {
            return state.copy(activePanel = ActivePanel.NONE)
                .addLogInternal("Your destiny as a ${state.vocation.displayName} is already sealed.")
        }
        if (state.level < ORACLE_MIN_LEVEL) {
            return state.addLogInternal("The Oracle: 'Return when you have reached Level $ORACLE_MIN_LEVEL.'")
        }
        if (chosen !in Vocation.ceremonyVocations) return state

        val newMaxCap = maxOf(
            state.maxCapOz + chosen.promotionCapBonus,
            GameWorldState.maxCapForLevel(state.level, chosen)
        )
        val capBonusGained = (newMaxCap - state.maxCapOz).roundToInt()

        return state.copy(
            vocation = chosen,
            hp = state.maxHp,
            mp = state.maxMp,
            maxCapOz = newMaxCap,
            poisonTicks = 0,
            fireTicks = 0,
            activePanel = ActivePanel.NONE,
            questFlags = state.questFlags + ("oracle_vocation_chosen" to true)
        )
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "${chosen.displayName.uppercase()}! +$capBonusGained oz CAP", FloatingTextKind.SKILL_UP, nowMs)
            .addLogInternal("The Oracle has blessed you as a ${chosen.displayName}! Instant +$capBonusGained oz Cap bonus (+${chosen.capPerLevel.toInt()} oz/lvl ahead)!")
    }

    /**
     * High-frequency 200ms sub-tick for variable weapon attack speeds (Fast 0.8s, Balanced 1.0s, Heavy 1.3-1.4s).
     */
    fun processPlayerAttackSubTick(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        val floorMap = WorldMaps.getFloor(state.floor)
        if (floorMap.isProtectionZone(state.playerX, state.playerY)) return state
        val target = state.monsters.firstOrNull { it.id == state.targetMonsterId && it.floor == state.floor } ?: return state

        val weapon = state.equipment.weaponDef()
        val intervalMs = weapon?.attackIntervalMs ?: 1000L
        if (nowMs - state.lastPlayerAttackMs < intervalMs) return state

        return performPlayerAutoAttack(state.copy(lastPlayerAttackMs = nowMs), target, nowMs)
    }

    /**
     * 1.0s World Tick: Temple PZ regen, Poison/Fire ticks, Monster AI, Respawns, Ore Node regen, and Death check.
     * Also triggers a player attack check if called directly in deterministic unit tests.
     */
    fun processCombatTick(
        state: GameWorldState,
        nowMs: Long,
        isFloorHost: Boolean = true,
        localPlayerId: String = state.saveId,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.deathReport != null) return pruneExpiredEffects(state, nowMs)
        var s = pruneExpiredEffects(state, nowMs).copy(
            tickCount = state.tickCount + 1,
            lastCombatTickMs = nowMs
        )

        val floorMap = WorldMaps.getFloor(s.floor)
        val inPz = floorMap.isProtectionZone(s.playerX, s.playerY)

        // 1. Temple Protection Zone passive healing & status cure
        if (inPz) {
            val healedHp = (s.hp + 15).coerceAtMost(s.maxHp)
            val healedMp = (s.mp + 15).coerceAtMost(s.maxMp)
            val cured = s.poisonTicks > 0 || s.fireTicks > 0
            s = s.copy(hp = healedHp, mp = healedMp, poisonTicks = 0, fireTicks = 0)
            if (cured) {
                s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "CURED!", FloatingTextKind.HEAL, nowMs)
                    .addLogInternal("The Temple sanctuary cleansed your afflictions.")
            }
        } else if (s.tickCount % 3L == 0L && s.poisonTicks == 0 && s.fireTicks == 0) {
            s = s.copy(
                hp = (s.hp + 2).coerceAtMost(s.maxHp),
                mp = (s.mp + 3).coerceAtMost(s.maxMp),
                soul = (s.soul + 1).coerceAtMost(s.maxSoul)
            )
        }

        // 2. Player attack check (respects variable weapon interval)
        s = processPlayerAttackSubTick(s, nowMs)

        // 3. Poison & Fire status ticks
        if (s.poisonTicks > 0) {
            val pDmg = Random.nextInt(4, 9)
            s = s.copy(hp = (s.hp - pDmg).coerceAtLeast(0), poisonTicks = s.poisonTicks - 1)
                .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$pDmg", FloatingTextKind.POISON, nowMs)
                .queuePlayerFx("-$pDmg", FloatingTextKind.POISON, PlayerActionState.IDLE)
        }
        if (s.fireTicks > 0 && s.hp > 0) {
            val fDmg = Random.nextInt(8, 15)
            s = s.copy(hp = (s.hp - fDmg).coerceAtLeast(0), fireTicks = s.fireTicks - 1)
                .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$fDmg", FloatingTextKind.FIRE, nowMs)
                .queuePlayerFx("-$fDmg", FloatingTextKind.FIRE, PlayerActionState.IDLE)
        }

        // 4. Monster AI on active floor (Host moves & elects targetPlayerId; Non-host resolves attacks targeting local player)
        if (s.hp > 0) {
            s = processMonstersTick(
                state = s,
                floorMap = floorMap,
                playerInPz = inPz,
                nowMs = nowMs,
                isFloorHost = isFloorHost,
                localPlayerId = localPlayerId.ifBlank { s.saveId },
                remotePlayersOnFloor = remotePlayersOnFloor
            )
        }

        // 5. Monster respawns (host-driven), Ore Vein & Chopped Tree regeneration
        if (isFloorHost) {
            s = processRespawns(s, remotePlayersOnFloor)
        }
        s = CraftingAndMiningEngine.regenerateOreNodesTick(s)
        s = CraftingAndMiningEngine.regenerateTreesTick(s)

        // 6. Death penalty check
        if (s.hp <= 0) {
            s = handlePlayerDeath(s, nowMs)
        }
        return s
    }

    /**
     * Applies monster armor mitigation so weapon upgrades and skill training noticeably matter
     * against heavy-armored dungeon foes (Minotaurs, Orc Warriors, Cyclopses, Bosses).
     * Magic and runes pierce 45% of physical armor.
     */
    internal fun mitigateMonsterDamage(rawDamage: Int, monsterArmor: Int, isMagic: Boolean): Int {
        if (monsterArmor <= 0) return rawDamage.coerceAtLeast(1)
        val armorFactor = if (isMagic) 0.55 else 1.0
        val absorbed = (monsterArmor * armorFactor * Random.nextDouble(0.45, 0.85)).roundToInt()
        val floorMin = if (isMagic) 2 else 1
        return (rawDamage - absorbed).coerceAtLeast(floorMin)
    }

    private fun performPlayerAutoAttack(state: GameWorldState, target: MonsterEntity, nowMs: Long): GameWorldState {
        var s = state
        val weapon = s.equipment.weaponDef()
        val dist = chebyshev(s.playerX, s.playerY, target.x, target.y)
        val effectiveRange = weapon?.range ?: 1
        if (dist > effectiveRange) return s

        // Turn character sprite toward the target
        s = s.copy(playerFacing = directionToward(s.playerX, s.playerY, target.x, target.y))

        // Soft skill-requirement scaling (-35% dmg) & Tier 3-4 off-vocation penalty (-25% dmg)
        val reqType = weapon?.requiredSkillType
        val skillPenaltyMult = if (reqType != null && s.skills.levelFor(reqType) < weapon.requiredSkillLevel) 0.65 else 1.0
        val offVocationMult = s.weaponOffVocationMultiplier(weapon)

        return when (weapon?.category) {
            ItemCategory.WEAPON_WAND -> {
                if (s.mp >= weapon.manaCost) {
                    val magicMult = s.magicSkillMultiplier()
                    val basePower = weapon.attack + s.level * 0.65
                    val rawDmg = (basePower * magicMult * Random.nextDouble(0.85, 1.18) * skillPenaltyMult * offVocationMult)
                        .roundToInt().coerceAtLeast(6)
                    val dmg = mitigateMonsterDamage(rawDmg, target.type.armor, isMagic = true)
                    s = s.copy(mp = s.mp - weapon.manaCost)
                    s = awardMagicXpFromMana(s, weapon.manaCost, nowMs)
                    s = s.addProjectileInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), target.x.toFloat(), target.y.toFloat(), ProjectileKind.ENERGY_BOLT, nowMs)
                    s = s.queuePlayerFx(null, null, PlayerActionState.CASTING)
                    applyDamageToMonster(s, target.id, dmg, nowMs, projectileKind = ProjectileKind.ENERGY_BOLT)
                } else if (dist <= 1) {
                    val meleeMult = s.attackSkillMultiplier(s.skills.fist.level)
                    val rawDmg = (Random.nextInt(4, 8) * meleeMult).roundToInt().coerceAtLeast(3)
                    val dmg = mitigateMonsterDamage(rawDmg, target.type.armor, isMagic = false)
                    s = advanceSkillPublic(s, SkillType.FIST, 1.5, nowMs)
                    s = s.queuePlayerFx(null, null, PlayerActionState.ATTACKING)
                    applyDamageToMonster(s, target.id, dmg, nowMs)
                } else {
                    s.addLogInternal("Low Mana for ${weapon.name}! Drink a Mana Potion.")
                }
            }
            ItemCategory.WEAPON_DISTANCE -> {
                val neededKind = weapon.requiredAmmoKind
                var ammoAtkBonus = 0
                var projKind = ProjectileKind.SPEAR

                if (neededKind != null) {
                    val equippedAmmo = s.equipment.ammoDef()
                    val chosenAmmoId = when {
                        equippedAmmo != null && equippedAmmo.ammoKind == neededKind && s.hasItem(equippedAmmo.id) -> equippedAmmo.id
                        else -> s.backpack.firstOrNull {
                            val d = ItemCatalog.get(it.itemId)
                            d.category == ItemCategory.AMMO && d.ammoKind == neededKind && it.count > 0
                        }?.itemId
                    }
                    if (chosenAmmoId == null) {
                        val kindLabel = if (neededKind == AmmoKind.SHAFTS) "Shafts" else "Quarrels"
                        return s.addLogInternal("Out of $kindLabel for ${weapon.name}! Forge more at an Anvil.")
                    }
                    val ammoDef = ItemCatalog.get(chosenAmmoId)
                    ammoAtkBonus = ammoDef.attack
                    projKind = if (neededKind == AmmoKind.QUARRELS) ProjectileKind.BOLT else ProjectileKind.ARROW
                    s = s.copy(
                        equipment = s.equipment.copy(ammoId = chosenAmmoId),
                        backpack = removeItemPublic(s.backpack, chosenAmmoId, 1)
                    )
                }

                val hitChance = (0.76 + (s.skills.distance.level - 10) * 0.015).coerceAtMost(0.96)
                s = advanceSkillPublic(s, SkillType.DISTANCE, 3.0, nowMs)
                s = s.addProjectileInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), target.x.toFloat(), target.y.toFloat(), projKind, nowMs)
                if (Random.nextDouble() <= hitChance) {
                    val distMult = s.attackSkillMultiplier(s.skills.distance.level)
                    val basePower = weapon.attack + ammoAtkBonus + s.level * 0.60
                    val rawDmg = (basePower * distMult * Random.nextDouble(0.80, 1.20) * skillPenaltyMult * offVocationMult)
                        .roundToInt().coerceAtLeast(5)
                    val dmg = mitigateMonsterDamage(rawDmg, target.type.armor, isMagic = false)
                    s = s.queuePlayerFx(null, null, PlayerActionState.ATTACKING)
                    applyDamageToMonster(s, target.id, dmg, nowMs, projectileKind = projKind)
                } else {
                    s.addFloatingInternal(s.floor, target.x.toFloat(), target.y.toFloat(), "MISS", FloatingTextKind.BLOCK, nowMs)
                        .queuePlayerFx("MISS", FloatingTextKind.BLOCK, PlayerActionState.ATTACKING, projKind, target.x, target.y)
                }
            }
            else -> {
                val baseAtk = weapon?.attack ?: 6
                val meleeSkillType = PlayerSkills.meleeSkillTypeForWeapon(weapon?.id)
                s = advanceSkillPublic(s, meleeSkillType, 3.2, nowMs)
                val activeLevel = s.skills.levelFor(meleeSkillType)
                val meleeMult = s.attackSkillMultiplier(activeLevel)
                val basePower = baseAtk + s.level * 0.60
                val rawDmg = (basePower * meleeMult * Random.nextDouble(0.80, 1.20) * skillPenaltyMult * offVocationMult)
                    .roundToInt().coerceAtLeast(4)
                val dmg = mitigateMonsterDamage(rawDmg, target.type.armor, isMagic = false)
                s = s.queuePlayerFx(null, null, PlayerActionState.ATTACKING)
                applyDamageToMonster(s, target.id, dmg, nowMs)
            }
        }
    }

    private fun applyDamageToMonster(
        state: GameWorldState,
        monsterId: Int,
        damage: Int,
        nowMs: Long,
        customLog: String? = null,
        projectileKind: ProjectileKind? = null
    ): GameWorldState {
        val m = state.monsters.firstOrNull { it.id == monsterId } ?: return state
        val localId = state.saveId.ifBlank { "local" }
        val newHp = (m.hp - damage).coerceAtLeast(0)
        val updatedContributors = m.contributors + localId

        val hitEvent = LocalMonsterHitEvent(
            monsterId = m.id,
            floor = m.floor,
            damage = damage,
            resultingHp = newHp,
            monsterTypeName = m.type.displayName,
            isBoss = m.type.isBoss,
            projectileKindName = projectileKind?.name,
            fromX = state.playerX,
            fromY = state.playerY
        )

        var s = state
            .copy(outboundMonsterHits = state.outboundMonsterHits + hitEvent)
            .addFloatingInternal(m.floor, m.x.toFloat(), m.y.toFloat(), "-$damage", FloatingTextKind.DAMAGE_MONSTER, nowMs)
        if (customLog != null) s = s.addLogInternal(customLog)

        if (newHp > 0) {
            return s.copy(
                monsters = s.monsters.map {
                    if (it.id == monsterId) it.copy(hp = newHp, contributors = updatedContributors) else it
                }
            )
        }

        // Monster slain: local player contributed, so spawn their personal instanced loot drop
        val corpse = generateMonsterCorpse(m)
        val remainingMonsters = s.monsters.filterNot { it.id == monsterId }
        val respawnTicks = if (m.type.isBoss) 90 else 32
        val updatedRespawns = if (!m.isSummon) {
            s.pendingRespawns + PendingRespawn(
                type = m.type,
                floor = m.floor,
                spawnX = m.spawnX,
                spawnY = m.spawnY,
                ticksRemaining = respawnTicks,
                originalMonsterId = m.id
            )
        } else {
            s.pendingRespawns
        }
        val bossFlags = if (m.type.isBoss) {
            s.questFlags + ("slain_${m.type.name.lowercase()}" to true)
        } else {
            s.questFlags
        }
        s = s.copy(
            monsters = remainingMonsters,
            targetMonsterId = if (s.targetMonsterId == monsterId) null else s.targetMonsterId,
            autoFollowTarget = if (s.targetMonsterId == monsterId) false else s.autoFollowTarget,
            corpses = (s.corpses + corpse).takeLast(38),
            pendingRespawns = updatedRespawns,
            questFlags = bossFlags
        )
        s = grantExperience(s, m.type.xpReward.toLong(), m.x.toFloat(), m.y.toFloat(), nowMs)
        return s.addLogInternal("Slain ${m.type.displayName}! (+${m.type.xpReward} XP) Tap corpse to loot.")
    }

    /**
     * Applies a co-op teammate's broadcast hit on a shared floor monster.
     * Shows floating damage + optional projectile, updates HP, and when HP reaches 0,
     * spawns a personal instanced loot drop & XP locally ONLY if the local player also dealt damage.
     */
    fun applyRemoteMonsterHit(
        state: GameWorldState,
        event: RemoteMonsterHitEvent,
        localPlayerId: String,
        isFloorHost: Boolean,
        nowMs: Long
    ): GameWorldState {
        if (event.floor != state.floor) return state
        val m = state.monsters.firstOrNull { it.id == event.monsterId && it.floor == event.floor } ?: return state
        var s = state.addFloatingInternal(m.floor, m.x.toFloat(), m.y.toFloat(), "-${event.damage}", FloatingTextKind.DAMAGE_MONSTER, nowMs)
        val proj = event.projectileKindName?.let { runCatching { ProjectileKind.valueOf(it) }.getOrNull() }
        if (proj != null) {
            s = s.addProjectileInternal(m.floor, event.fromX.toFloat(), event.fromY.toFloat(), m.x.toFloat(), m.y.toFloat(), proj, nowMs)
        }

        val updatedContributors = m.contributors + event.attackerId
        val newHp = minOf((m.hp - event.damage).coerceAtLeast(0), event.resultingHp).coerceAtLeast(0)

        if (newHp > 0) {
            return s.copy(
                monsters = s.monsters.map {
                    if (it.id == m.id) it.copy(hp = newHp, contributors = updatedContributors) else it
                }
            )
        }

        // Monster defeated by teammate!
        val remainingMonsters = s.monsters.filterNot { it.id == m.id }
        val respawnTicks = if (m.type.isBoss) 90 else 32
        val newRespawns = if (isFloorHost && !m.isSummon) {
            s.pendingRespawns + PendingRespawn(
                type = m.type,
                floor = m.floor,
                spawnX = m.spawnX,
                spawnY = m.spawnY,
                ticksRemaining = respawnTicks,
                originalMonsterId = m.id
            )
        } else {
            s.pendingRespawns
        }

        val localContributed = localPlayerId.isNotBlank() && localPlayerId in updatedContributors
        s = s.copy(
            monsters = remainingMonsters,
            targetMonsterId = if (s.targetMonsterId == m.id) null else s.targetMonsterId,
            autoFollowTarget = if (s.targetMonsterId == m.id) false else s.autoFollowTarget,
            pendingRespawns = newRespawns
        )

        if (localContributed) {
            val personalCorpse = generateMonsterCorpse(m)
            s = s.copy(corpses = (s.corpses + personalCorpse).takeLast(38))
            s = grantExperience(s, m.type.xpReward.toLong(), m.x.toFloat(), m.y.toFloat(), nowMs)
            s = s.addLogInternal("Co-op kill: ${m.type.displayName}! (+${m.type.xpReward} XP) Personal loot dropped.")
        } else {
            s = s.addLogInternal("${event.attackerName} slew ${m.type.displayName}.")
        }
        return s
    }

    /**
     * Applies authoritative host monster positions, HP, targetPlayerId, and respawns on non-host clients
     * while preserving local contributor tracking so personal instanced loot is never lost.
     */
    fun applyRemoteMonsterSync(
        state: GameWorldState,
        floor: Int,
        snapshots: List<SyncedMonsterSnapshot>,
        localPlayerId: String,
        nowMs: Long
    ): GameWorldState {
        if (floor != state.floor) return state
        val existingOnFloor = state.monsters.filter { it.floor == floor }.associateBy { it.id }
        val otherFloorMonsters = state.monsters.filter { it.floor != floor }
        val syncedIds = snapshots.map { it.id }.toSet()

        var s = state

        // Check if any monster we contributed to died on the host while a hit packet was missed
        for ((id, oldM) in existingOnFloor) {
            if (id !in syncedIds && localPlayerId.isNotBlank() && localPlayerId in oldM.contributors) {
                val personalCorpse = generateMonsterCorpse(oldM)
                s = s.copy(corpses = (s.corpses + personalCorpse).takeLast(38))
                s = grantExperience(s, oldM.type.xpReward.toLong(), oldM.x.toFloat(), oldM.y.toFloat(), nowMs)
                s = s.addLogInternal("Co-op kill: ${oldM.type.displayName}! (+${oldM.type.xpReward} XP) Personal loot dropped.")
            }
        }

        val syncedOnFloor = snapshots.mapNotNull { snap ->
            if (snap.hp <= 0) return@mapNotNull null
            val existing = existingOnFloor[snap.id]
            val type = existing?.type ?: MonsterType.fromWireName(snap.typeName) ?: return@mapNotNull null
            val moved = existing != null && (existing.x != snap.x || existing.y != snap.y)
            MonsterEntity(
                id = snap.id,
                type = type,
                floor = floor,
                x = snap.x,
                y = snap.y,
                spawnX = existing?.spawnX ?: snap.spawnX,
                spawnY = existing?.spawnY ?: snap.spawnY,
                hp = snap.hp.coerceIn(1, snap.maxHp),
                maxHp = snap.maxHp,
                facing = existing?.facing ?: Direction.SOUTH,
                moveCooldown = existing?.moveCooldown ?: 0,
                animOffset = existing?.animOffset ?: ((snap.id * 0.37f) % 1f),
                lastMoveFromX = if (moved) existing!!.x else snap.x,
                lastMoveFromY = if (moved) existing!!.y else snap.y,
                lastMoveTimeMs = if (moved) nowMs else (existing?.lastMoveTimeMs ?: nowMs),
                targetPlayerId = snap.targetPlayerId,
                contributors = existing?.contributors ?: emptySet(),
                isSummon = snap.isSummon || (existing?.isSummon == true),
                summonerId = existing?.summonerId,
                pendingAoeTiles = snap.pendingAoeTiles
            )
        }

        val newTarget = if (s.targetMonsterId != null && syncedOnFloor.none { it.id == s.targetMonsterId }) {
            null
        } else {
            s.targetMonsterId
        }
        return s.copy(
            monsters = otherFloorMonsters + syncedOnFloor,
            targetMonsterId = newTarget,
            autoFollowTarget = if (newTarget == null) false else s.autoFollowTarget
        )
    }

    /**
     * Applies a remote monster attack broadcast (ranged projectile, telegraphed Boss Fire Wave detonation,
     * or status cue) so all co-op players on the same floor see synchronized projectiles and AoE waves.
     */
    fun applyRemoteMonsterAttack(
        state: GameWorldState,
        event: RemoteMonsterAttackEvent,
        localPlayerId: String,
        nowMs: Long
    ): GameWorldState {
        if (event.floor != state.floor || state.deathReport != null) return state
        var s = state
        val proj = event.projectileKindName?.let { runCatching { ProjectileKind.valueOf(it) }.getOrNull() }
        if (proj != null && event.aoeTiles.isEmpty()) {
            s = s.addProjectileInternal(
                s.floor,
                event.fromX.toFloat(),
                event.fromY.toFloat(),
                event.toX.toFloat(),
                event.toY.toFloat(),
                proj,
                nowMs,
                300L
            )
        }

        if (event.aoeTiles.isNotEmpty()) {
            val burstProj = proj ?: ProjectileKind.FIRE_BREATH
            for ((ax, ay) in event.aoeTiles.take(10)) {
                s = s.addProjectileInternal(
                    s.floor,
                    event.fromX.toFloat(),
                    event.fromY.toFloat(),
                    ax.toFloat(),
                    ay.toFloat(),
                    burstProj,
                    nowMs,
                    340L
                )
            }
            val floorMap = WorldMaps.getFloor(s.floor)
            val inPz = floorMap.isProtectionZone(s.playerX, s.playerY)
            if (!inPz && s.hp > 0 && (s.playerX to s.playerY) in event.aoeTiles) {
                val caster = s.monsters.firstOrNull { it.id == event.monsterId }
                val rawDmg = if (caster != null) {
                    Random.nextInt(caster.type.minDmg, caster.type.maxDmg + 12)
                } else {
                    Random.nextInt(38, 66)
                }
                val reduced = (rawDmg - (s.equipment.totalArmor() * 0.5).roundToInt()).coerceAtLeast(14)
                val casterName = caster?.type?.displayName ?: "Boss"
                s = s.copy(
                    hp = (s.hp - reduced).coerceAtLeast(0),
                    fireTicks = max(s.fireTicks, 5)
                )
                    .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.FIRE, nowMs)
                    .addLogInternal("$casterName's Fire Wave scorched you for $reduced!")
                if (s.hp <= 0) {
                    s = handlePlayerDeath(s, nowMs)
                }
            }
        } else if (event.inflictedStatus != null && event.targetPlayerId == localPlayerId && s.hp > 0) {
            when (event.inflictedStatus) {
                "POISON" -> s = s.copy(poisonTicks = max(s.poisonTicks, 5))
                "FIRE" -> s = s.copy(fireTicks = max(s.fireTicks, 4))
            }
        }
        return s
    }

    /**
     * Renders floating heal/mana/damage numbers and optional spell/ranged projectiles emitted
     * by a remote co-op player on the same floor.
     */
    fun applyRemotePlayerActionFx(
        state: GameWorldState,
        event: RemotePlayerActionFxEvent,
        nowMs: Long
    ): GameWorldState {
        if (event.floor != state.floor) return state
        var s = state
        if (event.text != null && event.floatingKind != null) {
            val fxX = (event.toX ?: event.x).toFloat()
            val fxY = (event.toY ?: event.y).toFloat()
            s = s.addFloatingInternal(s.floor, fxX, fxY, event.text, event.floatingKind, nowMs)
        }
        val proj = event.projectileKindName?.let { runCatching { ProjectileKind.valueOf(it) }.getOrNull() }
        if (proj != null && event.toX != null && event.toY != null) {
            s = s.addProjectileInternal(
                s.floor,
                event.x.toFloat(),
                event.y.toFloat(),
                event.toX.toFloat(),
                event.toY.toFloat(),
                proj,
                nowMs
            )
        }
        return s
    }

    fun buildFloorMonsterSnapshots(state: GameWorldState): List<SyncedMonsterSnapshot> {
        return state.monsters
            .filter { it.floor == state.floor && it.hp > 0 }
            .map { m ->
                SyncedMonsterSnapshot(
                    id = m.id,
                    typeName = m.type.name,
                    floor = m.floor,
                    x = m.x,
                    y = m.y,
                    spawnX = m.spawnX,
                    spawnY = m.spawnY,
                    hp = m.hp,
                    maxHp = m.maxHp,
                    targetPlayerId = m.targetPlayerId,
                    isSummon = m.isSummon,
                    pendingAoeTiles = m.pendingAoeTiles
                )
            }
    }

    /**
     * Classic 4-Tier monster loot tables:
     * Preserves all 12 forging materials while scaling food, potions, runes, shields, and armor across tiers.
     */
    fun generateMonsterCorpse(m: MonsterEntity): CorpseEntity {
        val gold = Random.nextInt(m.type.goldMin, m.type.goldMax + 1)
        val loot = mutableListOf<ItemStack>()
        when (m.type) {
            // --- TIER 1: Starter Overland ---
            MonsterType.CAVE_RAT -> {
                if (Random.nextFloat() < 0.48f) loot += ItemStack("jagged_ivory", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("wild_feather", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("cheese", 1)
                if (Random.nextFloat() < 0.14f) loot += ItemStack("leather_helmet", 1)
                if (Random.nextFloat() < 0.10f) loot += ItemStack("wooden_club", 1)
            }
            MonsterType.WILD_RABBIT -> {
                if (Random.nextFloat() < 0.72f) loot += ItemStack("fresh_meat", 1)
                if (Random.nextFloat() < 0.68f) loot += ItemStack("wild_feather", 2)
                if (Random.nextFloat() < 0.40f) loot += ItemStack("brindle_hide", 1)
                if (Random.nextFloat() < 0.12f) loot += ItemStack("leather_boots", 1)
            }
            MonsterType.YOUNG_SNAKE -> {
                if (Random.nextFloat() < 0.58f) loot += ItemStack("bile_gland", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("fresh_meat", 1)
                if (Random.nextFloat() < 0.12f) loot += ItemStack("leather_legs", 1)
            }
            MonsterType.CRAG_WOLF -> {
                loot += ItemStack("brindle_hide", 1)
                if (Random.nextFloat() < 0.65f) loot += ItemStack("jagged_ivory", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("fresh_meat", 1)
                if (Random.nextFloat() < 0.12f) loot += ItemStack("leather_armor", 1)
            }

            // --- TIER 2: Outer Wilds & Floor -1 Sewers/Caves ---
            MonsterType.SWAMP_TROLL -> {
                loot += ItemStack("brindle_hide", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("ochre_nugget", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("health_potion", 1)
                if (Random.nextFloat() < 0.18f) loot += ItemStack("wooden_shield", 1)
                if (Random.nextFloat() < 0.14f) loot += ItemStack("wooden_club", 1)
            }
            MonsterType.SKELETON -> {
                loot += ItemStack("ancient_bone", 1)
                if (Random.nextFloat() < 0.30f) loot += ItemStack("health_potion", 1)
                if (Random.nextFloat() < 0.20f) loot += ItemStack("wooden_shield", 1)
                if (Random.nextFloat() < 0.16f) loot += ItemStack("leather_helmet", 1)
            }
            MonsterType.GOBLIN_SCOUT -> {
                loot += ItemStack("vesper_wing", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("wild_feather", 2)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("ochre_shafts", 6)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("blank_rune", 1)
                if (Random.nextFloat() < 0.25f) loot += ItemStack("magic_dart_rune", 2)
                if (Random.nextFloat() < 0.16f) loot += ItemStack("leather_armor", 1)
            }
            MonsterType.POISON_SPIDER -> {
                if (Random.nextFloat() < 0.75f) loot += ItemStack("spider_silk", 1)
                if (Random.nextFloat() < 0.65f) loot += ItemStack("bile_gland", 1)
                if (Random.nextFloat() < 0.24f) loot += ItemStack("healing_rune", 1)
                if (Random.nextFloat() < 0.28f) loot += ItemStack("health_potion", 1)
            }

            // --- TIER 3: Floor -2 Deep Caverns ---
            MonsterType.MINOTAUR -> {
                loot += ItemStack("brindle_hide", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("bogstone_shard", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("health_potion", 1)
                if (Random.nextFloat() < 0.18f) loot += ItemStack("steel_shield", 1)
                if (Random.nextFloat() < 0.15f) loot += ItemStack("brass_legs", 1)
            }
            MonsterType.ORC_WARRIOR -> {
                loot += ItemStack("colossus_tendon", 1)
                if (Random.nextFloat() < 0.50f) loot += ItemStack("cinder_pitch", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("health_potion", 1)
                if (Random.nextFloat() < 0.18f) loot += ItemStack("viking_helmet", 1)
                if (Random.nextFloat() < 0.15f) loot += ItemStack("brass_armor", 1)
            }
            MonsterType.GHOUL -> {
                loot += ItemStack("ghoul_gem", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("ancient_bone", 1)
                if (Random.nextFloat() < 0.42f) loot += ItemStack("mana_potion", 1)
                if (Random.nextFloat() < 0.32f) loot += ItemStack("hmm_rune", 2)
                if (Random.nextFloat() < 0.22f) loot += ItemStack("healing_rune", 1)
                if (Random.nextFloat() < 0.10f) loot += ItemStack("life_amulet", 1)
            }
            MonsterType.FIRE_DEVIL -> {
                loot += ItemStack("ember_heart", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("cinder_pitch", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("mana_potion", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("hmm_rune", 2)
                if (Random.nextFloat() < 0.26f) loot += ItemStack("fireball_rune", 1)
            }

            // --- TIER 4: Floor -3 Abyssal Crypt & Bosses ---
            MonsterType.CYCLOPS -> {
                loot += ItemStack("colossus_tendon", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("runic_slate", 1)
                if (Random.nextFloat() < 0.50f) loot += ItemStack("dragon_ham", 1)
                if (Random.nextFloat() < 0.28f) loot += ItemStack("fireball_rune", 1)
                if (Random.nextFloat() < 0.16f) loot += ItemStack("steel_shield", 1)
            }
            MonsterType.DRAGON_HATCHLING -> {
                loot += ItemStack("wyrmscale_plate", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("dragon_ham", 1)
                if (Random.nextFloat() < 0.48f) loot += ItemStack("ember_heart", 1)
                if (Random.nextFloat() < 0.32f) loot += ItemStack("fireball_rune", 2)
            }
            MonsterType.NECROMANCER -> {
                loot += ItemStack("runic_slate", 1)
                if (Random.nextFloat() < 0.70f) loot += ItemStack("ghoul_gem", 1)
                if (Random.nextFloat() < 0.50f) loot += ItemStack("mana_potion", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("fireball_rune", 1)
                if (Random.nextFloat() < 0.30f) loot += ItemStack("sd_rune", 1)
                if (Random.nextFloat() < 0.14f) loot += ItemStack("mystic_robe", 1)
            }
            MonsterType.DEMON_LORD -> {
                loot += ItemStack("dragon_tooth", 1)
                loot += ItemStack("ember_heart", 2)
                loot += ItemStack("runic_slate", 1)
                loot += ItemStack("dragon_ham", 2)
                loot += ItemStack("fireball_rune", 2)
                loot += ItemStack("sd_rune", 2)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("dragon_shield", 1)
            }
            MonsterType.ELDER_DRAGON -> {
                loot += ItemStack("dragon_tooth", 1)
                loot += ItemStack("wyrmscale_plate", 2)
                loot += ItemStack("ember_heart", 1)
                loot += ItemStack("dragon_ham", 2)
                loot += ItemStack("fireball_rune", 2)
                loot += ItemStack("sd_rune", 2)
                if (Random.nextFloat() < 0.40f) loot += ItemStack("dragon_mail", 1)
            }
        }
        return CorpseEntity(
            id = "m_${UUID.randomUUID()}",
            floor = m.floor,
            x = m.x,
            y = m.y,
            label = "Slain ${m.type.displayName}",
            isPlayerCorpse = false,
            monsterTypeName = m.type.name,
            gold = gold,
            items = loot
        )
    }

    fun useHealthPotion(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        if (!state.hasItem("health_potion")) return state.addLogInternal("Out of Health Potions! Buy more from Alaric.")
        val def = ItemCatalog.get("health_potion")
        val heal = def.restoreAmount + Random.nextInt(-10, 16)
        val newHp = (state.hp + heal).coerceAtMost(state.maxHp)
        val actualHeal = newHp - state.hp
        return state.copy(
            hp = newHp,
            poisonTicks = (state.poisonTicks - 2).coerceAtLeast(0),
            backpack = removeItemPublic(state.backpack, "health_potion", 1)
        )
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+$actualHeal HP", FloatingTextKind.HEAL, nowMs)
            .queuePlayerFx("+$actualHeal HP", FloatingTextKind.HEAL, PlayerActionState.HEALING)
            .addLogInternal("Drank Health Potion (+$actualHeal HP).")
    }

    fun useManaPotion(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        if (!state.hasItem("mana_potion")) return state.addLogInternal("Out of Mana Potions! Buy more from Alaric.")
        val def = ItemCatalog.get("mana_potion")
        val gain = def.restoreAmount + Random.nextInt(-8, 13)
        val newMp = (state.mp + gain).coerceAtMost(state.maxMp)
        val actual = newMp - state.mp
        return state.copy(
            mp = newMp,
            backpack = removeItemPublic(state.backpack, "mana_potion", 1)
        )
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+$actual MP", FloatingTextKind.MANA, nowMs)
            .queuePlayerFx("+$actual MP", FloatingTextKind.MANA, PlayerActionState.HEALING)
            .addLogInternal("Drank Mana Potion (+$actual MP).")
    }

    fun bindRuneToHotbar(state: GameWorldState, runeId: String): GameWorldState {
        val def = ItemCatalog.all[runeId] ?: return state
        return when (def.category) {
            ItemCategory.RUNE_ATTACK -> state.copy(boundAttackRuneId = runeId)
                .addLogInternal("Bound ${def.name} (Magic Lv ${def.requiredMagicLevel}) to your Attack Rune hotbar.")
            ItemCategory.RUNE_HEAL -> state.copy(boundHealRuneId = runeId)
                .addLogInternal("Bound ${def.name} (Magic Lv ${def.requiredMagicLevel}) to your Heal Rune hotbar.")
            else -> state
        }
    }

    fun useHealingRune(
        state: GameWorldState,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList(),
        explicitRuneId: String? = null
    ): GameWorldState {
        if (state.deathReport != null) return state
        val runeId = explicitRuneId ?: state.resolvedHealRuneId()
        if (!state.hasItem(runeId)) {
            return state.addLogInternal("No Healing Runes in backpack! Inscribe them at the Runecrafting Table.")
        }
        val runeDef = ItemCatalog.get(runeId)
        if (state.magicLevel < runeDef.requiredMagicLevel) {
            return state.addLogInternal("You need Magic Level ${runeDef.requiredMagicLevel} to use this rune.")
        }
        if (state.mp < runeDef.manaCost) {
            return state.addLogInternal("Not enough Mana (${runeDef.manaCost} MP required for ${runeDef.name}).")
        }

        val healPower = ((runeDef.restoreAmount + state.magicLevel * 7 + state.level * 2) * Random.nextDouble(0.92, 1.12))
            .roundToInt().coerceAtLeast(40)

        // If player recently tapped/inspected a nearby remote ally in range who needs healing, cast on that ally!
        val inspectedAlly = if (state.inspectedPlayerId != null && nowMs < state.inspectedPlayerUntilMs) {
            remotePlayersOnFloor.firstOrNull { rp ->
                rp.playerId == state.inspectedPlayerId &&
                    rp.floor == state.floor &&
                    rp.hp < rp.maxHp &&
                    chebyshev(state.playerX, state.playerY, rp.x, rp.y) <= runeDef.range
            }
        } else {
            null
        }

        var next = state.copy(
            mp = (state.mp - runeDef.manaCost).coerceAtLeast(0),
            backpack = removeItemPublic(state.backpack, runeId, 1)
        )
        next = awardMagicXpFromMana(next, runeDef.manaCost, nowMs)

        return if (inspectedAlly != null) {
            next
                .addProjectileInternal(
                    state.floor,
                    state.playerX.toFloat(),
                    state.playerY.toFloat(),
                    inspectedAlly.x.toFloat(),
                    inspectedAlly.y.toFloat(),
                    ProjectileKind.HEAL_RUNE,
                    nowMs,
                    durationMs = 340L
                )
                .addFloatingInternal(state.floor, inspectedAlly.x.toFloat(), inspectedAlly.y.toFloat(), "+$healPower HP", FloatingTextKind.HEAL, nowMs)
                .queuePlayerFx("+$healPower HP", FloatingTextKind.HEAL, PlayerActionState.HEALING)
                .addLogInternal("Cast ${runeDef.name} on ${inspectedAlly.name} (+$healPower HP).")
        } else {
            val newHp = (next.hp + healPower).coerceAtMost(next.maxHp)
            val actual = newHp - next.hp
            next.copy(
                hp = newHp,
                poisonTicks = (next.poisonTicks - 4).coerceAtLeast(0)
            )
                .addProjectileInternal(
                    state.floor,
                    state.playerX.toFloat(),
                    state.playerY.toFloat(),
                    state.playerX.toFloat(),
                    state.playerY.toFloat(),
                    ProjectileKind.HEAL_RUNE,
                    nowMs,
                    durationMs = 340L
                )
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+$actual HP", FloatingTextKind.HEAL, nowMs)
                .queuePlayerFx("+$actual HP", FloatingTextKind.HEAL, PlayerActionState.HEALING)
                .addLogInternal("Cast ${runeDef.name} (+$actual HP, eased poison).")
        }
    }

    fun useAttackRune(
        state: GameWorldState,
        nowMs: Long,
        explicitRuneId: String? = null
    ): GameWorldState {
        if (state.deathReport != null) return state
        val runeId = explicitRuneId ?: state.resolvedAttackRuneId()
            ?: return state.addLogInternal("No Attack Runes in backpack! Inscribe them at the Runecrafting Table.")
        if (!state.hasItem(runeId)) {
            return state.addLogInternal("Out of ${ItemCatalog.get(runeId).name}! Bind another rune or craft more at the Rune Altar.")
        }
        val runeDef = ItemCatalog.get(runeId)
        if (state.magicLevel < runeDef.requiredMagicLevel) {
            return state.addLogInternal("You need Magic Level ${runeDef.requiredMagicLevel} to use this rune.")
        }
        val target = state.monsters.firstOrNull { it.id == state.targetMonsterId && it.floor == state.floor }
            ?: return state.addLogInternal("Tap a monster first to aim your ${runeDef.name}!")
        if (chebyshev(state.playerX, state.playerY, target.x, target.y) > runeDef.range) {
            return state.addLogInternal("Target is too far for ${runeDef.name} (range ${runeDef.range}).")
        }
        if (state.mp < runeDef.manaCost) {
            return state.addLogInternal("Not enough Mana (${runeDef.manaCost} MP required for ${runeDef.name}).")
        }

        val magicMult = state.magicSkillMultiplier()
        val rawDmg = ((runeDef.attack + state.level * 1.15) * magicMult * Random.nextDouble(0.88, 1.16))
            .roundToInt().coerceAtLeast(10)
        // Tier 2 HMM pierces extra armor via shock; Tier 4 SD hits hardest
        val effectiveArmor = if (runeId == "hmm_rune") (target.type.armor * 0.7).roundToInt() else target.type.armor
        val primaryDmg = mitigateMonsterDamage(rawDmg, effectiveArmor, isMagic = true)

        var next = state.copy(
            mp = (state.mp - runeDef.manaCost).coerceAtLeast(0),
            playerFacing = directionToward(state.playerX, state.playerY, target.x, target.y),
            backpack = removeItemPublic(state.backpack, runeId, 1)
        )
        next = awardMagicXpFromMana(next, runeDef.manaCost, nowMs)

        val projKind = when (runeId) {
            "magic_dart_rune" -> ProjectileKind.ARCANE_DART
            "fireball_rune" -> ProjectileKind.FIREBALL_AOE
            "sd_rune" -> ProjectileKind.DEATH_RUNE
            else -> ProjectileKind.ENERGY_BOLT
        }
        next = next.addProjectileInternal(
            state.floor,
            state.playerX.toFloat(),
            state.playerY.toFloat(),
            target.x.toFloat(),
            target.y.toFloat(),
            projKind,
            nowMs
        )
        next = next.queuePlayerFx(null, null, PlayerActionState.CASTING)

        val originX = target.x
        val originY = target.y
        val blastSummary = when (runeId) {
            "hmm_rune" -> "${runeDef.name} shocked ${target.type.displayName} for $primaryDmg!"
            "fireball_rune" -> "${runeDef.name} engulfed ${target.type.displayName} for $primaryDmg!"
            "sd_rune" -> "${runeDef.name} obliterated ${target.type.displayName} for $primaryDmg!"
            else -> "${runeDef.name} struck ${target.type.displayName} for $primaryDmg!"
        }
        next = applyDamageToMonster(next, target.id, primaryDmg, nowMs, blastSummary, projKind)

        // Tier 3 (Great Fireball 3x3 splash) & Tier 4 (Sudden Death shockwave) hit nearby monsters in AoE radius
        if (runeDef.aoeRadius > 0) {
            val splashFactor = if (runeId == "fireball_rune") 0.75 else 0.45
            val splashTargets = next.monsters.filter { m ->
                m.floor == state.floor &&
                    m.id != target.id &&
                    chebyshev(originX, originY, m.x, m.y) <= runeDef.aoeRadius
            }
            for (splashTarget in splashTargets) {
                val splashRaw = (rawDmg * splashFactor).roundToInt().coerceAtLeast(8)
                val splashDmg = mitigateMonsterDamage(splashRaw, splashTarget.type.armor, isMagic = true)
                next = next.addProjectileInternal(
                    state.floor,
                    originX.toFloat(),
                    originY.toFloat(),
                    splashTarget.x.toFloat(),
                    splashTarget.y.toFloat(),
                    projKind,
                    nowMs,
                    durationMs = 240L
                )
                next = applyDamageToMonster(
                    next,
                    splashTarget.id,
                    splashDmg,
                    nowMs,
                    "${runeDef.name} splash hit ${splashTarget.type.displayName} for $splashDmg!",
                    projKind
                )
            }
        }
        return next
    }

    fun useRope(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        if (!state.hasItem("rope")) return state.addLogInternal("You need a Rope in your backpack to climb up!")
        val portal = WorldMaps.portals.firstOrNull { p ->
            p.fromFloor == state.floor && p.requiresRope && chebyshev(state.playerX, state.playerY, p.fromX, p.fromY) <= 1
        } ?: return state.addLogInternal("Stand on or beside a bright sunlit rope-spot to use your Rope.")

        val destFloor = WorldMaps.getFloor(portal.toFloor)
        return state.copy(
            floor = portal.toFloor,
            playerX = portal.toX,
            playerY = portal.toY,
            prevPlayerX = portal.toX,
            prevPlayerY = portal.toY,
            lastPlayerMoveMs = nowMs,
            walkPath = emptyList(),
            targetMonsterId = null,
            autoFollowTarget = false,
            activePanel = ActivePanel.NONE,
            floorWarningBanner = null
        )
            .addFloatingInternal(portal.toFloor, portal.toX.toFloat(), portal.toY.toFloat(), "ROPE UP!", FloatingTextKind.INFO, nowMs)
            .addLogInternal("Climbed up the rope-hole to ${destFloor.name}!")
    }

    fun attemptFishing(state: GameWorldState, waterX: Int, waterY: Int, nowMs: Long): GameWorldState {
        if (!state.hasItem("fishing_rod")) return state.addLogInternal("You need a Fishing Rod in your backpack to fish.")
        var next = state.addProjectileInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), waterX.toFloat(), waterY.toFloat(), ProjectileKind.SPLASH, nowMs, 340L)
        next = advanceSkillPublic(next, SkillType.FISHING, 4.5, nowMs)
        val catchChance = (0.38 + (next.skills.fishing.level - 10) * 0.03).coerceAtMost(0.85)
        if (Random.nextDouble() <= catchChance) {
            val fishDef = ItemCatalog.get("fresh_fish")
            if (next.remainingCapOz() < fishDef.weightOz) {
                return next.addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "CAP FULL", FloatingTextKind.INFO, nowMs)
                    .addLogInternal("A fish bit, but you lack Cap (${fishDef.weightOz} oz needed)!")
            }
            next = next.copy(backpack = addItemPublic(next.backpack, "fresh_fish", 1))
                .addFloatingInternal(state.floor, waterX.toFloat(), waterY.toFloat(), "+1 Fish!", FloatingTextKind.HEAL, nowMs)
                .addLogInternal("Caught a Fresh Fish! (Fishing ${next.skills.fishing.level})")
        } else {
            next = next.addFloatingInternal(state.floor, waterX.toFloat(), waterY.toFloat(), "Splash...", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Cast your line into the river... no bite yet.")
        }
        return next
    }

    private fun processMonstersTick(
        state: GameWorldState,
        floorMap: WorldFloor,
        playerInPz: Boolean,
        nowMs: Long,
        isFloorHost: Boolean,
        localPlayerId: String,
        remotePlayersOnFloor: List<RemotePlayerState>
    ): GameWorldState {
        var s = state
        val updatedMonsters = s.monsters.toMutableList()
        val spawnedSummons = mutableListOf<MonsterEntity>()
        val activeRemoteTargets = remotePlayersOnFloor.filter {
            it.floor == s.floor && it.hp > 0 && !floorMap.isProtectionZone(it.x, it.y)
        }

        for (i in updatedMonsters.indices) {
            var curM = updatedMonsters[i]
            if (curM.floor != s.floor) continue

            // 1. Determine target player: Host picks closest non-PZ player on floor; Non-host follows host's targetPlayerId
            val targetId: String?
            val targetX: Int
            val targetY: Int
            val distToTarget: Int

            if (isFloorHost) {
                var bestId: String? = null
                var bestX = s.playerX
                var bestY = s.playerY
                var bestDist = Int.MAX_VALUE

                if (!playerInPz && s.hp > 0) {
                    val dLocal = chebyshev(curM.x, curM.y, s.playerX, s.playerY)
                    if (dLocal <= 8) {
                        bestId = localPlayerId
                        bestX = s.playerX
                        bestY = s.playerY
                        bestDist = dLocal
                    }
                }
                for (rp in activeRemoteTargets) {
                    val dRem = chebyshev(curM.x, curM.y, rp.x, rp.y)
                    if (dRem <= 8 && dRem < bestDist) {
                        bestId = rp.playerId
                        bestX = rp.x
                        bestY = rp.y
                        bestDist = dRem
                    }
                }
                targetId = bestId
                targetX = bestX
                targetY = bestY
                distToTarget = bestDist
                if (curM.targetPlayerId != targetId) {
                    curM = curM.copy(targetPlayerId = targetId)
                    updatedMonsters[i] = curM
                }
            } else {
                targetId = curM.targetPlayerId
                targetX = s.playerX
                targetY = s.playerY
                distToTarget = chebyshev(curM.x, curM.y, s.playerX, s.playerY)
            }

            // Clear stale telegraph if target fled or stepped into Protection Zone
            if (targetId == null || distToTarget > 8) {
                if (isFloorHost && curM.pendingAoeTiles.isNotEmpty()) {
                    updatedMonsters[i] = curM.copy(pendingAoeTiles = emptyList())
                }
                continue
            }

            // 2. Boss 1-Tick Telegraphed Fire Wave AoE (Host-driven)
            if (isFloorHost && curM.type.aiStyle == MonsterAiStyle.BOSS_AOE_SUMMON) {
                if (curM.pendingAoeTiles.isNotEmpty()) {
                    val waveTiles = curM.pendingAoeTiles
                    val waveProj = curM.type.rangedProjectile ?: ProjectileKind.FIRE_BREATH
                    s = s.copy(
                        outboundMonsterAttacks = s.outboundMonsterAttacks + LocalMonsterAttackEvent(
                            monsterId = curM.id,
                            floor = s.floor,
                            fromX = curM.x,
                            fromY = curM.y,
                            toX = targetX,
                            toY = targetY,
                            projectileKindName = waveProj.name,
                            aoeTiles = waveTiles,
                            targetPlayerId = targetId,
                            inflictedStatus = "FIRE"
                        )
                    )
                    for ((ax, ay) in waveTiles.take(10)) {
                        s = s.addProjectileInternal(
                            s.floor,
                            curM.x.toFloat(),
                            curM.y.toFloat(),
                            ax.toFloat(),
                            ay.toFloat(),
                            waveProj,
                            nowMs,
                            340L
                        )
                    }
                    if (!playerInPz && s.hp > 0 && (s.playerX to s.playerY) in waveTiles) {
                        val rawWave = Random.nextInt(curM.type.minDmg, curM.type.maxDmg + 12)
                        val waveDmg = (rawWave - (s.equipment.totalArmor() * 0.5).roundToInt()).coerceAtLeast(14)
                        s = s.copy(
                            hp = (s.hp - waveDmg).coerceAtLeast(0),
                            fireTicks = max(s.fireTicks, 5)
                        )
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$waveDmg", FloatingTextKind.FIRE, nowMs)
                            .queuePlayerFx("-$waveDmg", FloatingTextKind.FIRE, PlayerActionState.IDLE)
                            .addLogInternal("${curM.type.displayName}'s Fire Wave scorched you for $waveDmg!")
                    }
                    updatedMonsters[i] = curM.copy(pendingAoeTiles = emptyList(), aoeCooldownTicks = 4)
                    if (s.hp <= 0) break
                    continue
                } else if (curM.aoeCooldownTicks <= 0 && distToTarget <= 4) {
                    val waveTiles = computeFireWaveTiles(floorMap, curM.x, curM.y, targetX, targetY)
                    if (waveTiles.isNotEmpty()) {
                        updatedMonsters[i] = curM.copy(pendingAoeTiles = waveTiles)
                        s = s.addFloatingInternal(s.floor, curM.x.toFloat(), curM.y.toFloat(), "FIRE WAVE!", FloatingTextKind.FIRE, nowMs)
                            .addLogInternal("${curM.type.displayName} draws a fiery breath — move out of the warning tiles!")
                        continue
                    }
                } else if (curM.aoeCooldownTicks > 0) {
                    curM = curM.copy(aoeCooldownTicks = curM.aoeCooldownTicks - 1)
                    updatedMonsters[i] = curM
                }
            }

            // 3. Summon Mechanics (Necromancer max 1 Skeleton, Demon Lord max 2 Fire Devils, Elder Dragon max 2 Hatchlings)
            if (isFloorHost && curM.type.maxSummons > 0) {
                val activeSummons = updatedMonsters.count { it.floor == s.floor && it.summonerId == curM.id } +
                    spawnedSummons.count { it.floor == s.floor && it.summonerId == curM.id }
                if (curM.summonCooldownTicks > 0) {
                    curM = curM.copy(summonCooldownTicks = curM.summonCooldownTicks - 1)
                    updatedMonsters[i] = curM
                } else if (activeSummons < curM.type.maxSummons && distToTarget <= 6) {
                    val summonType = curM.type.summonType()
                    val tile = if (summonType != null) {
                        findSummonTile(floorMap, curM.x, curM.y, s, activeRemoteTargets, updatedMonsters, spawnedSummons)
                    } else null
                    if (summonType != null && tile != null) {
                        val sid = nextMonsterId++
                        spawnedSummons += MonsterEntity(
                            id = sid,
                            type = summonType,
                            floor = s.floor,
                            x = tile.first,
                            y = tile.second,
                            spawnX = tile.first,
                            spawnY = tile.second,
                            hp = summonType.maxHp,
                            maxHp = summonType.maxHp,
                            targetPlayerId = targetId,
                            isSummon = true,
                            summonerId = curM.id
                        )
                        curM = curM.copy(summonCooldownTicks = 8)
                        updatedMonsters[i] = curM
                        s = s.addFloatingInternal(s.floor, curM.x.toFloat(), curM.y.toFloat(), "SUMMON!", FloatingTextKind.FIRE, nowMs)
                            .addLogInternal("${curM.type.displayName} summoned a ${summonType.displayName}!")
                    }
                }
            }

            // 4. Resolve monster attack locally when targeting this local player
            val targetsLocal = targetId == localPlayerId
            val distToLocal = chebyshev(curM.x, curM.y, s.playerX, s.playerY)
            val canRangedLocal = curM.type.preferredRange > 1 && curM.type.rangedProjectile != null &&
                distToLocal in 2..curM.type.preferredRange
            val shouldRangedLocal = canRangedLocal && (
                curM.type.aiStyle == MonsterAiStyle.RANGED_KITE || Random.nextFloat() < 0.55f
            )

            if (targetsLocal && !playerInPz && s.hp > 0 && (distToLocal <= 1 || shouldRangedLocal)) {
                val isRanged = distToLocal > 1
                val projKind = if (isRanged) curM.type.rangedProjectile ?: ProjectileKind.FIRE_BREATH else null
                if (projKind != null) {
                    s = s.addProjectileInternal(s.floor, curM.x.toFloat(), curM.y.toFloat(), s.playerX.toFloat(), s.playerY.toFloat(), projKind, nowMs, 300L)
                }
                if (!isRanged) {
                    val shieldTrainPts = when {
                        s.hasActiveShield() -> 3.4
                        s.isUsingTwoHandedWeapon() -> 1.35
                        else -> 2.2
                    }
                    s = advanceSkillPublic(s, SkillType.SHIELDING, shieldTrainPts, nowMs)
                }

                var statusTag: String? = null
                val dodgeChance = s.shieldDodgeChance(isRanged)
                if (Random.nextDouble() < dodgeChance) {
                    s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "DODGE!", FloatingTextKind.BLOCK, nowMs)
                        .queuePlayerFx("DODGE!", FloatingTextKind.BLOCK, PlayerActionState.IDLE)
                } else {
                    val rawDmg = Random.nextInt(curM.type.minDmg, curM.type.maxDmg + 1)
                    val armorMit = s.equipment.totalArmor() * 0.65
                    val glancingShieldMit = if (!isRanged && s.hasActiveShield()) s.equipment.shieldDefense() * 0.22 else 0.0
                    val reduced = (rawDmg - (if (isRanged) armorMit * 0.6 else (armorMit + glancingShieldMit) * Random.nextDouble(0.75, 1.05))).roundToInt()

                    if (reduced <= 0) {
                        s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "GLANCE", FloatingTextKind.BLOCK, nowMs)
                            .queuePlayerFx("GLANCE", FloatingTextKind.BLOCK, PlayerActionState.IDLE)
                    } else {
                        s = s.copy(hp = (s.hp - reduced).coerceAtLeast(0))
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.DAMAGE_PLAYER, nowMs)
                            .queuePlayerFx("-$reduced", FloatingTextKind.DAMAGE_PLAYER, PlayerActionState.IDLE)
                        if (curM.type.inflictsPoison && Random.nextFloat() < 0.35f) {
                            statusTag = "POISON"
                            s = s.copy(poisonTicks = max(s.poisonTicks, 5)).addLogInternal("${curM.type.displayName} poisoned you!")
                        }
                        if (curM.type.inflictsFire && (isRanged || Random.nextFloat() < 0.40f)) {
                            statusTag = "FIRE"
                            s = s.copy(fireTicks = max(s.fireTicks, 4)).addLogInternal("${curM.type.displayName} set you ablaze!")
                        }
                    }
                }
                if (projKind != null || statusTag != null) {
                    s = s.copy(
                        outboundMonsterAttacks = s.outboundMonsterAttacks + LocalMonsterAttackEvent(
                            monsterId = curM.id,
                            floor = s.floor,
                            fromX = curM.x,
                            fromY = curM.y,
                            toX = s.playerX,
                            toY = s.playerY,
                            projectileKindName = projKind?.name,
                            targetPlayerId = localPlayerId,
                            inflictedStatus = statusTag
                        )
                    )
                }
                if (s.hp <= 0) break
            } else if (isFloorHost && !targetsLocal && curM.type.rangedProjectile != null &&
                distToTarget in 2..curM.type.preferredRange &&
                (curM.type.aiStyle == MonsterAiStyle.RANGED_KITE || Random.nextFloat() < 0.50f)
            ) {
                val projKind = curM.type.rangedProjectile
                s = s.addProjectileInternal(s.floor, curM.x.toFloat(), curM.y.toFloat(), targetX.toFloat(), targetY.toFloat(), projKind, nowMs, 300L)
                s = s.copy(
                    outboundMonsterAttacks = s.outboundMonsterAttacks + LocalMonsterAttackEvent(
                        monsterId = curM.id,
                        floor = s.floor,
                        fromX = curM.x,
                        fromY = curM.y,
                        toX = targetX,
                        toY = targetY,
                        projectileKindName = projKind.name,
                        targetPlayerId = targetId
                    )
                )
            }

            // 5. Host Movement AI: Ranged kiters maintain 2-3 tile distance; melee chases into range 1
            if (isFloorHost) {
                if (curM.moveCooldown > 0) {
                    updatedMonsters[i] = curM.copy(moveCooldown = curM.moveCooldown - 1)
                } else {
                    val isTileFree: (Int, Int) -> Boolean = { cx, cy ->
                        val t = floorMap.tileAt(cx, cy)
                        t.walkable &&
                            !floorMap.isProtectionZone(cx, cy) &&
                            !(cx == s.playerX && cy == s.playerY) &&
                            remotePlayersOnFloor.none { rp -> rp.floor == curM.floor && rp.x == cx && rp.y == cy } &&
                            updatedMonsters.none { other -> other.id != curM.id && other.floor == curM.floor && other.x == cx && other.y == cy } &&
                            spawnedSummons.none { other -> other.floor == curM.floor && other.x == cx && other.y == cy }
                    }

                    val moveDest: Pair<Int, Int>? = when {
                        // Ranged kiters retreat 1 tile away when player closes to adjacent range
                        curM.type.aiStyle == MonsterAiStyle.RANGED_KITE && distToTarget <= 1 -> {
                            listOf(
                                curM.x + 1 to curM.y,
                                curM.x - 1 to curM.y,
                                curM.x to curM.y + 1,
                                curM.x to curM.y - 1
                            )
                                .filter { (cx, cy) -> isTileFree(cx, cy) && chebyshev(cx, cy, targetX, targetY) > distToTarget }
                                .maxByOrNull { (cx, cy) ->
                                    chebyshev(cx, cy, targetX, targetY) * 10 + abs(cx - targetX) + abs(cy - targetY)
                                }
                        }
                        // Ranged kiters hold position inside optimal 2..preferredRange firing band
                        curM.type.aiStyle == MonsterAiStyle.RANGED_KITE && distToTarget in 2..curM.type.preferredRange -> null
                        // Advance toward target
                        distToTarget > 1 -> {
                            val stepX = (targetX - curM.x).coerceIn(-1, 1)
                            val stepY = (targetY - curM.y).coerceIn(-1, 1)
                            val candidates = buildList {
                                if (stepX != 0 && stepY != 0) {
                                    add(curM.x + stepX to curM.y)
                                    add(curM.x to curM.y + stepY)
                                } else if (stepX != 0) {
                                    add(curM.x + stepX to curM.y); add(curM.x to curM.y + 1); add(curM.x to curM.y - 1)
                                } else if (stepY != 0) {
                                    add(curM.x to curM.y + stepY); add(curM.x + 1 to curM.y); add(curM.x - 1 to curM.y)
                                }
                            }
                            candidates.firstOrNull { (cx, cy) -> isTileFree(cx, cy) }
                        }
                        else -> null
                    }

                    if (moveDest != null) {
                        updatedMonsters[i] = curM.copy(
                            lastMoveFromX = curM.x,
                            lastMoveFromY = curM.y,
                            lastMoveTimeMs = nowMs,
                            x = moveDest.first,
                            y = moveDest.second,
                            targetPlayerId = targetId,
                            moveCooldown = (curM.type.moveIntervalTicks - 1).coerceAtLeast(0)
                        )
                    }
                }
            }
        }
        return s.copy(monsters = updatedMonsters + spawnedSummons)
    }

    /**
     * Computes a classic 3-wide directional Boss Fire Wave cone (up to 7 walkable non-PZ tiles)
     * aimed from the boss toward its target.
     */
    internal fun computeFireWaveTiles(
        floorMap: WorldFloor,
        fromX: Int,
        fromY: Int,
        targetX: Int,
        targetY: Int
    ): List<Pair<Int, Int>> {
        val dx = targetX - fromX
        val dy = targetY - fromY
        val (dirX, dirY) = if (abs(dx) >= abs(dy)) {
            (if (dx >= 0) 1 else -1) to 0
        } else {
            0 to (if (dy >= 0) 1 else -1)
        }
        val perpX = -dirY
        val perpY = dirX

        val tiles = linkedSetOf<Pair<Int, Int>>()
        for (dist in 1..3) {
            val spread = if (dist == 1) 0 else 1
            for (s in -spread..spread) {
                val tx = fromX + dirX * dist + perpX * s
                val ty = fromY + dirY * dist + perpY * s
                if (tx in 0 until floorMap.width &&
                    ty in 0 until floorMap.height &&
                    floorMap.tileAt(tx, ty).walkable &&
                    !floorMap.isProtectionZone(tx, ty)
                ) {
                    tiles += (tx to ty)
                }
            }
        }
        return tiles.toList()
    }

    private fun findSummonTile(
        floorMap: WorldFloor,
        fromX: Int,
        fromY: Int,
        state: GameWorldState,
        activeRemoteTargets: List<RemotePlayerState>,
        existingMonsters: List<MonsterEntity>,
        spawnedSummons: List<MonsterEntity>
    ): Pair<Int, Int>? {
        val offsets = listOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, -1 to 1, 1 to -1, -1 to -1
        )
        for ((ox, oy) in offsets) {
            val cx = fromX + ox
            val cy = fromY + oy
            if (cx !in 0 until floorMap.width || cy !in 0 until floorMap.height) continue
            if (!floorMap.tileAt(cx, cy).walkable || floorMap.isProtectionZone(cx, cy)) continue
            if (cx == state.playerX && cy == state.playerY) continue
            if (activeRemoteTargets.any { it.x == cx && it.y == cy }) continue
            if (existingMonsters.any { it.floor == state.floor && it.x == cx && it.y == cy }) continue
            if (spawnedSummons.any { it.floor == state.floor && it.x == cx && it.y == cy }) continue
            return cx to cy
        }
        return null
    }

    private fun processRespawns(
        state: GameWorldState,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.pendingRespawns.isEmpty()) return state
        val remaining = mutableListOf<PendingRespawn>()
        val spawned = mutableListOf<MonsterEntity>()
        for (r in state.pendingRespawns) {
            if (r.ticksRemaining <= 1) {
                val occupied = (state.floor == r.floor && state.playerX == r.spawnX && state.playerY == r.spawnY) ||
                    remotePlayersOnFloor.any { it.floor == r.floor && it.x == r.spawnX && it.y == r.spawnY } ||
                    state.monsters.any { it.floor == r.floor && it.x == r.spawnX && it.y == r.spawnY }
                if (!occupied) {
                    val respawnId = if (r.originalMonsterId > 0) r.originalMonsterId else nextMonsterId++
                    spawned += MonsterEntity(respawnId, r.type, r.floor, r.spawnX, r.spawnY, r.spawnX, r.spawnY, r.type.maxHp, r.type.maxHp)
                } else {
                    remaining += r.copy(ticksRemaining = 3)
                }
            } else {
                remaining += r.copy(ticksRemaining = r.ticksRemaining - 1)
            }
        }
        return state.copy(monsters = state.monsters + spawned, pendingRespawns = remaining)
    }

    private fun handlePlayerDeath(state: GameWorldState, nowMs: Long): GameWorldState {
        val xpLost = (state.xp * 0.10).toLong()
        val newXp = (state.xp - xpLost).coerceAtLeast(0L)
        var newLevel = state.level
        while (newLevel > 1 && newXp < state.xpForLevel(newLevel)) newLevel--
        val levelsLost = (state.level - newLevel).coerceAtLeast(0)
        val minHpFloor = minOf(state.vocation.baseHp, Vocation.ADVENTURER.baseHp)
        val minMpFloor = minOf(state.vocation.baseMp, Vocation.ADVENTURER.baseMp)
        val newMaxHp = (state.maxHp - levelsLost * state.vocation.hpPerLevel).coerceAtLeast(minHpFloor)
        val newMaxMp = (state.maxMp - levelsLost * state.vocation.mpPerLevel).coerceAtLeast(minMpFloor)
        val newMaxCap = GameWorldState.maxCapForLevel(newLevel, state.vocation)

        fun penalizeSkill(sp: SkillProgress): SkillProgress {
            if (sp.level <= 10) return sp.copy(points = sp.points * 0.90)
            val lostPts = sp.requiredPointsForNext() * 0.10
            return if (sp.points >= lostPts) sp.copy(points = sp.points - lostPts)
            else {
                val prev = SkillProgress(sp.level - 1, 0.0)
                prev.copy(points = (prev.requiredPointsForNext() - (lostPts - sp.points)).coerceAtLeast(0.0))
            }
        }
        fun penalizeMagicSkill(sp: SkillProgress): SkillProgress {
            if (sp.level <= 1) return sp.copy(points = sp.points * 0.90)
            val lostPts = sp.requiredMagicXpForNext() * 0.10
            return if (sp.points >= lostPts) sp.copy(points = sp.points - lostPts)
            else {
                val prev = SkillProgress((sp.level - 1).coerceAtLeast(1), 0.0)
                prev.copy(points = (prev.requiredMagicXpForNext() - (lostPts - sp.points)).coerceAtLeast(0.0))
            }
        }
        val penalizedSkills = PlayerSkills(
            melee = penalizeSkill(state.skills.melee),
            fist = penalizeSkill(state.skills.fist),
            club = penalizeSkill(state.skills.club),
            sword = penalizeSkill(state.skills.sword),
            axe = penalizeSkill(state.skills.axe),
            distance = penalizeSkill(state.skills.distance),
            shielding = penalizeSkill(state.skills.shielding),
            magic = penalizeMagicSkill(state.skills.magic),
            fishing = penalizeSkill(state.skills.fishing),
            mining = penalizeSkill(state.skills.mining)
        )

        val droppedCount = state.backpack.sumOf { it.count }
        val playerCorpse = CorpseEntity(
            id = "p_${UUID.randomUUID()}",
            floor = state.floor,
            x = state.playerX,
            y = state.playerY,
            label = "${state.playerName}'s Fallen Backpack",
            isPlayerCorpse = true,
            gold = 0,
            items = state.backpack
        )
        val updatedCorpses = if (state.backpack.isNotEmpty()) state.corpses + playerCorpse else state.corpses
        // Tool Bag stays strapped to the adventurer on death (ensuring a Rope is always available for recovery)
        val keptTools = if (state.toolBag.any { it.itemId == "rope" }) state.toolBag
        else addItemPublic(state.toolBag, "rope", 1)

        return state.copy(
            level = newLevel,
            xp = newXp,
            hp = newMaxHp,
            maxHp = newMaxHp,
            mp = newMaxMp,
            maxMp = newMaxMp,
            maxCapOz = newMaxCap,
            floor = 0,
            playerX = WorldMaps.TEMPLE_SPAWN_X,
            playerY = WorldMaps.TEMPLE_SPAWN_Y,
            prevPlayerX = WorldMaps.TEMPLE_SPAWN_X,
            prevPlayerY = WorldMaps.TEMPLE_SPAWN_Y,
            lastPlayerMoveMs = nowMs,
            poisonTicks = 0,
            fireTicks = 0,
            skills = penalizedSkills,
            backpack = emptyList(),
            toolBag = keptTools,
            corpses = updatedCorpses,
            targetMonsterId = null,
            autoFollowTarget = false,
            walkPath = emptyList(),
            activePanel = ActivePanel.NONE,
            deathReport = DeathReport(xpLost, state.floor, state.playerX, state.playerY, droppedCount)
        ).addLogInternal("You died! Lost $xpLost XP (-10%). Main Bag dropped on Floor ${state.floor}; Tool Bag remained strapped.")
    }

    internal fun grantExperience(state: GameWorldState, xpGain: Long, fx: Float, fy: Float, nowMs: Long): GameWorldState {
        var s = state.copy(xp = state.xp + xpGain)
            .addFloatingInternal(state.floor, fx, fy - 0.3f, "+$xpGain XP", FloatingTextKind.XP, nowMs)
        var lvl = s.level
        while (s.xp >= s.xpForLevel(lvl + 1)) {
            lvl++
        }
        val levelsGained = lvl - s.level
        if (levelsGained > 0) {
            val prevLevel = s.level
            // Forward-only Classic Tibia delta growth + canonical level-scaling Cap floor
            val newMaxHp = s.maxHp + levelsGained * s.vocation.hpPerLevel
            val newMaxMp = s.maxMp + levelsGained * s.vocation.mpPerLevel
            val newMaxCap = maxOf(
                s.maxCapOz + levelsGained * s.vocation.capPerLevel,
                GameWorldState.maxCapForLevel(lvl, s.vocation)
            )
            val capGain = (newMaxCap - s.maxCapOz).roundToInt()
            s = s.copy(level = lvl, maxHp = newMaxHp, hp = newMaxHp, maxMp = newMaxMp, mp = newMaxMp, maxCapOz = newMaxCap)
                .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "LEVEL $lvl!", FloatingTextKind.SKILL_UP, nowMs)
                .addLogInternal("Advanced to Character Level $lvl! (+${levelsGained * s.vocation.hpPerLevel} HP, +${levelsGained * s.vocation.mpPerLevel} MP, +$capGain oz Cap)")
            if (s.vocation == Vocation.ADVENTURER && prevLevel < ORACLE_MIN_LEVEL && lvl >= ORACLE_MIN_LEVEL) {
                s = s.addLogInternal("You have reached Level $ORACLE_MIN_LEVEL! Speak to The Oracle in Rookvale Temple for a generous Cap surge and vocation!")
            }
        }
        return s
    }

    fun awardMagicXpFromMana(state: GameWorldState, manaSpent: Int, nowMs: Long): GameWorldState {
        if (manaSpent <= 0) return state
        return advanceSkillPublic(state, SkillType.MAGIC, manaSpent.toDouble(), nowMs)
    }

    fun advanceSkillPublic(state: GameWorldState, skill: SkillType, rawPoints: Double, nowMs: Long): GameWorldState {
        val rate = when (skill) {
            SkillType.MELEE, SkillType.FIST, SkillType.CLUB, SkillType.SWORD, SkillType.AXE -> state.vocation.meleeGainRate
            SkillType.DISTANCE -> state.vocation.distanceGainRate
            SkillType.SHIELDING -> state.vocation.shieldingGainRate
            SkillType.MAGIC -> state.vocation.magicGainRate
            SkillType.FISHING -> 1.25
            SkillType.MINING -> 1.30
        }
        fun bump(sp: SkillProgress, isMagic: Boolean = false): Pair<SkillProgress, Boolean> {
            var cur = sp.copy(points = sp.points + rawPoints * rate)
            var up = false
            while (cur.points >= (if (isMagic) cur.requiredMagicXpForNext() else cur.requiredPointsForNext())) {
                val req = if (isMagic) cur.requiredMagicXpForNext() else cur.requiredPointsForNext()
                cur = SkillProgress(cur.level + 1, cur.points - req)
                up = true
            }
            return cur to up
        }
        fun syncBestMelee(skills: PlayerSkills, updated: SkillProgress): PlayerSkills {
            val best = if (updated.level > skills.melee.level ||
                (updated.level == skills.melee.level && updated.points > skills.melee.points)
            ) updated else skills.melee
            return skills.copy(melee = best)
        }
        val (newSkills, leveled, newLevel) = when (skill) {
            SkillType.MELEE -> bump(state.skills.melee).let { Triple(state.skills.copy(melee = it.first), it.second, it.first.level) }
            SkillType.FIST -> bump(state.skills.fist).let { Triple(syncBestMelee(state.skills.copy(fist = it.first), it.first), it.second, it.first.level) }
            SkillType.CLUB -> bump(state.skills.club).let { Triple(syncBestMelee(state.skills.copy(club = it.first), it.first), it.second, it.first.level) }
            SkillType.SWORD -> bump(state.skills.sword).let { Triple(syncBestMelee(state.skills.copy(sword = it.first), it.first), it.second, it.first.level) }
            SkillType.AXE -> bump(state.skills.axe).let { Triple(syncBestMelee(state.skills.copy(axe = it.first), it.first), it.second, it.first.level) }
            SkillType.DISTANCE -> bump(state.skills.distance).let { Triple(state.skills.copy(distance = it.first), it.second, it.first.level) }
            SkillType.SHIELDING -> bump(state.skills.shielding).let { Triple(state.skills.copy(shielding = it.first), it.second, it.first.level) }
            SkillType.MAGIC -> bump(state.skills.magic, isMagic = true).let { Triple(state.skills.copy(magic = it.first), it.second, it.first.level) }
            SkillType.FISHING -> bump(state.skills.fishing).let { Triple(state.skills.copy(fishing = it.first), it.second, it.first.level) }
            SkillType.MINING -> bump(state.skills.mining).let { Triple(state.skills.copy(mining = it.first), it.second, it.first.level) }
        }
        var next = state.copy(skills = newSkills)
        if (leveled) {
            next = next.addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat() - 0.35f, "${skill.displayName} $newLevel!", FloatingTextKind.SKILL_UP, nowMs)
                .addLogInternal("Advanced in ${skill.displayName} to level $newLevel!")
        }
        return next
    }

    /** Direct target-lock triggered from the Top-Right Battle List. */
    fun targetMonsterById(state: GameWorldState, monsterId: Int): GameWorldState {
        if (state.deathReport != null) return state
        val m = state.monsters.firstOrNull { it.id == monsterId && it.floor == state.floor } ?: return state
        val alreadyTargeting = state.targetMonsterId == m.id
        if (alreadyTargeting) {
            return state.copy(
                targetMonsterId = null,
                autoFollowTarget = false,
                walkPath = emptyList()
            ).addLogInternal("Released target on ${m.type.displayName}.")
        }
        val shouldFollow = state.isMeleeCombatSetup()
        val path = if (shouldFollow && chebyshev(state.playerX, state.playerY, m.x, m.y) > 1) {
            findPathToAdjacentOfTarget(state, m.x, m.y)
        } else {
            emptyList()
        }
        return state.copy(
            targetMonsterId = m.id,
            autoFollowTarget = shouldFollow,
            walkPath = path,
            activePanel = ActivePanel.NONE,
            inspectingCorpseId = null
        ).addLogInternal("Targeting ${m.type.displayName} [Lvl ${m.type.threatLevel}].")
    }

    /** A* auto-walk triggered from the clickable isometric minimap or Regional World Map. */
    fun walkToCoordinate(state: GameWorldState, tx: Int, ty: Int): GameWorldState {
        if (state.deathReport != null) return state
        val floorMap = WorldMaps.getFloor(state.floor)
        val clampedX = tx.coerceIn(0, floorMap.width - 1)
        val clampedY = ty.coerceIn(0, floorMap.height - 1)
        val dismissed = state.copy(
            activePanel = ActivePanel.NONE,
            inspectingCorpseId = null,
            autoFollowTarget = false
        )
        if (clampedX == state.playerX && clampedY == state.playerY) return dismissed
        val path = if (floorMap.tileAt(clampedX, clampedY).walkable) {
            findPath(dismissed, clampedX, clampedY)
        } else {
            findPathToAdjacentOfTarget(dismissed, clampedX, clampedY)
        }
        return if (path.isNotEmpty()) {
            dismissed.copy(walkPath = path)
                .addLogInternal("Auto-walking to ($clampedX, $clampedY)...")
        } else {
            dismissed.addLogInternal("No clear route to ($clampedX, $clampedY).")
        }
    }

    fun isCustomHotbarBindable(itemId: String): Boolean {
        val def = ItemCatalog.all[itemId] ?: return false
        return def.category == ItemCategory.POTION_HP ||
            def.category == ItemCategory.POTION_MP ||
            def.category == ItemCategory.RUNE_HEAL ||
            def.category == ItemCategory.RUNE_ATTACK ||
            def.category == ItemCategory.FOOD ||
            def.category == ItemCategory.TOOL_ROPE
    }

    fun bindCustomHotbarSlot(state: GameWorldState, slotIndex: Int, itemId: String?): GameWorldState {
        if (slotIndex !in 0..1) return state
        val current = List(2) { idx -> state.customHotbarSlots.getOrNull(idx) }.toMutableList()
        if (itemId == null) {
            current[slotIndex] = null
            return state.copy(customHotbarSlots = current.toList())
                .addLogInternal("Cleared Custom Hotbar Slot ${slotIndex + 1}.")
        }
        if (!isCustomHotbarBindable(itemId)) {
            return state.addLogInternal("Only potions, runes, or food can be bound to quick slots.")
        }
        current[slotIndex] = itemId
        val itemName = ItemCatalog.get(itemId).name
        return state.copy(customHotbarSlots = current.toList())
            .addLogInternal("Bound $itemName to Custom Slot ${slotIndex + 1}.")
    }

    fun useCustomHotbarSlot(state: GameWorldState, slotIndex: Int, nowMs: Long): GameWorldState {
        val itemId = state.customHotbarSlots.getOrNull(slotIndex) ?: return state
        if (!state.hasItem(itemId)) {
            val name = ItemCatalog.all[itemId]?.name ?: itemId
            return state.addLogInternal("Out of $name!")
        }
        return useOrEquipBackpackItem(state, itemId, nowMs)
    }

    fun useOrEquipBackpackItem(state: GameWorldState, itemId: String, nowMs: Long): GameWorldState {
        if (!state.hasItem(itemId)) return state
        val def = ItemCatalog.get(itemId)
        return when (def.category) {
            ItemCategory.POTION_HP -> useHealthPotion(state, nowMs)
            ItemCategory.POTION_MP -> useManaPotion(state, nowMs)
            ItemCategory.RUNE_BLANK -> state.addLogInternal("Bring Blank Runes to the Runecrafting Table (Rune Altar) in Rookvale to inscribe spells.")
            ItemCategory.RUNE_HEAL -> useHealingRune(state, nowMs, explicitRuneId = itemId)
            ItemCategory.RUNE_ATTACK -> useAttackRune(state, nowMs, explicitRuneId = itemId)
            ItemCategory.TOOL_ROPE -> useRope(state, nowMs)
            ItemCategory.TOOL_PICKAXE -> {
                val fl = WorldMaps.getFloor(state.floor)
                val vein = (-1..1).flatMap { dy -> (-1..1).map { dx -> (state.playerX + dx) to (state.playerY + dy) } }
                    .firstOrNull { (vx, vy) -> fl.tileAt(vx, vy).oreVeinKind() != null }
                if (vein != null) CraftingAndMiningEngine.mineOreNode(state, vein.first, vein.second, nowMs)
                else state.addLogInternal("Stand beside an Ore Vein and tap it to mine with your ${def.name}.")
            }
            ItemCategory.TOOL_HATCHET -> {
                val fl = WorldMaps.getFloor(state.floor)
                val standingTree = (-1..1).flatMap { dy -> (-1..1).map { dx -> (state.playerX + dx) to (state.playerY + dy) } }
                    .firstOrNull { (tx, ty) ->
                        fl.tileAt(tx, ty) == TileType.TREE &&
                            (state.choppedTrees[Triple(state.floor, tx, ty)] ?: 0) <= 0
                    }
                val anyTree = standingTree ?: (-1..1).flatMap { dy -> (-1..1).map { dx -> (state.playerX + dx) to (state.playerY + dy) } }
                    .firstOrNull { (tx, ty) -> fl.tileAt(tx, ty) == TileType.TREE }
                if (anyTree != null) CraftingAndMiningEngine.chopTree(state, anyTree.first, anyTree.second, nowMs)
                else state.addLogInternal("Stand beside an overland Tree and tap it to chop Wood Logs with your ${def.name}.")
            }
            ItemCategory.TOOL_FISHING_ROD -> {
                val fl = WorldMaps.getFloor(state.floor)
                val waterTile = (-2..2).flatMap { dy -> (-2..2).map { dx -> (state.playerX + dx) to (state.playerY + dy) } }
                    .firstOrNull { (wx, wy) -> fl.tileAt(wx, wy) == TileType.WATER }
                if (waterTile != null) attemptFishing(state, waterTile.first, waterTile.second, nowMs)
                else state.addLogInternal("Stand on the southern Fishing Pier near water to cast your line.")
            }
            ItemCategory.FOOD -> {
                val newHp = (state.hp + def.restoreAmount).coerceAtMost(state.maxHp)
                val newMp = (state.mp + def.restoreAmount / 2).coerceAtMost(state.maxMp)
                val healed = newHp - state.hp
                state.copy(hp = newHp, mp = newMp, backpack = removeItemPublic(state.backpack, itemId, 1))
                    .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+$healed HP", FloatingTextKind.HEAL, nowMs)
                    .queuePlayerFx("+$healed HP", FloatingTextKind.HEAL, PlayerActionState.HEALING)
                    .addLogInternal("Ate ${def.name}.")
            }
            ItemCategory.AMMO -> {
                state.copy(equipment = state.equipment.copy(ammoId = itemId))
                    .addLogInternal("Loaded ${def.name} into your Quiver slot.")
            }
            else -> {
                val slot = def.equipSlot ?: return state.addLogInternal("${def.name} can be smelted, forged, fletched, or sold to Alaric.")
                val previousEquipped = state.equipment.getSlot(slot)
                var bp = removeItemPublic(state.backpack, itemId, 1)
                if (previousEquipped != null) bp = addItemPublic(bp, previousEquipped, 1)
                var newEquip = state.equipment.withSlot(slot, itemId)
                val notes = StringBuilder()

                // Two-Handed weapon <-> Shield exclusivity
                if (slot == EquipmentSlot.WEAPON && def.isTwoHanded && newEquip.shieldId != null) {
                    val removedShield = newEquip.shieldId!!
                    bp = addItemPublic(bp, removedShield, 1)
                    newEquip = newEquip.copy(shieldId = null)
                    notes.append(" (2H weapon: Shield moved to bag — Shielding Dodge penalized)")
                } else if (slot == EquipmentSlot.SHIELD && newEquip.weaponDef()?.isTwoHanded == true) {
                    val removed2H = newEquip.weaponId!!
                    bp = addItemPublic(bp, removed2H, 1)
                    newEquip = newEquip.copy(weaponId = null)
                    notes.append(" (Unequipped 2H weapon to wield Shield)")
                }

                if (def.requiredSkillType != null && state.skills.levelFor(def.requiredSkillType) < def.requiredSkillLevel) {
                    notes.append(" (Req ${def.requiredSkillType.displayName} ${def.requiredSkillLevel} — -35% dmg)")
                }
                if (def.preferredVocation != null && state.vocation != def.preferredVocation) {
                    notes.append(" (${def.preferredVocation.displayName} mastery — -25% off-vocation dmg)")
                }
                val keepFollow = if (slot == EquipmentSlot.WEAPON && def.category != ItemCategory.WEAPON_MELEE) {
                    false
                } else {
                    state.autoFollowTarget
                }
                state.copy(equipment = newEquip, backpack = bp, autoFollowTarget = keepFollow)
                    .addLogInternal("Equipped ${def.name}.$notes")
            }
        }
    }

    fun unequipSlot(state: GameWorldState, slot: EquipmentSlot): GameWorldState {
        val equippedId = state.equipment.getSlot(slot) ?: return state
        if (slot == EquipmentSlot.AMMO) {
            return state.copy(equipment = state.equipment.copy(ammoId = null))
                .addLogInternal("Cleared active quiver selection.")
        }
        return state.copy(
            equipment = state.equipment.withSlot(slot, null),
            backpack = addItemPublic(state.backpack, equippedId, 1)
        ).addLogInternal("Unequipped ${ItemCatalog.get(equippedId).name}.")
    }

    fun dropItem(state: GameWorldState, itemId: String): GameWorldState {
        if (!state.hasItem(itemId)) return state
        return removeCarriedItem(state, itemId, 1)
            .addLogInternal("Dropped ${ItemCatalog.get(itemId).name}.")
    }

    /**
     * Transfers gold coins from a corpse or Fallen Backpack into the player's pouch, up to remaining Cap (0.1 oz/gp).
     * Automatically removes the corpse and closes the loot panel once completely emptied.
     */
    fun lootGoldFromCorpse(state: GameWorldState, corpseId: String, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        if (corpse.gold <= 0) return state
        val affordableGold = (state.remainingCapOz() / 0.1).toInt().coerceAtMost(corpse.gold)
        if (affordableGold <= 0) {
            return state.addLogInternal("Too heavy! Need at least 0.1 oz Cap to loot Gold Coins.")
        }
        val remainingGold = corpse.gold - affordableGold
        val isEmptyNow = remainingGold == 0 && corpse.items.isEmpty()
        val updatedCorpses = if (isEmptyNow) {
            state.corpses.filterNot { it.id == corpseId }
        } else {
            state.corpses.map { if (it.id == corpseId) it.copy(gold = remainingGold) else it }
        }
        val msg = when {
            isEmptyNow && corpse.isPlayerCorpse -> "Looted $affordableGold gp and recovered your fallen backpack!"
            isEmptyNow -> "Looted $affordableGold gp from ${corpse.label}."
            remainingGold > 0 -> "Looted $affordableGold gp, but ran out of Cap (oz) for the rest!"
            else -> "Looted $affordableGold gp."
        }
        return state.copy(
            gold = state.gold + affordableGold,
            corpses = updatedCorpses,
            activePanel = if (isEmptyNow) ActivePanel.NONE else state.activePanel,
            inspectingCorpseId = if (isEmptyNow) null else state.inspectingCorpseId
        )
            .addFloatingInternal(state.floor, corpse.x.toFloat(), corpse.y.toFloat(), "+$affordableGold gp", FloatingTextKind.XP, nowMs)
            .addLogInternal(msg)
    }

    /**
     * Transfers a tapped item stack from a corpse or Fallen Backpack into the player's inventory (or Tool Bag for tools),
     * moving as many units of the stack as fit within remaining carrying capacity (Cap).
     * Automatically removes the corpse and closes the loot panel once completely emptied.
     */
    fun lootItemFromCorpse(state: GameWorldState, corpseId: String, stackIndex: Int, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        val stack = corpse.items.getOrNull(stackIndex) ?: return state
        val def = ItemCatalog.get(stack.itemId)
        val unitWeight = def.carriedWeightOz()

        var s = state
        var taken = 0
        for (n in 1..stack.count) {
            if (s.remainingCapOz() + 1e-6 >= unitWeight) {
                s = addCarriedItem(s, stack.itemId, 1)
                taken++
            } else {
                break
            }
        }
        if (taken == 0) {
            return state.addLogInternal(
                "Too heavy! Need ${String.format(java.util.Locale.US, "%.1f", unitWeight)} oz Cap for ${def.name}."
            )
        }

        val updatedItems = corpse.items.toMutableList()
        if (taken >= stack.count) {
            updatedItems.removeAt(stackIndex)
        } else {
            updatedItems[stackIndex] = stack.copy(count = stack.count - taken)
        }

        val isEmptyNow = corpse.gold == 0 && updatedItems.isEmpty()
        val updatedCorpses = if (isEmptyNow) {
            s.corpses.filterNot { it.id == corpseId }
        } else {
            s.corpses.map { if (it.id == corpseId) it.copy(items = updatedItems) else it }
        }

        val qtyLabel = if (taken > 1) "${taken}x ${def.name}" else def.name
        val msg = when {
            isEmptyNow && corpse.isPlayerCorpse -> "Looted $qtyLabel and recovered your fallen backpack!"
            isEmptyNow -> "Looted $qtyLabel and emptied ${corpse.label}."
            taken < stack.count -> "Looted $taken/${stack.count} ${def.name} — ran out of Cap (oz)!"
            else -> "Looted $qtyLabel."
        }
        return s.copy(
            corpses = updatedCorpses,
            activePanel = if (isEmptyNow) ActivePanel.NONE else s.activePanel,
            inspectingCorpseId = if (isEmptyNow) null else s.inspectingCorpseId
        )
            .addFloatingInternal(s.floor, corpse.x.toFloat(), corpse.y.toFloat(), "+$qtyLabel", FloatingTextKind.INFO, nowMs)
            .addLogInternal(msg)
    }

    fun lootAllFromCorpse(state: GameWorldState, corpseId: String, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        var s = state
        var remainingGold = corpse.gold
        var lootedGold = 0
        if (remainingGold > 0) {
            val affordableGold = (s.remainingCapOz() / 0.1).toInt().coerceAtMost(remainingGold)
            if (affordableGold > 0) {
                s = s.copy(gold = s.gold + affordableGold)
                remainingGold -= affordableGold
                lootedGold = affordableGold
                s = s.addFloatingInternal(s.floor, corpse.x.toFloat(), corpse.y.toFloat(), "+$affordableGold gp", FloatingTextKind.XP, nowMs)
            }
        }
        val leftBehind = mutableListOf<ItemStack>()
        var lootedCount = 0
        for (stack in corpse.items) {
            val def = ItemCatalog.get(stack.itemId)
            val neededWeight = def.carriedWeightOz()
            var taken = 0
            for (n in 1..stack.count) {
                if (s.remainingCapOz() + 1e-6 >= neededWeight) {
                    s = addCarriedItem(s, stack.itemId, 1)
                    taken++; lootedCount++
                } else break
            }
            if (taken < stack.count) leftBehind += stack.copy(count = stack.count - taken)
        }
        val updatedCorpses = if (remainingGold == 0 && leftBehind.isEmpty()) s.corpses.filterNot { it.id == corpseId }
        else s.corpses.map { if (it.id == corpseId) it.copy(gold = remainingGold, items = leftBehind) else it }
        val closePanel = remainingGold == 0 && leftBehind.isEmpty()
        val msg = when {
            closePanel && corpse.isPlayerCorpse -> "Recovered your fallen backpack!"
            closePanel -> "Looted everything from ${corpse.label}."
            lootedCount > 0 || lootedGold > 0 -> "Looted $lootedCount item(s), but ran out of Cap (oz) for remaining items!"
            else -> "Too heavy! Smelt ores or store items in your Depot."
        }
        return s.copy(
            corpses = updatedCorpses,
            activePanel = if (closePanel) ActivePanel.NONE else s.activePanel,
            inspectingCorpseId = if (closePanel) null else s.inspectingCorpseId
        ).addLogInternal(msg)
    }

    fun depositItem(state: GameWorldState, itemId: String): GameWorldState {
        return try {
            val cleanId = itemId.trim()
            if (cleanId.isEmpty() || !state.hasItem(cleanId)) return state
            val def = ItemCatalog.get(cleanId)
            val unitWeight = def.carriedWeightOz()
            if (!unitWeight.isFinite() || unitWeight < 0.0) {
                return state.addLogInternal("Warning: Could not deposit ${def.name} (invalid item weight).")
            }
            val afterRemove = removeCarriedItem(state, cleanId, 1)
            val updatedDepot = addItemPublic(afterRemove.depot, cleanId, 1)
            val updated = afterRemove.copy(
                backpack = afterRemove.backpack.toList(),
                toolBag = afterRemove.toolBag.toList(),
                depot = updatedDepot
            )
            // Validate Cap recalculation safely before committing
            val capCheck = updated.remainingCapOz()
            if (!capCheck.isFinite()) {
                return state.addLogInternal("Warning: Cap recalculation failed while depositing ${def.name}.")
            }
            updated.addLogInternal("Stored ${def.name} in Depot.")
        } catch (t: Throwable) {
            state.addLogInternal("Warning: Failed to deposit item (${t.message ?: "unexpected error"}).").copy(
                backpack = state.backpack.toList(),
                toolBag = state.toolBag.toList(),
                depot = state.depot.toList()
            )
        }
    }

    fun withdrawItem(state: GameWorldState, itemId: String): GameWorldState {
        return try {
            val cleanId = itemId.trim()
            if (cleanId.isEmpty() || state.depot.none { it.itemId == cleanId && it.count > 0 }) return state
            val def = ItemCatalog.get(cleanId)
            val neededWeight = def.carriedWeightOz()
            if (!neededWeight.isFinite() || neededWeight < 0.0) {
                return state.addLogInternal("Warning: Could not withdraw ${def.name} (invalid item weight).")
            }
            val currentRemainingCap = state.remainingCapOz()
            if (!currentRemainingCap.isFinite() || currentRemainingCap + 1e-6 < neededWeight) {
                return state.addLogInternal(
                    "Not enough Cap (${String.format(java.util.Locale.US, "%.1f", neededWeight)} oz needed)."
                )
            }
            val destNote = if (def.isTool) " into Tool Bag" else ""
            val depotAfterRemove = removeItemPublic(state.depot, cleanId, 1)
            val updated = addCarriedItem(state.copy(depot = depotAfterRemove), cleanId, 1)
            val safeUpdated = updated.copy(
                backpack = updated.backpack.toList(),
                toolBag = updated.toolBag.toList(),
                depot = updated.depot.toList()
            )
            if (!safeUpdated.remainingCapOz().isFinite()) {
                return state.addLogInternal("Warning: Cap recalculation failed while withdrawing ${def.name}.")
            }
            safeUpdated.addLogInternal("Withdrew ${def.name}$destNote from Depot.")
        } catch (t: Throwable) {
            state.addLogInternal("Warning: Failed to withdraw item (${t.message ?: "unexpected error"}).").copy(
                backpack = state.backpack.toList(),
                toolBag = state.toolBag.toList(),
                depot = state.depot.toList()
            )
        }
    }

    fun buyShopItem(state: GameWorldState, itemId: String): GameWorldState {
        val def = ItemCatalog.get(itemId)
        if (def.buyPrice <= 0) return state
        if (state.gold < def.buyPrice) return state.addLogInternal("Not enough Gold (${def.buyPrice} gp required).")
        val neededWeight = def.carriedWeightOz()
        val netWeightDelta = neededWeight - def.buyPrice * 0.1
        if (netWeightDelta > 0 && state.remainingCapOz() < netWeightDelta) {
            return state.addLogInternal("Too heavy! Need ${String.format("%.1f", neededWeight)} oz Cap.")
        }
        val bagNote = if (def.isTool) " (packed in Tool Bag)" else ""
        return addCarriedItem(state.copy(gold = state.gold - def.buyPrice), itemId, 1)
            .addLogInternal("Bought ${def.name} for ${def.buyPrice} gp$bagNote.")
    }

    fun sellBackpackItem(state: GameWorldState, itemId: String): GameWorldState {
        if (!state.hasItem(itemId)) return state
        val def = ItemCatalog.get(itemId)
        if (def.sellPrice <= 0) return state.addLogInternal("Alaric isn't interested in ${def.name}.")
        return removeCarriedItem(state, itemId, 1)
            .let { it.copy(gold = it.gold + def.sellPrice) }
            .addLogInternal("Sold ${def.name} for ${def.sellPrice} gp.")
    }

    fun addCarriedItem(state: GameWorldState, itemId: String, amount: Int): GameWorldState {
        return if (ItemCatalog.get(itemId).isTool) {
            state.copy(toolBag = addItemPublic(state.toolBag, itemId, amount))
        } else {
            state.copy(backpack = addItemPublic(state.backpack, itemId, amount))
        }
    }

    fun removeCarriedItem(state: GameWorldState, itemId: String, amount: Int): GameWorldState {
        var remaining = amount
        var tb = state.toolBag
        var bp = state.backpack
        val inTools = tb.filter { it.itemId == itemId }.sumOf { it.count }
        if (inTools > 0) {
            val takeFromTools = minOf(inTools, remaining)
            tb = removeItemPublic(tb, itemId, takeFromTools)
            remaining -= takeFromTools
        }
        if (remaining > 0) {
            bp = removeItemPublic(bp, itemId, remaining)
        }
        return state.copy(backpack = bp, toolBag = tb)
    }

    private fun blessAtTemple(state: GameWorldState, nowMs: Long): GameWorldState {
        return state.copy(hp = state.maxHp, mp = state.maxMp, poisonTicks = 0, fireTicks = 0)
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "BLESSED!", FloatingTextKind.HEAL, nowMs)
            .queuePlayerFx("BLESSED!", FloatingTextKind.HEAL, PlayerActionState.HEALING)
            .addLogInternal("Brother Alden: 'May the light of Rookvale restore your body and spirit.'")
    }

    private fun setWalkPathToAdjacent(
        state: GameWorldState,
        tx: Int,
        ty: Int,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        val adj = listOf(tx to ty - 1, tx to ty + 1, tx - 1 to ty, tx + 1 to ty)
            .filter { (x, y) ->
                floorMap.tileAt(x, y).walkable && !isBlockedByRemotePlayer(floorMap, x, y, remotePlayersOnFloor)
            }
            .minByOrNull { (x, y) -> abs(x - state.playerX) + abs(y - state.playerY) }
            ?: return state
        return state.copy(walkPath = findPath(state, adj.first, adj.second, remotePlayersOnFloor))
    }

    /**
     * Finds the shortest BFS path to any walkable tile within Chebyshev distance 1 of a target monster
     * (all 8 surrounding melee tiles, prioritizing cardinal tiles when tied), avoiding monsters, NPCs,
     * and non-PZ remote players.
     */
    private fun findPathToAdjacentOfTarget(
        state: GameWorldState,
        targetX: Int,
        targetY: Int,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): List<Pair<Int, Int>> {
        val floorMap = WorldMaps.getFloor(state.floor)
        val monsterSet = state.monsters.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()
        val npcSet = WorldMaps.npcs.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()

        val candidates = listOf(
            targetX to targetY - 1,
            targetX to targetY + 1,
            targetX - 1 to targetY,
            targetX + 1 to targetY,
            targetX - 1 to targetY - 1,
            targetX + 1 to targetY - 1,
            targetX - 1 to targetY + 1,
            targetX + 1 to targetY + 1
        ).filter { (cx, cy) ->
            cx in 0 until floorMap.width &&
                cy in 0 until floorMap.height &&
                floorMap.tileAt(cx, cy).walkable &&
                (cx to cy) !in monsterSet &&
                (cx to cy) !in npcSet &&
                !isBlockedByRemotePlayer(floorMap, cx, cy, remotePlayersOnFloor)
        }.sortedBy { (cx, cy) -> abs(cx - state.playerX) + abs(cy - state.playerY) }

        var bestPath: List<Pair<Int, Int>> = emptyList()
        for ((cx, cy) in candidates) {
            val p = findPath(state, cx, cy, remotePlayersOnFloor)
            if (p.isNotEmpty() && (bestPath.isEmpty() || p.size < bestPath.size)) {
                bestPath = p
            }
        }
        return bestPath
    }

    private fun findPath(
        state: GameWorldState,
        goalX: Int,
        goalY: Int,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): List<Pair<Int, Int>> {
        if (state.playerX == goalX && state.playerY == goalY) return emptyList()
        val floorMap = WorldMaps.getFloor(state.floor)
        val width = floorMap.width
        val height = floorMap.height
        if (goalX !in 0 until width || goalY !in 0 until height) return emptyList()

        val monsterSet = state.monsters.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()
        val npcSet = WorldMaps.npcs.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()

        // True A* search with Manhattan heuristic + closest-tile waypoint fallback for 400x400 overland
        val startKey = state.playerY * width + state.playerX
        val goalKey = goalY * width + goalX
        val gScore = HashMap<Int, Int>(512)
        val parent = HashMap<Int, Int>(512)
        val closed = HashSet<Int>(512)
        // Encode (fScore, key) as LongArray-free lightweight Node
        val open = PriorityQueue<Pair<Int, Int>>(256, compareBy<Pair<Int, Int>> { it.first })

        val initialH = abs(goalX - state.playerX) + abs(goalY - state.playerY)
        gScore[startKey] = 0
        open.add(initialH to startKey)

        var bestKey = startKey
        var bestDist = initialH
        var found = false
        var explored = 0
        val maxExpansions = if (width >= 200) 4500 else 1200

        while (open.isNotEmpty() && explored < maxExpansions) {
            val (_, curKey) = open.poll() ?: break
            if (!closed.add(curKey)) continue
            explored++

            if (curKey == goalKey) {
                found = true
                bestKey = curKey
                break
            }

            val cx = curKey % width
            val cy = curKey / width
            val curDist = abs(goalX - cx) + abs(goalY - cy)
            if (curDist < bestDist) {
                bestDist = curDist
                bestKey = curKey
            }

            val nextG = (gScore[curKey] ?: 0) + 1
            for (dir in Direction.entries) {
                val nx = cx + dir.dx
                val ny = cy + dir.dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val nKey = ny * width + nx
                if (nKey in closed) continue
                if (!floorMap.tileAt(nx, ny).walkable) continue
                val coord = nx to ny
                if (coord in monsterSet || coord in npcSet) continue
                if (isBlockedByRemotePlayer(floorMap, nx, ny, remotePlayersOnFloor)) continue

                val prevG = gScore[nKey]
                if (prevG == null || nextG < prevG) {
                    gScore[nKey] = nextG
                    parent[nKey] = curKey
                    val h = abs(goalX - nx) + abs(goalY - ny)
                    open.add((nextG + h) to nKey)
                }
            }
        }

        val targetKey = if (found) goalKey else if (bestKey != startKey) bestKey else return emptyList()
        val path = ArrayList<Pair<Int, Int>>(64)
        var curr: Int? = targetKey
        while (curr != null && curr != startKey) {
            path.add((curr % width) to (curr / width))
            curr = parent[curr]
        }
        path.reverse()
        return path
    }

    fun addItemPublic(list: List<ItemStack>, itemId: String, amount: Int): List<ItemStack> {
        val cleanId = itemId.trim()
        val sanitized = list.filter { it.itemId.isNotBlank() && it.count > 0 }
        if (cleanId.isEmpty() || amount <= 0) return sanitized.toList()
        val def = ItemCatalog.get(cleanId)
        val out = sanitized.toMutableList()
        if (def.stackable) {
            val idx = out.indexOfFirst { it.itemId == cleanId }
            if (idx >= 0) {
                val mergedCount = (out[idx].count.toLong() + amount.toLong()).coerceIn(1L, 999_999L).toInt()
                out[idx] = out[idx].copy(count = mergedCount)
                return out.toList()
            }
            out.add(ItemStack(cleanId, amount.coerceIn(1, 999_999)))
            return out.toList()
        }
        repeat(amount) { out.add(ItemStack(cleanId, 1)) }
        return out.toList()
    }

    fun removeItemPublic(list: List<ItemStack>, itemId: String, amount: Int): List<ItemStack> {
        val cleanId = itemId.trim()
        val sanitized = list.filter { it.itemId.isNotBlank() && it.count > 0 }
        if (cleanId.isEmpty() || amount <= 0) return sanitized.toList()
        var rem = amount
        val out = ArrayList<ItemStack>(sanitized.size)
        for (cur in sanitized) {
            if (rem > 0 && cur.itemId == cleanId) {
                if (cur.count <= rem) {
                    rem -= cur.count
                    // Depleted stack (count <= 0) is omitted from the new immutable list
                } else {
                    val left = cur.count - rem
                    rem = 0
                    if (left > 0) out.add(cur.copy(count = left))
                }
            } else {
                out.add(cur)
            }
        }
        return out.toList()
    }

    private fun pruneExpiredEffects(state: GameWorldState, nowMs: Long): GameWorldState {
        val activeBanner = state.floorWarningBanner?.takeIf { nowMs - it.createdAtMs < 3500L }
        val keepInspected = state.inspectedPlayerId != null && nowMs < state.inspectedPlayerUntilMs
        return state.copy(
            floatingTexts = state.floatingTexts.filter { nowMs - it.createdAtMs < 1350L },
            projectiles = state.projectiles.filter { nowMs - it.createdAtMs < it.durationMs },
            floorWarningBanner = activeBanner,
            inspectedPlayerId = if (keepInspected) state.inspectedPlayerId else null,
            inspectedPlayerUntilMs = if (keepInspected) state.inspectedPlayerUntilMs else 0L
        )
    }

    internal fun allocateEffectId(): Long = nextEffectId++

    internal fun directionToward(fromX: Int, fromY: Int, toX: Int, toY: Int): Direction {
        val dx = toX - fromX
        val dy = toY - fromY
        return if (abs(dx) >= abs(dy)) {
            if (dx >= 0) Direction.EAST else Direction.WEST
        } else {
            if (dy >= 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun chebyshev(x1: Int, y1: Int, x2: Int, y2: Int): Int = max(abs(x1 - x2), abs(y1 - y2))
}

internal fun GameWorldState.addFloatingInternal(floor: Int, x: Float, y: Float, text: String, kind: FloatingTextKind, nowMs: Long): GameWorldState {
    val jitterX = Random.nextFloat() * 0.25f - 0.125f
    return copy(floatingTexts = (floatingTexts + FloatingText(GameEngine.allocateEffectId(), floor, x + jitterX, y, text, kind, nowMs)).takeLast(24))
}

internal fun GameWorldState.addProjectileInternal(floor: Int, fx: Float, fy: Float, tx: Float, ty: Float, kind: ProjectileKind, nowMs: Long, durationMs: Long = 260L): GameWorldState {
    return copy(projectiles = (projectiles + ProjectileEffect(GameEngine.allocateEffectId(), floor, fx, fy, tx, ty, kind, nowMs, durationMs)).takeLast(14))
}

internal fun GameWorldState.queuePlayerFx(
    text: String?,
    kind: FloatingTextKind?,
    actionState: PlayerActionState,
    projectileKind: ProjectileKind? = null,
    toX: Int? = null,
    toY: Int? = null
): GameWorldState {
    val event = LocalPlayerActionFxEvent(
        floor = floor,
        x = playerX,
        y = playerY,
        text = text,
        floatingKindName = kind?.name,
        actionState = actionState,
        projectileKindName = projectileKind?.name,
        toX = toX,
        toY = toY
    )
    return copy(outboundPlayerEffects = (outboundPlayerEffects + event).takeLast(10))
}

internal fun GameWorldState.addLogInternal(message: String): GameWorldState {
    return copy(statusLog = (statusLog + message).takeLast(6))
}
