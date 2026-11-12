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
import com.vibespace.core.language.lsp.WorkspaceLspUriMapper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files

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
            bundledRuntimeArchiveProvider = { architecture ->
                runCatching { assets.open("runtime/$architecture.tar.gz") }.getOrNull()
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
        DefaultTerminalService(
            dispatchers = dispatchers,
            defaultHomeDirectory = File(filesDir, "workspace").apply { mkdirs() }
        )
    }

    val aiService: AIService by lazy {
        AIService(dispatchers = dispatchers)
    }

    private val lspClients = java.util.concurrent.ConcurrentHashMap<String, com.vibespace.core.language.lsp.LanguageServerClient>()
    private val lspCleanupScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }
    private val lspClientMutex = Mutex()
    private val lspWorkspaceUriMaps = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>()
    private val lspWorkspaceUriReverseMaps = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>()

    fun registerLspClient(languageId: String, client: com.vibespace.core.language.lsp.LanguageServerClient) {
        val normalized = languageId.trim().lowercase()
        lspClients["manual:$normalized"] = client
    }

    suspend fun getLspClient(languageId: String): com.vibespace.core.language.lsp.LanguageServerClient? = lspClientMutex.withLock {
        val normalized = languageId.trim().lowercase()
        if (normalized.isEmpty()) return@withLock null
        lspClients["manual:$normalized"]?.let { return@withLock it }
        if (normalized !in setOf("python", "javascript", "typescript")) return@withLock null
        val project = workspaceManager.currentProject.value ?: return@withLock null
        val clientKey = managedLspKey(project.id, normalized)
        val alpine = runnerManager.getRunnerForMode(com.vibespace.core.runner.model.RunnerMode.ALPINE)
            as? com.vibespace.core.runner.alpine.AlpineLinuxRunner ?: return@withLock null
        val existingClient = lspClients[clientKey]
        if (existingClient == null && !alpine.isLanguageServerInstalled(normalized)) return@withLock null
        val needsStaging = project.rootPath?.let { !File(it).isDirectory } ?: true
        val stagedWorkspace = if (needsStaging) {
            try {
                stageLspWorkspace(project)
            } catch (failure: Exception) {
                logger.w("VibeSpaceApp", "Could not stage the workspace for $normalized: ${failure.message}")
                null
            } ?: return@withLock null
        } else null
        val workspaceRoot = project.rootPath?.let(::File)?.takeIf { it.isDirectory } ?: stagedWorkspace?.first
            ?: return@withLock null
        val uriMap = stagedWorkspace?.second ?: java.util.concurrent.ConcurrentHashMap()
        val reverseUriMap = if (stagedWorkspace != null) {
            lspWorkspaceUriReverseMaps.computeIfAbsent(project.id) { java.util.concurrent.ConcurrentHashMap() }
        } else java.util.concurrent.ConcurrentHashMap()
        existingClient?.let { return@withLock it }
        // A language server is rooted in one workspace. Stop old workspace processes rather than
        // accidentally reusing their index after the active project changes.
        val currentProjectPrefix = "managed:${project.id}:"
        lspClients.entries.toList()
            .filter { it.key.startsWith("managed:") && !it.key.startsWith(currentProjectPrefix) }
            .forEach { entry ->
                if (lspClients.remove(entry.key, entry.value)) {
                    lspCleanupScope.launch { runCatching { entry.value.stop() } }
                }
            }
        try {
            val process = alpine.startLanguageServer(normalized, workspaceRoot)
            val uriMapper = WorkspaceLspUriMapper(
                workspaceRootUri = project.rootUri,
                workspaceDirectory = workspaceRoot,
                documentUriToRelativePath = reverseUriMap::get,
                relativePathToDocumentUri = uriMap::get
            )
            val client = com.vibespace.core.language.lsp.StdioLanguageServerClient(
                languageId = normalized,
                transport = com.vibespace.core.language.lsp.ProcessLspTransport(process, dispatchers),
                dispatchers = dispatchers,
                uriToServer = uriMapper::toServerUri,
                uriToClient = uriMapper::toClientUri
            )
            lspClients[clientKey] = client
            client
        } catch (failure: Exception) {
            logger.w("VibeSpaceApp", "Could not start $normalized language server: ${failure.message}")
            null
        }
    }

    private suspend fun stageLspWorkspace(
        project: com.vibespace.core.workspace.model.Project
    ): Pair<File, java.util.concurrent.ConcurrentHashMap<String, String>>? {
        val files = when (val result = workspaceManager.getAllProjectFiles(project.rootUri)) {
            is com.vibespace.core.common.result.VibeResult.Success -> result.data
            else -> {
                logger.w("VibeSpaceApp", "Could not list workspace files for the language server.")
                return null
            }
        }
        val root = File(filesDir, "runner-workspaces/${project.id.toLspDirectoryName()}").canonicalFile
        root.mkdirs()
        if (root.walkTopDown().any { Files.isSymbolicLink(it.toPath()) }) {
            logger.w("VibeSpaceApp", "Language-server staging workspace contains symbolic links.")
            return null
        }
        val entries = linkedMapOf<String, String>()
        for (file in files.filter { it.isFile }) {
            val relative = workspaceRelativePath(file, files, project.rootUri) ?: return null
            if (!isSafeLspPath(relative)) return null
            val bytes = when (val result = workspaceManager.readBytes(file.uri)) {
                is com.vibespace.core.common.result.VibeResult.Success -> result.data
                else -> return null
            }
            val destination = safeLspFile(root, relative) ?: return null
            destination.parentFile?.mkdirs()
            if (!destination.exists() || !destination.readBytes().contentEquals(bytes)) destination.writeBytes(bytes)
            entries[relative] = file.uri
        }
        root.walkTopDown().filter { it.isFile && it.relativeTo(root).invariantSeparatorsPath !in entries }.forEach { it.delete() }
        val sharedMap = lspWorkspaceUriMaps.computeIfAbsent(project.id) { java.util.concurrent.ConcurrentHashMap() }
        val sharedReverseMap = lspWorkspaceUriReverseMaps.computeIfAbsent(project.id) { java.util.concurrent.ConcurrentHashMap() }
        sharedMap.clear()
        sharedMap.putAll(entries)
        sharedReverseMap.clear()
        entries.forEach { (relative, uri) -> sharedReverseMap[uri] = relative }
        return root to sharedMap
    }

    private fun workspaceRelativePath(
        file: com.vibespace.core.workspace.model.VirtualFile,
        files: List<com.vibespace.core.workspace.model.VirtualFile>,
        rootUri: String
    ): String? {
        val byUri = files.associateBy { it.uri }
        val parts = mutableListOf(file.name)
        var parent = file.parentUri ?: return null
        val seen = mutableSetOf<String>()
        while (parent != rootUri) {
            if (!seen.add(parent)) return null
            val directory = byUri[parent]?.takeIf { it.isDirectory } ?: return null
            parts += directory.name
            parent = directory.parentUri ?: return null
        }
        return parts.asReversed().joinToString("/")
    }

    private fun safeLspFile(root: File, relativePath: String): File? {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        return candidate.takeIf { it != canonicalRoot && it.toPath().startsWith(canonicalRoot.toPath()) }
    }

    private fun isSafeLspPath(path: String) = path.isNotBlank() && !path.startsWith('/') &&
        path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private fun String.toLspDirectoryName() = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private fun managedLspKey(projectId: String, languageId: String) = "managed:$projectId:$languageId"

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

        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            com.vibespace.core.editor.sora.TextMateLanguageManager.initialize(this@VibeSpaceApplication)
        }

        val alpine = runnerManager.getRunnerForMode(com.vibespace.core.runner.model.RunnerMode.ALPINE)
            as? com.vibespace.core.runner.alpine.AlpineLinuxRunner
        if (alpine?.hasBundledRuntime() == true) {
            runnerManager.setRunnerMode(com.vibespace.core.runner.model.RunnerMode.ALPINE)
            CoroutineScope(SupervisorJob() + dispatchers.io).launch {
                val result = alpine.installSubsystem()
                if (result is com.vibespace.core.common.result.VibeResult.Error) {
                    logger.e("VibeSpaceApp", "Could not prepare bundled Python and Node runtime: ${result.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "VibeSpaceApp"
    }
}
