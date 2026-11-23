package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.GameEngine
import com.example.game.model.CURRENT_MAP_VERSION
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.OVERLAND_REGION_SIZE
import com.example.game.model.PlayerSaveData
import com.example.game.model.TileType
import com.example.game.model.WORLD_CHUNK_SIZE
import com.example.game.world.WorldMaps
import com.example.ui.screens.IsometricCamera
import com.example.ui.screens.MinimapCoordinateMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import kotlin.math.abs

class RegionalWorldAndCamera360Test {

    @Test
    fun chunked400x400OverlandStructureAndCulling() {
        val floor0 = WorldMaps.getFloor(0)
        assertEquals(OVERLAND_REGION_SIZE, floor0.width)
        assertEquals(OVERLAND_REGION_SIZE, floor0.height)
        assertEquals(DEFAULT_REGION_ID, floor0.regionId)
        assertEquals(WORLD_CHUNK_SIZE, floor0.chunkedGrid.chunkSize)
        assertEquals(13, floor0.chunkedGrid.chunksX)
        assertEquals(13, floor0.chunkedGrid.chunksY)
        assertEquals(169, floor0.chunkedGrid.totalChunks)

        // Rotated viewport culling on 400x400 must only cover a tiny subset of chunks per frame
        val cam = IsometricCamera()
        for (yaw in listOf(0f, 45f, 90f, 135f, 180f, 270f, 315f)) {
            cam.configure(
                viewportW = 1920f,
                viewportH = 1080f,
                camTileX = WorldMaps.TEMPLE_SPAWN_X.toFloat(),
                camTileY = WorldMaps.TEMPLE_SPAWN_Y.toFloat(),
                zoomFactor = 1.0f,
                cameraYawDeg = yaw
            )
            val bounds = cam.visibleTileBounds(1920f, 1080f, floor0.width, floor0.height, margin = 4)
            val chunks = floor0.visibleChunkBounds(bounds.minTx, bounds.maxTx, bounds.minTy, bounds.maxTy)
            val visibleChunkCount = (chunks.maxChunkX - chunks.minChunkX + 1) * (chunks.maxChunkY - chunks.minChunkY + 1)
            assertTrue("Yaw $yaw should cull to <= 16 chunks, got $visibleChunkCount", visibleChunkCount in 1..16)
            assertTrue("Player X must stay in visible bounds at yaw $yaw", WorldMaps.TEMPLE_SPAWN_X in bounds.minTx..bounds.maxTx)
            assertTrue("Player Y must stay in visible bounds at yaw $yaw", WorldMaps.TEMPLE_SPAWN_Y in bounds.minTy..bounds.maxTy)
        }
    }

    @Test
    fun camera360RoundTripPickingAndDepthOrderAtAllAngles() {
        val cam = IsometricCamera()
        val yawAngles = listOf(0f, 30f, 45f, 90f, 135f, 180f, 225f, 270f, 330f)
        val zooms = listOf(0.6f, 1.0f, 1.8f)

        for (yaw in yawAngles) {
            for (zoom in zooms) {
                cam.configure(1920f, 1080f, 196f, 192f, zoom, yaw)
                val probes = listOf(196 to 192, 190 to 188, 202 to 198, 188 to 196, 204 to 186)
                for ((tx, ty) in probes) {
                    val screen = cam.tileCenterToScreen(tx.toFloat(), ty.toFloat())
                    val (rx, ry) = cam.screenToWorldTile(screen.x, screen.y)
                    assertEquals("X round-trip mismatch at yaw=$yaw zoom=$zoom", tx, rx)
                    assertEquals("Y round-trip mismatch at yaw=$yaw zoom=$zoom", ty, ry)
                }

                // Dynamic depth order must strictly match screen-Y ordering
                val sA = cam.tileCenterToScreen(190f, 190f)
                val sB = cam.tileCenterToScreen(202f, 196f)
                val dA = cam.depthOf(190f, 190f)
                val dB = cam.depthOf(202f, 196f)
                if (abs(sA.y - sB.y) > 1f) {
                    assertEquals(sA.y < sB.y, dA < dB)
                }
            }
        }

        // Shortest-arc compass snap back to North (45°)
        assertEquals(35f, IsometricCamera.shortestAngleDeltaDeg(10f, 45f), 0.01f)
        assertEquals(65f, IsometricCamera.shortestAngleDeltaDeg(340f, 45f), 0.01f)
        assertEquals(-45f, IsometricCamera.shortestAngleDeltaDeg(90f, 45f), 0.01f)
    }

