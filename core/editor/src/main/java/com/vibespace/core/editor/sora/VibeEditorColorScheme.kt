package com.vibespace.core.editor.sora

import android.graphics.Color
import com.vibespace.core.editor.theme.EditorThemeDefinition
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Custom ColorScheme for Sora Editor supporting VS Code themes and presets.
 */
class VibeEditorColorScheme(
    private val theme: EditorThemeDefinition = EditorThemeDefinition.VIBE_DARK
) : EditorColorScheme() {

    override fun applyDefault() {
        super.applyDefault()

        fun parseSafe(colorHex: String, fallback: String): Int {
            return try {
                Color.parseColor(colorHex)
            } catch (_: Throwable) {
                Color.parseColor(fallback)
            }
        }

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
