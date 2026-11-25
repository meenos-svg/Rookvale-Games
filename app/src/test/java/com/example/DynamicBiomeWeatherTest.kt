package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.game.data.DisplaySettingsStore
import com.example.game.model.DisplaySettings
import com.example.game.model.GameWorldState
import com.example.game.model.GraphicsQuality
import com.example.game.world.BiomeWeatherSystem
import com.example.game.world.ResolvedWeatherState
import com.example.game.world.WeatherType
import com.example.game.world.WorldRegionMeta
import com.example.ui.screens.AdminControlHudOverlay
import com.example.ui.screens.LiveWeatherHudBadge
import com.example.ui.screens.SettingsPanelContent
import com.example.ui.theme.RookvaleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DynamicBiomeWeatherTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allTwelveOverlandRegionsHaveValidBiomeFrequencyTablesSummingTo100() {
        assertEquals(12, WorldRegionMeta.ALL_REGIONS.size)

        for (region in WorldRegionMeta.ALL_REGIONS) {
            val weights = BiomeWeatherSystem.biomeFrequencies[region.regionId]
            assertTrue("Missing weather weights for ${region.regionId}", weights != null && weights.isNotEmpty())
            val total = weights!!.sumOf { it.weightPercent }
            assertEquals("Weather weights for ${region.regionId} must sum to 100", 100, total)
            assertTrue("All weather weights must be positive", weights.all { it.weightPercent > 0 })
        }

        // Verify authentic biome affinities
        assertEquals(
            WeatherType.SNOW,
            BiomeWeatherSystem.frequenciesForRegion(WorldRegionMeta.FROSTCREST_HIGHLANDS.regionId).maxBy { it.weightPercent }.weather
        )
        assertEquals(
            WeatherType.VOLCANIC_ASH,
            BiomeWeatherSystem.frequenciesForRegion(WorldRegionMeta.EMBER_ISLES.regionId).maxBy { it.weightPercent }.weather
        )
        assertEquals(
            WeatherType.SWAMP_MIST,
            BiomeWeatherSystem.frequenciesForRegion(WorldRegionMeta.WRITHING_MARSHES.regionId).maxBy { it.weightPercent }.weather
        )
        assertEquals(
            WeatherType.FALLING_LEAVES,
            BiomeWeatherSystem.frequenciesForRegion(WorldRegionMeta.SPIREWOOD_WEST.regionId).maxBy { it.weightPercent }.weather
        )
        assertEquals(
            WeatherType.LIGHT_RAIN,
            BiomeWeatherSystem.frequenciesForRegion(WorldRegionMeta.MAELSTROM_JUNGLE_WEST.regionId).maxBy { it.weightPercent }.weather
        )
    }

    @Test
    fun deterministicSharedClockProducesIdenticalWeatherAndSmoothTransitions() {
        val region = WorldRegionMeta.FROSTCREST_HIGHLANDS.regionId
        val sampleUtc = 1_760_000_123_456L

        // Identical inputs always yield identical output (multiplayer sync without network cost)
        val a = BiomeWeatherSystem.resolveWeather(region, floor = 0, utcMillis = sampleUtc)
        val b = BiomeWeatherSystem.resolveWeather(region, floor = 0, utcMillis = sampleUtc)
        assertEquals(a, b)
        assertFalse(a.isAdminOverride)
        assertFalse(a.isSuppressedUnderground)

        // Find an epoch boundary where weather changes and verify smooth transitionBlend progression
        var changingEpoch = -1L
        for (ep in 10L..300L) {
            if (BiomeWeatherSystem.weatherForEpoch(region, ep) != BiomeWeatherSystem.weatherForEpoch(region, ep - 1L)) {
                changingEpoch = ep
                break
            }
        }
        assertTrue("Expected at least one weather transition within 300 epochs", changingEpoch > 0L)

        val boundaryMs = changingEpoch * BiomeWeatherSystem.WEATHER_EPOCH_MS
        val startState = BiomeWeatherSystem.resolveWeather(region, floor = 0, utcMillis = boundaryMs)
        val midState = BiomeWeatherSystem.resolveWeather(region, floor = 0, utcMillis = boundaryMs + BiomeWeatherSystem.TRANSITION_WINDOW_MS / 2)
        val endState = BiomeWeatherSystem.resolveWeather(region, floor = 0, utcMillis = boundaryMs + BiomeWeatherSystem.TRANSITION_WINDOW_MS + 1000L)

        assertEquals(0f, startState.transitionBlend, 0.001f)
        assertTrue("Expected smooth mid transition blend around 0.5, got ${midState.transitionBlend}", midState.transitionBlend in 0.45f..0.55f)
        assertEquals(1f, endState.transitionBlend, 0.001f)

        // Statistical distribution over 1000 epochs should reflect Frostcrest's heavy Snow weighting (65%)
        val snowCount = (0L until 1000L).count { ep ->
            BiomeWeatherSystem.weatherForEpoch(region, ep) == WeatherType.SNOW
        }
        assertTrue("Expected Frostcrest snow frequency near 65% (550..750/1000), got $snowCount", snowCount in 550..750)
    }

    @Test
    fun undergroundFloorsSuppressWeatherAndAdminOverrideAppliesOverland() {
        val region = WorldRegionMeta.ROOKVALE_VALLEY.regionId
        val nowMs = 1_760_000_000_000L

        // Dungeon floor suppresses weather even if admin override is set
        val underground = BiomeWeatherSystem.resolveWeather(
            regionId = region,
            floor = -1,
            utcMillis = nowMs,
            adminOverride = WeatherType.SNOW
        )
        assertTrue(underground.isSuppressedUnderground)
        assertEquals(WeatherType.CLEAR, underground.activeWeather)
        assertEquals(0f, underground.intensity, 0.001f)

        // Overland admin override forces exact weather at full intensity
        val forcedAsh = BiomeWeatherSystem.resolveWeather(
            regionId = region,
            floor = 0,
            utcMillis = nowMs,
            adminOverride = WeatherType.VOLCANIC_ASH
        )
        assertFalse(forcedAsh.isSuppressedUnderground)
        assertTrue(forcedAsh.isAdminOverride)
        assertEquals(WeatherType.VOLCANIC_ASH, forcedAsh.activeWeather)
        assertEquals(1f, forcedAsh.intensity, 0.001f)

        val forcedClear = BiomeWeatherSystem.resolveWeather(
            regionId = region,
            floor = 0,
            utcMillis = nowMs,
            adminOverride = WeatherType.CLEAR
        )
        assertTrue(forcedClear.isAdminOverride)
        assertEquals(WeatherType.CLEAR, forcedClear.activeWeather)
        assertEquals(0f, forcedClear.intensity, 0.001f)
    }

    @Test
    fun graphicsQualityTiersScaleWeatherParticlesAndShadersMonotonically() {
        val low = BiomeWeatherSystem.visualProfileFor(GraphicsQuality.LOW)
        val med = BiomeWeatherSystem.visualProfileFor(GraphicsQuality.MEDIUM)
        val high = BiomeWeatherSystem.visualProfileFor(GraphicsQuality.HIGH)
        val hd = BiomeWeatherSystem.visualProfileFor(GraphicsQuality.HD)

        assertEquals(0, low.particleCount)
        assertFalse(low.enableGroundRipples)
        assertFalse(low.enableAtmosphericGradient)
        assertFalse(low.enableAgslShader)

        assertTrue(med.particleCount in 24..45)
        assertFalse(med.enableGroundRipples)
        assertFalse(med.enableAtmosphericGradient)
        assertFalse(med.enableAgslShader)

        assertTrue(high.particleCount > med.particleCount)
        assertTrue(high.enableGroundRipples)
        assertTrue(high.enableAtmosphericGradient)
        assertFalse(high.enableAgslShader)

        assertTrue(hd.particleCount > high.particleCount)
        assertTrue(hd.enableGroundRipples)
        assertTrue(hd.enableAtmosphericGradient)
        assertTrue(hd.enableAgslShader)
    }

    @Test
    fun displaySettingsStorePersistsShowWeatherEffectsPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_weather_settings_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val store = DisplaySettingsStore(prefs)
        assertTrue(store.load().showWeatherEffects)

        store.saveShowWeatherEffects(false)
        assertFalse(DisplaySettingsStore(prefs).load().showWeatherEffects)

        store.save(DisplaySettings(showWeatherEffects = true, graphicsQuality = GraphicsQuality.HD))
        val reloaded = DisplaySettingsStore(prefs).load()
        assertTrue(reloaded.showWeatherEffects)
        assertEquals(GraphicsQuality.HD, reloaded.graphicsQuality)
    }

    @Test
    fun settingsPanelWeatherToggleAndAdminOverrideChipsWorkInCompose() {
        var weatherEnabled = true
        var selectedOverride: WeatherType? = null

        composeRule.setContent {
            RookvaleTheme {
                SettingsPanelContent(
                    displaySettings = DisplaySettings(showWeatherEffects = true),
                    onSelectGraphicsQuality = {},
                    onToggleMinimap = {},
                    onToggleFpsCounter = {},
                    onToggleWeatherEffects = { weatherEnabled = it },
                    showCombatTextSection = false
                )
            }
        }

        composeRule.onNodeWithTag("settings_toggle_weather").performScrollTo().assertIsDisplayed().performClick()
        assertFalse(weatherEnabled)
    }

    @Test
    fun liveWeatherHudBadgeAndAdminWeatherOverrideUiRespondCorrectly() {
        var chosenOverride: WeatherType? = WeatherType.LIGHT_RAIN
        val adminState = GameWorldState(
            isAdmin = true,
            floor = 0,
            adminWeatherOverride = WeatherType.LIGHT_RAIN
        )

        composeRule.setContent {
            RookvaleTheme {
                LiveWeatherHudBadge(
                    weather = ResolvedWeatherState(
                        regionId = WorldRegionMeta.ROOKVALE_VALLEY.regionId,
                        activeWeather = WeatherType.SNOW,
                        previousWeather = WeatherType.SNOW,
                        transitionBlend = 1f,
                        intensity = 1f,
                        isAdminOverride = true,
                        isSuppressedUnderground = false
                    )
                )
                AdminControlHudOverlay(
                    state = adminState,
                    expanded = true,
                    onToggleExpanded = {},
                    onToggleSpectator = {},
                    onSetTimeOfDay = { _, _ -> },
                    onSetWeatherOverride = { chosenOverride = it },
                    onSetLevel = {},
                    onGrantEquipment = {}
                )
            }
        }

        composeRule.onNodeWithTag("live_weather_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Snowfall").assertIsDisplayed()
        composeRule.onNodeWithText("OVR").assertIsDisplayed()

        composeRule.onNodeWithTag("admin_weather_chip_snow").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(WeatherType.SNOW, chosenOverride)

        composeRule.onNodeWithTag("admin_weather_chip_auto").performScrollTo().assertIsDisplayed().performClick()
        assertNull(chosenOverride)
    }
}
