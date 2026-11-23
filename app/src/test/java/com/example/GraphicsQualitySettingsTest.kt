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
        assertEquals(LightingDetailMode.OFF, low.lightingMode)
        assertEquals(ParticleDetailLevel.MINIMAL, low.particleLevel)
        assertEquals(1, low.treeDetailLayers)

        // MEDIUM: balanced defaults
        assertFalse(med.drawGroundClutter)
        assertTrue(med.drawWildflowers)
        assertFalse(med.drawShorelineDetails)
        assertTrue(med.drawContactShadows)
        assertTrue(med.drawCutawayRoofs)
        assertFalse(med.drawEnhancedWater)
        assertFalse(med.drawHdStonePolish)
        assertFalse(med.drawHdVignette)
        assertEquals(LightingDetailMode.STATIC_HALO, med.lightingMode)
        assertEquals(ParticleDetailLevel.MODERATE, med.particleLevel)
        assertEquals(2, med.treeDetailLayers)

        // HIGH: full terrain clutter, dynamic flickering glows, 3-layer trees, full particles
        assertTrue(high.drawGroundClutter)
        assertTrue(high.drawWildflowers)
        assertTrue(high.drawShorelineDetails)
        assertTrue(high.drawContactShadows)
        assertTrue(high.drawCutawayRoofs)
        assertFalse(high.drawEnhancedWater)
        assertFalse(high.drawHdStonePolish)
        assertFalse(high.drawHdVignette)
        assertEquals(LightingDetailMode.DYNAMIC_FLICKER, high.lightingMode)
        assertEquals(ParticleDetailLevel.FULL, high.particleLevel)
        assertEquals(3, high.treeDetailLayers)

        // HD: maximum fidelity with enhanced water, stone polish, vignette, multi-layer lighting & HD FX
        assertTrue(hd.drawGroundClutter)
        assertTrue(hd.drawWildflowers)
        assertTrue(hd.drawShorelineDetails)
        assertTrue(hd.drawContactShadows)
        assertTrue(hd.drawCutawayRoofs)
        assertTrue(hd.drawEnhancedWater)
        assertTrue(hd.drawHdStonePolish)
        assertTrue(hd.drawHdVignette)
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
