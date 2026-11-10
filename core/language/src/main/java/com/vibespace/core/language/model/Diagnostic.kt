package com.vibespace.core.language.model

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFORMATION,
    HINT
}

data class DiagnosticPosition(
    val line: Int,
    val column: Int
)

data class DiagnosticRange(
    val start: DiagnosticPosition,
    val end: DiagnosticPosition
)

/**
 * Compiler / Linter code diagnostic contract matching LSP Diagnostic model.
 */
data class Diagnostic(
    val id: String,
    val message: String,
    val severity: DiagnosticSeverity,
    val range: DiagnosticRange,
    val source: String? = null,
    val code: String? = null
)
