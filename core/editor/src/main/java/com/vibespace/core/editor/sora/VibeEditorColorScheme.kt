package com.vibespace.core.editor.sora

import android.graphics.Color
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Custom Dark-First ColorScheme for Sora Editor matching VibeSpace Design System.
 */
class VibeEditorColorScheme : EditorColorScheme() {

    override fun applyDefault() {
        super.applyDefault()

        // Background & Gutter
        setColor(WHOLE_BACKGROUND, Color.parseColor("#0F1115"))
        setColor(LINE_NUMBER_BACKGROUND, Color.parseColor("#12141A"))
        setColor(LINE_NUMBER, Color.parseColor("#5A5A5A"))
        setColor(LINE_NUMBER_CURRENT, Color.parseColor("#00E5FF"))
        setColor(LINE_DIVIDER, Color.parseColor("#1E2128"))

        // Cursor & Current Line
        setColor(CURRENT_LINE, Color.parseColor("#181B22"))
        setColor(SELECTION_INSERT, Color.parseColor("#00E5FF"))
        setColor(SELECTION_HANDLE, Color.parseColor("#00E5FF"))
        setColor(SELECTED_TEXT_BACKGROUND, Color.parseColor("#264F78"))

        // Syntax Tokens
        setColor(TEXT_NORMAL, Color.parseColor("#F3F4F6"))
        setColor(KEYWORD, Color.parseColor("#FF7B72"))
        setColor(LITERAL, Color.parseColor("#A5D6FF"))
        setColor(COMMENT, Color.parseColor("#8B949E"))
        setColor(OPERATOR, Color.parseColor("#F3F4F6"))
        setColor(IDENTIFIER_NAME, Color.parseColor("#9CDCFE"))
        setColor(FUNCTION_NAME, Color.parseColor("#D2A8FF"))
    }
}
