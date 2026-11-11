package com.vibespace.feature.search

import com.google.re2j.Pattern
import org.junit.Assert.assertEquals
import org.junit.Test

class RegexSearchTest {
    @Test
    fun findsZeroWidthMatchesAndEndOfFileMatchesWithoutLooping() {
        // RE2/J deliberately excludes look-around; anchors still exercise valid zero-width matches.
        val zeroWidth = RegexSearch.findMatches("abc", Pattern.compile("^|$"))
        val endOfFile = RegexSearch.findMatches("abc", Pattern.compile("$"))

        assertEquals(listOf(0..-1, 2..1), zeroWidth)
        assertEquals(listOf(3..2), endOfFile)
    }

    @Test
    fun replacesZeroWidthAndEndOfFileMatchesSafely() {
        assertEquals("xabcx", RegexSearch.replaceAll("abc", Pattern.compile("^|$"), "x"))
        assertEquals("abc!", RegexSearch.replaceAll("abc", Pattern.compile("$"), "!"))
    }
}
