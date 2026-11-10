package com.vibespace.core.language.model

/**
 * LSP Hover response model containing documentation and optional source range.
 */
data class HoverInfo(
    val contents: String,
    val range: DiagnosticRange? = null
)
