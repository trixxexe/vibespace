package com.vibespace.core.editor.model

data class CursorPosition(
    val line: Int = 1,
    val column: Int = 1,
    val offset: Int = 0
)

data class TextSelection(
    val start: CursorPosition,
    val end: CursorPosition
) {
    val isCollapsed: Boolean get() = start == end
}

data class TextSpan(
    val startOffset: Int,
    val endOffset: Int,
    val styleKey: String
)
