package com.vibespace.core.editor

import com.vibespace.core.editor.sora.TextMateLanguageManager
import com.vibespace.core.language.model.LanguageDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TextMateLanguageManagerTest {

    @Test
    fun getScopeNameForLanguage_maps_known_languages_correctly() {
        assertEquals("source.python", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Python))
        assertEquals("source.js", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.JavaScript))
        assertEquals("source.kotlin", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Kotlin))
        assertEquals("text.html.basic", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Html))
        assertEquals("source.css", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Css))
    }

    @Test
    fun createLanguageForDefinition_handles_uninitialized_gracefully() {
        // Without Android assets context, should fallback to EmptyLanguage without crashing
        val language = TextMateLanguageManager.createLanguageForDefinition(LanguageDefinition.Python)
        assertNotNull(language)
    }
}
