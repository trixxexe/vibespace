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
            context = this,
            recentProjectsRepository = recentProjectsRepository,
            dispatchers = dispatchers
        )
    }

    val gitManager: com.vibespace.core.workspace.git.GitManager by lazy {
        com.vibespace.core.workspace.git.JGitWorkspaceManager(
            workspaceManager = workspaceManager,
            storageDirectory = java.io.File(filesDir, "git-repositories"),
            dispatchers = dispatchers
        )
    }

    val languageDetector: LanguageDetector by lazy { DefaultLanguageDetector() }

    val editorEngine: EditorEngine by lazy {
        DefaultEditorEngine(dispatchers = dispatchers)
    }

    val extensionManager: com.vibespace.core.editor.extension.ExtensionManager by lazy {
        val stateFile = java.io.File(filesDir, "installed_extensions.json")
        com.vibespace.core.editor.extension.DefaultExtensionManager(
            stateFile = stateFile,
            dispatchers = dispatchers
        )
    }

    val runnerManager: RunnerManager by lazy {
        val alpineDir = java.io.File(filesDir, "alpine")
        val alpineRunner = com.vibespace.core.runner.alpine.AlpineLinuxRunner(
            rootDirectory = alpineDir,
            prootBinaryPath = java.io.File(alpineDir, "bin/proot").absolutePath,
            prootArchitecture = when (android.os.Build.SUPPORTED_ABIS.firstOrNull()) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a", "armeabi" -> "arm"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> null
            },
            dnsServersProvider = {
                val connectivity = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val network = connectivity?.activeNetwork
                connectivity?.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
            },
            dispatchers = dispatchers
        )
        DefaultRunnerManager(
            workspaceManager = workspaceManager,
            dispatchers = dispatchers,
            alpineRunner = alpineRunner,
            termuxRunner = com.vibespace.core.runner.termux.TermuxBridgeRunner(context = this),
            runnerWorkspaceDirectory = java.io.File(filesDir, "runner-workspaces")
        )
    }

    val terminalService: TerminalService by lazy {
        DefaultTerminalService(dispatchers = dispatchers)
    }

    val aiService: AIService by lazy {
        AIService(dispatchers = dispatchers)
    }

    private val lspClients = java.util.concurrent.ConcurrentHashMap<String, com.vibespace.core.language.lsp.LanguageServerClient>()

    fun registerLspClient(languageId: String, client: com.vibespace.core.language.lsp.LanguageServerClient) {
        val normalized = languageId.trim().lowercase()
        lspClients[normalized] = client
    }

    @Synchronized
    fun getLspClient(languageId: String): com.vibespace.core.language.lsp.LanguageServerClient? {
        val normalized = languageId.trim().lowercase()
        if (normalized.isEmpty()) return null
        lspClients[normalized]?.let { return it }
        if (normalized !in setOf("python", "javascript", "typescript")) return null
        val project = workspaceManager.currentProject.value ?: return null
        // SAF URIs are not filesystem paths; do not hand them to a process-based language server.
        val workspaceRoot = project.rootPath?.let { java.io.File(it) }?.takeIf { it.isDirectory } ?: return null
        val alpine = runnerManager.getRunnerForMode(com.vibespace.core.runner.model.RunnerMode.ALPINE)
            as? com.vibespace.core.runner.alpine.AlpineLinuxRunner ?: return null
        if (!alpine.isLanguageServerInstalled(normalized)) return null
        return try {
            val process = alpine.startLanguageServer(normalized, workspaceRoot)
            val client = com.vibespace.core.language.lsp.StdioLanguageServerClient(
                languageId = normalized,
                transport = com.vibespace.core.language.lsp.ProcessLspTransport(process, dispatchers),
                dispatchers = dispatchers
            )
            lspClients[normalized] = client
            client
        } catch (failure: Exception) {
            logger.w("VibeSpaceApp", "Could not start $normalized language server: ${failure.message}")
            null
        }
    }

    override fun onCreate() {
        super.onCreate()

        val processName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            packageName
        }
        if (processName.endsWith(":crash")) {
            return
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.e(TAG, "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "crash_log.txt")
                crashFile.writeText("CRASH: ${throwable::class.java.name}: ${throwable.message}\n${throwable.stackTraceToString()}")

                val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("error_type", throwable::class.java.simpleName)
                    putExtra("error_message", throwable.message ?: "Unknown error")
                    putExtra("stack_trace", throwable.stackTraceToString())
                }
                startActivity(intent)
                try {
                    Thread.sleep(300)
                } catch (_: InterruptedException) {}
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (_: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        logger.i(TAG, "VibeSpace Application Initialized v0.1.0")
    }

    companion object {
        private const val TAG = "VibeSpaceApp"
    }
}
