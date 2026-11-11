package com.vibespace.core.editor.theme

import java.util.concurrent.ConcurrentHashMap

/** Runtime registry for declarative themes imported from supported VSIX packages. */
internal object CustomEditorThemeRegistry {
    private val themes = ConcurrentHashMap<String, EditorThemeDefinition>()
    private val extensionThemeIds = ConcurrentHashMap<String, String>()

    fun register(theme: EditorThemeDefinition, extensionId: String) {
        if (theme.id !in BUILT_IN_IDS) themes[theme.id] = theme
        themes[extensionId] = theme
        extensionThemeIds[extensionId] = theme.id
    }

    fun removeExtension(extensionId: String) {
        val themeId = extensionThemeIds.remove(extensionId) ?: return
        themes.remove(extensionId)
        if (themeId !in BUILT_IN_IDS && extensionThemeIds.values.none { it == themeId }) themes.remove(themeId)
    }

    fun find(themeId: String): EditorThemeDefinition? = themes[themeId]

    private val BUILT_IN_IDS = setOf("vibe_dark", "one_dark_pro", "dracula", "monokai_pro", "nord")
}
