package com.vibespace.core.terminal.ansi

/**
 * ANSI / VT100 Text Style Model.
 * Represents terminal text styling attributes parsed from escape sequences.
 */
data class AnsiStyle(
    val foregroundColor: Long? = null, // 0xAARRGGBB
    val backgroundColor: Long? = null, // 0xAARRGGBB
    val isBold: Boolean = false,
    val isDim: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isInverse: Boolean = false,
    val isStrikethrough: Boolean = false
)

/**
 * A contiguous segment of text with unified ANSI styling.
 */
data class AnsiSpan(
    val text: String,
    val style: AnsiStyle = AnsiStyle()
)

/**
 * ANSI/VT100 Escape Sequence Parser.
 * Supports SGR (Select Graphic Rendition) colors (16 standard, 256-color palette, 24-bit truecolor RGB),
 * font styles (bold, dim, italic, underline, strikethrough, inverse), and control command filtering.
 */
object AnsiParser {

    private val STANDARD_COLORS = longArrayOf(
        0xFF1E1E1EL, // 0: Black
        0xFFE06C75L, // 1: Red
        0xFF98C379L, // 2: Green
        0xFFE5C07BL, // 3: Yellow
        0xFF61AFEFL, // 4: Blue
        0xFFC678DDL, // 5: Magenta
        0xFF56B6C2L, // 6: Cyan
        0xFFABB2BFL  // 7: White
    )

    private val BRIGHT_COLORS = longArrayOf(
        0xFF5C6370L, // 0: Bright Black (Gray)
        0xFFE88388L, // 1: Bright Red
        0xFFA8CC8CL, // 2: Bright Green
        0xFFEBCA89L, // 3: Bright Yellow
        0xFF73B8F1L, // 4: Bright Blue
        0xFFCF8CE2L, // 5: Bright Magenta
        0xFF68C2CDL, // 6: Bright Cyan
        0xFFFFFFFFL  // 7: Bright White
    )

    // Regex to match escape sequences: \u001B[ ... <letter> or \u001B] ... \u0007
    private val ANSI_REGEX = Regex("\u001B\\[([0-9;]*)([A-Za-z])|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")

    /**
     * Parses raw terminal output into styled spans.
     */
    fun parse(rawText: String): List<AnsiSpan> {
        if (rawText.isEmpty()) return emptyList()

        // Handle carriage return without newline (e.g. progress bars \r)
        val processedText = normalizeCarriageReturns(rawText)

        val result = mutableListOf<AnsiSpan>()
        var currentStyle = AnsiStyle()
        var lastIndex = 0

        val matches = ANSI_REGEX.findAll(processedText).toList()

        for (match in matches) {
            val range = match.range
            if (range.first > lastIndex) {
                val segment = processedText.substring(lastIndex, range.first)
                if (segment.isNotEmpty()) {
                    result.add(AnsiSpan(text = segment, style = currentStyle))
                }
            }

            val commandLetter = match.groupValues.getOrNull(2)
            val paramString = match.groupValues.getOrNull(1)

            if (commandLetter == "m") {
                // SGR color & style formatting
                currentStyle = applySgrCodes(currentStyle, paramString ?: "")
            }
            // Other commands like cursor movements [H, [A, clear [2J, [K are ignored/stripped from text output

            lastIndex = range.last + 1
        }

        if (lastIndex < processedText.length) {
            val trailing = processedText.substring(lastIndex)
            if (trailing.isNotEmpty()) {
                result.add(AnsiSpan(text = trailing, style = currentStyle))
            }
        }

        return result
    }

    /**
     * Strips all ANSI escape sequences and returns clean plaintext.
     */
    fun stripAnsi(text: String): String {
        return ANSI_REGEX.replace(text, "")
    }

    private fun normalizeCarriageReturns(text: String): String {
        if (!text.contains('\r')) return text
        val lines = text.split("\n")
        val processedLines = lines.map { line ->
            if (line.contains('\r')) {
                val parts = line.split('\r')
                parts.lastOrNull { it.isNotEmpty() } ?: ""
            } else {
                line
            }
        }
        return processedLines.joinToString("\n")
    }

