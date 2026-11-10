package com.vibespace.core.language.snippets

import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind

/**
 * Mobile-first snippet and keyword catalog providing intelligent auto-completions
 * for Python, JavaScript/TypeScript, Kotlin, HTML, CSS, and Shell.
 */
object SnippetRepository {

    fun getSuggestions(languageId: String, prefix: String): List<CompletionItem> {
        val all = getSnippetsForLanguage(languageId)
        if (prefix.isBlank()) {
            return all.take(8)
        }
        val cleanPrefix = prefix.lowercase()
        return all.filter {
            it.label.lowercase().startsWith(cleanPrefix) ||
                (it.insertText.lowercase().contains(cleanPrefix))
        }.sortedWith(
            compareBy(
                { !it.label.lowercase().startsWith(cleanPrefix) },
                { it.label.length }
            )
        ).take(8)
    }

    fun getSnippetsForLanguage(languageId: String): List<CompletionItem> {
        return when (languageId.lowercase()) {
            "python", "py" -> PYTHON_SNIPPETS
            "javascript", "js", "typescript", "ts" -> JS_SNIPPETS
            "kotlin", "kt" -> KOTLIN_SNIPPETS
            "html" -> HTML_SNIPPETS
            "css" -> CSS_SNIPPETS
            "shell", "sh", "bash" -> SHELL_SNIPPETS
            else -> DEFAULT_SNIPPETS
        }
    }

    private val PYTHON_SNIPPETS = listOf(
        CompletionItem(
            label = "print()",
            kind = CompletionItemKind.SNIPPET,
            detail = "Print statement",
            insertText = "print(\"\")"
        ),
        CompletionItem(
            label = "def",
            kind = CompletionItemKind.SNIPPET,
            detail = "Define function",
            insertText = "def function_name():\n    pass\n"
        ),
        CompletionItem(
            label = "if __name__",
            kind = CompletionItemKind.SNIPPET,
            detail = "Main execution block",
            insertText = "if __name__ == \"__main__\":\n    pass\n"
        ),
        CompletionItem(
            label = "class",
            kind = CompletionItemKind.SNIPPET,
            detail = "Define class",
            insertText = "class ClassName:\n    def __init__(self):\n        pass\n"
        ),
        CompletionItem(
            label = "for in",
            kind = CompletionItemKind.SNIPPET,
            detail = "For loop",
            insertText = "for item in items:\n    pass\n"
        ),
        CompletionItem(
            label = "try/except",
            kind = CompletionItemKind.SNIPPET,
            detail = "Exception handling",
            insertText = "try:\n    pass\nexcept Exception as e:\n    print(e)\n"
        ),
        CompletionItem(
            label = "import",
            kind = CompletionItemKind.KEYWORD,
            detail = "Import module",
            insertText = "import "
        ),
        CompletionItem(
            label = "from import",
            kind = CompletionItemKind.SNIPPET,
            detail = "From import statement",
            insertText = "from module import name"
        ),
        CompletionItem(
            label = "return",
            kind = CompletionItemKind.KEYWORD,
            detail = "Return value",
            insertText = "return "
        ),
        CompletionItem(
            label = "with open",
            kind = CompletionItemKind.SNIPPET,
            detail = "Open file context",
            insertText = "with open(\"file.txt\", \"r\") as f:\n    content = f.read()\n"
        )
    )

    private val JS_SNIPPETS = listOf(
        CompletionItem(
            label = "console.log()",
            kind = CompletionItemKind.SNIPPET,
            detail = "Log to console",
            insertText = "console.log();"
        ),
        CompletionItem(
            label = "const",
            kind = CompletionItemKind.KEYWORD,
            detail = "Constant declaration",
            insertText = "const name = value;"
        ),
        CompletionItem(
            label = "let",
            kind = CompletionItemKind.KEYWORD,
            detail = "Variable declaration",
            insertText = "let name = value;"
        ),
        CompletionItem(
            label = "function",
            kind = CompletionItemKind.SNIPPET,
            detail = "Function declaration",
            insertText = "function name(args) {\n    \n}"
        ),
        CompletionItem(
            label = "arrow fn",
            kind = CompletionItemKind.SNIPPET,
            detail = "Arrow function",
            insertText = "const name = () => {\n    \n};"
        ),
        CompletionItem(
            label = "async/await",
            kind = CompletionItemKind.SNIPPET,
            detail = "Async function",
            insertText = "async function fetchData() {\n    const res = await fetch(url);\n    return res.json();\n}"
        ),
        CompletionItem(
            label = "if",
            kind = CompletionItemKind.SNIPPET,
            detail = "If condition",
            insertText = "if (condition) {\n    \n}"
        ),
        CompletionItem(
            label = "for of",
            kind = CompletionItemKind.SNIPPET,
            detail = "For of loop",
            insertText = "for (const item of items) {\n    \n}"
        ),
        CompletionItem(
            label = "import",
            kind = CompletionItemKind.KEYWORD,
            detail = "ES import",
            insertText = "import { module } from \"package\";"
        ),
        CompletionItem(
            label = "export",
            kind = CompletionItemKind.KEYWORD,
            detail = "ES export",
            insertText = "export const "
        )
    )

