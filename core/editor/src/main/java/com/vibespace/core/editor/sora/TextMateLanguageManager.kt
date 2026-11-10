package com.vibespace.core.editor.sora

import android.content.Context
import com.vibespace.core.language.model.LanguageDefinition
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver

/**
 * Manages TextMate grammars and supplies syntax highlighting engines to Sora Editor.
 */
object TextMateLanguageManager {

    private var isInitialized = false

    /**
     * Initializes the asset file resolver and loads grammar registry from assets/textmate/languages.json.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            // Register asset resolver so Sora TextMate can load nested syntax definitions
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.applicationContext.assets)
            )

            // Load master language mapping
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            isInitialized = true
        } catch (_: Throwable) {
            // Gracefully handle missing assets in test or headless environments
            isInitialized = false
        }
    }

    /**
     * Returns a syntax highlighting Language instance for the specified language definition.
     * Falls back to EmptyLanguage if TextMate grammar is unavailable.
     */
    fun createLanguageForDefinition(definition: LanguageDefinition): Language {
        val scopeName = getScopeNameForLanguage(definition) ?: return EmptyLanguage()

        return try {
            TextMateLanguage.create(scopeName, true)
        } catch (_: Throwable) {
            EmptyLanguage()
        }
    }

    /**
     * Maps VibeSpace LanguageDefinition to standard TextMate scope name.
     */
    fun getScopeNameForLanguage(definition: LanguageDefinition): String? {
        return when (definition.id.lowercase()) {
            "python" -> "source.python"
            "javascript", "js" -> "source.js"
            "typescript", "ts" -> "source.ts"
            "json" -> "source.json"
            "kotlin", "kt" -> "source.kotlin"
            "java" -> "source.java"
            "html" -> "text.html.basic"
            "css" -> "source.css"
            "markdown", "md" -> "text.html.markdown"
            "shell", "sh", "bash" -> "source.shell"
            else -> null
        }
    }
}
