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
import com.example.game.multiplayer.SharedLootClaimEvent
import com.example.game.multiplayer.SharedLootSpawnEvent
import com.example.game.multiplayer.SyncedMonsterSnapshot
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.NpcRole
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import java.util.ArrayDeque
import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

object GameEngine {
    private var nextEffectId = 1L
    private var nextMonsterId = 1000

    fun createNewWorld(
        name: String,
        vocation: Vocation = Vocation.ADVENTURER,
        tunicColorIndex: Int = 0,
        hairColorIndex: Int = 0,
        isAdmin: Boolean = false
    ): GameWorldState {
        val cleanName = if (isAdmin) "Admin" else name.trim().ifEmpty { "Aldric" }.take(18)
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
            ItemStack("bronze_pickaxe", 1),
            ItemStack("rope", 1),
            ItemStack("fishing_rod", 1)
        )
        val starterBackpack = when (vocation) {
            Vocation.ADVENTURER -> listOf(
                ItemStack("health_potion", 4),
                ItemStack("mana_potion", 2),
                ItemStack("magic_dart_rune", 3),
                ItemStack("blank_rune", 2),
                ItemStack("cheese", 3),
                ItemStack("garden_worm", 15)
            )
            Vocation.KNIGHT -> listOf(
                ItemStack("health_potion", 4),
                ItemStack("mana_potion", 1),
                ItemStack("magic_dart_rune", 2),
                ItemStack("blank_rune", 1),
                ItemStack("cheese", 3),
                ItemStack("garden_worm", 15)
            )
            Vocation.PALADIN -> listOf(
                ItemStack("ochre_shafts", 45),
                ItemStack("health_potion", 3),
                ItemStack("mana_potion", 2),
                ItemStack("magic_dart_rune", 4),
                ItemStack("blank_rune", 2),
                ItemStack("cheese", 3),
                ItemStack("garden_worm", 15)
            )
            Vocation.MAGE -> listOf(
                ItemStack("health_potion", 2),
                ItemStack("mana_potion", 5),
                ItemStack("magic_dart_rune", 6),
                ItemStack("blank_rune", 4),
                ItemStack("cheese", 2),
                ItemStack("garden_worm", 15)
            )
        }
        val introLine = if (vocation == Vocation.ADVENTURER) {
            "Train any skill freely! Forge bows or scepters at the Smithy, and visit The Oracle at Level 20."
        } else {
            "Mine Ochre Veins in the West Quarry, smelt at the Smithy Furnace, and forge at the Anvil."
        }
        val introLog = if (isAdmin) {
            listOf(
                "Welcome, Admin! Infinite Health is active.",
                "Tap the golden Crown button in the bottom-right corner to open Admin Control (Spectator, Time of Day, Level & Tier 5 Armory)."
            )
        } else {
            listOf(
                "Welcome to Rookvale, $cleanName the ${vocation.displayName}!",
                introLine
            )
        }
        return GameWorldState(
            saveId = UUID.randomUUID().toString(),
            playerName = cleanName,
            vocation = vocation,
            isAdmin = isAdmin,
            infiniteHealth = isAdmin,
            isSpectator = false,
            timeOfDayHours = DEFAULT_TIME_OF_DAY_HOURS,
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
            selectedBaitId = "garden_worm",
            statusLog = introLog
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
        val isLegacyV3 = save.mapVersion == 3
        val floorMap = WorldMaps.getFloor(save.floor)

        var targetX = save.x
        var targetY = save.y
        if (save.floor == 0) {
            if (isLegacyV1) {
                targetX += V1_SURFACE_OFFSET + V2_TO_V3_OFFSET_X + V3_TO_V4_OFFSET_X
                targetY += V1_SURFACE_OFFSET + V2_TO_V3_OFFSET_Y + V3_TO_V4_OFFSET_Y
            } else if (isLegacyV2) {
                targetX += V2_TO_V3_OFFSET_X + V3_TO_V4_OFFSET_X
                targetY += V2_TO_V3_OFFSET_Y + V3_TO_V4_OFFSET_Y
            } else if (isLegacyV3) {
                targetX += V3_TO_V4_OFFSET_X
                targetY += V3_TO_V4_OFFSET_Y
            }
        }
        // Map v5 rebuilt the Deep Delve floors (-1..-4) as larger 96x96 layouts: anyone saved inside
        // one of them resumes at that floor's forward-camp entrance instead of an unrelated spot.
        if (save.mapVersion < DEEP_DELVE_REBUILD_MAP_VERSION && save.floor in -4..-1) {
            val (lx, ly) = WorldMaps.fallbackLandingForFloor(save.floor)
            targetX = lx
            targetY = ly
        }
        // Map v6 rebuilt Quarry Mines (-11), North Mountain Cavern (-12) and Crag Deep Hollow (-14).
        if (save.mapVersion < HEARTLAND_SITE_REBUILD_MAP_VERSION && save.floor in setOf(-11, -12, -14)) {
            val (lx, ly) = WorldMaps.fallbackLandingForFloor(save.floor)
            targetX = lx
            targetY = ly
        }
        // Map v7 rebuilt Gorek Labyrinth (-6), Sunken Mire Vaults (-7) and the Overgrown Ziggurat (-8).
        if (save.mapVersion < FRONTIER_SITE_REBUILD_MAP_VERSION && save.floor in setOf(-6, -7, -8)) {
            val (lx, ly) = WorldMaps.fallbackLandingForFloor(save.floor)
            targetX = lx
            targetY = ly
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
        if (save.floor == 0) {
            floorMap.chunkedGrid.prewarmAround(targetX, targetY, radiusChunks = 2)
        }
        val resolvedRegionId = if (save.floor == 0) {
            WorldMaps.regionForGlobal(targetX, targetY).regionId
        } else {
            normalizeRegionId(save.regionId)
        }

        // Normalize legacy ore/item IDs and auto-migrate any tools sitting in backpack into the Tool Bag
        var migratedToolBag = emptyList<ItemStack>()
        for (stack in save.toolBag) {
            val cleanId = ItemCatalog.migrateLegacyItemId(stack.itemId)
            migratedToolBag = addItemPublic(migratedToolBag, cleanId, stack.count)
        }
        var migratedBackpack = mutableListOf<ItemStack>()
        var movedToolsCount = 0
        for (stack in save.backpack) {
            val cleanId = ItemCatalog.migrateLegacyItemId(stack.itemId)
            if (ItemCatalog.get(cleanId).isTool) {
                migratedToolBag = addItemPublic(migratedToolBag, cleanId, stack.count)
                movedToolsCount += stack.count
            } else {
                migratedBackpack = addItemPublic(migratedBackpack, cleanId, stack.count).toMutableList()
            }
        }
        var normalizedDepot = emptyList<ItemStack>()
        for (stack in save.depot) {
            normalizedDepot = addItemPublic(normalizedDepot, ItemCatalog.migrateLegacyItemId(stack.itemId), stack.count)
        }
        // Normalize Reaper Vault items & rescue items from any legacy pre-refactor playerCorpses
        var normalizedReaperVault = emptyList<ItemStack>()
        for (stack in save.reaperVault) {
            normalizedReaperVault = addItemPublic(normalizedReaperVault, ItemCatalog.migrateLegacyItemId(stack.itemId), stack.count)
        }
        for (legacyCorpse in save.playerCorpses) {
            for (stack in legacyCorpse.items) {
                normalizedReaperVault = addItemPublic(normalizedReaperVault, ItemCatalog.migrateLegacyItemId(stack.itemId), stack.count)
            }
        }

        // Safely unequip any shield conflicting with a 2H weapon on older saves
        var migratedEquip = save.equipment
        if (migratedEquip.weaponDef()?.isTwoHanded == true && migratedEquip.shieldId != null) {
            migratedBackpack = addItemPublic(migratedBackpack, migratedEquip.shieldId!!, 1).toMutableList()
            migratedEquip = migratedEquip.copy(shieldId = null)
        }

        val hasAnyPickaxe = (migratedBackpack + migratedToolBag + normalizedDepot)
            .any { ItemCatalog.get(it.itemId).category == ItemCategory.TOOL_PICKAXE }
        val migratedDepot = if (isLegacyV1 && !hasAnyPickaxe) {
            addItemPublic(normalizedDepot, "bronze_pickaxe", 1)
        } else {
            normalizedDepot
        }

        val notes = buildString {
            if (isLegacyV1) append(" (Migrated V1 save — a Bronze Pickaxe awaits in Depot!)")
            if (movedToolsCount > 0) append(" (Packed $movedToolsCount tool(s) into your Tool Bag!)")
            if (normalizedReaperVault.isNotEmpty()) {
                val totalHeld = normalizedReaperVault.sumOf { it.count }
                append(" (Mortis holds $totalHeld lost item(s) at the Temple of Reapers.)")
            }
        }
        // Legacy Sovereign's Scroll has been superseded by the bottom-right Admin Control HUD overlay.
        val finalBackpack = migratedBackpack.filterNot {
            it.itemId == "admin_sovereign_scroll" || ItemCatalog.all[it.itemId]?.adminOnly == true
        }
        val finalDepot = migratedDepot.filterNot {
            it.itemId == "admin_sovereign_scroll" || ItemCatalog.all[it.itemId]?.adminOnly == true
        }
        val loadedMaxHp = save.maxHp + migratedEquip.totalBonusMaxHp()
        val loadedHp = if (save.isAdmin) loadedMaxHp else save.hp.coerceIn(1, loadedMaxHp)

        return GameWorldState(
            saveId = save.slotId,
            playerName = if (save.isAdmin) "Admin" else save.name,
            vocation = voc,
            isAdmin = save.isAdmin,
            infiniteHealth = save.isAdmin,
            isSpectator = false,
            timeOfDayHours = DEFAULT_TIME_OF_DAY_HOURS,
            tunicColorIndex = save.tunicColorIndex.coerceIn(0, AdventurerPalette.tunicColors.lastIndex),
            hairColorIndex = save.hairColorIndex.coerceIn(0, AdventurerPalette.hairColors.lastIndex),
            level = save.level,
            xp = save.xp,
            hp = loadedHp,
            maxHp = save.maxHp,
            mp = save.mp.coerceIn(0, save.maxMp),
            maxMp = save.maxMp,
            soul = save.soul,
            maxSoul = save.maxSoul,
            maxCapOz = maxOf(save.maxCapOz, GameWorldState.maxCapForLevel(save.level, voc)),
            gold = save.gold,
            bankGold = save.bankGold.coerceAtLeast(0L),
            regionId = resolvedRegionId,
            floor = save.floor,
            playerX = targetX,
            playerY = targetY,
            prevPlayerX = targetX,
            prevPlayerY = targetY,
            playerFacing = Direction.SOUTH,
            poisonTicks = 0,
            fireTicks = 0,
            skills = save.skills,
            equipment = migratedEquip,
            backpack = finalBackpack,
            toolBag = migratedToolBag,
            depot = finalDepot,
            reaperVault = normalizedReaperVault,
            // World entities always initialize deterministically from WorldMaps (never loaded from DB)
            monsters = WorldMaps.initialMonsters(),
            oreNodes = WorldMaps.initialOreNodes(),
            corpses = emptyList(),
            questFlags = save.questFlags,
            boundAttackRuneId = save.boundAttackRuneId ?: "magic_dart_rune",
            boundHealRuneId = save.boundHealRuneId ?: "healing_rune",
            selectedBaitId = save.selectedBaitId,
            customHotbarSlots = HotbarActions.migrate(save.customHotbarSlots),
            npcProgress = save.npcProgress,
            statusLog = listOf("Loaded ${save.name} (Lvl ${save.level} ${voc.displayName}).$notes")
        )
    }

    fun toSaveData(state: GameWorldState): PlayerSaveData {
        return PlayerSaveData(
            slotId = state.saveId,
            name = state.playerName,
            vocationName = state.vocation.name,
            isAdmin = state.isAdmin,
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
            bankGold = state.bankGold.coerceAtLeast(0L),
            floor = state.floor.coerceIn(com.example.game.world.BiomeProgressionRules.MIN_DUNGEON_FLOOR, 0),
            x = state.playerX,
            y = state.playerY,
            poisonTicks = 0,
            fireTicks = 0,
            skills = state.skills,
            equipment = state.equipment,
            backpack = state.backpack.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            toolBag = state.toolBag.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            depot = state.depot.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            reaperVault = state.reaperVault.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            playerCorpses = emptyList(),
            mapVersion = CURRENT_MAP_VERSION,
            tunicColorIndex = state.tunicColorIndex,
            hairColorIndex = state.hairColorIndex,
            questFlags = state.questFlags.toMap(),
            boundAttackRuneId = state.boundAttackRuneId,
            boundHealRuneId = state.boundHealRuneId,
            selectedBaitId = state.selectedBaitId,
            customHotbarSlots = List(HotbarActions.MAX_SLOTS) { idx -> state.customHotbarSlots.getOrNull(idx) },
            regionId = normalizeRegionId(state.regionId),
            facingName = Direction.SOUTH.name,
            npcProgress = state.npcProgress.toMap()
        )
    }

    /** Time per tile at level 1. */
    const val BASE_STEP_INTERVAL_MS: Long = 210L
    /** Fastest possible walking pace. */
    const val MIN_STEP_INTERVAL_MS: Long = 120L
    private const val STEP_SPEED_LEVEL_SCALE: Double = 40.0

    /**
     * Walking gets faster as the hero levels up, with diminishing returns:
     * Lv 1 = 210 ms per tile, Lv 20 ~ 182 ms, Lv 50 ~ 146 ms, Lv 100 ~ 127 ms (never below 120 ms).
     * The same interval drives the step timer, the walk animation and remote-player interpolation.
     */
    fun stepIntervalMs(level: Int): Long {
        val lv = (level - 1).coerceAtLeast(0)
        val span = (BASE_STEP_INTERVAL_MS - MIN_STEP_INTERVAL_MS).toDouble()
        return (MIN_STEP_INTERVAL_MS + span * kotlin.math.exp(-lv / STEP_SPEED_LEVEL_SCALE)).roundToLong()
            .coerceIn(MIN_STEP_INTERVAL_MS, BASE_STEP_INTERVAL_MS)
    }

    fun walkSpeedTilesPerSec(level: Int): Float = 1000f / stepIntervalMs(level)

    /** True when the previous step's animation has finished and the hero may take the next step. */
    fun isStepReady(state: GameWorldState, nowMs: Long): Boolean =
        state.lastPlayerMoveMs <= 0L || nowMs - state.lastPlayerMoveMs >= stepIntervalMs(state.level)

    /** 0..1 progress of the current step's glide, used by the renderer. */
    fun stepGlideFraction(state: GameWorldState, nowMs: Long): Float =
        ((nowMs - state.lastPlayerMoveMs).toFloat() / stepIntervalMs(state.level)).coerceIn(0f, 1f)

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
            state.copy(activePanel = ActivePanel.NONE, inspectingCorpseId = null, activeBiomeStall = null)
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
            return if (engageMeleeFollow && distToMonster > 1 && isStepReady(targeted, nowMs)) {
                stepAutoFollowOrPath(targeted, nowMs, remotePlayersOnFloor)
            } else {
                targeted
            }
        }

        // Any tap on a non-monster tile cancels melee auto-follow so the player can disengage freely
        val baseState = (if (dismissedState.autoFollowTarget) dismissedState.copy(autoFollowTarget = false) else dismissedState)
            .let { if (it.pendingLootCorpseId != null) it.copy(pendingLootCorpseId = null) else it }

        // 1.5 Tap Remote Player -> Tibia-style Look / Inspect + Adventurer Action Card (Trade)
        val tappedPlayer = remotePlayersOnFloor.firstOrNull { it.floor == baseState.floor && it.x == tx && it.y == ty }
        if (tappedPlayer != null) {
            return baseState.copy(
                inspectedPlayerId = tappedPlayer.playerId,
                inspectedPlayerUntilMs = nowMs + 8_000L,
                walkPath = emptyList()
            ).addLogInternal("You see ${tappedPlayer.name} (Level ${tappedPlayer.level} ${tappedPlayer.vocation.displayName}).")
        }

        val npc = WorldMaps.npcs.firstOrNull { it.floor == baseState.floor && it.x == tx && it.y == ty }
        val tile = floorMap.tileAt(tx, ty)
        val dist = chebyshev(baseState.playerX, baseState.playerY, tx, ty)

