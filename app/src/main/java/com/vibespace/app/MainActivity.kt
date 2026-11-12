package com.vibespace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vibespace.app.navigation.VibeNavigation
import com.vibespace.core.designsystem.theme.VibeSpaceTheme
import com.vibespace.feature.settings.repository.ThemeMode

/**
 * Main Activity for VibeSpace.
 * Kept lean and decoupled from domain logic.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { enableEdgeToEdge() } catch (_: Throwable) {}

        val app = application as VibeSpaceApplication

        setContent {
            val settingsFlow = remember { app.settingsRepository.observeSettings() }
            val settingsState by settingsFlow.collectAsState(
                initial = com.vibespace.feature.settings.repository.UserSettings()
            )

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = settingsState.themeMode == ThemeMode.DARK || (settingsState.themeMode == ThemeMode.SYSTEM && isSystemDark)

            VibeSpaceTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .imePadding()
                ) {
                    VibeNavigation(app = app)
                }
            }
        }
    }
}
