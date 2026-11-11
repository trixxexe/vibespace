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
    private var completionJob: Job? = null
    private val openedLspDocuments = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** True only when a configured language server is currently running for the active file. */
    val hasRunningLanguageServer: Boolean
        get() {
            val activeLanguage = _uiState.value.activeDocument?.language?.id ?: return false
            return activeLspClient?.let { it.isRunning && it.languageId.equals(activeLanguage, ignoreCase = true) } == true
        }

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
                _uiState.update {
                    it.copy(
                        activeDocument = activeDoc,
                        cursorPosition = activeDoc?.cursorPosition ?: CursorPosition(1, 1, 0),
                        diagnostics = diags,
                        completions = emptyList()
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
                                if (openedLspDocuments.add(lspDocumentKey(client, mainPyUri))) {
                                    client.didOpen(mainPyUri, readResult.data)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun openFile(file: VirtualFile, lineNumber: Int? = null) {
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

            val openResult = editorEngine.openDocument(
                uri = file.uri,
                fileName = file.name,
                content = content,
                language = language
            )
            when (openResult) {
                is VibeResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Could not open ${file.name}: ${openResult.message}") }
                    return@launch
                }
                else -> Unit
            }
            if (lineNumber != null) {
                val opened = (openResult as? VibeResult.Success)?.data
                if (opened != null) {
                    val safeLine = lineNumber.coerceIn(1, content.count { it == '\n' } + 1)
                    val offset = content.lineSequence().take(safeLine - 1).sumOf { it.length + 1 }.coerceAtMost(content.length)
                    editorEngine.updateCursorPosition(opened.documentId, CursorPosition(safeLine, 1, offset))
                }
            }
            val client = lspClientProvider?.invoke(language.id)
            activeLspClient = client
            if (client != null) {
                if (!client.isRunning) {
                    val rootUri = workspaceManager.currentProject.value?.rootUri ?: file.parentUri ?: file.uri
                    client.start(rootUri)
                }
                if (client.isRunning && openedLspDocuments.add(lspDocumentKey(client, file.uri))) {
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
                if (client?.isRunning == true && openedLspDocuments.add(lspDocumentKey(client, doc.uri))) {
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
                    openedLspDocuments.remove(lspDocumentKey(client, doc.uri))
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
            val cursorOffset = _uiState.value.cursorPosition.offset.coerceIn(0, newContent.length)
            val prefix = identifierPrefixAt(newContent, cursorOffset)
            val suggestions = AutocompleteEngine.getSuggestions(newContent, prefix, activeDoc.language.id)
            _uiState.update { it.copy(diagnostics = diags, completions = suggestions) }
            val client = activeLspClient
            if (client != null && client.isRunning) {
                client.didChange(updatedDocument.uri, updatedDocument.content, updatedDocument.version)
            }
            requestLspCompletions(updatedDocument, prefix, suggestions)
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
        if (prefix.isBlank()) {
            _uiState.update { it.copy(completions = emptyList()) }
            return
        }
        viewModelScope.launch(dispatchers.default) {
            val suggestions = AutocompleteEngine.getSuggestions(activeDoc.content, prefix, activeDoc.language.id)
            _uiState.update { it.copy(completions = suggestions) }
            requestLspCompletions(activeDoc, prefix, suggestions)
        }
    }

    private fun requestLspCompletions(
        document: EditorDocument,
        prefix: String,
        fallback: List<CompletionItem>
    ) {
        val client = activeLspClient?.takeIf {
            it.isRunning && it.languageId.equals(document.language.id, ignoreCase = true)
        } ?: return
        if (prefix.isBlank()) return
        completionJob?.cancel()
        completionJob = viewModelScope.launch(dispatchers.default) {
            delay(LSP_COMPLETION_DEBOUNCE_MS)
            val cursor = _uiState.value.cursorPosition
            val result = client.requestCompletion(
                document.uri,
                cursor.line,
                cursor.column
            )
            val current = _uiState.value.activeDocument ?: return@launch
            if (current.documentId != document.documentId || current.content != document.content) return@launch
            val completions = when (result) {
                is VibeResult.Success -> (result.data + fallback).distinctBy { it.label }
                else -> fallback
            }
            _uiState.update { it.copy(completions = completions) }
        }
    }

    fun insertCompletion(item: CompletionItem) {
        val activeDoc = _uiState.value.activeDocument ?: return
        val currentContent = activeDoc.content
        val cursorOffset = activeDoc.cursorPosition.offset.coerceIn(0, currentContent.length)
        var wordStart = cursorOffset
        while (wordStart > 0 && isIdentifierCharacter(currentContent[wordStart - 1])) wordStart--
        val newContent = currentContent.substring(0, wordStart) + item.insertText + currentContent.substring(cursorOffset)
        onContentChanged(newContent)
    }

    fun onCursorPositionChanged(cursor: CursorPosition) {
        _uiState.update { it.copy(cursorPosition = cursor) }
        val activeDoc = _uiState.value.activeDocument ?: return
        viewModelScope.launch(dispatchers.default) {
            editorEngine.updateCursorPosition(activeDoc.documentId, cursor)
            updateCompletions(identifierPrefixAt(activeDoc.content, cursor.offset.coerceIn(0, activeDoc.content.length)))
        }
    }

    fun saveActiveDocument(onComplete: (Boolean) -> Unit = {}) {
        val activeDoc = _uiState.value.activeDocument ?: run {
            onComplete(false)
            return
        }
        autoSaveJob?.cancel()
        viewModelScope.launch(dispatchers.io) {
            val saved = saveDocument(activeDoc.documentId)
            kotlinx.coroutines.withContext(dispatchers.main) { onComplete(saved) }
        }
    }

    private suspend fun saveDocument(documentId: String): Boolean {
        val document = editorEngine.getDocument(documentId) ?: return false
        _uiState.update { it.copy(isSaving = true) }
        when (val writeResult = workspaceManager.writeFile(document.uri, document.content)) {
            is VibeResult.Success -> {
                val latestDocument = editorEngine.getDocument(documentId)
                if (latestDocument?.content == document.content) {
                    editorEngine.markSaved(documentId, contentAtSave = document.content)
                }
                val savedCurrentContent = editorEngine.getDocument(documentId)?.let {
                    it.content == document.content && !it.isDirty
                } == true
                gitManager?.refreshStatus()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = if (savedCurrentContent) "Saved ${document.fileName}" else "Saved ${document.fileName}; newer edits are still unsaved"
                    )
                }
                if (!savedCurrentContent && _uiState.value.autoSave) {
                    autoSaveJob?.cancel()
                    autoSaveJob = viewModelScope.launch(dispatchers.io) {
                        delay(AUTO_SAVE_DEBOUNCE_MS)
                        saveDocument(documentId)
                    }
                }
                return savedCurrentContent
            }
            is VibeResult.Error -> {
                _uiState.update { it.copy(isSaving = false, statusMessage = "Save failed: ${writeResult.message}") }
                return false
            }
            else -> {
                _uiState.update { it.copy(isSaving = false, statusMessage = "Save failed: no result returned") }
                return false
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
        const val LSP_COMPLETION_DEBOUNCE_MS = 120L
    }

    private fun lspDocumentKey(client: com.vibespace.core.language.lsp.LanguageServerClient, uri: String) =
        "${client.languageId}:$uri"

    private fun identifierPrefixAt(content: String, offset: Int): String {
        val end = offset.coerceIn(0, content.length)
        var start = end
        while (start > 0 && isIdentifierCharacter(content[start - 1])) start--
        return content.substring(start, end)
    }

    private fun isIdentifierCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()
}
