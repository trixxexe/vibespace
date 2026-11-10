package com.vibespace.core.language.model

enum class CompletionItemKind {
    TEXT,
    METHOD,
    FUNCTION,
    CONSTRUCTOR,
    FIELD,
    VARIABLE,
    CLASS,
    INTERFACE,
    MODULE,
    PROPERTY,
    UNIT,
    VALUE,
    ENUM,
    KEYWORD,
    SNIPPET,
    COLOR,
    FILE,
    REFERENCE,
    FOLDER
}

/**
 * Autocomplete item model aligned with LSP specifications.
 */
data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind = CompletionItemKind.TEXT,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String = label,
    val sortText: String? = null
)
