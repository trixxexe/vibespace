package com.vibespace.core.runner.alpine

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.manager.ExternalRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed interface AlpineInstallationState {
    data object NotInstalled : AlpineInstallationState
    data class Downloading(val progress: Float) : AlpineInstallationState
    data object Extracting : AlpineInstallationState
    data class Installed(val packages: List<String>) : AlpineInstallationState
    data class Error(val message: String) : AlpineInstallationState
}

fun interface AlpineProcessExecutor {
    suspend fun execute(
        command: List<String>,
        workingDir: File,
        environment: Map<String, String>,
        onOutputLine: (line: String, isError: Boolean) -> Unit
    ): Int
}

class DefaultAlpineProcessExecutor(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AlpineProcessExecutor {
    override suspend fun execute(
        command: List<String>,
        workingDir: File,
        environment: Map<String, String>,
        onOutputLine: (line: String, isError: Boolean) -> Unit
    ): Int = withContext(dispatchers.io) {
        val pb = ProcessBuilder(command)
        pb.directory(workingDir)
        pb.environment().putAll(environment)
        val process = pb.start()

        try {
            kotlinx.coroutines.coroutineScope {
                val stdoutJob = launch {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { onOutputLine(it, false) }
                    }
                }
                val stderrJob = launch {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { onOutputLine(it, true) }
                    }
                }
                while (!process.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                stdoutJob.join()
                stderrJob.join()
                process.exitValue()
            }
        } finally {
            process.destroyForcibly()
        }
    }
}

/**
 * In-app Linux container runner using Alpine Linux + PRoot. Execution remains unavailable
 * when the build does not provide a compatible PRoot binary.
 */
