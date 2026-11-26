package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.BOSS_CORPSE_LIFETIME_MS
import com.example.game.model.BOSS_RESPAWN_MS
import com.example.game.model.BossSpecialKind
import com.example.game.model.CORPSE_EXPIRY_WARNING_MS
import com.example.game.model.CorpseEntity
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterLootTable
import com.example.game.model.MonsterType
import com.example.game.model.ProjectileKind
import com.example.game.model.REGULAR_CORPSE_LIFETIME_MS
import com.example.game.model.TileType
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DungeonBossLootAndDespawnTest {

    private fun reachableTiles(floor: WorldFloor, startX: Int, startY: Int): Set<Pair<Int, Int>> {
        val visited = HashSet<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        if (!floor.tileAt(startX, startY).walkable) return emptySet()
        visited.add(startX to startY)
        queue.add(startX to startY)
        val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((dx, dy) in dirs) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until floor.width && ny in 0 until floor.height) {
                    val key = nx to ny
                    if (key !in visited && floor.tileAt(nx, ny).walkable) {
                        visited.add(key)
                        queue.add(key)
                    }
                }
            }
        }
        return visited
    }

    @Test
    fun floorMinusOneAndTwoMultiRoomDungeonsAreFullyConnectedWithBridgesGatesTorchesAndBosses() {
        val f1 = WorldMaps.getFloor(-1)
        val f1Portals = WorldMaps.portals.filter { it.fromFloor == -1 }
        assertTrue("Floor -1 must have portals", f1Portals.isNotEmpty())
        val f1Start = f1Portals.first().let { it.fromX to it.fromY }
        val f1Reachable = reachableTiles(f1, f1Start.first, f1Start.second)

        for (portal in f1Portals) {
            assertTrue(
                "Floor -1 portal (${portal.fromX},${portal.fromY}) must be reachable from (${f1Start.first},${f1Start.second})",
                (portal.fromX to portal.fromY) in f1Reachable
            )
        }
        // Floor -1 upper floor has zero bosses and connects via STAIRS_DOWN (24,34) -> Floor -16 Briar Brood Sanctum
        assertTrue("Upper Floor -1 must NOT host a boss directly", WorldMaps.monsterSpawns.none { it.floor == -1 && it.type.isBoss })
        val f1Descent = WorldMaps.portals.first { it.fromFloor == -1 && it.toFloor == -16 }
        assertTrue(
            "Floor -1 descent stairs (${f1Descent.fromX},${f1Descent.fromY}) to Lower Boss Floor -16 must be reachable",
            (f1Descent.fromX to f1Descent.fromY) in f1Reachable
        )
        val f16 = WorldMaps.getFloor(-16)
        val f16Reachable = reachableTiles(f16, f1Descent.toX, f1Descent.toY)
        val f16Boss = WorldMaps.monsterSpawns.first { it.floor == -16 && it.type == MonsterType.BROODMOTHER_ARACHNE }
        assertTrue(
            "Broodmother Arachne chamber (${f16Boss.x},${f16Boss.y}) on Lower Floor -16 must be reachable",
            (f16Boss.x to f16Boss.y) in f16Reachable
        )
        // Verify dungeon architectural tiles exist on Floor -1
        val f1Tiles = (0 until f1.height).flatMap { y -> (0 until f1.width).map { x -> f1.tileAt(x, y) } }.toSet()
        assertTrue("Floor -1 must contain STONE_BRIDGE", TileType.STONE_BRIDGE in f1Tiles)
        assertTrue("Floor -1 must contain IRON_BAR_GATE", TileType.IRON_BAR_GATE in f1Tiles)
        assertTrue("Floor -1 must contain TORCH_WALL", TileType.TORCH_WALL in f1Tiles)
        assertTrue("Floor -1 must contain WATER chasm", TileType.WATER in f1Tiles)

        val f2 = WorldMaps.getFloor(-2)
        val f2Portals = WorldMaps.portals.filter { it.fromFloor == -2 }
        assertTrue("Floor -2 must have portals", f2Portals.isNotEmpty())
        val f2Start = f2Portals.first().let { it.fromX to it.fromY }
        val f2Reachable = reachableTiles(f2, f2Start.first, f2Start.second)

        for (portal in f2Portals) {
            assertTrue(
                "Floor -2 portal (${portal.fromX},${portal.fromY}) must be reachable from (${f2Start.first},${f2Start.second})",
                (portal.fromX to portal.fromY) in f2Reachable
            )
        }
        // Floor -2 upper floor has zero bosses and connects via STAIRS_DOWN (24,12) -> Floor -21 Molten Pit Throne
        assertTrue("Upper Floor -2 must NOT host a boss directly", WorldMaps.monsterSpawns.none { it.floor == -2 && it.type.isBoss })
        val f2Descent = WorldMaps.portals.first { it.fromFloor == -2 && it.toFloor == -21 }
        assertTrue(
            "Floor -2 descent stairs (${f2Descent.fromX},${f2Descent.fromY}) to Lower Boss Floor -21 must be reachable",
            (f2Descent.fromX to f2Descent.fromY) in f2Reachable
        )
        val f21 = WorldMaps.getFloor(-21)
        val f21Reachable = reachableTiles(f21, f2Descent.toX, f2Descent.toY)
        val f21Boss = WorldMaps.monsterSpawns.first { it.floor == -21 && it.type == MonsterType.IGNIS_LORD_OF_THE_PIT }
        assertTrue(
            "Ignis Lord of the Pit chamber (${f21Boss.x},${f21Boss.y}) on Lower Floor -21 must be reachable",
            (f21Boss.x to f21Boss.y) in f21Reachable
        )
        val f2Tiles = (0 until f2.height).flatMap { y -> (0 until f2.width).map { x -> f2.tileAt(x, y) } }.toSet()
        assertTrue("Floor -2 must contain STONE_BRIDGE over lava", TileType.STONE_BRIDGE in f2Tiles)
        assertTrue("Floor -2 must contain IRON_BAR_GATE", TileType.IRON_BAR_GATE in f2Tiles)
        assertTrue("Floor -2 must contain TORCH_WALL", TileType.TORCH_WALL in f2Tiles)
        assertTrue("Floor -2 must contain LAVA chasm", TileType.LAVA in f2Tiles)

        // All Floor -1 and Floor -2 monster spawns must be reachable from their floor's entry portal
        WorldMaps.monsterSpawns.filter { it.floor == -1 }.forEach { sp ->
            assertTrue("F-1 monster ${sp.type} at (${sp.x},${sp.y}) must be reachable", (sp.x to sp.y) in f1Reachable)
        }
        WorldMaps.monsterSpawns.filter { it.floor == -2 }.forEach { sp ->
            assertTrue("F-2 monster ${sp.type} at (${sp.x},${sp.y}) must be reachable", (sp.x to sp.y) in f2Reachable)
        }
    }

    @Test
    fun weightedMonsterLootTableCoversAllNineteenSpeciesWithValidItemCatalogIds() {
        val rng = Random(42)
        for (type in MonsterType.entries) {
            val table = MonsterLootTable.entryFor(type)
            assertTrue("${type.name} minGp <= maxGp", table.minGp <= table.maxGp)
            table.guaranteedDrops.forEach { drop ->
                assertNotNull(
                    "${type.name} guaranteed drop '${drop.itemId}' must exist in ItemCatalog",
                    ItemCatalog.all[drop.itemId]
                )
            }
            table.weightedDrops.forEach { drop ->
                assertNotNull(
                    "${type.name} weighted drop '${drop.itemId}' must exist in ItemCatalog",
                    ItemCatalog.all[drop.itemId]
                )
                assertTrue("${type.name} dropChance in (0,1]", drop.dropChance > 0f && drop.dropChance <= 1f)
            }

            // Roll 15 times and verify gold range and item validity
            repeat(15) {
                val rolled = MonsterLootTable.rollLoot(type, rng)
                assertTrue(
                    "${type.name} gold ${rolled.gold} must be within ${table.minGp}..${table.maxGp}",
                    rolled.gold in table.minGp..table.maxGp
                )
                rolled.items.forEach { stack ->
                    assertNotNull("Rolled item '${stack.itemId}' must exist in ItemCatalog", ItemCatalog.all[stack.itemId])
                    assertTrue("Rolled count must be positive", stack.count > 0)
                }
            }
        }

        // Bosses guarantee rich gold and guaranteed drops
        val arachneLoot = MonsterLootTable.rollLoot(MonsterType.BROODMOTHER_ARACHNE, Random(7))
        assertTrue("Broodmother Arachne must drop substantial gold", arachneLoot.gold >= 110)
        assertTrue("Broodmother Arachne must drop guaranteed items", arachneLoot.items.isNotEmpty())

        val ignisLoot = MonsterLootTable.rollLoot(MonsterType.IGNIS_LORD_OF_THE_PIT, Random(7))
        assertTrue("Ignis must drop substantial gold", ignisLoot.gold >= 180)
        assertTrue("Ignis must drop guaranteed items", ignisLoot.items.isNotEmpty())
    }

    @Test
    fun allMonstersHaveRareHealthPotionDropChance() {
        // Verify rare drop chances scale cleanly with monster strength
        assertEquals(0.05f, MonsterLootTable.rareHealthPotionChance(MonsterType.CAVE_RAT), 0.0001f)
        assertEquals(0.07f, MonsterLootTable.rareHealthPotionChance(MonsterType.GHOUL), 0.0001f)
        assertEquals(0.09f, MonsterLootTable.rareHealthPotionChance(MonsterType.NECROMANCER), 0.0001f)
        assertEquals(0.14f, MonsterLootTable.rareHealthPotionChance(MonsterType.ELDER_DRAGON), 0.0001f)

        // Verify monsters without an explicit potion entry drop Health Potions rarely (not every kill, > 0 over 400 kills)
        val sampleSpecies = listOf(
            MonsterType.CAVE_RAT,
            MonsterType.SKELETON,
            MonsterType.GHOUL,
            MonsterType.NECROMANCER,
            MonsterType.ELDER_DRAGON
        )
        for (type in sampleSpecies) {
            val rng = Random(1234 + type.ordinal)
            var potionDrops = 0
            val trials = 400
            repeat(trials) {
                val rolled = MonsterLootTable.rollLoot(type, rng)
                if (rolled.items.any { it.itemId == "health_potion" }) {
                    potionDrops++
                }
            }
            assertTrue(
                "${type.name} should occasionally drop a rare Health Potion (got $potionDrops/$trials)",
                potionDrops in 3..(trials / 3)
            )
        }
    }

    @Test
    fun regularMonstersFrequentlyDropCommonGoldOnlyLootWhileBossesDropItems() {
        val regularSpecies = listOf(
            MonsterType.WILD_RABBIT,
            MonsterType.CAVE_RAT,
            MonsterType.OUTLAW_BANDIT,
            MonsterType.ORC_WARRIOR,
            MonsterType.GHOUL,
            MonsterType.DRAGON_HATCHLING
        )
        for (type in regularSpecies) {
            val rng = Random(999 + type.ordinal)
            var goldOnlyKills = 0
            var itemKills = 0
            val trials = 300
            repeat(trials) {
                val rolled = MonsterLootTable.rollLoot(type, rng)
                assertTrue("${type.name} should still drop gold", rolled.gold > 0)
                if (rolled.items.isEmpty()) goldOnlyKills++ else itemKills++
            }
            assertTrue(
                "${type.name} should commonly drop gold-only loot so not every kill is valuable (got $goldOnlyKills/$trials)",
                goldOnlyKills in (trials / 4)..((trials * 3) / 4)
            )
            assertTrue(
                "${type.name} should still drop items on other kills (got $itemKills/$trials)",
                itemKills in (trials / 4)..((trials * 3) / 4)
            )
        }

        // Bosses still reliably drop items alongside gold
        val bossRng = Random(55)
        repeat(20) {
            val bossLoot = MonsterLootTable.rollLoot(MonsterType.BROODMOTHER_ARACHNE, bossRng)
            assertTrue("Bosses must continue dropping guaranteed/bonus items", bossLoot.items.isNotEmpty())
        }
    }

    @Test
    fun broodmotherAndIgnisTelegraphDangerZonesBroadcastSlayAndRespawnOnFiveMinuteTimer() {
        // 1. Broodmother Arachne Venom Nova telegraph -> detonation inflicts POISON
        val arachne = MonsterEntity(
            id = 701,
            type = MonsterType.BROODMOTHER_ARACHNE,
            floor = -1,
            x = 24,
            y = 34,
            spawnX = 24,
            spawnY = 34,
            hp = MonsterType.BROODMOTHER_ARACHNE.maxHp,
            maxHp = MonsterType.BROODMOTHER_ARACHNE.maxHp,
            aoeCooldownTicks = 0,
            summonCooldownTicks = 10
        )
        assertEquals(BossSpecialKind.VENOM_NOVA, arachne.type.specialKind)
        val heroF1 = GameEngine.createNewWorld("Roland").copy(
            saveId = "p_host",
            floor = -1,
            playerX = 24,
            playerY = 32,
            hp = 450,
            maxHp = 450,
            monsters = listOf(arachne)
        )
        val tick1 = GameEngine.processCombatTick(heroF1, nowMs = 1_000L, isFloorHost = true)
        val boss1 = tick1.monsters.first { it.id == 701 }
        assertTrue("Broodmother Arachne must telegraph Venom Nova danger tiles", boss1.pendingAoeTiles.isNotEmpty())
        assertTrue("Player tile (24,32) must be inside Venom Nova radius", (24 to 32) in boss1.pendingAoeTiles)

        val tick2 = GameEngine.processCombatTick(tick1, nowMs = 2_000L, isFloorHost = true)
        assertTrue("Venom Nova telegraph clears after detonation", tick2.monsters.first { it.id == 701 }.pendingAoeTiles.isEmpty())
        assertTrue("Player caught in Venom Nova must take damage", tick2.hp < tick1.hp)
        assertTrue("Player caught in Venom Nova must be Poisoned", tick2.poisonTicks > 0)
        assertTrue(
            "Venom Nova must emit VENOM_GLOB projectiles",
            tick2.projectiles.any { it.kind == ProjectileKind.VENOM_GLOB }
        )

        // 2. Ignis Lord of the Pit Flame Burst telegraph -> detonation inflicts FIRE
        val ignis = MonsterEntity(
            id = 702,
            type = MonsterType.IGNIS_LORD_OF_THE_PIT,
            floor = -2,
            x = 24,
            y = 12,
            spawnX = 24,
            spawnY = 12,
            hp = MonsterType.IGNIS_LORD_OF_THE_PIT.maxHp,
            maxHp = MonsterType.IGNIS_LORD_OF_THE_PIT.maxHp,
            aoeCooldownTicks = 0,
            summonCooldownTicks = 10
        )
        assertEquals(BossSpecialKind.FLAME_BURST, ignis.type.specialKind)
        val heroF2 = GameEngine.createNewWorld("Roland").copy(
            saveId = "p_host",
            floor = -2,
            playerX = 24,
            playerY = 14,
            hp = 500,
            maxHp = 500,
            monsters = listOf(ignis)
        )
        val ignisTick1 = GameEngine.processCombatTick(heroF2, nowMs = 1_000L, isFloorHost = true)
        val ignisAfter1 = ignisTick1.monsters.first { it.id == 702 }
        assertTrue("Ignis must telegraph Flame Burst danger tiles", ignisAfter1.pendingAoeTiles.isNotEmpty())
        assertTrue("Player tile (24,14) must be inside Flame Burst danger zone", (24 to 14) in ignisAfter1.pendingAoeTiles)

        val ignisTick2 = GameEngine.processCombatTick(ignisTick1, nowMs = 2_000L, isFloorHost = true)
        assertTrue("Player caught in Flame Burst must take damage", ignisTick2.hp < ignisTick1.hp)
        assertTrue("Player caught in Flame Burst must be Burning", ignisTick2.fireTicks > 0)

        // 3. Slaying a Boss emits a [BOSS] slay alert, sets 180s corpse timer, and respawns after 5 minutes (300_000L ms)
        val killTimeMs = 10_000L
        val readyKill = heroF1.copy(
            playerX = 23,
            playerY = 34,
            targetMonsterId = 701,
            lastPlayerAttackMs = 0L,
            monsters = listOf(arachne.copy(hp = 1))
        )
        val afterKill = GameEngine.processPlayerAttackSubTick(readyKill, nowMs = killTimeMs)
        assertTrue("Slain Boss must be removed", afterKill.monsters.none { it.id == 701 })
        assertTrue(
            "Slaying a Boss must queue a [BOSS] system chat alert",
            afterKill.outboundSystemAlerts.any { it.contains("[BOSS]") && it.contains("Broodmother Arachne") }
        )
        val bossCorpse = afterKill.corpses.firstOrNull { it.label.contains(MonsterType.BROODMOTHER_ARACHNE.displayName) }
        assertNotNull("Slain Boss must drop a lootable corpse", bossCorpse)
        assertEquals(
            "Boss corpse lifetime must be 180_000 ms",
            killTimeMs + BOSS_CORPSE_LIFETIME_MS,
            bossCorpse!!.expiresAtMs
        )
        val pendingBoss = afterKill.pendingRespawns.firstOrNull { it.type == MonsterType.BROODMOTHER_ARACHNE }
        assertNotNull("Slain Boss must schedule PendingRespawn", pendingBoss)
        assertEquals(
            "Boss respawn timestamp must be fixed 5 minutes (300_000 ms)",
            killTimeMs + BOSS_RESPAWN_MS,
            pendingBoss!!.respawnAtMs
        )

        // Before 5 minutes: boss has not respawned yet
        val movedAway = afterKill.copy(playerX = 12, playerY = 12, outboundSystemAlerts = emptyList())
        val beforeTimer = GameEngine.processCombatTick(movedAway, nowMs = killTimeMs + BOSS_RESPAWN_MS - 1_000L, isFloorHost = true)
        assertTrue("Boss must NOT respawn before 5 minutes", beforeTimer.monsters.none { it.type == MonsterType.BROODMOTHER_ARACHNE })

        // At 5 minutes: boss respawns and queues a [BOSS] spawn announcement
        val atTimer = GameEngine.processCombatTick(beforeTimer, nowMs = killTimeMs + BOSS_RESPAWN_MS, isFloorHost = true)
        assertTrue("Boss must respawn once 5-minute timer completes", atTimer.monsters.any { it.type == MonsterType.BROODMOTHER_ARACHNE })
        assertTrue(
            "Boss respawn must emit a [BOSS] system alert",
            atTimer.outboundSystemAlerts.any { it.contains("[BOSS]") && it.contains("Broodmother Arachne") }
        )
    }

    @Test
    fun timedCorpseExpiryWarningAndAutoDespawnPreservePlayerDeathCorpse() {
        val t0 = 50_000L
        val regularCorpse = CorpseEntity(
            id = "c_reg",
            label = "Slain Cave Rat",
            floor = -1,
            x = 26,
            y = 13,
            gold = 9,
            items = listOf(ItemStack("cheese", 1)),
            isPlayerCorpse = false,
            createdAtMs = t0,
            expiresAtMs = t0 + REGULAR_CORPSE_LIFETIME_MS
        )
        val bossCorpse = CorpseEntity(
            id = "c_boss",
            label = "Slain Broodmother Arachne",
            floor = -1,
            x = 24,
            y = 34,
            gold = 220,
            items = listOf(ItemStack("health_potion", 2)),
            isPlayerCorpse = false,
            createdAtMs = t0,
            expiresAtMs = t0 + BOSS_CORPSE_LIFETIME_MS
        )
        val playerCorpse = CorpseEntity(
            id = "c_player",
            label = "Roland's Corpse",
            floor = -1,
            x = 25,
            y = 13,
            gold = 150,
            items = listOf(ItemStack("rope", 1)),
            isPlayerCorpse = true,
            createdAtMs = t0,
            expiresAtMs = 0L
        )

        // At 80s elapsed (10s left on regular corpse): regular isExpiringSoon == true, boss == false, player == false
        val tWarn = t0 + REGULAR_CORPSE_LIFETIME_MS - 10_000L
        assertTrue("Regular corpse under 15s remaining must trigger warning", regularCorpse.isExpiringSoon(tWarn))
        assertFalse("Boss corpse with 100s remaining must NOT trigger warning yet", bossCorpse.isExpiringSoon(tWarn))
        assertFalse("Player death corpse must never expire or warn", playerCorpse.isExpiringSoon(tWarn))
        assertNull("Player death corpse remainingMs must be null", playerCorpse.remainingMs(tWarn))
        assertEquals(CORPSE_EXPIRY_WARNING_MS, 15_000L)

        // Inspecting the regular corpse when it expires auto-removes the corpse and closes LOOT_CORPSE modal
        val inspectingState = GameEngine.createNewWorld("Roland").copy(
            floor = -1,
            playerX = 26,
            playerY = 12,
            monsters = emptyList(),
            corpses = listOf(regularCorpse, bossCorpse, playerCorpse),
            activePanel = ActivePanel.LOOT_CORPSE,
            inspectingCorpseId = regularCorpse.id
        )
        val afterRegularDespawn = GameEngine.processCombatTick(
            inspectingState,
            nowMs = t0 + REGULAR_CORPSE_LIFETIME_MS + 100L,
            isFloorHost = true
        )
        assertTrue("Expired regular corpse must be auto-removed", afterRegularDespawn.corpses.none { it.id == regularCorpse.id })
        assertTrue("Boss corpse must still remain at 90s", afterRegularDespawn.corpses.any { it.id == bossCorpse.id })
        assertTrue("Player recovery corpse must remain", afterRegularDespawn.corpses.any { it.id == playerCorpse.id })
        assertEquals("Inspecting expired corpse must close LOOT_CORPSE panel", ActivePanel.NONE, afterRegularDespawn.activePanel)
        assertNull("inspectingCorpseId must clear when inspected corpse vanishes", afterRegularDespawn.inspectingCorpseId)

        // At 180s+: Boss corpse also auto-despawns, while player recovery corpse stays persistent
        val afterBossDespawn = GameEngine.processCombatTick(
            afterRegularDespawn,
            nowMs = t0 + BOSS_CORPSE_LIFETIME_MS + 100L,
            isFloorHost = true
        )
        assertTrue("Expired boss corpse must be auto-removed after 180s", afterBossDespawn.corpses.none { it.id == bossCorpse.id })
        assertTrue("Player recovery corpse must remain persistent forever", afterBossDespawn.corpses.any { it.id == playerCorpse.id })
    }
}
