package com.example

import android.content.SharedPreferences
import com.example.game.data.DisplaySettingsStore
import com.example.game.model.DisplaySettings
import com.example.game.model.GraphicsQuality
import com.example.game.model.LightingDetailMode
import com.example.game.model.ParticleDetailLevel
import com.example.game.world.WorldMaps
import com.example.ui.screens.IsometricCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsQualitySettingsTest {

    @Test
    fun graphicsQualityTiersHaveExpectedOrderLabelsAndDefault() {
        val tiers = GraphicsQuality.entries
        assertEquals(
            listOf(GraphicsQuality.LOW, GraphicsQuality.MEDIUM, GraphicsQuality.HIGH, GraphicsQuality.HD),
            tiers
        )
        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.DEFAULT)
        assertEquals(listOf("Low", "Medium", "High", "HD"), tiers.map { it.displayName })
        assertEquals(listOf("LOW", "MED", "HIGH", "HD"), tiers.map { it.shortBadge })

        for (tier in tiers) {
            assertTrue("${tier.name} summaryNote should not be blank", tier.summaryNote.isNotBlank())
            assertTrue("${tier.name} should expose capability tags", tier.capabilityTags.isNotEmpty())
            assertTrue("${tier.name} cullMargin should be >= 2", tier.cullMargin >= 2)
        }
    }

    @Test
    fun fromStorageNameParsesValidNamesAndFallsBackSafely() {
        assertEquals(GraphicsQuality.LOW, GraphicsQuality.fromStorageName("LOW"))
        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.fromStorageName("MEDIUM"))
        assertEquals(GraphicsQuality.HIGH, GraphicsQuality.fromStorageName("HIGH"))
        assertEquals(GraphicsQuality.HD, GraphicsQuality.fromStorageName("HD"))

        assertEquals(GraphicsQuality.HD, GraphicsQuality.fromStorageName("  hd "))
        assertEquals(GraphicsQuality.LOW, GraphicsQuality.fromStorageName("low"))

        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.fromStorageName(null))
        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.fromStorageName(""))
        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.fromStorageName("   "))
        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.fromStorageName("ULTRA_BROKEN"))
    }

    @Test
    fun renderProfilesScaleMonotonicallyAcrossAllFourTiers() {
        val low = GraphicsQuality.LOW
        val med = GraphicsQuality.MEDIUM
        val high = GraphicsQuality.HIGH
        val hd = GraphicsQuality.HD

        // LOW: performance-first profile
        assertFalse(low.drawGroundClutter)
        assertFalse(low.drawWildflowers)
        assertFalse(low.drawShorelineDetails)
        assertFalse(low.drawContactShadows)
        assertFalse(low.drawCutawayRoofs)
        assertFalse(low.drawEnhancedWater)
        assertFalse(low.drawHdStonePolish)
        assertFalse(low.drawHdVignette)
        assertFalse(low.useProceduralTileTextures)
        assertFalse(low.useDirectionalLighting)
        assertFalse(low.drawAmbientOcclusion)
        assertFalse(low.drawCastShadows)
        assertFalse(low.drawTwilightAtmosphere)
        assertFalse(low.drawPointLightPools)
        assertFalse(low.useHdPostProcessShader)
        assertEquals(LightingDetailMode.OFF, low.lightingMode)
        assertEquals(ParticleDetailLevel.MINIMAL, low.particleLevel)
        assertEquals(1, low.treeDetailLayers)

        // MEDIUM: balanced defaults (confirmed default tier)
        assertFalse(med.drawGroundClutter)
        assertTrue(med.drawWildflowers)
        assertFalse(med.drawShorelineDetails)
        assertTrue(med.drawContactShadows)
        assertTrue(med.drawCutawayRoofs)
        assertFalse(med.drawEnhancedWater)
        assertFalse(med.drawHdStonePolish)
        assertFalse(med.drawHdVignette)
        assertFalse(med.useProceduralTileTextures)
        assertFalse(med.useDirectionalLighting)
        assertFalse(med.drawAmbientOcclusion)
        assertFalse(med.drawCastShadows)
        assertFalse(med.drawTwilightAtmosphere)
        assertFalse(med.drawPointLightPools)
        assertFalse(med.useHdPostProcessShader)
        assertEquals(LightingDetailMode.STATIC_HALO, med.lightingMode)
        assertEquals(ParticleDetailLevel.MODERATE, med.particleLevel)
        assertEquals(2, med.treeDetailLayers)

        // HIGH: handcrafted pixel-mosaic tiles, directional twilight lighting, AO, cast shadows & point lights
        assertTrue(high.drawGroundClutter)
        assertTrue(high.drawWildflowers)
        assertTrue(high.drawShorelineDetails)
        assertTrue(high.drawContactShadows)
        assertTrue(high.drawCutawayRoofs)
        assertFalse(high.drawEnhancedWater)
        assertFalse(high.drawHdStonePolish)
        assertFalse(high.drawHdVignette)
        assertTrue(high.useProceduralTileTextures)
        assertTrue(high.useDirectionalLighting)
        assertTrue(high.drawAmbientOcclusion)
        assertTrue(high.drawCastShadows)
        assertTrue(high.drawTwilightAtmosphere)
        assertTrue(high.drawPointLightPools)
        assertFalse(high.useHdPostProcessShader)
        assertEquals(LightingDetailMode.DYNAMIC_FLICKER, high.lightingMode)
        assertEquals(ParticleDetailLevel.FULL, high.particleLevel)
        assertEquals(3, high.treeDetailLayers)

        // HD: maximum fidelity with enhanced water, stone polish, multi-layer bloom & AGSL twilight shader
        assertTrue(hd.drawGroundClutter)
        assertTrue(hd.drawWildflowers)
        assertTrue(hd.drawShorelineDetails)
        assertTrue(hd.drawContactShadows)
        assertTrue(hd.drawCutawayRoofs)
        assertTrue(hd.drawEnhancedWater)
        assertTrue(hd.drawHdStonePolish)
        assertTrue(hd.drawHdVignette)
        assertTrue(hd.useProceduralTileTextures)
        assertTrue(hd.useDirectionalLighting)
        assertTrue(hd.drawAmbientOcclusion)
        assertTrue(hd.drawCastShadows)
        assertTrue(hd.drawTwilightAtmosphere)
        assertTrue(hd.drawPointLightPools)
        assertTrue(hd.useHdPostProcessShader)
        assertEquals(LightingDetailMode.HD_MULTI_LAYER, hd.lightingMode)
        assertEquals(ParticleDetailLevel.HD_ENHANCED, hd.particleLevel)
        assertEquals(3, hd.treeDetailLayers)

        // Monotonic scaling checks
        assertTrue(low.cullMargin <= med.cullMargin)
        assertTrue(med.cullMargin <= high.cullMargin)
        assertTrue(high.cullMargin <= hd.cullMargin)

        assertTrue(low.spriteScaleMultiplier < med.spriteScaleMultiplier)
        assertTrue(med.spriteScaleMultiplier <= high.spriteScaleMultiplier)
        assertTrue(high.spriteScaleMultiplier < hd.spriteScaleMultiplier)

        assertTrue(low.strokeScale < med.strokeScale)
        assertTrue(high.strokeScale < hd.strokeScale)
    }

    @Test
    fun proceduralTileAtlasDeterminismAndSurfaceResolution() {
        // Deterministic per-(x,y) variant selection
        val v1 = com.example.ui.screens.ProceduralTileTextureAtlas.variantIndex(190, 190)
        val v2 = com.example.ui.screens.ProceduralTileTextureAtlas.variantIndex(190, 190)
        assertEquals(v1, v2)
        assertTrue(v1 in 0 until com.example.ui.screens.ProceduralTileTextureAtlas.VARIANT_COUNT)

        // Across a plaza patch, multiple variants appear so surfaces look handcrafted
        val uniqueVariants = (180..200).flatMap { x ->
            (180..200).map { y -> com.example.ui.screens.ProceduralTileTextureAtlas.variantIndex(x, y) }
        }.toSet()
        assertEquals(com.example.ui.screens.ProceduralTileTextureAtlas.VARIANT_COUNT, uniqueVariants.size)

        // Shallow vs deep water and distinct architectural interiors resolve cleanly
        assertEquals(
            com.example.ui.screens.ProceduralTileTextureAtlas.SurfaceFamily.WATER_SHALLOW,
            com.example.ui.screens.ProceduralTileTextureAtlas.resolveSurfaceFamily(com.example.game.model.TileType.WATER, isShallowWater = true)
        )
        assertEquals(
            com.example.ui.screens.ProceduralTileTextureAtlas.SurfaceFamily.WATER_DEEP,
            com.example.ui.screens.ProceduralTileTextureAtlas.resolveSurfaceFamily(com.example.game.model.TileType.WATER, isShallowWater = false)
        )
        assertEquals(
            com.example.ui.screens.ProceduralTileTextureAtlas.SurfaceFamily.TEMPLE_FLOOR,
            com.example.ui.screens.ProceduralTileTextureAtlas.resolveSurfaceFamily(com.example.game.model.TileType.TEMPLE_FLOOR, isShallowWater = false)
        )
        assertEquals(
            com.example.ui.screens.ProceduralTileTextureAtlas.SurfaceFamily.WOOD_PLANK_FLOOR,
            com.example.ui.screens.ProceduralTileTextureAtlas.resolveSurfaceFamily(com.example.game.model.TileType.WOOD_PLANK_FLOOR, isShallowWater = false)
        )
        assertEquals(
            com.example.ui.screens.ProceduralTileTextureAtlas.SurfaceFamily.ARCANE_FLOOR,
            com.example.ui.screens.ProceduralTileTextureAtlas.resolveSurfaceFamily(com.example.game.model.TileType.ARCANE_FLOOR, isShallowWater = false)
        )
    }

    @Test
    fun directionalLightingAnd360CastShadowsRespondToNormalsAndCameraYaw() {
        val neutralStone = androidx.compose.ui.graphics.Color(0xFF8C8478)

        // LOW & MEDIUM leave base colors unmodified
        assertEquals(
            neutralStone,
            com.example.ui.screens.IsometricLightingEngine.shadeColorForFace(neutralStone, 1f, 0f, 0f, true, GraphicsQuality.MEDIUM)
        )

        // HIGH & HD brighten light-facing NW faces and cool-shade opposite SE faces
        val westLit = com.example.ui.screens.IsometricLightingEngine.shadeColorForFace(neutralStone, -1f, 0f, 0f, true, GraphicsQuality.HIGH)
        val eastShaded = com.example.ui.screens.IsometricLightingEngine.shadeColorForFace(neutralStone, 1f, 0f, 0f, true, GraphicsQuality.HIGH)
        val northLit = com.example.ui.screens.IsometricLightingEngine.shadeColorForFace(neutralStone, 0f, -1f, 0f, true, GraphicsQuality.HD)
        val southShaded = com.example.ui.screens.IsometricLightingEngine.shadeColorForFace(neutralStone, 0f, 1f, 0f, true, GraphicsQuality.HD)

        assertTrue("West lit face should be brighter than East shaded face", westLit.red > eastShaded.red && westLit.green > eastShaded.green)
        assertTrue("North lit face should be brighter than South shaded face", northLit.red > southShaded.red && northLit.green > southShaded.green)
        assertTrue("Shaded twilight face should lean cool indigo (B >= R)", eastShaded.blue >= eastShaded.red * 0.9f)

        // Cast shadow offset rotates exactly opposite when camera yaw spins 180 degrees
        val camA = IsometricCamera().apply { configure(1920f, 1080f, 190f, 190f, 1.0f, 45f) }
        val camB = IsometricCamera().apply { configure(1920f, 1080f, 190f, 190f, 1.0f, 225f) }
        val shadowA = com.example.ui.screens.IsometricLightingEngine.castShadowScreenOffset(camA, 1.0f)
        val shadowB = com.example.ui.screens.IsometricLightingEngine.castShadowScreenOffset(camB, 1.0f)

        assertTrue("Shadow offset at 45 deg should be non-zero", kotlin.math.hypot(shadowA.x.toDouble(), shadowA.y.toDouble()) > 1.0)
        assertEquals(-shadowA.x, shadowB.x, 0.05f)
        assertEquals(-shadowA.y, shadowB.y, 0.05f)
    }

    @Test
    fun pointLightCollectorRegistersWarmLanternsAndCoolArcaneSources() {
        val cam = IsometricCamera().apply { configure(1920f, 1080f, 190f, 190f, 1.0f, 45f) }
        val lights = ArrayList<com.example.ui.screens.ScenePointLight>()

        com.example.ui.screens.IsometricLightingEngine.collectTilePointLight(com.example.game.model.TileType.LANTERN_POST, 190, 190, 500f, 400f, cam, lights)
        com.example.ui.screens.IsometricLightingEngine.collectTilePointLight(com.example.game.model.TileType.SMELTING_FURNACE, 191, 190, 540f, 420f, cam, lights)
        com.example.ui.screens.IsometricLightingEngine.collectTilePointLight(com.example.game.model.TileType.RUNECRAFTING_TABLE, 192, 190, 580f, 440f, cam, lights)
        com.example.ui.screens.IsometricLightingEngine.collectTilePointLight(com.example.game.model.TileType.FOUNTAIN, 193, 190, 620f, 460f, cam, lights)
        com.example.ui.screens.IsometricLightingEngine.collectProjectilePointLight(com.example.game.model.ProjectileKind.FIREBALL_AOE, 510f, 410f, cam.halfW, lights)
        com.example.ui.screens.IsometricLightingEngine.collectProjectilePointLight(com.example.game.model.ProjectileKind.ARCANE_DART, 520f, 415f, cam.halfW, lights)

        assertEquals(6, lights.size)
        assertEquals(3, lights.count { !it.isArcane })
        assertEquals(3, lights.count { it.isArcane })
        assertTrue(lights.all { it.radiusPx > 0f && it.intensity > 0f })
    }

    @Test
    fun chunked400x400ViewportCullingStaysBoundedForEveryQualityTier() {
        val floor0 = WorldMaps.getFloor(0)
        val cam = IsometricCamera()
        for (tier in GraphicsQuality.entries) {
            for (yaw in listOf(0f, 45f, 135f, 270f)) {
                cam.configure(
                    viewportW = 1920f,
                    viewportH = 1080f,
                    camTileX = WorldMaps.TEMPLE_SPAWN_X.toFloat(),
                    camTileY = WorldMaps.TEMPLE_SPAWN_Y.toFloat(),
                    zoomFactor = 1.0f,
                    cameraYawDeg = yaw
                )
                val bounds = cam.visibleTileBounds(1920f, 1080f, floor0.width, floor0.height, margin = tier.cullMargin)
                val chunks = floor0.visibleChunkBounds(bounds.minTx, bounds.maxTx, bounds.minTy, bounds.maxTy)
                val visibleChunkCount = (chunks.maxChunkX - chunks.minChunkX + 1) * (chunks.maxChunkY - chunks.minChunkY + 1)
                assertTrue(
                    "Tier ${tier.name} at yaw $yaw should cull to <= 16 chunks, got $visibleChunkCount",
                    visibleChunkCount in 1..16
                )
            }
        }
    }

    @Test
    fun displaySettingsStorePersistsAndRestoresAllSettings() {
        val fakePrefs = InMemorySharedPreferences()
        val store = DisplaySettingsStore(fakePrefs)

        // Fresh install defaults
        val initial = store.load()
        assertEquals(GraphicsQuality.MEDIUM, initial.graphicsQuality)
        assertTrue(initial.showMinimap)
        assertFalse(initial.showFpsCounter)

        // Individual updates
        store.saveGraphicsQuality(GraphicsQuality.HD)
        store.saveShowMinimap(false)
        store.saveShowFpsCounter(true)

        val reloaded = DisplaySettingsStore(fakePrefs).load()
        assertEquals(GraphicsQuality.HD, reloaded.graphicsQuality)
        assertFalse(reloaded.showMinimap)
        assertTrue(reloaded.showFpsCounter)

        // Full save overwrite
        val lowPreset = DisplaySettings(
            graphicsQuality = GraphicsQuality.LOW,
            showMinimap = true,
            showFpsCounter = false
        )
        store.save(lowPreset)
        assertEquals(lowPreset, DisplaySettingsStore(fakePrefs).load())

        // Corrupted preference value falls back gracefully to MEDIUM without throwing
        fakePrefs.edit().putString(DisplaySettingsStore.KEY_GRAPHICS_QUALITY, "INVALID_TIER").apply()
        val recovered = DisplaySettingsStore(fakePrefs).load()
        assertEquals(GraphicsQuality.MEDIUM, recovered.graphicsQuality)
        assertTrue(recovered.showMinimap)
        assertFalse(recovered.showFpsCounter)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)

        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val staged = LinkedHashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = values
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in staged) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }
}