        // 2. NPC or Shop Counter (Brother Alden, The Oracle, Alaric, Harbor Captains)
        if (npc != null || tile == TileType.SHOP_COUNTER) {
            val townsfolk = npc?.let { NpcInteractions.talkTo(baseState, it) }
            if (townsfolk != null && dist <= 2) return townsfolk
            return if (dist <= 2) {
                when (npc?.role) {
                    NpcRole.TEMPLE_PRIEST -> blessAtTemple(baseState, nowMs)
                    NpcRole.ORACLE -> baseState.copy(activePanel = ActivePanel.ORACLE, walkPath = emptyList(), longRangeGoal = null, longRangeWaypoints = emptyList())
                        .addLogInternal(
                            when {
                                baseState.vocation != Vocation.ADVENTURER -> "The Oracle: 'Walk proudly on your chosen path, ${baseState.vocation.displayName}.'"
                                baseState.level < ORACLE_MIN_LEVEL -> "The Oracle: 'You are not yet ready, child of Rookvale. Return at Level $ORACLE_MIN_LEVEL.'"
                                else -> "The Oracle: 'Level ${baseState.level} achieved! Are you ready to claim your vocation?'"
                            }
                        )
                    NpcRole.HARBOR_CAPTAIN -> {
                        val port = WorldMaps.harborPortAtNpc(npc.x, npc.y)
                        baseState.copy(
                            activePanel = ActivePanel.HARBOR_FERRY,
                            activeHarborPortId = port?.id,
                            walkPath = emptyList(),
                            longRangeGoal = null,
                            longRangeWaypoints = emptyList()
                        ).addLogInternal("${npc.name}: 'Fair winds, traveler! Choose any coastal port for immediate passage.'")
                    }
                    NpcRole.REAPER_KEEPER -> {
                        val heldCount = baseState.reaperVault.sumOf { it.count }
                        val totalFee = reaperTotalReclaimFee(baseState.reaperVault)
                        val greeting = if (heldCount > 0) {
                            "Mortis: 'I hold $heldCount item(s) reclaimed from your fall. Offer ${formatGold(totalFee.toLong())} gp to restore them.'"
                        } else {
                            "Mortis: 'The Soul Basin is empty — tread carefully, mortal. Should you fall, your backpack shall await you here.'"
                        }
                        baseState.copy(
                            activePanel = ActivePanel.REAPER_TEMPLE,
                            walkPath = emptyList(),
                            longRangeGoal = null,
                            longRangeWaypoints = emptyList()
                        ).addLogInternal(greeting)
                    }
                    else -> baseState.copy(activePanel = ActivePanel.SHOP, activeShopId = null, walkPath = emptyList(), longRangeGoal = null, longRangeWaypoints = emptyList())
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
        val biomeStall = tile.biomeCraftingStation()
        if (biomeStall != null) {
            return if (dist <= CraftingAndMiningEngine.BIOME_STALL_INTERACT_RADIUS) {
                baseState.copy(
                    activePanel = ActivePanel.BIOME_STALL,
                    activeBiomeStall = biomeStall,
                    walkPath = emptyList(),
                    longRangeGoal = null,
                    longRangeWaypoints = emptyList()
                ).addLogInternal("Stepped up to the ${biomeStall.displayName}.")
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

        // 7. Lootable Corpse: open it when adjacent, otherwise walk over and open it on arrival
        val corpse = LootCollection.corpseToOpenAt(baseState, tx, ty, nowMs)
        if (corpse != null) {
            if (dist <= 1) {
                return baseState.copy(
                    activePanel = ActivePanel.LOOT_CORPSE,
                    inspectingCorpseId = corpse.id,
                    walkPath = emptyList(),
                    pendingLootCorpseId = null
                )
            }
            val walking = setWalkPathToAdjacent(baseState.copy(longRangeGoal = null, longRangeWaypoints = emptyList()), tx, ty, remotePlayersOnFloor)
            if (walking.walkPath.isNotEmpty()) return walking.copy(pendingLootCorpseId = corpse.id)
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

        // 10. Walkable tile -> A* path (respecting Tibia-style non-PZ player blocking)
        if (tile.walkable) {
            val clean = baseState.copy(longRangeGoal = null, longRangeWaypoints = emptyList())
            val path = findPath(clean, tx, ty, remotePlayersOnFloor)
            if (path.isNotEmpty()) {
                val queued = clean.copy(walkPath = path)
                // Mid-step taps just re-route; the walk loop takes the next step on rhythm (no double steps)
                return if (isStepReady(queued, nowMs)) stepAlongPath(queued, nowMs, remotePlayersOnFloor) else queued
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
            state.copy(
                walkPath = emptyList(),
                longRangeGoal = null,
                longRangeWaypoints = emptyList(),
                autoFollowTarget = false
            ),
            state.playerX + dir.dx,
            state.playerY + dir.dy,
            dir,
            nowMs,
            remotePlayersOnFloor
        )
    }

    /**
     * Advances a manual tap-to-walk path, a hierarchical multi-region auto-walk chain,
     * or active melee Target Auto-Follow pursuit.
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
        if (state.longRangeWaypoints.isNotEmpty() || state.longRangeGoal != null) {
            return advanceLongRangeAutoWalk(state, nowMs, remotePlayersOnFloor)
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

    private fun advanceLongRangeAutoWalk(
        state: GameWorldState,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState>
    ): GameWorldState {
        val finalGoal = state.longRangeGoal ?: return state.copy(longRangeWaypoints = emptyList())
        if (abs(finalGoal.first - state.playerX) + abs(finalGoal.second - state.playerY) <= 2) {
            return state.copy(longRangeGoal = null, longRangeWaypoints = emptyList())
                .addLogInternal("Arrived at (${finalGoal.first}, ${finalGoal.second}).")
        }
        val floorMap = WorldMaps.getFloor(state.floor)
        val blockedNearby = blockedTilesNear(state, remotePlayersOnFloor)
        val replanned = RoutePlanner.plan(floorMap, state.playerX, state.playerY, finalGoal.first, finalGoal.second) { x, y ->
            (x to y) in blockedNearby
        }
        if (replanned.isNullOrEmpty()) {
            return state.copy(longRangeGoal = null, longRangeWaypoints = emptyList())
                .addLogInternal("Auto-walk stopped: the way is blocked.")
        }
        return stepAlongPath(state.copy(walkPath = replanned, longRangeWaypoints = emptyList()), nowMs, remotePlayersOnFloor)
    }

    /** NPCs anywhere on the floor plus monsters and players close to the hero (they move, so only nearby ones matter). */
    private fun blockedTilesNear(state: GameWorldState, remotePlayersOnFloor: List<RemotePlayerState>): Set<Pair<Int, Int>> {
        val near = 6
        return buildSet {
            WorldMaps.npcs.forEach { if (it.floor == state.floor) add(it.x to it.y) }
            state.monsters.forEach {
                if (it.floor == state.floor && abs(it.x - state.playerX) <= near && abs(it.y - state.playerY) <= near) add(it.x to it.y)
            }
            val floorMap = WorldMaps.getFloor(state.floor)
            remotePlayersOnFloor.forEach {
                if (it.floor == state.floor && abs(it.x - state.playerX) <= near && abs(it.y - state.playerY) <= near &&
                    isBlockedByRemotePlayer(floorMap, it.x, it.y, remotePlayersOnFloor)
                ) add(it.x to it.y)
            }
        }
    }

    fun stepAlongPath(
        state: GameWorldState,
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        val stepped = stepAlongPathInner(state, nowMs, remotePlayersOnFloor)
        return if (stepped.pendingLootCorpseId != null && stepped.walkPath.isEmpty()) LootCollection.onArrival(stepped, nowMs) else stepped
    }

    private fun stepAlongPathInner(
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
        if (moved.playerX == state.playerX && moved.playerY == state.playerY && moved.floor == state.floor) {
            val goal = state.longRangeGoal
            val isAdjacentStep = abs(nx - state.playerX) + abs(ny - state.playerY) == 1
            if (goal != null && isAdjacentStep && WorldMaps.getFloor(state.floor).tileAt(nx, ny).walkable) {
                // Something (a monster, player or NPC) is in the way: step around it and rejoin the route
                val rejoinIdx = minOf(DETOUR_REJOIN_STEPS, remaining.lastIndex)
                if (rejoinIdx >= 0) {
                    val rejoin = remaining[rejoinIdx]
                    val detour = findPath(state, rejoin.first, rejoin.second, remotePlayersOnFloor)
                    if (detour.isNotEmpty() && detour.last() == rejoin && detour.first() != (nx to ny)) {
                        return moved.copy(walkPath = detour + remaining.drop(rejoinIdx + 1))
                    }
                }
            }
            return moved.copy(walkPath = emptyList(), longRangeGoal = null, longRangeWaypoints = emptyList())
        }
        val finalGoal = moved.longRangeGoal
        if (remaining.isEmpty() && finalGoal != null &&
            abs(finalGoal.first - moved.playerX) + abs(finalGoal.second - moved.playerY) <= 2
        ) {
            return moved.copy(longRangeGoal = null, longRangeWaypoints = emptyList())
        }
        return moved
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
            val targetRegion = if (portal.toFloor == 0) {
                WorldMaps.regionForGlobal(portal.toX, portal.toY).regionId
            } else {
                state.regionId
            }
            return state.copy(
                floor = portal.toFloor,
                regionId = targetRegion,
                playerX = portal.toX,
                playerY = portal.toY,
                prevPlayerX = portal.toX,
                prevPlayerY = portal.toY,
                lastPlayerMoveMs = nowMs,
                playerFacing = dir,
                walkPath = emptyList(),
                longRangeGoal = null,
                longRangeWaypoints = emptyList(),
                targetMonsterId = null,
                autoFollowTarget = false,
                activePanel = ActivePanel.NONE,
                activeHarborPortId = null,
                activeBiomeStall = null,
                floorWarningBanner = banner,
                questFlags = updatedFlags
            ).addLogInternal(msg)
        }

        // Auto-close station panels if the player walks away from the physical station!
        var moved = state.copy(
            prevPlayerX = state.playerX,
            prevPlayerY = state.playerY,
            playerX = nx,
            playerY = ny,
            lastPlayerMoveMs = nowMs,
            playerFacing = dir
        )

        // Seamless 400x400 region transition detection on Floor 0
        if (moved.floor == 0) {
            val currentRegion = WorldMaps.regionForGlobal(nx, ny)
            if (currentRegion.regionId != moved.regionId) {
                floorMap.chunkedGrid.prewarmAround(nx, ny, radiusChunks = 2)
                moved = moved.copy(
                    regionId = currentRegion.regionId,
                    floorWarningBanner = FloorWarningBanner(
                        floorId = 0,
                        floorName = currentRegion.displayName,
                        dangerLevel = currentRegion.dangerLevel,
                        recommendedLevel = currentRegion.recommendedLevel,
                        severity = currentRegion.dangerSeverity,
                        createdAtMs = nowMs
                    )
                ).addLogInternal("Entered ${currentRegion.displayName} (${currentRegion.recommendedLevel} • ${currentRegion.dangerLevel} Danger).")
            }
        }

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
            ActivePanel.HARBOR_FERRY -> {
                val port = moved.activeHarborPortId?.let { WorldMaps.harborPortById(it) }
                moved.floor != 0 || port == null || chebyshev(moved.playerX, moved.playerY, port.dockX, port.dockY) > 3
            }
            ActivePanel.REAPER_TEMPLE -> {
                moved.floor != 0 || chebyshev(moved.playerX, moved.playerY, WorldMaps.REAPER_X, WorldMaps.REAPER_Y) > 3
            }
            ActivePanel.BIOME_STALL -> {
                val st = moved.activeBiomeStall
                st == null || !CraftingAndMiningEngine.isNearBiomeStation(moved, st, CraftingAndMiningEngine.BIOME_STALL_INTERACT_RADIUS)
            }
            else -> false
        }
        return if (shouldCloseStation) {
            moved.copy(
                activePanel = ActivePanel.NONE,
                inspectingCorpseId = null,
                activeHarborPortId = null,
                activeBiomeStall = null
            )
        } else moved
    }

    /**
     * Gold cost to buy back a single unit of [itemId] from Mortis at the Temple of Reapers.
     * Priced at ~15% of catalog value (fair gold sink while affordable even for endgame gear).
     */
    fun reaperReclaimPricePerUnit(itemId: String): Int {
        val def = ItemCatalog.get(itemId)
        val raw = (def.buyPrice * 0.15).toInt()
        return if (def.stackable) {
            raw.coerceIn(2, 250)
        } else {
            raw.coerceIn(10, 2_500)
        }
    }

    fun reaperReclaimFeeForStack(stack: ItemStack): Int {
        if (stack.count <= 0) return 0
        val perUnit = reaperReclaimPricePerUnit(stack.itemId)
        val rawTotal = perUnit.toLong() * stack.count.toLong()
        return rawTotal.coerceIn(10L, 50_000L).toInt()
    }

    fun reaperTotalReclaimFee(vault: List<ItemStack>): Int =
        vault.sumOf { reaperReclaimFeeForStack(it) }

    /**
     * Deducts [fee] from carried gold first and draws any remainder seamlessly from Rookvale Bank gold.
     * Returns null if combined carried + bank gold is insufficient.
     */
    private fun spendGoldWithBankFallback(carriedGold: Int, bankGold: Long, fee: Int): Pair<Int, Long>? {
        val safeCarried = carriedGold.coerceAtLeast(0)
        val safeBank = bankGold.coerceAtLeast(0L)
        val totalAvailable = safeCarried.toLong() + safeBank
        if (totalAvailable < fee.toLong()) return null
        val fromCarried = minOf(safeCarried, fee)
        val remainderFromBank = (fee - fromCarried).toLong()
        return (safeCarried - fromCarried) to (safeBank - remainderFromBank)
    }

    fun buyBackReaperItem(state: GameWorldState, itemId: String): GameWorldState {
        val stack = state.reaperVault.firstOrNull { it.itemId == itemId } ?: return state
        val def = ItemCatalog.get(stack.itemId)
        val fee = reaperReclaimFeeForStack(stack)
        val weight = def.weightOz * stack.count * if (def.isTool) TOOL_BAG_WEIGHT_MULTIPLIER else 1.0
        if (weight > state.remainingCapOz() + 0.001) {
            return state.addLogInternal("Not enough carrying Cap (${String.format("%.1f", weight)} oz needed) to reclaim ${def.name}.")
        }
        val payment = spendGoldWithBankFallback(state.gold, state.bankGold, fee)
            ?: return state.addLogInternal("Need ${formatGold(fee.toLong())} gp (purse + bank) to reclaim ${def.name}.")

        val (newGold, newBank) = payment
        val newVault = state.reaperVault.filterNot { it.itemId == itemId }
        val newBackpack = if (def.isTool) state.backpack else addItemPublic(state.backpack, stack.itemId, stack.count)
        val newToolBag = if (def.isTool) addItemPublic(state.toolBag, stack.itemId, stack.count) else state.toolBag
        val qtyLabel = if (stack.count > 1) "${stack.count}x ${def.name}" else def.name
        return state.copy(
            gold = newGold,
            bankGold = newBank,
            backpack = newBackpack,
            toolBag = newToolBag,
            reaperVault = newVault
        ).addLogInternal("Mortis restored $qtyLabel for ${formatGold(fee.toLong())} gp.")
    }

    fun buyBackAllReaperItems(state: GameWorldState): GameWorldState {
        if (state.reaperVault.isEmpty()) return state.addLogInternal("Mortis holds no lost items for you.")
        var cur = state
        var reclaimedStacks = 0
        var totalPaid = 0
        var skippedForCapOrGold = 0
        for (stack in state.reaperVault) {
            val def = ItemCatalog.get(stack.itemId)
            val fee = reaperReclaimFeeForStack(stack)
            val weight = def.weightOz * stack.count * if (def.isTool) TOOL_BAG_WEIGHT_MULTIPLIER else 1.0
            if (weight > cur.remainingCapOz() + 0.001) {
                skippedForCapOrGold++
                continue
            }
            val payment = spendGoldWithBankFallback(cur.gold, cur.bankGold, fee)
            if (payment == null) {
                skippedForCapOrGold++
                continue
            }
            val (newGold, newBank) = payment
            cur = cur.copy(
                gold = newGold,
                bankGold = newBank,
                backpack = if (def.isTool) cur.backpack else addItemPublic(cur.backpack, stack.itemId, stack.count),
                toolBag = if (def.isTool) addItemPublic(cur.toolBag, stack.itemId, stack.count) else cur.toolBag,
                reaperVault = cur.reaperVault.filterNot { it.itemId == stack.itemId }
            )
            reclaimedStacks++
            totalPaid += fee
        }
        return when {
            reclaimedStacks == 0 -> cur.addLogInternal("Insufficient gold (purse + bank) or Cap to reclaim your lost items.")
            skippedForCapOrGold > 0 -> cur.addLogInternal("Reclaimed $reclaimedStacks stack(s) for ${formatGold(totalPaid.toLong())} gp ($skippedForCapOrGold held for Cap/gold).")
            else -> cur.addLogInternal("Mortis restored all $reclaimedStacks stack(s) for ${formatGold(totalPaid.toLong())} gp.")
        }
    }

    fun discardReaperItem(state: GameWorldState, itemId: String): GameWorldState {
        val stack = state.reaperVault.firstOrNull { it.itemId == itemId } ?: return state
        val def = ItemCatalog.get(stack.itemId)
        val newVault = state.reaperVault.filterNot { it.itemId == itemId }
        return state.copy(reaperVault = newVault)
            .addLogInternal("Released ${stack.count}x ${def.name} to the Temple of Reapers.")
    }

    /**
     * Boards a coastal Harbor Ferry from any port to any other coastal port on the 1600x1200 world map.
     */
    fun boardHarborFerry(
        state: GameWorldState,
        destinationPortId: String,
        nowMs: Long
    ): GameWorldState {
        if (state.deathReport != null) return state
        val destPort = WorldMaps.harborPortById(destinationPortId) ?: return state
        val floorMap = WorldMaps.getFloor(0)
        val (ax, ay) = WorldMaps.findNearestWalkableTile(0, destPort.arrivalX, destPort.arrivalY, maxRadius = 6)
            ?: (destPort.arrivalX to destPort.arrivalY)
        floorMap.chunkedGrid.prewarmAround(ax, ay, radiusChunks = 2)
        val destRegion = WorldMaps.regionForGlobal(ax, ay)
        return state.copy(
            floor = 0,
            regionId = destRegion.regionId,
            playerX = ax,
            playerY = ay,
            prevPlayerX = ax,
            prevPlayerY = ay,
            lastPlayerMoveMs = nowMs,
            playerFacing = Direction.SOUTH,
            walkPath = emptyList(),
            longRangeGoal = null,
            longRangeWaypoints = emptyList(),
            targetMonsterId = null,
            autoFollowTarget = false,
            activePanel = ActivePanel.NONE,
            activeHarborPortId = null,
            floorWarningBanner = FloorWarningBanner(
                floorId = 0,
                floorName = destPort.name,
                dangerLevel = destRegion.dangerLevel,
                recommendedLevel = destRegion.recommendedLevel,
                severity = destRegion.dangerSeverity,
                createdAtMs = nowMs
            )
        ).addLogInternal("Sailed by Harbor Ferry to ${destPort.name} (${destRegion.displayName})!")
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
            hp = state.effectiveMaxHp(),
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
        if (state.deathReport != null) return pruneExpiredCorpses(pruneExpiredEffects(state, nowMs), nowMs)
        var s = pruneExpiredCorpses(pruneExpiredEffects(state, nowMs), nowMs).copy(
            tickCount = state.tickCount + 1,
            lastCombatTickMs = nowMs
        )

        val floorMap = WorldMaps.getFloor(s.floor)
        val inPz = floorMap.isProtectionZone(s.playerX, s.playerY)

        // 1. Temple Protection Zone passive healing & status cure
        if (inPz) {
            val healedHp = (s.hp + 15).coerceAtMost(s.effectiveMaxHp())
            val healedMp = (s.mp + 15).coerceAtMost(s.maxMp)
            val cured = s.poisonTicks > 0 || s.fireTicks > 0
            s = s.copy(hp = healedHp, mp = healedMp, poisonTicks = 0, fireTicks = 0)
            if (cured) {
                s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "CURED!", FloatingTextKind.HEAL, nowMs)
                    .addLogInternal("The Temple sanctuary cleansed your afflictions.")
            }
        } else if (s.tickCount % 3L == 0L && s.poisonTicks == 0 && s.fireTicks == 0) {
            s = s.copy(
                hp = (s.hp + 2).coerceAtMost(s.effectiveMaxHp()),
                mp = (s.mp + 3).coerceAtMost(s.maxMp),
                soul = (s.soul + 1).coerceAtMost(s.maxSoul)
            )
        }

        // 2. Player attack check (respects variable weapon interval)
        s = processPlayerAttackSubTick(s, nowMs)

        // 3. Poison & Fire status ticks (Admin / Infinite Health is immune to elemental afflictions)
        if (s.isInvulnerable) {
            if (s.hp != s.effectiveMaxHp() || s.poisonTicks > 0 || s.fireTicks > 0) {
                s = s.copy(hp = s.effectiveMaxHp(), poisonTicks = 0, fireTicks = 0)
            }
        } else {
            if (s.poisonTicks > 0) {
                val basePoison = Random.nextInt(4, 9)
                val resistPct = s.equipment.totalPoisonResistPercent()
                val pDmg = (basePoison * (100 - resistPct) / 100.0).roundToInt().coerceAtLeast(1)
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
            s = processRespawns(s, nowMs, remotePlayersOnFloor)
        }
        s = CraftingAndMiningEngine.regenerateOreNodesTick(s)
        s = CraftingAndMiningEngine.regenerateTreesTick(s)

        // 6. Death penalty check (Admin / Infinite Health stays permanently locked at effectiveMaxHp)
        if (s.isInvulnerable) {
            s = s.copy(hp = s.effectiveMaxHp(), poisonTicks = 0, fireTicks = 0)
        } else if (s.hp <= 0) {
            s = handlePlayerDeath(s, nowMs)
        }
        return s
    }

    /**
     * Applies monster armor mitigation so weapon upgrades and skill training noticeably matter
     * against heavy-armored dungeon foes (Orc Warriors, Grimmaw Crushers, Gorek Phalanxes, Bosses).
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

        // Soft skill-requirement scaling (-35% dmg for grandfathered equipped gear) & Tier 3-5 off-vocation penalty (-25% dmg)
        val reqType = weapon?.requiredSkillType
        val skillPenaltyMult = if (reqType != null && s.skills.levelFor(reqType) < weapon.requiredSkillLevel) 0.65 else 1.0
        val offVocationMult = s.weaponOffVocationMultiplier(weapon)
        val accessoryAtk = s.equipment.totalAccessoryAttackBonus()
        val effectiveTargetArmor = if (target.shatterTicks > 0) (target.scaledArmor * 0.55).roundToInt() else target.scaledArmor

        return when (weapon?.category) {
            ItemCategory.WEAPON_WAND -> {
                if (s.mp >= weapon.manaCost) {
                    val magicMult = s.magicSkillMultiplier()
                    val basePower = weapon.attack + accessoryAtk + s.level * 0.65
                    val rawDmg = (basePower * magicMult * Random.nextDouble(0.85, 1.18) * skillPenaltyMult * offVocationMult)
                        .roundToInt().coerceAtLeast(6)
                    val dmg = mitigateMonsterDamage(rawDmg, effectiveTargetArmor, isMagic = true)
                    s = s.copy(mp = s.mp - weapon.manaCost)
                    s = awardMagicXpFromMana(s, weapon.manaCost, nowMs)
                    s = s.addProjectileInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), target.x.toFloat(), target.y.toFloat(), ProjectileKind.ENERGY_BOLT, nowMs)
                    s = s.queuePlayerFx(null, null, PlayerActionState.CASTING)
                    s = applyDamageToMonster(s, target.id, dmg, nowMs, projectileKind = ProjectileKind.ENERGY_BOLT)
                    EquipmentProcEngine.resolveAttackProcs(s, target, dmg, nowMs)
                } else if (dist <= 1) {
                    val meleeMult = s.attackSkillMultiplier(s.skills.fist.level)
                    val rawDmg = (Random.nextInt(4, 8) * meleeMult).roundToInt().coerceAtLeast(3)
                    val dmg = mitigateMonsterDamage(rawDmg, effectiveTargetArmor, isMagic = false)
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
                    val basePower = weapon.attack + ammoAtkBonus + accessoryAtk + s.level * 0.60
                    val rawDmg = (basePower * distMult * Random.nextDouble(0.80, 1.20) * skillPenaltyMult * offVocationMult)
                        .roundToInt().coerceAtLeast(5)
                    val dmg = mitigateMonsterDamage(rawDmg, effectiveTargetArmor, isMagic = false)
                    s = s.queuePlayerFx(null, null, PlayerActionState.ATTACKING)
                    s = applyDamageToMonster(s, target.id, dmg, nowMs, projectileKind = projKind)
                    EquipmentProcEngine.resolveAttackProcs(s, target, dmg, nowMs)
                } else {
                    s.addFloatingInternal(s.floor, target.x.toFloat(), target.y.toFloat(), "MISS", FloatingTextKind.BLOCK, nowMs)
                        .queuePlayerFx("MISS", FloatingTextKind.BLOCK, PlayerActionState.ATTACKING, projKind, target.x, target.y)
                }
            }
            else -> {
                val baseAtk = (weapon?.attack ?: 6) + accessoryAtk
                val meleeSkillType = PlayerSkills.meleeSkillTypeForWeapon(weapon?.id)
                s = advanceSkillPublic(s, meleeSkillType, 3.2, nowMs)
                val activeLevel = s.skills.levelFor(meleeSkillType)
                val meleeMult = s.attackSkillMultiplier(activeLevel)
                val basePower = baseAtk + s.level * 0.60
                val rawDmg = (basePower * meleeMult * Random.nextDouble(0.80, 1.20) * skillPenaltyMult * offVocationMult)
                    .roundToInt().coerceAtLeast(4)
                val dmg = mitigateMonsterDamage(rawDmg, effectiveTargetArmor, isMagic = false)
                s = s.queuePlayerFx(null, null, PlayerActionState.ATTACKING)
                s = applyDamageToMonster(s, target.id, dmg, nowMs)
                EquipmentProcEngine.resolveAttackProcs(s, target, dmg, nowMs)
            }
        }
    }

    internal fun applyDamageToMonster(
        state: GameWorldState,
        monsterId: Int,
        damage: Int,
        nowMs: Long,
        customLog: String? = null,
        projectileKind: ProjectileKind? = null
    ): GameWorldState {
        val m = state.monsters.firstOrNull { it.id == monsterId } ?: return state
        val damage = NpcInteractions.applyBestiaryBonus(state, m.type, damage)
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

        // Monster slain: spawn loot corpse protected for the killer for 5s, then public for anyone nearby
        val corpse = generateMonsterCorpse(
            m = m,
            nowMs = nowMs,
            ownerPlayerId = s.saveId,
            ownerName = s.playerName
        )
        val remainingMonsters = s.monsters.filterNot { it.id == monsterId }
        val respawnTicks = if (m.type.isBoss) 300 else 32
        val respawnAtMs = if (m.type.isBoss) nowMs + BOSS_RESPAWN_MS else 0L
        val updatedRespawns = if (!m.isSummon) {
            s.pendingRespawns + PendingRespawn(
                type = m.type,
                floor = m.floor,
                spawnX = m.spawnX,
                spawnY = m.spawnY,
                ticksRemaining = respawnTicks,
                originalMonsterId = m.id,
                respawnAtMs = respawnAtMs,
                level = m.level
            )
        } else {
            s.pendingRespawns
        }
        val bossFlags = if (m.type.isBoss) {
            s.questFlags + ("slain_${m.type.name.lowercase()}" to true)
        } else {
            s.questFlags
        }
        val bossAlert = if (m.type.isBoss) {
            listOf("[BOSS] ${s.playerName} defeated ${m.type.displayName} on Floor ${m.floor}! Respawns in 5m.")
        } else {
            emptyList()
        }
        s = s.copy(
            monsters = remainingMonsters,
            targetMonsterId = if (s.targetMonsterId == monsterId) null else s.targetMonsterId,
            autoFollowTarget = if (s.targetMonsterId == monsterId) false else s.autoFollowTarget,
            corpses = (s.corpses + corpse).takeLast(38),
            outboundLootSpawns = s.outboundLootSpawns + corpse,
            pendingRespawns = updatedRespawns,
            questFlags = bossFlags,
            outboundSystemAlerts = s.outboundSystemAlerts + bossAlert
        )
        s = NpcInteractions.onKillCredited(s, m, nowMs)
        s = LootCollection.autoLootOnKill(s, corpse.id, nowMs)
        val expiryHint = if (m.type.isBoss) "3m" else "2m"
        val potionQty = corpse.items.filter { it.itemId == "health_potion" }.sumOf { it.count }
        val isGuaranteedPotion = MonsterLootTable.entryFor(m.type).guaranteedDrops.any { it.itemId == "health_potion" }
        val rarePotionNote = if (potionQty > 0 && !isGuaranteedPotion) {
            s = s.addFloatingInternal(m.floor, m.x.toFloat(), m.y.toFloat(), "RARE POTION!", FloatingTextKind.HEAL, nowMs)
            " Rare drop: ${potionQty}x Health Potion!"
        } else {
            ""
        }
        val lootSummary = if (corpse.items.isEmpty() && corpse.gold > 0) {
            "Dropped ${corpse.gold} gp ($expiryHint)."
        } else {
            "Tap corpse to loot ($expiryHint).$rarePotionNote"
        }
        return s.addLogInternal("Slain ${m.type.displayName} [Lvl ${m.level}]! (+${m.scaledXpReward} XP) $lootSummary")
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
        val respawnTicks = if (m.type.isBoss) 300 else 32
        val respawnAtMs = if (m.type.isBoss) nowMs + BOSS_RESPAWN_MS else 0L
        val newRespawns = if (isFloorHost && !m.isSummon) {
            s.pendingRespawns + PendingRespawn(
                type = m.type,
                floor = m.floor,
                spawnX = m.spawnX,
                spawnY = m.spawnY,
                ticksRemaining = respawnTicks,
                originalMonsterId = m.id,
                respawnAtMs = respawnAtMs,
                level = m.level
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
            val personalCorpse = generateMonsterCorpse(
                m = m,
                nowMs = nowMs,
                ownerPlayerId = localPlayerId,
                ownerName = s.playerName
            )
            s = s.copy(
                corpses = (s.corpses + personalCorpse).takeLast(38),
                outboundLootSpawns = s.outboundLootSpawns + personalCorpse
            )
            s = NpcInteractions.onKillCredited(s, m, nowMs)
            s = s.addLogInternal("Co-op kill: ${m.type.displayName}! (+${m.scaledXpReward} XP) Personal loot dropped.")
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
                val personalCorpse = generateMonsterCorpse(
                    m = oldM,
                    nowMs = nowMs,
                    ownerPlayerId = localPlayerId,
                    ownerName = s.playerName
                )
                s = s.copy(
                    corpses = (s.corpses + personalCorpse).takeLast(38),
                    outboundLootSpawns = s.outboundLootSpawns + personalCorpse
                )
                s = NpcInteractions.onKillCredited(s, oldM, nowMs)
                s = s.addLogInternal("Co-op kill: ${oldM.type.displayName}! (+${oldM.scaledXpReward} XP) Personal loot dropped.")
            }
        }

        val syncedOnFloor = snapshots.mapNotNull { snap ->
            if (snap.hp <= 0) return@mapNotNull null
            val existing = existingOnFloor[snap.id]
            val type = existing?.type ?: MonsterType.fromWireName(snap.typeName) ?: return@mapNotNull null
            val moved = existing != null && (existing.x != snap.x || existing.y != snap.y)
            val resolvedLevel = existing?.level ?: if (floor == 0) {
                BiomeProgressionRules.resolveOverlandSpawnLevel(snap.spawnX, snap.spawnY, type)
            } else {
                BiomeProgressionRules.resolveDungeonSpawnLevel(floor, snap.spawnX, snap.spawnY, type)
            }
            val scaledTemplate = if (existing == null) {
                BiomeProgressionRules.createScaledMonster(snap.id, type, floor, snap.x, snap.y, resolvedLevel)
            } else null
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
                level = resolvedLevel,
                scaledArmor = existing?.scaledArmor ?: scaledTemplate!!.scaledArmor,
                scaledMinDmg = existing?.scaledMinDmg ?: scaledTemplate!!.scaledMinDmg,
                scaledMaxDmg = existing?.scaledMaxDmg ?: scaledTemplate!!.scaledMaxDmg,
                scaledXpReward = existing?.scaledXpReward ?: scaledTemplate!!.scaledXpReward,
                scaledGoldMin = existing?.scaledGoldMin ?: scaledTemplate!!.scaledGoldMin,
                scaledGoldMax = existing?.scaledGoldMax ?: scaledTemplate!!.scaledGoldMax,
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
        var s = state.copy(
            monsters = state.monsters.map { m ->
                if (m.id == event.monsterId) m.copy(lastAttackTimeMs = nowMs) else m
            }
        )
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
            if (!inPz && !s.isSpectator && s.hp > 0 && (s.playerX to s.playerY) in event.aoeTiles) {
                if (s.isInvulnerable) {
                    s = s.copy(hp = s.effectiveMaxHp(), poisonTicks = 0, fireTicks = 0)
                        .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "IMMUNE", FloatingTextKind.BLOCK, nowMs)
                } else {
                    val caster = s.monsters.firstOrNull { it.id == event.monsterId }
                    val rawDmg = if (caster != null) {
                        Random.nextInt(caster.scaledMinDmg, caster.scaledMaxDmg + 12)
                    } else {
                        Random.nextInt(38, 66)
                    }
                    val reduced = (rawDmg - (s.equipment.totalArmor() * 0.5).roundToInt()).coerceAtLeast(14)
                    val casterName = caster?.type?.displayName ?: "Boss"
                    val special = caster?.type?.specialKind ?: BossSpecialKind.FLAME_BURST
                    val isVenom = event.inflictedStatus == "POISON" || special == BossSpecialKind.VENOM_NOVA
                    val isQuake = special == BossSpecialKind.QUARRY_QUAKE
                    s = when {
                        isVenom -> s.copy(
                            hp = (s.hp - reduced).coerceAtLeast(0),
                            poisonTicks = max(s.poisonTicks, 6)
                        )
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.POISON, nowMs)
                            .addLogInternal("$casterName's Venom Nova seared you for $reduced!")
                        isQuake -> s.copy(
                            hp = (s.hp - reduced).coerceAtLeast(0)
                        )
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.DAMAGE_PLAYER, nowMs)
                            .addLogInternal("$casterName's Quarry Quake shattered the ground for $reduced!")
                        special == BossSpecialKind.LABYRINTH_PYRE -> s.copy(
                            hp = (s.hp - reduced).coerceAtLeast(0),
                            fireTicks = max(s.fireTicks, 6)
                        )
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.FIRE, nowMs)
                            .addLogInternal("$casterName's Labyrinth Pyre engulfed you for $reduced!")
                        else -> s.copy(
                            hp = (s.hp - reduced).coerceAtLeast(0),
                            fireTicks = max(s.fireTicks, 5)
                        )
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.FIRE, nowMs)
                            .addLogInternal("$casterName's Flame Burst scorched you for $reduced!")
                    }
                    if (s.hp <= 0) {
                        s = handlePlayerDeath(s, nowMs)
                    }
                }
            }
        } else if (event.inflictedStatus != null && event.targetPlayerId == localPlayerId && s.hp > 0 && !s.isSpectator && !s.isInvulnerable) {
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
     * Generates a slain monster corpse using the data-driven [MonsterLootTable] registry and
     * stamps its auto-despawn expiration timer (`90s` regular monsters, `180s` Dungeon Bosses)
     * plus its 5-second owner-only loot protection window (`LOOT_OWNER_PROTECTION_MS`).
     */
    fun generateMonsterCorpse(
        m: MonsterEntity,
        nowMs: Long = 0L,
        ownerPlayerId: String? = null,
        ownerName: String? = null
    ): CorpseEntity {
        val rolled = MonsterLootTable.rollLoot(m.type, monsterLevel = m.level)
        val scaledGold = if (m.scaledGoldMax > m.type.goldMax) {
            Random.nextInt(m.scaledGoldMin, m.scaledGoldMax + 1)
        } else {
            rolled.gold
        }
        val lifetimeMs = if (m.type.isBoss) BOSS_CORPSE_LIFETIME_MS else REGULAR_CORPSE_LIFETIME_MS
        val expiresAt = if (nowMs > 0L) nowMs + lifetimeMs else 0L
        val publicAt = if (nowMs > 0L && !ownerPlayerId.isNullOrBlank()) nowMs + LOOT_OWNER_PROTECTION_MS else 0L
        return CorpseEntity(
            id = "m_${UUID.randomUUID()}",
            floor = m.floor,
            x = m.x,
            y = m.y,
            label = "Slain ${m.type.displayName}",
            isPlayerCorpse = false,
            monsterTypeName = m.type.name,
            gold = scaledGold,
            items = rolled.items,
            createdAtMs = nowMs,
            expiresAtMs = expiresAt,
            ownerPlayerId = ownerPlayerId?.takeIf { it.isNotBlank() },
            ownerName = ownerName?.takeIf { it.isNotBlank() },
            publicAtMs = publicAt,
            isDroppedBag = false
        )
    }

    /**
     * Automatically removes expired unlooted monster corpses (`expiresAtMs <= nowMs`), while keeping
     * player death recovery corpses persistent (`isPlayerCorpse = true`). Safely closes the Loot
     * Corpse modal if the inspected corpse vanished before looting finished.
     */
    fun pruneExpiredCorpses(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.corpses.isEmpty() || nowMs <= 0L) return state
        val expiredIds = state.corpses.filter { it.isExpired(nowMs) }.map { it.id }.toSet()
        if (expiredIds.isEmpty()) return state

        val remaining = state.corpses.filterNot { it.id in expiredIds }
        val inspectingExpired = state.inspectingCorpseId != null && state.inspectingCorpseId in expiredIds
        var next = state.copy(
            corpses = remaining,
            inspectingCorpseId = if (inspectingExpired) null else state.inspectingCorpseId,
            activePanel = if (inspectingExpired && state.activePanel == ActivePanel.LOOT_CORPSE) {
                ActivePanel.NONE
            } else {
                state.activePanel
            }
        )
        if (inspectingExpired) {
            next = next.addLogInternal("The corpse crumbled to dust before you could finish looting.")
        }
        return next
    }

    fun useHealthPotion(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        if (!state.hasItem("health_potion")) return state.addLogInternal("Out of Health Potions! Buy more from Alaric.")
        val def = ItemCatalog.get("health_potion")
        val heal = def.restoreAmount + Random.nextInt(-10, 16)
        val newHp = (state.hp + heal).coerceAtMost(state.effectiveMaxHp())
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
            val newHp = (next.hp + healPower).coerceAtMost(next.effectiveMaxHp())
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
        val effectiveArmor = if (runeId == "hmm_rune") (target.scaledArmor * 0.7).roundToInt() else target.scaledArmor
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
                val splashDmg = mitigateMonsterDamage(splashRaw, splashTarget.scaledArmor, isMagic = true)
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

    fun selectFishingBait(state: GameWorldState, baitId: String, nowMs: Long = 0L): GameWorldState {
        val tier = FishingCatalog.tierForBait(baitId) ?: return state
        val fishingLevel = state.skills.fishing.level
        if (state.selectedBaitId == baitId) {
            val barePct = (FishingCatalog.bareHookCatchChance(fishingLevel) * 100.0).toInt()
            return state.copy(selectedBaitId = null)
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "BARE HOOK", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Cleared Active Bait — now fishing with a bare hook ($barePct% small chance for Fresh Fish).")
        }
        if (!state.hasItem(baitId)) {
            val name = ItemCatalog.get(baitId).name
            return state.addLogInternal("You don't have any $name in your backpack.")
        }
        val baitDef = ItemCatalog.get(baitId)
        val fishDef = ItemCatalog.get(tier.fishId)
        val count = state.itemCount(baitId)
        val consumeRange = FishingCatalog.baitConsumeRangePercent(fishingLevel, tier)
        val next = state.copy(selectedBaitId = baitId)
        return if (!state.isAdmin && fishingLevel < tier.requiredFishingLevel) {
            next
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "BAIT: ${baitDef.name}", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Selected ${baitDef.name} (x$count) as Active Bait — catches ${fishDef.name}, but requires Fishing Lv ${tier.requiredFishingLevel} (you: Lv $fishingLevel).")
        } else {
            next
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "BAIT: ${baitDef.name}", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Baited hook with ${baitDef.name} (x$count) — targets ${fishDef.name} (+${fishDef.restoreAmount} HP, ${consumeRange.first}%–${consumeRange.last}% worm use on catch).")
        }
    }

    fun attemptFishing(
        state: GameWorldState,
        waterX: Int,
        waterY: Int,
        nowMs: Long,
        catchRollOverride: Double? = null,
        baitChancePercentOverride: Int? = null,
        baitConsumeRollOverride: Double? = null
    ): GameWorldState {
        if (!state.hasItem("fishing_rod")) {
            return state.addLogInternal("You need a Fishing Rod in your backpack or Tool Bag to fish.")
        }

        val currentFishingLevel = state.skills.fishing.level
        val activeBaitId = state.selectedBaitId
        val tier = FishingCatalog.tierForBait(activeBaitId)
        val currentBaitCount = if (tier != null) state.itemCount(tier.baitId) else 0

        // Bare-hook fallback: when no bait is selected or active worm stack is empty, allow casting with a small chance for Fresh Fish
        if (tier == null || activeBaitId == null || currentBaitCount <= 0) {
            var next = state.copy(
                playerFacing = directionToward(state.playerX, state.playerY, waterX, waterY)
            ).addProjectileInternal(
                state.floor,
                state.playerX.toFloat(),
                state.playerY.toFloat(),
                waterX.toFloat(),
                waterY.toFloat(),
                ProjectileKind.SPLASH,
                nowMs,
                340L
            )
            val bareFishDef = ItemCatalog.get(FishingCatalog.BARE_HOOK_FISH_ID)
            val bareCatchChance = FishingCatalog.bareHookCatchChance(currentFishingLevel)
            val barePct = (bareCatchChance * 100.0).toInt()
            val roll = catchRollOverride ?: Random.nextDouble()

            if (roll <= bareCatchChance) {
                if (next.remainingCapOz() < bareFishDef.weightOz) {
                    return next
                        .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "CAP FULL", FloatingTextKind.INFO, nowMs)
                        .addLogInternal("A ${bareFishDef.name} bit your bare hook, but you lack Cap (${bareFishDef.weightOz} oz needed)!")
                }
                next = advanceSkillPublic(next, SkillType.FISHING, FishingCatalog.BARE_HOOK_CATCH_XP, nowMs)
                val updatedBackpack = addItemPublic(next.backpack, bareFishDef.id, 1)
                next = next.copy(backpack = updatedBackpack)
                    .addFloatingInternal(state.floor, waterX.toFloat(), waterY.toFloat(), "+1 ${bareFishDef.name}!", FloatingTextKind.HEAL, nowMs)
                    .addLogInternal("Bare-hook catch! Landed a ${bareFishDef.name} without worms ($barePct% chance • Fishing ${next.skills.fishing.level})")
            } else {
                val baitHint = if (state.carriedFishingBaitStacks().isNotEmpty()) {
                    "Pick a worm in your Backpack for higher odds & rare fish."
                } else {
                    "Buy worms at Alaric's Shop or dig ore veins & chop trees for higher odds!"
                }
                next = advanceSkillPublic(next, SkillType.FISHING, FishingCatalog.BARE_HOOK_MISS_XP, nowMs)
                    .addFloatingInternal(state.floor, waterX.toFloat(), waterY.toFloat(), "Splash...", FloatingTextKind.INFO, nowMs)
                    .addLogInternal("Cast a bare hook ($barePct% chance)... no bite yet. $baitHint")
            }
            return next
        }

        val baitDef = ItemCatalog.get(tier.baitId)
        val fishDef = ItemCatalog.get(tier.fishId)

        if (!state.isAdmin && currentFishingLevel < tier.requiredFishingLevel) {
            return state
                .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "FISHING LV ${tier.requiredFishingLevel}", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Need Fishing Lv ${tier.requiredFishingLevel} to fish with ${baitDef.name} for ${fishDef.name} (you: Lv $currentFishingLevel). Select a lower worm or Clear bait for bare hook.")
        }

        var next = state.copy(
            playerFacing = directionToward(state.playerX, state.playerY, waterX, waterY)
        ).addProjectileInternal(
            state.floor,
            state.playerX.toFloat(),
            state.playerY.toFloat(),
            waterX.toFloat(),
            waterY.toFloat(),
            ProjectileKind.SPLASH,
            nowMs,
            340L
        )

        val levelAdvantage = (currentFishingLevel - tier.requiredFishingLevel).coerceAtLeast(0)
        val catchChance = (tier.baseCatchChance + levelAdvantage * 0.025).coerceIn(0.28, 0.88)
        val roll = catchRollOverride ?: Random.nextDouble()

        if (roll <= catchChance) {
            val consumeRange = FishingCatalog.baitConsumeRangePercent(currentFishingLevel, tier)
            val rolledConsumePct = (
                baitChancePercentOverride ?: Random.nextInt(consumeRange.first, consumeRange.last + 1)
                ).coerceIn(consumeRange.first, consumeRange.last)
            val consumeRoll = baitConsumeRollOverride ?: Random.nextDouble()
            val consumedWorm = consumeRoll < (rolledConsumePct / 100.0)

            // Validate exact carrying capacity based on whether the worm is consumed or saved
            val netExtraWeight = if (consumedWorm) {
                (fishDef.weightOz - baitDef.weightOz).coerceAtLeast(0.0)
            } else {
                fishDef.weightOz
            }
            if (next.remainingCapOz() < netExtraWeight) {
                return next
                    .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "CAP FULL", FloatingTextKind.INFO, nowMs)
                    .addLogInternal("A ${fishDef.name} bit, but you lack Cap (${fishDef.weightOz} oz needed)! No worm lost.")
            }

            next = advanceSkillPublic(next, SkillType.FISHING, tier.catchXpPoints, nowMs)
            val afterBaitStep = if (consumedWorm) removeItemPublic(next.backpack, tier.baitId, 1) else next.backpack
            val updatedBackpack = addItemPublic(afterBaitStep, tier.fishId, 1)
            val remainingWorms = updatedBackpack.filter { it.itemId == tier.baitId }.sumOf { it.count }
            next = next.copy(backpack = updatedBackpack)
                .addFloatingInternal(state.floor, waterX.toFloat(), waterY.toFloat(), "+1 ${fishDef.name}!", FloatingTextKind.HEAL, nowMs)
            next = if (consumedWorm) {
                next.addLogInternal(
                    "Caught a ${fishDef.name}! (-1 ${baitDef.name} on $rolledConsumePct% roll, $remainingWorms left • Fishing ${next.skills.fishing.level})"
                )
            } else {
                next
                    .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "BAIT SAVED!", FloatingTextKind.INFO, nowMs)
                    .addLogInternal(
                        "Caught a ${fishDef.name}! Worm saved ($rolledConsumePct% use roll • $remainingWorms ${baitDef.name} left • Fishing ${next.skills.fishing.level})"
                    )
            }
        } else {
            // Miss: ZERO worms consumed; still award modest Fishing skill practice XP
            next = advanceSkillPublic(next, SkillType.FISHING, tier.missXpPoints, nowMs)
                .addFloatingInternal(state.floor, waterX.toFloat(), waterY.toFloat(), "Splash...", FloatingTextKind.INFO, nowMs)
                .addLogInternal("Cast with ${baitDef.name}... no bite yet (0 worms used).")
        }
        return next
    }

    /**
     * Distance threshold (Chebyshev tiles) beyond which monsters on the active floor enter AI sleep.
     * Sleeping monsters preserve their HP, position, and co-op aggro targets without spending CPU on
     * targeting, combat, or pathfinding until the local player or an active co-op ally comes within range.
     */
    const val MONSTER_AI_SLEEP_DISTANCE: Int = 22

    /**
     * Returns true when [monster] is on [activeFloor] and within [sleepDistance] Chebyshev tiles of
     * either the local player ([localX], [localY]) or any living non-spectator remote co-op ally
     * on the same floor.
     */
    fun isMonsterAwakeForAi(
        monster: MonsterEntity,
        activeFloor: Int,
        localX: Int,
        localY: Int,
        remoteTargetsOnFloor: List<RemotePlayerState>,
        sleepDistance: Int = MONSTER_AI_SLEEP_DISTANCE
    ): Boolean {
        if (monster.floor != activeFloor) return false
        if (chebyshev(monster.x, monster.y, localX, localY) <= sleepDistance) return true
        for (i in 0 until remoteTargetsOnFloor.size) {
            val rp = remoteTargetsOnFloor[i]
            if (!rp.isSpectator && rp.hp > 0 && rp.floor == activeFloor &&
                chebyshev(monster.x, monster.y, rp.x, rp.y) <= sleepDistance
            ) {
                return true
            }
        }
        return false
    }

    private fun packAiTile(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

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
        val defensiveProcAttackers = mutableListOf<MonsterEntity>()
        val awakeRemotePlayers = remotePlayersOnFloor.filter {
            it.floor == s.floor && it.hp > 0 && !it.isSpectator
        }
        val activeRemoteTargets = awakeRemotePlayers.filter {
            !floorMap.isProtectionZone(it.x, it.y)
        }
        var monstersMutated = false
        var occupiedTiles: HashSet<Long>? = null

        for (i in updatedMonsters.indices) {
            var curM = updatedMonsters[i]
            if (curM.floor != s.floor) continue
            // Distant Monster AI Sleep Culling: skip pathfinding & heavy AI beyond 22 tiles from
            // the local player and all active co-op allies on this floor (keeps HP and co-op aggro intact).
            if (!isMonsterAwakeForAi(curM, s.floor, s.playerX, s.playerY, awakeRemotePlayers)) continue
            if (curM.shatterTicks > 0 || curM.slowTicks > 0) {
                curM = curM.copy(
                    shatterTicks = (curM.shatterTicks - 1).coerceAtLeast(0),
                    slowTicks = (curM.slowTicks - 1).coerceAtLeast(0)
                )
                updatedMonsters[i] = curM
                monstersMutated = true
            }

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

                if (!playerInPz && !s.isSpectator && s.hp > 0) {
                    val dLocal = chebyshev(curM.x, curM.y, s.playerX, s.playerY)
                    if (dLocal <= 8) {
                        bestId = localPlayerId
                        bestX = s.playerX
                        bestY = s.playerY
                        bestDist = dLocal
                    }
                }
                for (rp in activeRemoteTargets) {
                    if (rp.isSpectator) continue
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
                targetId = if (s.isSpectator && curM.targetPlayerId == localPlayerId) null else curM.targetPlayerId
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

            // 2. Boss Telegraphed Ground Danger Zone Specials (Venom Nova / Flame Burst / Quarry Quake / Labyrinth Pyre)
            if (isFloorHost && curM.type.aiStyle == MonsterAiStyle.BOSS_AOE_SUMMON) {
                val special = curM.type.specialKind
                val isVenomBoss = special == BossSpecialKind.VENOM_NOVA
                val isQuakeBoss = special == BossSpecialKind.QUARRY_QUAKE
                val isPyreBoss = special == BossSpecialKind.LABYRINTH_PYRE
                if (curM.pendingAoeTiles.isNotEmpty()) {
                    val waveTiles = curM.pendingAoeTiles
                    val waveProj = when {
                        isVenomBoss -> ProjectileKind.VENOM_GLOB
                        isQuakeBoss -> ProjectileKind.THROWN_STONE
                        isPyreBoss -> ProjectileKind.FIREBALL
                        else -> curM.type.rangedProjectile ?: ProjectileKind.FIRE_BREATH
                    }
                    val inflictedStatus = when {
                        isVenomBoss -> "POISON"
                        isQuakeBoss -> null
                        else -> "FIRE"
                    }
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
                            inflictedStatus = inflictedStatus
                        )
                    )
                    for ((ax, ay) in waveTiles.take(12)) {
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
                    if (!playerInPz && !s.isSpectator && s.hp > 0 && (s.playerX to s.playerY) in waveTiles) {
                        if (s.isInvulnerable) {
                            s = s.copy(hp = s.effectiveMaxHp(), poisonTicks = 0, fireTicks = 0)
                                .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "IMMUNE", FloatingTextKind.BLOCK, nowMs)
                        } else {
                            val rawWave = Random.nextInt(curM.scaledMinDmg, curM.scaledMaxDmg + 12)
                            val waveDmg = (rawWave - (s.equipment.totalArmor() * 0.5).roundToInt()).coerceAtLeast(14)
                            s = when {
                                isVenomBoss -> s.copy(
                                    hp = (s.hp - waveDmg).coerceAtLeast(0),
                                    poisonTicks = max(s.poisonTicks, 6)
                                )
                                    .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$waveDmg", FloatingTextKind.POISON, nowMs)
                                    .queuePlayerFx("-$waveDmg", FloatingTextKind.POISON, PlayerActionState.IDLE)
                                    .addLogInternal("${curM.type.displayName}'s Venom Nova seared you for $waveDmg!")
                                isQuakeBoss -> s.copy(
                                    hp = (s.hp - waveDmg).coerceAtLeast(0)
                                )
                                    .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$waveDmg", FloatingTextKind.DAMAGE_PLAYER, nowMs)
                                    .queuePlayerFx("-$waveDmg", FloatingTextKind.DAMAGE_PLAYER, PlayerActionState.IDLE)
                                    .addLogInternal("${curM.type.displayName}'s Quarry Quake shattered you for $waveDmg!")
                                isPyreBoss -> s.copy(
                                    hp = (s.hp - waveDmg).coerceAtLeast(0),
                                    fireTicks = max(s.fireTicks, 6)
                                )
                                    .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$waveDmg", FloatingTextKind.FIRE, nowMs)
                                    .queuePlayerFx("-$waveDmg", FloatingTextKind.FIRE, PlayerActionState.IDLE)
                                    .addLogInternal("${curM.type.displayName}'s Labyrinth Pyre engulfed you for $waveDmg!")
                                else -> s.copy(
                                    hp = (s.hp - waveDmg).coerceAtLeast(0),
                                    fireTicks = max(s.fireTicks, 5)
                                )
                                    .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$waveDmg", FloatingTextKind.FIRE, nowMs)
                                    .queuePlayerFx("-$waveDmg", FloatingTextKind.FIRE, PlayerActionState.IDLE)
                                    .addLogInternal("${curM.type.displayName}'s Flame Burst scorched you for $waveDmg!")
                            }
                        }
                    }
                    updatedMonsters[i] = curM.copy(pendingAoeTiles = emptyList(), aoeCooldownTicks = 4)
                    if (s.hp <= 0) break
                    continue
                } else if (curM.aoeCooldownTicks <= 0 && distToTarget <= 4) {
                    val waveTiles = computeBossSpecialTiles(floorMap, curM.type, curM.x, curM.y, targetX, targetY)
                    if (waveTiles.isNotEmpty()) {
                        updatedMonsters[i] = curM.copy(pendingAoeTiles = waveTiles)
                        val cueLabel = when {
                            isVenomBoss -> "VENOM NOVA!"
                            isQuakeBoss -> "QUARRY QUAKE!"
                            isPyreBoss -> "LABYRINTH PYRE!"
                            curM.type == MonsterType.IGNIS_LORD_OF_THE_PIT -> "FLAME BURST!"
                            else -> "FIRE WAVE!"
                        }
                        val cueKind = when {
                            isVenomBoss -> FloatingTextKind.POISON
                            isQuakeBoss -> FloatingTextKind.DAMAGE_PLAYER
                            else -> FloatingTextKind.FIRE
                        }
                        val cueLog = when {
                            isVenomBoss -> "${curM.type.displayName} swells with venom — dodge the glowing green danger zone!"
                            isQuakeBoss -> "${curM.type.displayName} raises a massive monolith — dodge the glowing violet quake ring!"
                            isPyreBoss -> "${curM.type.displayName} ignites a Labyrinth Pyre — dodge the glowing molten danger tiles!"
                            else -> "${curM.type.displayName} charges a fiery burst — dodge the glowing warning tiles!"
                        }
                        s = s.addFloatingInternal(s.floor, curM.x.toFloat(), curM.y.toFloat(), cueLabel, cueKind, nowMs)
                            .addLogInternal(cueLog)
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
                        spawnedSummons += BiomeProgressionRules.createScaledMonster(
                            id = sid,
                            type = summonType,
                            floor = s.floor,
                            x = tile.first,
                            y = tile.second,
                            targetLevel = (curM.level - 3).coerceAtLeast(1),
                            isSummon = true,
                            summonerId = curM.id
                        ).copy(targetPlayerId = targetId)
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

            if (targetsLocal && !playerInPz && !s.isSpectator && s.hp > 0 && (distToLocal <= 1 || shouldRangedLocal)) {
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

                val mDx = s.playerX - curM.x
                val mDy = s.playerY - curM.y
                val mFacing = when {
                    mDx == 0 && mDy == 0 -> curM.facing
                    abs(mDx) >= abs(mDy) -> if (mDx > 0) Direction.EAST else Direction.WEST
                    else -> if (mDy > 0) Direction.SOUTH else Direction.NORTH
                }
                curM = curM.copy(
                    lastAttackTimeMs = nowMs,
                    facing = mFacing
                )
                updatedMonsters[i] = curM
                var statusTag: String? = null
                if (s.isInvulnerable) {
                    // Admin / Infinite Health negates all incoming monster damage & status afflictions
                    s = s.copy(hp = s.effectiveMaxHp(), poisonTicks = 0, fireTicks = 0)
                        .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "IMMUNE", FloatingTextKind.BLOCK, nowMs)
                } else {
                    val dodgeChance = s.shieldDodgeChance(isRanged)
                    if (Random.nextDouble() < dodgeChance) {
                        s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "DODGE!", FloatingTextKind.BLOCK, nowMs)
                            .queuePlayerFx("DODGE!", FloatingTextKind.BLOCK, PlayerActionState.IDLE)
                    } else {
                        val rawDmg = Random.nextInt(curM.scaledMinDmg, curM.scaledMaxDmg + 1)
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
                                val resistPct = s.equipment.totalPoisonResistPercent()
                                if (resistPct > 0 && Random.nextInt(100) < resistPct) {
                                    s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "RESISTED", FloatingTextKind.BLOCK, nowMs)
                                        .addLogInternal("Your Mire-Woven gear resisted ${curM.type.displayName}'s poison!")
                                } else {
                                    statusTag = "POISON"
                                    val ticks = (5 * (100 - resistPct) / 100.0).roundToInt().coerceAtLeast(2)
                                    s = s.copy(poisonTicks = max(s.poisonTicks, ticks)).addLogInternal("${curM.type.displayName} poisoned you!")
                                }
                            }
                            if (curM.type.inflictsFire && (isRanged || Random.nextFloat() < 0.40f)) {
                                statusTag = "FIRE"
                                s = s.copy(fireTicks = max(s.fireTicks, 4)).addLogInternal("${curM.type.displayName} set you ablaze!")
                            }
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
                if (s.hp > 0) {
                    defensiveProcAttackers.add(curM)
                }
                if (s.hp <= 0) break
            } else if (isFloorHost && !targetsLocal && curM.type.rangedProjectile != null &&
                distToTarget in 2..curM.type.preferredRange &&
                (curM.type.aiStyle == MonsterAiStyle.RANGED_KITE || Random.nextFloat() < 0.50f)
            ) {
                val projKind = curM.type.rangedProjectile
                val rDx = targetX - curM.x
                val rDy = targetY - curM.y
                val rFacing = when {
                    rDx == 0 && rDy == 0 -> curM.facing
                    abs(rDx) >= abs(rDy) -> if (rDx > 0) Direction.EAST else Direction.WEST
                    else -> if (rDy > 0) Direction.SOUTH else Direction.NORTH
                }
                curM = curM.copy(
                    lastAttackTimeMs = nowMs,
                    facing = rFacing
                )
                updatedMonsters[i] = curM
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
                    monstersMutated = true
                } else {
                    // Lazily build O(1) monster tile occupancy set once per tick when any awake monster moves
                    val occ = occupiedTiles ?: HashSet<Long>(updatedMonsters.size + 8).also { set ->
                        for (j in 0 until updatedMonsters.size) {
                            val om = updatedMonsters[j]
                            if (om.floor == s.floor && om.hp > 0) set.add(packAiTile(om.x, om.y))
                        }
                        for (j in 0 until spawnedSummons.size) {
                            val sm = spawnedSummons[j]
                            if (sm.floor == s.floor && sm.hp > 0) set.add(packAiTile(sm.x, sm.y))
                        }
                        occupiedTiles = set
                    }
                    val isTileFree: (Int, Int) -> Boolean = { cx, cy ->
                        val t = floorMap.tileAt(cx, cy)
                        t.walkable &&
                            !floorMap.isProtectionZone(cx, cy) &&
                            !(cx == s.playerX && cy == s.playerY) &&
                            !occ.contains(packAiTile(cx, cy)) &&
                            remotePlayersOnFloor.none { rp -> rp.floor == curM.floor && rp.x == cx && rp.y == cy }
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
                        val slowPenalty = if (curM.slowTicks > 0) 1 else 0
                        occ.remove(packAiTile(curM.x, curM.y))
                        occ.add(packAiTile(moveDest.first, moveDest.second))
                        updatedMonsters[i] = curM.copy(
                            lastMoveFromX = curM.x,
                            lastMoveFromY = curM.y,
                            lastMoveTimeMs = nowMs,
                            x = moveDest.first,
                            y = moveDest.second,
                            targetPlayerId = targetId,
                            moveCooldown = (curM.type.moveIntervalTicks - 1 + slowPenalty).coerceAtLeast(0)
                        )
                        monstersMutated = true
                    }
                }
            }
        }
        if (monstersMutated || spawnedSummons.isNotEmpty() || updatedMonsters != s.monsters) {
            s = s.copy(monsters = if (spawnedSummons.isEmpty()) updatedMonsters else updatedMonsters + spawnedSummons)
        }

        // 6. Tick proc Ignite Burn DoT on active floor monsters (awards full XP & loot on kill)
        val burningIds = s.monsters.filter { it.floor == s.floor && it.hp > 0 && it.burnTicks > 0 }.map { it.id }
        for (mId in burningIds) {
            val bm = s.monsters.firstOrNull { it.id == mId } ?: continue
            val bDmg = bm.burnDamagePerTick.coerceAtLeast(5)
            s = s.copy(
                monsters = s.monsters.map { m ->
                    if (m.id == mId) m.copy(burnTicks = (m.burnTicks - 1).coerceAtLeast(0)) else m
                }
            )
            s = s.addFloatingInternal(bm.floor, bm.x.toFloat(), bm.y.toFloat() - 0.18f, "-$bDmg", FloatingTextKind.FIRE, nowMs)
            s = applyDamageToMonster(s, mId, bDmg, nowMs)
        }

        // 7. Resolve ON_DEFEND equipment procs (Shield, Chest Armor, Necklace, Ring) for incoming hits
        if (!playerInPz && s.hp > 0) {
            for (attacker in defensiveProcAttackers) {
                if (s.hp <= 0) break
                s = EquipmentProcEngine.resolveDefenseProcs(s, attacker, nowMs)
            }
        }
        return s
    }

