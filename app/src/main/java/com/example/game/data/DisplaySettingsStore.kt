package com.example.game.data

import android.content.Context
import android.content.SharedPreferences
import com.example.game.model.DisplaySettings
import com.example.game.model.GraphicsQuality
import com.example.game.model.HudLayout
import com.example.game.model.VitalsStyle

/**
 * Local device preferences store for Graphics Quality, Minimap visibility, and Live FPS Counter.
 * Kept strictly device-local via SharedPreferences so GPU/battery settings stay tailored per phone
 * without altering Supabase cloud character saves.
 */
class DisplaySettingsStore(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun load(): DisplaySettings {
        val quality = GraphicsQuality.fromStorageName(prefs.getString(KEY_GRAPHICS_QUALITY, null))
        val showMinimap = prefs.getBoolean(KEY_SHOW_MINIMAP, true)
        val showFpsCounter = prefs.getBoolean(KEY_SHOW_FPS_COUNTER, false)
        val showWeatherEffects = prefs.getBoolean(KEY_SHOW_WEATHER_EFFECTS, true)
        val showDamageNumbers = prefs.getBoolean(KEY_SHOW_DAMAGE_NUMBERS, true)
        val showHealingTicks = prefs.getBoolean(KEY_SHOW_HEALING_TICKS, true)
        val showProcNames = prefs.getBoolean(KEY_SHOW_PROC_NAMES, true)
        val showStatusAndBlockTicks = prefs.getBoolean(KEY_SHOW_STATUS_BLOCK_TICKS, true)
        val showXpAndSkillGains = prefs.getBoolean(KEY_SHOW_XP_SKILL_GAINS, true)
        return DisplaySettings(
            graphicsQuality = quality,
            showMinimap = showMinimap,
            showFpsCounter = showFpsCounter,
            showWeatherEffects = showWeatherEffects,
            showDamageNumbers = showDamageNumbers,
            showHealingTicks = showHealingTicks,
            showProcNames = showProcNames,
            showStatusAndBlockTicks = showStatusAndBlockTicks,
            showXpAndSkillGains = showXpAndSkillGains,
            hud = HudLayout(
                vitalsStyle = VitalsStyle.entries.firstOrNull { it.name == prefs.getString(KEY_HUD_VITALS, null) } ?: VitalsStyle.ORB,
                hotbarSize = prefs.getInt(KEY_HUD_HOTBAR_SIZE, 7).let { if (it in HudLayout.HOTBAR_SIZES) it else 7 },
                offsets = HudLayout.decodeOffsets(prefs.getString(KEY_HUD_OFFSETS, null)),
                hidden = HudLayout.decodeHidden(prefs.getString(KEY_HUD_HIDDEN, null))
            )
        )
    }

    fun saveHudLayout(hud: HudLayout) {
        prefs.edit()
            .putString(KEY_HUD_VITALS, hud.vitalsStyle.name)
            .putInt(KEY_HUD_HOTBAR_SIZE, hud.hotbarSize)
            .putString(KEY_HUD_OFFSETS, hud.encodeOffsets())
            .putString(KEY_HUD_HIDDEN, hud.encodeHidden())
            .apply()
    }

    fun save(settings: DisplaySettings) {
        prefs.edit()
            .putString(KEY_GRAPHICS_QUALITY, settings.graphicsQuality.name)
            .putBoolean(KEY_SHOW_MINIMAP, settings.showMinimap)
            .putBoolean(KEY_SHOW_FPS_COUNTER, settings.showFpsCounter)
            .putBoolean(KEY_SHOW_WEATHER_EFFECTS, settings.showWeatherEffects)
            .putBoolean(KEY_SHOW_DAMAGE_NUMBERS, settings.showDamageNumbers)
            .putBoolean(KEY_SHOW_HEALING_TICKS, settings.showHealingTicks)
            .putBoolean(KEY_SHOW_PROC_NAMES, settings.showProcNames)
            .putBoolean(KEY_SHOW_STATUS_BLOCK_TICKS, settings.showStatusAndBlockTicks)
            .putBoolean(KEY_SHOW_XP_SKILL_GAINS, settings.showXpAndSkillGains)
            .apply()
        saveHudLayout(settings.hud)
    }

    fun saveGraphicsQuality(quality: GraphicsQuality) {
        prefs.edit()
            .putString(KEY_GRAPHICS_QUALITY, quality.name)
            .apply()
    }

    fun saveShowMinimap(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_MINIMAP, show)
            .apply()
    }

    fun saveShowFpsCounter(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_FPS_COUNTER, show)
            .apply()
    }

    fun saveShowWeatherEffects(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_WEATHER_EFFECTS, show)
            .apply()
    }

    fun saveShowDamageNumbers(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_DAMAGE_NUMBERS, show)
            .apply()
    }

    fun saveShowHealingTicks(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_HEALING_TICKS, show)
            .apply()
    }

    fun saveShowProcNames(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_PROC_NAMES, show)
            .apply()
    }

    fun saveShowStatusAndBlockTicks(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_STATUS_BLOCK_TICKS, show)
            .apply()
    }

    fun saveShowXpAndSkillGains(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_XP_SKILL_GAINS, show)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "rookvale_display_settings"
        const val KEY_GRAPHICS_QUALITY = "graphics_quality"
        const val KEY_SHOW_MINIMAP = "show_minimap"
        const val KEY_SHOW_FPS_COUNTER = "show_fps_counter"
        const val KEY_SHOW_WEATHER_EFFECTS = "show_weather_effects"
        const val KEY_SHOW_DAMAGE_NUMBERS = "show_damage_numbers"
        const val KEY_SHOW_HEALING_TICKS = "show_healing_ticks"
        const val KEY_SHOW_PROC_NAMES = "show_proc_names"
        const val KEY_SHOW_STATUS_BLOCK_TICKS = "show_status_block_ticks"
        const val KEY_SHOW_XP_SKILL_GAINS = "show_xp_skill_gains"
        const val KEY_HUD_VITALS = "hud_vitals_style"
        const val KEY_HUD_HOTBAR_SIZE = "hud_hotbar_size"
        const val KEY_HUD_OFFSETS = "hud_offsets"
        const val KEY_HUD_HIDDEN = "hud_hidden"
    }
}