    private val KOTLIN_SNIPPETS = listOf(
        CompletionItem(
            label = "fun",
            kind = CompletionItemKind.SNIPPET,
            detail = "Function declaration",
            insertText = "fun name(): Unit {\n    \n}"
        ),
        CompletionItem(
            label = "val",
            kind = CompletionItemKind.KEYWORD,
            detail = "Read-only property",
            insertText = "val name = "
        ),
        CompletionItem(
            label = "var",
            kind = CompletionItemKind.KEYWORD,
            detail = "Mutable property",
            insertText = "var name = "
        ),
        CompletionItem(
            label = "data class",
            kind = CompletionItemKind.SNIPPET,
            detail = "Data class",
            insertText = "data class Name(\n    val id: String\n)"
        ),
        CompletionItem(
            label = "when",
            kind = CompletionItemKind.SNIPPET,
            detail = "When expression",
            insertText = "when (state) {\n    else -> Unit\n}"
        ),
        CompletionItem(
            label = "println()",
            kind = CompletionItemKind.SNIPPET,
            detail = "Print line",
            insertText = "println(\"\")"
        ),
        CompletionItem(
            label = "suspend fun",
            kind = CompletionItemKind.SNIPPET,
            detail = "Coroutine function",
            insertText = "suspend fun name(): Unit {\n    \n}"
        )
    )

    private val HTML_SNIPPETS = listOf(
        CompletionItem(label = "<div>", kind = CompletionItemKind.SNIPPET, insertText = "<div></div>"),
        CompletionItem(label = "<span>", kind = CompletionItemKind.SNIPPET, insertText = "<span></span>"),
        CompletionItem(label = "<button>", kind = CompletionItemKind.SNIPPET, insertText = "<button type=\"button\"></button>"),
        CompletionItem(label = "<a>", kind = CompletionItemKind.SNIPPET, insertText = "<a href=\"\"></a>"),
        CompletionItem(label = "<p>", kind = CompletionItemKind.SNIPPET, insertText = "<p></p>"),
        CompletionItem(label = "<h1>", kind = CompletionItemKind.SNIPPET, insertText = "<h1></h1>"),
        CompletionItem(label = "<input>", kind = CompletionItemKind.SNIPPET, insertText = "<input type=\"text\" placeholder=\"\" />")
    )

    private val CSS_SNIPPETS = listOf(
        CompletionItem(label = "display: flex", kind = CompletionItemKind.SNIPPET, insertText = "display: flex;"),
        CompletionItem(label = "justify-content", kind = CompletionItemKind.SNIPPET, insertText = "justify-content: center;"),
        CompletionItem(label = "align-items", kind = CompletionItemKind.SNIPPET, insertText = "align-items: center;"),
        CompletionItem(label = "margin", kind = CompletionItemKind.SNIPPET, insertText = "margin: 0;"),
        CompletionItem(label = "padding", kind = CompletionItemKind.SNIPPET, insertText = "padding: 0;"),
        CompletionItem(label = "color", kind = CompletionItemKind.SNIPPET, insertText = "color: #ffffff;"),
        CompletionItem(label = "background", kind = CompletionItemKind.SNIPPET, insertText = "background: #12141a;")
    )

    private val SHELL_SNIPPETS = listOf(
        CompletionItem(label = "#!/bin/bash", kind = CompletionItemKind.SNIPPET, insertText = "#!/usr/bin/env bash\nset -euo pipefail\n\n"),
        CompletionItem(label = "echo", kind = CompletionItemKind.KEYWORD, insertText = "echo \"\""),
        CompletionItem(label = "if [ ]", kind = CompletionItemKind.SNIPPET, insertText = "if [ condition ]; then\n    echo \"ok\"\nfi\n")
    )

    private val DEFAULT_SNIPPETS = listOf(
        CompletionItem(label = "TODO", kind = CompletionItemKind.SNIPPET, insertText = "// TODO: "),
        CompletionItem(label = "FIXME", kind = CompletionItemKind.SNIPPET, insertText = "// FIXME: ")
    )
}
