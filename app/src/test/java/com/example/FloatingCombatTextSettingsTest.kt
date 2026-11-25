package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.game.data.DisplaySettingsStore
import com.example.game.model.DisplaySettings
import com.example.game.model.FloatingTextKind
import com.example.game.model.GraphicsQuality
import com.example.ui.screens.SettingsPanelContent
import com.example.ui.theme.RookvaleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FloatingCombatTextSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultDisplaySettingsRendersAllFloatingTextKinds() {
        val defaults = DisplaySettings()
        assertTrue(defaults.showDamageNumbers)
        assertTrue(defaults.showHealingTicks)
        assertTrue(defaults.showProcNames)
        assertTrue(defaults.showStatusAndBlockTicks)
        assertTrue(defaults.showXpAndSkillGains)

        for (kind in FloatingTextKind.entries) {
            assertTrue("Expected default settings to render $kind", defaults.shouldRenderFloatingText(kind))
        }
    }

    @Test
    fun eachFloatingTextToggleFiltersOnlyItsMappedKindsAndNeverSuppressesInfo() {
        val noDamage = DisplaySettings(showDamageNumbers = false)
        assertFalse(noDamage.shouldRenderFloatingText(FloatingTextKind.DAMAGE_MONSTER))
        assertFalse(noDamage.shouldRenderFloatingText(FloatingTextKind.DAMAGE_PLAYER))
        assertTrue(noDamage.shouldRenderFloatingText(FloatingTextKind.HEAL))
        assertTrue(noDamage.shouldRenderFloatingText(FloatingTextKind.PROC_CALLOUT))
        assertTrue(noDamage.shouldRenderFloatingText(FloatingTextKind.POISON))
        assertTrue(noDamage.shouldRenderFloatingText(FloatingTextKind.XP))
        assertTrue(noDamage.shouldRenderFloatingText(FloatingTextKind.INFO))

        val noHealing = DisplaySettings(showHealingTicks = false)
        assertFalse(noHealing.shouldRenderFloatingText(FloatingTextKind.HEAL))
        assertFalse(noHealing.shouldRenderFloatingText(FloatingTextKind.MANA))
        assertTrue(noHealing.shouldRenderFloatingText(FloatingTextKind.DAMAGE_MONSTER))
        assertTrue(noHealing.shouldRenderFloatingText(FloatingTextKind.INFO))

        val noProcs = DisplaySettings(showProcNames = false)
        assertFalse(noProcs.shouldRenderFloatingText(FloatingTextKind.PROC_CALLOUT))
        assertTrue(noProcs.shouldRenderFloatingText(FloatingTextKind.DAMAGE_MONSTER))
        assertTrue(noProcs.shouldRenderFloatingText(FloatingTextKind.SKILL_UP))
        assertTrue(noProcs.shouldRenderFloatingText(FloatingTextKind.INFO))

        val noStatusOrBlocks = DisplaySettings(showStatusAndBlockTicks = false)
        assertFalse(noStatusOrBlocks.shouldRenderFloatingText(FloatingTextKind.POISON))
        assertFalse(noStatusOrBlocks.shouldRenderFloatingText(FloatingTextKind.FIRE))
        assertFalse(noStatusOrBlocks.shouldRenderFloatingText(FloatingTextKind.BLOCK))
        assertTrue(noStatusOrBlocks.shouldRenderFloatingText(FloatingTextKind.DAMAGE_PLAYER))
        assertTrue(noStatusOrBlocks.shouldRenderFloatingText(FloatingTextKind.INFO))

        val noXp = DisplaySettings(showXpAndSkillGains = false)
        assertFalse(noXp.shouldRenderFloatingText(FloatingTextKind.XP))
        assertFalse(noXp.shouldRenderFloatingText(FloatingTextKind.SKILL_UP))
        assertTrue(noXp.shouldRenderFloatingText(FloatingTextKind.PROC_CALLOUT))
        assertTrue(noXp.shouldRenderFloatingText(FloatingTextKind.INFO))

        // Even with all 5 combat text groups disabled, system INFO notices remain visible
        val allCombatOff = DisplaySettings(
            showDamageNumbers = false,
            showHealingTicks = false,
            showProcNames = false,
            showStatusAndBlockTicks = false,
            showXpAndSkillGains = false
        )
        for (kind in FloatingTextKind.entries) {
            if (kind == FloatingTextKind.INFO) {
                assertTrue("System INFO must stay visible", allCombatOff.shouldRenderFloatingText(kind))
            } else {
                assertFalse("Expected $kind to be suppressed when all 5 groups are off", allCombatOff.shouldRenderFloatingText(kind))
            }
        }
    }

    @Test
    fun displaySettingsStorePersistsAndRestoresAllFiveCombatTextToggles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_fct_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val store = DisplaySettingsStore(prefs)
        val initial = store.load()
        assertTrue(initial.showDamageNumbers)
        assertTrue(initial.showHealingTicks)
        assertTrue(initial.showProcNames)
        assertTrue(initial.showStatusAndBlockTicks)
        assertTrue(initial.showXpAndSkillGains)

        store.saveShowDamageNumbers(false)
        store.saveShowHealingTicks(false)
        store.saveShowProcNames(true)
        store.saveShowStatusAndBlockTicks(false)
        store.saveShowXpAndSkillGains(false)

        val reloaded = DisplaySettingsStore(prefs).load()
        assertFalse(reloaded.showDamageNumbers)
        assertFalse(reloaded.showHealingTicks)
        assertTrue(reloaded.showProcNames)
        assertFalse(reloaded.showStatusAndBlockTicks)
        assertFalse(reloaded.showXpAndSkillGains)

        val customFull = DisplaySettings(
            graphicsQuality = GraphicsQuality.HIGH,
            showMinimap = false,
            showFpsCounter = true,
            showDamageNumbers = true,
            showHealingTicks = false,
            showProcNames = false,
            showStatusAndBlockTicks = true,
            showXpAndSkillGains = false
        )
        store.save(customFull)
        assertEquals(customFull, DisplaySettingsStore(prefs).load())
    }

    @Test
    fun titleSettingsModeHidesCombatTextSection() {
        composeRule.setContent {
            RookvaleTheme {
                SettingsPanelContent(
                    displaySettings = DisplaySettings(),
                    onSelectGraphicsQuality = {},
                    onToggleMinimap = {},
                    onToggleFpsCounter = {},
                    showCombatTextSection = false
                )
            }
        }

        composeRule.onNodeWithTag("settings_panel_content").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_combat_text_section").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_toggle_damage_numbers").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_toggle_healing_ticks").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_toggle_proc_names").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_toggle_status_blocks").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_toggle_xp_gains").assertDoesNotExist()
    }

    @Test
    fun inGameSettingsModeDisplaysAllFiveCombatTextSwitchesAndFiresCallbacks() {
        var damageVal = true
        var healingVal = true
        var procsVal = true
        var statusVal = true
        var xpVal = true

        composeRule.setContent {
            RookvaleTheme {
                SettingsPanelContent(
                    displaySettings = DisplaySettings(),
                    onSelectGraphicsQuality = {},
                    onToggleMinimap = {},
                    onToggleFpsCounter = {},
                    showCombatTextSection = true,
                    onToggleDamageNumbers = { damageVal = it },
                    onToggleHealingTicks = { healingVal = it },
                    onToggleProcNames = { procsVal = it },
                    onToggleStatusAndBlockTicks = { statusVal = it },
                    onToggleXpAndSkillGains = { xpVal = it }
                )
            }
        }

        composeRule.onNodeWithTag("settings_combat_text_section").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings_toggle_damage_numbers").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_toggle_healing_ticks").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_toggle_proc_names").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_toggle_status_blocks").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_toggle_xp_gains").performScrollTo().assertIsDisplayed().performClick()

        assertFalse(damageVal)
        assertFalse(healingVal)
        assertFalse(procsVal)
        assertFalse(statusVal)
        assertFalse(xpVal)
    }
}
