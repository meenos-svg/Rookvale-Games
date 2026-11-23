package com.example.game.data

import android.content.Context
import android.content.SharedPreferences
import com.example.game.model.DisplaySettings
import com.example.game.model.GraphicsQuality

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
        return DisplaySettings(
            graphicsQuality = quality,
            showMinimap = showMinimap,
            showFpsCounter = showFpsCounter
        )
    }

    fun save(settings: DisplaySettings) {
        prefs.edit()
            .putString(KEY_GRAPHICS_QUALITY, settings.graphicsQuality.name)
            .putBoolean(KEY_SHOW_MINIMAP, settings.showMinimap)
            .putBoolean(KEY_SHOW_FPS_COUNTER, settings.showFpsCounter)
            .apply()
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

    companion object {
        const val PREFS_NAME = "rookvale_display_settings"
        const val KEY_GRAPHICS_QUALITY = "graphics_quality"
        const val KEY_SHOW_MINIMAP = "show_minimap"
        const val KEY_SHOW_FPS_COUNTER = "show_fps_counter"
    }
}
