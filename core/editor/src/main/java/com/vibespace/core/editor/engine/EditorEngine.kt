package com.vibespace.core.editor.engine

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.model.EditorDocument
import com.vibespace.core.language.model.LanguageDefinition
import kotlinx.coroutines.flow.StateFlow

/**
 * Editor Engine contract as required by Section 8.
 * Decouples the UI from the concrete editor implementation (Default Compose editor vs future Sora Editor).
 */
interface EditorEngine {
    val documents: StateFlow<List<EditorDocument>>
    val activeDocument: StateFlow<EditorDocument?>

    suspend fun openDocument(
        uri: String,
        fileName: String,
        content: String,
        language: LanguageDefinition
    ): VibeResult<EditorDocument>

    suspend fun closeDocument(documentId: String): VibeResult<Unit>
    suspend fun setActiveDocument(documentId: String): VibeResult<Unit>

    suspend fun updateDocument(documentId: String, newContent: String): VibeResult<EditorDocument>
    suspend fun updateCursorPosition(documentId: String, cursorPosition: CursorPosition): VibeResult<Unit>
    suspend fun markSaved(documentId: String): VibeResult<Unit>

    fun getDocument(documentId: String): EditorDocument?

    suspend fun undo(documentId: String): VibeResult<EditorDocument>
    suspend fun redo(documentId: String): VibeResult<EditorDocument>

    fun find(documentId: String, query: String, matchCase: Boolean = false): List<IntRange>
    suspend fun replace(documentId: String, range: IntRange, replacement: String): VibeResult<EditorDocument>
    suspend fun replaceAll(documentId: String, query: String, replacement: String, matchCase: Boolean = false): VibeResult<EditorDocument>
}
