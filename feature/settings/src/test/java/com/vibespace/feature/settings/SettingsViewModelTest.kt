package com.vibespace.feature.settings

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.feature.settings.repository.InMemorySettingsRepository
import com.vibespace.feature.settings.repository.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    private val repository = InMemorySettingsRepository()

    @Test
    fun setThemeMode_updates_repository_and_ui_state() = runTest {
        val viewModel = SettingsViewModel(
            repository = repository,
            dispatchers = TestDispatcherProvider()
        )

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.settings.themeMode)
    }

    @Test
    fun setFontSize_clamps_and_updates() = runTest {
        val viewModel = SettingsViewModel(
            repository = repository,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.setFontSize(16f)
        assertEquals(16f, viewModel.uiState.value.settings.editorFontSizeSp, 0.01f)
    }

    @Test
    fun setEditorTheme_updates_theme_id() = runTest {
        val viewModel = SettingsViewModel(
            repository = repository,
            dispatchers = TestDispatcherProvider()
        )

        assertEquals("vibe_dark", viewModel.uiState.value.settings.editorThemeId)
        viewModel.setEditorTheme("dracula")
        assertEquals("dracula", viewModel.uiState.value.settings.editorThemeId)
    }

    @Test
    fun setAiConfig_updates_provider_and_api_key() = runTest {
        val viewModel = SettingsViewModel(
            repository = repository,
            dispatchers = TestDispatcherProvider()
        )

        assertEquals("", viewModel.uiState.value.settings.aiApiKey)
        viewModel.setAiConfig("gemini", "AIzaSyTest123", "gemini-2.0-flash")
        assertEquals("gemini", viewModel.uiState.value.settings.aiProviderId)
        assertEquals("AIzaSyTest123", viewModel.uiState.value.settings.aiApiKey)
        assertEquals("gemini-2.0-flash", viewModel.uiState.value.settings.aiModel)
    }
}
