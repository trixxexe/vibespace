package com.vibespace.core.editor.theme

/**
 * Definition of an editor color theme.
 * Compatible with VS Code themes and TextMate color rules.
 */
data class EditorThemeDefinition(
    val id: String,
    val name: String,
    val author: String = "Community",
    val isDark: Boolean = true,
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
            author = "StarDevs",
            isDark = true,
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

        val VSCODE_BLUE = EditorThemeDefinition(
            id = "vscode_blue",
            name = "VS Code Blue (Tomorrow Night Blue)",
            author = "Microsoft / StarDevs",
            isDark = true,
            backgroundColor = "#002451",
            gutterColor = "#001C40",
            currentLineColor = "#00346E",
            selectionColor = "#003F8E",
            cursorColor = "#FFFFFF",
            textColor = "#FFFFFF",
            lineNumberColor = "#7285B7",
            keywordColor = "#EBBBFF",
            literalColor = "#D1F1A9",
            commentColor = "#7285B7",
            functionColor = "#BBDAFF"
        )

        val VIBE_LIGHT = EditorThemeDefinition(
            id = "vibe_light",
            name = "Vibe Light (VS Code Light+)",
            author = "StarDevs",
            isDark = false,
            backgroundColor = "#FFFFFF",
            gutterColor = "#F3F4F6",
            currentLineColor = "#EBF5FF",
            selectionColor = "#ADD6FF",
            cursorColor = "#007ACC",
            textColor = "#111827",
            lineNumberColor = "#6B7280",
            keywordColor = "#CF222E",
            literalColor = "#0A3069",
            commentColor = "#57606A",
            functionColor = "#8250DF"
        )

        val ONE_DARK_PRO = EditorThemeDefinition(
            id = "one_dark_pro",
            name = "One Dark Pro",
            author = "Binaryify",
            isDark = true,
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
            isDark = true,
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
            isDark = true,
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
            isDark = true,
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

        val GITHUB_DARK = EditorThemeDefinition(
            id = "github_dark",
            name = "GitHub Dark Default",
            author = "GitHub",
            isDark = true,
            backgroundColor = "#0D1117",
            gutterColor = "#090D11",
            currentLineColor = "#161B22",
            selectionColor = "#1F6FEB",
            cursorColor = "#58A6FF",
            textColor = "#C9D1D9",
            lineNumberColor = "#484F58",
            keywordColor = "#FF7B72",
            literalColor = "#A5D6FF",
            commentColor = "#8B949E",
            functionColor = "#D2A8FF"
        )

        val TOKYO_NIGHT = EditorThemeDefinition(
            id = "tokyo_night",
            name = "Tokyo Night",
            author = "Enkia",
            isDark = true,
            backgroundColor = "#1A1B26",
            gutterColor = "#16161E",
            currentLineColor = "#292E42",
            selectionColor = "#33467C",
            cursorColor = "#7AA2F7",
            textColor = "#C0CAF5",
            lineNumberColor = "#565F89",
            keywordColor = "#BB9AF7",
            literalColor = "#9ECE6A",
            commentColor = "#565F89",
            functionColor = "#7AA2F7"
        )

        val CATPPUCCIN_MOCHA = EditorThemeDefinition(
            id = "catppuccin_mocha",
            name = "Catppuccin Mocha",
            author = "Catppuccin",
            isDark = true,
            backgroundColor = "#1E1E2E",
            gutterColor = "#181825",
            currentLineColor = "#313244",
            selectionColor = "#45475A",
            cursorColor = "#F5E0DC",
            textColor = "#CDD6F4",
            lineNumberColor = "#6C7086",
            keywordColor = "#CBA6F7",
            literalColor = "#A6E3A1",
            commentColor = "#6C7086",
            functionColor = "#89B4FA"
        )

        val SOLARIZED_LIGHT = EditorThemeDefinition(
            id = "solarized_light",
            name = "Solarized Light",
            author = "Ethan Schoonover",
            isDark = false,
            backgroundColor = "#FDF6E3",
            gutterColor = "#EEE8D5",
            currentLineColor = "#EEE8D5",
            selectionColor = "#D3CBB7",
            cursorColor = "#268BD2",
            textColor = "#586E75",
            lineNumberColor = "#93A1A1",
            keywordColor = "#859900",
            literalColor = "#2AA198",
            commentColor = "#93A1A1",
            functionColor = "#268BD2"
        )

        val BUILTIN_THEMES = listOf(
            VIBE_DARK,
            VSCODE_BLUE,
            VIBE_LIGHT,
            ONE_DARK_PRO,
            DRACULA,
            MONOKAI_PRO,
            NORD,
            GITHUB_DARK,
            TOKYO_NIGHT,
            CATPPUCCIN_MOCHA,
            SOLARIZED_LIGHT
        )

        fun fromId(id: String): EditorThemeDefinition =
            CustomEditorThemeRegistry.find(id) ?: BUILTIN_THEMES.find { it.id == id } ?: VIBE_DARK
    }
}
