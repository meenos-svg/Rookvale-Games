package com.example

import com.example.game.world.WorldMaps
import com.example.ui.screens.WorldMapViewportCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorldMapZoomAndTapTest {

    @Test
    fun defaultOverlandCameraOpensAtLocalAreaZoomCenteredOnPlayerWithHybridTileDetail() {
        val overland = WorldMaps.getFloor(0)
        val cam = WorldMapViewportCamera.initialCenteredOnPlayer(
            floorWidth = overland.width,
            floorHeight = overland.height,
            isOverland = true,
            playerX = WorldMaps.TEMPLE_SPAWN_X,
            playerY = WorldMaps.TEMPLE_SPAWN_Y
        )

        assertEquals(WorldMapViewportCamera.DEFAULT_OVERLAND_ZOOM, cam.mapZoom, 0.001f)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X + 0.5f, cam.centerX, 0.001f)
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y + 0.5f, cam.centerY, 0.001f)

        // Default 3.5f zoom on 1600x1200 shows a close local neighborhood (~100-135 tile radius around player)
        val (visW, visH) = WorldMapViewportCamera.visibleTileSpan(overland.width, overland.height, cam.mapZoom, true)
        val radiusX = visW * 0.5f
        val radiusY = visH * 0.5f
        assertTrue("Expected local X radius near 90-140 tiles, got $radiusX", radiusX in 90f..140f)
        assertTrue("Expected local Y radius near 85-120 tiles, got $radiusY", radiusY in 85f..120f)

        // Hybrid detail should be fully active at default 3.5f and 0f at full-world 1.0f
        assertEquals(1.0f, WorldMapViewportCamera.localTileDetailAlpha(cam.mapZoom, true), 0.001f)
        assertEquals(0.0f, WorldMapViewportCamera.localTileDetailAlpha(1.0f, true), 0.001f)
        val midFade = WorldMapViewportCamera.localTileDetailAlpha(2.6f, true)
        assertTrue("Expected smooth mid crossfade between 0 and 1, got $midFade", midFade in 0.2f..0.8f)

        // Zooming all the way out to 1.0f displays the full 1600x1200 continent
        val fullWorld = WorldMapViewportCamera.stepZoom(cam, -10f)
        assertEquals(WorldMapViewportCamera.MIN_ZOOM, fullWorld.mapZoom, 0.001f)
        val (fullW, fullH) = WorldMapViewportCamera.visibleTileSpan(overland.width, overland.height, fullWorld.mapZoom, true)
        assertEquals(1600f, fullW, 0.5f)
        assertEquals(1200f, fullH, 0.5f)
    }

    @Test
    fun tapToWalkCoordinateConversionMatchesRenderedMarkersAtAnyZoomAndPanOffset() {
        val canvasW = 640f
        val canvasH = 480f
        val overland = WorldMaps.getFloor(0)

        val testZooms = listOf(1.0f, 2.25f, 3.5f, 4.75f, 6.0f)
        val startCam = WorldMapViewportCamera.initialCenteredOnPlayer(
            floorWidth = overland.width,
            floorHeight = overland.height,
            isOverland = true,
            playerX = 1396,
            playerY = 592
        )

        for (z in testZooms) {
            val zoomed = WorldMapViewportCamera.stepZoom(startCam, z - startCam.mapZoom)
            val panned = WorldMapViewportCamera.panByScreenDelta(zoomed, deltaScreenX = -95f, deltaScreenY = 62f, canvasW, canvasH)

            // Test center, off-center, and near-corner world points inside the active viewport
            val bounds = WorldMapViewportCamera.viewportBounds(panned)
            val sampleTargets = listOf(
                ((bounds[0] + bounds[2]) * 0.5f).toInt() to ((bounds[1] + bounds[3]) * 0.5f).toInt(),
                (bounds[0] + (bounds[2] - bounds[0]) * 0.22f).toInt() to (bounds[1] + (bounds[3] - bounds[1]) * 0.78f).toInt(),
                (bounds[0] + (bounds[2] - bounds[0]) * 0.81f).toInt() to (bounds[1] + (bounds[3] - bounds[1]) * 0.19f).toInt()
            )
            for ((wx, wy) in sampleTargets) {
                val screenPos = WorldMapViewportCamera.worldToScreen(wx + 0.5f, wy + 0.5f, panned, canvasW, canvasH)
                val (mappedX, mappedY) = WorldMapViewportCamera.screenToWorldTile(screenPos.x, screenPos.y, panned, canvasW, canvasH)
                assertEquals("Tap X mismatch at zoom $z", wx, mappedX)
                assertEquals("Tap Y mismatch at zoom $z", wy, mappedY)
            }
        }
    }

    @Test
    fun pinchZoomPreservesFocalTileAndRecenterSnapsBackToPlayer() {
        val canvasW = 600f
        val canvasH = 450f
        val overland = WorldMaps.getFloor(0)
        val initial = WorldMapViewportCamera.initialCenteredOnPlayer(
            floorWidth = overland.width,
            floorHeight = overland.height,
            isOverland = true,
            playerX = 800,
            playerY = 600,
            initialZoom = 3.0f
        )

        val focalX = 420f
        val focalY = 160f
        val (worldBeforeX, worldBeforeY) = WorldMapViewportCamera.screenToWorldExact(focalX, focalY, initial, canvasW, canvasH)

        // Pinch zoom in around (420, 160)
        val pinched = WorldMapViewportCamera.zoomAroundFocal(initial, 4.5f, focalX, focalY, canvasW, canvasH)
        assertEquals(4.5f, pinched.mapZoom, 0.001f)
        val (worldAfterX, worldAfterY) = WorldMapViewportCamera.screenToWorldExact(focalX, focalY, pinched, canvasW, canvasH)
        assertTrue("Focal X drifted too far: $worldBeforeX vs $worldAfterX", abs(worldBeforeX - worldAfterX) < 0.25f)
        assertTrue("Focal Y drifted too far: $worldBeforeY vs $worldAfterY", abs(worldBeforeY - worldAfterY) < 0.25f)

        // Pan far away, then verify Center on Player snaps right back to (800, 600)
        val pannedAway = WorldMapViewportCamera.panByScreenDelta(pinched, -300f, -200f, canvasW, canvasH)
        assertTrue(abs(pannedAway.centerX - 800.5f) > 5f)
        val recentered = WorldMapViewportCamera.recenterOnPlayer(pannedAway, 800, 600)
        assertEquals(800.5f, recentered.centerX, 0.01f)
        assertEquals(600.5f, recentered.centerY, 0.01f)

        // Zoom clamping at extremes
        val maxClamped = WorldMapViewportCamera.stepZoom(recentered, 20f)
        assertEquals(WorldMapViewportCamera.MAX_ZOOM, maxClamped.mapZoom, 0.001f)
        val minClamped = WorldMapViewportCamera.stepZoom(maxClamped, -20f)
        assertEquals(WorldMapViewportCamera.MIN_ZOOM, minClamped.mapZoom, 0.001f)
    }
}
