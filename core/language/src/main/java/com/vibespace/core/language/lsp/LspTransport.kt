package com.vibespace.core.language.lsp

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Transport contract for Language Server Protocol (LSP) JSON-RPC messaging.
 */
interface LspTransport {
    suspend fun send(jsonPayload: String)
    fun incoming(): Flow<String>
    fun close()
}

/**
 * Deterministic in-memory LSP transport for unit testing and mock language servers.
 */
class InMemoryLspTransport : LspTransport {

    val clientToServer = Channel<String>(Channel.UNLIMITED)
    val serverToClient = Channel<String>(Channel.UNLIMITED)

    override suspend fun send(jsonPayload: String) {
        clientToServer.send(jsonPayload)
    }

    override fun incoming(): Flow<String> {
        return serverToClient.receiveAsFlow()
    }

    suspend fun emitFromServer(jsonPayload: String) {
        serverToClient.send(jsonPayload)
    }

    fun tryEmitFromServer(jsonPayload: String): Boolean {
        return serverToClient.trySend(jsonPayload).isSuccess
    }

    override fun close() {
        clientToServer.close()
        serverToClient.close()
    }
}

/**
 * Real stdio stream LSP transport connecting to a language server process.
 */
class StreamLspTransport(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : LspTransport {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val incomingChannel = Channel<String>(Channel.BUFFERED)
    private val decoder = LspFramer.StreamDecoder()
    private var readerJob: Job? = null

    init {
        startReading()
    }

    private fun startReading() {
        readerJob = scope.launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val read = withContext(dispatchers.io) {
                        inputStream.read(buffer)
                    }
                    if (read == -1) break
                    val messages = decoder.feed(buffer, 0, read)
                    for (msg in messages) {
                        incomingChannel.send(msg)
                    }
                }
            } catch (_: Exception) {
                // Stream closed or canceled
            } finally {
                incomingChannel.close()
            }
        }
    }

    override suspend fun send(jsonPayload: String) = withContext(dispatchers.io) {
        val framedBytes = LspFramer.frame(jsonPayload)
        outputStream.write(framedBytes)
        outputStream.flush()
    }

    override fun incoming(): Flow<String> {
        return incomingChannel.receiveAsFlow()
    }

    override fun close() {
        readerJob?.cancel()
        incomingChannel.close()
        try {
            outputStream.close()
        } catch (_: Exception) {}
        try {
            inputStream.close()
        } catch (_: Exception) {}
    }
}

/** Stdio transport that also owns and terminates the server process. */
class ProcessLspTransport(
    private val process: Process,
    dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : LspTransport {
    private val delegate = StreamLspTransport(process.inputStream, process.outputStream, dispatchers)
    private val stderrScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val stderrJob = stderrScope.launch {
        runCatching { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { /* Drain stderr to prevent the child from blocking. */ } } }
    }

    override suspend fun send(jsonPayload: String) = delegate.send(jsonPayload)
    override fun incoming(): Flow<String> = delegate.incoming()

    override fun close() {
        delegate.close()
        stderrJob.cancel()
        stderrScope.cancel()
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
    }
}
