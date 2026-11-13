package com.vibespace.core.language.autocomplete

import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind

/**
 * Mobile-first touchscreen code autocomplete engine.
 * Generates keyword, built-in function, symbol, identifier, and snippet suggestions matching user input.
 */
object AutocompleteEngine {

    private val pythonKeywords = listOf(
        "def", "class", "import", "from", "return", "if", "elif", "else",
        "for", "while", "try", "except", "finally", "with", "as", "pass",
        "break", "continue", "lambda", "yield", "async", "await", "raise",
        "assert", "global", "nonlocal", "in", "is", "not", "and", "or",
        "True", "False", "None", "self", "__init__", "__main__", "__name__"
    )

    private val pythonBuiltins = listOf(
        "print" to "print()",
        "len" to "len()",
        "range" to "range()",
        "enumerate" to "enumerate()",
        "isinstance" to "isinstance()",
        "input" to "input()",
        "open" to "open()",
        "str" to "str()",
        "int" to "int()",
        "float" to "float()",
        "list" to "list()",
        "dict" to "dict()",
        "set" to "set()",
        "tuple" to "tuple()",
        "sorted" to "sorted()",
        "map" to "map()",
        "filter" to "filter()",
        "zip" to "zip()",
        "sum" to "sum()",
        "min" to "min()",
        "max" to "max()",
        "abs" to "abs()",
        "type" to "type()"
    )

    private val jsKeywords = listOf(
        "function", "const", "let", "var", "return", "if", "else", "for",
        "while", "switch", "case", "default", "break", "continue", "import",
        "export", "from", "class", "extends", "constructor", "super", "this",
        "async", "await", "try", "catch", "finally", "throw", "new", "typeof",
        "instanceof", "true", "false", "null", "undefined", "interface", "type"
    )

    private val jsBuiltins = listOf(
        "console.log" to "console.log()",
        "console.error" to "console.error()",
        "Promise" to "Promise",
        "Array" to "Array",
        "Object" to "Object",
        "JSON.stringify" to "JSON.stringify()",
        "JSON.parse" to "JSON.parse()",
        "setTimeout" to "setTimeout(() => {\n  \n}, 1000)",
        "fetch" to "fetch()",
        "document.querySelector" to "document.querySelector()",
        "document.getElementById" to "document.getElementById()",
        "addEventListener" to "addEventListener()"
    )

    private val kotlinKeywords = listOf(
        "fun", "val", "var", "class", "interface", "object", "return", "if",
        "else", "when", "for", "while", "do", "import", "package", "override",
        "suspend", "data", "sealed", "enum", "companion", "private", "public",
        "internal", "protected", "try", "catch", "finally", "throw", "true",
        "false", "null", "this", "super", "is", "in", "by", "lazy"
    )

    private val kotlinBuiltins = listOf(
        "println" to "println()",
        "print" to "print()",
        "listOf" to "listOf()",
        "mutableListOf" to "mutableListOf()",
        "mapOf" to "mapOf()",
        "mutableMapOf" to "mutableMapOf()",
        "setOf" to "setOf()",
        "require" to "require()",
        "check" to "check()",
        "runCatching" to "runCatching {\n    \n}"
    )

    private val htmlKeywords = listOf(
        "div", "span", "section", "header", "footer", "main", "nav", "article",
        "button", "input", "form", "label", "select", "option", "textarea",
        "h1", "h2", "h3", "p", "a", "img", "ul", "ol", "li", "table", "tr", "td",
        "script", "style", "link", "meta", "title", "head", "body", "html", "canvas"
    )

    private val cssKeywords = listOf(
        "display", "flex", "grid", "position", "relative", "absolute", "fixed",
        "width", "height", "margin", "padding", "border", "border-radius",
        "background", "background-color", "color", "font-size", "font-weight",
        "justify-content", "align-items", "gap", "box-shadow", "opacity", "transition"
    )

    private val shellKeywords = listOf(
        "echo", "cd", "ls", "mkdir", "rm", "cp", "mv", "cat", "grep", "sed",
        "awk", "find", "chmod", "chown", "tar", "curl", "wget", "git", "python3",
        "node", "npm", "pip", "apk", "export", "if", "then", "else", "fi", "for", "do", "done"
    )

    fun getSuggestions(
        content: String,
        prefix: String,
        languageId: String
    ): List<CompletionItem> {
        val cleanPrefix = prefix.trim()
        if (cleanPrefix.isEmpty()) return emptyList()
        val lang = languageId.lowercase()
        val languageKeywords = when (lang) {
            "python" -> pythonKeywords
            "javascript", "typescript", "js", "ts" -> jsKeywords
            "kotlin", "kt", "java" -> kotlinKeywords
            "html" -> htmlKeywords
            "css" -> cssKeywords
            "shell", "sh", "bash" -> shellKeywords
            else -> pythonKeywords + jsKeywords + kotlinKeywords
        }

        val builtins = when (lang) {
            "python" -> pythonBuiltins
            "javascript", "typescript", "js", "ts" -> jsBuiltins
            "kotlin", "kt", "java" -> kotlinBuiltins
            else -> pythonBuiltins + jsBuiltins
        }

        // Extract identifier symbols present in document
        val identifierRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]{2,}\b""")
        val documentSymbols = identifierRegex.findAll(content)
            .map { it.value }
            .filter { !it.equals(cleanPrefix, ignoreCase = false) }
            .distinct()
            .take(80)
            .toList()

        val results = mutableListOf<CompletionItem>()

        // 1. Match Keywords
        for (kw in languageKeywords) {
            if (kw.startsWith(cleanPrefix, ignoreCase = true) || (cleanPrefix.length >= 2 && kw.contains(cleanPrefix, ignoreCase = true))) {
                results.add(
                    CompletionItem(
                        label = kw,
                        kind = CompletionItemKind.KEYWORD,
                        detail = "keyword",
                        insertText = kw
                    )
                )
            }
        }

        // 2. Match Built-in Functions
        for ((label, insertText) in builtins) {
            if (label.startsWith(cleanPrefix, ignoreCase = true) || (cleanPrefix.length >= 2 && label.contains(cleanPrefix, ignoreCase = true))) {
                if (results.none { it.label == label }) {
                    results.add(
                        CompletionItem(
                            label = label,
                            kind = CompletionItemKind.FUNCTION,
                            detail = "builtin",
                            insertText = insertText
                        )
                    )
                }
            }
        }

        // 3. Match Document Symbols (variables, function names, classes)
        for (sym in documentSymbols) {
            if (results.none { it.label == sym }) {
                if (sym.startsWith(cleanPrefix, ignoreCase = true) || (cleanPrefix.length >= 2 && sym.contains(cleanPrefix, ignoreCase = true))) {
                    results.add(
                        CompletionItem(
                            label = sym,
                            kind = CompletionItemKind.VARIABLE,
                            detail = "symbol",
                            insertText = sym
                        )
                    )
                }
            }
        }

        // Sort: exact prefix matches first, then document symbols/keywords, then alphabetical
        return results.sortedWith(
            compareBy(
                { !it.label.startsWith(cleanPrefix, ignoreCase = false) },
                { !it.label.startsWith(cleanPrefix, ignoreCase = true) },
                { it.kind != CompletionItemKind.KEYWORD && it.kind != CompletionItemKind.FUNCTION },
                { it.label.length },
                { it.label }
            )
        ).take(20)
    }
}
