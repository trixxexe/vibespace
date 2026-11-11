package com.vibespace.feature.search

import com.google.re2j.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexSearchAdversarialTest {

    private fun assertValidRanges(content: String, ranges: List<IntRange>) {
        for (range in ranges) {
            // Must have non-negative start
            assertTrue("Range start must be >= 0: $range", range.first >= 0)
            // Range start cannot exceed content.length
            assertTrue(
                "Range start ${range.first} exceeds content length ${content.length}",
                range.first <= content.length
            )
            // Valid range invariant: first <= last + 1 (for empty ranges, first == last + 1)
            assertTrue(
                "Invalid range bounds: start ${range.first} > end ${range.last} + 1",
                range.first <= range.last + 1
            )
            // Must never crash with StringIndexOutOfBoundsException
            if (range.isEmpty()) {
                val sub = content.substring(range.first, range.first)
                assertEquals("", sub)
            } else {
                assertTrue("Range last must be < content length", range.last < content.length)
                val sub = content.substring(range.first, range.last + 1)
                assertFalse(sub.isEmpty())
            }
        }
    }

    @Test
    fun emptyString_withStartAnchor() {
        val matches = RegexSearch.findMatches("", Pattern.compile("^"))
        assertEquals(listOf(0..-1), matches)
        assertValidRanges("", matches)
    }

    @Test
    fun emptyString_withEndAnchor() {
        val matches = RegexSearch.findMatches("", Pattern.compile("$"))
        assertEquals(listOf(0..-1), matches)
        assertValidRanges("", matches)
    }

    @Test
    fun emptyString_withStartOrEndAnchor() {
        val matches = RegexSearch.findMatches("", Pattern.compile("^|$"))
        // On empty string, ^ and $ both match at index 0. RE2/J advances after zero-width match.
        assertEquals(listOf(0..-1), matches)
        assertValidRanges("", matches)
    }

    @Test
    fun emptyString_withMatchAllZeroOrMore() {
        val matches = RegexSearch.findMatches("", Pattern.compile("a*"))
        assertEquals(listOf(0..-1), matches)
        assertValidRanges("", matches)
    }

    @Test
    fun emptyString_withDotStar() {
        val matches = RegexSearch.findMatches("", Pattern.compile(".*"))
        assertEquals(listOf(0..-1), matches)
        assertValidRanges("", matches)
    }

    @Test
    fun singleChar_withAnchorsAndPatterns() {
        val s = "a"
        val startAnchor = RegexSearch.findMatches(s, Pattern.compile("^"))
        assertEquals(listOf(0..-1), startAnchor)
        assertValidRanges(s, startAnchor)

        val endAnchor = RegexSearch.findMatches(s, Pattern.compile("$"))
        assertEquals(listOf(1..0), endAnchor)
        assertValidRanges(s, endAnchor)

        val bothAnchors = RegexSearch.findMatches(s, Pattern.compile("^|$"))
        assertEquals(listOf(0..-1, 1..0), bothAnchors)
        assertValidRanges(s, bothAnchors)

        val exactMatch = RegexSearch.findMatches(s, Pattern.compile("a"))
        assertEquals(listOf(0..0), exactMatch)
        assertValidRanges(s, exactMatch)

        val noMatch = RegexSearch.findMatches(s, Pattern.compile("b"))
        assertTrue(noMatch.isEmpty())
        assertValidRanges(s, noMatch)
    }

    @Test
    fun multilineString_withAnchorsAndFlags() {
        val content = "hello\nworld\n"

        // Default single-line mode (without MULTILINE flag)
        val defaultStart = RegexSearch.findMatches(content, Pattern.compile("^"))
        assertEquals(listOf(0..-1), defaultStart)
        assertValidRanges(content, defaultStart)

        val defaultEnd = RegexSearch.findMatches(content, Pattern.compile("$"))
        // In RE2 without MULTILINE, $ matches at end of string (index 12)
        assertEquals(listOf(12..11), defaultEnd)
        assertValidRanges(content, defaultEnd)

        // Multiline mode (?m)
        val multiStart = RegexSearch.findMatches(content, Pattern.compile("(?m)^"))
        // Lines start at index 0 ("hello"), index 6 ("world"), index 12 ("")
        assertEquals(listOf(0..-1, 6..5, 12..11), multiStart)
        assertValidRanges(content, multiStart)

        val multiEnd = RegexSearch.findMatches(content, Pattern.compile("(?m)$"))
        // Lines end before \n at index 5, index 11, and at end of string index 12
        assertEquals(listOf(5..4, 11..10, 12..11), multiEnd)
        assertValidRanges(content, multiEnd)

        val multiBoth = RegexSearch.findMatches(content, Pattern.compile("(?m)^|$"))
        assertValidRanges(content, multiBoth)
    }

    @Test
    fun multilineWithoutTrailingNewline() {
        val content = "abc\ndef"
        val multiStart = RegexSearch.findMatches(content, Pattern.compile("(?m)^"))
        assertEquals(listOf(0..-1, 4..3), multiStart)
        assertValidRanges(content, multiStart)

        val multiEnd = RegexSearch.findMatches(content, Pattern.compile("(?m)$"))
        assertEquals(listOf(3..2, 7..6), multiEnd)
        assertValidRanges(content, multiEnd)

        val multiBoth = RegexSearch.findMatches(content, Pattern.compile("(?m)^|$"))
        assertEquals(listOf(0..-1, 3..2, 4..3, 7..6), multiBoth)
        assertValidRanges(content, multiBoth)
    }

    @Test
    fun stressTestRandomPatternsAndInputs() {
        val testInputs = listOf(
            "",
            "x",
            "hello",
            "\n",
            "\r\n",
            "a\nb\nc\n",
            "   ",
            "\t\t\t",
            "foo\n\n\nbar",
            "Emojis: 🚀✨🎉 test"
        )
        val testPatterns = listOf(
            "^",
            "$",
            "^|$",
            "(?m)^",
            "(?m)$",
            "(?m)^|$",
            ".*",
            ".+",
            "\\w+",
            "\\s*",
            "\\b",
            "\\B",
            "[a-z]*",
            "^.*$"
        )

        for (input in testInputs) {
            for (patStr in testPatterns) {
                val pattern = Pattern.compile(patStr)
                val matches = RegexSearch.findMatches(input, pattern)
                assertValidRanges(input, matches)
            }
        }
    }

    @Test
    fun replaceAll_edgeCases() {
        assertEquals("X", RegexSearch.replaceAll("", Pattern.compile("^"), "X"))
        assertEquals("X", RegexSearch.replaceAll("", Pattern.compile("$"), "X"))
        assertEquals("X", RegexSearch.replaceAll("", Pattern.compile("^|$"), "X"))
        assertEquals("X-aX-", RegexSearch.replaceAll("a", Pattern.compile("^|$"), "X-"))
        assertEquals("> hello\n> world", RegexSearch.replaceAll("hello\nworld", Pattern.compile("(?m)^"), "> "))
    }
}
