package com.vibespace.core.editor.extension

enum class ExtensionCategory {
    THEME,
    LANGUAGE,
    LINTER,
    FORMATTER,
    ICON_PACK,
    SNIPPET
}

data class ExtensionMetadata(
    val id: String,
    val name: String,
    val displayName: String,
    val publisher: String,
    val version: String,
    val description: String,
    val category: ExtensionCategory = ExtensionCategory.THEME,
    val iconUrl: String? = null,
    val downloadUrl: String? = null,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val isBuiltIn: Boolean = false,
    val downloadCount: Long = 0
)
