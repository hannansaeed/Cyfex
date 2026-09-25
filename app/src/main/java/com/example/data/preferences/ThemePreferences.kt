package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.theme.DisplayMode
import com.example.ui.theme.ThemeConfig
import com.example.ui.theme.ThemePalette

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cyfex_theme_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PALETTE = "key_palette"
        private const val KEY_DISPLAY_MODE = "key_display_mode"
    }

    fun getThemeConfig(): ThemeConfig {
        val paletteName = prefs.getString(KEY_PALETTE, ThemePalette.DETECTIVE_ORANGE.name)
        val displayModeName = prefs.getString(KEY_DISPLAY_MODE, DisplayMode.LIGHT.name)

        val palette = try {
            ThemePalette.valueOf(paletteName ?: ThemePalette.DETECTIVE_ORANGE.name)
        } catch (e: Exception) {
            ThemePalette.DETECTIVE_ORANGE
        }

        val displayMode = try {
            DisplayMode.valueOf(displayModeName ?: DisplayMode.LIGHT.name)
        } catch (e: Exception) {
            DisplayMode.LIGHT
        }

        return ThemeConfig(palette = palette, displayMode = displayMode)
    }

    fun saveThemeConfig(config: ThemeConfig) {
        prefs.edit()
            .putString(KEY_PALETTE, config.palette.name)
            .putString(KEY_DISPLAY_MODE, config.displayMode.name)
            .apply()
    }
}
