package com.vibespace.feature.settings.repository

import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val editorThemeId: String = "vibe_dark",
    val editorFontSizeSp: Float = 13f,
    val tabSizeSpaces: Int = 4,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val autoSave: Boolean = true
)

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateSettings(settings: UserSettings): VibeResult<Unit>
    suspend fun setThemeMode(mode: ThemeMode): VibeResult<Unit>
    suspend fun setEditorTheme(themeId: String): VibeResult<Unit>
    suspend fun setEditorFontSize(fontSizeSp: Float): VibeResult<Unit>
    suspend fun setTabSize(spaces: Int): VibeResult<Unit>
}

class InMemorySettingsRepository(
    initialSettings: UserSettings = UserSettings()
) : SettingsRepository {

    private val _settings = MutableStateFlow(initialSettings)

    override fun observeSettings(): Flow<UserSettings> = _settings.asStateFlow()

    override suspend fun updateSettings(settings: UserSettings): VibeResult<Unit> {
        _settings.value = settings
        return VibeResult.Success(Unit)
    }

    override suspend fun setThemeMode(mode: ThemeMode): VibeResult<Unit> {
        _settings.value = _settings.value.copy(themeMode = mode)
        return VibeResult.Success(Unit)
    }

    override suspend fun setEditorTheme(themeId: String): VibeResult<Unit> {
        _settings.value = _settings.value.copy(editorThemeId = themeId)
        return VibeResult.Success(Unit)
    }

    override suspend fun setEditorFontSize(fontSizeSp: Float): VibeResult<Unit> {
        _settings.value = _settings.value.copy(editorFontSizeSp = fontSizeSp.coerceIn(10f, 24f))
        return VibeResult.Success(Unit)
    }

    override suspend fun setTabSize(spaces: Int): VibeResult<Unit> {
        _settings.value = _settings.value.copy(tabSizeSpaces = spaces.coerceIn(2, 8))
        return VibeResult.Success(Unit)
    }
}
