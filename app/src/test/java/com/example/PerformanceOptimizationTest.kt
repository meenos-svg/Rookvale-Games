package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.GameWorldState
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.world.WorldMaps
import com.example.ui.screens.HalfResLightLayerRenderer
import com.example.ui.screens.IsometricCamera
import com.example.ui.screens.LightGlowSpriteAtlas
import com.example.ui.screens.MinimapOffscreenCache
import com.example.ui.screens.ProceduralTileTextureAtlas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceOptimizationTest {

    // -------------------------------------------------------------------------
    // Pillar 1: Offscreen Caching for Procedural Tiles, Light Sprites & 0.5x Layer
    // -------------------------------------------------------------------------

    @Test
    fun proceduralTileAtlasPrewarmsCoreSurfacesAndReturnsCachedInstances() {
        ProceduralTileTextureAtlas.prewarmCoreTextures()
        assertTrue(ProceduralTileTextureAtlas.isCorePrewarmed())
        assertTrue(
            "Core prewarm should cache multiple surface variants/frames",
            ProceduralTileTextureAtlas.cachedTileCount() >= 24
        )

        val coreSurfaces = listOf(
            TileType.COBBLESTONE to false,
            TileType.CRYPT_FLOOR to false,
            TileType.OBSIDIAN_FLOOR to false,
            TileType.GRASS to false,
            TileType.DIRT_FLOOR to false,
            TileType.WATER to false,
            TileType.WATER to true,
            TileType.LAVA to false
        )
        for ((tile, shallow) in coreSurfaces) {
            val family = ProceduralTileTextureAtlas.resolveSurfaceFamily(tile, shallow)
            assertNotNull("Expected surface family for $tile (shallow=$shallow)", family)
            val first = ProceduralTileTextureAtlas.getOrBakeTile(family!!, variant = 0)
            val second = ProceduralTileTextureAtlas.getOrBakeTile(family, variant = 0)
            assertSame("Repeated lookup for $family should return exact cached Bitmap", first, second)
            assertEquals(ProceduralTileTextureAtlas.TILE_PX, first.width)
            assertEquals(ProceduralTileTextureAtlas.TILE_PX, first.height)
        }
    }

    @Test
    fun lightGlowSpriteAtlasAndHalfResLightLayerAllocateAndReuseBuffers() {
        LightGlowSpriteAtlas.prewarm()
        assertTrue(LightGlowSpriteAtlas.isPrewarmed())

        val outer1 = LightGlowSpriteAtlas.getOuterHaloBitmap()
        val outer2 = LightGlowSpriteAtlas.getOuterHaloBitmap()
        val inner1 = LightGlowSpriteAtlas.getInnerBloomBitmap()
        val inner2 = LightGlowSpriteAtlas.getInnerBloomBitmap()

        assertSame(outer1, outer2)
        assertSame(inner1, inner2)
        assertEquals(LightGlowSpriteAtlas.SPRITE_PX, outer1.width)
        assertEquals(LightGlowSpriteAtlas.SPRITE_PX, inner1.height)
        assertEquals(LightGlowSpriteAtlas.SPRITE_PX, LightGlowSpriteAtlas.getOuterHaloImageBitmap().width)
        assertEquals(LightGlowSpriteAtlas.SPRITE_PX, LightGlowSpriteAtlas.getInnerBloomImageBitmap().height)

        val renderer = HalfResLightLayerRenderer()
        assertEquals(0.5f, renderer.resolutionScale, 0.0001f)
        assertTrue(renderer.ensureLayerSize(1920f, 1080f))
        assertEquals(960, renderer.currentBufferWidth)
        assertEquals(540, renderer.currentBufferHeight)

        // Calling again with identical viewport keeps the exact half-res dimensions
        assertTrue(renderer.ensureLayerSize(1920f, 1080f))
        assertEquals(960, renderer.currentBufferWidth)
        assertEquals(540, renderer.currentBufferHeight)

        // Resizing updates to new half-res dimensions
        assertTrue(renderer.ensureLayerSize(1280f, 720f))
        assertEquals(640, renderer.currentBufferWidth)
        assertEquals(360, renderer.currentBufferHeight)
    }

    // -------------------------------------------------------------------------
    // Pillar 2: Tight Camera-Rotated Frustum Culling & Zero-Alloc Projection
    // -------------------------------------------------------------------------

    @Test
    fun rotatedCameraFrustumCullingUsesTightTwoTileMarginAndZeroAllocProjectionMatches() {
        val cam = IsometricCamera()
        val yaws = listOf(0f, 45f, 135f, 270f)
        val zooms = listOf(0.75f, 1.0f, 1.8f)
        val outXY = FloatArray(2)

        for (yaw in yaws) {
            for (zoom in zooms) {
                cam.configure(
                    viewportW = 1920f,
                    viewportH = 1080f,
                    camTileX = 200f,
                    camTileY = 200f,
                    zoomFactor = zoom,
                    cameraYawDeg = yaw
                )
                val tight = cam.visibleTileBounds(1920f, 1080f, 400, 400, margin = IsometricCamera.FRUSTUM_MARGIN_TILES)
                val wide = cam.visibleTileBounds(1920f, 1080f, 400, 400, margin = 4)

                // Center tile and immediate neighbors must always be inside the tight frustum
                assertTrue(200 in tight.minTx..tight.maxTx)
                assertTrue(200 in tight.minTy..tight.maxTy)
                assertTrue(199 in tight.minTx..tight.maxTx)
                assertTrue(201 in tight.minTy..tight.maxTy)

                // Tight 2-tile margin must cull strictly more tiles than margin 4
                val tightArea = (tight.maxTx - tight.minTx + 1) * (tight.maxTy - tight.minTy + 1)
                val wideArea = (wide.maxTx - wide.minTx + 1) * (wide.maxTy - wide.minTy + 1)
                assertTrue(
                    "Expected tight frustum ($tightArea) < wide frustum ($wideArea) at yaw=$yaw zoom=$zoom",
                    tightArea < wideArea
                )

                // Zero-allocation projection matches Offset-returning projection identically
                val ref = cam.tileCenterToScreen(203f, 198f)
                cam.tileCenterToScreenOut(203f, 198f, outXY)
                assertEquals(ref.x, outXY[0], 0.0001f)
                assertEquals(ref.y, outXY[1], 0.0001f)

                // Frustum screen-space test accepts center and rejects far off-screen points
                assertTrue(cam.isScreenPointInFrustum(960f, 540f, 1920f, 1080f))
                assertFalse(cam.isScreenPointInFrustum(-500f, 540f, 1920f, 1080f))
                assertFalse(cam.isScreenPointInFrustum(2600f, 540f, 1920f, 1080f))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pillar 3: Offscreen Minimap Cache & 12 FPS Entity Throttling
    // -------------------------------------------------------------------------

    @Test
    fun minimapOffscreenCacheRedrawsOnlyOnTileChangeYawThresholdOrThrottledEntityMove() {
        val cache = MinimapOffscreenCache()
        val floorMap = WorldMaps.getFloor(0)
        val nearbyMonster = MonsterEntity(
            id = 501,
            type = MonsterType.SKELETON,
            floor = 0,
            x = 203,
            y = 200,
            hp = MonsterType.SKELETON.maxHp,
            maxHp = MonsterType.SKELETON.maxHp,
            spawnX = 203,
            spawnY = 200
        )
        val baseState = GameWorldState(
            floor = 0,
            playerX = 200,
            playerY = 200,
            monsters = listOf(nearbyMonster)
        )

        // 1. Initial render
        val bmp1 = cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = baseState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 45f,
            nowUptimeMs = 1_000L
        )
        assertNotNull(bmp1)
        assertEquals(1, cache.redrawCount)

        // 2. Identical frame skips redraw completely
        val bmp2 = cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = baseState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 45f,
            nowUptimeMs = 1_016L
        )
        assertSame(bmp1, bmp2)
        assertEquals(1, cache.redrawCount)

        // 3. Sub-2-degree yaw jitter skips redraw
        cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = baseState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 46.8f,
            nowUptimeMs = 1_032L
        )
        assertEquals(1, cache.redrawCount)

        // 4. > 2-degree yaw shift triggers immediate redraw
        cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = baseState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 48.5f,
            nowUptimeMs = 1_048L
        )
        assertEquals(2, cache.redrawCount)

        // 5. Player tile move triggers immediate redraw even within 16ms
        val movedPlayerState = baseState.copy(playerX = 201, playerY = 200)
        cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = movedPlayerState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 48.5f,
            nowUptimeMs = 1_064L
        )
        assertEquals(3, cache.redrawCount)

        // 6. Nearby entity move within < 83ms is throttled (~12 FPS cap)
        val movedMonsterState = movedPlayerState.copy(
            monsters = listOf(nearbyMonster.copy(x = 204, y = 200))
        )
        cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = movedMonsterState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 48.5f,
            nowUptimeMs = 1_100L // only 36ms since 1_064L
        )
        assertEquals(3, cache.redrawCount)

        // 7. Once >= 83ms elapses, the queued entity change redraws
        cache.getOrRender(
            widthPx = 220,
            heightPx = 220,
            floorMap = floorMap,
            state = movedMonsterState,
            remotePlayers = emptyList(),
            radius = 22,
            cameraYawDeg = 48.5f,
            nowUptimeMs = 1_160L // 96ms since 1_064L
        )
        assertEquals(4, cache.redrawCount)
    }

    // -------------------------------------------------------------------------
    // Pillar 4: Distant Monster AI Sleep Culling (> 22 Tiles, Co-op Aware)
    // -------------------------------------------------------------------------

    @Test
    fun monsterAiSleepCullingPausesDistantMonstersWhilePreservingHpAndCoopAggro() {
        val floor = -6
        val nearMonster = MonsterEntity(
            id = 10,
            type = MonsterType.SKELETON,
            floor = floor,
            x = 50,
            y = 50,
            hp = 40,
            maxHp = 50,
            spawnX = 50,
            spawnY = 50
        )
        val sleepingMonster = MonsterEntity(
            id = 11,
            type = MonsterType.SKELETON,
            floor = floor,
            x = 85,
            y = 50,
            hp = 27,
            maxHp = 50,
            spawnX = 85,
            spawnY = 50,
            targetPlayerId = "ally-coop-1",
            moveCooldown = 0
        )

        assertEquals(22, GameEngine.MONSTER_AI_SLEEP_DISTANCE)
        // Local player at (45, 50): nearMonster is 5 tiles away (awake), sleepingMonster is 40 tiles away (asleep)
        assertTrue(
            GameEngine.isMonsterAwakeForAi(nearMonster, floor, 45, 50, emptyList())
        )
        assertFalse(
            GameEngine.isMonsterAwakeForAi(sleepingMonster, floor, 45, 50, emptyList())
        )

        // Active non-spectator remote co-op ally within 22 tiles wakes the distant monster
        val activeAlly = RemotePlayerState(
            playerId = "ally-coop-1",
            name = "AllyKnight",
            level = 20,
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            x = 80,
            y = 50,
            floor = floor,
            hp = 120,
            maxHp = 150,
            isSpectator = false
        )
        assertTrue(
            GameEngine.isMonsterAwakeForAi(sleepingMonster, floor, 45, 50, listOf(activeAlly))
        )

        // Spectator or dead remote players do NOT wake sleeping monsters
        assertFalse(
            GameEngine.isMonsterAwakeForAi(
                sleepingMonster,
                floor,
                45,
                50,
                listOf(activeAlly.copy(isSpectator = true))
            )
        )
        assertFalse(
            GameEngine.isMonsterAwakeForAi(
                sleepingMonster,
                floor,
                45,
                50,
                listOf(activeAlly.copy(hp = 0))
            )
        )

        // Run live GameEngine.processCombatTick with sleeping monster beyond 22 tiles (solo):
        // Verify its position, HP, and preserved co-op aggro targetPlayerId remain untouched.
        val floorMap = WorldMaps.getFloor(floor)
        var localPos: Pair<Int, Int>? = null
        var farPos: Pair<Int, Int>? = null
        for (y in 4 until floorMap.height - 4) {
            for (x in 4 until floorMap.width - 4) {
                if (floorMap.tileAt(x, y).walkable && !floorMap.isProtectionZone(x, y)) {
                    if (localPos == null) {
                        localPos = x to y
                    } else if (farPos == null &&
                        maxOf(kotlin.math.abs(x - localPos.first), kotlin.math.abs(y - localPos.second)) >= 30
                    ) {
                        farPos = x to y
                        break
                    }
                }
            }
            if (localPos != null && farPos != null) break
        }
        assertNotNull(localPos)
        assertNotNull(farPos)

        val dormantM = sleepingMonster.copy(
            x = farPos!!.first,
            y = farPos.second,
            spawnX = farPos.first,
            spawnY = farPos.second,
            hp = 27,
            targetPlayerId = "ally-coop-1"
        )
        val initialState = GameWorldState(
            floor = floor,
            playerX = localPos!!.first,
            playerY = localPos.second,
            hp = 200,
            maxHp = 200,
            monsters = listOf(dormantM)
        )

        val afterTick = GameEngine.processCombatTick(
            state = initialState,
            nowMs = 10_000L,
            isFloorHost = true,
            localPlayerId = "local-hero",
            remotePlayersOnFloor = emptyList()
        )
        val afterDormant = afterTick.monsters.first { it.id == dormantM.id }
        assertEquals("Sleeping monster X should not move", dormantM.x, afterDormant.x)
        assertEquals("Sleeping monster Y should not move", dormantM.y, afterDormant.y)
        assertEquals("Sleeping monster HP must be preserved", 27, afterDormant.hp)
        assertEquals("Sleeping monster co-op aggro must be preserved", "ally-coop-1", afterDormant.targetPlayerId)
    }
}
