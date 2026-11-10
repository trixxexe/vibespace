package com.vibespace.core.common.id

import java.util.UUID

/**
 * Utility for generating stable unique identifiers across documents, sessions, and tasks.
 */
object IdGenerator {
    fun generate(prefix: String = ""): String {
        val uuid = UUID.randomUUID().toString()
        return if (prefix.isBlank()) uuid else "$prefix-$uuid"
    }

    fun shortId(prefix: String = ""): String {
        val short = UUID.randomUUID().toString().substring(0, 8)
        return if (prefix.isBlank()) short else "$prefix-$short"
    }
}
