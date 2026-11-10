package com.vibespace.core.terminal

import com.vibespace.core.terminal.ansi.AnsiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiParserTest {

    @Test
    fun parse_plainText_returnsSingleSpanWithDefaultStyle() {
        val input = "Hello, VibeSpace!"
        val spans = AnsiParser.parse(input)

        assertEquals(1, spans.size)
        assertEquals("Hello, VibeSpace!", spans[0].text)
        assertNull(spans[0].style.foregroundColor)
        assertFalse(spans[0].style.isBold)
    }

    @Test
    fun parse_standardColorAndBold() {
        // \033[1;31mRed Bold\033[0m Normal
        val input = "\u001B[1;31mRed Bold\u001B[0m Normal"
        val spans = AnsiParser.parse(input)

        assertEquals(2, spans.size)
        assertEquals("Red Bold", spans[0].text)
        assertTrue(spans[0].style.isBold)
        assertEquals(0xFFE06C75L, spans[0].style.foregroundColor)

        assertEquals(" Normal", spans[1].text)
        assertFalse(spans[1].style.isBold)
        assertNull(spans[1].style.foregroundColor)
    }

    @Test
    fun parse_256ColorForeground() {
        // \033[38;5;196mHello
        val input = "\u001B[38;5;196mHello"
        val spans = AnsiParser.parse(input)

        assertEquals(1, spans.size)
        assertEquals("Hello", spans[0].text)
        assertNotNull(spans[0].style.foregroundColor)
    }

    @Test
    fun parse_24BitTrueColorRgb() {
        // \033[38;2;120;200;80mRGB Text\033[0m
        val input = "\u001B[38;2;120;200;80mRGB Text\u001B[0m"
        val spans = AnsiParser.parse(input)

        assertEquals(1, spans.size)
        assertEquals("RGB Text", spans[0].text)
        val expectedColor = 0xFF000000L or (120L shl 16) or (200L shl 8) or 80L
        assertEquals(expectedColor, spans[0].style.foregroundColor)
    }

    @Test
    fun parse_textStyles_italicUnderlineStrikethrough() {
        val input = "\u001B[3mItalic\u001B[23m \u001B[4mUnderline\u001B[24m \u001B[9mStrike\u001B[0m"
        val spans = AnsiParser.parse(input)

        assertEquals(5, spans.size)
        assertEquals("Italic", spans[0].text)
        assertTrue(spans[0].style.isItalic)

        assertEquals(" ", spans[1].text)
        assertFalse(spans[1].style.isItalic)

        assertEquals("Underline", spans[2].text)
        assertTrue(spans[2].style.isUnderline)

        assertEquals(" ", spans[3].text)
        assertFalse(spans[3].style.isUnderline)

        assertEquals("Strike", spans[4].text)
        assertTrue(spans[4].style.isStrikethrough)
    }

    @Test
    fun parse_stripsNonSgrEscapeSequences() {
        // \033[2J clear screen, \033[H cursor home
        val input = "\u001B[2J\u001B[HWelcome to Alpine Linux"
        val spans = AnsiParser.parse(input)

        assertEquals(1, spans.size)
        assertEquals("Welcome to Alpine Linux", spans[0].text)
    }

    @Test
    fun parse_handlesCarriageReturnProgress() {
        // Overwriting line with \r
        val input = "Downloading 10%\rDownloading 50%\rDownloading 100%"
        val spans = AnsiParser.parse(input)

        assertEquals(1, spans.size)
        assertEquals("Downloading 100%", spans[0].text)
    }

    @Test
    fun stripAnsi_removesAllAnsiSequences() {
        val input = "\u001B[1;32mSuccess:\u001B[0m All tests passed!"
        val clean = AnsiParser.stripAnsi(input)

        assertEquals("Success: All tests passed!", clean)
    }
}
