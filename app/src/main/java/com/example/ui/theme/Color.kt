package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Detective Orange Palette (Matching Cat Logo)
val OrangePrimaryDark = Color(0xFFFF7A00)
val OrangePrimaryLight = Color(0xFFE65100)
val OrangeSecondaryDark = Color(0xFFFFB049)
val OrangeSecondaryLight = Color(0xFFF57C00)

// Clean neutral dark tones for Dark Mode (Removes muddy brownish/orangeish background shade)
val OrangeBackgroundDark = Color(0xFF0F1117)
val OrangeSurfaceDark = Color(0xFF161A22)
val OrangeSurfaceVariantDark = Color(0xFF212631)
val OrangeOutlineDark = Color(0xFF2F3746)
val OrangeTextPrimaryDark = Color(0xFFF1F5F9)
val OrangeTextSecondaryDark = Color(0xFF94A3B8)

// Crisp Light Mode (Orange Default)
val OrangeBackgroundLight = Color(0xFFFFF9F5)
val OrangeSurfaceLight = Color(0xFFFFFFFF)
val OrangeSurfaceVariantLight = Color(0xFFFDF0E6)
val OrangeOutlineLight = Color(0xFFEAD5C3)
val OrangeTextPrimaryLight = Color(0xFF1E1208)
val OrangeTextSecondaryLight = Color(0xFF6B584B)

// Cyber Cyan Palette
val CyanPrimary = Color(0xFF00E5FF)
val CyanBackgroundDark = Color(0xFF0A0F1D)
val CyanSurfaceDark = Color(0xFF131B2E)
val CyanSurfaceVariantDark = Color(0xFF1C2742)
val CyanOutlineDark = Color(0xFF2B3A5A)
val CyanTextPrimaryDark = Color(0xFFEDF2F7)
val CyanTextSecondaryDark = Color(0xFF94A3B8)

// Midnight OLED Palette
val OledPrimary = Color(0xFFBB86FC)
val OledBackgroundDark = Color(0xFF000000)
val OledSurfaceDark = Color(0xFF0E0E12)
val OledSurfaceVariantDark = Color(0xFF18181F)
val OledOutlineDark = Color(0xFF2D2D38)

// Emerald Sentinel Palette
val EmeraldPrimary = Color(0xFF00E676)
val EmeraldBackgroundDark = Color(0xFF07140B)
val EmeraldSurfaceDark = Color(0xFF0D2214)
val EmeraldSurfaceVariantDark = Color(0xFF14331E)
val EmeraldOutlineDark = Color(0xFF1C472A)

// Risk Indicators
val RiskLow = Color(0xFF00E676)
val RiskMedium = Color(0xFFFFB300)
val RiskHigh = Color(0xFFFF5722)
val RiskCritical = Color(0xFFFF1744)

// Legacy compatibility values (dynamically mapped where possible)
val CyberBackground = OrangeBackgroundDark
val CyberSurface = OrangeSurfaceDark
val CyberSurfaceVariant = OrangeSurfaceVariantDark
val CyberPrimary = OrangePrimaryDark
val CyberOnPrimary = Color(0xFF2A1000)
val CyberSecondary = OrangeSecondaryDark
val CyberOnSecondary = Color(0xFF2A1000)
val CyberTertiary = RiskLow
val CyberOutline = OrangeOutlineDark
val CyberTextPrimary = OrangeTextPrimaryDark
val CyberTextSecondary = OrangeTextSecondaryDark
val CyberTextTertiary = Color(0xFF8D7B6E)

enum class ThemePalette(val displayName: String, val primaryColor: Color) {
    DETECTIVE_ORANGE("Detective Orange (Default)", Color(0xFFFF7A00)),
    CYBER_CYAN("Cyber Cyan", Color(0xFF00E5FF)),
    MIDNIGHT_OLED("Midnight OLED", Color(0xFFBB86FC)),
    EMERALD_SENTINEL("Emerald Sentinel", Color(0xFF00E676))
}

enum class DisplayMode(val displayName: String) {
    SYSTEM("System Default"),
    DARK("Dark Mode"),
    LIGHT("Light Mode")
}

data class ThemeConfig(
    val palette: ThemePalette = ThemePalette.DETECTIVE_ORANGE,
    val displayMode: DisplayMode = DisplayMode.LIGHT
)

