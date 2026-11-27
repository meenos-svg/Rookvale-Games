package com.example

import com.example.game.model.MonsterType
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rookvale (surface + every dungeon beneath it) is a Lv 1-20 region; deep floors may host bosses. */
class RookvaleLevelBandsTest {

    private val monsters by lazy { WorldMaps.initialMonsters() }

    @Test
    fun deepDelveChainClimbsSteadilyThroughLevels5To20() {
        val expected = mapOf(-1 to 5..9, -2 to 9..13, -3 to 13..17, -4 to 16..20)
        for ((floorId, range) in expected) {
            val regular = monsters.filter { it.floor == floorId && !it.type.isBoss }
            assertTrue("Floor $floorId must have monsters", regular.isNotEmpty())
            regular.forEach { m -> assertTrue("Floor $floorId ${m.type} level ${m.level} must be in $range", m.level in range) }
        }
        // Boss lairs under -1/-2 follow their parent floor
        assertEquals(10, monsters.first { it.type == MonsterType.BROODMOTHER_ARACHNE }.level)
        assertEquals(14, monsters.first { it.type == MonsterType.IGNIS_LORD_OF_THE_PIT }.level)
    }

    @Test
    fun deepFloorsHostTheirBossesWithinTheLevel20Cap() {
        val demon = monsters.filter { it.floor == -3 && it.type.isBoss }
        val dragon = monsters.filter { it.floor == -4 && it.type.isBoss }
        assertEquals(listOf(MonsterType.DEMON_LORD), demon.map { it.type })
        assertEquals(listOf(MonsterType.ELDER_DRAGON), dragon.map { it.type })
        assertEquals(18, demon.single().level)
        assertEquals(BiomeProgressionRules.ROOKVALE_MAX_LEVEL, dragon.single().level)
        assertTrue("Shallow floors keep bosses in their lairs", monsters.none { it.floor in -2..-1 && it.type.isBoss })
    }

    @Test
    fun surfaceAroundRookvaleOffersPlentyOfBeginnerMonsters() {
        val valley = monsters.filter { it.floor == 0 && WorldMaps.regionForGlobal(it.x, it.y).regionId == "rookvale_valley" }
        assertTrue("Rookvale valley must be populated", valley.size >= 200)
        valley.forEach { m -> assertTrue("${m.type} level ${m.level} must be within 1..20", m.level in 1..20) }
        assertTrue("No bosses roam the Rookvale surface", valley.none { it.type.isBoss })

        val beginners = valley.count { it.level <= 5 }
        val mid = valley.count { it.level in 6..12 }
        val high = valley.count { it.level >= 13 }
        assertTrue("Level 1 monsters must exist right outside the gates", valley.any { it.level == 1 })
        assertTrue("Beginner (Lv 1-5) monsters must be a large share: $beginners/${valley.size}", beginners * 4 >= valley.size)
        assertTrue("Lv 13-20 must not dominate the valley ($high vs $beginners beginners, $mid mid)", high < beginners && high < mid)
    }
}
