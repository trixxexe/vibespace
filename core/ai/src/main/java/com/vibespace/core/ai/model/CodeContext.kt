package com.vibespace.core.ai.model

data class CodeContext(
    val fileUri: String,
    val fileName: String,
    val language: String,
    val selectedText: String? = null,
    val fullContent: String? = null,
    val cursorLine: Int = 1
)

data class CodeChange(
    val fileUri: String,
    val originalSnippet: String,
    val proposedSnippet: String,
    val explanation: String
)

data class DiffPreview(
    val originalText: String,
    val modifiedText: String,
    val addedLines: Int,
    val removedLines: Int
)
