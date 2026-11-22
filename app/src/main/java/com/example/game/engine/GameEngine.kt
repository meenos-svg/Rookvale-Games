package com.example.game.engine

import com.example.game.model.*
import com.example.game.multiplayer.LocalMonsterHitEvent
import com.example.game.multiplayer.RemoteMonsterHitEvent
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.SyncedMonsterSnapshot
import com.example.game.world.NpcRole
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import java.util.ArrayDeque
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
                ItemStack("hmm_rune", 3),
                ItemStack("cheese", 3)
            )
            Vocation.KNIGHT -> listOf(
                ItemStack("health_potion", 4),
                ItemStack("mana_potion", 1),
                ItemStack("hmm_rune", 2),
                ItemStack("cheese", 3)
            )
            Vocation.PALADIN -> listOf(
                ItemStack("ochre_shafts", 45),
                ItemStack("health_potion", 3),
                ItemStack("mana_potion", 2),
                ItemStack("hmm_rune", 4),
                ItemStack("cheese", 3)
            )
            Vocation.MAGE -> listOf(
                ItemStack("health_potion", 2),
                ItemStack("mana_potion", 5),
                ItemStack("hmm_rune", 6),
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
            maxCapOz = vocation.baseCap,
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
        val isLegacyV1 = save.mapVersion < CURRENT_MAP_VERSION
        val floorMap = WorldMaps.getFloor(save.floor)

        var targetX = save.x
        var targetY = save.y
        if (isLegacyV1 && save.floor == 0) {
            targetX += V1_SURFACE_OFFSET
            targetY += V1_SURFACE_OFFSET
        }
        if (targetX !in 0 until floorMap.width || targetY !in 0 until floorMap.height || !floorMap.tileAt(targetX, targetY).walkable) {
            val (fx, fy) = WorldMaps.fallbackLandingForFloor(save.floor)
            targetX = fx
            targetY = fy
        }

        val migratedCorpses = save.playerCorpses.map { corpse ->
            if (isLegacyV1 && corpse.floor == 0) {
                corpse.copy(x = corpse.x + V1_SURFACE_OFFSET, y = corpse.y + V1_SURFACE_OFFSET)
            } else {
                val cMap = WorldMaps.getFloor(corpse.floor)
                if (corpse.x in 0 until cMap.width && corpse.y in 0 until cMap.height && cMap.tileAt(corpse.x, corpse.y).walkable) {
                    corpse
                } else {
                    val (fx, fy) = WorldMaps.fallbackLandingForFloor(corpse.floor)
                    corpse.copy(x = fx, y = fy)
                }
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
            maxCapOz = save.maxCapOz,
            gold = save.gold,
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
            backpack = state.backpack,
            toolBag = state.toolBag,
            depot = state.depot,
            playerCorpses = state.corpses.filter { it.isPlayerCorpse },
            mapVersion = CURRENT_MAP_VERSION,
            tunicColorIndex = state.tunicColorIndex,
            hairColorIndex = state.hairColorIndex
        )
    }

    fun handleTileTap(state: GameWorldState, tx: Int, ty: Int, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        val floorMap = WorldMaps.getFloor(state.floor)
        if (tx !in 0 until floorMap.width || ty !in 0 until floorMap.height) return state

        // 1. Tap Monster -> toggle red target outline
        val tappedMonster = state.monsters.firstOrNull { it.floor == state.floor && it.x == tx && it.y == ty }
        if (tappedMonster != null) {
            val newTarget = if (state.targetMonsterId == tappedMonster.id) null else tappedMonster.id
            val msg = if (newTarget != null) {
                "Targeting ${tappedMonster.type.displayName} (${tappedMonster.hp}/${tappedMonster.maxHp} HP)."
            } else {
                "Target cleared."
            }
            return state.copy(targetMonsterId = newTarget).addLogInternal(msg)
        }

        val npc = WorldMaps.npcs.firstOrNull { it.floor == state.floor && it.x == tx && it.y == ty }
        val tile = floorMap.tileAt(tx, ty)
        val dist = chebyshev(state.playerX, state.playerY, tx, ty)

        // 2. NPC or Shop Counter (Brother Alden, The Oracle, Alaric)
        if (npc != null || tile == TileType.SHOP_COUNTER) {
            return if (dist <= 2) {
                when (npc?.role) {
                    NpcRole.TEMPLE_PRIEST -> blessAtTemple(state, nowMs)
                    NpcRole.ORACLE -> state.copy(activePanel = ActivePanel.ORACLE, walkPath = emptyList())
                        .addLogInternal(
                            when {
                                state.vocation != Vocation.ADVENTURER -> "The Oracle: 'Walk proudly on your chosen path, ${state.vocation.displayName}.'"
                                state.level < ORACLE_MIN_LEVEL -> "The Oracle: 'You are not yet ready, child of Rookvale. Return at Level $ORACLE_MIN_LEVEL.'"
                                else -> "The Oracle: 'Level ${state.level} achieved! Are you ready to claim your vocation?'"
                            }
                        )
                    else -> state.copy(activePanel = ActivePanel.SHOP, walkPath = emptyList())
                        .addLogInternal("Alaric: 'Potions, runes, and sturdy tools — forge your own steel at the Smithy!'")
                }
            } else setWalkPathToAdjacent(state, tx, ty)
        }

        // 3. Temple Altar
        if (tile == TileType.TEMPLE_ALTAR) {
            return if (dist <= 2) blessAtTemple(state, nowMs) else setWalkPathToAdjacent(state, tx, ty)
        }

        // 4. Depot Chest & Deep Outpost Mini-Depot
        if (tile == TileType.DEPOT_CHEST || tile == TileType.MINI_DEPOT) {
            return if (dist <= 1) {
                val label = if (tile == TileType.MINI_DEPOT) "Deep Outpost Mini-Depot" else "Rookvale Depot Chest"
                state.copy(activePanel = ActivePanel.DEPOT, walkPath = emptyList())
                    .addLogInternal("Opened your $label.")
            } else setWalkPathToAdjacent(state, tx, ty)
        }

        // 5. Physical World-Anchored Crafting Stations (Smelting Furnace & Blacksmith Anvil)
        if (tile == TileType.SMELTING_FURNACE) {
            return if (dist <= 1) {
                state.copy(activePanel = ActivePanel.FURNACE, walkPath = emptyList())
                    .addLogInternal("Stepped up to the roaring Smelting Furnace.")
            } else setWalkPathToAdjacent(state, tx, ty)
        }
        if (tile == TileType.BLACKSMITH_ANVIL) {
            return if (dist <= 1) {
                state.copy(activePanel = ActivePanel.ANVIL, walkPath = emptyList())
                    .addLogInternal("Readied your hammer at the Blacksmith Anvil.")
            } else setWalkPathToAdjacent(state, tx, ty)
        }

        // 6. Interactive Ore Veins (Ochre, Bogstone, Brimstone, Aetherium)
        if (tile.oreVeinKind() != null) {
            return if (dist <= 1) {
                CraftingAndMiningEngine.mineOreNode(state.copy(walkPath = emptyList()), tx, ty, nowMs)
            } else setWalkPathToAdjacent(state, tx, ty)
        }

        // 7. Lootable Corpse
        val corpse = state.corpses.firstOrNull { it.floor == state.floor && it.x == tx && it.y == ty }
        if (corpse != null && dist <= 1) {
            return state.copy(
                activePanel = ActivePanel.LOOT_CORPSE,
                inspectingCorpseId = corpse.id,
                walkPath = emptyList()
            )
        }

        // 8. Water -> Fishing
        if (tile == TileType.WATER) {
            return if (dist <= 2) attemptFishing(state, tx, ty, nowMs)
            else state.addLogInternal("Walk onto the wooden fishing pier to cast your line.")
        }

        // 9. Rope Spot Up
        if (tile == TileType.ROPE_SPOT_UP && dist <= 1) {
            return useRope(state, nowMs)
        }

        // 10. Walkable tile -> BFS path
        if (tile.walkable) {
            val path = findPath(state, tx, ty)
            if (path.isNotEmpty()) {
                return stepAlongPath(state.copy(walkPath = path), nowMs)
            }
        }
        return state
    }

    fun stepDirection(state: GameWorldState, dir: Direction, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        return attemptMoveTo(state.copy(walkPath = emptyList()), state.playerX + dir.dx, state.playerY + dir.dy, dir, nowMs)
    }

    fun stepAlongPath(state: GameWorldState, nowMs: Long): GameWorldState {
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
        val moved = attemptMoveTo(state.copy(walkPath = remaining), nx, ny, dir, nowMs)
        return if (moved.playerX == state.playerX && moved.playerY == state.playerY && moved.floor == state.floor) {
            moved.copy(walkPath = emptyList())
        } else {
            moved
        }
    }

    private fun attemptMoveTo(state: GameWorldState, nx: Int, ny: Int, dir: Direction, nowMs: Long): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        val tile = floorMap.tileAt(nx, ny)
        if (!tile.walkable) return state.copy(playerFacing = dir)

        val blockedByMonster = state.monsters.any { it.floor == state.floor && it.x == nx && it.y == ny }
        val blockedByNpc = WorldMaps.npcs.any { it.floor == state.floor && it.x == nx && it.y == ny }
        if (blockedByMonster || blockedByNpc) return state.copy(playerFacing = dir)

        val portal = WorldMaps.portalAt(state.floor, nx, ny, requiresRope = false)
        if (portal != null) {
            val destFloor = WorldMaps.getFloor(portal.toFloor)
            val msg = if (tile == TileType.ROPE_HOLE_DOWN) {
                "Dropped down the hole into ${destFloor.name}! Bring a Rope to climb back up."
            } else {
                "Entered ${destFloor.name} (${destFloor.subtitle})."
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
                activePanel = ActivePanel.NONE
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
            ActivePanel.DEPOT -> !CraftingAndMiningEngine.isNearStation(moved, TileType.DEPOT_CHEST) &&
                !CraftingAndMiningEngine.isNearStation(moved, TileType.MINI_DEPOT)
            ActivePanel.ORACLE -> moved.floor != 0 || chebyshev(moved.playerX, moved.playerY, WorldMaps.ORACLE_X, WorldMaps.ORACLE_Y) > 2
            else -> false
        }
        return if (shouldCloseStation) moved.copy(activePanel = ActivePanel.NONE) else moved
    }

    /**
     * Classic Tibia Level 20 Oracle Vocation Ceremony.
     * Preserves all earned skill levels and current stats; new vocation growth rates apply from the next level up.
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

        return state.copy(
            vocation = chosen,
            hp = state.maxHp,
            mp = state.maxMp,
            poisonTicks = 0,
            fireTicks = 0,
            activePanel = ActivePanel.NONE
        )
            .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "${chosen.displayName.uppercase()} ASCENDED!", FloatingTextKind.SKILL_UP, nowMs)
            .addLogInternal("The Oracle has blessed you as a ${chosen.displayName}! All skills preserved; new growth starts next level.")
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
        }
        if (s.fireTicks > 0 && s.hp > 0) {
            val fDmg = Random.nextInt(8, 15)
            s = s.copy(hp = (s.hp - fDmg).coerceAtLeast(0), fireTicks = s.fireTicks - 1)
                .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$fDmg", FloatingTextKind.FIRE, nowMs)
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

        // 5. Monster respawns (host-driven) & Ore Vein regeneration
        if (isFloorHost) {
            s = processRespawns(s, remotePlayersOnFloor)
        }
        s = CraftingAndMiningEngine.regenerateOreNodesTick(s)

        // 6. Death penalty check
        if (s.hp <= 0) {
            s = handlePlayerDeath(s, nowMs)
        }
        return s
    }

    private fun performPlayerAutoAttack(state: GameWorldState, target: MonsterEntity, nowMs: Long): GameWorldState {
        var s = state
        val weapon = s.equipment.weaponDef()
        val dist = chebyshev(s.playerX, s.playerY, target.x, target.y)
        val effectiveRange = weapon?.range ?: 1
        if (dist > effectiveRange) return s

        // Soft skill-requirement scaling (-35% dmg) & Tier 3-4 off-vocation penalty (-25% dmg)
        val reqType = weapon?.requiredSkillType
        val skillPenaltyMult = if (reqType != null && s.skills.levelFor(reqType) < weapon.requiredSkillLevel) 0.65 else 1.0
        val offVocationMult = s.weaponOffVocationMultiplier(weapon)

        return when (weapon?.category) {
            ItemCategory.WEAPON_WAND -> {
                if (s.mp >= weapon.manaCost) {
                    val magicMult = s.attackSkillMultiplier(s.skills.magic.level)
                    val basePower = weapon.attack + s.level * 0.65
                    val dmg = (basePower * magicMult * Random.nextDouble(0.85, 1.18) * skillPenaltyMult * offVocationMult)
                        .roundToInt().coerceAtLeast(6)
                    s = s.copy(mp = s.mp - weapon.manaCost)
                    s = advanceSkillPublic(s, SkillType.MAGIC, weapon.manaCost.toDouble() * 1.25, nowMs)
                    s = s.addProjectileInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), target.x.toFloat(), target.y.toFloat(), ProjectileKind.ENERGY_BOLT, nowMs)
                    applyDamageToMonster(s, target.id, dmg, nowMs, projectileKind = ProjectileKind.ENERGY_BOLT)
                } else if (dist <= 1) {
                    val meleeMult = s.attackSkillMultiplier(s.skills.melee.level)
                    val dmg = (Random.nextInt(4, 8) * meleeMult).roundToInt().coerceAtLeast(3)
                    s = advanceSkillPublic(s, SkillType.MELEE, 1.5, nowMs)
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
                    val dmg = (basePower * distMult * Random.nextDouble(0.80, 1.20) * skillPenaltyMult * offVocationMult)
                        .roundToInt().coerceAtLeast(5)
                    applyDamageToMonster(s, target.id, dmg, nowMs, projectileKind = projKind)
                } else {
                    s.addFloatingInternal(s.floor, target.x.toFloat(), target.y.toFloat(), "MISS", FloatingTextKind.BLOCK, nowMs)
                }
            }
            else -> {
                val baseAtk = weapon?.attack ?: 6
                s = advanceSkillPublic(s, SkillType.MELEE, 3.2, nowMs)
                val meleeMult = s.attackSkillMultiplier(s.skills.melee.level)
                val basePower = baseAtk + s.level * 0.60
                val dmg = (basePower * meleeMult * Random.nextDouble(0.80, 1.20) * skillPenaltyMult * offVocationMult)
                    .roundToInt().coerceAtLeast(4)
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
        s = s.copy(
            monsters = remainingMonsters,
            targetMonsterId = if (s.targetMonsterId == monsterId) null else s.targetMonsterId,
            corpses = (s.corpses + corpse).takeLast(38),
            pendingRespawns = s.pendingRespawns + PendingRespawn(
                type = m.type,
                floor = m.floor,
                spawnX = m.spawnX,
                spawnY = m.spawnY,
                ticksRemaining = respawnTicks,
                originalMonsterId = m.id
            )
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
        val newRespawns = if (isFloorHost) {
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
            val type = existing?.type ?: runCatching { MonsterType.valueOf(snap.typeName) }.getOrNull() ?: return@mapNotNull null
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
                contributors = existing?.contributors ?: emptySet()
            )
        }

        val newTarget = if (s.targetMonsterId != null && syncedOnFloor.none { it.id == s.targetMonsterId }) {
            null
        } else {
            s.targetMonsterId
        }
        return s.copy(
            monsters = otherFloorMonsters + syncedOnFloor,
            targetMonsterId = newTarget
        )
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
                    targetPlayerId = m.targetPlayerId
                )
            }
    }

    /**
     * World-sourced material-only monster loot tables (ZERO finished weapons!).
     */
    fun generateMonsterCorpse(m: MonsterEntity): CorpseEntity {
        val gold = Random.nextInt(m.type.goldMin, m.type.goldMax + 1)
        val loot = mutableListOf<ItemStack>()
        when (m.type) {
            MonsterType.RAT -> {
                if (Random.nextFloat() < 0.48f) loot += ItemStack("jagged_ivory", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("cheese", 1)
            }
            MonsterType.CRAG_WOLF -> {
                loot += ItemStack("brindle_hide", 1)
                if (Random.nextFloat() < 0.65f) loot += ItemStack("jagged_ivory", 1)
            }
            MonsterType.VESPER_BAT -> {
                loot += ItemStack("vesper_wing", 1)
                if (Random.nextFloat() < 0.40f) loot += ItemStack("jagged_ivory", 1)
            }
            MonsterType.CAVE_SPIDER -> {
                if (Random.nextFloat() < 0.72f) loot += ItemStack("spider_silk", 1)
                if (Random.nextFloat() < 0.58f) loot += ItemStack("bile_gland", 1)
                if (Random.nextFloat() < 0.25f) loot += ItemStack("health_potion", 1)
            }
            MonsterType.STONEHORN_TROLL -> {
                loot += ItemStack("brindle_hide", 1)
                if (Random.nextFloat() < 0.60f) loot += ItemStack("ochre_nugget", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("health_potion", 1)
            }
            MonsterType.SKELETON -> {
                loot += ItemStack("ancient_bone", 1)
                if (Random.nextFloat() < 0.28f) loot += ItemStack("health_potion", 1)
            }
            MonsterType.MARROW_ARCHER -> {
                loot += ItemStack("ancient_bone", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("vesper_wing", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("ochre_shafts", 6)
            }
            MonsterType.GHOUL -> {
                loot += ItemStack("ghoul_gem", 1)
                if (Random.nextFloat() < 0.50f) loot += ItemStack("ancient_bone", 1)
                if (Random.nextFloat() < 0.32f) loot += ItemStack("mana_potion", 1)
            }
            MonsterType.CYCLOPS -> {
                loot += ItemStack("colossus_tendon", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("dragon_ham", 1)
                if (Random.nextFloat() < 0.30f) loot += ItemStack("sd_rune", 1)
            }
            MonsterType.FORGE_BRUTE -> {
                loot += ItemStack("colossus_tendon", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("cinder_pitch", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("health_potion", 1)
            }
            MonsterType.RUNIC_GOLEM -> {
                loot += ItemStack("runic_slate", 1)
                if (Random.nextFloat() < 0.50f) loot += ItemStack("bogstone_shard", 1)
                if (Random.nextFloat() < 0.30f) loot += ItemStack("mana_potion", 1)
            }
            MonsterType.WYRM_HATCHLING -> {
                loot += ItemStack("wyrmscale_plate", 1)
                if (Random.nextFloat() < 0.55f) loot += ItemStack("dragon_ham", 1)
                if (Random.nextFloat() < 0.40f) loot += ItemStack("ember_heart", 1)
            }
            MonsterType.CINDER_WRAITH -> {
                loot += ItemStack("ember_heart", 1)
                if (Random.nextFloat() < 0.45f) loot += ItemStack("ghoul_gem", 1)
                if (Random.nextFloat() < 0.35f) loot += ItemStack("sd_rune", 1)
            }
            MonsterType.DRAGON_BOSS -> {
                loot += ItemStack("dragon_tooth", 1)
                loot += ItemStack("wyrmscale_plate", 2)
                loot += ItemStack("ember_heart", 1)
                loot += ItemStack("dragon_ham", 2)
                loot += ItemStack("sd_rune", 2)
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
            .addLogInternal("Drank Mana Potion (+$actual MP).")
    }

    fun useAttackRune(state: GameWorldState, nowMs: Long): GameWorldState {
        if (state.deathReport != null) return state
        val runeId = state.bestAttackRuneId() ?: return state.addLogInternal("No Attack Runes in backpack!")
        val target = state.monsters.firstOrNull { it.id == state.targetMonsterId && it.floor == state.floor }
            ?: return state.addLogInternal("Tap a monster first to aim your Attack Rune!")
        val runeDef = ItemCatalog.get(runeId)
        if (chebyshev(state.playerX, state.playerY, target.x, target.y) > runeDef.range) {
            return state.addLogInternal("Target is too far for ${runeDef.name} (range ${runeDef.range}).")
        }
        if (state.mp < runeDef.manaCost) return state.addLogInternal("Not enough Mana (${runeDef.manaCost} MP required).")

        val magicMult = state.attackSkillMultiplier(state.skills.magic.level)
        val dmg = ((runeDef.attack + state.level * 1.0) * magicMult * Random.nextDouble(0.85, 1.18))
            .roundToInt().coerceAtLeast(12)
        var next = state.copy(
            mp = state.mp - runeDef.manaCost,
            soul = (state.soul - 1).coerceAtLeast(0),
            backpack = removeItemPublic(state.backpack, runeId, 1)
        )
        next = advanceSkillPublic(next, SkillType.MAGIC, runeDef.manaCost.toDouble() * 1.4, nowMs)
        val projKind = if (runeId == "sd_rune") ProjectileKind.DEATH_RUNE else ProjectileKind.ENERGY_BOLT
        next = next.addProjectileInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), target.x.toFloat(), target.y.toFloat(), projKind, nowMs)
        return applyDamageToMonster(next, target.id, dmg, nowMs, "${runeDef.name} struck ${target.type.displayName} for $dmg!", projKind)
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
            activePanel = ActivePanel.NONE
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
        val activeRemoteTargets = remotePlayersOnFloor.filter {
            it.floor == s.floor && it.hp > 0 && !floorMap.isProtectionZone(it.x, it.y)
        }

        for (i in updatedMonsters.indices) {
            val m = updatedMonsters[i]
            if (m.floor != s.floor) continue

            // Determine target player: Host picks closest non-PZ player on floor; Non-host follows host's targetPlayerId
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
                    val dLocal = chebyshev(m.x, m.y, s.playerX, s.playerY)
                    if (dLocal <= 8) {
                        bestId = localPlayerId
                        bestX = s.playerX
                        bestY = s.playerY
                        bestDist = dLocal
                    }
                }
                for (rp in activeRemoteTargets) {
                    val dRem = chebyshev(m.x, m.y, rp.x, rp.y)
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
                if (m.targetPlayerId != targetId) {
                    updatedMonsters[i] = m.copy(targetPlayerId = targetId)
                }
            } else {
                targetId = m.targetPlayerId
                targetX = s.playerX
                targetY = s.playerY
                distToTarget = chebyshev(m.x, m.y, s.playerX, s.playerY)
            }

            if (targetId == null || distToTarget > 8) continue

            // Resolve monster attack locally ONLY when targeting this local player
            val targetsLocal = targetId == localPlayerId
            val distToLocal = chebyshev(m.x, m.y, s.playerX, s.playerY)
            if (targetsLocal && !playerInPz && s.hp > 0 &&
                (distToLocal <= 1 || (m.type.rangedBreathRange > 1 && distToLocal <= m.type.rangedBreathRange && Random.nextFloat() < 0.45f))
            ) {
                val isRanged = distToLocal > 1
                if (isRanged) {
                    val proj = if (m.type == MonsterType.MARROW_ARCHER) ProjectileKind.ARROW else ProjectileKind.FIRE_BREATH
                    s = s.addProjectileInternal(s.floor, m.x.toFloat(), m.y.toFloat(), s.playerX.toFloat(), s.playerY.toFloat(), proj, nowMs, 300L)
                }
                if (!isRanged) {
                    val shieldTrainPts = when {
                        s.hasActiveShield() -> 3.4
                        s.isUsingTwoHandedWeapon() -> 1.35
                        else -> 2.2
                    }
                    s = advanceSkillPublic(s, SkillType.SHIELDING, shieldTrainPts, nowMs)
                }

                val dodgeChance = s.shieldDodgeChance(isRanged)
                if (Random.nextDouble() < dodgeChance) {
                    s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "DODGE!", FloatingTextKind.BLOCK, nowMs)
                } else {
                    val rawDmg = Random.nextInt(m.type.minDmg, m.type.maxDmg + 1)
                    val armorMit = s.equipment.totalArmor() * 0.65
                    val glancingShieldMit = if (!isRanged && s.hasActiveShield()) s.equipment.shieldDefense() * 0.22 else 0.0
                    val reduced = (rawDmg - (if (isRanged) armorMit * 0.6 else (armorMit + glancingShieldMit) * Random.nextDouble(0.75, 1.05))).roundToInt()

                    if (reduced <= 0) {
                        s = s.addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "GLANCE", FloatingTextKind.BLOCK, nowMs)
                    } else {
                        s = s.copy(hp = (s.hp - reduced).coerceAtLeast(0))
                            .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "-$reduced", FloatingTextKind.DAMAGE_PLAYER, nowMs)
                        if (m.type.inflictsPoison && Random.nextFloat() < 0.35f) {
                            s = s.copy(poisonTicks = max(s.poisonTicks, 5)).addLogInternal("${m.type.displayName} poisoned you!")
                        }
                        if (m.type.inflictsFire && (isRanged || Random.nextFloat() < 0.40f)) {
                            s = s.copy(fireTicks = max(s.fireTicks, 4)).addLogInternal("${m.type.displayName} set you ablaze!")
                        }
                    }
                }
                if (s.hp <= 0) break
            }

            // Only the elected Floor Host moves monsters to keep all clients synchronized
            if (isFloorHost && distToTarget > 1) {
                val curM = updatedMonsters[i]
                if (curM.moveCooldown > 0) {
                    updatedMonsters[i] = curM.copy(moveCooldown = curM.moveCooldown - 1)
                } else {
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
                    val moveDest = candidates.firstOrNull { (cx, cy) ->
                        val t = floorMap.tileAt(cx, cy)
                        t.walkable &&
                            !floorMap.isProtectionZone(cx, cy) &&
                            !(cx == s.playerX && cy == s.playerY) &&
                            activeRemoteTargets.none { rp -> rp.x == cx && rp.y == cy } &&
                            updatedMonsters.none { other -> other.id != curM.id && other.floor == curM.floor && other.x == cx && other.y == cy }
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
        return s.copy(monsters = updatedMonsters)
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
        val minCapFloor = minOf(state.vocation.baseCap, Vocation.ADVENTURER.baseCap)
        val newMaxHp = (state.maxHp - levelsLost * state.vocation.hpPerLevel).coerceAtLeast(minHpFloor)
        val newMaxMp = (state.maxMp - levelsLost * state.vocation.mpPerLevel).coerceAtLeast(minMpFloor)
        val newMaxCap = (state.maxCapOz - levelsLost * state.vocation.capPerLevel).coerceAtLeast(minCapFloor)

        fun penalizeSkill(sp: SkillProgress): SkillProgress {
            if (sp.level <= 10) return sp.copy(points = sp.points * 0.90)
            val lostPts = sp.requiredPointsForNext() * 0.10
            return if (sp.points >= lostPts) sp.copy(points = sp.points - lostPts)
            else {
                val prev = SkillProgress(sp.level - 1, 0.0)
                prev.copy(points = (prev.requiredPointsForNext() - (lostPts - sp.points)).coerceAtLeast(0.0))
            }
        }
        val penalizedSkills = PlayerSkills(
            melee = penalizeSkill(state.skills.melee),
            distance = penalizeSkill(state.skills.distance),
            shielding = penalizeSkill(state.skills.shielding),
            magic = penalizeSkill(state.skills.magic),
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
            // Forward-only Classic Tibia delta growth so pre-20 Adventurer stats stay locked in after Oracle Ceremony
            val newMaxHp = s.maxHp + levelsGained * s.vocation.hpPerLevel
            val newMaxMp = s.maxMp + levelsGained * s.vocation.mpPerLevel
            val newMaxCap = s.maxCapOz + levelsGained * s.vocation.capPerLevel
            s = s.copy(level = lvl, maxHp = newMaxHp, hp = newMaxHp, maxMp = newMaxMp, mp = newMaxMp, maxCapOz = newMaxCap)
                .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat(), "LEVEL $lvl!", FloatingTextKind.SKILL_UP, nowMs)
                .addLogInternal("Advanced to Character Level $lvl! (+${levelsGained * s.vocation.hpPerLevel} HP, +${levelsGained * s.vocation.mpPerLevel} MP, +${(levelsGained * s.vocation.capPerLevel).toInt()} oz)")
            if (s.vocation == Vocation.ADVENTURER && prevLevel < ORACLE_MIN_LEVEL && lvl >= ORACLE_MIN_LEVEL) {
                s = s.addLogInternal("You have reached Level $ORACLE_MIN_LEVEL! Speak to The Oracle in Rookvale Temple to choose your vocation!")
            }
        }
        return s
    }

    fun advanceSkillPublic(state: GameWorldState, skill: SkillType, rawPoints: Double, nowMs: Long): GameWorldState {
        val rate = when (skill) {
            SkillType.MELEE -> state.vocation.meleeGainRate
            SkillType.DISTANCE -> state.vocation.distanceGainRate
            SkillType.SHIELDING -> state.vocation.shieldingGainRate
            SkillType.MAGIC -> state.vocation.magicGainRate
            SkillType.FISHING -> 1.25
            SkillType.MINING -> 1.30
        }
        fun bump(sp: SkillProgress): Pair<SkillProgress, Boolean> {
            var cur = sp.copy(points = sp.points + rawPoints * rate)
            var up = false
            while (cur.points >= cur.requiredPointsForNext()) {
                cur = SkillProgress(cur.level + 1, cur.points - cur.requiredPointsForNext())
                up = true
            }
            return cur to up
        }
        val (newSkills, leveled, newLevel) = when (skill) {
            SkillType.MELEE -> bump(state.skills.melee).let { Triple(state.skills.copy(melee = it.first), it.second, it.first.level) }
            SkillType.DISTANCE -> bump(state.skills.distance).let { Triple(state.skills.copy(distance = it.first), it.second, it.first.level) }
            SkillType.SHIELDING -> bump(state.skills.shielding).let { Triple(state.skills.copy(shielding = it.first), it.second, it.first.level) }
            SkillType.MAGIC -> bump(state.skills.magic).let { Triple(state.skills.copy(magic = it.first), it.second, it.first.level) }
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

    fun useOrEquipBackpackItem(state: GameWorldState, itemId: String, nowMs: Long): GameWorldState {
        if (!state.hasItem(itemId)) return state
        val def = ItemCatalog.get(itemId)
        return when (def.category) {
            ItemCategory.POTION_HP -> useHealthPotion(state, nowMs)
            ItemCategory.POTION_MP -> useManaPotion(state, nowMs)
            ItemCategory.RUNE_ATTACK -> useAttackRune(state, nowMs)
            ItemCategory.TOOL_ROPE -> useRope(state, nowMs)
            ItemCategory.TOOL_PICKAXE -> {
                val fl = WorldMaps.getFloor(state.floor)
                val vein = (-1..1).flatMap { dy -> (-1..1).map { dx -> (state.playerX + dx) to (state.playerY + dy) } }
                    .firstOrNull { (vx, vy) -> fl.tileAt(vx, vy).oreVeinKind() != null }
                if (vein != null) CraftingAndMiningEngine.mineOreNode(state, vein.first, vein.second, nowMs)
                else state.addLogInternal("Stand beside an Ore Vein and tap it to mine with your ${def.name}.")
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
                state.copy(hp = newHp, mp = newMp, backpack = removeItemPublic(state.backpack, itemId, 1))
                    .addFloatingInternal(state.floor, state.playerX.toFloat(), state.playerY.toFloat(), "+${newHp - state.hp} HP", FloatingTextKind.HEAL, nowMs)
                    .addLogInternal("Ate ${def.name}.")
            }
            ItemCategory.AMMO -> {
                state.copy(equipment = state.equipment.copy(ammoId = itemId))
                    .addLogInternal("Loaded ${def.name} into your Quiver slot.")
            }
            else -> {
                val slot = def.equipSlot ?: return state.addLogInternal("${def.name} can be smelted, forged, or sold to Alaric.")
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
                state.copy(equipment = newEquip, backpack = bp)
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

    fun lootAllFromCorpse(state: GameWorldState, corpseId: String, nowMs: Long): GameWorldState {
        val corpse = state.corpses.firstOrNull { it.id == corpseId } ?: return state
        var s = state
        var remainingGold = corpse.gold
        if (remainingGold > 0) {
            val affordableGold = (s.remainingCapOz() / 0.1).toInt().coerceAtMost(remainingGold)
            if (affordableGold > 0) {
                s = s.copy(gold = s.gold + affordableGold)
                remainingGold -= affordableGold
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
                if (s.remainingCapOz() >= neededWeight) {
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
            lootedCount > 0 -> "Looted $lootedCount item(s), but ran out of Cap (oz)!"
            else -> "Too heavy! Smelt ores or store items in your Depot."
        }
        return s.copy(
            corpses = updatedCorpses,
            activePanel = if (closePanel) ActivePanel.NONE else s.activePanel,
            inspectingCorpseId = if (closePanel) null else s.inspectingCorpseId
        ).addLogInternal(msg)
    }

    fun depositItem(state: GameWorldState, itemId: String): GameWorldState {
        if (!state.hasItem(itemId)) return state
        return removeCarriedItem(state, itemId, 1)
            .let { it.copy(depot = addItemPublic(it.depot, itemId, 1)) }
            .addLogInternal("Stored ${ItemCatalog.get(itemId).name} in Depot.")
    }

    fun withdrawItem(state: GameWorldState, itemId: String): GameWorldState {
        if (state.depot.none { it.itemId == itemId && it.count > 0 }) return state
        val def = ItemCatalog.get(itemId)
        val neededWeight = def.carriedWeightOz()
        if (state.remainingCapOz() < neededWeight) return state.addLogInternal("Not enough Cap (${String.format("%.1f", neededWeight)} oz needed).")
        val destNote = if (def.isTool) " into Tool Bag" else ""
        return addCarriedItem(state.copy(depot = removeItemPublic(state.depot, itemId, 1)), itemId, 1)
            .addLogInternal("Withdrew ${def.name}$destNote from Depot.")
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
            .addLogInternal("Brother Alden: 'May the light of Rookvale restore your body and spirit.'")
    }

    private fun setWalkPathToAdjacent(state: GameWorldState, tx: Int, ty: Int): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        val adj = listOf(tx to ty - 1, tx to ty + 1, tx - 1 to ty, tx + 1 to ty)
            .filter { (x, y) -> floorMap.tileAt(x, y).walkable }
            .minByOrNull { (x, y) -> abs(x - state.playerX) + abs(y - state.playerY) }
            ?: return state
        return state.copy(walkPath = findPath(state, adj.first, adj.second))
    }

    private fun findPath(state: GameWorldState, goalX: Int, goalY: Int): List<Pair<Int, Int>> {
        if (state.playerX == goalX && state.playerY == goalY) return emptyList()
        val floorMap = WorldMaps.getFloor(state.floor)
        val visited = HashSet<Int>(256)
        val parent = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val start = state.playerX to state.playerY
        queue.add(start)
        visited.add(start.second * floorMap.width + start.first)

        val monsterSet = state.monsters.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()
        val npcSet = WorldMaps.npcs.filter { it.floor == state.floor }.map { it.x to it.y }.toSet()

        var found = false
        var explored = 0
        while (queue.isNotEmpty() && explored < 420) {
            explored++
            val cur = queue.removeFirst()
            if (cur.first == goalX && cur.second == goalY) {
                found = true; break
            }
            for (dir in Direction.entries) {
                val nx = cur.first + dir.dx
                val ny = cur.second + dir.dy
                if (nx !in 0 until floorMap.width || ny !in 0 until floorMap.height) continue
                val key = ny * floorMap.width + nx
                if (key in visited) continue
                if (!floorMap.tileAt(nx, ny).walkable) continue
                val coord = nx to ny
                if (coord in monsterSet || coord in npcSet) continue
                visited.add(key)
                parent[coord] = cur
                queue.add(coord)
            }
        }
        if (!found) return emptyList()
        val path = mutableListOf<Pair<Int, Int>>()
        var curr: Pair<Int, Int>? = goalX to goalY
        while (curr != null && curr != start) {
            path.add(curr); curr = parent[curr]
        }
        return path.reversed()
    }

    fun addItemPublic(list: List<ItemStack>, itemId: String, amount: Int): List<ItemStack> {
        val def = ItemCatalog.get(itemId)
        val out = list.toMutableList()
        if (def.stackable) {
            val idx = out.indexOfFirst { it.itemId == itemId }
            if (idx >= 0) {
                out[idx] = out[idx].copy(count = out[idx].count + amount)
                return out
            }
            out.add(ItemStack(itemId, amount))
            return out
        }
        repeat(amount) { out.add(ItemStack(itemId, 1)) }
        return out
    }

    fun removeItemPublic(list: List<ItemStack>, itemId: String, amount: Int): List<ItemStack> {
        val out = list.toMutableList()
        var rem = amount
        val iter = out.listIterator()
        while (iter.hasNext() && rem > 0) {
            val cur = iter.next()
            if (cur.itemId == itemId) {
                if (cur.count <= rem) {
                    rem -= cur.count; iter.remove()
                } else {
                    iter.set(cur.copy(count = cur.count - rem)); rem = 0
                }
            }
        }
        return out
    }

    private fun pruneExpiredEffects(state: GameWorldState, nowMs: Long): GameWorldState {
        return state.copy(
            floatingTexts = state.floatingTexts.filter { nowMs - it.createdAtMs < 1350L },
            projectiles = state.projectiles.filter { nowMs - it.createdAtMs < it.durationMs }
        )
    }

    internal fun allocateEffectId(): Long = nextEffectId++

    private fun chebyshev(x1: Int, y1: Int, x2: Int, y2: Int): Int = max(abs(x1 - x2), abs(y1 - y2))
}

internal fun GameWorldState.addFloatingInternal(floor: Int, x: Float, y: Float, text: String, kind: FloatingTextKind, nowMs: Long): GameWorldState {
    val jitterX = Random.nextFloat() * 0.25f - 0.125f
    return copy(floatingTexts = (floatingTexts + FloatingText(GameEngine.allocateEffectId(), floor, x + jitterX, y, text, kind, nowMs)).takeLast(24))
}

internal fun GameWorldState.addProjectileInternal(floor: Int, fx: Float, fy: Float, tx: Float, ty: Float, kind: ProjectileKind, nowMs: Long, durationMs: Long = 260L): GameWorldState {
    return copy(projectiles = (projectiles + ProjectileEffect(GameEngine.allocateEffectId(), floor, fx, fy, tx, ty, kind, nowMs, durationMs)).takeLast(14))
}

internal fun GameWorldState.addLogInternal(message: String): GameWorldState {
    return copy(statusLog = (statusLog + message).takeLast(6))
}
