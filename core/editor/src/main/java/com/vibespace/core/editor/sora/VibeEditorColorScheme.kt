package com.vibespace.core.editor.sora

import android.graphics.Color
import com.vibespace.core.editor.theme.EditorThemeDefinition
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Custom ColorScheme for Sora Editor supporting VS Code themes and presets.
 */
class VibeEditorColorScheme(
    private val theme: EditorThemeDefinition = EditorThemeDefinition.VIBE_DARK
) : EditorColorScheme() {

    override fun applyDefault() {
        super.applyDefault()
        // EditorColorScheme invokes this overridable method from its constructor, before this
        // subclass's constructor properties have been initialized. Do not read `theme` here.
        applyTheme(EditorThemeDefinition.VIBE_DARK)
    }

    init {
        // Constructor properties are initialized by this point, so the requested preset/custom
        // theme can safely replace the temporary fallback applied during superclass construction.
        applyTheme(theme)
    }

    override fun getColor(type: Int): Int {
        val color = super.getColor(type)
        if (type == WHOLE_BACKGROUND || type == LINE_NUMBER_BACKGROUND || type == CURRENT_LINE || type == SELECTED_TEXT_BACKGROUND || type == LINE_DIVIDER) {
            return if (color != 0) color else parseSafe(theme.backgroundColor, "#0F1115")
        }

        val bgColor = getColor(WHOLE_BACKGROUND)

        // TextMate and dynamic token indices
        if (type >= 255) {
            if (color != 0 && isContrastValid(color, bgColor)) {
                return color
            }
            val tmTheme = runCatching { ThemeRegistry.getInstance().currentThemeModel?.theme }.getOrNull()
            if (tmTheme != null) {
                val colorHex = runCatching { tmTheme.getColor(type - 255) }.getOrNull()
                if (colorHex != null && !"@default".equals(colorHex, ignoreCase = true)) {
                    val parsed = runCatching { Color.parseColor(colorHex) }.getOrNull()
                    if (parsed != null && parsed != 0 && isContrastValid(parsed, bgColor)) {
                        super.colors.put(type, parsed)
                        return parsed
                    }
                }
            }
            return getColor(TEXT_NORMAL)
        }

        // Standard token indices (type < 255)
        if (color == 0 || !isContrastValid(color, bgColor)) {
            return when (type) {
                LINE_NUMBER -> parseSafe(theme.lineNumberColor, "#5A5A5A")
                LINE_NUMBER_CURRENT -> parseSafe(theme.cursorColor, "#00E5FF")
                KEYWORD -> parseSafe(theme.keywordColor, "#FF7B72")
                LITERAL -> parseSafe(theme.literalColor, "#A5D6FF")
                COMMENT -> parseSafe(theme.commentColor, "#8B949E")
                OPERATOR -> parseSafe(theme.textColor, "#F3F4F6")
                IDENTIFIER_NAME -> parseSafe(theme.textColor, "#9CDCFE")
                FUNCTION_NAME -> parseSafe(theme.functionColor, "#D2A8FF")
                else -> parseSafe(theme.textColor, "#F3F4F6")
            }
        }
        return color
    }

    private fun isContrastValid(textColor: Int, bgColor: Int): Boolean {
        if (Color.alpha(textColor) < 80) return false
        val textLum = 0.299 * Color.red(textColor) + 0.587 * Color.green(textColor) + 0.114 * Color.blue(textColor)
        val bgLum = 0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)
        // If background is dark and text is dark, poor contrast
        if (bgLum < 128 && textLum < 45) return false
        // If background is light and text is light, poor contrast
        if (bgLum >= 128 && textLum > 210) return false
        return true
    }

    private fun parseSafe(colorHex: String, fallback: String): Int = try {
        Color.parseColor(colorHex)
    } catch (_: Throwable) {
        Color.parseColor(fallback)
    }

    private fun applyTheme(theme: EditorThemeDefinition) {
        // Background & Gutter
        setColor(WHOLE_BACKGROUND, parseSafe(theme.backgroundColor, "#0F1115"))
        setColor(LINE_NUMBER_BACKGROUND, parseSafe(theme.gutterColor, "#12141A"))
        setColor(LINE_NUMBER, parseSafe(theme.lineNumberColor, "#5A5A5A"))
        setColor(LINE_NUMBER_CURRENT, parseSafe(theme.cursorColor, "#00E5FF"))
        setColor(LINE_DIVIDER, parseSafe(theme.gutterColor, "#1E2128"))

        // Cursor & Current Line
        setColor(CURRENT_LINE, parseSafe(theme.currentLineColor, "#181B22"))
        setColor(SELECTION_INSERT, parseSafe(theme.cursorColor, "#00E5FF"))
        setColor(SELECTION_HANDLE, parseSafe(theme.cursorColor, "#00E5FF"))
        setColor(SELECTED_TEXT_BACKGROUND, parseSafe(theme.selectionColor, "#264F78"))

        // Syntax Tokens
        setColor(TEXT_NORMAL, parseSafe(theme.textColor, "#F3F4F6"))
        setColor(KEYWORD, parseSafe(theme.keywordColor, "#FF7B72"))
        setColor(LITERAL, parseSafe(theme.literalColor, "#A5D6FF"))
        setColor(COMMENT, parseSafe(theme.commentColor, "#8B949E"))
        setColor(OPERATOR, parseSafe(theme.textColor, "#F3F4F6"))
        setColor(IDENTIFIER_NAME, parseSafe(theme.textColor, "#9CDCFE"))
        setColor(FUNCTION_NAME, parseSafe(theme.functionColor, "#D2A8FF"))
    }
}
