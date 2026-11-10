package com.vibespace.feature.settings.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.extension.ExtensionCategory
import com.vibespace.core.editor.extension.ExtensionManager
import com.vibespace.core.editor.extension.ExtensionMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = extensionManager.getCuratedExtensions()
            if (result is VibeResult.Success) {
                _uiState.update { it.copy(extensions = result.data, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load extensions.") }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true) }
            val result = extensionManager.searchOpenVsx(query)
            if (result is VibeResult.Success) {
                _uiState.update { it.copy(extensions = result.data, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFilter(filter: ExtensionFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun installExtension(extension: ExtensionMetadata) {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update {
                it.copy(downloadingExtensions = it.downloadingExtensions + (extension.id to 0.1f))
            }
            val result = extensionManager.downloadAndInstall(extension) { progress ->
                _uiState.update {
                    it.copy(downloadingExtensions = it.downloadingExtensions + (extension.id to progress))
                }
            }
            _uiState.update {
                it.copy(downloadingExtensions = it.downloadingExtensions - extension.id)
            }
            if (result is VibeResult.Error) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
            loadExtensions()
        }
    }

    fun uninstallExtension(extensionId: String) {
        viewModelScope.launch(dispatchers.io) {
            extensionManager.uninstallExtension(extensionId)
            loadExtensions()
        }
    }

    fun toggleExtension(extensionId: String, enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            extensionManager.toggleExtension(extensionId, enabled)
            loadExtensions()
        }
    }

    fun installFromVsix(inputStream: InputStream) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = extensionManager.installFromVsix(inputStream)) {
                is VibeResult.Success -> {
                    loadExtensions()
                }
                is VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                else -> Unit
            }
        }
    }
}
