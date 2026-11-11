package com.vibespace.core.runner.manager

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.embedded.EmbeddedInstantRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.runner.termux.TermuxBridgeRunner
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DefaultRunnerManager(
    private val workspaceManager: WorkspaceManager? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    alpineRunner: AlpineLinuxRunner? = null,
    termuxRunner: TermuxBridgeRunner? = null,
    private val runnerWorkspaceDirectory: File = File(System.getProperty("java.io.tmpdir", "."), "vibespace-runner-workspaces")
) : RunnerManager {

    constructor(dispatchers: DispatcherProvider) : this(null, dispatchers, null, null)

    private val runners = mutableMapOf<String, ExternalRunner>()
    private val _activeExecutions = MutableStateFlow<List<ProcessExecution>>(emptyList())
    override val activeExecutions: StateFlow<List<ProcessExecution>> = _activeExecutions.asStateFlow()

    private val _activeRunnerMode = MutableStateFlow(RunnerMode.EMBEDDED)
    override val activeRunnerMode: StateFlow<RunnerMode> = _activeRunnerMode.asStateFlow()

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val runnerScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val collectionJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val executionRunners = java.util.concurrent.ConcurrentHashMap<String, ExternalRunner>()

    init {
        // Register runner implementations. Availability is reported by each runner at runtime.
        registerRunner(EmbeddedInstantRunner(workspaceManager = workspaceManager))
        registerRunner(alpineRunner ?: AlpineLinuxRunner())
        registerRunner(termuxRunner ?: TermuxBridgeRunner())
    }

    override fun registerRunner(runner: ExternalRunner) {
        runners[runner.runnerId] = runner
    }

    override fun getAvailableRunners(): List<ExternalRunner> {
        return runners.values.toList()
    }

    override fun setRunnerMode(mode: RunnerMode) {
        _activeRunnerMode.value = mode
    }

    override fun getRunnerForMode(mode: RunnerMode): ExternalRunner? {
        return runners[mode.id]
    }

    override suspend fun runCommand(
        command: String,
        workingDirectory: String,
        preferredRunnerId: String?,
        sessionId: String?
    ): VibeResult<ProcessExecution> = withContext(dispatchers.default) {
        val targetRunnerId = preferredRunnerId ?: _activeRunnerMode.value.id
        val runner = runners[targetRunnerId] ?: runners["embedded"]
            ?: return@withContext VibeResult.Error("No runner available for target: $targetRunnerId")

        val safMirror = if (runner.runnerId == "alpine") {
            when (val prepared = prepareSafWorkspace()) {
                is com.vibespace.core.common.result.VibeResult.Success -> prepared.data
                is com.vibespace.core.common.result.VibeResult.Error -> return@withContext prepared
                else -> null
            }
        } else null
        val runnerWorkingDirectory = if (runner.runnerId == "alpine") {
            safMirror?.first?.absolutePath ?: workspaceManager?.currentProject?.value?.rootPath ?: workingDirectory
        } else workingDirectory
        val environment = sessionId?.let { mapOf("VIBESPACE_TERMINAL_SESSION_ID" to it) }.orEmpty()
        val result = runner.execute(command, runnerWorkingDirectory, environment)
        if (safMirror != null) {
            val syncResult = syncSafWorkspaceBack(safMirror.first, safMirror.second)
            if (syncResult is com.vibespace.core.common.result.VibeResult.Error) return@withContext syncResult
        }

        if (result is VibeResult.Success) {
            val execution = result.data
            _activeExecutions.value = _activeExecutions.value + execution
            executionRunners[execution.executionId] = runner

            if (execution.state != ExecutionState.RUNNING) {
                return@withContext result
            }

            // Pipe output from runner to output flow
            val combinedFlow = outputFlows.getOrPut(execution.executionId) { MutableSharedFlow(replay = 50) }
            val runnerOutputFlow = runner.observeOutput(execution.executionId)

            val job = runnerScope.launch {
                try {
                    runnerOutputFlow.collect { chunk ->
                        combinedFlow.emit(chunk)
                    }
                } finally {
                    collectionJobs.remove(execution.executionId)
                }
            }
            collectionJobs[execution.executionId]?.cancel()
            collectionJobs[execution.executionId] = job

        }

        result
    }

    private suspend fun prepareSafWorkspace(): com.vibespace.core.common.result.VibeResult<Pair<File, Map<String, String>>?> {
        val manager = workspaceManager ?: return com.vibespace.core.common.result.VibeResult.Success(null)
        val project = manager.currentProject.value?.takeIf { it.isStorageAccessFramework }
            ?: return com.vibespace.core.common.result.VibeResult.Success(null)
        val files = when (val result = manager.getAllProjectFiles(project.rootUri)) {
            is com.vibespace.core.common.result.VibeResult.Success -> result.data
            is com.vibespace.core.common.result.VibeResult.Error -> return result
            else -> return com.vibespace.core.common.result.VibeResult.Error("Could not enumerate the SAF workspace for Linux execution.")
        }
        val root = File(runnerWorkspaceDirectory, project.id.toRunnerDirectoryName()).canonicalFile
        root.mkdirs()
        val uriMap = linkedMapOf<String, String>()
        for (file in files.filter { it.isFile }) {
            val relative = relativeWorkspacePath(file, files, project.rootUri)
                ?: return com.vibespace.core.common.result.VibeResult.Error("Could not map '${file.name}' to a workspace-relative path.")
            if (!isSafeRelativePath(relative)) return com.vibespace.core.common.result.VibeResult.Error("Unsafe workspace path rejected: $relative")
            val bytes = when (val read = manager.readBytes(file.uri)) {
                is com.vibespace.core.common.result.VibeResult.Success -> read.data
                is com.vibespace.core.common.result.VibeResult.Error -> return com.vibespace.core.common.result.VibeResult.Error("Could not stage '${file.name}' for Linux: ${read.message}", read.throwable)
                else -> return com.vibespace.core.common.result.VibeResult.Error("Could not stage '${file.name}' for Linux.")
            }
            val destination = safeWorkspaceFile(root, relative)
                ?: return com.vibespace.core.common.result.VibeResult.Error("Workspace path escapes the Linux staging directory: $relative")
            destination.parentFile?.mkdirs()
            if (!destination.exists() || !destination.readBytes().contentEquals(bytes)) destination.writeBytes(bytes)
            uriMap[relative] = file.uri
        }
        root.walkTopDown().filter { it.isFile && it.relativeTo(root).invariantSeparatorsPath !in uriMap }.forEach { it.delete() }
        return com.vibespace.core.common.result.VibeResult.Success(root to uriMap)
    }

    private suspend fun syncSafWorkspaceBack(root: File, existingUris: Map<String, String>): com.vibespace.core.common.result.VibeResult<Unit> {
        val manager = workspaceManager ?: return com.vibespace.core.common.result.VibeResult.Success(Unit)
        val project = manager.currentProject.value ?: return com.vibespace.core.common.result.VibeResult.Error("The workspace was closed while the command was running.")
        val diskFiles = root.walkTopDown().filter { it.isFile }.map { file ->
            file.relativeTo(root).invariantSeparatorsPath to file
        }.toMap()
        val relativeToUri = existingUris.toMutableMap()
        for ((relative, uri) in existingUris) {
            if (relative !in diskFiles) {
                val deleted = manager.delete(uri)
                if (deleted is com.vibespace.core.common.result.VibeResult.Error && !deleted.message.contains("not found", true)) return deleted
            }
        }
        for ((relative, file) in diskFiles) {
            if (!isSafeRelativePath(relative)) continue
            val uri = relativeToUri[relative] ?: resolveWorkspaceUri(project.rootUri, relative, createFile = true) ?:
                return com.vibespace.core.common.result.VibeResult.Error("Could not create workspace file '$relative' from the Linux command.")
            when (val written = manager.writeBytes(uri, file.readBytes())) {
                is com.vibespace.core.common.result.VibeResult.Error -> return com.vibespace.core.common.result.VibeResult.Error("Could not sync '$relative' back to the SAF workspace: ${written.message}", written.throwable)
                else -> relativeToUri[relative] = uri
            }
        }
        return com.vibespace.core.common.result.VibeResult.Success(Unit)
    }

    private suspend fun resolveWorkspaceUri(rootUri: String, relative: String, createFile: Boolean): String? {
        val manager = workspaceManager ?: return null
        val segments = relative.split('/')
        var parent = rootUri
        for (part in segments.dropLast(1)) {
            val listed = manager.listFiles(parent) as? com.vibespace.core.common.result.VibeResult.Success ?: return null
            parent = listed.data.firstOrNull { it.isDirectory && it.name == part }?.uri ?: when (val created = manager.createDirectory(parent, part)) {
                is com.vibespace.core.common.result.VibeResult.Success -> created.data.uri
                else -> return null
            }
        }
        val current = manager.listFiles(parent) as? com.vibespace.core.common.result.VibeResult.Success ?: return null
        current.data.firstOrNull { it.name == segments.last() }?.let { return it.uri }
        if (!createFile) return null
        return (manager.createFile(parent, segments.last()) as? com.vibespace.core.common.result.VibeResult.Success)?.data?.uri
    }

    private fun relativeWorkspacePath(file: VirtualFile, files: List<VirtualFile>, rootUri: String): String? {
        val byUri = files.associateBy { it.uri }
        val parts = mutableListOf(file.name)
        var parent = file.parentUri ?: return null
        val seen = mutableSetOf<String>()
        while (parent != rootUri) {
            if (!seen.add(parent)) return null
            val directory = byUri[parent] ?: return null
            if (directory.nodeType != FileNodeType.DIRECTORY) return null
            parts += directory.name
            parent = directory.parentUri ?: return null
        }
        return parts.asReversed().joinToString("/")
    }

    private fun safeWorkspaceFile(root: File, relative: String): File? {
        val base = root.canonicalFile
        val file = File(base, relative).canonicalFile
        return file.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun isSafeRelativePath(path: String) = path.isNotBlank() && !path.startsWith('/') &&
        path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private fun String.toRunnerDirectoryName(): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    override suspend fun stopExecution(executionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val current = _activeExecutions.value.firstOrNull { it.executionId == executionId }
            ?: return@withContext VibeResult.Error("Execution not found: $executionId")
        if (current.state != ExecutionState.RUNNING) {
            return@withContext VibeResult.Error("Execution '$executionId' is not running.")
        }
        val runner = executionRunners[executionId]
            ?: return@withContext VibeResult.Error("No runner is attached to execution '$executionId'.")

        when (val stopResult = runner.stop(executionId)) {
            is VibeResult.Error -> return@withContext stopResult
            is VibeResult.Success -> Unit
            else -> return@withContext VibeResult.Error("Runner did not confirm that execution stopped.")
        }

        collectionJobs.remove(executionId)?.cancel()
        executionRunners.remove(executionId)

        val updated = current.copy(
            state = ExecutionState.CANCELLED,
            endTime = System.currentTimeMillis()
        )

        _activeExecutions.value = _activeExecutions.value.map {
            if (it.executionId == executionId) updated else it
        }

        outputFlows[executionId]?.emit(ProcessOutput("Process terminated by user.", isError = true))
        VibeResult.Success(Unit)
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        val runner = executionRunners[executionId] ?: runners[_activeRunnerMode.value.id]
        if (runner != null) {
            return runner.observeOutput(executionId)
        }
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
