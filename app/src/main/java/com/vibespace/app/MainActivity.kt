package com.vibespace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        enableEdgeToEdge()

        val app = application as VibeSpaceApplication

        setContent {
            val settingsState by app.settingsRepository.observeSettings().collectAsState(
                initial = com.vibespace.feature.settings.repository.UserSettings()
            )

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (settingsState.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }

            VibeSpaceTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VibeNavigation(app = app)
                }
            }
        }
    }
}
