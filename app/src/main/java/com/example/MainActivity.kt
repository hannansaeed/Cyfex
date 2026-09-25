package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.data.preferences.ThemePreferences
import com.example.ui.navigation.ThreatMonitorNavHost
import com.example.ui.theme.CyfexTheme
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.ThemeConfig
import com.example.ui.viewmodel.ThreatMonitorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ThreatMonitorViewModel by viewModels()
    private lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themePreferences = ThemePreferences(this)
        enableEdgeToEdge()

        setContent {
            val initialConfig = remember { themePreferences.getThemeConfig() }
            val themeConfigState = remember { mutableStateOf(initialConfig) }

            // Whenever themeConfigState changes, persist it to ThemePreferences
            val currentConfig = themeConfigState.value
            remember(currentConfig) {
                themePreferences.saveThemeConfig(currentConfig)
                true
            }

            CompositionLocalProvider(LocalThemeConfig provides themeConfigState) {
                CyfexTheme(config = themeConfigState.value) {
                    ThreatMonitorNavHost(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDeviceInfo()
    }
}
