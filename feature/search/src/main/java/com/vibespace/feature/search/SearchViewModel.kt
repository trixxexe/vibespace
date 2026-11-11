package com.vibespace.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.engine.EditorEngine
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val editorEngine: EditorEngine,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val workspaceManager: WorkspaceManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var currentMatches = listOf<IntRange>()
    private var workspaceSearchJob: Job? = null
    private var workspaceSearchGeneration = 0L

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, searchError = null) }
        if (_uiState.value.scope == SearchScope.WORKSPACE) searchWorkspace() else performSearch()
    }

    fun onReplacementChange(newReplacement: String) {
        _uiState.update { it.copy(replacement = newReplacement) }
    }

    fun toggleMatchCase() {
        _uiState.update { it.copy(matchCase = !it.matchCase) }
        if (_uiState.value.scope == SearchScope.WORKSPACE) searchWorkspace() else performSearch()
    }

    fun setScope(scope: SearchScope) {
        _uiState.update { it.copy(scope = scope, searchError = null, isSearchingWorkspace = false) }
        if (scope == SearchScope.WORKSPACE) searchWorkspace() else performSearch()
    }

    fun toggleRegex() {
        _uiState.update { it.copy(useRegex = !it.useRegex, searchError = null) }
        if (_uiState.value.scope == SearchScope.WORKSPACE) searchWorkspace() else performSearch()
    }

    fun toggleReplaceMode() {
        if (_uiState.value.scope == SearchScope.FILE) {
            _uiState.update { it.copy(isReplaceMode = !it.isReplaceMode) }
        }
    }

    fun performSearch() {
        val state = _uiState.value
        val doc = editorEngine.activeDocument.value
        if (doc == null || state.query.isEmpty()) {
            currentMatches = emptyList()
            _uiState.update { it.copy(matchCount = 0, activeMatchIndex = 0) }
            return
        }

        currentMatches = if (state.useRegex) {
            try {
                val flags = if (state.matchCase) 0 else Pattern.CASE_INSENSITIVE
                val matcher = Pattern.compile(state.query, flags).matcher(doc.content)
                buildList {
                    while (matcher.find()) add(matcher.start() until matcher.end())
                }
            } catch (invalid: PatternSyntaxException) {
                _uiState.update { it.copy(matchCount = 0, activeMatchIndex = 0, searchError = "Invalid regular expression: ${invalid.message}") }
                currentMatches = emptyList()
                return
            }
        } else editorEngine.find(doc.documentId, state.query, state.matchCase)
        val first = currentMatches.firstOrNull()
        _uiState.update {
            it.copy(
                matchCount = currentMatches.size,
                activeMatchIndex = if (currentMatches.isNotEmpty()) 1 else 0
            )
        }
        first?.let { moveCursorTo(doc.documentId, it.first, doc.content) }
    }

    fun findNext() {
        if (currentMatches.isEmpty()) return
        val current = _uiState.value.activeMatchIndex
        val next = if (current >= currentMatches.size) 1 else current + 1
        _uiState.update { it.copy(activeMatchIndex = next) }
        val doc = editorEngine.activeDocument.value ?: return
        currentMatches.getOrNull(next - 1)?.let { moveCursorTo(doc.documentId, it.first, doc.content) }
    }

    fun findPrevious() {
        if (currentMatches.isEmpty()) return
        val current = _uiState.value.activeMatchIndex
        val previous = if (current <= 1) currentMatches.size else current - 1
        _uiState.update { it.copy(activeMatchIndex = previous) }
        val doc = editorEngine.activeDocument.value ?: return
        currentMatches.getOrNull(previous - 1)?.let { moveCursorTo(doc.documentId, it.first, doc.content) }
    }

    private fun moveCursorTo(documentId: String, offset: Int, content: String) {
        val safeOffset = offset.coerceIn(0, content.length)
        val prefix = content.substring(0, safeOffset)
        val line = prefix.count { it == '\n' } + 1
        val column = prefix.length - (prefix.lastIndexOf('\n') + 1) + 1
        viewModelScope.launch(dispatchers.default) {
            editorEngine.updateCursorPosition(documentId, CursorPosition(line, column, safeOffset))
        }
    }

    private fun searchWorkspace() {
        workspaceSearchJob?.cancel()
        val generation = ++workspaceSearchGeneration
        val searchState = _uiState.value
        val query = searchState.query
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    workspaceMatches = emptyList(), matchCount = 0,
                    isSearchingWorkspace = false, searchError = null,
                    skippedFileCount = 0, resultsTruncated = false
                )
            }
            return
        }
        val manager = workspaceManager
        if (manager == null) {
            _uiState.update { it.copy(isSearchingWorkspace = false, searchError = "Workspace search is unavailable.") }
            return
        }

        workspaceSearchJob = viewModelScope.launch(dispatchers.io) {
            delay(180)
            val options = searchState
            val projectId = manager.currentProject.value?.id
            val pattern = try {
                val expression = if (options.useRegex) query else Pattern.quote(query)
                Pattern.compile(expression, if (options.matchCase) 0 else Pattern.CASE_INSENSITIVE)
            } catch (invalid: PatternSyntaxException) {
                _uiState.update {
                    it.copy(
                        workspaceMatches = emptyList(), matchCount = 0,
                        isSearchingWorkspace = false,
                        searchError = "Invalid regular expression: ${invalid.message}"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isSearchingWorkspace = true, searchError = null,
                    workspaceMatches = emptyList(), matchCount = 0, activeMatchIndex = 0,
                    skippedFileCount = 0,
                    resultsTruncated = false
                )
            }
            val allFiles = when (val result = manager.getAllProjectFiles()) {
                is VibeResult.Success -> result.data
                is VibeResult.Error -> {
                    _uiState.update { it.copy(isSearchingWorkspace = false, searchError = result.message) }
                    return@launch
                }
                else -> {
                    _uiState.update { it.copy(isSearchingWorkspace = false, searchError = "Could not list workspace files.") }
                    return@launch
                }
            }
            if (!isCurrentSearch(generation, options, projectId, manager)) return@launch

            val files = allFiles.filter { it.isFile }
            val parentByUri = allFiles.associateBy { it.uri }
            val matches = mutableListOf<WorkspaceSearchMatch>()
            var skipped = 0
            var bytesRead = 0L
            var truncated = false

            searchFiles@ for (file in files) {
                if (!isCurrentSearch(generation, options, projectId, manager)) return@launch
                if (matches.size >= MAX_MATCHES || bytesRead >= MAX_TOTAL_BYTES) {
                    truncated = true
                    break
                }
                if (file.sizeBytes > MAX_FILE_BYTES) {
                    skipped++
                    continue
                }
                when (val read = manager.readFile(file.uri)) {
                    is VibeResult.Success -> {
                        val content = read.data
                        bytesRead += content.length * 2L
                        if (content.contains('\u0000')) {
                            skipped++
                            continue
                        }
                        val relativePath = buildRelativePath(file, parentByUri)
                        for ((index, line) in content.lineSequence().withIndex()) {
                            if (!isCurrentSearch(generation, options, projectId, manager)) return@launch
                            val matcher = pattern.matcher(line)
                            while (matcher.find()) {
                                if (matches.size >= MAX_MATCHES) {
                                    truncated = true
                                    break@searchFiles
                                }
                                matches.add(
                                    WorkspaceSearchMatch(
                                        file = file,
                                        relativePath = relativePath,
                                        lineNumber = index + 1,
                                        column = matcher.start() + 1,
                                        lineText = line.trim().take(MAX_LINE_LENGTH)
                                    )
                                )
                            }
                        }
                    }
                    is VibeResult.Error -> skipped++
                    else -> skipped++
                }
            }
            if (isCurrentSearch(generation, options, projectId, manager)) {
                _uiState.update {
                    it.copy(
                        workspaceMatches = matches,
                        matchCount = matches.size,
                        isSearchingWorkspace = false,
                        skippedFileCount = skipped,
                        resultsTruncated = truncated
                    )
                }
            }
        }
    }

    private fun isCurrentSearch(
        generation: Long,
        requested: SearchUiState,
        projectId: String?,
        manager: WorkspaceManager
    ): Boolean {
        val current = _uiState.value
        return generation == workspaceSearchGeneration &&
            current.query == requested.query &&
            current.matchCase == requested.matchCase &&
            current.useRegex == requested.useRegex &&
            current.scope == SearchScope.WORKSPACE &&
            manager.currentProject.value?.id == projectId
    }

    private fun buildRelativePath(file: VirtualFile, filesByUri: Map<String, VirtualFile>): String {
        val segments = mutableListOf(file.name)
        var parentUri = file.parentUri
        val visited = mutableSetOf<String>()
        while (parentUri != null && visited.add(parentUri)) {
            val parent = filesByUri[parentUri] ?: break
            segments.add(parent.name)
            parentUri = parent.parentUri
        }
        return segments.asReversed().joinToString("/")
    }

    fun replaceAll() {
        val state = _uiState.value
        val doc = editorEngine.activeDocument.value ?: return
        if (state.scope != SearchScope.FILE || state.query.isEmpty()) return
        viewModelScope.launch(dispatchers.default) {
            if (state.useRegex) {
                val matcher = try {
                    Pattern.compile(state.query, if (state.matchCase) 0 else Pattern.CASE_INSENSITIVE).matcher(doc.content)
                } catch (invalid: PatternSyntaxException) {
                    _uiState.update { it.copy(searchError = "Invalid regular expression: ${invalid.message}") }
                    return@launch
                }
                val ranges = buildList {
                    while (matcher.find()) add(matcher.start() until matcher.end())
                }
                for (range in ranges.asReversed()) {
                    editorEngine.replace(doc.documentId, range, state.replacement)
                }
            } else {
                editorEngine.replaceAll(doc.documentId, state.query, state.replacement, state.matchCase)
            }
            performSearch()
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 1024L * 1024L
        const val MAX_TOTAL_BYTES = 24L * 1024L * 1024L
        const val MAX_MATCHES = 1000
        const val MAX_LINE_LENGTH = 500
    }
}
