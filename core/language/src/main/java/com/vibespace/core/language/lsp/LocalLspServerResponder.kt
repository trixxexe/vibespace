package com.vibespace.core.language.lsp

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.language.autocomplete.AutocompleteEngine
import com.vibespace.core.language.diagnostics.SyntaxDiagnosticsValidator
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.language.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded heuristic assistant that communicates with [InMemoryLspTransport].
 * Provides local keyword and syntax-based suggestions clearly labeled as local heuristics
 * rather than a live language server.
 */
class LocalLspServerResponder(
    private val languageId: String,
    private val transport: InMemoryLspTransport,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val documentContents = ConcurrentHashMap<String, String>()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            try {
                for (message in transport.clientToServer) {
                    if (!isActive) break
                    handleMessage(message)
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope.cancel()
    }

    private suspend fun handleMessage(rawJson: String) {
        val json = try {
            JSONObject(rawJson)
        } catch (_: Throwable) {
            return
        }

        val id = if (json.has("id") && !json.isNull("id")) json.getLong("id") else null
        val method = json.optString("method", "")
        val params = json.optJSONObject("params")

        when (method) {
            "initialize" -> {
                if (id != null) {
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":{"capabilities":{"hoverProvider":true,"definitionProvider":true,"completionProvider":{"resolveProvider":false},"documentFormattingProvider":true,"textDocumentSync":1}}}"""
                    transport.emitFromServer(resp)
                }
            }

            "shutdown" -> {
                if (id != null) {
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":null}"""
                    transport.emitFromServer(resp)
                }
            }

            "textDocument/didOpen" -> {
                val textDoc = params?.optJSONObject("textDocument")
                val uri = textDoc?.optString("uri", "") ?: ""
                val text = textDoc?.optString("text", "") ?: ""
                if (uri.isNotEmpty()) {
                    documentContents[uri] = text
                    val diags = SyntaxDiagnosticsValidator.validate(text, languageId)
                    emitDiagnostics(uri, diags)
                }
            }

            "textDocument/didChange" -> {
                val textDoc = params?.optJSONObject("textDocument")
                val uri = textDoc?.optString("uri", "") ?: ""
                val changes = params?.optJSONArray("contentChanges")
                val text = changes?.optJSONObject(0)?.optString("text", "") ?: ""
                if (uri.isNotEmpty()) {
                    documentContents[uri] = text
                    val diags = SyntaxDiagnosticsValidator.validate(text, languageId)
                    emitDiagnostics(uri, diags)
                }
            }

            "textDocument/didClose" -> {
                val textDoc = params?.optJSONObject("textDocument")
                val uri = textDoc?.optString("uri", "") ?: ""
                if (uri.isNotEmpty()) {
                    documentContents.remove(uri)
                }
            }

            "textDocument/hover" -> {
                if (id != null) {
                    val textDoc = params?.optJSONObject("textDocument")
                    val uri = textDoc?.optString("uri", "") ?: ""
                    val position = params?.optJSONObject("position")
                    val line = (position?.optInt("line", 0) ?: 0) + 1
                    val col = position?.optInt("character", 0) ?: 0

                    val content = documentContents[uri] ?: ""
                    val lines = content.split('\n')
                    val lineText = lines.getOrNull(line - 1) ?: lines.getOrNull(line) ?: ""
                    val word = findWordAt(lineText, col)

                    if (word.isNotEmpty()) {
                        val doc = getHoverMarkdown(word, uri, content)
                        val escapedDoc = JSONObject.quote(doc)
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":{"contents":$escapedDoc}}"""
                        transport.emitFromServer(resp)
                    } else {
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":null}"""
                        transport.emitFromServer(resp)
                    }
                }
            }

            "textDocument/definition" -> {
                if (id != null) {
                    val textDoc = params?.optJSONObject("textDocument")
                    val uri = textDoc?.optString("uri", "") ?: ""
                    val position = params?.optJSONObject("position")
                    val line = (position?.optInt("line", 0) ?: 0) + 1
                    val col = position?.optInt("character", 0) ?: 0

                    val content = documentContents[uri] ?: ""
                    val lines = content.split('\n')
                    val lineText = lines.getOrNull(line - 1) ?: ""
                    val word = findWordAt(lineText, col)

                    var targetLine = 1
                    var targetCol = 1
                    var found = false
                    if (word.isNotEmpty()) {
                        for (idx in lines.indices) {
                            val l = lines[idx]
                            val matchIdx = l.indexOf(word)
                            if (matchIdx != -1 && (l.contains("def ") || l.contains("class ") || l.contains("fun ") || l.contains("function ") || l.contains("val ") || l.contains("const ") || l.contains("let "))) {
                                targetLine = idx + 1
                                targetCol = matchIdx + 1
                                found = true
                                break
                            }
                        }
                    }

                    if (found) {
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":[{"uri":"$uri","range":{"start":{"line":${targetLine - 1},"character":${targetCol - 1}},"end":{"line":${targetLine - 1},"character":${targetCol + word.length - 1}}}}]}"""
                        transport.emitFromServer(resp)
                    } else {
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":[]}"""
                        transport.emitFromServer(resp)
                    }
                }
            }

            "textDocument/completion" -> {
                if (id != null) {
                    val textDoc = params?.optJSONObject("textDocument")
                    val uri = textDoc?.optString("uri", "") ?: ""
                    val position = params?.optJSONObject("position")
                    val line = (position?.optInt("line", 0) ?: 0) + 1
                    val col = position?.optInt("character", 0) ?: 0

                    val content = documentContents[uri] ?: ""
                    val lines = content.split('\n')
                    val lineText = lines.getOrNull(line - 1) ?: ""
                    val word = findWordAt(lineText, col)

                    val suggestions = AutocompleteEngine.getSuggestions(content, word, languageId)
                    val itemsArray = JSONArray()
                    for (item in suggestions) {
                        val iObj = JSONObject()
                        iObj.put("label", item.label)
                        iObj.put("kind", item.kind.ordinal + 1)
                        iObj.put("insertText", item.insertText)
                        itemsArray.put(iObj)
                    }
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":$itemsArray}"""
                    transport.emitFromServer(resp)
                }
            }

            "textDocument/formatting" -> {
                if (id != null) {
                    val textDoc = params?.optJSONObject("textDocument")
                    val uri = textDoc?.optString("uri", "") ?: ""
                    val content = documentContents[uri] ?: ""
                    val formatted = formatCode(content)
                    val escaped = JSONObject.quote(formatted)
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":[{"range":{"start":{"line":0,"character":0},"end":{"line":999999,"character":0}},"newText":$escaped}]}"""
                    transport.emitFromServer(resp)
                }
            }
        }
    }

    private suspend fun emitDiagnostics(uri: String, diags: List<Diagnostic>) {
        val params = JSONObject()
        params.put("uri", uri)
        val dArray = JSONArray()
        for (d in diags) {
            val dObj = JSONObject()
            dObj.put("message", d.message)
            dObj.put(
                "severity",
                when (d.severity) {
                    DiagnosticSeverity.ERROR -> 1
                    DiagnosticSeverity.WARNING -> 2
                    DiagnosticSeverity.INFORMATION -> 3
                    DiagnosticSeverity.HINT -> 4
                }
            )
            val range = JSONObject()
            val start = JSONObject().apply {
                put("line", d.range.start.line - 1)
                put("character", d.range.start.column)
            }
            val end = JSONObject().apply {
                put("line", d.range.end.line - 1)
                put("character", d.range.end.column)
            }
            range.put("start", start)
            range.put("end", end)
            dObj.put("range", range)
            dArray.put(dObj)
        }
        params.put("diagnostics", dArray)

        val root = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "textDocument/publishDiagnostics")
            put("params", params)
        }
        transport.emitFromServer(root.toString())
    }

    private fun findWordAt(line: String, col: Int): String {
        if (line.isEmpty()) return ""
        val clampedCol = col.coerceIn(0, line.length)
        var start = clampedCol
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) {
            start--
        }
        var end = clampedCol
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) {
            end++
        }
        return if (start < end) line.substring(start, end) else ""
    }

    private fun formatCode(code: String): String {
        if (code.isBlank()) return code
        val lines = code.split('\n')
        val formattedLines = lines.map { it.trimEnd() }
        return formattedLines.joinToString("\n").trimEnd() + "\n"
    }

    private fun getHoverMarkdown(word: String, uri: String, content: String): String {
        val label = "*(Local Heuristic Analyzer)*\n\n"
        val body = when (languageId.lowercase()) {
            "python" -> when (word) {
                "print" -> "```python\nprint(*values: object, sep: str = \" \", end: str = \"\\n\", file=None, flush=False) -> None\n```\nPrints values to standard output."
                "len" -> "```python\nlen(obj, /) -> int\n```\nReturn the number of items in a container."
                "range" -> "```python\nrange(stop) -> range object\nrange(start, stop[, step]) -> range object\n```\nReturn an object producing an integer sequence."
                "open" -> "```python\nopen(file, mode=\"r\", buffering=-1, encoding=None) -> file object\n```\nOpen file and return a stream."
                "def" -> "```python\ndef function_name(*args, **kwargs) -> return_type:\n```\nDefines a function or method."
                "class" -> "```python\nclass ClassName(BaseClass):\n```\nDefines a new class."
                "import" -> "```python\nimport module [as alias]\nfrom module import item\n```\nImports modules into namespace."
                "return" -> "```python\nreturn [expression]\n```\nExits a function and optionally returns values."
                "with" -> "```python\nwith context_manager [as var]:\n```\nExecutes block with a context manager."
                else -> findCustomDocInContent(word, content) ?: "```python\n(symbol) $word\n```\nPython identifier in `${uri.substringAfterLast('/')}`"
            }
            "javascript", "typescript" -> when (word) {
                "log" -> "```typescript\nconsole.log(...data: any[]): void\n```\nPrints to standard output with trailing newline."
                "console" -> "```typescript\nvar console: Console\n```\nThe console module provides a simple debugging console."
                "function" -> "```typescript\nfunction name(...args): ReturnType\n```\nDeclares a JavaScript function."
                "const" -> "```typescript\nconst identifier: type = value\n```\nDeclares a block-scoped, read-only named constant."
                "let" -> "```typescript\nlet identifier: type = value\n```\nDeclares a reassignable, block-scoped local variable."
                else -> findCustomDocInContent(word, content) ?: "```typescript\n(symbol) $word\n```\nJavaScript identifier in `${uri.substringAfterLast('/')}`"
            }
            else -> findCustomDocInContent(word, content) ?: "```$languageId\n(symbol) $word\n```\nDefined in `${uri.substringAfterLast('/')}`"
        }
        return label + body
    }

    private fun findCustomDocInContent(word: String, content: String): String? {
        val lines = content.split('\n')
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("def $word") || line.contains("fun $word") || line.contains("function $word") || line.contains("class $word")) {
                val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""
                val doc = if (nextLine.startsWith("\"\"\"") || nextLine.startsWith("/**")) " — $nextLine" else ""
                return "```$languageId\n${line.trim()}\n```$doc"
            }
        }
        return null
    }
}
