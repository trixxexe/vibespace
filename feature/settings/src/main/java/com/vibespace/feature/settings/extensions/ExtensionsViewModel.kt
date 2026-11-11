package com.vibespace.feature.settings.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.extension.ExtensionCategory
import com.vibespace.core.editor.extension.ExtensionManager
import com.vibespace.core.editor.extension.ExtensionMetadata
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.feature.settings.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream

enum class ExtensionFilter {
    ALL,
    INSTALLED,
    THEMES,
    LANGUAGES,
    ICONS,
    FORMATTERS
}

data class ExtensionsUiState(
    val searchQuery: String = "",
    val activeFilter: ExtensionFilter = ExtensionFilter.ALL,
    val extensions: List<ExtensionMetadata> = emptyList(),
    val installedExtensions: List<ExtensionMetadata> = emptyList(),
    val downloadingExtensions: Map<String, Float> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ExtensionsViewModel(
    private val extensionManager: ExtensionManager,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        observeInstalled()
        loadExtensions()
    }

    private fun observeInstalled() {
        viewModelScope.launch(dispatchers.default) {
            extensionManager.installedExtensions.collect { installed ->
                _uiState.update { it.copy(installedExtensions = installed) }
            }
        }
    }

    fun loadExtensions() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(dispatchers.io) {
            refreshExtensions(_uiState.value.searchQuery)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(dispatchers.io) {
            delay(300)
            if (_uiState.value.searchQuery != query) return@launch
            refreshExtensions(query)
        }
    }

    private suspend fun refreshExtensions(query: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = extensionManager.searchOpenVsx(query)) {
            is VibeResult.Success -> {
                if (_uiState.value.searchQuery == query) {
                    _uiState.update { it.copy(extensions = result.data, isLoading = false) }
                }
            }
            is VibeResult.Error -> {
                if (_uiState.value.searchQuery == query) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
            else -> _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setFilter(filter: ExtensionFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun installExtension(extension: ExtensionMetadata) {
        viewModelScope.launch(dispatchers.io) {
            val installedBefore = extensionManager.installedExtensions.value.mapTo(mutableSetOf()) { it.id }
            _uiState.update {
                it.copy(errorMessage = null, downloadingExtensions = it.downloadingExtensions + (extension.id to 0f))
            }
            val result = extensionManager.downloadAndInstall(extension) { progress ->
                _uiState.update {
                    it.copy(downloadingExtensions = it.downloadingExtensions + (extension.id to progress))
                }
            }
            _uiState.update {
                it.copy(downloadingExtensions = it.downloadingExtensions - extension.id)
            }
            when (result) {
                is VibeResult.Success -> {
                    val installedTheme = extensionManager.installedExtensions.value
                        .firstOrNull { it.id !in installedBefore && it.category == ExtensionCategory.THEME }
                    val themeId = installedTheme?.id ?: extension.id
                    if (isResolvableTheme(themeId)) {
                        applyTheme(themeId)
                    }
                    refreshExtensions(_uiState.value.searchQuery)
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> _uiState.update { it.copy(errorMessage = "Extension installation did not complete.") }
            }
        }
    }

    fun uninstallExtension(extensionId: String) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = extensionManager.uninstallExtension(extensionId)) {
                is VibeResult.Success -> {
                    if (currentThemeId() == extensionId) applyTheme("vibe_dark")
                    refreshExtensions(_uiState.value.searchQuery)
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun toggleExtension(extensionId: String, enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = extensionManager.toggleExtension(extensionId, enabled)) {
                is VibeResult.Success -> {
                    if (!enabled && currentThemeId() == extensionId) {
                        applyTheme("vibe_dark")
                    } else if (enabled && isResolvableTheme(extensionId)) {
                        applyTheme(extensionId)
                    }
                    refreshExtensions(_uiState.value.searchQuery)
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun installFromVsix(inputStream: InputStream) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = extensionManager.installFromVsix(inputStream)) {
                is VibeResult.Success -> {
                    applyTheme(result.data.id)
                    refreshExtensions(_uiState.value.searchQuery)
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun isResolvableTheme(themeId: String): Boolean {
        val resolved = EditorThemeDefinition.fromId(themeId)
        return themeId == "vibe_dark" || resolved != EditorThemeDefinition.VIBE_DARK
    }

    private suspend fun currentThemeId(): String? =
        settingsRepository?.observeSettings()?.first()?.editorThemeId

    private suspend fun applyTheme(themeId: String) {
        when (val result = settingsRepository?.setEditorTheme(themeId)) {
            is VibeResult.Error -> _uiState.update { it.copy(errorMessage = "Theme installed, but could not be applied: ${result.message}") }
            else -> Unit
        }
    }
}
