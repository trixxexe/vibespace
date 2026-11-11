package com.vibespace.feature.search

import com.google.re2j.Pattern
import org.junit.Assert.assertEquals
import org.junit.Test

class RegexSearchTest {
    @Test
    fun findsZeroWidthMatchesAndEndOfFileMatchesWithoutLooping() {
        val zeroWidth = RegexSearch.findMatches("aba", Pattern.compile("(?=a)"))
        val endOfFile = RegexSearch.findMatches("abc", Pattern.compile("$"))

        assertEquals(listOf(0..-1, 2..1), zeroWidth)
        assertEquals(listOf(3..2), endOfFile)
    }

    @Test
    fun replacesZeroWidthAndEndOfFileMatchesSafely() {
        assertEquals("xabxa", RegexSearch.replaceAll("aba", Pattern.compile("(?=a)"), "x"))
        assertEquals("abc!", RegexSearch.replaceAll("abc", Pattern.compile("$"), "!"))
    }
}
