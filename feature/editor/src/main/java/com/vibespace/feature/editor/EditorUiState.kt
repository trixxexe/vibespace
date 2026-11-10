package com.vibespace.feature.editor

import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.model.EditorDocument

data class EditorUiState(
    val openDocuments: List<EditorDocument> = emptyList(),
    val activeDocument: EditorDocument? = null,
    val cursorPosition: CursorPosition = CursorPosition(1, 1, 0),
    val fontSizeSp: Float = 13f,
    val themeId: String = "vibe_dark",
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)