    private fun applySgrCodes(initialStyle: AnsiStyle, paramString: String): AnsiStyle {
        if (paramString.isEmpty()) {
            return AnsiStyle() // \033[m is reset
        }

        val tokens = paramString.split(';').mapNotNull { it.toIntOrNull() }
        if (tokens.isEmpty()) {
            return AnsiStyle()
        }

        var style = initialStyle
        var i = 0
        while (i < tokens.size) {
            val code = tokens[i]
            when (code) {
                0 -> style = AnsiStyle()
                1 -> style = style.copy(isBold = true, isDim = false)
                2 -> style = style.copy(isDim = true, isBold = false)
                3 -> style = style.copy(isItalic = true)
                4 -> style = style.copy(isUnderline = true)
                7 -> style = style.copy(isInverse = true)
                9 -> style = style.copy(isStrikethrough = true)
                22 -> style = style.copy(isBold = false, isDim = false)
                23 -> style = style.copy(isItalic = false)
                24 -> style = style.copy(isUnderline = false)
                27 -> style = style.copy(isInverse = false)
                29 -> style = style.copy(isStrikethrough = false)

                // Standard Foreground Colors (30-37)
                in 30..37 -> {
                    style = style.copy(foregroundColor = STANDARD_COLORS[code - 30])
                }
                39 -> style = style.copy(foregroundColor = null) // Default FG

                // Standard Background Colors (40-47)
                in 40..47 -> {
                    style = style.copy(backgroundColor = STANDARD_COLORS[code - 40])
                }
                49 -> style = style.copy(backgroundColor = null) // Default BG

                // Bright Foreground Colors (90-97)
                in 90..97 -> {
                    style = style.copy(foregroundColor = BRIGHT_COLORS[code - 90])
                }

                // Bright Background Colors (100-107)
                in 100..107 -> {
                    style = style.copy(backgroundColor = BRIGHT_COLORS[code - 100])
                }

                // Extended Foreground Color (38;5;n or 38;2;r;g;b)
                38 -> {
                    val (color, consumed) = parseExtendedColor(tokens, i + 1)
                    if (color != null) {
                        style = style.copy(foregroundColor = color)
                    }
                    i += consumed
                }

                // Extended Background Color (48;5;n or 48;2;r;g;b)
                48 -> {
                    val (color, consumed) = parseExtendedColor(tokens, i + 1)
                    if (color != null) {
                        style = style.copy(backgroundColor = color)
                    }
                    i += consumed
                }
            }
            i++
        }
        return style
    }

    private fun parseExtendedColor(tokens: List<Int>, startIndex: Int): Pair<Long?, Int> {
        if (startIndex >= tokens.size) return Pair(null, 0)
        val mode = tokens[startIndex]

        return when (mode) {
            5 -> {
                // 256 colors: 38;5;N
                if (startIndex + 1 < tokens.size) {
                    val colorIndex = tokens[startIndex + 1]
                    Pair(colorFrom256(colorIndex), 2)
                } else {
                    Pair(null, 1)
                }
            }
            2 -> {
                // 24-bit RGB: 38;2;R;G;B
                if (startIndex + 3 < tokens.size) {
                    val r = tokens[startIndex + 1].coerceIn(0, 255)
                    val g = tokens[startIndex + 2].coerceIn(0, 255)
                    val b = tokens[startIndex + 3].coerceIn(0, 255)
                    val rgb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                    Pair(rgb, 4)
                } else {
                    Pair(null, 1)
                }
            }
            else -> Pair(null, 1)
        }
    }

    private fun colorFrom256(index: Int): Long {
        return when {
            index in 0..7 -> STANDARD_COLORS[index]
            index in 8..15 -> BRIGHT_COLORS[index - 8]
            index in 16..231 -> {
                // 6x6x6 color cube
                val cube = index - 16
                val r = (cube / 36) % 6
                val g = (cube / 6) % 6
                val b = cube % 6
                val rVal = if (r == 0) 0 else 55 + r * 40
                val gVal = if (g == 0) 0 else 55 + g * 40
                val bVal = if (b == 0) 0 else 55 + b * 40
                0xFF000000L or (rVal.toLong() shl 16) or (gVal.toLong() shl 8) or bVal.toLong()
            }
            index in 232..255 -> {
                // 24 grayscale levels
                val gray = (index - 232) * 10 + 8
                0xFF000000L or (gray.toLong() shl 16) or (gray.toLong() shl 8) or gray.toLong()
            }
            else -> 0xFFFFFFFFL
        }
    }
}
