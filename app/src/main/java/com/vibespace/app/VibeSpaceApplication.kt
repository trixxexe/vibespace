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
import com.vibespace.core.workspace.repository.DataStoreRecentProjectsRepository
import com.vibespace.core.workspace.repository.RecentProjectsRepository
import com.vibespace.feature.settings.repository.DataStoreSettingsRepository
import com.vibespace.feature.settings.repository.SettingsRepository

/**
 * Application class serving as dependency container for VibeSpace.
 * Provides singleton instances for workspace, editor, runner, terminal, and AI systems.
 */
class VibeSpaceApplication : Application() {

    val dispatchers: DispatcherProvider by lazy { DefaultDispatcherProvider() }
    val logger: VibeLogger by lazy { AndroidVibeLogger() }

    val recentProjectsRepository: RecentProjectsRepository by lazy {
        DataStoreRecentProjectsRepository(this, dispatchers)
    }
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(this, dispatchers)
    }

    val workspaceManager: WorkspaceManager by lazy {
        DefaultWorkspaceManager(
            recentProjectsRepository = recentProjectsRepository,
            dispatchers = dispatchers
        )
    }

    val gitManager: com.vibespace.core.workspace.git.GitManager by lazy {
        com.vibespace.core.workspace.git.DefaultGitManager(
            workspaceManager = workspaceManager,
            dispatchers = dispatchers
        )
    }

    val languageDetector: LanguageDetector by lazy { DefaultLanguageDetector() }

    val editorEngine: EditorEngine by lazy {
        DefaultEditorEngine(dispatchers = dispatchers)
    }

    val extensionManager: com.vibespace.core.editor.extension.ExtensionManager by lazy {
        com.vibespace.core.editor.extension.DefaultExtensionManager(dispatchers = dispatchers)
    }

    val runnerManager: RunnerManager by lazy {
        val alpineDir = java.io.File(filesDir, "alpine")
        val alpineRunner = com.vibespace.core.runner.alpine.AlpineLinuxRunner(rootDirectory = alpineDir)
        DefaultRunnerManager(
            workspaceManager = workspaceManager,
            dispatchers = dispatchers,
            alpineRunner = alpineRunner
        )
    }

    val terminalService: TerminalService by lazy {
        DefaultTerminalService(dispatchers = dispatchers)
    }

    val aiService: AIService by lazy {
        AIService(dispatchers = dispatchers)
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.e(TAG, "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "crash_log.txt")
                crashFile.writeText("CRASH: ${throwable::class.java.name}: ${throwable.message}\n${throwable.stackTraceToString()}")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
        logger.i(TAG, "VibeSpace Application Initialized v0.1.0")
    }

    companion object {
        private const val TAG = "VibeSpaceApp"
    }
}
