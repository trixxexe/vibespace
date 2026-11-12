package com.vibespace.core.editor.sora

import android.content.Context
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.language.model.LanguageDefinition
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Manages TextMate grammars and supplies syntax highlighting engines to Sora Editor.
 */
object TextMateLanguageManager {

    @Volatile
    private var isInitialized = false

    fun isReady(): Boolean = isInitialized

    /**
     * Initializes the asset file resolver, default theme, and grammar registry from assets/textmate/languages.json.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            // Register asset resolver so Sora TextMate can load nested syntax definitions
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.applicationContext.assets)
            )

            // Load default theme first so any grammar/language creation always has a valid non-null ThemeModel
            loadThemeForDefinition(EditorThemeDefinition.VIBE_DARK)

            // Load master language mapping
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            isInitialized = true
        } catch (_: Throwable) {
            // Gracefully handle missing assets in test or headless environments
            isInitialized = false
        }
    }

    /**
     * Loads or switches the active TextMate theme in ThemeRegistry.
     * Uses explicit ThemeModel(IThemeSource, theme.id) so ThemeModel.name is never null
     * (preventing NullPointerException in ThemeRegistry.findThemeByThemeName on subsequent calls).
     */
    @Synchronized
    fun loadThemeForDefinition(theme: EditorThemeDefinition) {
        try {
            val json = createThemeJson(theme)
            val themeSource = IThemeSource.fromString(IThemeSource.ContentType.JSON, json)
            val themeModel = ThemeModel(themeSource, theme.id).apply {
                isDark = true
            }
            ThemeRegistry.getInstance().loadTheme(themeModel, true)
        } catch (_: Throwable) {
            // Non-critical; fallback to VibeEditorColorScheme defaults
        }
    }

    private fun createThemeJson(theme: EditorThemeDefinition): String {
        return """
        {
          "name": "${theme.id}",
          "type": "dark",
          "colors": {
            "editor.background": "${theme.backgroundColor}",
            "editor.foreground": "${theme.textColor}",
            "editorLineNumber.foreground": "${theme.lineNumberColor}",
            "editorLineNumber.activeForeground": "${theme.cursorColor}",
            "editorCursor.foreground": "${theme.cursorColor}",
            "editor.selectionBackground": "${theme.selectionColor}",
            "editor.lineHighlightBackground": "${theme.currentLineColor}"
          },
          "tokenColors": [
            {
              "settings": {
                "foreground": "${theme.textColor}"
              }
            },
            {
              "scope": ["keyword", "storage", "storage.type", "storage.modifier", "keyword.control"],
              "settings": { "foreground": "${theme.keywordColor}" }
            },
            {
              "scope": ["string", "string.quoted", "string.template"],
              "settings": { "foreground": "${theme.literalColor}" }
            },
            {
              "scope": ["constant.numeric", "constant.language"],
              "settings": { "foreground": "${theme.literalColor}" }
            },
            {
              "scope": ["comment", "punctuation.definition.comment"],
              "settings": { "foreground": "${theme.commentColor}" }
            },
            {
              "scope": ["entity.name.function", "support.function"],
              "settings": { "foreground": "${theme.functionColor}" }
            },
            {
              "scope": ["variable", "variable.other", "variable.parameter", "entity.name.variable"],
              "settings": { "foreground": "${theme.textColor}" }
            },
            {
              "scope": ["entity.name.type", "entity.name.class", "support.type", "support.class"],
              "settings": { "foreground": "${theme.literalColor}" }
            },
            {
              "scope": ["punctuation", "meta.brace"],
              "settings": { "foreground": "${theme.textColor}" }
            }
          ]
        }
        """.trimIndent()
    }

    /**
     * Returns a syntax highlighting Language instance for the specified language definition.
     * Falls back to EmptyLanguage if TextMate grammar is unavailable.
     */
    @Synchronized
    fun createLanguageForDefinition(definition: LanguageDefinition): Language {
        if (!isInitialized) return EmptyLanguage()
        val scopeName = getScopeNameForLanguage(definition) ?: return EmptyLanguage()

        return try {
            if (ThemeRegistry.getInstance().currentThemeModel?.theme == null) {
                loadThemeForDefinition(EditorThemeDefinition.VIBE_DARK)
            }
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
