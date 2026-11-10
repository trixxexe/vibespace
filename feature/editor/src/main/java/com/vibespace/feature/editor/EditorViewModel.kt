package com.vibespace.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.editor.engine.EditorEngine
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.model.EditorDocument
import com.vibespace.core.language.detector.LanguageDetector
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    private val editorEngine: EditorEngine,
    private val workspaceManager: WorkspaceManager,
    private val languageDetector: LanguageDetector,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        observeEditorEngine()
    }

    private fun observeEditorEngine() {
        viewModelScope.launch(dispatchers.default) {
            editorEngine.documents.collect { docs ->
                _uiState.update { it.copy(openDocuments = docs) }
            }
        }
        viewModelScope.launch(dispatchers.default) {
            editorEngine.activeDocument.collect { activeDoc ->
                _uiState.update {
                    it.copy(
                        activeDocument = activeDoc,
                        cursorPosition = activeDoc?.cursorPosition ?: CursorPosition(1, 1, 0)
                    )
                }
            }
        }
    }

    fun openFile(file: VirtualFile) {
        viewModelScope.launch(dispatchers.io) {
            val readResult = workspaceManager.readFile(file.uri)
            val content = readResult.getOrDefault("")
            val language = languageDetector.detectByFileName(file.name)

            editorEngine.openDocument(
                uri = file.uri,
                fileName = file.name,
                content = content,
                language = language
            )
        }
    }

    fun switchActiveTab(documentId: String) {
        viewModelScope.launch(dispatchers.default) {
            editorEngine.setActiveDocument(documentId)
        }
    }

    fun closeTab(documentId: String) {
        viewModelScope.launch(dispatchers.default) {
            editorEngine.closeDocument(documentId)
        }
    }

    fun onContentChanged(newContent: String) {
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            editorEngine.updateDocument(activeDoc.documentId, newContent)
        }
    }

    fun onCursorPositionChanged(cursor: CursorPosition) {
        _uiState.update { it.copy(cursorPosition = cursor) }
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            editorEngine.updateCursorPosition(activeDoc.documentId, cursor)
        }
    }

    fun saveActiveDocument() {
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSaving = true) }
            val writeResult = workspaceManager.writeFile(activeDoc.uri, activeDoc.content)
            writeResult.onSuccess {
                editorEngine.markSaved(activeDoc.documentId)
                _uiState.update { it.copy(isSaving = false, statusMessage = "Saved ${activeDoc.fileName}") }
            }.onError { err ->
                _uiState.update { it.copy(isSaving = false, statusMessage = "Save failed: ${err.message}") }
            }
        }
    }

    fun undo() {
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            editorEngine.undo(activeDoc.documentId)
        }
    }

    fun redo() {
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            editorEngine.redo(activeDoc.documentId)
        }
    }

    fun setFontSize(fontSizeSp: Float) {
        _uiState.update { it.copy(fontSizeSp = fontSizeSp.coerceIn(10f, 24f)) }
    }
}
