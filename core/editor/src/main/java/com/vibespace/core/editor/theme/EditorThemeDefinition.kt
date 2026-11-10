package com.vibespace.core.editor.theme

/**
 * Definition of an editor color theme.
 * Compatible with VS Code themes and TextMate color rules.
 */
data class EditorThemeDefinition(
    val id: String,
    val name: String,
    val author: String = "Community",
    val backgroundColor: String = "#0F1115",
    val gutterColor: String = "#12141A",
    val currentLineColor: String = "#181B22",
    val selectionColor: String = "#264F78",
    val cursorColor: String = "#00E5FF",
    val textColor: String = "#F3F4F6",
    val lineNumberColor: String = "#5A5A5A",
    val keywordColor: String = "#FF7B72",
    val literalColor: String = "#A5D6FF",
    val commentColor: String = "#8B949E",
    val functionColor: String = "#D2A8FF"
) {
    companion object {
        val VIBE_DARK = EditorThemeDefinition(
            id = "vibe_dark",
            name = "Vibe Dark (Default)",
            author = "VibeSpace Team",
            backgroundColor = "#0F1115",
            gutterColor = "#12141A",
            currentLineColor = "#181B22",
            selectionColor = "#264F78",
            cursorColor = "#00E5FF",
            textColor = "#F3F4F6",
            lineNumberColor = "#5A5A5A",
            keywordColor = "#FF7B72",
            literalColor = "#A5D6FF",
            commentColor = "#8B949E",
            functionColor = "#D2A8FF"
        )

        val ONE_DARK_PRO = EditorThemeDefinition(
            id = "one_dark_pro",
            name = "One Dark Pro",
            author = "Binaryify",
            backgroundColor = "#282C34",
            gutterColor = "#21252B",
            currentLineColor = "#2C313C",
            selectionColor = "#3E4451",
            cursorColor = "#528BFF",
            textColor = "#ABB2BF",
            lineNumberColor = "#4B5263",
            keywordColor = "#C678DD",
            literalColor = "#98C379",
            commentColor = "#5C6370",
            functionColor = "#61AFEF"
        )

        val DRACULA = EditorThemeDefinition(
            id = "dracula",
            name = "Dracula",
            author = "Zeno Rocha",
            backgroundColor = "#282A36",
            gutterColor = "#21222C",
            currentLineColor = "#44475A",
            selectionColor = "#44475A",
            cursorColor = "#FF79C6",
            textColor = "#F8F8F2",
            lineNumberColor = "#6272A4",
            keywordColor = "#FF79C6",
            literalColor = "#F1FA8C",
            commentColor = "#6272A4",
            functionColor = "#50FA7B"
        )

        val MONOKAI_PRO = EditorThemeDefinition(
            id = "monokai_pro",
            name = "Monokai Pro",
            author = "Monokai",
            backgroundColor = "#2D2A2E",
            gutterColor = "#221F22",
            currentLineColor = "#3E3B3F",
            selectionColor = "#403E41",
            cursorColor = "#FFD866",
            textColor = "#FCFCFA",
            lineNumberColor = "#727072",
            keywordColor = "#FF6188",
            literalColor = "#A9DC76",
            commentColor = "#727072",
            functionColor = "#78DCE8"
        )

        val NORD = EditorThemeDefinition(
            id = "nord",
            name = "Nord",
            author = "Arctic Ice Studio",
            backgroundColor = "#2E3440",
            gutterColor = "#242933",
            currentLineColor = "#3B4252",
            selectionColor = "#434C5E",
            cursorColor = "#88C0D0",
            textColor = "#D8DEE9",
            lineNumberColor = "#4C566A",
            keywordColor = "#81A1C1",
            literalColor = "#A3BE8C",
            commentColor = "#616E88",
            functionColor = "#88C0D0"
        )

        val BUILTIN_THEMES = listOf(VIBE_DARK, ONE_DARK_PRO, DRACULA, MONOKAI_PRO, NORD)

        fun fromId(id: String): EditorThemeDefinition =
            BUILTIN_THEMES.find { it.id == id } ?: VIBE_DARK
    }
}
