package com.vibespace.core.editor.sora

import android.graphics.Color
import com.vibespace.core.editor.theme.EditorThemeDefinition
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Custom ColorScheme for Sora Editor supporting VS Code themes and presets.
 *
 * Note on superclass initialization order:
 * [EditorColorScheme] calls [applyDefault] from its constructor, which in turn calls
 * [setColor] -> [getColor] BEFORE Kotlin initializes subclass properties (`theme`, etc.).
 * Therefore, [getColor] and [applyDefault] must never assume `theme` is non-null during
 * superclass construction.
 */
class VibeEditorColorScheme(
    private val theme: EditorThemeDefinition = EditorThemeDefinition.VIBE_DARK
) : EditorColorScheme() {

    private var activeTheme: EditorThemeDefinition? = null
    private var cachedBgColor: Int = 0
    private var cachedGutterColor: Int = 0
    private var cachedLineNumberColor: Int = 0
    private var cachedCursorColor: Int = 0
    private var cachedCurrentLineColor: Int = 0
    private var cachedSelectionColor: Int = 0
    private var cachedTextColor: Int = 0
    private var cachedKeywordColor: Int = 0
    private var cachedLiteralColor: Int = 0
    private var cachedCommentColor: Int = 0
    private var cachedFunctionColor: Int = 0

    override fun applyDefault() {
        super.applyDefault()
        // Superclass constructor invokes applyDefault() before subclass properties are initialized.
        applyTheme(EditorThemeDefinition.VIBE_DARK)
    }

    init {
        // Subclass constructor properties are now initialized; apply the requested preset/custom theme.
        applyTheme(theme)
    }

    @Suppress("USELESS_ELVIS")
    private fun resolveTheme(): EditorThemeDefinition {
        return activeTheme ?: (theme as EditorThemeDefinition?) ?: EditorThemeDefinition.VIBE_DARK
    }

    override fun getColor(type: Int): Int {
        val color = super.getColor(type)
        val currentTheme = resolveTheme()
        val bgColor = if (cachedBgColor != 0) cachedBgColor else parseSafe(currentTheme.backgroundColor, "#0F1115")

        if (type == WHOLE_BACKGROUND) {
            return if (color != 0) color else bgColor
        }
        if (type == LINE_NUMBER_BACKGROUND || type == LINE_DIVIDER) {
            return if (color != 0) color else if (cachedGutterColor != 0) cachedGutterColor else parseSafe(currentTheme.gutterColor, "#12141A")
        }
        if (type == CURRENT_LINE) {
            return if (color != 0) color else if (cachedCurrentLineColor != 0) cachedCurrentLineColor else parseSafe(currentTheme.currentLineColor, "#181B22")
        }
        if (type == SELECTED_TEXT_BACKGROUND) {
            return if (color != 0) color else if (cachedSelectionColor != 0) cachedSelectionColor else parseSafe(currentTheme.selectionColor, "#264F78")
        }

        // TextMate dynamic token indices (255 + TextMate color table index)
        if (type >= 255) {
            val tmTheme = runCatching { ThemeRegistry.getInstance().currentThemeModel?.theme }.getOrNull()
            if (tmTheme != null) {
                val colorHex = runCatching { tmTheme.getColor(type - 255) }.getOrNull()
                if (colorHex != null && !"@default".equals(colorHex, ignoreCase = true)) {
                    val parsed = parseColorOrNull(colorHex)
                    if (parsed != null && parsed != 0) {
                        // When a token's background color in TextMate equals the editor background,
                        // return 0 (transparent) so Sora Editor does not draw an opaque box over the line.
                        if (parsed == bgColor) {
                            return 0
                        }
                        super.colors.put(type, parsed)
                        return parsed
                    }
                }
            }
            if (color != 0) {
                return if (color == bgColor) 0 else color
            }
            return if (cachedTextColor != 0) cachedTextColor else parseSafe(currentTheme.textColor, "#F3F4F6")
        }

        // Standard Sora Editor color slots (type < 255)
        if (color != 0) {
            return color
        }
        return when (type) {
            LINE_NUMBER -> if (cachedLineNumberColor != 0) cachedLineNumberColor else parseSafe(currentTheme.lineNumberColor, "#5A5A5A")
            LINE_NUMBER_CURRENT, SELECTION_INSERT, SELECTION_HANDLE ->
                if (cachedCursorColor != 0) cachedCursorColor else parseSafe(currentTheme.cursorColor, "#00E5FF")
            TEXT_NORMAL, OPERATOR, IDENTIFIER_NAME ->
                if (cachedTextColor != 0) cachedTextColor else parseSafe(currentTheme.textColor, "#F3F4F6")
            KEYWORD -> if (cachedKeywordColor != 0) cachedKeywordColor else parseSafe(currentTheme.keywordColor, "#FF7B72")
            LITERAL -> if (cachedLiteralColor != 0) cachedLiteralColor else parseSafe(currentTheme.literalColor, "#A5D6FF")
            COMMENT -> if (cachedCommentColor != 0) cachedCommentColor else parseSafe(currentTheme.commentColor, "#8B949E")
            FUNCTION_NAME -> if (cachedFunctionColor != 0) cachedFunctionColor else parseSafe(currentTheme.functionColor, "#D2A8FF")
            // Leave intentionally transparent slots (such as UNDERLINE = 0) as 0
            else -> 0
        }
    }

    private fun parseSafe(colorHex: String?, fallback: String): Int {
        if (!colorHex.isNullOrBlank()) {
            parseColorOrNull(colorHex)?.let { if (it != 0) return it }
        }
        return parseColorOrNull(fallback) ?: 0xFF0F1115.toInt()
    }

    private fun parseColorOrNull(colorHex: String): Int? {
        val trimmed = colorHex.trim()
        if (!trimmed.startsWith("#")) return null
        // Try Android's Color.parseColor first, then pure Kotlin hex fallback for JVM unit tests
        runCatching {
            val c = Color.parseColor(trimmed)
            if (c != 0) return c
        }
        val hex = trimmed.removePrefix("#")
        return try {
            when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun putThemeColor(type: Int, value: Int) {
        super.colors.put(type, value)
        setColor(type, value)
    }

    private fun applyTheme(target: EditorThemeDefinition) {
        activeTheme = target
        cachedBgColor = parseSafe(target.backgroundColor, "#0F1115")
        cachedGutterColor = parseSafe(target.gutterColor, "#12141A")
        cachedLineNumberColor = parseSafe(target.lineNumberColor, "#5A5A5A")
        cachedCursorColor = parseSafe(target.cursorColor, "#00E5FF")
        cachedCurrentLineColor = parseSafe(target.currentLineColor, "#181B22")
        cachedSelectionColor = parseSafe(target.selectionColor, "#264F78")
        cachedTextColor = parseSafe(target.textColor, "#F3F4F6")
        cachedKeywordColor = parseSafe(target.keywordColor, "#FF7B72")
        cachedLiteralColor = parseSafe(target.literalColor, "#A5D6FF")
        cachedCommentColor = parseSafe(target.commentColor, "#8B949E")
        cachedFunctionColor = parseSafe(target.functionColor, "#D2A8FF")

        // Background & Gutter
        putThemeColor(WHOLE_BACKGROUND, cachedBgColor)
        putThemeColor(LINE_NUMBER_BACKGROUND, cachedGutterColor)
        putThemeColor(LINE_NUMBER, cachedLineNumberColor)
        putThemeColor(LINE_NUMBER_CURRENT, cachedCursorColor)
        putThemeColor(LINE_DIVIDER, cachedGutterColor)

        // Cursor & Current Line
        putThemeColor(CURRENT_LINE, cachedCurrentLineColor)
        putThemeColor(SELECTION_INSERT, cachedCursorColor)
        putThemeColor(SELECTION_HANDLE, cachedCursorColor)
        putThemeColor(SELECTED_TEXT_BACKGROUND, cachedSelectionColor)

        // Syntax Tokens
        putThemeColor(TEXT_NORMAL, cachedTextColor)
        putThemeColor(KEYWORD, cachedKeywordColor)
        putThemeColor(LITERAL, cachedLiteralColor)
        putThemeColor(COMMENT, cachedCommentColor)
        putThemeColor(OPERATOR, cachedTextColor)
        putThemeColor(IDENTIFIER_NAME, cachedTextColor)
        putThemeColor(FUNCTION_NAME, cachedFunctionColor)
    }
}
