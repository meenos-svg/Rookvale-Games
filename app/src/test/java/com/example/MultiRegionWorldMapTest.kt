package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.CANONICAL_OVERLAND_REGION_IDS
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.OreVeinKind
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import com.example.game.model.isRegionCompatible
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import com.example.game.world.WorldRegionMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiRegionWorldMapTest {

    @Test
    fun twelveRegionWorldDimensionsStreamingLruCapAndContinentalBiomes() {
        val floor0 = WorldMaps.getFloor(0)
        assertEquals(WORLD_OVERLAND_WIDTH, floor0.width)
        assertEquals(WORLD_OVERLAND_HEIGHT, floor0.height)
        assertEquals(12, WorldRegionMeta.ALL_REGIONS.size)
        assertEquals(12, CANONICAL_OVERLAND_REGION_IDS.size)

        // Verify all 12 400x400 regions resolve deterministically from their center tile
        for (region in WorldRegionMeta.ALL_REGIONS) {
            val cx = region.originX + 200
            val cy = region.originY + 200
            assertEquals(region.regionId, WorldMaps.regionForGlobal(cx, cy).regionId)
        }

        // Stream chunks across the entire 1600x1200 continent grid and ensure LRU stays strictly bounded <= 180
        val discoveredBiomes = mutableSetOf<TileType>()
        for (gy in 10 until WORLD_OVERLAND_HEIGHT step 29) {
            for (gx in 10 until WORLD_OVERLAND_WIDTH step 29) {
                discoveredBiomes += floor0.tileAt(gx, gy)
            }
        }
        assertTrue(
            "Streaming chunk cache must stay capped at <= 180 chunks, was ${floor0.chunkedGrid.cachedChunkCount}",
            floor0.chunkedGrid.cachedChunkCount <= 180
        )

        // All signature biomes matching 4308.jpg must exist across the continents
        val expectedBiomes = listOf(
            TileType.SAND,
            TileType.MARSH,
            TileType.SNOW,
            TileType.JUNGLE_FLOOR,
            TileType.VOLCANIC_ASH,
            TileType.BLIGHT_SOIL,
            TileType.DEEP_WATER,
            TileType.GRASS
        )
        for (biome in expectedBiomes) {
            assertTrue("Continent map must contain biome $biome", biome in discoveredBiomes)
        }
    }

    @Test
    fun allSixHarborFerriesAndFiveSeaBridgesWorkCleanly() {
        val floor0 = WorldMaps.getFloor(0)
        assertEquals(6, WorldBiomeBlueprint.harborPorts.size)

        // Every Harbor Port landing tile is walkable and connects via boardHarborFerry
        var state = GameEngine.createNewWorld("Navigator")
        val nowMs = 1_750_000_000_000L
        for (port in WorldBiomeBlueprint.harborPorts) {
            assertTrue(
                "Harbor ${port.name} arrival (${port.arrivalX}, ${port.arrivalY}) must be walkable",
                floor0.tileAt(port.arrivalX, port.arrivalY).walkable
            )
            // Board from Rookvale Harbor to target port
            state = state.copy(activePanel = ActivePanel.HARBOR_FERRY, activeHarborPortId = "rookvale_harbor")
            val sailed = GameEngine.boardHarborFerry(state, port.id, nowMs)
            assertEquals(0, sailed.floor)
            assertEquals(port.regionId, sailed.regionId)
            assertEquals(port.arrivalX, sailed.playerX)
            assertEquals(port.arrivalY, sailed.playerY)
            assertEquals(ActivePanel.NONE, sailed.activePanel)
        }

        // All 5 inter-continental sea-bridges are walkable end-to-end across water
        val bridgeSpans = listOf(
            Triple("Frostbay Sea-Bridge", 440 to 211, 868 to 211),
            Triple("Central Alder Causeway", 595 to 591, 946 to 591),
            Triple("Sunspire-Jungle Strait Bridge", 450 to 979, 652 to 979),
            Triple("Spirewood-Sunleaf Causeway", 1041 to 755, 1041 to 992),
            Triple("Rookvale-Ember Isles Causeway", 1394 to 951, 1446 to 951)
        )
        for ((name, startPt, endPt) in bridgeSpans) {
            val (x1, y1) = startPt
            val (x2, y2) = endPt
            val steps = maxOf(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1)).coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toDouble() / steps.toDouble()
                val bx = (x1 + (x2 - x1) * t).toInt()
                val by = (y1 + (y2 - y1) * t).toInt()
                assertTrue(
                    "Bridge $name tile ($bx, $by) must be walkable",
                    floor0.tileAt(bx, by).walkable
                )
            }
        }
    }

    @Test
    fun overworldKeepsOnlyLowTierOresWhileDungeonsHoldVesperiteAndCinderite() {
        val ores = WorldMaps.initialOreNodes().values
        val overworldOres = ores.filter { it.floor == 0 }
        val dungeonOres = ores.filter { it.floor < 0 }

        assertTrue("Overworld must spawn Copper, Tin, and Bog Iron ores", overworldOres.size >= 35)
        assertTrue(
            "Overworld must contain ONLY Copper/Tin/Bog Iron",
            overworldOres.all { it.kind in setOf(OreVeinKind.COPPER, OreVeinKind.TIN, OreVeinKind.BOG_IRON) }
        )
        assertTrue("Overworld must include Copper", overworldOres.any { it.kind == OreVeinKind.COPPER })
        assertTrue("Overworld must include Tin", overworldOres.any { it.kind == OreVeinKind.TIN })
        assertTrue("Overworld must include Bog Iron", overworldOres.any { it.kind == OreVeinKind.BOG_IRON })

        assertTrue("Dungeons must hold Vesperite veins", dungeonOres.any { it.kind == OreVeinKind.VESPERITE })
        assertTrue("Dungeons must hold Cinderite veins", dungeonOres.any { it.kind == OreVeinKind.CINDERITE })
    }

    @Test
    fun hierarchicalAutoWalkCrossesRegionBorderAndUpdatesRegionMetadata() {
        val start = GameEngine.createNewWorld("Wayfarer")
        assertEquals(DEFAULT_REGION_ID, start.regionId)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X, start.playerX)
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y, start.playerY)

        // Coarse routing graph connects Rookvale City across the sea-bridge to Western Mirewatch
        val coarseRoute = WorldBiomeBlueprint.findCoarseWaypoints(
            startX = WorldMaps.TEMPLE_SPAWN_X,
            startY = WorldMaps.TEMPLE_SPAWN_Y,
            goalX = 545,
            goalY = 592
        )
        assertTrue("Coarse route across continents should have multiple waypoints", coarseRoute.size >= 3)
        assertEquals(545 to 592, coarseRoute.last())

        // Walk from Rookvale West Gate (1204, 592) across border into Spirewood West (1192, 592)
        var state = start.copy(playerX = 1204, playerY = 592, regionId = DEFAULT_REGION_ID)
        val nowMs = 1_750_000_000_000L
        state = GameEngine.walkToCoordinate(state, 1192, 592)
        assertTrue("Path should be plotted across the region border", state.walkPath.isNotEmpty())

        var stepTime = nowMs + 300L
        repeat(20) {
            if (state.walkPath.isEmpty() && state.longRangeWaypoints.isEmpty()) return@repeat
            state = GameEngine.stepAutoFollowOrPath(state, stepTime)
            stepTime += 300L
        }

        assertEquals("Player must arrive in Spirewood West", "spirewood_west", state.regionId)
        assertTrue("Player X must be west of x=1200 border", state.playerX < 1200)
        assertNotNull("Crossing into a new region should trigger a region banner", state.floorWarningBanner)
    }

    @Test
    fun borderSeamlessMultiplayerShowsOverlandAlliesAcrossAllTwelveRegions() {
        assertTrue(isRegionCompatible(0, "rookvale_valley", "spirewood_west"))
        assertTrue(isRegionCompatible(0, "rookvale_valley", "sunspire_desert"))
        assertTrue(isRegionCompatible(0, "caldera_peaks", "ember_isles"))
        assertFalse(isRegionCompatible(0, "rookvale_valley", "frozen_citadel"))
        assertFalse(isRegionCompatible(-1, "rookvale_valley", "spirewood_west"))

        val nowMs = 1_750_000_000_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { nowMs },
            animClockProvider = { nowMs }
        )
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "local_1",
                name = "RookHero",
                level = 20,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 1202,
                y = 592,
                floor = 0,
                regionId = "rookvale_valley",
                hp = 350,
                maxHp = 350,
                joinedAt = nowMs - 10_000L,
                lastSeenMs = nowMs
            )
        )

        val spirewoodAlly = RemotePlayerState(
            playerId = "ally_west",
            name = "Mira",
            level = 22,
            vocation = Vocation.PALADIN,
            tunicColorIndex = 1,
            hairColorIndex = 1,
            x = 1196,
            y = 592,
            floor = 0,
            regionId = "spirewood_west",
            hp = 300,
            maxHp = 300,
            joinedAt = nowMs - 8_000L,
            lastSeenMs = nowMs - 500L
        )
        val dungeonAlly = spirewoodAlly.copy(
            playerId = "ally_dungeon",
            name = "Delver",
            floor = -2,
            regionId = "rookvale_valley"
        )

        mgr.mergePolledPlayers(
            polled = listOf(spirewoodAlly, dungeonAlly),
            localFloor = 0,
            localRegionId = "rookvale_valley",
            localPlayerId = "local_1",
            localPlayerName = "RookHero"
        )

        val visible = mgr.uiState.value.remotePlayers
        assertEquals(1, visible.size)
        assertEquals("ally_west", visible.first().playerId)
        assertEquals("spirewood_west", visible.first().regionId)
    }
}