    /**
     * Computes telegraphed ground danger zone tiles tailored to each Dungeon Boss:
     * - [BossSpecialKind.VENOM_NOVA] (Broodmother Arachne): lethal 2-tile radial venom eruption ring/cross.
     * - [MonsterType.IGNIS_LORD_OF_THE_PIT]: volcanic flame burst (adjacent magma ring + directional pillar wave).
     * - [MonsterType.DEMON_LORD] / [MonsterType.ELDER_DRAGON]: directional 3-wide fire wave cone.
     */
    internal fun computeBossSpecialTiles(
        floorMap: WorldFloor,
        type: MonsterType,
        fromX: Int,
        fromY: Int,
        targetX: Int,
        targetY: Int
    ): List<Pair<Int, Int>> = when {
        type.specialKind == BossSpecialKind.VENOM_NOVA -> {
            val tiles = linkedSetOf<Pair<Int, Int>>()
            for (dy in -2..2) {
                for (dx in -2..2) {
                    if (dx == 0 && dy == 0) continue
                    val manhattan = abs(dx) + abs(dy)
                    if (manhattan > 3) continue // Diamond-ring venom burst leaving diagonal escape corners
                    val tx = fromX + dx
                    val ty = fromY + dy
                    if (tx in 0 until floorMap.width &&
                        ty in 0 until floorMap.height &&
                        floorMap.tileAt(tx, ty).walkable &&
                        !floorMap.isProtectionZone(tx, ty)
                    ) {
                        tiles += (tx to ty)
                    }
                }
            }
            tiles.toList()
        }
        type.specialKind == BossSpecialKind.QUARRY_QUAKE -> {
            val tiles = linkedSetOf<Pair<Int, Int>>()
            // 2-tile radial seismic quake ring around Kragor with outer shockwave fissures
            for (dy in -2..2) {
                for (dx in -2..2) {
                    if (dx == 0 && dy == 0) continue
                    val cheb = max(abs(dx), abs(dy))
                    if (cheb == 1 || (cheb == 2 && (dx == 0 || dy == 0 || abs(dx) == abs(dy)))) {
                        val tx = fromX + dx
                        val ty = fromY + dy
                        if (tx in 0 until floorMap.width &&
                            ty in 0 until floorMap.height &&
                            floorMap.tileAt(tx, ty).walkable &&
                            !floorMap.isProtectionZone(tx, ty)
                        ) {
                            tiles += (tx to ty)
                        }
                    }
                }
            }
            tiles.toList()
        }
        type.specialKind == BossSpecialKind.LABYRINTH_PYRE -> {
            val tiles = linkedSetOf<Pair<Int, Int>>()
            // Expanding volcanic cross + inner sanctum ring + targeted flame wave
            for (d in -3..3) {
                if (d == 0) continue
                listOf(fromX + d to fromY, fromX to fromY + d).forEach { (tx, ty) ->
                    if (tx in 0 until floorMap.width &&
                        ty in 0 until floorMap.height &&
                        floorMap.tileAt(tx, ty).walkable &&
                        !floorMap.isProtectionZone(tx, ty)
                    ) {
                        tiles += (tx to ty)
                    }
                }
            }
            tiles.addAll(computeFireWaveTiles(floorMap, fromX, fromY, targetX, targetY))
            tiles.toList()
        }
        type == MonsterType.IGNIS_LORD_OF_THE_PIT -> {
            val tiles = linkedSetOf<Pair<Int, Int>>()
            // Surrounding molten shockwave + directional magma wave toward target
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val tx = fromX + dx
                    val ty = fromY + dy
                    if (tx in 0 until floorMap.width &&
                        ty in 0 until floorMap.height &&
                        floorMap.tileAt(tx, ty).walkable &&
                        !floorMap.isProtectionZone(tx, ty)
                    ) {
                        tiles += (tx to ty)
                    }
                }
            }
            tiles.addAll(computeFireWaveTiles(floorMap, fromX, fromY, targetX, targetY))
            tiles.toList()
        }
        else -> computeFireWaveTiles(floorMap, fromX, fromY, targetX, targetY)
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
        nowMs: Long,
        remotePlayersOnFloor: List<RemotePlayerState> = emptyList()
    ): GameWorldState {
        if (state.pendingRespawns.isEmpty()) return state
        val remaining = mutableListOf<PendingRespawn>()
        val spawned = mutableListOf<MonsterEntity>()
        val bossSpawnAlerts = mutableListOf<String>()
        for (r in state.pendingRespawns) {
            val isReady = if (r.respawnAtMs > 0L && nowMs > 0L) {
                nowMs >= r.respawnAtMs
            } else {
                r.ticksRemaining <= 1
            }
            if (isReady) {
                val occupied = (state.floor == r.floor && state.playerX == r.spawnX && state.playerY == r.spawnY) ||
                    remotePlayersOnFloor.any { it.floor == r.floor && it.x == r.spawnX && it.y == r.spawnY } ||
                    state.monsters.any { it.floor == r.floor && it.x == r.spawnX && it.y == r.spawnY }
                val floorMap = WorldMaps.getFloor(r.floor)
                val inPz = floorMap.isProtectionZone(r.spawnX, r.spawnY)
                if (inPz) {
                    // Never respawn wild monsters inside Protection Zones
                    continue
                }
                if (!occupied) {
                    val respawnId = if (r.originalMonsterId > 0) r.originalMonsterId else nextMonsterId++
                    val respawnSeed = ((nowMs / 1000L).toInt() xor (respawnId * 31) xor (r.spawnX * 17) xor r.spawnY)
                    val (resolvedType, resolvedLevel) = com.example.game.world.BiomeMonsterSpawner.resolveRespawnSpeciesAndLevel(
                        floor = r.floor,
                        spawnX = r.spawnX,
                        spawnY = r.spawnY,
                        originalType = r.type,
                        fallbackLevel = r.level,
                        respawnSeed = respawnSeed
                    )
                    spawned += BiomeProgressionRules.createScaledMonster(
                        id = respawnId,
                        type = resolvedType,
                        floor = r.floor,
                        x = r.spawnX,
                        y = r.spawnY,
                        targetLevel = resolvedLevel
                    )
                    if (resolvedType.isBoss) {
                        bossSpawnAlerts += "[BOSS] ${resolvedType.displayName} has awakened on Floor ${r.floor}!"
                    }
                } else {
                    remaining += r.copy(
                        ticksRemaining = 3,
                        respawnAtMs = if (r.respawnAtMs > 0L && nowMs > 0L) nowMs + 3_000L else r.respawnAtMs
                    )
                }
            } else {
                remaining += r.copy(ticksRemaining = (r.ticksRemaining - 1).coerceAtLeast(1))
            }
        }
        var next = state.copy(
            monsters = state.monsters + spawned,
            pendingRespawns = remaining,
            outboundSystemAlerts = state.outboundSystemAlerts + bossSpawnAlerts
        )
        for (msg in bossSpawnAlerts) {
            next = next.addLogInternal(msg)
        }
        return next
    }

    private fun handlePlayerDeath(state: GameWorldState, nowMs: Long): GameWorldState {
        // Hard invulnerability safeguard: Admin / Infinite Health can never die or lose gear
        if (state.isInvulnerable) {
            return state.copy(
                hp = state.effectiveMaxHp(),
                poisonTicks = 0,
                fireTicks = 0,
                deathReport = null
            )
        }
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
        val droppedGold = state.gold.coerceAtLeast(0)
        val safeBankGold = state.bankGold.coerceAtLeast(0L)
        // Transfer dropped backpack items cleanly into the Reaper's Vault (no cloud-persisted world corpses)
        var nextReaperVault = state.reaperVault
        for (stack in state.backpack) {
            if (stack.itemId.isNotBlank() && stack.count > 0) {
                nextReaperVault = addItemPublic(nextReaperVault, stack.itemId, stack.count)
            }
        }
        if (nextReaperVault.size > 48) {
            nextReaperVault = nextReaperVault.takeLast(48)
        }
        // Tool Bag stays strapped to the adventurer on death (ensuring a Rope is always available)
        val keptTools = if (state.toolBag.any { it.itemId == "rope" }) state.toolBag
        else addItemPublic(state.toolBag, "rope", 1)

        val reaperNote = if (droppedCount > 0) {
            " Mortis claimed $droppedCount backpack item(s) — visit the Temple of Reapers (NW of Plaza) to buy them back."
        } else ""
        val goldDropNote = when {
            droppedGold > 0 && safeBankGold > 0L -> " Lost $droppedGold carried gp; ${formatGold(safeBankGold)} gp stayed safe in Bank."
            droppedGold > 0 -> " Lost $droppedGold carried gp."
            safeBankGold > 0L -> " Your ${formatGold(safeBankGold)} gp in the Bank Vault stayed safe."
            else -> ""
        }

        return state.copy(
            level = newLevel,
            xp = newXp,
            hp = newMaxHp,
            maxHp = newMaxHp,
            mp = newMaxMp,
            maxMp = newMaxMp,
            maxCapOz = newMaxCap,
            gold = 0,
            bankGold = safeBankGold,
            regionId = DEFAULT_REGION_ID,
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
            reaperVault = nextReaperVault,
            targetMonsterId = null,
            autoFollowTarget = false,
            walkPath = emptyList(),
            longRangeGoal = null,
            longRangeWaypoints = emptyList(),
            activePanel = ActivePanel.NONE,
            deathReport = DeathReport(xpLost, state.floor, state.playerX, state.playerY, droppedCount, droppedGold, safeBankGold)
        ).addLogInternal("You died! Lost $xpLost XP (-10%). Tool Bag stayed strapped.$reaperNote$goldDropNote")
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
        ).addLogInternal("Targeting ${m.type.displayName} [Lvl ${m.level}].")
    }

    /**
     * Auto-walk triggered from the minimap or World Map. Plans the full route tile by tile with
     * [RoutePlanner]; if the destination is across the sea, walks to the nearest ferry captain instead.
     */
    fun walkToCoordinate(state: GameWorldState, tx: Int, ty: Int): GameWorldState {
        if (state.deathReport != null) return state
        val floorMap = WorldMaps.getFloor(state.floor)
        val clampedX = tx.coerceIn(0, floorMap.width - 1)
        val clampedY = ty.coerceIn(0, floorMap.height - 1)
        val dismissed = state.copy(
            activePanel = ActivePanel.NONE,
            inspectingCorpseId = null,
            activeHarborPortId = null,
            autoFollowTarget = false,
            longRangeGoal = null,
            longRangeWaypoints = emptyList()
        )
        if (clampedX == state.playerX && clampedY == state.playerY) return dismissed

        // Snap slightly if tapping water/cliff edge on the parchment World Map
        val (goalX, goalY) = if (floorMap.tileAt(clampedX, clampedY).walkable) {
            clampedX to clampedY
        } else {
            WorldMaps.findNearestWalkableTile(state.floor, clampedX, clampedY, maxRadius = 14)
                ?: (clampedX to clampedY)
        }

        val npcSet = WorldMaps.npcs.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()
        val route = RoutePlanner.plan(floorMap, state.playerX, state.playerY, goalX, goalY) { x, y -> (x to y) in npcSet }
        if (route != null && route.isNotEmpty()) {
            val far = route.size > LONG_ROUTE_STEPS
            val label = if (state.floor == 0 && far) WorldMaps.regionForGlobal(goalX, goalY).displayName else "($goalX, $goalY)"
            return dismissed.copy(
                walkPath = route,
                longRangeGoal = goalX to goalY,
                longRangeWaypoints = emptyList()
            ).addLogInternal(if (far) "Charted a route to $label (${route.size} steps)." else "Auto-walking to $label...")
        }

        // No land route (another continent or island): walk to the nearest reachable ferry captain
        if (state.floor == 0) {
            val ports = WorldMaps.harborPorts.sortedBy { abs(it.dockX - state.playerX) + abs(it.dockY - state.playerY) }.take(3)
            for (port in ports) {
                val dock = WorldMaps.findNearestWalkableTile(0, port.dockX, port.dockY + 1, maxRadius = 3) ?: continue
                val toPort = RoutePlanner.plan(floorMap, state.playerX, state.playerY, dock.first, dock.second) { x, y -> (x to y) in npcSet }
                if (toPort != null && toPort.isNotEmpty()) {
                    val dest = WorldMaps.regionForGlobal(goalX, goalY).displayName
                    return dismissed.copy(walkPath = toPort, longRangeGoal = dock, longRangeWaypoints = emptyList())
                        .addLogInternal("No land route to $dest. Heading to ${port.name}: ask ${port.captainName} for a ferry.")
                }
            }
        }
        return dismissed.addLogInternal("No route to ($goalX, $goalY) from here.")
    }

    /** How far along the route a detour around a blocker tries to rejoin it. */
    private const val DETOUR_REJOIN_STEPS = 8

    /** Routes longer than this are announced as charted journeys. */
    private const val LONG_ROUTE_STEPS = 60

    /** Anything you can use or equip can go on the hotbar, plus the built-in actions. */
    fun isCustomHotbarBindable(itemId: String): Boolean {
        if (HotbarActions.isAction(itemId)) return itemId in HotbarActions.labels
        val def = ItemCatalog.all[itemId] ?: return false
        if (def.adminOnly && def.category != ItemCategory.ADMIN_SCROLL) return false
        return when (def.category) {
            ItemCategory.RAW_ORE, ItemCategory.METAL_INGOT, ItemCategory.CRAFT_MATERIAL,
            ItemCategory.LOOT_VALUABLE, ItemCategory.RUNE_BLANK -> false
            else -> true
        }
    }

    fun bindCustomHotbarSlot(state: GameWorldState, slotIndex: Int, itemId: String?): GameWorldState {
        if (slotIndex !in 0 until HotbarActions.MAX_SLOTS) return state
        val current = List(HotbarActions.MAX_SLOTS) { idx -> state.customHotbarSlots.getOrNull(idx) }.toMutableList()
        if (itemId == null) {
            current[slotIndex] = null
            return state.copy(customHotbarSlots = current.toList())
                .addLogInternal("Cleared hotbar slot ${slotIndex + 1}.")
        }
        if (!isCustomHotbarBindable(itemId)) {
            return state.addLogInternal("That can't go on the hotbar.")
        }
        current[slotIndex] = itemId
        val name = HotbarActions.labels[itemId] ?: ItemCatalog.get(itemId).name
        return state.copy(customHotbarSlots = current.toList())
            .addLogInternal("Set hotbar slot ${slotIndex + 1} to $name.")
    }

    /** Restores the classic hotbar (Heal, Mana, Heal Rune, Attack Rune, Rope). */
    fun resetHotbar(state: GameWorldState): GameWorldState =
        state.copy(customHotbarSlots = HotbarActions.DEFAULT_SLOTS).addLogInternal("Hotbar reset to default.")

    /**
     * Health/mana orb taps. Health: a health potion, or hotbar food (e.g. fish) when out of potions.
     * Mana: a mana potion.
     */
    fun useOrbSide(state: GameWorldState, health: Boolean, nowMs: Long): GameWorldState {
        if (!health) {
            return if (state.hasItem("mana_potion")) useManaPotion(state, nowMs)
            else state.addLogInternal("Out of Mana Potions! Old Finn, Alaric and Zephyr sell them.")
        }
        if (state.hasItem("health_potion")) return useHealthPotion(state, nowMs)
        val food = state.customHotbarSlots.filterNotNull().firstOrNull { id ->
            state.hasItem(id) && ItemCatalog.all[id]?.category == ItemCategory.FOOD
        }
        return if (food != null) useOrEquipBackpackItem(state, food, nowMs)
        else state.addLogInternal("Out of Health Potions and no food on your hotbar. Bind fish to a + slot!")
    }

    fun useCustomHotbarSlot(state: GameWorldState, slotIndex: Int, nowMs: Long): GameWorldState {
        val itemId = state.customHotbarSlots.getOrNull(slotIndex) ?: return state
        if (HotbarActions.isAction(itemId)) return useHotbarAction(state, itemId, nowMs)
        if (!state.hasItem(itemId)) {
            val name = ItemCatalog.all[itemId]?.name ?: itemId
            return state.addLogInternal("Out of $name!")
        }
        return useOrEquipBackpackItem(state, itemId, nowMs)
    }

    private fun useHotbarAction(state: GameWorldState, action: String, nowMs: Long): GameWorldState = when (action) {
        HotbarActions.HEALTH_POTION -> useHealthPotion(state, nowMs)
        HotbarActions.MANA_POTION -> useManaPotion(state, nowMs)
        HotbarActions.HEAL_RUNE -> useHealingRune(state, nowMs)
        HotbarActions.ATTACK_RUNE -> useAttackRune(state, nowMs)
        HotbarActions.ROPE -> useRope(state, nowMs)
        HotbarActions.EAT_FOOD -> {
            val best = state.allCarriedItems()
                .map { it.itemId }
                .distinct()
                .mapNotNull { ItemCatalog.all[it] }
                .filter { it.category == ItemCategory.FOOD }
                .maxByOrNull { it.restoreAmount }
            if (best != null) useOrEquipBackpackItem(state, best.id, nowMs) else state.addLogInternal("You have no food.")
        }
        HotbarActions.OPEN_BAG -> state.copy(activePanel = ActivePanel.BACKPACK)
        HotbarActions.OPEN_GEAR -> state.copy(activePanel = ActivePanel.EQUIPMENT)
        HotbarActions.OPEN_MAP -> state.copy(activePanel = ActivePanel.WORLD_MAP)
        HotbarActions.OPEN_SKILLS -> state.copy(activePanel = ActivePanel.SKILLS)
        HotbarActions.LOOT_NEARBY -> LootCollection.lootNearby(state, nowMs)
        else -> state
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
                val newHp = (state.hp + def.restoreAmount).coerceAtMost(state.effectiveMaxHp())
                val newMp = (state.mp + def.restoreAmount / 2).coerceAtMost(state.maxMp)
                val healed = newHp - state.hp
                val healedMp = newMp - state.mp
                val healLabel = if (healedMp > 0) "+$healed HP / +$healedMp MP" else "+$healed HP"
                state.copy(hp = newHp, mp = newMp, backpack = removeItemPublic(state.backpack, itemId, 1))
                    .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+$healed HP", FloatingTextKind.HEAL, nowMs)
                    .queuePlayerFx("+$healed HP", FloatingTextKind.HEAL, PlayerActionState.HEALING)
                    .addLogInternal("Ate ${def.name} ($healLabel).")
            }
            ItemCategory.FISHING_BAIT -> selectFishingBait(state, itemId, nowMs)
            ItemCategory.ADMIN_SCROLL -> {
                if (!state.isAdmin) {
                    return state.addLogInternal("Only the Admin can unseal the Sovereign's Scroll.")
                }
                state.copy(activePanel = ActivePanel.ADMIN_SCROLL)
            }
            ItemCategory.AMMO -> {
                state.copy(equipment = state.equipment.copy(ammoId = itemId))
                    .addLogInternal("Loaded ${def.name} into your Quiver slot.")
            }
            else -> {
                val slot = def.equipSlot ?: return state.addLogInternal("${def.name} can be smelted, forged, fletched, or sold to Alaric.")
                val unmetReqs = if (state.isAdmin) emptyList() else state.unmetEquipRequirements(def)
                if (unmetReqs.isNotEmpty()) {
                    val reqText = unmetReqs.joinToString(" & ")
                    return state
                        .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "REQ UNMET", FloatingTextKind.INFO, nowMs)
                        .addLogInternal("Cannot equip ${def.name}: requires $reqText.")
                }

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

                if (def.preferredVocation != null && state.vocation != def.preferredVocation) {
                    notes.append(" (${def.preferredVocation.displayName} mastery — -25% off-vocation dmg)")
                }
                if (def.proc != null) {
                    notes.append(" • Proc: ${def.proc.name} (${def.proc.triggerChancePercent}%)")
                }
                val keepFollow = if (slot == EquipmentSlot.WEAPON && def.category != ItemCategory.WEAPON_MELEE) {
                    false
                } else {
                    state.autoFollowTarget
                }
                val nextState = state.copy(equipment = newEquip, backpack = bp, autoFollowTarget = keepFollow)
                val resolvedHp = if (nextState.isInvulnerable) nextState.effectiveMaxHp() else nextState.hp.coerceAtMost(nextState.effectiveMaxHp())
                nextState.copy(hp = resolvedHp)
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
        val next = state.copy(
            equipment = state.equipment.withSlot(slot, null),
            backpack = addItemPublic(state.backpack, equippedId, 1)
        )
        val resolvedHp = if (next.isInvulnerable) next.effectiveMaxHp() else next.hp.coerceAtMost(next.effectiveMaxHp())
        return next.copy(hp = resolvedHp)
            .addLogInternal("Unequipped ${ItemCatalog.get(equippedId).name}.")
    }

    fun dropItem(state: GameWorldState, itemId: String, nowMs: Long = 0L): GameWorldState {
        if (!state.hasItem(itemId)) return state
        val def = ItemCatalog.get(itemId)
        if (def.adminOnly) return state.addLogInternal("${def.name} is bound to the Admin and cannot be dropped.")
        val removed = removeCarriedItem(state, itemId, 1)
        val ownerId = state.saveId.takeIf { it.isNotBlank() }
        val ownerLabel = state.playerName.ifBlank { "Adventurer" }
        val publicAt = if (nowMs > 0L && ownerId != null) nowMs + LOOT_OWNER_PROTECTION_MS else 0L
        val expiresAt = if (nowMs > 0L) nowMs + REGULAR_CORPSE_LIFETIME_MS else 0L
        val droppedBag = CorpseEntity(
            id = "drop_${UUID.randomUUID()}",
            floor = removed.floor,
            x = removed.playerX,
            y = removed.playerY,
            label = "$ownerLabel's Dropped Bag",
            isPlayerCorpse = false,
            monsterTypeName = null,
            gold = 0,
            items = listOf(ItemStack(itemId, 1)),
            createdAtMs = nowMs,
            expiresAtMs = expiresAt,
            ownerPlayerId = ownerId,
            ownerName = ownerLabel,
            publicAtMs = publicAt,
            isDroppedBag = true
        )
        return removed.copy(
            corpses = (removed.corpses + droppedBag).takeLast(38),
            outboundLootSpawns = removed.outboundLootSpawns + droppedBag
        )
            .addFloatingInternal(removed.floor, removed.playerX.toFloat(), removed.playerY.toFloat(), "Dropped ${def.name}", FloatingTextKind.INFO, nowMs)
            .addLogInternal("Dropped ${def.name} on the ground (protected for 5s).")
    }

    /**
     * Admin-only Sovereign's Scroll action: sets character level to any target level (1..999),
     * synchronizes XP, scales maxHp/maxMp/maxCapOz cleanly, locks HP full, and optionally
     * boosts combat/magic skills so high-tier equipment works at full power.
     */
    fun setAdminLevel(
        state: GameWorldState,
        targetLevel: Int,
        syncSkills: Boolean = true,
        nowMs: Long = 0L
    ): GameWorldState {
        if (!state.isAdmin) return state
        val clampedLevel = targetLevel.coerceIn(1, 999)
        val voc = state.vocation
        val levelsAboveOne = clampedLevel - 1
        val newMaxHp = (voc.baseHp + levelsAboveOne * voc.hpPerLevel).coerceIn(voc.baseHp, 50_000)
        val newMaxMp = (voc.baseMp + levelsAboveOne * voc.mpPerLevel).coerceIn(voc.baseMp, 50_000)
        val newMaxCap = GameWorldState.maxCapForLevel(clampedLevel, voc)
        val newXp = state.xpForLevel(clampedLevel)

        val updatedSkills = if (syncSkills) {
            val targetCombatSkill = clampedLevel.coerceIn(10, 120)
            val targetMagicSkill = clampedLevel.coerceIn(1, 100)
            fun boost(sp: SkillProgress, floorLevel: Int): SkillProgress =
                if (sp.level >= floorLevel) sp else SkillProgress(level = floorLevel, points = 0.0)
            PlayerSkills(
                melee = boost(state.skills.melee, targetCombatSkill),
                fist = boost(state.skills.fist, targetCombatSkill),
                club = boost(state.skills.club, targetCombatSkill),
                sword = boost(state.skills.sword, targetCombatSkill),
                axe = boost(state.skills.axe, targetCombatSkill),
                distance = boost(state.skills.distance, targetCombatSkill),
                shielding = boost(state.skills.shielding, targetCombatSkill),
                magic = boost(state.skills.magic, targetMagicSkill),
                fishing = boost(state.skills.fishing, targetCombatSkill),
                mining = boost(state.skills.mining, targetCombatSkill)
            )
        } else {
            state.skills
        }

        val updated = state.copy(
            level = clampedLevel,
            xp = newXp,
            maxHp = newMaxHp,
            mp = newMaxMp,
            maxMp = newMaxMp,
            maxCapOz = newMaxCap,
            skills = updatedSkills,
            poisonTicks = 0,
            fireTicks = 0
        )
        return updated
            .copy(hp = updated.effectiveMaxHp())
            .addFloatingInternal(updated.floor, updated.playerX.toFloat(), updated.playerY.toFloat(), "LEVEL $clampedLevel!", FloatingTextKind.SKILL_UP, nowMs)
            .addLogInternal("Admin Control set your level to $clampedLevel (HP ${updated.effectiveMaxHp()}, MP $newMaxMp).")
    }

    /**
     * Admin-only Sovereign's Scroll action: grants top Tier 5 Cinderite equipment & endgame jewelry
     * for the chosen weapon classification (Sword, Axe, Club, Distance, Magic, or All Classes).
     * Bypasses carrying Cap overflow so Admin always receives the complete kit cleanly.
     */
    fun grantAdminEquipmentKit(
        state: GameWorldState,
        classification: AdminEquipmentClassification,
        autoEquip: Boolean = true,
        nowMs: Long = 0L
    ): GameWorldState {
        if (!state.isAdmin) return state
        val commonDefenseAndJewelry = listOf(
            "cinderite_warplate" to 1,
            "cinderite_bastion" to 1,
            "cinderite_crown" to 1,
            "cinderite_tassets" to 1,
            "cinderite_treads" to 1,
            "cinderheart_sovereign_torc" to 1,
            "cinderite_overlord_band" to 1
        )
        val classItems: List<Pair<String, Int>> = when (classification) {
            AdminEquipmentClassification.SWORD -> listOf(
                "cinderite_greatsword" to 1,
                "vesperite_longblade" to 1
            )
            AdminEquipmentClassification.AXE -> listOf(
                "cinderite_greataxe" to 1,
                "vesperite_cleaver" to 1
            )
            AdminEquipmentClassification.CLUB -> listOf(
                "cinderite_worldbreaker" to 1,
                "vesperite_warmaul" to 1
            )
            AdminEquipmentClassification.DISTANCE -> listOf(
                "cinderite_ashen_bow" to 1,
                "venom_shafts" to 100,
                "starlight_quarrels" to 100
            )
            AdminEquipmentClassification.MAGIC -> listOf(
                "cinderite_soulspire" to 1,
                "sd_rune" to 50,
                "fireball_rune" to 50,
                "healing_rune" to 50
            )
            AdminEquipmentClassification.ALL -> listOf(
                "cinderite_greatsword" to 1,
                "vesperite_longblade" to 1,
                "cinderite_greataxe" to 1,
                "vesperite_cleaver" to 1,
                "cinderite_worldbreaker" to 1,
                "vesperite_warmaul" to 1,
                "cinderite_ashen_bow" to 1,
                "venom_shafts" to 100,
                "starlight_quarrels" to 100,
                "cinderite_soulspire" to 1,
                "sd_rune" to 50,
                "fireball_rune" to 50,
                "healing_rune" to 50
            )
        }

        val allToGrant = classItems + commonDefenseAndJewelry
        var bp = state.backpack
        for ((itemId, qty) in allToGrant) {
            val def = ItemCatalog.all[itemId] ?: continue
            val alreadyCarried = bp.filter { it.itemId == itemId }.sumOf { it.count } +
                (if (state.equipment.getAllEquippedIds().contains(itemId)) 1 else 0)
            if (def.stackable) {
                val needed = (qty - alreadyCarried).coerceAtLeast(0)
                if (needed > 0) bp = addItemPublic(bp, itemId, needed)
            } else if (alreadyCarried == 0) {
                bp = addItemPublic(bp, itemId, 1)
            }
        }

        var next = state.copy(backpack = bp)
        if (autoEquip) {
            val primaryWeaponId = when (classification) {
                AdminEquipmentClassification.SWORD -> "cinderite_greatsword"
                AdminEquipmentClassification.AXE -> "cinderite_greataxe"
                AdminEquipmentClassification.CLUB -> "cinderite_worldbreaker"
                AdminEquipmentClassification.DISTANCE -> "cinderite_ashen_bow"
                AdminEquipmentClassification.MAGIC -> "cinderite_soulspire"
                AdminEquipmentClassification.ALL -> "cinderite_greatsword"
            }
            val slotsToEquip = buildList {
                add(primaryWeaponId)
                add("cinderite_warplate")
                add("cinderite_crown")
                add("cinderite_tassets")
                add("cinderite_treads")
                add("cinderheart_sovereign_torc")
                add("cinderite_overlord_band")
                if (classification == AdminEquipmentClassification.DISTANCE) {
                    add("venom_shafts")
                }
            }
            for (eqId in slotsToEquip) {
                if (next.hasItem(eqId)) {
                    next = useOrEquipBackpackItem(next, eqId, nowMs)
                }
            }
            if (classification == AdminEquipmentClassification.MAGIC || classification == AdminEquipmentClassification.ALL) {
                next = next.copy(boundAttackRuneId = "sd_rune", boundHealRuneId = "healing_rune")
            }
        }
        return next
            .copy(hp = next.effectiveMaxHp())
            .addFloatingInternal(next.floor, next.playerX.toFloat(), next.playerY.toFloat(), "ARMORY GRANTED!", FloatingTextKind.SKILL_UP, nowMs)
            .addLogInternal("Admin Control granted top Tier 5 ${classification.label} equipment!")
    }

    /**
     * Admin-only local atmospheric lighting override (0.0..24.0 hours).
     * Strictly gated on [GameWorldState.isAdmin]; does not modify persistent cloud saves.
     */
    fun setAdminTimeOfDay(
        state: GameWorldState,
        hours: Float
    ): GameWorldState {
        if (!state.isAdmin) return state
        val clamped = if (hours.isNaN() || hours.isInfinite()) DEFAULT_TIME_OF_DAY_HOURS else hours.coerceIn(0f, 24f)
        return state.copy(timeOfDayHours = clamped)
    }

    /**
     * Admin-only Sovereign's Scroll action: toggles Spectator Mode.
     * While enabled, monsters treat Admin as completely invisible (clearing any active aggro),
     * and RealtimeMultiplayerManager hides Admin from all remote players.
     */
    fun toggleAdminSpectatorMode(
        state: GameWorldState,
        enabled: Boolean,
        nowMs: Long = 0L
    ): GameWorldState {
        if (!state.isAdmin) return state
        val localId = state.saveId.ifBlank { "local" }
        val updatedMonsters = if (enabled) {
            state.monsters.map { m ->
                if (m.targetPlayerId == localId) m.copy(targetPlayerId = null, pendingAoeTiles = emptyList()) else m
            }
        } else {
            state.monsters
        }
        val label = if (enabled) "SPECTATOR ON" else "VISIBLE"
        val logMsg = if (enabled) {
            "Spectator Mode ENABLED — you are now invisible to monsters and other players."
        } else {
            "Spectator Mode DISABLED — you are visible to the world again."
        }
        return state.copy(
            isSpectator = enabled,
            monsters = updatedMonsters,
            targetMonsterId = if (enabled) null else state.targetMonsterId,
            autoFollowTarget = if (enabled) false else state.autoFollowTarget
        )
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), label, FloatingTextKind.INFO, nowMs)
            .addLogInternal(logMsg)
    }

    /**
     * Admin-only instant teleport to a landmark or custom (floor, x, y) coordinates.
     * Strictly gated on [GameWorldState.isAdmin], validates floor allowlist (-6..0),
     * clamps coordinates to floor bounds, snaps to the nearest walkable NPC-free tile,
     * prewarms surrounding terrain chunks, and clears walk paths & combat aggro cleanly.
     */
    fun teleportAdmin(
        state: GameWorldState,
        targetFloor: Int,
        targetX: Int,
        targetY: Int,
        destinationLabel: String? = null,
        nowMs: Long = 0L
    ): GameWorldState {
        if (!state.isAdmin) return state
        val validFloor = if (WorldMaps.floors.containsKey(targetFloor)) targetFloor else 0
        val floorMap = WorldMaps.getFloor(validFloor)
        val clampedX = targetX.coerceIn(0, (floorMap.width - 1).coerceAtLeast(0))
        val clampedY = targetY.coerceIn(0, (floorMap.height - 1).coerceAtLeast(0))
        val (landX, landY) = WorldMaps.findNearestWalkableTile(validFloor, clampedX, clampedY, maxRadius = 12)
            ?: WorldMaps.fallbackLandingForFloor(validFloor)

        floorMap.chunkedGrid.prewarmAround(landX, landY, radiusChunks = 2)
        val destRegionId = if (validFloor == 0) {
            WorldMaps.regionForGlobal(landX, landY).regionId
        } else {
            floorMap.regionId
        }
        val localId = state.saveId.ifBlank { "local" }
        val clearedMonsters = state.monsters.map { m ->
            if (m.targetPlayerId == localId) m.copy(targetPlayerId = null, pendingAoeTiles = emptyList()) else m
        }
        val cleanLabel = destinationLabel?.trim()?.take(40)?.ifBlank { null }
        val logLocation = cleanLabel ?: "Floor $validFloor ($landX, $landY)"

        val next = state.copy(
            floor = validFloor,
            regionId = destRegionId,
            playerX = landX,
            playerY = landY,
            prevPlayerX = landX,
            prevPlayerY = landY,
            lastPlayerMoveMs = nowMs,
            walkPath = emptyList(),
            longRangeGoal = null,
            longRangeWaypoints = emptyList(),
            targetMonsterId = null,
            autoFollowTarget = false,
            monsters = clearedMonsters,
            floorWarningBanner = null
        )
        return next
            .copy(hp = next.effectiveMaxHp())
            .addFloatingInternal(validFloor, landX.toFloat(), landY.toFloat(), "WARPED!", FloatingTextKind.SKILL_UP, nowMs)
            .addLogInternal("Admin warped to $logLocation (F$validFloor: $landX, $landY).")
    }

    /**
     * Transfers gold coins from a corpse or Fallen Backpack into the player's pouch, up to remaining Cap (0.01 oz/gp).
     * Automatically removes the corpse and closes the loot panel once completely emptied.
     */
    fun lootGoldFromCorpse(state: GameWorldState, corpseId: String, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        if (corpse.isOwnerLockedFor(state.saveId, nowMs)) {
            val secs = ((corpse.ownerLockRemainingMs(state.saveId, nowMs) + 999L) / 1000L).coerceAtLeast(1L)
            return state.addLogInternal("${corpse.ownerName ?: "Owner"}'s loot is protected for ${secs}s!")
        }
        if (corpse.gold <= 0) return state
        val intRoom = (Int.MAX_VALUE.toLong() - state.gold.toLong()).coerceAtLeast(0L)
        val capRoom = ((state.remainingCapOz() + 1e-6) / GOLD_COIN_WEIGHT_OZ).toLong().coerceAtLeast(0L)
        val affordableGold = minOf(capRoom, corpse.gold.toLong(), intRoom).toInt()
        if (affordableGold <= 0) {
            return state.addLogInternal("Too heavy! Need at least 0.01 oz Cap to loot Gold Coins.")
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
        if (corpse.isOwnerLockedFor(state.saveId, nowMs)) {
            val secs = ((corpse.ownerLockRemainingMs(state.saveId, nowMs) + 999L) / 1000L).coerceAtLeast(1L)
            return state.addLogInternal("${corpse.ownerName ?: "Owner"}'s loot is protected for ${secs}s!")
        }
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
        val result = s.copy(
            corpses = updatedCorpses,
            activePanel = if (isEmptyNow) ActivePanel.NONE else s.activePanel,
            inspectingCorpseId = if (isEmptyNow) null else s.inspectingCorpseId
        )
            .addFloatingInternal(s.floor, corpse.x.toFloat(), corpse.y.toFloat(), "+$qtyLabel", FloatingTextKind.INFO, nowMs)
            .addLogInternal(msg)
        val remembered = LootCollection.remember(result, stack.itemId)
        return if (isEmptyNow) LootCollection.openNextCorpseOnTile(remembered, corpse, nowMs) else remembered
    }

    fun lootAllFromCorpse(state: GameWorldState, corpseId: String, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        if (corpse.isOwnerLockedFor(state.saveId, nowMs)) {
            val secs = ((corpse.ownerLockRemainingMs(state.saveId, nowMs) + 999L) / 1000L).coerceAtLeast(1L)
            return state.addLogInternal("${corpse.ownerName ?: "Owner"}'s loot is protected for ${secs}s!")
        }
        var s = state
        var remainingGold = corpse.gold
        var lootedGold = 0
        if (remainingGold > 0) {
            val intRoom = (Int.MAX_VALUE.toLong() - s.gold.toLong()).coerceAtLeast(0L)
            val capRoom = ((s.remainingCapOz() + 1e-6) / GOLD_COIN_WEIGHT_OZ).toLong().coerceAtLeast(0L)
            val affordableGold = minOf(capRoom, remainingGold.toLong(), intRoom).toInt()
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
        val result = s.copy(
            corpses = updatedCorpses,
            activePanel = if (closePanel) ActivePanel.NONE else s.activePanel,
            inspectingCorpseId = if (closePanel) null else s.inspectingCorpseId
        ).addLogInternal(msg)
        return if (closePanel) LootCollection.openNextCorpseOnTile(result, corpse, nowMs) else result
    }

    /**
     * Moves gold and/or items from a corpse into the bag without touching panels or the log.
     * Takes as much as carrying capacity allows; returns the new state, gold taken and item units taken.
     */
    internal fun takeLootFromCorpse(
        state: GameWorldState,
        corpseId: String,
        nowMs: Long,
        takeGold: Boolean = true,
        takeItem: (String) -> Boolean = { true }
    ): Triple<GameWorldState, Int, Int> {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return Triple(state, 0, 0)
        if (corpse.isOwnerLockedFor(state.saveId, nowMs)) return Triple(state, 0, 0)
        var s = state
        var gold = corpse.gold
        var goldTaken = 0
        if (takeGold && gold > 0) {
            val intRoom = (Int.MAX_VALUE.toLong() - s.gold.toLong()).coerceAtLeast(0L)
            val capRoom = ((s.remainingCapOz() + 1e-6) / GOLD_COIN_WEIGHT_OZ).toLong().coerceAtLeast(0L)
            goldTaken = minOf(capRoom, gold.toLong(), intRoom).toInt()
            if (goldTaken > 0) { s = s.copy(gold = s.gold + goldTaken); gold -= goldTaken }
        }
        val left = mutableListOf<ItemStack>()
        var units = 0
        for (stack in corpse.items) {
            if (!takeItem(stack.itemId)) { left += stack; continue }
            val w = ItemCatalog.get(stack.itemId).carriedWeightOz()
            var taken = 0
            while (taken < stack.count && s.remainingCapOz() + 1e-6 >= w) { s = addCarriedItem(s, stack.itemId, 1); taken++ }
            units += taken
            if (taken < stack.count) left += stack.copy(count = stack.count - taken)
        }
        val empty = gold == 0 && left.isEmpty()
        val corpses = if (empty) s.corpses.filterNot { it.id == corpseId } else s.corpses.map { if (it.id == corpseId) it.copy(gold = gold, items = left) else it }
        return Triple(s.copy(corpses = corpses), goldTaken, units)
    }

    /**
     * Spawns a shared ground corpse or dropped item pouch broadcast by another player on the same floor.
     * Uses relative [SharedLootSpawnEvent.lockRemainingMs] and [SharedLootSpawnEvent.lifetimeRemainingMs]
     * anchored to local [nowMs] so the 5-second owner-only countdown is exact across devices.
     */
    fun applyRemoteLootSpawn(
        state: GameWorldState,
        event: SharedLootSpawnEvent,
        nowMs: Long
    ): GameWorldState {
        if (event.floor != state.floor) return state
        if (event.gold <= 0 && event.items.isEmpty()) return state
        if (state.corpses.any { it.id == event.corpseId }) return state
        val validItems = event.items.filter { it.count > 0 && it.itemId.isNotBlank() }
        if (event.gold <= 0 && validItems.isEmpty()) return state
        val lockMs = event.lockRemainingMs.coerceIn(0L, LOOT_OWNER_PROTECTION_MS)
        val lifeMs = event.lifetimeRemainingMs.coerceIn(5_000L, BOSS_CORPSE_LIFETIME_MS)
        val corpse = CorpseEntity(
            id = event.corpseId,
            floor = event.floor,
            x = event.x,
            y = event.y,
            label = event.label,
            isPlayerCorpse = false,
            monsterTypeName = event.monsterTypeName,
            gold = event.gold.coerceAtLeast(0),
            items = validItems,
            createdAtMs = nowMs,
            expiresAtMs = if (nowMs > 0L) nowMs + lifeMs else 0L,
            ownerPlayerId = event.ownerPlayerId.takeIf { it.isNotBlank() },
            ownerName = event.ownerName.takeIf { it.isNotBlank() },
            publicAtMs = if (nowMs > 0L && lockMs > 0L) nowMs + lockMs else 0L,
            isDroppedBag = event.isDroppedBag
        )
        return state.copy(corpses = (state.corpses + corpse).takeLast(38))
    }

    /**
     * Synchronizes a ground loot pickup performed by another player on the same floor so items
     * cannot be duplicated across clients.
     */
    fun applyRemoteLootClaim(
        state: GameWorldState,
        event: SharedLootClaimEvent
    ): GameWorldState {
        if (event.floor != state.floor) return state
        val existing = state.corpses.firstOrNull { it.id == event.corpseId } ?: return state
        val isEmptied = event.emptied || (event.remainingGold <= 0 && event.remainingItems.isEmpty())
        val updatedCorpses = if (isEmptied) {
            state.corpses.filterNot { it.id == event.corpseId }
        } else {
            state.corpses.map {
                if (it.id == event.corpseId) {
                    it.copy(
                        gold = event.remainingGold.coerceAtLeast(0),
                        items = event.remainingItems.filter { stack -> stack.count > 0 }
                    )
                } else it
            }
        }
        val wasInspecting = state.inspectingCorpseId == event.corpseId
        val closePanel = wasInspecting && isEmptied
        var next = state.copy(
            corpses = updatedCorpses,
            activePanel = if (closePanel && state.activePanel == ActivePanel.LOOT_CORPSE) ActivePanel.NONE else state.activePanel,
            inspectingCorpseId = if (closePanel) null else state.inspectingCorpseId
        )
        if (closePanel) {
            next = next.addLogInternal("${event.claimerName} claimed the remaining loot from ${existing.label}.")
        }
        return next
    }

    /**
     * Computes the net carrying-capacity weight delta (in oz) for a player-to-player trade.
     * Negative values mean the trade makes the player lighter; positive values require remaining Cap.
     */
    fun tradeNetWeightDeltaOz(
        outgoingGold: Int,
        outgoingItems: List<ItemStack>,
        incomingGold: Int,
        incomingItems: List<ItemStack>
    ): Double {
        val outOz = outgoingGold.coerceAtLeast(0) * GOLD_COIN_WEIGHT_OZ +
            outgoingItems.sumOf { ItemCatalog.get(it.itemId).carriedWeightOz() * it.count.coerceAtLeast(0) }
        val inOz = incomingGold.coerceAtLeast(0) * GOLD_COIN_WEIGHT_OZ +
            incomingItems.sumOf { ItemCatalog.get(it.itemId).carriedWeightOz() * it.count.coerceAtLeast(0) }
        return inOz - outOz
    }

    fun canCarryTradeDelta(
        state: GameWorldState,
        outgoingGold: Int,
        outgoingItems: List<ItemStack>,
        incomingGold: Int,
        incomingItems: List<ItemStack>
    ): Boolean {
        val delta = tradeNetWeightDeltaOz(outgoingGold, outgoingItems, incomingGold, incomingItems)
        return state.remainingCapOz() + 1e-6 >= delta
    }

    /**
     * Atomically executes a confirmed player-to-player trade swap:
     * validates owned gold, owned non-admin items, carrying capacity (Cap oz), and gold overflow,
     * then deducts outgoing offers and grants incoming offers in a single state transition.
     */
    fun executePlayerTradeSwap(
        state: GameWorldState,
        partnerName: String,
        outgoingGold: Int,
        outgoingItems: List<ItemStack>,
        incomingGold: Int,
        incomingItems: List<ItemStack>,
        nowMs: Long
    ): Pair<GameWorldState, String?> {
        val safeOutGold = outgoingGold.coerceAtLeast(0)
        val safeInGold = incomingGold.coerceAtLeast(0)
        if (safeOutGold > state.gold) {
            return state to "Not enough gold to complete the trade."
        }
        val cleanOutItems = outgoingItems
            .filter { it.count > 0 && it.itemId.isNotBlank() }
            .groupBy { it.itemId }
            .map { (id, list) -> ItemStack(id, list.sumOf { it.count }) }
        val cleanInItems = incomingItems
            .filter { it.count > 0 && it.itemId.isNotBlank() }
            .groupBy { it.itemId }
            .map { (id, list) -> ItemStack(id, list.sumOf { it.count }) }

        for (stack in cleanOutItems) {
            val def = ItemCatalog.get(stack.itemId)
            if (def.adminOnly) {
                return state to "${def.name} is Admin-bound and cannot be traded."
            }
            if (state.itemCount(stack.itemId) < stack.count) {
                return state to "You no longer have ${stack.count}x ${def.name}."
            }
        }
        val resultingGoldLong = state.gold.toLong() - safeOutGold.toLong() + safeInGold.toLong()
        if (resultingGoldLong < 0L || resultingGoldLong > Int.MAX_VALUE.toLong()) {
            return state to "Gold pouch limit exceeded."
        }
        if (!canCarryTradeDelta(state, safeOutGold, cleanOutItems, safeInGold, cleanInItems)) {
            return state to "Too heavy! Not enough Cap (oz) to carry ${partnerName}'s offer."
        }

        var s = state.copy(gold = resultingGoldLong.toInt())
        for (stack in cleanOutItems) {
            s = removeCarriedItem(s, stack.itemId, stack.count)
        }
        for (stack in cleanInItems) {
            s = addCarriedItem(s, stack.itemId, stack.count)
        }

        val receivedParts = buildList {
            if (safeInGold > 0) add("$safeInGold gp")
            cleanInItems.forEach { st ->
                val name = ItemCatalog.get(st.itemId).name
                add(if (st.count > 1) "${st.count}x $name" else name)
            }
        }
        val summary = if (receivedParts.isEmpty()) {
            "Completed trade with $partnerName."
        } else {
            "Traded with $partnerName! Received ${receivedParts.joinToString(", ")}."
        }
        return s
            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "Trade Complete!", FloatingTextKind.XP, nowMs)
            .addLogInternal(summary) to null
    }

    fun depositItem(state: GameWorldState, itemId: String, requestedCount: Int = 1): GameWorldState {
        return try {
            val cleanId = itemId.trim()
            val available = state.itemCount(cleanId)
            if (cleanId.isEmpty() || available <= 0 || requestedCount <= 0) return state
            val def = ItemCatalog.get(cleanId)
            val unitWeight = def.carriedWeightOz()
            if (!unitWeight.isFinite() || unitWeight < 0.0) {
                return state.addLogInternal("Warning: Could not deposit ${def.name} (invalid item weight).")
            }
            val actualCount = requestedCount.coerceAtMost(available)
            val afterRemove = removeCarriedItem(state, cleanId, actualCount)
            val updatedDepot = addItemPublic(afterRemove.depot, cleanId, actualCount)
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
            val qtyLabel = if (actualCount > 1) "${actualCount}x ${def.name}" else def.name
            updated.addLogInternal("Stored $qtyLabel in Depot.")
        } catch (t: Throwable) {
            state.addLogInternal("Warning: Failed to deposit item (${t.message ?: "unexpected error"}).").copy(
                backpack = state.backpack.toList(),
                toolBag = state.toolBag.toList(),
                depot = state.depot.toList()
            )
        }
    }

    fun withdrawItem(state: GameWorldState, itemId: String, requestedCount: Int = 1): GameWorldState {
        return try {
            val cleanId = itemId.trim()
            val available = state.depot.filter { it.itemId == cleanId && it.count > 0 }.sumOf { it.count }
            if (cleanId.isEmpty() || available <= 0 || requestedCount <= 0) return state
            val def = ItemCatalog.get(cleanId)
            val neededWeight = def.carriedWeightOz()
            if (!neededWeight.isFinite() || neededWeight < 0.0) {
                return state.addLogInternal("Warning: Could not withdraw ${def.name} (invalid item weight).")
            }
            val currentRemainingCap = state.remainingCapOz()
            if (!currentRemainingCap.isFinite()) {
                return state.addLogInternal("Warning: Cap recalculation failed while withdrawing ${def.name}.")
            }
            val desiredCount = requestedCount.coerceAtMost(available)
            val maxAffordable = if (neededWeight <= 0.0) {
                desiredCount
            } else {
                ((currentRemainingCap + 1e-6) / neededWeight).toInt().coerceAtLeast(0)
            }
            val actualCount = minOf(desiredCount, maxAffordable)
            if (actualCount <= 0) {
                return state.addLogInternal(
                    "Not enough Cap (${String.format(java.util.Locale.US, "%.1f", neededWeight)} oz needed)."
                )
            }
            val destNote = if (def.isTool) " into Tool Bag" else ""
            val depotAfterRemove = removeItemPublic(state.depot, cleanId, actualCount)
            val updated = addCarriedItem(state.copy(depot = depotAfterRemove), cleanId, actualCount)
            val safeUpdated = updated.copy(
                backpack = updated.backpack.toList(),
                toolBag = updated.toolBag.toList(),
                depot = updated.depot.toList()
            )
            if (!safeUpdated.remainingCapOz().isFinite()) {
                return state.addLogInternal("Warning: Cap recalculation failed while withdrawing ${def.name}.")
            }
            val msg = if (actualCount < desiredCount) {
                "Withdrew $actualCount/$desiredCount ${def.name}$destNote from Depot (Cap limit reached)."
            } else {
                val qtyLabel = if (actualCount > 1) "${actualCount}x ${def.name}" else def.name
                "Withdrew $qtyLabel$destNote from Depot."
            }
            safeUpdated.addLogInternal(msg)
        } catch (t: Throwable) {
            state.addLogInternal("Warning: Failed to withdraw item (${t.message ?: "unexpected error"}).").copy(
                backpack = state.backpack.toList(),
                toolBag = state.toolBag.toList(),
                depot = state.depot.toList()
            )
        }
    }

    fun depositGold(state: GameWorldState, amount: Int): GameWorldState {
        if (amount <= 0) return state.addLogInternal("Enter a positive gold amount to deposit.")
        if (state.gold <= 0) return state.addLogInternal("You have no carried gold to deposit.")
        val actual = amount.coerceAtMost(state.gold)
        val newBank = (state.bankGold.coerceAtLeast(0L) + actual.toLong()).coerceAtLeast(0L)
        return state.copy(
            gold = state.gold - actual,
            bankGold = newBank
        ).addLogInternal("Deposited ${formatGold(actual.toLong())} gp into your Bank Vault (Balance: ${formatGold(newBank)} gp).")
    }

    fun depositAllGold(state: GameWorldState): GameWorldState {
        if (state.gold <= 0) return state.addLogInternal("You have no carried gold to deposit.")
        return depositGold(state, state.gold)
    }

    fun withdrawGold(state: GameWorldState, amount: Long): GameWorldState {
        if (amount <= 0L) return state.addLogInternal("Enter a positive gold amount to withdraw.")
        val availableBank = state.bankGold.coerceAtLeast(0L)
        if (availableBank <= 0L) return state.addLogInternal("Your Bank Vault is empty.")
        val desired = amount.coerceAtMost(availableBank)
        val intRoom = (Int.MAX_VALUE.toLong() - state.gold.toLong()).coerceAtLeast(0L)
        val capRoom = ((state.remainingCapOz() + 1e-6) / GOLD_COIN_WEIGHT_OZ).toLong().coerceAtLeast(0L)
        val actual = minOf(desired, intRoom, capRoom)
        if (actual <= 0L) {
            return if (capRoom <= 0L) {
                state.addLogInternal("Not enough Cap! Need at least 0.01 oz per gp to withdraw gold.")
            } else {
                state.addLogInternal("Your carried gold pouch is full.")
            }
        }
        val newBank = (availableBank - actual).coerceAtLeast(0L)
        val newGold = state.gold + actual.toInt()
        val msg = if (actual < desired) {
            "Withdrew ${formatGold(actual)} gp from Bank Vault (Cap limit reached; ${formatGold(newBank)} gp left in Bank)."
        } else {
            "Withdrew ${formatGold(actual)} gp from your Bank Vault (Balance: ${formatGold(newBank)} gp)."
        }
        return state.copy(
            gold = newGold,
            bankGold = newBank
        ).addLogInternal(msg)
    }

    fun withdrawAllGold(state: GameWorldState): GameWorldState {
        val availableBank = state.bankGold.coerceAtLeast(0L)
        if (availableBank <= 0L) return state.addLogInternal("Your Bank Vault is empty.")
        return withdrawGold(state, availableBank)
    }

    private fun formatGold(amount: Long): String = String.format(java.util.Locale.US, "%,d", amount)

    fun shopBuyPrice(state: GameWorldState, itemId: String): Int = VendorCatalog.buyPrice(state.activeShopId, itemId)

    fun shopSellPrice(state: GameWorldState, itemId: String): Int = VendorCatalog.sellPrice(state.activeShopId, itemId)

    fun maxShopBuyQuantity(state: GameWorldState, itemId: String, requestedQuantity: Int): Int {
        val qty = requestedQuantity.coerceAtLeast(1)
        val def = ItemCatalog.get(itemId)
        val price = shopBuyPrice(state, itemId)
        if (price <= 0) return 0
        val maxByGold = state.gold / price
        if (maxByGold <= 0) return 0
        val netWeightPerUnit = def.carriedWeightOz() - price * GOLD_COIN_WEIGHT_OZ
        val maxByCap = if (netWeightPerUnit <= 1e-6) {
            qty
        } else {
            ((state.remainingCapOz() + 1e-6) / netWeightPerUnit).toInt().coerceAtLeast(0)
        }
        return minOf(qty, maxByGold, maxByCap)
    }

    fun buyShopItem(state: GameWorldState, itemId: String, quantity: Int = 1): GameWorldState {
        val req = quantity.coerceAtLeast(1)
        val def = ItemCatalog.get(itemId)
        val price = shopBuyPrice(state, itemId)
        if (price <= 0) return state
        if (state.gold < price) return state.addLogInternal("Not enough Gold ($price gp required).")
        val actualQty = maxShopBuyQuantity(state, itemId, req)
        if (actualQty <= 0) {
            return state.addLogInternal("Too heavy! Need ${String.format("%.1f", def.carriedWeightOz())} oz Cap.")
        }
        val totalCost = price * actualQty
        val bagNote = if (def.isTool) " (packed in Tool Bag)" else ""
        val qtyPrefix = if (actualQty > 1) "${actualQty}x " else ""
        val clampNote = if (actualQty < req) " (max affordable/Cap)" else ""
        return addCarriedItem(state.copy(gold = state.gold - totalCost), itemId, actualQty)
            .addLogInternal("Bought $qtyPrefix${def.name} for $totalCost gp$bagNote$clampNote.")
    }

    fun sellBackpackItem(state: GameWorldState, itemId: String, quantity: Int = 1): GameWorldState {
        val owned = state.itemCount(itemId)
        if (owned <= 0) return state
        val def = ItemCatalog.get(itemId)
        val shopkeeper = VendorCatalog.vendor(state.activeShopId)?.npcName ?: "Alaric"
        val unitPrice = shopSellPrice(state, itemId)
        if (def.adminOnly || unitPrice <= 0) return state.addLogInternal("$shopkeeper isn't interested in ${def.name}.")
        val actualQty = minOf(quantity.coerceAtLeast(1), owned)
        val totalGain = unitPrice * actualQty
        val qtyPrefix = if (actualQty > 1) "${actualQty}x " else ""
        return removeCarriedItem(state, itemId, actualQty)
            .let { it.copy(gold = it.gold + totalGain) }
            .addLogInternal("Sold $qtyPrefix${def.name} for $totalGain gp.")
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
        return state.copy(hp = state.effectiveMaxHp(), mp = state.maxMp, poisonTicks = 0, fireTicks = 0)
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
        val maxExpansions = when {
            width >= 200 -> 4500
            width >= 80 -> 3000   // 96x96 Deep Delve floors: long winding routes through mazes and caverns
            else -> 1200
        }

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
            procAoeEffects = state.procAoeEffects.filter { nowMs - it.createdAtMs < it.durationMs },
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

internal fun GameWorldState.addFloatingInternal(
    floor: Int,
    x: Float,
    y: Float,
    text: String,
    kind: FloatingTextKind,
    nowMs: Long,
    customColorHex: Long? = null
): GameWorldState {
    val jitterX = if (kind == FloatingTextKind.PROC_CALLOUT) 0f else Random.nextFloat() * 0.25f - 0.125f
    return copy(
        floatingTexts = (floatingTexts + FloatingText(
            id = GameEngine.allocateEffectId(),
            floor = floor,
            tileX = x + jitterX,
            tileY = y,
            text = text,
            kind = kind,
            createdAtMs = nowMs,
            customColorHex = customColorHex
        )).takeLast(28)
    )
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
