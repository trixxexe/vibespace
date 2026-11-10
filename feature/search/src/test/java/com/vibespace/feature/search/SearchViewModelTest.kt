package com.vibespace.feature.search

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.engine.DefaultEditorEngine
import com.vibespace.core.language.model.LanguageDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelTest {

    private val editorEngine = DefaultEditorEngine(dispatchers = TestDispatcherProvider())

    @Test
    fun onQueryChange_finds_matches_and_updates_count() = runTest {
        editorEngine.openDocument("uri", "test.py", "val a = 1\nval b = 2\nval c = 3", LanguageDefinition.Python)

        val viewModel = SearchViewModel(
            editorEngine = editorEngine,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.onQueryChange("val")
        assertEquals(3, viewModel.uiState.value.matchCount)
        assertEquals(1, viewModel.uiState.value.activeMatchIndex)

        viewModel.findNext()
        assertEquals(2, viewModel.uiState.value.activeMatchIndex)
    }

    @Test
    fun replaceAll_replaces_all_occurrences() = runTest {
        editorEngine.openDocument("uri", "test.txt", "cat dog cat", LanguageDefinition.PlainText)

        val viewModel = SearchViewModel(
            editorEngine = editorEngine,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.onQueryChange("cat")
        viewModel.onReplacementChange("fox")
        viewModel.replaceAll()

        assertEquals("fox dog fox", editorEngine.activeDocument.value!!.content)
    }
}
