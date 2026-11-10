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
}
