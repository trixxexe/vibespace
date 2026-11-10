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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Standard Language Server Protocol (LSP) 3.17 stdio client.
 * Connects to background language servers (pyright, typescript-language-server, rust-analyzer, etc.)
 * via JSON-RPC 2.0 messages parsed with standard org.json.JSONObject.
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
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        transport.close()
        VibeResult.Success(Unit)
    }

    override suspend fun didOpen(documentUri: String, text: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val escapedText = JSONObject.quote(text)
        val params = """
            {
                "textDocument": {
                    "uri": "$documentUri",
                    "languageId": "$languageId",
                    "version": 1,
                    "text": $escapedText
                }
            }
        """.trimIndent()
        sendNotification("textDocument/didOpen", params)
        VibeResult.Success(Unit)
    }

    override suspend fun didChange(documentUri: String, newText: String, version: Int): VibeResult<Unit> = withContext(dispatchers.default) {
        val escapedText = JSONObject.quote(newText)
        val params = """
            {
                "textDocument": {
                    "uri": "$documentUri",
                    "version": $version
                },
                "contentChanges": [
                    {
                        "text": $escapedText
                    }
                ]
            }
        """.trimIndent()
        sendNotification("textDocument/didChange", params)
        VibeResult.Success(Unit)
    }

    override suspend fun didClose(documentUri: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val params = """
            {
                "textDocument": {
                    "uri": "$documentUri"
                }
            }
        """.trimIndent()
        sendNotification("textDocument/didClose", params)
        diagnosticsFlows.remove(documentUri)
        VibeResult.Success(Unit)
    }

    override fun observeDiagnostics(documentUri: String): Flow<List<Diagnostic>> {
        return diagnosticsFlows.getOrPut(documentUri) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    override suspend fun requestHover(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<HoverInfo?> = withContext(dispatchers.default) {
        if (!_isRunning) {
            return@withContext VibeResult.Error("Language server for '$languageId' is not running")
        }
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "position": {"line": ${line - 1}, "character": $column}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/hover", params)) {
            is VibeResult.Success -> {
                val hoverInfo = parseHoverResponse(result.data)
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
        if (!_isRunning) {
            return@withContext VibeResult.Error("Language server for '$languageId' is not running")
        }
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "position": {"line": ${line - 1}, "character": $column}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/definition", params)) {
            is VibeResult.Success -> {
                val locs = parseDefinitionResponse(result.data)
                VibeResult.Success(locs)
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
        if (!_isRunning) {
            return@withContext VibeResult.Error("Language server for '$languageId' is not running")
        }
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "position": {"line": ${line - 1}, "character": $column}
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
        if (!_isRunning) {
            return@withContext VibeResult.Error("Language server for '$languageId' is not running")
        }
        val params = """
            {
                "textDocument": {"uri": "$documentUri"},
                "options": {"tabSize": 4, "insertSpaces": true}
            }
        """.trimIndent()

        when (val result = sendRequest("textDocument/formatting", params)) {
            is VibeResult.Success -> {
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
        try {
            val json = JSONObject(rawJson)

            // Handle response
            if (json.has("id") && !json.isNull("id")) {
                val id = json.getLong("id")
                val deferred = pendingRequests.remove(id)
                if (deferred != null) {
                    if (json.has("error") && !json.isNull("error")) {
                        val errObj = json.getJSONObject("error")
                        val errMsg = errObj.optString("message", "Unknown LSP error")
                        deferred.completeExceptionally(RuntimeException("LSP Error: $errMsg"))
                    } else {
                        deferred.complete(rawJson)
                    }
                    return
                }
            }

            // Handle notification
            if (json.has("method")) {
                val method = json.getString("method")
                if (method == "textDocument/publishDiagnostics") {
                    parseAndPublishDiagnostics(json)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun parseAndPublishDiagnostics(json: JSONObject) {
        val params = json.optJSONObject("params") ?: return
        val uri = params.optString("uri", "")
        if (uri.isEmpty()) return

        val diagsArray = params.optJSONArray("diagnostics") ?: JSONArray()
        val diagnosticsList = mutableListOf<Diagnostic>()

        for (i in 0 until diagsArray.length()) {
            val dObj = diagsArray.optJSONObject(i) ?: continue
            val message = dObj.optString("message", "")
            val severityInt = dObj.optInt("severity", 1)
            val severity = when (severityInt) {
                1 -> DiagnosticSeverity.ERROR
                2 -> DiagnosticSeverity.WARNING
                3 -> DiagnosticSeverity.INFORMATION
                else -> DiagnosticSeverity.HINT
            }
            val rangeObj = dObj.optJSONObject("range")
            val startObj = rangeObj?.optJSONObject("start")
            val endObj = rangeObj?.optJSONObject("end")

            val startLine = startObj?.optInt("line", 0) ?: 0
            val startChar = startObj?.optInt("character", 0) ?: 0
            val endLine = endObj?.optInt("line", startLine) ?: startLine
            val endChar = endObj?.optInt("character", startChar + 1) ?: (startChar + 1)

            diagnosticsList.add(
                Diagnostic(
                    id = IdGenerator.shortId("diag"),
                    message = message,
                    severity = severity,
                    range = DiagnosticRange(
                        start = DiagnosticPosition(startLine, startChar),
                        end = DiagnosticPosition(endLine, endChar)
                    ),
                    source = languageId
                )
            )
        }

        diagnosticsFlows.getOrPut(uri) { MutableStateFlow(emptyList()) }.value = diagnosticsList
    }

    private fun parseHoverResponse(rawJson: String): HoverInfo? {
        return try {
            val json = JSONObject(rawJson)
            val result = json.opt("result") ?: return null
            if (result == JSONObject.NULL) return null

            val text = when (result) {
                is String -> result
                is JSONObject -> {
                    val contents = result.opt("contents")
                    when (contents) {
                        is String -> contents
                        is JSONObject -> contents.optString("value", contents.toString())
                        is JSONArray -> {
                            (0 until contents.length()).joinToString("\n") { idx ->
                                val item = contents.opt(idx)
                                if (item is JSONObject) item.optString("value", "") else item.toString()
                            }
                        }
                        else -> contents?.toString() ?: ""
                    }
                }
                else -> result.toString()
            }
            if (text.isNotBlank()) HoverInfo(contents = text) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseDefinitionResponse(rawJson: String): List<DefinitionLocation> {
        val list = mutableListOf<DefinitionLocation>()
        try {
            val json = JSONObject(rawJson)
            val result = json.opt("result") ?: return emptyList()
            if (result == JSONObject.NULL) return emptyList()

            val locs = when (result) {
                is JSONArray -> result
                is JSONObject -> JSONArray().apply { put(result) }
                else -> return emptyList()
            }

            for (i in 0 until locs.length()) {
                val loc = locs.optJSONObject(i) ?: continue
                val targetUri = loc.optString("targetUri", loc.optString("uri", ""))
                val range = loc.optJSONObject("targetRange") ?: loc.optJSONObject("range")
                val start = range?.optJSONObject("start")
                val line = (start?.optInt("line", 0) ?: 0) + 1
                val col = (start?.optInt("character", 0) ?: 0) + 1

                if (targetUri.isNotEmpty()) {
                    list.add(DefinitionLocation(uri = targetUri, line = line, column = col))
                }
            }
        } catch (_: Throwable) {}
        return list
    }

    private fun parseCompletionResponse(rawJson: String): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()
        try {
            val json = JSONObject(rawJson)
            val result = json.opt("result") ?: return emptyList()
            if (result == JSONObject.NULL) return emptyList()

            val array = when (result) {
                is JSONArray -> result
                is JSONObject -> result.optJSONArray("items") ?: JSONArray()
                else -> JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val label = item.optString("label", "")
                val insertText = item.optString("insertText", label)
                val kindInt = item.optInt("kind", 3)
                val kind = when (kindInt) {
                    1 -> CompletionItemKind.TEXT
                    2 -> CompletionItemKind.METHOD
                    3 -> CompletionItemKind.FUNCTION
                    6 -> CompletionItemKind.VARIABLE
                    7 -> CompletionItemKind.CLASS
                    14 -> CompletionItemKind.KEYWORD
                    15 -> CompletionItemKind.SNIPPET
                    else -> CompletionItemKind.FUNCTION
                }
                if (label.isNotEmpty()) {
                    items.add(CompletionItem(label = label, kind = kind, insertText = insertText))
                }
            }
        } catch (_: Throwable) {}
        return items
    }

    private fun extractFormattedText(rawJson: String): String {
        return try {
            val json = JSONObject(rawJson)
            val result = json.optJSONArray("result") ?: return ""
            if (result.length() > 0) {
                result.getJSONObject(0).optString("newText", "")
            } else ""
        } catch (_: Throwable) {
            ""
        }
    }
}
