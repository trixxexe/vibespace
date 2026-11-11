package com.vibespace.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vibespace.app.VibeSpaceApplication
import com.vibespace.feature.aichat.AIChatViewModel
import com.vibespace.feature.editor.EditorViewModel
import com.vibespace.feature.explorer.ExplorerViewModel
import com.vibespace.feature.explorer.git.GitViewModel
import com.vibespace.feature.home.HomeViewModel
import com.vibespace.feature.search.SearchViewModel
import com.vibespace.feature.settings.SettingsViewModel
import com.vibespace.feature.settings.extensions.ExtensionsViewModel
import com.vibespace.feature.terminal.TerminalViewModel

/**
 * ViewModelProvider.Factory ensuring all feature ViewModels are lifecycle-owned,
 * retain their state across configuration and orientation changes (e.g. Samsung DeX mode switch),
 * and clean up their coroutine scopes and observers upon destruction.
 */
class VibeViewModelFactory(
    private val app: VibeSpaceApplication
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    workspaceManager = app.workspaceManager,
                    recentProjectsRepository = app.recentProjectsRepository,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(EditorViewModel::class.java) -> {
                EditorViewModel(
                    editorEngine = app.editorEngine,
                    workspaceManager = app.workspaceManager,
                    languageDetector = app.languageDetector,
                    lspClientProvider = { langId -> app.getLspClient(langId) },
                    gitManager = app.gitManager,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(ExplorerViewModel::class.java) -> {
                ExplorerViewModel(
                    workspaceManager = app.workspaceManager,
                    gitManager = app.gitManager,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(TerminalViewModel::class.java) -> {
                TerminalViewModel(
                    terminalService = app.terminalService,
                    runnerManager = app.runnerManager,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(AIChatViewModel::class.java) -> {
                AIChatViewModel(
                    aiService = app.aiService,
                    editorEngine = app.editorEngine,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(
                    editorEngine = app.editorEngine,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    repository = app.settingsRepository,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(GitViewModel::class.java) -> {
                GitViewModel(
                    gitManager = app.gitManager,
                    dispatchers = app.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(ExtensionsViewModel::class.java) -> {
                ExtensionsViewModel(
                    extensionManager = app.extensionManager,
                    dispatchers = app.dispatchers,
                    settingsRepository = app.settingsRepository
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
