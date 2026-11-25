package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.GameEngine
import com.example.game.model.CURRENT_MAP_VERSION
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.OVERLAND_REGION_SIZE
import com.example.game.model.PlayerSaveData
import com.example.game.model.TileType
import com.example.game.model.WORLD_CHUNK_SIZE
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import com.example.game.world.WorldMaps
import com.example.ui.screens.IsometricCamera
import com.example.ui.screens.MinimapCoordinateMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RegionalWorldAndCamera360Test {

    @Test
    fun chunked400x400OverlandStructureAndCulling() {
        val floor0 = WorldMaps.getFloor(0)
        assertEquals(WORLD_OVERLAND_WIDTH, floor0.width)
        assertEquals(WORLD_OVERLAND_HEIGHT, floor0.height)
        assertEquals(DEFAULT_REGION_ID, floor0.regionId)
        assertEquals(WORLD_CHUNK_SIZE, floor0.chunkedGrid.chunkSize)
        assertEquals(50, floor0.chunkedGrid.chunksX)
        assertEquals(38, floor0.chunkedGrid.chunksY)
        assertEquals(1900, floor0.chunkedGrid.totalChunks)

        // Rotated viewport culling on 1600x1200 must only cover a tiny subset of chunks per frame
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

        val ox = WorldMaps.ROOKVALE_ORIGIN_X
        val oy = WorldMaps.ROOKVALE_ORIGIN_Y
        for (yaw in yawAngles) {
            for (zoom in zooms) {
                cam.configure(1920f, 1080f, (ox + 196).toFloat(), (oy + 192).toFloat(), zoom, yaw)
                val probes = listOf(
                    (ox + 196) to (oy + 192),
                    (ox + 190) to (oy + 188),
                    (ox + 202) to (oy + 198),
                    (ox + 188) to (oy + 196),
                    (ox + 204) to (oy + 186)
                )
                for ((tx, ty) in probes) {
                    val screen = cam.tileCenterToScreen(tx.toFloat(), ty.toFloat())
                    val (rx, ry) = cam.screenToWorldTile(screen.x, screen.y)
                    assertEquals("X round-trip mismatch at yaw=$yaw zoom=$zoom", tx, rx)
                    assertEquals("Y round-trip mismatch at yaw=$yaw zoom=$zoom", ty, ry)
                }

                // Dynamic depth order must strictly match screen-Y ordering
                val sA = cam.tileCenterToScreen((ox + 190).toFloat(), (oy + 190).toFloat())
                val sB = cam.tileCenterToScreen((ox + 202).toFloat(), (oy + 196).toFloat())
                val dA = cam.depthOf((ox + 190).toFloat(), (oy + 190).toFloat())
                val dB = cam.depthOf((ox + 202).toFloat(), (oy + 196).toFloat())
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
                playerX = WorldMaps.TEMPLE_SPAWN_X, playerY = WorldMaps.TEMPLE_SPAWN_Y,
                radius = 22, floorWidth = WORLD_OVERLAND_WIDTH, floorHeight = WORLD_OVERLAND_HEIGHT,
                cameraYawDeg = yaw
            )
            assertEquals("Center tap must pick player X at yaw=$yaw", WorldMaps.TEMPLE_SPAWN_X, cx)
            assertEquals("Center tap must pick player Y at yaw=$yaw", WorldMaps.TEMPLE_SPAWN_Y, cy)
        }
    }

    @Test
    fun rookvaleCityDistrictsHighwaysAndAllDungeonPortalsAreReachable() {
        val floor0 = WorldMaps.getFloor(0)
        val ox = WorldMaps.ROOKVALE_ORIGIN_X
        val oy = WorldMaps.ROOKVALE_ORIGIN_Y
        assertTrue(
            "Temple spawn (${WorldMaps.TEMPLE_SPAWN_X}, ${WorldMaps.TEMPLE_SPAWN_Y}) must be walkable",
            floor0.tileAt(WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y).walkable
        )
        assertTrue(
            "Temple spawn must be in Protection Zone",
            floor0.isProtectionZone(WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y)
        )

        // Full 400x400 Rookvale Valley heartland BFS from Temple Spawn (1396, 592)
        val size = OVERLAND_REGION_SIZE
        val visited = BooleanArray(size * size)
        val queue = ArrayDeque<Int>()
        val startLx = WorldMaps.TEMPLE_SPAWN_X - ox
        val startLy = WorldMaps.TEMPLE_SPAWN_Y - oy
        val startKey = startLy * size + startLx
        visited[startKey] = true
        queue.add(startKey)

        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val lx = cur % size
            val ly = cur / size
            for ((dx, dy) in dirs) {
                val nlx = lx + dx
                val nly = ly + dy
                if (nlx !in 0 until size || nly !in 0 until size) continue
                val nKey = nly * size + nlx
                if (visited[nKey]) continue
                if (!floor0.tileAt(ox + nlx, oy + nly).walkable) continue
                visited[nKey] = true
                queue.add(nKey)
            }
        }

        fun isReachable(gx: Int, gy: Int): Boolean {
            val lx = gx - ox
            val ly = gy - oy
            return lx in 0 until size && ly in 0 until size && visited[ly * size + lx]
        }
        fun isAdjacentReachable(gx: Int, gy: Int): Boolean = dirs.any { (dx, dy) ->
            isReachable(gx + dx, gy + dy)
        }

        // 1. All Rookvale Valley NPCs reachable
        WorldMaps.npcs.filter { it.floor == 0 && it.x in ox until (ox + size) && it.y in oy until (oy + size) }.forEach { npc ->
            assertTrue("NPC ${npc.name} at (${npc.x},${npc.y}) must be reachable", isAdjacentReachable(npc.x, npc.y))
        }

        // 2. All Key City Landmarks, Stations, Fountain, Orchard Trees & Harbor Fishing Piers
        assertTrue("Town Square Fountain reachable", isAdjacentReachable(ox + 196, oy + 198))
        assertTrue("Depot Chest reachable", isAdjacentReachable(ox + 158, oy + 182))
        assertTrue("Runecrafting Altar reachable", isAdjacentReachable(ox + 225, oy + 179))
        assertTrue("Smelting Furnace reachable", isAdjacentReachable(ox + 219, oy + 201))
        assertTrue("Blacksmith Anvil reachable", isAdjacentReachable(ox + 223, oy + 201))
        assertTrue("Fletching Bench reachable", isAdjacentReachable(ox + 228, oy + 201))
        assertTrue("Logging Orchard tree reachable", isAdjacentReachable(ox + 245, oy + 204))
        assertTrue("West Harbor Fishing Pier reachable", isReachable(ox + 183, oy + 231))
        assertTrue("East Harbor Fishing Pier reachable", isReachable(ox + 211, oy + 231))

        // 3. All Four 400x400 Regional Border Highways reachable
        assertTrue("North Border Highway reachable", isReachable(ox + 196, oy + 0))
        assertTrue("West Border Highway reachable", isReachable(ox + 0, oy + 192))
        assertTrue("East Border Highway reachable", isReachable(ox + 399, oy + 192))
        assertTrue("South Frontier Road reachable", isReachable(ox + 196, oy + 399))

        // 4. All Rookvale Heartland Dungeon Entrances (including direct Floor -2 Deep Abyssal Cavern!) reachable
        val heartlandPortals = WorldMaps.portals.filter {
            it.fromFloor == 0 && it.fromX in ox until (ox + size) && it.fromY in oy until (oy + size)
        }
        assertTrue("Direct Floor 0 -> Floor -2 portal must exist", heartlandPortals.any { it.toFloor == -2 })
        heartlandPortals.forEach { p ->
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
        assertEquals(TileType.FOUNTAIN, floor0.tileAt(ox + 196, oy + 198))
    }

    @Test
    fun legacySaveMigrationAndRegionIdPersistenceWorkSafely() {
        val base = GameEngine.toSaveData(GameEngine.createNewWorld("iDemon"))
        assertEquals(DEFAULT_REGION_ID, base.regionId)
        assertEquals(CURRENT_MAP_VERSION, base.mapVersion)

        // V2 save at old Temple (47, 46) migrates directly to V4 Temple (1396, 592)
        val v2Save = base.copy(floor = 0, x = 47, y = 46, mapVersion = 2)
        val fromV2 = GameEngine.fromSaveData(v2Save)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X, fromV2.playerX)
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y, fromV2.playerY)
        assertEquals(DEFAULT_REGION_ID, fromV2.regionId)

        // V3 save at old 400x400 Temple (196, 192) migrates seamlessly by (+1200, +400) to (1396, 592)
        val v3Save = base.copy(floor = 0, x = 196, y = 192, mapVersion = 3)
        val fromV3 = GameEngine.fromSaveData(v3Save)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X, fromV3.playerX)
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y, fromV3.playerY)
        assertEquals(DEFAULT_REGION_ID, fromV3.regionId)

        // Blocked tile (Town Square Fountain at 1396, 598) gently nudges onto adjacent walkable plaza tile
        val fx = WorldMaps.ROOKVALE_ORIGIN_X + 196
        val fy = WorldMaps.ROOKVALE_ORIGIN_Y + 198
        val blockedSave = base.copy(floor = 0, x = fx, y = fy, mapVersion = CURRENT_MAP_VERSION)
        val nudged = GameEngine.fromSaveData(blockedSave)
        val floor0 = WorldMaps.getFloor(0)
        assertTrue("Nudged tile must be walkable", floor0.tileAt(nudged.playerX, nudged.playerY).walkable)
        assertTrue("Nudged tile should stay right beside original spot", abs(nudged.playerX - fx) <= 2 && abs(nudged.playerY - fy) <= 2)

        // CloudSavePayloadCodec round-trips regionId and safely defaults when missing in legacy JSON
        val encoded = CloudSavePayloadCodec.encode(base)
        assertEquals(DEFAULT_REGION_ID, encoded.getString("regionId"))
        encoded.remove("regionId")
        val decoded = CloudSavePayloadCodec.decode(JSONObject(encoded.toString()), "slot_1", "iDemon")
        assertNotNull(decoded.regionId)
        assertEquals(DEFAULT_REGION_ID, decoded.regionId)
    }
}
