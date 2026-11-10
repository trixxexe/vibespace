package com.vibespace.core.language

import com.vibespace.core.language.snippets.SnippetRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetRepositoryTest {

    @Test
    fun getSuggestions_returns_python_snippets_for_py_prefix() {
        val suggestions = SnippetRepository.getSuggestions("python", "pr")
        assertFalse(suggestions.isEmpty())
        assertTrue(suggestions.any { it.label == "print()" })
    }

    @Test
    fun getSuggestions_returns_js_snippets_for_js_prefix() {
        val suggestions = SnippetRepository.getSuggestions("javascript", "con")
        assertFalse(suggestions.isEmpty())
        assertTrue(suggestions.any { it.label.contains("console") || it.label.contains("const") })
    }

    @Test
    fun getSnippetsForLanguage_returns_kotlin_snippets() {
        val snippets = SnippetRepository.getSnippetsForLanguage("kotlin")
        assertTrue(snippets.any { it.label == "fun" })
        assertTrue(snippets.any { it.label == "data class" })
    }
}
