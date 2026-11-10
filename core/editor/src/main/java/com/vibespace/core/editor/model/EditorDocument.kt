package com.vibespace.core.editor.model

import com.vibespace.core.language.model.LanguageDefinition

/**
 * In-memory document model representing an open file inside the VibeSpace editor.
 */
data class EditorDocument(
    val documentId: String,
    val uri: String,
    val fileName: String,
    val content: String,
    val language: LanguageDefinition,
    val isDirty: Boolean = false,
    val lineCount: Int = content.lines().size.coerceAtLeast(1),
    val cursorPosition: CursorPosition = CursorPosition(1, 1, 0),
    val selection: TextSelection? = null,
    val version: Int = 1
)
