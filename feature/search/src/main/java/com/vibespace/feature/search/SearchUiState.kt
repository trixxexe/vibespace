package com.vibespace.feature.search

data class SearchUiState(
    val query: String = "",
    val replacement: String = "",
    val matchCase: Boolean = false,
    val matchCount: Int = 0,
    val activeMatchIndex: Int = 0,
    val isReplaceMode: Boolean = false
)