class AlpineLinuxRunner(
    private val rootDirectory: File? = null,
    private val downloadTransport: (suspend (url: String, destFile: File, onProgress: (Float) -> Unit) -> Unit)? = null,
    private val prootBinaryPath: String? = null,
    private val processExecutor: AlpineProcessExecutor? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ExternalRunner {

    override val runnerId: String = "alpine"
    override val name: String = "Alpine Linux Subsystem"

    private val _installationState = MutableStateFlow<AlpineInstallationState>(AlpineInstallationState.NotInstalled)
    val installationState: StateFlow<AlpineInstallationState> = _installationState.asStateFlow()

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val activeRuns = mutableMapOf<String, ProcessExecution>()

    private val alpineCdnUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz"

    init {
        val rootfsDir = getRootfsDir()
        _installationState.value = when {
            !hasExecutableProot() -> AlpineInstallationState.Error(prootUnavailableMessage())
            rootfsDir.exists() && File(rootfsDir, "etc/alpine-release").exists() -> AlpineInstallationState.Installed(emptyList())
            else -> AlpineInstallationState.NotInstalled
        }
    }

    private fun getBaseDir(): File {
        return rootDirectory ?: File(System.getProperty("java.io.tmpdir", "."), "vibespace_alpine")
    }

    private fun prootFile(): File = prootBinaryPath?.let(::File) ?: File(getBaseDir(), "bin/proot")

    private fun hasExecutableProot(): Boolean =
        processExecutor != null || (prootFile().exists() && prootFile().canExecute())

    private fun prootUnavailableMessage(): String =
        "PRoot executable is missing from this build. Alpine setup is unavailable; use the Embedded or Termux runner."

    fun getRootfsDir(): File {
        return File(getBaseDir(), "rootfs")
    }

    override fun isAvailable(): Boolean {
        return File(getRootfsDir(), "etc/alpine-release").exists() && hasExecutableProot()
    }

    suspend fun installSubsystem(): VibeResult<Unit> = withContext(dispatchers.io) {
        if (!hasExecutableProot()) {
            val message = prootUnavailableMessage()
            _installationState.value = AlpineInstallationState.Error(message)
            return@withContext VibeResult.Error(message)
        }
        try {
            val baseDir = getBaseDir()
            baseDir.mkdirs()
            val archiveFile = File(baseDir, "alpine-minirootfs.tar.gz")
            val rootfsDir = getRootfsDir()

            _installationState.value = AlpineInstallationState.Downloading(0.05f)

            if (downloadTransport != null) {
                downloadTransport.invoke(alpineCdnUrl, archiveFile) { progress ->
                    _installationState.value = AlpineInstallationState.Downloading(progress)
                }
            } else {
                downloadArchive(alpineCdnUrl, archiveFile) { progress ->
                    _installationState.value = AlpineInstallationState.Downloading(progress)
                }
            }

            _installationState.value = AlpineInstallationState.Extracting
            rootfsDir.mkdirs()
            extractTarGz(archiveFile, rootfsDir)

            _installationState.value = AlpineInstallationState.Installed(emptyList())
            VibeResult.Success(Unit)
        } catch (e: Throwable) {
            val errMsg = e.message ?: "Failed to set up Alpine Linux rootfs"
            _installationState.value = AlpineInstallationState.Error(errMsg)
            VibeResult.Error("Alpine setup failed: $errMsg", e)
        }
    }

    suspend fun uninstallSubsystem(): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            val baseDir = getBaseDir()
            if (baseDir.exists()) {
                baseDir.deleteRecursively()
            }
            _installationState.value = AlpineInstallationState.NotInstalled
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to uninstall Alpine Linux: ${e.message}", e)
        }
    }

    private fun downloadArchive(urlStr: String, destFile: File, onProgress: (Float) -> Unit) {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 25000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VibeSpace-Android/1.0")
        }
        connection.connect()
        val contentLength = connection.contentLengthLong
        destFile.parentFile?.mkdirs()

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val progress = (bytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }
        }
    }

    private fun extractTarGz(tarGzFile: File, destDir: File) {
        destDir.mkdirs()
        val destNorm = destDir.toPath().normalize().toAbsolutePath()
        val maxTotalBytes = 300 * 1024 * 1024L // 300 MB total limit
        val maxEntryBytes = 100 * 1024 * 1024L // 100 MB per file
        val maxEntries = 10000
        var totalBytesRead = 0L
        var entryCount = 0

        GZIPInputStream(FileInputStream(tarGzFile)).use { gzis ->
            val header = ByteArray(512)
            while (true) {
                val bytesRead = readFully(gzis, header)
                if (bytesRead < 512) break
                // Tar EOF marker is two consecutive 512 zero blocks
                if (header.all { it == 0.toByte() }) break

                entryCount++
                if (entryCount > maxEntries) {
                    throw IllegalStateException("Archive exceeds maximum entry limit ($maxEntries entries).")
                }

                val name = String(header, 0, 100, Charsets.US_ASCII).trim('\u0000', ' ')
                if (name.isEmpty()) continue

                val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ')
                val fileSize = if (sizeStr.isNotEmpty()) sizeStr.toLongOrNull(8) ?: 0L else 0L
                val typeFlag = header[156].toInt().toChar()

                if (fileSize > maxEntryBytes) {
                    throw IllegalStateException("Archive entry '$name' exceeds per-entry limit of $maxEntryBytes bytes.")
                }

                val entryFile = File(destDir, name)
                val entryNorm = entryFile.toPath().normalize().toAbsolutePath()
                // Component-based path containment
                if (!entryNorm.startsWith(destNorm)) {
                    val pad = (512 - (fileSize % 512)) % 512
                    val toSkip = fileSize + if (pad == 512L) 0L else pad
                    gzis.skip(toSkip)
                    continue
                }

                if (typeFlag == '5' || name.endsWith("/")) {
                    entryFile.mkdirs()
                } else if (typeFlag == '0' || typeFlag == '\u0000') {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { fos ->
                        var remaining = fileSize
                        val buffer = ByteArray(4096)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val r = gzis.read(buffer, 0, toRead)
                            if (r <= 0) break
                            fos.write(buffer, 0, r)
                            remaining -= r
                            totalBytesRead += r
                            if (totalBytesRead > maxTotalBytes) {
                                throw IllegalStateException("Total uncompressed size exceeds limit of $maxTotalBytes bytes.")
                            }
                        }
                    }
                } else {
                    // Skip unsupported archive entry types (fifos, block/char devices, etc.)
                    val pad = (512 - (fileSize % 512)) % 512
                    val toSkip = fileSize + if (pad == 512L) 0L else pad
                    gzis.skip(toSkip)
                    continue
                }

                // Skip padding to 512-byte boundary
                val pad = (512 - (fileSize % 512)) % 512
                if (pad in 1..511) {
                    var padRemaining = pad
                    while (padRemaining > 0) {
                        val skipped = gzis.skip(padRemaining)
                        if (skipped <= 0) {
                            gzis.read()
                            padRemaining--
                        } else {
                            padRemaining -= skipped
                        }
                    }
                }
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }

    override suspend fun execute(
        command: String,
        workingDirectory: String,
        environmentVariables: Map<String, String>
    ): VibeResult<ProcessExecution> {
        val executionId = IdGenerator.shortId("alpine")
        val flow = MutableSharedFlow<ProcessOutput>(replay = 50)
        outputFlows[executionId] = flow

        val execution = ProcessExecution(
            executionId = executionId,
            command = command,
            workingDirectoryUri = workingDirectory,
            state = ExecutionState.RUNNING,
            startTime = System.currentTimeMillis()
        )
        activeRuns[executionId] = execution

        val outputs = mutableListOf<ProcessOutput>()

        fun emitLine(line: String, isError: Boolean = false) {
            val out = ProcessOutput(line = line, isError = isError)
            outputs.add(out)
            flow.tryEmit(out)
        }

        if (_installationState.value !is AlpineInstallationState.Installed) {
            val unavailable = _installationState.value as? AlpineInstallationState.Error
            emitLine(unavailable?.message ?: "[Alpine Linux Subsystem Not Installed]", true)
            if (unavailable == null) {
                emitLine("Install the Alpine Linux Subsystem after a compatible PRoot runtime is available.", false)
            }
            emitLine("Tip: Switch to 'Instant Embedded' mode to run Python/JS without downloads.", false)

            val failed = execution.copy(
                state = ExecutionState.FAILED,
                exitCode = 127,
                endTime = System.currentTimeMillis(),
                outputs = outputs
            )
            activeRuns[executionId] = failed
            return VibeResult.Success(failed)
        }

        emitLine("[Alpine Linux PRoot Container] $ $command", false)
        val rootfsDir = getRootfsDir()
        val prootFile = prootFile()
        val hostWorkingDirectory = File(workingDirectory)
        val canBindProject = hostWorkingDirectory.isDirectory
        val containerWorkingDirectory = if (canBindProject) "/workspace" else "/root"
        if (canBindProject) File(rootfsDir, "workspace").mkdirs()
        val workspaceBindArgs = if (canBindProject) {
            listOf("-b", "${hostWorkingDirectory.canonicalPath}:/workspace")
        } else {
            emptyList()
        }

        val exitCode: Int
        if (processExecutor != null) {
            val prootCommand = listOf(prootFile.absolutePath, "-0", "-r", rootfsDir.absolutePath) +
                    workspaceBindArgs + listOf("-w", containerWorkingDirectory, "/bin/sh", "-c", command)
            exitCode = processExecutor.execute(
                prootCommand,
                rootfsDir,
                environmentVariables
            ) { line, isError ->
                emitLine(line, isError)
            }
        } else if (prootFile.exists() && prootFile.canExecute()) {
            val prootCommand = listOf(prootFile.absolutePath, "-0", "-r", rootfsDir.absolutePath) +
                    workspaceBindArgs + listOf("-w", containerWorkingDirectory, "/bin/sh", "-c", command)
            val defaultExecutor = DefaultAlpineProcessExecutor(dispatchers)
            exitCode = defaultExecutor.execute(
                prootCommand,
                rootfsDir,
                environmentVariables
            ) { line, isError ->
                emitLine(line, isError)
            }
        } else {
            emitLine("[Alpine Linux Error: PRoot binary not found at ${prootFile.absolutePath}. Subsystem requires PRoot executable for Linux emulation.]", true)
            emitLine("Tip: Place PRoot binary in ${File(getBaseDir(), "bin").absolutePath} or use Termux/Embedded runner.", true)
            val failed = execution.copy(
                state = ExecutionState.FAILED,
                exitCode = 126,
                endTime = System.currentTimeMillis(),
                outputs = outputs
            )
            activeRuns[executionId] = failed
            return VibeResult.Success(failed)
        }

        val completed = execution.copy(
            state = if (exitCode == 0) ExecutionState.SUCCEEDED else ExecutionState.FAILED,
            exitCode = exitCode,
            endTime = System.currentTimeMillis(),
            outputs = outputs
        )
        activeRuns[executionId] = completed
        emitLine("[Process finished with exit code $exitCode]", exitCode != 0)
        return VibeResult.Success(completed)
    }

    override suspend fun stop(executionId: String): VibeResult<Unit> {
        val current = activeRuns[executionId] ?: return VibeResult.Error("Execution not found: $executionId")
        return if (current.state == ExecutionState.RUNNING) {
            VibeResult.Error("The Alpine process executor does not currently support interruption.")
        } else {
            VibeResult.Error("Execution is no longer running.")
        }
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
