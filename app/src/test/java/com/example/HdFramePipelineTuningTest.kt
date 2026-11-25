package com.example

import androidx.compose.ui.graphics.Color
import com.example.game.model.GraphicsQuality
import com.example.game.world.BiomeWeatherSystem
import com.example.game.world.WeatherType
import com.example.ui.screens.HalfResLightLayerRenderer
import com.example.ui.screens.HdPostProcessRenderer
import com.example.ui.screens.IsometricLightingEngine
import com.example.ui.screens.ScenePointLight
import com.example.ui.screens.WeatherOverlayRenderer
import com.example.ui.screens.WorldViewportScaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying the tuned Rookvale HD graphics & frame pipeline:
 * 1. Automatic 0.8f world render scale in HD with clean clamping and 1.0x native overlay parity.
 * 2. Strict closest-14 dynamic point-light capping with priority ranking & offscreen culling.
 * 3. Single-pass half-res light compositing without redundant BlendMode.Plus / ADD double-draws.
 * 4. Full removal of full-screen AGSL RuntimeShader passes in both twilight and weather renderers,
 *    replaced by viewport-cached gradient/vignette Brushes.
 * 5. Clear separation across Low, Medium, High, and HD quality tiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HdFramePipelineTuningTest {

    @Test
    fun hdRenderScaleIsAutomatic08fAndClampsSafelyAcrossBounds() {
        assertEquals(0.8f, GraphicsQuality.HD_RENDER_SCALE, 0.0001f)
        assertEquals(1.0f, GraphicsQuality.LOW.renderScaleFactor, 0.0001f)
        assertEquals(1.0f, GraphicsQuality.MEDIUM.renderScaleFactor, 0.0001f)
        assertEquals(1.0f, GraphicsQuality.HIGH.renderScaleFactor, 0.0001f)
        assertEquals(0.8f, GraphicsQuality.HD.renderScaleFactor, 0.0001f)

        assertEquals(0.8f, GraphicsQuality.HD.effectiveRenderScale(), 0.0001f)
        assertEquals(0.5f, GraphicsQuality.clampRenderScale(0.1f), 0.0001f)
        assertEquals(1.0f, GraphicsQuality.clampRenderScale(1.8f), 0.0001f)
        assertEquals(0.75f, GraphicsQuality.HD.effectiveRenderScale(0.75f), 0.0001f)

        // Scaled viewport dimensions and round-trip native 1.0x coordinate parity
        assertEquals(1536, WorldViewportScaling.scaledDimensionPx(1920, GraphicsQuality.HD.renderScaleFactor))
        assertEquals(864, WorldViewportScaling.scaledDimensionPx(1080, GraphicsQuality.HD.renderScaleFactor))
        assertEquals(1920, WorldViewportScaling.scaledDimensionPx(1920, GraphicsQuality.HIGH.renderScaleFactor))

        val nativeX = 942.5f
        val scaledX = WorldViewportScaling.toScaledCoord(nativeX, GraphicsQuality.HD.renderScaleFactor)
        assertEquals(754.0f, scaledX, 0.01f)
        assertEquals(nativeX, WorldViewportScaling.toNativeCoord(scaledX, GraphicsQuality.HD.renderScaleFactor), 0.01f)
    }

    @Test
    fun pointLightSelectionEnforcesClosest14CapPriorityAndViewportCulling() {
        val viewportW = 1600f
        val viewportH = 900f
        val centerX = viewportW * 0.5f
        val centerY = viewportH * 0.5f

        val candidateLights = ArrayList<ScenePointLight>()

        // 1. Add 24 visible ambient/torch lights at varying distances from viewport center
        for (i in 0 until 24) {
            val offset = 40f + i * 28f
            candidateLights.add(
                ScenePointLight(
                    screenX = centerX + offset,
                    screenY = centerY,
                    radiusPx = 110f,
                    coreColor = Color(0xFFFFCF6E),
                    outerColor = Color(0xFFE67E22),
                    intensity = 0.85f,
                    flickerSeed = i.toFloat(),
                    priority = if (i % 2 == 0) ScenePointLight.PRIORITY_TORCH_OR_RUNE else ScenePointLight.PRIORITY_AMBIENT
                )
            )
        }

        // 2. Add 4 completely offscreen lights that must be culled despite high priority
        for (j in 0 until 4) {
            candidateLights.add(
                ScenePointLight(
                    screenX = viewportW + 800f + j * 50f,
                    screenY = viewportH + 800f,
                    radiusPx = 90f,
                    coreColor = Color.Cyan,
                    outerColor = Color.Blue,
                    intensity = 1.2f,
                    flickerSeed = 900f + j,
                    isArcane = true,
                    priority = ScenePointLight.PRIORITY_COMBAT_FX
                )
            )
        }

        // 3. Add a visible hero lantern and an active equipment proc light near screen edge
        val procLight = ScenePointLight(
            screenX = centerX - 520f,
            screenY = centerY - 260f,
            radiusPx = 140f,
            coreColor = Color(0xFF7BF5FF),
            outerColor = Color(0xFF3399FF),
            intensity = 1.15f,
            flickerSeed = 777f,
            isArcane = true,
            priority = ScenePointLight.PRIORITY_COMBAT_FX
        )
        val heroLight = ScenePointLight(
            screenX = centerX - 40f,
            screenY = centerY + 10f,
            radiusPx = 180f,
            coreColor = Color(0xFFFFE6A8),
            outerColor = Color(0xFFE69A32),
            intensity = 1.0f,
            flickerSeed = 888f,
            priority = ScenePointLight.PRIORITY_HERO_OR_BOSS
        )
        candidateLights.add(procLight)
        candidateLights.add(heroLight)

        val selected = ArrayList<ScenePointLight>(IsometricLightingEngine.MAX_SIMULTANEOUS_POINT_LIGHTS)
        val scores = FloatArray(IsometricLightingEngine.MAX_SIMULTANEOUS_POINT_LIGHTS)

        // Even if caller asks for 32 lights, engine hard-caps at 14
        val count = IsometricLightingEngine.selectClosestPrioritizedLights(
            lights = candidateLights,
            viewportW = viewportW,
            viewportH = viewportH,
            maxLights = 32,
            outSelected = selected,
            scratchScores = scores
        )

        assertEquals(IsometricLightingEngine.MAX_SIMULTANEOUS_POINT_LIGHTS, count)
        assertEquals(14, selected.size)
        assertTrue("Active proc light must be prioritized into the top 14", selected.contains(procLight))
        assertTrue("Hero lantern light must be prioritized into the top 14", selected.contains(heroLight))
        assertTrue(
            "Offscreen lights must never be selected",
            selected.none { it.screenX > viewportW + it.radiusPx * 1.35f }
        )

        // Highest priority lights should sort ahead of lower-priority ones
        assertEquals(procLight, selected.first())
        assertEquals(heroLight, selected[1])

        // Re-running with a smaller tier budget (High = 10, Medium = 6) honors tier cap cleanly
        val highCount = IsometricLightingEngine.selectClosestPrioritizedLights(
            lights = candidateLights,
            viewportW = viewportW,
            viewportH = viewportH,
            maxLights = GraphicsQuality.HIGH.maxDynamicPointLights,
            outSelected = selected,
            scratchScores = scores
        )
        assertEquals(10, highCount)
        assertEquals(10, selected.size)
    }

    @Test
    fun halfResLightRendererAvoidsAdditiveOverlapPasses() {
        val renderer = HalfResLightLayerRenderer()
        assertFalse(
            "HalfResLightLayerRenderer should composite each light in a single clean SCREEN pass without overlapping ADD passes",
            renderer.usesAdditiveOverlapPass
        )
        assertEquals(HalfResLightLayerRenderer.HALF_RES_SCALE, renderer.resolutionScale, 0.0001f)
    }

    @Test
    fun hdPostProcessAndWeatherRenderersDisableRuntimeShadersAndReuseCachedBrushes() {
        val postProcess = HdPostProcessRenderer()
        assertFalse(postProcess.isRuntimeShaderActive)

        val brush1 = postProcess.getOrCreateCachedVignetteBrush(1536f, 864f, isOverland = true, vignetteScale = 0.85f)
        val brush2 = postProcess.getOrCreateCachedVignetteBrush(1536f, 864f, isOverland = true, vignetteScale = 0.85f)
        assertSame("Identical viewport & vignette parameters must reuse cached Brush", brush1, brush2)
        assertEquals(1, postProcess.cacheRebuildCount)

        // Switching to dungeon or changing viewport size rebuilds once
        val brushDungeon = postProcess.getOrCreateCachedVignetteBrush(1536f, 864f, isOverland = false, vignetteScale = 1.0f)
        assertNotSame(brush1, brushDungeon)
        assertEquals(2, postProcess.cacheRebuildCount)

        val weatherRenderer = WeatherOverlayRenderer()
        assertFalse(weatherRenderer.isRuntimeShaderActive)

        val rainBrush1 = weatherRenderer.getOrCreateAtmosphericBrush(1536f, 864f, WeatherType.LIGHT_RAIN)
        val rainBrush2 = weatherRenderer.getOrCreateAtmosphericBrush(1536f, 864f, WeatherType.LIGHT_RAIN)
        assertNotNull(rainBrush1)
        assertSame("Identical viewport & weather must reuse cached atmospheric gradient Brush", rainBrush1, rainBrush2)
        assertEquals(1, weatherRenderer.gradientCacheRebuildCount)

        val snowBrush = weatherRenderer.getOrCreateAtmosphericBrush(1536f, 864f, WeatherType.SNOW)
        assertNotNull(snowBrush)
        assertNotSame(rainBrush1, snowBrush)
        assertEquals(2, weatherRenderer.gradientCacheRebuildCount)
    }

    @Test
    fun fourGraphicsQualityTiersRemainClearlySeparatedWithoutShaderBottlenecks() {
        val tiers = GraphicsQuality.entries
        assertEquals(4, tiers.size)

        // No tier activates full-screen RuntimeShader post-processing or weather shaders
        for (tier in tiers) {
            assertFalse("${tier.name} must not enable full-screen RuntimeShader", tier.useHdPostProcessShader)
            assertFalse("${tier.name} must not enable weather AGSL shader", BiomeWeatherSystem.visualProfileFor(tier).enableAgslShader)
        }

        // Distinct progression across Low -> Medium -> High -> HD
        assertEquals(0, GraphicsQuality.LOW.maxDynamicPointLights)
        assertEquals(6, GraphicsQuality.MEDIUM.maxDynamicPointLights)
        assertEquals(10, GraphicsQuality.HIGH.maxDynamicPointLights)
        assertEquals(14, GraphicsQuality.HD.maxDynamicPointLights)

        // HD preserves enhanced water, stone polish, vignette, and higher weather density over High
        assertFalse(GraphicsQuality.HIGH.drawEnhancedWater)
        assertTrue(GraphicsQuality.HD.drawEnhancedWater)
        assertFalse(GraphicsQuality.HIGH.drawHdStonePolish)
        assertTrue(GraphicsQuality.HD.drawHdStonePolish)
        assertFalse(GraphicsQuality.HIGH.drawHdVignette)
        assertTrue(GraphicsQuality.HD.drawHdVignette)
        assertTrue(
            BiomeWeatherSystem.visualProfileFor(GraphicsQuality.HD).particleCount >
                BiomeWeatherSystem.visualProfileFor(GraphicsQuality.HIGH).particleCount
        )
    }
}
