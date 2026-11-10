package com.vibespace.core.language.model

/**
 * Target location for "Go to Definition" and "Find References".
 */
data class DefinitionLocation(
    val uri: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column
)
