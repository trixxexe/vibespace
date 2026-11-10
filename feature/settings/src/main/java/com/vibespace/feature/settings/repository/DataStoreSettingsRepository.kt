package com.vibespace.feature.settings.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vibespace_settings")

/**
 * Production persistent settings repository backed by AndroidX DataStore Preferences.
 * Ensures user themes, editor preferences, and BYOK AI keys survive app process destruction.
 */
class DataStoreSettingsRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EDITOR_THEME_ID = stringPreferencesKey("editor_theme_id")
        val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val AI_PROVIDER_ID = stringPreferencesKey("ai_provider_id")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_CUSTOM_ENDPOINT = stringPreferencesKey("ai_custom_endpoint")
    }

    override fun observeSettings(): Flow<UserSettings> {
        return context.settingsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
                val themeMode = try {
                    ThemeMode.valueOf(themeModeStr)
                } catch (e: Exception) {
                    ThemeMode.DARK
                }

                UserSettings(
                    themeMode = themeMode,
                    editorThemeId = preferences[PreferencesKeys.EDITOR_THEME_ID] ?: "vibe_dark",
                    editorFontSizeSp = preferences[PreferencesKeys.EDITOR_FONT_SIZE] ?: 13f,
                    tabSizeSpaces = preferences[PreferencesKeys.TAB_SIZE] ?: 4,
                    showLineNumbers = preferences[PreferencesKeys.SHOW_LINE_NUMBERS] ?: true,
                    wordWrap = preferences[PreferencesKeys.WORD_WRAP] ?: false,
                    autoSave = preferences[PreferencesKeys.AUTO_SAVE] ?: true,
                    aiProviderId = preferences[PreferencesKeys.AI_PROVIDER_ID] ?: "gemini",
                    aiApiKey = preferences[PreferencesKeys.AI_API_KEY] ?: "",
                    aiModel = preferences[PreferencesKeys.AI_MODEL] ?: "gemini-2.0-flash",
                    aiCustomEndpoint = preferences[PreferencesKeys.AI_CUSTOM_ENDPOINT] ?: ""
                )
            }
            .flowOn(dispatchers.io)
    }

    override suspend fun updateSettings(settings: UserSettings): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.settingsDataStore.edit { preferences ->
                preferences[PreferencesKeys.THEME_MODE] = settings.themeMode.name
                preferences[PreferencesKeys.EDITOR_THEME_ID] = settings.editorThemeId
                preferences[PreferencesKeys.EDITOR_FONT_SIZE] = settings.editorFontSizeSp
                preferences[PreferencesKeys.TAB_SIZE] = settings.tabSizeSpaces
                preferences[PreferencesKeys.SHOW_LINE_NUMBERS] = settings.showLineNumbers
                preferences[PreferencesKeys.WORD_WRAP] = settings.wordWrap
                preferences[PreferencesKeys.AUTO_SAVE] = settings.autoSave
                preferences[PreferencesKeys.AI_PROVIDER_ID] = settings.aiProviderId
                preferences[PreferencesKeys.AI_API_KEY] = settings.aiApiKey
                preferences[PreferencesKeys.AI_MODEL] = settings.aiModel
                preferences[PreferencesKeys.AI_CUSTOM_ENDPOINT] = settings.aiCustomEndpoint
            }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to update settings: ${e.message}", e)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.settingsDataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to update theme mode: ${e.message}", e)
        }
    }

    override suspend fun setEditorTheme(themeId: String): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.settingsDataStore.edit { it[PreferencesKeys.EDITOR_THEME_ID] = themeId }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to update editor theme: ${e.message}", e)
        }
    }

    override suspend fun setEditorFontSize(fontSizeSp: Float): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.settingsDataStore.edit { it[PreferencesKeys.EDITOR_FONT_SIZE] = fontSizeSp.coerceIn(10f, 24f) }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to update font size: ${e.message}", e)
        }
    }

    override suspend fun setTabSize(spaces: Int): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.settingsDataStore.edit { it[PreferencesKeys.TAB_SIZE] = spaces.coerceIn(2, 8) }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to update tab size: ${e.message}", e)
        }
    }

    override suspend fun setAiConfig(
        providerId: String,
        apiKey: String,
        model: String,
        customEndpoint: String
    ): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            context.settingsDataStore.edit {
                it[PreferencesKeys.AI_PROVIDER_ID] = providerId
                it[PreferencesKeys.AI_API_KEY] = apiKey.trim()
                it[PreferencesKeys.AI_MODEL] = model.trim()
                it[PreferencesKeys.AI_CUSTOM_ENDPOINT] = customEndpoint.trim()
            }
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to update AI configuration: ${e.message}", e)
        }
    }
}
