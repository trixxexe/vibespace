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
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded Language Server responder that communicates with [InMemoryLspTransport]
 * providing JSON-RPC 2.0 capabilities for hover documentation, go-to-definition,
 * document formatting, completions, and syntax diagnostics.
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
        val trimmed = rawJson.trim()
        val idMatch = Regex(""""id"\s*:\s*(\d+)""").find(trimmed)
        val id = idMatch?.groupValues?.get(1)?.toLongOrNull()

        val methodMatch = Regex(""""method"\s*:\s*"([^"]+)"""").find(trimmed)
        val method = methodMatch?.groupValues?.get(1) ?: return

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
                val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                val textMatch = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                if (uriMatch != null && textMatch != null) {
                    val uri = unescapeJson(uriMatch.groupValues[1])
                    val text = unescapeJson(textMatch.groupValues[1])
                    documentContents[uri] = text
                    val diags = SyntaxDiagnosticsValidator.validate(text, languageId)
                    emitDiagnostics(uri, diags)
                }
            }

            "textDocument/didChange" -> {
                val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                val textMatch = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                if (uriMatch != null && textMatch != null) {
                    val uri = unescapeJson(uriMatch.groupValues[1])
                    val text = unescapeJson(textMatch.groupValues[1])
                    documentContents[uri] = text
                    val diags = SyntaxDiagnosticsValidator.validate(text, languageId)
                    emitDiagnostics(uri, diags)
                }
            }

            "textDocument/didClose" -> {
                val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                if (uriMatch != null) {
                    val uri = unescapeJson(uriMatch.groupValues[1])
                    documentContents.remove(uri)
                }
            }

            "textDocument/hover" -> {
                if (id != null) {
                    val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                    val lineMatch = Regex(""""line"\s*:\s*(\d+)""").find(trimmed)
                    val charMatch = Regex(""""character"\s*:\s*(\d+)""").find(trimmed)

                    val uri = uriMatch?.let { unescapeJson(it.groupValues[1]) } ?: ""
                    val line = lineMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val col = charMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    val content = documentContents[uri] ?: ""
                    val lines = content.split('\n')
                    val lineText = lines.getOrNull(line - 1) ?: lines.getOrNull(line) ?: ""
                    val word = findWordAt(lineText, col)

                    if (word.isNotEmpty()) {
                        val doc = getHoverMarkdown(word, uri, content)
                        val escapedDoc = escapeJson(doc)
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":{"contents":"$escapedDoc"}}"""
                        transport.emitFromServer(resp)
                    } else {
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":null}"""
                        transport.emitFromServer(resp)
                    }
                }
            }

            "textDocument/definition" -> {
                if (id != null) {
                    val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                    val lineMatch = Regex(""""line"\s*:\s*(\d+)""").find(trimmed)
                    val charMatch = Regex(""""character"\s*:\s*(\d+)""").find(trimmed)

                    val uri = uriMatch?.let { unescapeJson(it.groupValues[1]) } ?: ""
                    val line = lineMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val col = charMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    val content = documentContents[uri] ?: ""
                    val lines = content.split('\n')
                    val lineText = lines.getOrNull(line - 1) ?: lines.getOrNull(line) ?: ""
                    val word = findWordAt(lineText, col)

                    var defLine = 1
                    var found = false
                    if (word.isNotEmpty()) {
                        for (i in lines.indices) {
                            val l = lines[i]
                            if (l.contains("def $word") || l.contains("class $word") || l.contains("fun $word") || l.contains("function $word") || l.contains("const $word") || l.contains("let $word") || l.contains("var $word")) {
                                defLine = i + 1
                                found = true
                                break
                            }
                        }
                    }

                    if (found && word.isNotEmpty()) {
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":[{"uri":"$uri","range":{"start":{"line":$defLine,"character":0},"end":{"line":$defLine,"character":${word.length}}}}]}"""
                        transport.emitFromServer(resp)
                    } else {
                        val resp = """{"jsonrpc":"2.0","id":$id,"result":[]}"""
                        transport.emitFromServer(resp)
                    }
                }
            }

            "textDocument/completion" -> {
                if (id != null) {
                    val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                    val lineMatch = Regex(""""line"\s*:\s*(\d+)""").find(trimmed)
                    val charMatch = Regex(""""character"\s*:\s*(\d+)""").find(trimmed)

                    val uri = uriMatch?.let { unescapeJson(it.groupValues[1]) } ?: ""
                    val line = lineMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val col = charMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    val content = documentContents[uri] ?: ""
                    val lines = content.split('\n')
                    val lineText = lines.getOrNull(line - 1) ?: ""
                    val word = findWordAt(lineText, col)

                    val suggestions = AutocompleteEngine.getSuggestions(content, word, languageId)
                    val itemsJson = suggestions.joinToString(",") { item ->
                        """{"label":"${escapeJson(item.label)}","kind":${item.kind.ordinal + 1},"insertText":"${escapeJson(item.insertText)}"}"""
                    }
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":[$itemsJson]}"""
                    transport.emitFromServer(resp)
                }
            }

            "textDocument/formatting" -> {
                if (id != null) {
                    val uriMatch = Regex(""""uri"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(trimmed)
                    val uri = uriMatch?.let { unescapeJson(it.groupValues[1]) } ?: ""
                    val content = documentContents[uri] ?: ""
                    val formatted = formatCode(content)
                    val escaped = escapeJson(formatted)
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":[{"range":{"start":{"line":0,"character":0},"end":{"line":999999,"character":0}},"newText":"$escaped"}]}"""
                    transport.emitFromServer(resp)
                }
            }
        }
    }

    private suspend fun emitDiagnostics(uri: String, diags: List<Diagnostic>) {
        val diagJson = StringBuilder()
        diagJson.append("""{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics","params":{"uri":"$uri","diagnostics":[""")
        diags.forEachIndexed { idx, d ->
            val sev = when (d.severity) {
                DiagnosticSeverity.ERROR -> 1
                DiagnosticSeverity.WARNING -> 2
                DiagnosticSeverity.INFORMATION -> 3
                DiagnosticSeverity.HINT -> 4
            }
            diagJson.append("""{"message":"${escapeJson(d.message)}","severity":$sev,"range":{"start":{"line":${d.range.start.line},"character":${d.range.start.column}},"end":{"line":${d.range.end.line},"character":${d.range.end.column}}}}""")
            if (idx < diags.size - 1) diagJson.append(",")
        }
        diagJson.append("]}}")
        transport.emitFromServer(diagJson.toString())
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
        return when (languageId.lowercase()) {
            "python" -> when (word) {
                "print" -> "```python\nprint(*values: object, sep: str = \" \", end: str = \"\\n\", file=None, flush=False) -> None\n```\nPrints the values to a stream, or to sys.stdout by default."
                "len" -> "```python\nlen(obj, /) -> int\n```\nReturn the number of items in a container."
                "range" -> "```python\nrange(stop) -> range object\nrange(start, stop[, step]) -> range object\n```\nReturn an object that produces a sequence of integers from start to stop."
                "open" -> "```python\nopen(file, mode=\"r\", buffering=-1, encoding=None) -> file object\n```\nOpen file and return a stream."
                "def" -> "```python\ndef function_name(*args, **kwargs) -> return_type:\n```\nDefines a function or method."
                "class" -> "```python\nclass ClassName(BaseClass):\n```\nDefines a new class."
                "import" -> "```python\nimport module [as alias]\nfrom module import item\n```\nImports modules into the current namespace."
                "return" -> "```python\nreturn [expression]\n```\nExits a function and optionally returns values."
                "with" -> "```python\nwith context_manager [as var]:\n```\nExecutes block with a context manager (RAII)."
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
