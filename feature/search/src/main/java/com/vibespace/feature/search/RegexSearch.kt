package com.vibespace.feature.search

import com.google.re2j.Pattern

/** Offset-safe regex operations shared by file and workspace search. */
internal object RegexSearch {
    fun findMatches(content: String, pattern: Pattern): List<IntRange> {
        val matcher = pattern.matcher(content)
        return buildList {
            while (matcher.find()) add(matcher.start() until matcher.end())
        }
    }

    fun replaceAll(content: String, pattern: Pattern, replacement: String): String =
        pattern.matcher(content).replaceAll(replacement)
}