    @Test
    fun minimapTapMatchesRotatedYawAngles() {
        for (yaw in listOf(0f, 45f, 90f, 180f, 270f)) {
            val (cx, cy) = MinimapCoordinateMapper.screenTapToWorldTile(
                tapX = 100f, tapY = 100f,
                canvasW = 200f, canvasH = 200f,
                playerX = 196, playerY = 192,
                radius = 22, floorWidth = 400, floorHeight = 400,
                cameraYawDeg = yaw
            )
            assertEquals("Center tap must pick player X at yaw=$yaw", 196, cx)
            assertEquals("Center tap must pick player Y at yaw=$yaw", 192, cy)
        }
    }

    @Test
    fun rookvaleCityDistrictsHighwaysAndAllDungeonPortalsAreReachable() {
        val floor0 = WorldMaps.getFloor(0)
        assertTrue(
            "Temple spawn (${WorldMaps.TEMPLE_SPAWN_X}, ${WorldMaps.TEMPLE_SPAWN_Y}) must be walkable",
            floor0.tileAt(WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y).walkable
        )
        assertTrue(
            "Temple spawn must be in Protection Zone",
            floor0.isProtectionZone(WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y)
        )

        // Full overland BFS from Temple Spawn (196, 192)
        val visited = BooleanArray(floor0.width * floor0.height)
        val queue = ArrayDeque<Int>()
        val startKey = WorldMaps.TEMPLE_SPAWN_Y * floor0.width + WorldMaps.TEMPLE_SPAWN_X
        visited[startKey] = true
        queue.add(startKey)

        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val x = cur % floor0.width
            val y = cur / floor0.width
            for ((dx, dy) in dirs) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until floor0.width || ny !in 0 until floor0.height) continue
                val nKey = ny * floor0.width + nx
                if (visited[nKey]) continue
                if (!floor0.tileAt(nx, ny).walkable) continue
                visited[nKey] = true
                queue.add(nKey)
            }
        }

        fun isReachable(x: Int, y: Int): Boolean = visited[y * floor0.width + x]
        fun isAdjacentReachable(x: Int, y: Int): Boolean = dirs.any { (dx, dy) ->
            val nx = x + dx
            val ny = y + dy
            nx in 0 until floor0.width && ny in 0 until floor0.height && isReachable(nx, ny)
        }

        // 1. All City NPCs reachable
        WorldMaps.npcs.filter { it.floor == 0 }.forEach { npc ->
            assertTrue("NPC ${npc.name} at (${npc.x},${npc.y}) must be reachable", isAdjacentReachable(npc.x, npc.y))
        }

        // 2. All Key City Landmarks, Stations, Fountain, Orchard Trees & Harbor Fishing Piers
        assertTrue("Town Square Fountain reachable", isAdjacentReachable(196, 198))
        assertTrue("Depot Chest reachable", isAdjacentReachable(158, 182))
        assertTrue("Runecrafting Altar reachable", isAdjacentReachable(225, 179))
        assertTrue("Smelting Furnace reachable", isAdjacentReachable(219, 201))
        assertTrue("Blacksmith Anvil reachable", isAdjacentReachable(223, 201))
        assertTrue("Fletching Bench reachable", isAdjacentReachable(228, 201))
        assertTrue("Logging Orchard tree reachable", isAdjacentReachable(245, 204))
        assertTrue("West Harbor Fishing Pier reachable", isReachable(183, 231))
        assertTrue("East Harbor Fishing Pier reachable", isReachable(211, 231))

        // 3. All Four 400x400 Regional Border Highways reachable
        assertTrue("North Border Highway reachable", isReachable(196, 0))
        assertTrue("West Border Highway reachable", isReachable(0, 192))
        assertTrue("East Border Highway reachable", isReachable(399, 192))
        assertTrue("South Frontier Road reachable", isReachable(196, 399))

        // 4. All Floor 0 Dungeon Entrances (including direct Floor -2 Deep Abyssal Cavern!) reachable
        val floor0Portals = WorldMaps.portals.filter { it.fromFloor == 0 }
        assertTrue("Direct Floor 0 -> Floor -2 portal must exist", floor0Portals.any { it.toFloor == -2 })
        floor0Portals.forEach { p ->
            assertTrue("Portal (${p.fromX},${p.fromY}) -> Floor ${p.toFloor} must be reachable", isReachable(p.fromX, p.fromY))
            val destMap = WorldMaps.getFloor(p.toFloor)
            assertTrue("Portal dest (${p.toX},${p.toY}) on Floor ${p.toFloor} must be walkable", destMap.tileAt(p.toX, p.toY).walkable)
        }

        // 5. All Monster Spawns on Floor 0 are walkable and outside Protection Zone
        WorldMaps.monsterSpawns.filter { it.floor == 0 }.forEach { sp ->
            assertTrue("Monster ${sp.type} at (${sp.x},${sp.y}) must be on walkable tile", floor0.tileAt(sp.x, sp.y).walkable)
            assertFalse("Monster ${sp.type} at (${sp.x},${sp.y}) must NOT be inside PZ", floor0.isProtectionZone(sp.x, sp.y))
        }

        // 6. Cutaway building footprints exist for all major Rookvale landmarks
        assertTrue("Rookvale City should register building footprints for cutaway roofs", floor0.buildings.size >= 5)
        assertEquals(TileType.FOUNTAIN, floor0.tileAt(196, 198))
    }

    @Test
    fun legacySaveMigrationAndRegionIdPersistenceWorkSafely() {
        val base = GameEngine.toSaveData(GameEngine.createNewWorld("iDemon"))
        assertEquals(DEFAULT_REGION_ID, base.regionId)
        assertEquals(CURRENT_MAP_VERSION, base.mapVersion)

        // V2 save at old Temple (47, 46) migrates directly to new Temple (196, 192)
        val v2Save = base.copy(floor = 0, x = 47, y = 46, mapVersion = 2)
        val fromV2 = GameEngine.fromSaveData(v2Save)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X, fromV2.playerX)
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y, fromV2.playerY)
        assertEquals(DEFAULT_REGION_ID, fromV2.regionId)

        // Blocked tile (Town Square Fountain at 196, 198) gently nudges onto adjacent walkable plaza tile
        val blockedSave = base.copy(floor = 0, x = 196, y = 198, mapVersion = CURRENT_MAP_VERSION)
        val nudged = GameEngine.fromSaveData(blockedSave)
        val floor0 = WorldMaps.getFloor(0)
        assertTrue("Nudged tile must be walkable", floor0.tileAt(nudged.playerX, nudged.playerY).walkable)
        assertTrue("Nudged tile should stay right beside original spot", abs(nudged.playerX - 196) <= 2 && abs(nudged.playerY - 198) <= 2)

        // CloudSavePayloadCodec round-trips regionId and safely defaults when missing in legacy JSON
        val encoded = CloudSavePayloadCodec.encode(base)
        assertEquals(DEFAULT_REGION_ID, encoded.getString("regionId"))
        encoded.remove("regionId")
        val decoded = CloudSavePayloadCodec.decode(JSONObject(encoded.toString()), "slot_1", "iDemon")
        assertNotNull(decoded.regionId)
        assertEquals(DEFAULT_REGION_ID, decoded.regionId)
    }
}
