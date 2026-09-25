package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

val LocalThemeConfig = compositionLocalOf { mutableStateOf(ThemeConfig()) }

@Composable
fun CyfexTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (config.displayMode) {
        DisplayMode.SYSTEM -> systemDark
        DisplayMode.DARK -> true
        DisplayMode.LIGHT -> false
    }

    val colorScheme = buildColorScheme(config.palette, isDark)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun ThreatMonitorTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CyfexTheme(config = ThemeConfig(), content = content)
}
