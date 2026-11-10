package com.vibespace.feature.editor

import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.model.EditorDocument
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.Diagnostic

data class EditorUiState(
    val openDocuments: List<EditorDocument> = emptyList(),
    val activeDocument: EditorDocument? = null,
    val cursorPosition: CursorPosition = CursorPosition(1, 1, 0),
    val fontSizeSp: Float = 13f,
    val themeId: String = "vibe_dark",
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val diagnostics: List<Diagnostic> = emptyList(),
    val completions: List<CompletionItem> = emptyList(),
    val hoverInfo: com.vibespace.core.language.model.HoverInfo? = null,
    val definitionLocations: List<com.vibespace.core.language.model.DefinitionLocation> = emptyList()
)
