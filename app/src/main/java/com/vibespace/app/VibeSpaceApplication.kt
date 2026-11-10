package com.vibespace.app

import android.app.Application
import com.vibespace.core.ai.service.AIService
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.logging.AndroidVibeLogger
import com.vibespace.core.common.logging.VibeLogger
import com.vibespace.core.editor.engine.DefaultEditorEngine
import com.vibespace.core.editor.engine.EditorEngine
import com.vibespace.core.language.detector.DefaultLanguageDetector
import com.vibespace.core.language.detector.LanguageDetector
import com.vibespace.core.runner.manager.DefaultRunnerManager
import com.vibespace.core.runner.manager.RunnerManager
import com.vibespace.core.terminal.service.DefaultTerminalService
import com.vibespace.core.terminal.service.TerminalService
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.repository.InMemoryRecentProjectsRepository
import com.vibespace.core.workspace.repository.RecentProjectsRepository
import com.vibespace.feature.settings.repository.InMemorySettingsRepository
import com.vibespace.feature.settings.repository.SettingsRepository

/**
 * Application class serving as dependency container for VibeSpace.
 * Provides singleton instances for workspace, editor, runner, terminal, and AI systems.
 */
class VibeSpaceApplication : Application() {

    val dispatchers: DispatcherProvider by lazy { DefaultDispatcherProvider() }
    val logger: VibeLogger by lazy { AndroidVibeLogger() }

    val recentProjectsRepository: RecentProjectsRepository by lazy { InMemoryRecentProjectsRepository() }
    val settingsRepository: SettingsRepository by lazy { InMemorySettingsRepository() }

    val workspaceManager: WorkspaceManager by lazy {
        DefaultWorkspaceManager(
            recentProjectsRepository = recentProjectsRepository,
            dispatchers = dispatchers
        )
    }

    val languageDetector: LanguageDetector by lazy { DefaultLanguageDetector() }

    val editorEngine: EditorEngine by lazy {
        DefaultEditorEngine(dispatchers = dispatchers)
    }

    val runnerManager: RunnerManager by lazy {
        DefaultRunnerManager(dispatchers = dispatchers)
    }

    val terminalService: TerminalService by lazy {
        DefaultTerminalService(dispatchers = dispatchers)
    }

    val aiService: AIService by lazy {
        AIService(dispatchers = dispatchers)
    }

    override fun onCreate() {
        super.onCreate()
        logger.i(TAG, "VibeSpace Application Initialized v0.1.0")
    }

    companion object {
        private const val TAG = "VibeSpaceApp"
    }
}
