package com.example

import com.example.game.model.TileType
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import com.example.ui.screens.WorldMapTerrainSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeamlessWorldMapAndMinimapTest {

    @Test
    fun rookvaleHeartlandHasNoStraightRectangularMountainBorderWall() {
        val floor0 = WorldMaps.getFloor(0)

        // West edge of Rookvale Valley (x = 1200, y in 420..780 excluding gates) should not be a solid rock wall
        var westMountainCount = 0
        var westSampleCount = 0
        for (y in 420..780 step 4) {
            if (y in 584..602) continue
            westSampleCount++
            if (floor0.tileAt(1200, y) == TileType.MOUNTAIN_ROCK) {
                westMountainCount++
            }
        }
        assertTrue(
            "Rookvale west sector edge should blend naturally without a straight MOUNTAIN_ROCK wall (found $westMountainCount/$westSampleCount)",
            westMountainCount < westSampleCount / 4
        )

        // North edge of Rookvale Valley (y = 400, x in 1220..1540 excluding north highway gate)
        var northMountainCount = 0
        var northSampleCount = 0
        for (x in 1220..1540 step 4) {
            if (x in 1388..1404) continue
            northSampleCount++
            if (floor0.tileAt(x, 400) == TileType.MOUNTAIN_ROCK) {
                northMountainCount++
            }
        }
        assertTrue(
            "Rookvale north sector edge should blend naturally without a straight MOUNTAIN_ROCK wall (found $northMountainCount/$northSampleCount)",
            northMountainCount < northSampleCount / 4
        )
    }

    @Test
    fun continuousBaseTerrainHasNoHardSeamsAcrossFormerSectorBorders() {
        // Test 1-tile steps directly across former 400x400 rectangular sector borders
        // and verify average gradient matches interior control lines (zero border seam anomaly)
        val verticalBorders = listOf(400f, 800f, 1200f)
        for (bx in verticalBorders) {
            var maxDelta = 0f
            var borderSum = 0f
            var controlSum = 0f
            var count = 0
            for (y in 80..1120 step 20) {
                val leftCol = WorldMapTerrainSampler.sampleOverlandBaseArgb(bx - 0.5f, y.toFloat())
                val rightCol = WorldMapTerrainSampler.sampleOverlandBaseArgb(bx + 0.5f, y.toFloat())
                val dist = WorldMapTerrainSampler.colorDistance(leftCol, rightCol)
                if (dist > maxDelta) maxDelta = dist
                borderSum += dist

                val ctrlLeft = WorldMapTerrainSampler.sampleOverlandBaseArgb(bx + 16.5f, y.toFloat())
                val ctrlRight = WorldMapTerrainSampler.sampleOverlandBaseArgb(bx + 17.5f, y.toFloat())
                controlSum += WorldMapTerrainSampler.colorDistance(ctrlLeft, ctrlRight)
                count++
            }
            val avgBorder = borderSum / count
            val avgControl = controlSum / count
            assertTrue(
                "Expected smooth terrain transition across former vertical border x=$bx, but max 1-tile RGB delta was $maxDelta",
                maxDelta < 55f
            )
            assertTrue(
                "Former vertical border x=$bx avg delta ($avgBorder) should match interior control ($avgControl)",
                avgBorder < avgControl + 4.5f
            )
        }

        val horizontalBorders = listOf(400f, 800f)
        for (by in horizontalBorders) {
            var maxDelta = 0f
            var borderSum = 0f
            var controlSum = 0f
            var count = 0
            for (x in 80..1520 step 20) {
                val topCol = WorldMapTerrainSampler.sampleOverlandBaseArgb(x.toFloat(), by - 0.5f)
                val bottomCol = WorldMapTerrainSampler.sampleOverlandBaseArgb(x.toFloat(), by + 0.5f)
                val dist = WorldMapTerrainSampler.colorDistance(topCol, bottomCol)
                if (dist > maxDelta) maxDelta = dist
                borderSum += dist

                val ctrlTop = WorldMapTerrainSampler.sampleOverlandBaseArgb(x.toFloat(), by + 16.5f)
                val ctrlBottom = WorldMapTerrainSampler.sampleOverlandBaseArgb(x.toFloat(), by + 17.5f)
                controlSum += WorldMapTerrainSampler.colorDistance(ctrlTop, ctrlBottom)
                count++
            }
            val avgBorder = borderSum / count
            val avgControl = controlSum / count
            assertTrue(
                "Expected smooth terrain transition across former horizontal border y=$by, but max 1-tile RGB delta was $maxDelta",
                maxDelta < 55f
            )
            assertTrue(
                "Former horizontal border y=$by avg delta ($avgBorder) should match interior control ($avgControl)",
                avgBorder < avgControl + 4.5f
            )
        }
    }

    @Test
    fun coastalTransectTransitionsSmoothlyFromDeepOceanToLand() {
        // Transect across Central Sea -> Spirewood western shoreline around y = 520
        val y = 520f
        var sawDeepWater = false
        var sawShallowWater = false
        var sawBeachOrShore = false
        var sawInland = false
        var prevCol = WorldMapTerrainSampler.sampleOverlandBaseArgb(760f, y)
        var maxStepJump = 0f

        for (x in 761..1010) {
            val landVal = WorldBiomeBlueprint.continuousLandmassField(x.toFloat(), y)
            if (landVal < -0.22f) sawDeepWater = true
            if (landVal in -0.14f..-0.01f) sawShallowWater = true
            if (landVal in 0.006f..0.055f) sawBeachOrShore = true
            if (landVal > 0.12f) sawInland = true

            val col = WorldMapTerrainSampler.sampleOverlandBaseArgb(x.toFloat(), y)
            val jump = WorldMapTerrainSampler.colorDistance(prevCol, col)
            if (jump > maxStepJump) maxStepJump = jump
            prevCol = col
        }

        assertTrue("Transect should cross deep water", sawDeepWater)
        assertTrue("Transect should cross shallow coastal water", sawShallowWater)
        assertTrue("Transect should cross coastal beach/shore zone", sawBeachOrShore)
        assertTrue("Transect should reach inland biome ground", sawInland)
        assertTrue(
            "Coastal gradient should transition smoothly without harsh jumps (max 1-tile jump=$maxStepJump)",
            maxStepJump < 55f
        )
    }

    @Test
    fun overviewSamplingIsChunkFreeAndRespectsLruCap() {
        val floor0 = WorldMaps.getFloor(0)
        val chunksBefore = floor0.chunkedGrid.cachedChunkCount

        // Sample across the entire 1600x1200 world just like WorldMapOverviewCache does
        var samples = 0
        for (py in 0 until 120) {
            val fy = (py + 0.5f) * (WORLD_OVERLAND_HEIGHT / 120f)
            for (px in 0 until 160) {
                val fx = (px + 0.5f) * (WORLD_OVERLAND_WIDTH / 160f)
                val argb = WorldMapTerrainSampler.sampleOverlandOverviewArgb(fx, fy)
                assertEquals("Overview color must be fully opaque", 0xFF, (argb ushr 24) and 0xFF)
                samples++
            }
        }
        assertEquals(160 * 120, samples)
        assertEquals(
            "Overview sampling must not allocate or evict any ChunkedTileGrid chunks",
            chunksBefore,
            floor0.chunkedGrid.cachedChunkCount
        )
        assertTrue(floor0.chunkedGrid.cachedChunkCount <= 180)
    }

    @Test
    fun biomeWatermarksAreInsideWorldBoundsAndNonEmpty() {
        val marks = WorldMapTerrainSampler.overlandBiomeWatermarks
        assertTrue("Should expose soft floating biome titles for overview zoom", marks.size >= 8)
        for (wm in marks) {
            assertTrue("Watermark title should not be blank", wm.title.isNotBlank())
            assertTrue("Watermark x=${wm.x} out of bounds", wm.x in 0f..WORLD_OVERLAND_WIDTH.toFloat())
            assertTrue("Watermark y=${wm.y} out of bounds", wm.y in 0f..WORLD_OVERLAND_HEIGHT.toFloat())
        }
    }
}
