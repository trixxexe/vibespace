package com.vibespace.core.editor

import com.vibespace.core.editor.sora.TextMateLanguageManager
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.language.model.LanguageDefinition
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class TextMateLanguageManagerTest {

    @Test
    fun getScopeNameForLanguage_maps_known_languages_correctly() {
        assertEquals("source.python", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Python))
        assertEquals("source.js", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.JavaScript))
        assertEquals("source.ts", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.TypeScript))
        assertEquals("source.json", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Json))
        assertEquals("source.kotlin", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Kotlin))
        assertEquals("source.java", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Java))
        assertEquals("text.html.basic", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Html))
        assertEquals("source.css", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Css))
        assertEquals("text.html.markdown", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Markdown))
        assertEquals("source.shell", TextMateLanguageManager.getScopeNameForLanguage(LanguageDefinition.Shell))
    }

    @Test
    fun createLanguageForDefinition_handles_uninitialized_gracefully() {
        // Without Android assets context, should fallback to EmptyLanguage without crashing
        val language = TextMateLanguageManager.createLanguageForDefinition(LanguageDefinition.Python)
        assertNotNull(language)
    }

    @Test
    fun loadGrammars_and_repeated_loadThemeForDefinition_succeed_for_all_languages_and_themes() {
        val assetsDir = File("src/main/assets").let {
            if (it.exists()) it else File("core/editor/src/main/assets")
        }
        FileProviderRegistry.getInstance().addFileProvider(
            FileResolver { path ->
                val f = File(assetsDir, path)
                if (f.exists()) FileInputStream(f) else null
            }
        )

        // Repeatedly loading themes (including the same theme twice) must never throw NullPointerException
        for (preset in EditorThemeDefinition.BUILTIN_THEMES) {
            TextMateLanguageManager.loadThemeForDefinition(preset)
            TextMateLanguageManager.loadThemeForDefinition(preset)
            val currentModel = ThemeRegistry.getInstance().currentThemeModel
            assertNotNull("currentThemeModel should not be null for ${preset.id}", currentModel)
            assertEquals(preset.id, currentModel.name)
            assertNotNull("theme should not be null for ${preset.id}", currentModel.theme)
        }

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

        val allDefinitions = listOf(
            LanguageDefinition.Python,
            LanguageDefinition.JavaScript,
            LanguageDefinition.TypeScript,
            LanguageDefinition.Json,
            LanguageDefinition.Kotlin,
            LanguageDefinition.Java,
            LanguageDefinition.Html,
            LanguageDefinition.Css,
            LanguageDefinition.Markdown,
            LanguageDefinition.Shell
        )

        for (def in allDefinitions) {
            val scopeName = TextMateLanguageManager.getScopeNameForLanguage(def)
            assertNotNull("Scope name missing for ${def.id}", scopeName)
            val grammar = GrammarRegistry.getInstance().findGrammar(scopeName!!)
            assertNotNull("Grammar missing in languages.json for $scopeName", grammar)
            val lang = TextMateLanguage.create(scopeName, true)
            assertNotNull("TextMateLanguage.create failed for $scopeName", lang)
            val tokenized = grammar!!.tokenizeLine("def main():\n")
            assertNotNull(tokenized)
        }
    }
}
