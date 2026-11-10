package com.vibespace.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.feature.settings.repository.SettingsRepository
import com.vibespace.feature.settings.repository.ThemeMode
import com.vibespace.feature.settings.repository.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val isInitialized: Boolean = false,
    val appVersion: String = "0.1.0-alpha",
    val buildNumber: String = "1",
    val kotlinVersion: String = "2.0.21"
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch(dispatchers.default) {
            repository.observeSettings().collect { userSettings ->
                _uiState.update { it.copy(settings = userSettings, isInitialized = true) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(dispatchers.default) {
            repository.setThemeMode(mode)
        }
    }

    fun setEditorTheme(themeId: String) {
        viewModelScope.launch(dispatchers.default) {
            repository.setEditorTheme(themeId)
        }
    }

    fun setFontSize(fontSizeSp: Float) {
        viewModelScope.launch(dispatchers.default) {
            repository.setEditorFontSize(fontSizeSp)
        }
    }

    fun setTabSize(spaces: Int) {
        viewModelScope.launch(dispatchers.default) {
            repository.setTabSize(spaces)
        }
    }

    fun toggleLineNumbers(enabled: Boolean) {
        viewModelScope.launch(dispatchers.default) {
            repository.updateSettings(_uiState.value.settings.copy(showLineNumbers = enabled))
        }
    }

    fun toggleWordWrap(enabled: Boolean) {
        viewModelScope.launch(dispatchers.default) {
            repository.updateSettings(_uiState.value.settings.copy(wordWrap = enabled))
        }
    }

    fun setAiConfig(providerId: String, apiKey: String, model: String, customEndpoint: String = "") {
        viewModelScope.launch(dispatchers.default) {
            repository.setAiConfig(providerId, apiKey, model, customEndpoint)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch(dispatchers.default) {
            repository.setOnboardingCompleted(completed)
        }
    }

    fun setOnboardingStep(step: Int) {
        viewModelScope.launch(dispatchers.default) {
            repository.setOnboardingStep(step)
        }
    }
}