fun buildColorScheme(palette: ThemePalette, isDark: Boolean): ColorScheme {
    return when (palette) {
        ThemePalette.DETECTIVE_ORANGE -> {
            if (isDark) {
                darkColorScheme(
                    primary = OrangePrimaryDark,
                    onPrimary = Color(0xFF2A1000),
                    secondary = OrangeSecondaryDark,
                    onSecondary = Color(0xFF2A1000),
                    background = OrangeBackgroundDark,
                    onBackground = OrangeTextPrimaryDark,
                    surface = OrangeSurfaceDark,
                    onSurface = OrangeTextPrimaryDark,
                    surfaceVariant = OrangeSurfaceVariantDark,
                    onSurfaceVariant = OrangeTextSecondaryDark,
                    outline = OrangeOutlineDark,
                    error = RiskCritical,
                    onError = Color.White
                )
            } else {
                lightColorScheme(
                    primary = OrangePrimaryLight,
                    onPrimary = Color.White,
                    secondary = OrangeSecondaryLight,
                    onSecondary = Color.White,
                    background = OrangeBackgroundLight,
                    onBackground = OrangeTextPrimaryLight,
                    surface = OrangeSurfaceLight,
                    onSurface = OrangeTextPrimaryLight,
                    surfaceVariant = OrangeSurfaceVariantLight,
                    onSurfaceVariant = OrangeTextSecondaryLight,
                    outline = OrangeOutlineLight,
                    error = RiskCritical,
                    onError = Color.White
                )
            }
        }
        ThemePalette.CYBER_CYAN -> {
            if (isDark) {
                darkColorScheme(
                    primary = CyanPrimary,
                    onPrimary = Color(0xFF00363F),
                    secondary = Color(0xFF7C4DFF),
                    onSecondary = Color.White,
                    background = CyanBackgroundDark,
                    onBackground = CyanTextPrimaryDark,
                    surface = CyanSurfaceDark,
                    onSurface = CyanTextPrimaryDark,
                    surfaceVariant = CyanSurfaceVariantDark,
                    onSurfaceVariant = CyanTextSecondaryDark,
                    outline = CyanOutlineDark,
                    error = RiskCritical,
                    onError = Color.White
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0097A7),
                    onPrimary = Color.White,
                    secondary = Color(0xFF536DFE),
                    onSecondary = Color.White,
                    background = Color(0xFFF4F8FA),
                    onBackground = Color(0xFF0D1B2A),
                    surface = Color.White,
                    onSurface = Color(0xFF0D1B2A),
                    surfaceVariant = Color(0xFFE0F2F1),
                    onSurfaceVariant = Color(0xFF455A64),
                    outline = Color(0xFFB0BEC5),
                    error = RiskCritical,
                    onError = Color.White
                )
            }
        }
        ThemePalette.MIDNIGHT_OLED -> {
            darkColorScheme(
                primary = OledPrimary,
                onPrimary = Color.Black,
                secondary = Color(0xFF03DAC6),
                onSecondary = Color.Black,
                background = OledBackgroundDark,
                onBackground = Color.White,
                surface = OledSurfaceDark,
                onSurface = Color.White,
                surfaceVariant = OledSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFAAAAAA),
                outline = OledOutlineDark,
                error = RiskCritical,
                onError = Color.White
            )
        }
        ThemePalette.EMERALD_SENTINEL -> {
            if (isDark) {
                darkColorScheme(
                    primary = EmeraldPrimary,
                    onPrimary = Color(0xFF003314),
                    secondary = Color(0xFF69F0AE),
                    onSecondary = Color(0xFF003314),
                    background = EmeraldBackgroundDark,
                    onBackground = Color(0xFFE8F5E9),
                    surface = EmeraldSurfaceDark,
                    onSurface = Color(0xFFE8F5E9),
                    surfaceVariant = EmeraldSurfaceVariantDark,
                    onSurfaceVariant = Color(0xFFA5D6A7),
                    outline = EmeraldOutlineDark,
                    error = RiskCritical,
                    onError = Color.White
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2E7D32),
                    onPrimary = Color.White,
                    secondary = Color(0xFF00C853),
                    onSecondary = Color.White,
                    background = Color(0xFFF1F8E9),
                    onBackground = Color(0xFF1B5E20),
                    surface = Color.White,
                    onSurface = Color(0xFF1B5E20),
                    surfaceVariant = Color(0xFFDCEDC8),
                    onSurfaceVariant = Color(0xFF33691E),
                    outline = Color(0xFFC5E1A5),
                    error = RiskCritical,
                    onError = Color.White
                )
            }
        }
    }
}
