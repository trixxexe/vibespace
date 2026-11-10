package com.vibespace.core.language.lsp

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind
import com.vibespace.core.language.model.DefinitionLocation
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.language.model.DiagnosticPosition
import com.vibespace.core.language.model.DiagnosticRange
import com.vibespace.core.language.model.DiagnosticSeverity
import com.vibespace.core.language.model.HoverInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Standard Language Server Protocol (LSP) 3.17 stdio client.
 * Connects to background language servers (pyright, typescript-language-server, rust-analyzer, etc.)
 * via JSON-RPC 2.0 messages framed over stdin/stdout.
 */
class StdioLanguageServerClient(
    override val languageId: String,
    private val transport: LspTransport,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : LanguageServerClient {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val diagnosticsFlows = ConcurrentHashMap<String, MutableStateFlow<List<Diagnostic>>>()

    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning

    private var listenJob: Job? = null

    init {
        startListening()
    }

    private fun startListening() {
        listenJob = scope.launch {
            transport.incoming().collect { rawJson ->
                handleIncomingMessage(rawJson)
            }
        }
    }

    override suspend fun start(workspaceRootUri: String): VibeResult<Unit> = withContext(dispatchers.default) {
        if (_isRunning) return@withContext VibeResult.Success(Unit)

        val initParams = """
            {
                "processId": null,
                "rootUri": "$workspaceRootUri",
                "capabilities": {
                    "textDocument": {
                        "hover": {"contentFormat": ["markdown", "plaintext"]},
                        "completion": {"completionItem": {"snippetSupport": true}},
                        "definition": {"linkSupport": true},
                        "publishDiagnostics": {"relatedInformation": true}
                    }
                }
            }
        """.trimIndent()

        when (val initResponse = sendRequest("initialize", initParams)) {
            is VibeResult.Success -> {
                sendNotification("initialized", "{}")
                _isRunning = true
                VibeResult.Success(Unit)
            }
            is VibeResult.Error -> VibeResult.Error("Failed to initialize language server: ${initResponse.message}")
            else -> VibeResult.Error("Unexpected response during LSP initialization")
        }
    }

    override suspend fun stop(): VibeResult<Unit> = withContext(dispatchers.default) {
        try {
            if (_isRunning) {
                sendRequest("shutdown", "{}")
                sendNotification("exit", "{}")
            }
        } catch (_: Exception) {}
        _isRunning = false
        listenJob?.cancel()
        transport.close()
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        VibeResult.Success(Unit)
    }

    override suspend fun didOpen(documentUri: String, text: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val escaped = escapeJson(text)
        val params = """
            {
                "textDocument": {
                    "uri": "$documentUri",
                    "languageId": "$languageId",
                    "version": 1,
                    "text": "$escaped"
                }
            }
        """.trimIndent()
        sendNotification("textDocument/didOpen", params)
        VibeResult.Success(Unit)
    }

    override suspend fun didChange(documentUri: String, newText: String, version: Int): VibeResult<Unit> = withContext(dispatchers.default) {
        val escaped = escapeJson(newText)
        val params = """
            {
                "textDocument": {
                    "uri": "$documentUri",
                    "version": $version
                },
                "contentChanges": [
                    {"text": "$escaped"}
                ]
            }
        """.trimIndent()
        sendNotification("textDocument/didChange", params)
        VibeResult.Success(Unit)
    }

    override suspend fun didClose(documentUri: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val params = """{"textDocument": {"uri": "$documentUri"}}"""
        sendNotification("textDocument/didClose", params)
        VibeResult.Success(Unit)
    }

    override fun observeDiagnostics(documentUri: String): Flow<List<Diagnostic>> {
        return diagnosticsFlows.getOrPut(documentUri) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }

    override suspend fun requestHover(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<HoverInfo?> = withContext(dispatchers.default) {
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "position": {"line": $line, "character": $column}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/hover", params)) {
            is VibeResult.Success -> {
                val json = result.data
                val hoverInfo = parseHoverResponse(json)
                VibeResult.Success(hoverInfo)
            }
            is VibeResult.Error -> VibeResult.Error(result.message)
            else -> VibeResult.Success(null)
        }
    }

    override suspend fun requestDefinition(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<List<DefinitionLocation>> = withContext(dispatchers.default) {
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "position": {"line": $line, "character": $column}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/definition", params)) {
            is VibeResult.Success -> {
                val locations = parseDefinitionResponse(result.data)
                VibeResult.Success(locations)
            }
            is VibeResult.Error -> VibeResult.Error(result.message)
            else -> VibeResult.Success(emptyList())
        }
    }

    override suspend fun requestCompletion(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<List<CompletionItem>> = withContext(dispatchers.default) {
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "position": {"line": $line, "character": $column}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/completion", params)) {
            is VibeResult.Success -> {
                val items = parseCompletionResponse(result.data)
                VibeResult.Success(items)
            }
            is VibeResult.Error -> VibeResult.Error(result.message)
            else -> VibeResult.Success(emptyList())
        }
    }

    override suspend fun formatDocument(documentUri: String): VibeResult<String> = withContext(dispatchers.default) {
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "options": {"tabSize": 4, "insertSpaces": true}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/formatting", params)) {
            is VibeResult.Success -> {
                // If formatting text edits returned, extract newText
                val formattedText = extractFormattedText(result.data)
                VibeResult.Success(formattedText)
            }
            is VibeResult.Error -> VibeResult.Error(result.message)
            else -> VibeResult.Error("Document formatting failed")
        }
    }

    private suspend fun sendRequest(method: String, paramsJson: String): VibeResult<String> {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<String>()
        pendingRequests[id] = deferred

        val json = """{"jsonrpc":"2.0","id":$id,"method":"$method","params":$paramsJson}"""
        transport.send(json)

        val response = withTimeoutOrNull(8000L) {
            deferred.await()
        } ?: run {
            pendingRequests.remove(id)
            return VibeResult.Error("LSP request '$method' (id: $id) timed out.")
        }

        return VibeResult.Success(response)
    }

    private suspend fun sendNotification(method: String, paramsJson: String) {
        val json = """{"jsonrpc":"2.0","method":"$method","params":$paramsJson}"""
        transport.send(json)
    }

    private fun handleIncomingMessage(rawJson: String) {
        // Check if message is a response (has "id")
        val idMatch = Regex(""""id"\s*:\s*(\d+)""").find(rawJson)
        if (idMatch != null) {
            val id = idMatch.groupValues[1].toLongOrNull()
            if (id != null) {
                val deferred = pendingRequests.remove(id)
                if (deferred != null) {
                    val errorMatch = Regex(""""error"\s*:\s*\{([^}]+)\}""").find(rawJson)
                    if (errorMatch != null) {
                        deferred.completeExceptionally(RuntimeException("LSP Error: ${errorMatch.groupValues[1]}"))
                    } else {
                        deferred.complete(rawJson)
                    }
                    return
                }
            }
        }

        // Check if message is a notification
        val methodMatch = Regex(""""method"\s*:\s*"([^"]+)"""").find(rawJson)
        if (methodMatch != null) {
            val method = methodMatch.groupValues[1]
            if (method == "textDocument/publishDiagnostics") {
                parseAndPublishDiagnostics(rawJson)
            }
        }
    }

    private fun parseAndPublishDiagnostics(rawJson: String) {
        val uriMatch = Regex(""""uri"\s*:\s*"([^"]+)"""").find(rawJson) ?: return
        val uri = uriMatch.groupValues[1]

        val diagnosticsList = mutableListOf<Diagnostic>()

        // Find each diagnostic block
        val diagnosticRegex = Regex("""\{[^{}]*"message"\s*:\s*"([^"]+)"[^{}]*\}""")
        for (diagMatch in diagnosticRegex.findAll(rawJson)) {
            val block = diagMatch.value
            val message = Regex(""""message"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "Diagnostic"
            val severityInt = Regex(""""severity"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val severity = when (severityInt) {
                1 -> DiagnosticSeverity.ERROR
                2 -> DiagnosticSeverity.WARNING
                3 -> DiagnosticSeverity.INFORMATION
                else -> DiagnosticSeverity.HINT
            }

            val line = Regex(""""line"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val col = Regex(""""character"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            diagnosticsList.add(
                Diagnostic(
                    id = IdGenerator.shortId("diag"),
                    message = message,
                    severity = severity,
                    range = DiagnosticRange(
                        start = DiagnosticPosition(line, col),
                        end = DiagnosticPosition(line, col + 1)
                    ),
                    source = languageId
                )
            )
        }

        diagnosticsFlows.getOrPut(uri) { MutableStateFlow(emptyList()) }.value = diagnosticsList
    }

    private fun parseHoverResponse(json: String): HoverInfo? {
        val valueMatch = Regex(""""value"\s*:\s*"([^"]+)"""").find(json)
        if (valueMatch != null) {
            return HoverInfo(contents = unescapeJson(valueMatch.groupValues[1]))
        }
        val contentsMatch = Regex(""""contents"\s*:\s*"([^"]+)"""").find(json)
        if (contentsMatch != null) {
            return HoverInfo(contents = unescapeJson(contentsMatch.groupValues[1]))
        }
        return null
    }

    private fun parseDefinitionResponse(json: String): List<DefinitionLocation> {
        val list = mutableListOf<DefinitionLocation>()
        val uriMatches = Regex(""""(?:targetUri|uri)"\s*:\s*"([^"]+)"""").findAll(json).toList()
        for (uriMatch in uriMatches) {
            val uri = uriMatch.groupValues[1]
            list.add(
                DefinitionLocation(
                    uri = uri,
                    line = 1,
                    column = 1
                )
            )
        }
        return list
    }

    private fun parseCompletionResponse(json: String): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()
        val labelRegex = Regex(""""label"\s*:\s*"([^"]+)"""")
        for (match in labelRegex.findAll(json)) {
            val label = match.groupValues[1]
            items.add(
                CompletionItem(
                    label = label,
                    kind = CompletionItemKind.FUNCTION,
                    insertText = label
                )
            )
        }
        return items
    }

    private fun extractFormattedText(json: String): String {
        val textMatch = Regex(""""newText"\s*:\s*"([^"]+)"""").find(json)
        return textMatch?.groupValues?.get(1) ?: ""
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJson(input: String): String {
        return input.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
