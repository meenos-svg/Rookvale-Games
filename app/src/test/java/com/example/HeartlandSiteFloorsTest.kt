package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.HEARTLAND_SITE_REBUILD_MAP_VERSION
import com.example.game.world.HeartlandSiteFloors
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartlandSiteFloorsTest {

    private fun reachable(floorId: Int, sx: Int, sy: Int): Set<Pair<Int, Int>> {
        val fl = WorldMaps.getFloor(floorId)
        val seen = HashSet<Pair<Int, Int>>()
        val q = ArrayDeque<Pair<Int, Int>>()
        seen += sx to sy; q.add(sx to sy)
        while (q.isNotEmpty()) {
            val (x, y) = q.removeFirst()
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val n = (x + dx) to (y + dy)
                if (n.first in 0 until fl.width && n.second in 0 until fl.height && n !in seen &&
                    fl.tileAt(n.first, n.second).walkable
                ) { seen += n; q.add(n) }
            }
        }
        return seen
    }

    @Test
    fun quarryMountainAndCragAreLargeFullyConnectedAndWellPopulated() {
        val monsters = WorldMaps.initialMonsters()
        val minMonsters = mapOf(-11 to 20, -12 to 20, -14 to 24)
        for (floorId in listOf(-11, -12, -14)) {
            val fl = WorldMaps.getFloor(floorId)
            assertEquals(HeartlandSiteFloors.SIZE, fl.width)
            assertEquals(HeartlandSiteFloors.SIZE, fl.height)

            val entry = WorldMaps.portals.first { it.fromFloor == 0 && it.toFloor == floorId }
            val exit = WorldMaps.portals.first { it.fromFloor == floorId && it.toFloor == 0 }
            val down = WorldMaps.portals.first { it.fromFloor == floorId && it.toFloor in -28..-16 }
            val back = WorldMaps.portals.first { it.toFloor == floorId && it.fromFloor in -28..-16 }
            val seen = reachable(floorId, entry.toX, entry.toY)

            var walkable = 0
            for (y in 0 until fl.height) for (x in 0 until fl.width) if (fl.tileAt(x, y).walkable) walkable++
            assertEquals("Floor $floorId has walkable tiles you can't reach", walkable, seen.size)
            assertTrue((exit.fromX to exit.fromY) in seen)
            assertTrue((down.fromX to down.fromY) in seen)
            assertTrue((back.toX to back.toY) in seen)
            assertTrue(fl.isProtectionZone(entry.toX, entry.toY))

            val here = monsters.filter { it.floor == floorId }
            assertTrue("Floor $floorId has only ${here.size} monsters", here.size >= minMonsters.getValue(floorId))
            here.forEach { m ->
                assertTrue((m.x to m.y) in seen)
                assertTrue("Rookvale stays Lv 1-20", m.level in 1..20)
            }
        }
        // The three layouts are genuinely different
        val shapes = listOf(-11, -12, -14).map { id ->
            val fl = WorldMaps.getFloor(id)
            (0 until fl.height).joinToString("") { y -> (0 until fl.width).joinToString("") { x -> if (fl.tileAt(x, y).walkable) "." else "#" } }
        }
        assertEquals(3, shapes.toSet().size)
    }

    @Test
    fun oldSavesInsideRebuiltFloorsResumeAtTheCampLanding() {
        for (floorId in listOf(-11, -12, -14)) {
            val hero = GameEngine.createNewWorld("Delver").copy(floor = floorId, playerX = 24, playerY = 40)
            val oldSave = GameEngine.toSaveData(hero).copy(mapVersion = HEARTLAND_SITE_REBUILD_MAP_VERSION - 1)
            val loaded = GameEngine.fromSaveData(oldSave)
            val (lx, ly) = WorldMaps.fallbackLandingForFloor(floorId)
            assertEquals(floorId, loaded.floor)
            assertEquals(lx to ly, loaded.playerX to loaded.playerY)
        }
    }
}
