package com.vibespace.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var autoSaveJob: Job? = null

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
            val content = when (val readResult = workspaceManager.readFile(file.uri)) {
                is VibeResult.Success -> readResult.data
                is VibeResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Could not open ${file.name}: ${readResult.message}") }
                    return@launch
                }
                else -> {
                    _uiState.update { it.copy(statusMessage = "Could not open ${file.name}: file contents are unavailable.") }
                    return@launch
                }
            }
            val language = languageDetector.detectByFileName(file.name)

            when (val openResult = editorEngine.openDocument(
                uri = file.uri,
                fileName = file.name,
                content = content,
                language = language
            )) {
                is VibeResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Could not open ${file.name}: ${openResult.message}") }
                    return@launch
                }
                else -> Unit
            }
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
                if (client?.isRunning == true) {
                    client.didOpen(doc.uri, doc.content)
                }
            }
        }
    }

    fun closeTab(documentId: String) {
        viewModelScope.launch(dispatchers.default) {
            val doc = editorEngine.documents.value.find { it.documentId == documentId }
            if (doc != null) {
                val client = lspClientProvider?.invoke(doc.language.id)
                if (client != null && client.isRunning) {
                    client.didClose(doc.uri)
                }
            }
            editorEngine.closeDocument(documentId)
        }
    }

    fun onContentChanged(newContent: String) {
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            val updatedDocument = when (val result = editorEngine.updateDocument(activeDoc.documentId, newContent)) {
                is VibeResult.Success -> result.data
                else -> return@launch
            }
            val diags = SyntaxDiagnosticsValidator.validate(newContent, activeDoc.language.id)
            val suggestions = AutocompleteEngine.getSuggestions(newContent, "", activeDoc.language.id)
            _uiState.update { it.copy(diagnostics = diags, completions = suggestions) }
            val client = activeLspClient
            if (client != null && client.isRunning) {
                client.didChange(updatedDocument.uri, updatedDocument.content, updatedDocument.version)
            }
            if (_uiState.value.autoSave) {
                autoSaveJob?.cancel()
                autoSaveJob = viewModelScope.launch(dispatchers.io) {
                    delay(AUTO_SAVE_DEBOUNCE_MS)
                    saveDocument(activeDoc.documentId)
                }
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
        autoSaveJob?.cancel()
        viewModelScope.launch(dispatchers.io) { saveDocument(activeDoc.documentId) }
    }

    private suspend fun saveDocument(documentId: String) {
        val document = editorEngine.getDocument(documentId) ?: return
        _uiState.update { it.copy(isSaving = true) }
        when (val writeResult = workspaceManager.writeFile(document.uri, document.content)) {
            is VibeResult.Success -> {
                val latestDocument = editorEngine.getDocument(documentId)
                if (latestDocument?.content == document.content) {
                    editorEngine.markSaved(documentId)
                }
                gitManager?.refreshStatus()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = if (latestDocument?.content == document.content) "Saved ${document.fileName}" else "Saved ${document.fileName}; newer edits are still unsaved"
                    )
                }
                if (latestDocument?.content != document.content && _uiState.value.autoSave) {
                    autoSaveJob?.cancel()
                    autoSaveJob = viewModelScope.launch(dispatchers.io) {
                        delay(AUTO_SAVE_DEBOUNCE_MS)
                        saveDocument(documentId)
                    }
                }
            }
            is VibeResult.Error -> {
                _uiState.update { it.copy(isSaving = false, statusMessage = "Save failed: ${writeResult.message}") }
            }
            else -> _uiState.update { it.copy(isSaving = false, statusMessage = "Save failed: no result returned") }
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

    fun updateEditorPreferences(
        fontSizeSp: Float,
        tabSize: Int,
        showLineNumbers: Boolean,
        wordWrap: Boolean,
        themeId: String,
        autoSave: Boolean
    ) {
        _uiState.update {
            it.copy(
                fontSizeSp = fontSizeSp,
                tabSize = tabSize,
                showLineNumbers = showLineNumbers,
                wordWrap = wordWrap,
                themeId = themeId,
                autoSave = autoSave
            )
        }
        if (!autoSave) {
            autoSaveJob?.cancel()
            autoSaveJob = null
        }
    }

    fun requestHover(line: Int? = null, column: Int? = null) {
        val activeDoc = _uiState.value.activeDocument ?: return
        val client = activeLspClient
        if (client == null || !client.isRunning) {
            _uiState.update { it.copy(statusMessage = "Language server unavailable for ${activeDoc.language.displayName}") }
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
            _uiState.update { it.copy(statusMessage = "Language server unavailable for ${activeDoc.language.displayName}") }
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
            _uiState.update { it.copy(statusMessage = "Language server unavailable for ${activeDoc.language.displayName}") }
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

    private companion object {
        const val AUTO_SAVE_DEBOUNCE_MS = 700L
    }
}
