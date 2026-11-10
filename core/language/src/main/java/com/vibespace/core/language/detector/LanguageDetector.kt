package com.vibespace.core.language.detector

import com.vibespace.core.language.model.LanguageDefinition

interface LanguageDetector {
    fun detectByFileName(fileName: String): LanguageDefinition
    fun detectByExtension(extension: String): LanguageDefinition
    fun detectByContent(firstLines: String, fileName: String? = null): LanguageDefinition
}

class DefaultLanguageDetector : LanguageDetector {

    override fun detectByFileName(fileName: String): LanguageDefinition {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty()) {
            val byExt = detectByExtension(extension)
            if (byExt != LanguageDefinition.PlainText) {
                return byExt
            }
        }

        // Special filenames like Dockerfile, Makefile, etc.
        return when (fileName.lowercase()) {
            "dockerfile" -> LanguageDefinition(id = "dockerfile", displayName = "Dockerfile", fileExtensions = emptyList())
            "makefile" -> LanguageDefinition(id = "makefile", displayName = "Makefile", fileExtensions = emptyList())
            else -> LanguageDefinition.PlainText
        }
    }

    override fun detectByExtension(extension: String): LanguageDefinition {
        val cleanExt = extension.removePrefix(".").lowercase()
        return LanguageDefinition.AllLanguages.firstOrNull { lang ->
            lang.fileExtensions.contains(cleanExt)
        } ?: LanguageDefinition.PlainText
    }

    override fun detectByContent(firstLines: String, fileName: String?): LanguageDefinition {
        if (!fileName.isNullOrBlank()) {
            val byName = detectByFileName(fileName)
            if (byName != LanguageDefinition.PlainText) {
                return byName
            }
        }

        val firstLine = firstLines.lineSequence().firstOrNull()?.trim() ?: ""
        if (firstLine.startsWith("#!")) {
            return when {
                firstLine.contains("python") -> LanguageDefinition.Python
                firstLine.contains("node") -> LanguageDefinition.JavaScript
                firstLine.contains("sh") || firstLine.contains("bash") -> LanguageDefinition.Shell
                else -> LanguageDefinition.PlainText
            }
        }

        return LanguageDefinition.PlainText
    }
}
