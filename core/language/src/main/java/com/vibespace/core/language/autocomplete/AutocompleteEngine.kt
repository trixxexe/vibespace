package com.vibespace.core.language.autocomplete

import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind

/**
 * Mobile-first touchscreen code autocomplete engine.
 * Generates keyword, symbol, identifier, and snippet suggestions matching user input.
 */
object AutocompleteEngine {

    private val pythonKeywords = listOf(
        "def", "class", "import", "from", "return", "if", "elif", "else",
        "for", "while", "try", "except", "finally", "with", "as", "pass",
        "break", "continue", "lambda", "yield", "async", "await", "print",
        "len", "range", "enumerate", "isinstance", "True", "False", "None"
    )

    private val jsKeywords = listOf(
        "function", "const", "let", "var", "return", "if", "else", "for",
        "while", "import", "export", "class", "extends", "async", "await",
        "try", "catch", "finally", "throw", "new", "typeof", "console.log",
        "true", "false", "null", "undefined", "Promise", "Array", "Object"
    )

    private val kotlinKeywords = listOf(
        "fun", "val", "var", "class", "interface", "object", "return", "if",
        "else", "when", "for", "while", "import", "package", "override",
        "suspend", "data", "sealed", "println", "true", "false", "null"
    )

    fun getSuggestions(
        content: String,
        prefix: String,
        languageId: String
    ): List<CompletionItem> {
        val cleanPrefix = prefix.trim()
        val languageKeywords = when (languageId.lowercase()) {
            "python" -> pythonKeywords
            "javascript", "typescript" -> jsKeywords
            "kotlin" -> kotlinKeywords
            else -> pythonKeywords + jsKeywords
        }

        // Extract identifier symbols present in document
        val identifierRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]{2,}\b""")
        val documentSymbols = identifierRegex.findAll(content)
            .map { it.value }
            .filter { it != cleanPrefix }
            .distinct()
            .take(50)
            .toList()

        val results = mutableListOf<CompletionItem>()

        // 1. Match Keywords
        for (kw in languageKeywords) {
            if (cleanPrefix.isEmpty() || kw.startsWith(cleanPrefix, ignoreCase = true)) {
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

        // 2. Match Document Symbols (variables, function names, classes)
        for (sym in documentSymbols) {
            if (results.none { it.label == sym }) {
                if (cleanPrefix.isEmpty() || sym.startsWith(cleanPrefix, ignoreCase = true)) {
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

        // Sort: exact matches / prefix matches first, then alphabetical
        return results.sortedWith(
            compareBy(
                { !it.label.startsWith(cleanPrefix, ignoreCase = true) },
                { it.kind != CompletionItemKind.KEYWORD },
                { it.label }
            )
        ).take(15)
    }
}
