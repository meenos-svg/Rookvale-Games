package com.example

import com.example.game.engine.GameEngine
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoutePlannerTest {

    private fun assertContiguousFrom(sx: Int, sy: Int, path: List<Pair<Int, Int>>) {
        var px = sx
        var py = sy
        val floor0 = WorldMaps.getFloor(0)
        for ((x, y) in path) {
            assertEquals("Route must move one tile at a time", 1, abs(x - px) + abs(y - py))
            assertTrue("Route tile ($x,$y) must be walkable", floor0.tileAt(x, y).walkable)
            px = x
            py = y
        }
    }

    @Test
    fun mapWalkPlansTheWholeRouteAndArrives() {
        val start = GameEngine.createNewWorld("Walker")
        var s = GameEngine.walkToCoordinate(start, 1042, 592) // Spirewood Crossing
        assertTrue(s.walkPath.size > 100)
        assertContiguousFrom(start.playerX, start.playerY, s.walkPath)

        var t = 0L
        var ticks = 0
        while ((s.walkPath.isNotEmpty() || s.longRangeGoal != null) && ticks < 3000) {
            t += 500L
            s = GameEngine.stepAutoFollowOrPath(s.copy(lastPlayerMoveMs = 0L), t)
            ticks++
        }
        assertTrue("Should arrive near Spirewood, was at (${s.playerX},${s.playerY})", abs(s.playerX - 1042) + abs(s.playerY - 592) <= 3)
    }

    @Test
    fun overseasDestinationsSendYouToAFerryCaptain() {
        val start = GameEngine.createNewWorld("Sailor")
        val s = GameEngine.walkToCoordinate(start, 960, 995) // Sunleaf, across the sea
        val goal = s.longRangeGoal!!
        assertTrue(WorldBiomeBlueprint.harborPorts.any { abs(it.dockX - goal.first) + abs(it.dockY - goal.second) <= 4 })
        assertContiguousFrom(start.playerX, start.playerY, s.walkPath)
        assertTrue(s.statusLog.last().contains("ferry"))
    }
}
