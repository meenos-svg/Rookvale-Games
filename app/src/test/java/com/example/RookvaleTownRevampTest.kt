package com.example

import com.example.game.model.TileType
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RookvaleTownRevampTest {
    private val ox = WorldMaps.ROOKVALE_ORIGIN_X
    private val oy = WorldMaps.ROOKVALE_ORIGIN_Y
    private val floor0 = WorldMaps.getFloor(0)

    @Test
    fun townHasNoPerimeterWall() {
        val wallish = setOf(TileType.HEDGE, TileType.WOOD_FENCE)
        val northEdge = (146..256).count { floor0.tileAt(ox + it, oy + 148) in wallish }
        val westEdge = (149..226).count { floor0.tileAt(ox + 146, oy + it) in wallish }
        val eastEdge = (149..226).count { floor0.tileAt(ox + 256, oy + it) in wallish }
        assertEquals(0, northEdge + westEdge + eastEdge)
    }

    @Test
    fun theRiverIsGoneAndAFishingPondSitsSouthOfTown() {
        // No water left along the old river line away from the pond
        for (x in listOf(10, 60, 120, 150, 240, 280, 330)) {
            for (y in 220..250) {
                assertFalse("Old river water at local ($x,$y)", floor0.tileAt(ox + x, oy + y) == TileType.WATER)
            }
        }
        // The pond has plenty of open water
        var water = 0
        for (y in 230..265) for (x in 165..228) if (floor0.tileAt(ox + x, oy + y) == TileType.WATER) water++
        assertTrue("Pond should have lots of water, had $water", water > 600)

        // Piers reach the water and the whole pond park is a safe zone for fishing
        for ((x, y) in listOf(183 to 243, 210 to 243, 173 to 247, 219 to 247, 196 to 240)) {
            assertTrue(floor0.tileAt(ox + x, oy + y).walkable)
            assertTrue("($x,$y) should be in the safe zone", floor0.isProtectionZone(ox + x, oy + y))
            val nearWater = (-2..2).any { dy -> (-2..2).any { dx -> floor0.tileAt(ox + x + dx, oy + y + dy) == TileType.WATER } }
            assertTrue("($x,$y) should be close enough to water to fish", nearWater)
        }
        assertTrue(WorldMaps.monsterSpawns.none { it.floor == 0 && floor0.isProtectionZone(it.x, it.y) })
    }
}
