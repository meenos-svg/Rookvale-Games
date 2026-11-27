package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.Direction
import com.example.game.model.EquippedGear
import com.example.game.model.MonsterAiStyle
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.PlayerSkills
import com.example.game.model.ProjectileKind
import com.example.game.model.SkillProgress
import com.example.game.model.ThreatBand
import com.example.game.multiplayer.RemoteMonsterAttackEvent
import com.example.game.multiplayer.SyncedMonsterSnapshot
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterProgressionTest {

    @Test
    fun allTwentyFiveMonstersScaleAcrossSixTiersAndSpawnOnWalkableNonPzTiles() {
        assertEquals(70, MonsterType.entries.size)
        val tierAvgHp = (1..6).map { t ->
            MonsterType.entries.filter { it.tier == t && !it.isBoss }.map { it.maxHp }.average()
        }
        assertTrue("Tier 2 HP must exceed Tier 1", tierAvgHp[1] > tierAvgHp[0])
        assertTrue("Tier 3 HP must exceed Tier 2", tierAvgHp[2] > tierAvgHp[1])
        assertTrue("Tier 4 HP must exceed Tier 3", tierAvgHp[3] > tierAvgHp[2])
        assertTrue("Tier 5 Grimmaw HP must exceed classic Tier 4", tierAvgHp[4] > tierAvgHp[3])
        assertTrue("Tier 6 Gorek HP must exceed Tier 5 Grimmaw", tierAvgHp[5] > tierAvgHp[4])

        val tierAvgArmor = (1..6).map { t ->
            MonsterType.entries.filter { it.tier == t && !it.isBoss }.map { it.armor }.average()
        }
        assertTrue("Tier 2 armor must exceed Tier 1", tierAvgArmor[1] > tierAvgArmor[0])
        assertTrue("Tier 3 armor must exceed Tier 2", tierAvgArmor[2] > tierAvgArmor[1])
        assertTrue("Tier 4 armor must exceed Tier 3", tierAvgArmor[3] > tierAvgArmor[2])
        assertTrue("Tier 5 Grimmaw armor must exceed classic Tier 4", tierAvgArmor[4] > tierAvgArmor[3])
        assertTrue("Tier 6 Gorek armor must exceed Tier 5 Grimmaw", tierAvgArmor[5] > tierAvgArmor[4])

        // Every spawn coordinate across all floors must be walkable and outside Protection Zone
        val spawns = WorldMaps.initialMonsters()
        assertTrue("Must spawn Broodmother Arachne in Briar Lower Boss Lair (-16)", spawns.any { it.floor == -16 && it.type == MonsterType.BROODMOTHER_ARACHNE })
        assertTrue("Must spawn Ignis Lord of the Pit in Watchwood Lower Boss Lair (-21)", spawns.any { it.floor == -21 && it.type == MonsterType.IGNIS_LORD_OF_THE_PIT })
        assertTrue("Must spawn Demon Lord on Deep Delve Floor -3", spawns.any { it.floor == -3 && it.type == MonsterType.DEMON_LORD })
        assertTrue("Must spawn Elder Dragon on Deep Delve Floor -4", spawns.any { it.floor == -4 && it.type == MonsterType.ELDER_DRAGON })
        assertTrue("Must spawn Kragor the Stone-Eyed in Grimmaw Lower Boss Lair (-22)", spawns.any { it.floor == -22 && it.type == MonsterType.KRAGOR_THE_STONE_EYED })
        assertTrue("Must spawn Vaelgor the Labyrinth Warden in Gorek Lower Boss Lair (-23)", spawns.any { it.floor == -23 && it.type == MonsterType.VAELGOR_LABYRINTH_WARDEN })
        for (m in spawns) {
            val fl = WorldMaps.getFloor(m.floor)
            assertTrue(
                "Monster ${m.type.displayName} at (${m.floor}:${m.x},${m.y}) must spawn on walkable tile",
                fl.tileAt(m.x, m.y).walkable
            )
            assertFalse(
                "Monster ${m.type.displayName} at (${m.floor}:${m.x},${m.y}) must not spawn inside Protection Zone",
                fl.isProtectionZone(m.x, m.y)
            )
        }
    }

    @Test
    fun townPerimeterIsProtectionZoneAndDescendingTriggersWarningBannerAndLog() {
        val surface = WorldMaps.getFloor(0)
        // Temple, Depot & Town center are protected; outskirts are combat-active
        assertTrue(surface.isProtectionZone(WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y))
        assertTrue(surface.isProtectionZone(WorldMaps.ORACLE_X, WorldMaps.ORACLE_Y))
        assertFalse(surface.isProtectionZone(18, 48))

        // Locate Floor 0 -> Floor -2 Watchwood Caverns stairs down portal and step onto it
        val portalToF2 = WorldMaps.portals.first { it.fromFloor == 0 && it.toFloor == -2 && !it.requiresRope }
        val heroAboveF2 = GameEngine.createNewWorld("Roland").copy(
            floor = 0,
            playerX = portalToF2.fromX - 1,
            playerY = portalToF2.fromY
        )
        val descended = GameEngine.stepDirection(heroAboveF2, Direction.EAST, nowMs = 5000L)
        assertEquals(-2, descended.floor)
        val banner = descended.floorWarningBanner
        assertNotNull("Descending must trigger a floor warning banner", banner)
        assertEquals(-2, banner!!.floorId)
        assertEquals("Watchwood Caverns", banner.floorName)
        assertEquals("High", banner.dangerLevel)
        assertTrue(
            "Console log must contain descent danger warning",
            descended.statusLog.last().contains("You descend to Floor -2: Watchwood Caverns. Danger Level: High!")
        )

        // Banner automatically clears after 3.5 seconds
        val afterExpiry = GameEngine.processCombatTick(descended, nowMs = 9000L)
        assertNull("Floor warning banner must auto-dismiss after 3.5s", afterExpiry.floorWarningBanner)
    }

    @Test
    fun threatBandsAndArmorMitigationScaleCorrectly() {
        assertEquals(ThreatBand.EASY, MonsterType.CAVE_RAT.threatBandFor(playerLevel = 5))
        assertEquals(ThreatBand.EVEN, MonsterType.SWAMP_TROLL.threatBandFor(playerLevel = 4))
        assertEquals(ThreatBand.CHALLENGING, MonsterType.ORC_WARRIOR.threatBandFor(playerLevel = 6))
        assertEquals(ThreatBand.DEADLY, MonsterType.GRIMMAW_CRUSHER.threatBandFor(playerLevel = 5))
        assertEquals(ThreatBand.DEADLY, MonsterType.DEMON_LORD.threatBandFor(playerLevel = 25))
        assertEquals(ThreatBand.DEADLY, MonsterType.ELDER_DRAGON.threatBandFor(playerLevel = 30))

        // High-armor Grimmaw Crusher mitigates noticeably more physical damage than 0-armor Cave Rat,
        // while magic pierces 45% of monster armor
        var ratPhys = 0
        var crusherPhys = 0
        var crusherMagic = 0
        repeat(40) {
            ratPhys += GameEngine.mitigateMonsterDamage(25, MonsterType.CAVE_RAT.armor, isMagic = false)
            crusherPhys += GameEngine.mitigateMonsterDamage(25, MonsterType.GRIMMAW_CRUSHER.armor, isMagic = false)
            crusherMagic += GameEngine.mitigateMonsterDamage(25, MonsterType.GRIMMAW_CRUSHER.armor, isMagic = true)
        }
        assertTrue("Grimmaw Crusher armor must reduce physical damage vs Cave Rat", crusherPhys < ratPhys * 0.75)
        assertTrue("Magic must pierce Grimmaw Crusher armor better than physical", crusherMagic > crusherPhys)
    }

    @Test
    fun rangedKiterRetreatsFromMeleeAndFiresVisibleProjectileAtRange() {
        val (cx, cy) = TestArenas.openCenter(-1)
        val goblin = MonsterEntity(
            id = 501,
            type = MonsterType.GOBLIN_SCOUT,
            floor = -1,
            x = cx + 1,
            y = cy,
            spawnX = cx + 1,
            spawnY = cy,
            hp = MonsterType.GOBLIN_SCOUT.maxHp,
            maxHp = MonsterType.GOBLIN_SCOUT.maxHp
        )
        assertEquals(MonsterAiStyle.RANGED_KITE, goblin.type.aiStyle)

        // Adjacent (dist = 1): Goblin Scout retreats to dist 2
        val adjacentState = GameEngine.createNewWorld("Lyra").copy(
            floor = -1,
            playerX = cx,
            playerY = cy,
            monsters = listOf(goblin)
        )
        val afterRetreat = GameEngine.processCombatTick(adjacentState, nowMs = 1000L, isFloorHost = true)
        val movedGoblin = afterRetreat.monsters.first { it.id == 501 }
        val newDist = maxOf(kotlin.math.abs(movedGoblin.x - cx), kotlin.math.abs(movedGoblin.y - cy))
        assertTrue("Goblin Scout must kite away when player is adjacent (dist=$newDist)", newDist >= 2)

        // At optimal range (dist = 3): Goblin Scout holds position and fires THROWN_STONE + queues co-op attack
        val rangedGoblin = goblin.copy(x = cx + 3, y = cy)
        val rangedState = adjacentState.copy(monsters = listOf(rangedGoblin))
        val afterShot = GameEngine.processCombatTick(rangedState, nowMs = 2000L, isFloorHost = true)
        val heldGoblin = afterShot.monsters.first { it.id == 501 }
        assertEquals(cx + 3, heldGoblin.x)
        assertEquals(cy, heldGoblin.y)
        assertTrue(
            "Goblin Scout must fire visible THROWN_STONE projectile",
            afterShot.projectiles.any { it.kind == ProjectileKind.THROWN_STONE }
        )
        assertTrue(
            "Goblin Scout ranged shot must queue outbound monster attack for co-op sync",
            afterShot.outboundMonsterAttacks.any { it.projectileKindName == ProjectileKind.THROWN_STONE.name }
        )
    }

    @Test
    fun bossTelegraphsFireWaveDetonatesNextTickRespectsMaxTwoSummonsAndSyncsCoop() {
        val (cx, cy) = TestArenas.openCenter(-3)
        val boss = MonsterEntity(
            id = 900,
            type = MonsterType.DEMON_LORD,
            floor = -3,
            x = cx - 1,
            y = cy,
            spawnX = cx - 1,
            spawnY = cy,
            hp = MonsterType.DEMON_LORD.maxHp,
            maxHp = MonsterType.DEMON_LORD.maxHp,
            aoeCooldownTicks = 0,
            summonCooldownTicks = 0
        )
        val hero = GameEngine.createNewWorld("Roland").copy(
            saveId = "p_host",
            floor = -3,
            playerX = cx + 1,
            playerY = cy,
            hp = 400,
            maxHp = 400,
            monsters = listOf(boss),
            equipment = EquippedGear(armorId = "brass_armor"),
            skills = PlayerSkills(shielding = SkillProgress(10, 0.0))
        )

        // Tick 1: Boss telegraphs Fire Wave (pendingAoeTiles populated)
        val tick1 = GameEngine.processCombatTick(hero, nowMs = 1000L, isFloorHost = true)
        val bossAfter1 = tick1.monsters.first { it.id == 900 }
        assertTrue("Boss must telegraph pendingAoeTiles on tick 1", bossAfter1.pendingAoeTiles.isNotEmpty())
        assertTrue("Player tile should be inside east-aimed wave cone", (cx + 1 to cy) in bossAfter1.pendingAoeTiles)

        // Tick 2: Boss detonates Fire Wave -> clears telegraph, damages player in cone, sets Burning, broadcasts AoE event
        val tick2 = GameEngine.processCombatTick(tick1, nowMs = 2000L, isFloorHost = true)
        val bossAfter2 = tick2.monsters.first { it.id == 900 }
        assertTrue("Telegraph must clear after detonation", bossAfter2.pendingAoeTiles.isEmpty())
        assertTrue("Player standing in Fire Wave must take damage", tick2.hp < tick1.hp)
        assertTrue("Player hit by Fire Wave must be Burning", tick2.fireTicks > 0)
        assertTrue(
            "Boss detonation must broadcast aoeTiles in outboundMonsterAttacks",
            tick2.outboundMonsterAttacks.any { it.aoeTiles.isNotEmpty() }
        )

        // Summon cap: run ticks with summonCooldown=0 and verify Demon Lord never exceeds 2 active summons
        var summonState = hero.copy(
            monsters = listOf(boss.copy(aoeCooldownTicks = 20, summonCooldownTicks = 0))
        )
        repeat(4) { step ->
            summonState = summonState.copy(
                hp = 400,
                monsters = summonState.monsters.map {
                    if (it.id == 900) it.copy(aoeCooldownTicks = 20, summonCooldownTicks = 0) else it
                }
            )
            summonState = GameEngine.processCombatTick(summonState, nowMs = 3000L + step * 1000L, isFloorHost = true)
        }
        val summons = summonState.monsters.filter { it.isSummon && it.summonerId == 900 }
        assertEquals("Boss must cap active summons at 2", 2, summons.size)
        assertTrue("Demon Lord summons must be Fire Devils", summons.all { it.type == MonsterType.FIRE_DEVIL })

        // Killing a summon must NOT schedule a PendingRespawn
        val summonTarget = summons.first()
        val readyKill = summonState.copy(
            playerX = summonTarget.x - 1,
            playerY = summonTarget.y,
            targetMonsterId = summonTarget.id,
            lastPlayerAttackMs = 0L,
            monsters = summonState.monsters.map { if (it.id == summonTarget.id) it.copy(hp = 1) else it }
        )
        val afterKill = GameEngine.processPlayerAttackSubTick(readyKill, nowMs = 9000L)
        assertTrue("Slain summon must be removed", afterKill.monsters.none { it.id == summonTarget.id })
        assertTrue("Slain summon must NOT queue PendingRespawn", afterKill.pendingRespawns.isEmpty())
    }

    @Test
    fun legacyWireNamesAndRemoteMonsterAttackSyncWorkSafely() {
        assertEquals(MonsterType.ELDER_DRAGON, MonsterType.fromWireName("DRAGON_BOSS"))
        assertEquals(MonsterType.CAVE_RAT, MonsterType.fromWireName("RAT"))
        assertEquals(MonsterType.POISON_SPIDER, MonsterType.fromWireName("CAVE_SPIDER"))
        assertEquals(MonsterType.SWAMP_TROLL, MonsterType.fromWireName("STONEHORN_TROLL"))
        assertEquals(MonsterType.DEMON_LORD, MonsterType.fromWireName("DEMON_LORD"))
        assertNull(MonsterType.fromWireName("UNKNOWN_FOE"))

        // Non-host receives synced pendingAoeTiles via applyRemoteMonsterSync and AoE detonation via applyRemoteMonsterAttack
        val nonHost = GameEngine.createNewWorld("Lyra").copy(
            saveId = "p_client",
            floor = -3,
            playerX = 14,
            playerY = 14,
            hp = 350,
            maxHp = 350,
            monsters = emptyList()
        )
        val synced = GameEngine.applyRemoteMonsterSync(
            state = nonHost,
            floor = -3,
            snapshots = listOf(
                SyncedMonsterSnapshot(
                    id = 900,
                    typeName = MonsterType.DEMON_LORD.name,
                    floor = -3,
                    x = 12,
                    y = 14,
                    spawnX = 12,
                    spawnY = 14,
                    hp = 800,
                    maxHp = 860,
                    targetPlayerId = "p_client",
                    isSummon = false,
                    pendingAoeTiles = listOf(13 to 14, 14 to 14)
                )
            ),
            localPlayerId = "p_client",
            nowMs = 1000L
        )
        assertEquals(listOf(13 to 14, 14 to 14), synced.monsters.first().pendingAoeTiles)

        val afterRemoteAoe = GameEngine.applyRemoteMonsterAttack(
            state = synced,
            event = RemoteMonsterAttackEvent(
                monsterId = 900,
                floor = -3,
                fromX = 12,
                fromY = 14,
                toX = 14,
                toY = 14,
                projectileKindName = ProjectileKind.FIRE_BREATH.name,
                aoeTiles = listOf(13 to 14, 14 to 14),
                targetPlayerId = "p_client",
                inflictedStatus = "FIRE"
            ),
            localPlayerId = "p_client",
            nowMs = 2000L
        )
        assertTrue("Non-host standing in remote Boss AoE must take damage", afterRemoteAoe.hp < synced.hp)
        assertTrue("Non-host hit by remote Boss AoE must be Burning", afterRemoteAoe.fireTicks > 0)
        assertTrue("Non-host must render synced AoE projectiles", afterRemoteAoe.projectiles.isNotEmpty())
    }
}
