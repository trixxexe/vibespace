package com.vibespace.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.editor.engine.EditorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val editorEngine: EditorEngine,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var currentMatches = listOf<IntRange>()

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        performSearch()
    }

    fun onReplacementChange(newReplacement: String) {
        _uiState.update { it.copy(replacement = newReplacement) }
    }

    fun toggleMatchCase() {
        _uiState.update { it.copy(matchCase = !it.matchCase) }
        performSearch()
    }

    fun toggleReplaceMode() {
        _uiState.update { it.copy(isReplaceMode = !it.isReplaceMode) }
    }

    fun performSearch() {
        val query = _uiState.value.query
        val doc = editorEngine.activeDocument.value ?: return
        if (query.isEmpty()) {
            currentMatches = emptyList()
            _uiState.update { it.copy(matchCount = 0, activeMatchIndex = 0) }
            return
        }

        currentMatches = editorEngine.find(doc.documentId, query, _uiState.value.matchCase)
        _uiState.update {
            it.copy(
                matchCount = currentMatches.size,
                activeMatchIndex = if (currentMatches.isNotEmpty()) 1 else 0
            )
        }
    }

    fun findNext() {
        if (currentMatches.isEmpty()) return
        val currentIdx = _uiState.value.activeMatchIndex
        val nextIdx = if (currentIdx >= currentMatches.size) 1 else currentIdx + 1
        _uiState.update { it.copy(activeMatchIndex = nextIdx) }
    }

    fun findPrevious() {
        if (currentMatches.isEmpty()) return
        val currentIdx = _uiState.value.activeMatchIndex
        val prevIdx = if (currentIdx <= 1) currentMatches.size else currentIdx - 1
        _uiState.update { it.copy(activeMatchIndex = prevIdx) }
    }

    fun replaceAll() {
        val query = _uiState.value.query
        val replacement = _uiState.value.replacement
        val doc = editorEngine.activeDocument.value ?: return
        if (query.isEmpty()) return

        viewModelScope.launch(dispatchers.default) {
            editorEngine.replaceAll(doc.documentId, query, replacement, _uiState.value.matchCase)
            performSearch()
        }
    }
}
