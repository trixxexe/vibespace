package com.vibespace.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.getOrDefault
import com.vibespace.core.editor.engine.EditorEngine
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.model.EditorDocument
import com.vibespace.core.language.autocomplete.AutocompleteEngine
import com.vibespace.core.language.detector.LanguageDetector
import com.vibespace.core.language.diagnostics.SyntaxDiagnosticsValidator
import com.vibespace.core.language.model.CompletionItem
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
    private val lspClientProvider: ((String) -> com.vibespace.core.language.lsp.LanguageServerClient?)? = null,
    private val gitManager: com.vibespace.core.workspace.git.GitManager? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private var activeLspClient: com.vibespace.core.language.lsp.LanguageServerClient? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        observeEditorEngine()
        observeWorkspace()
    }

    private fun observeEditorEngine() {
        viewModelScope.launch(dispatchers.default) {
            editorEngine.documents.collect { docs ->
                _uiState.update { it.copy(openDocuments = docs) }
            }
        }
        viewModelScope.launch(dispatchers.default) {
            editorEngine.activeDocument.collect { activeDoc ->
                val diags = if (activeDoc != null) {
                    SyntaxDiagnosticsValidator.validate(activeDoc.content, activeDoc.language.id)
                } else emptyList()
                val suggestions = if (activeDoc != null) {
                    AutocompleteEngine.getSuggestions(activeDoc.content, "", activeDoc.language.id)
                } else emptyList()

                _uiState.update {
                    it.copy(
                        activeDocument = activeDoc,
                        cursorPosition = activeDoc?.cursorPosition ?: CursorPosition(1, 1, 0),
                        diagnostics = diags,
                        completions = suggestions
                    )
                }
            }
        }
    }

    private fun observeWorkspace() {
        viewModelScope.launch(dispatchers.default) {
            workspaceManager.currentProject.collect { project ->
                if (project != null && editorEngine.documents.value.isEmpty()) {
                    val mainPyUri = "${project.rootUri}/main.py"
                    val readResult = workspaceManager.readFile(mainPyUri)
                    if (readResult is com.vibespace.core.common.result.VibeResult.Success) {
                        val language = languageDetector.detectByFileName("main.py")
                        editorEngine.openDocument(
                            uri = mainPyUri,
                            fileName = "main.py",
                            content = readResult.data,
                            language = language
                        )
                        val client = lspClientProvider?.invoke(language.id)
                        activeLspClient = client
                        if (client != null) {
                            if (!client.isRunning) {
                                client.start(project.rootUri)
                            }
                            if (client.isRunning) {
                                client.didOpen(mainPyUri, readResult.data)
                            }
                        }
                    }
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
            val client = lspClientProvider?.invoke(language.id)
            activeLspClient = client
            if (client != null) {
                if (!client.isRunning) {
                    val rootUri = workspaceManager.currentProject.value?.rootUri ?: file.parentUri ?: file.uri
                    client.start(rootUri)
                }
                if (client.isRunning) {
                    client.didOpen(file.uri, content)
                }
            }
        }
    }

    fun switchActiveTab(documentId: String) {
        viewModelScope.launch(dispatchers.default) {
            editorEngine.setActiveDocument(documentId)
            val doc = editorEngine.documents.value.find { it.documentId == documentId }
            if (doc != null) {
                val client = lspClientProvider?.invoke(doc.language.id)
                activeLspClient = client
                if (client != null && !client.isRunning) {
                    val rootUri = workspaceManager.currentProject.value?.rootUri ?: doc.uri
                    client.start(rootUri)
                }
            }
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
            val diags = SyntaxDiagnosticsValidator.validate(newContent, activeDoc.language.id)
            val suggestions = AutocompleteEngine.getSuggestions(newContent, "", activeDoc.language.id)
            _uiState.update { it.copy(diagnostics = diags, completions = suggestions) }
            val client = activeLspClient
            if (client != null && client.isRunning) {
                client.didChange(activeDoc.uri, newContent, activeDoc.version)
            }
        }
    }

    fun updateCompletions(prefix: String) {
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            val suggestions = AutocompleteEngine.getSuggestions(activeDoc.content, prefix, activeDoc.language.id)
            _uiState.update { it.copy(completions = suggestions) }
        }
    }

    fun insertCompletion(item: CompletionItem) {
        val activeDoc = _uiState.value.activeDocument ?: return
        val currentContent = activeDoc.content
        val insertText = item.insertText
        val newContent = if (currentContent.isEmpty()) {
            insertText
        } else if (currentContent.endsWith(" ") || currentContent.endsWith("\n")) {
            currentContent + insertText
        } else {
            val lastSpace = maxOf(currentContent.lastIndexOf(' '), currentContent.lastIndexOf('\n'))
            if (lastSpace == -1) insertText else currentContent.substring(0, lastSpace + 1) + insertText
        }
        onContentChanged(newContent)
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
            when (val writeResult = workspaceManager.writeFile(activeDoc.uri, activeDoc.content)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    editorEngine.markSaved(activeDoc.documentId)
                    gitManager?.refreshStatus()
                    _uiState.update { it.copy(isSaving = false, statusMessage = "Saved ${activeDoc.fileName}") }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(isSaving = false, statusMessage = "Save failed: ${writeResult.message}") }
                }
                else -> Unit
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

    fun setEditorTheme(themeId: String) {
        _uiState.update { it.copy(themeId = themeId) }
    }

    fun requestHover(line: Int? = null, column: Int? = null) {
        val activeDoc = _uiState.value.activeDocument ?: return
        val client = activeLspClient
        if (client == null || !client.isRunning) {
            _uiState.update { it.copy(statusMessage = "Language server unavailable for ${activeDoc.language.name}") }
            return
        }
        val targetLine = line ?: _uiState.value.cursorPosition.line
        val targetCol = column ?: _uiState.value.cursorPosition.column
        viewModelScope.launch(dispatchers.default) {
            when (val result = client.requestHover(activeDoc.uri, targetLine, targetCol)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    _uiState.update { it.copy(hoverInfo = result.data) }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Hover failed: ${result.message}") }
                }
                else -> Unit
            }
        }
    }

    fun clearHover() {
        _uiState.update { it.copy(hoverInfo = null) }
    }

    fun requestDefinition(
        line: Int? = null,
        column: Int? = null,
        onLocations: (List<com.vibespace.core.language.model.DefinitionLocation>) -> Unit = {},
        onNavigateToFile: ((VirtualFile) -> Unit)? = null
    ) {
        val activeDoc = _uiState.value.activeDocument ?: return
        val client = activeLspClient
        if (client == null || !client.isRunning) {
            _uiState.update { it.copy(statusMessage = "Language server unavailable for ${activeDoc.language.name}") }
            return
        }
        val targetLine = line ?: _uiState.value.cursorPosition.line
        val targetCol = column ?: _uiState.value.cursorPosition.column
        viewModelScope.launch(dispatchers.default) {
            when (val result = client.requestDefinition(activeDoc.uri, targetLine, targetCol)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    _uiState.update { it.copy(definitionLocations = result.data) }
                    onLocations(result.data)
                    val target = result.data.firstOrNull()
                    if (target != null && target.uri != activeDoc.uri && onNavigateToFile != null) {
                        val targetName = target.uri.substringAfterLast('/')
                        val targetFile = VirtualFile(
                            uri = target.uri,
                            name = targetName,
                            nodeType = com.vibespace.core.workspace.model.FileNodeType.FILE,
                            parentUri = target.uri.substringBeforeLast('/')
                        )
                        onNavigateToFile(targetFile)
                    }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Definition lookup failed: ${result.message}") }
                }
                else -> Unit
            }
        }
    }

    fun formatDocument() {
        val activeDoc = _uiState.value.activeDocument ?: return
        val client = activeLspClient
        if (client == null || !client.isRunning) {
            _uiState.update { it.copy(statusMessage = "Language server unavailable for ${activeDoc.language.name}") }
            return
        }
        viewModelScope.launch(dispatchers.default) {
            when (val result = client.formatDocument(activeDoc.uri)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        onContentChanged(result.data)
                        _uiState.update { it.copy(statusMessage = "Document formatted") }
                    }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Formatting failed: ${result.message}") }
                }
                else -> Unit
            }
        }
    }
}
