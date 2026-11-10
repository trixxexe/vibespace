package com.vibespace.core.language.model

/**
 * Metadata definition for a supported programming or markup language in VibeSpace.
 */
data class LanguageDefinition(
    val id: String,
    val displayName: String,
    val fileExtensions: List<String>,
    val filenames: List<String> = emptyList(),
    val mimeTypes: List<String> = emptyList(),
    val lineCommentPrefix: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
    val supportsLsp: Boolean = false,
    val supportsTreeSitter: Boolean = false
) {
    companion object {
        val Kotlin = LanguageDefinition(
            id = "kotlin",
            displayName = "Kotlin",
            fileExtensions = listOf("kt", "kts"),
            lineCommentPrefix = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val Java = LanguageDefinition(
            id = "java",
            displayName = "Java",
            fileExtensions = listOf("java"),
            lineCommentPrefix = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val Python = LanguageDefinition(
            id = "python",
            displayName = "Python",
            fileExtensions = listOf("py", "pyw"),
            lineCommentPrefix = "#",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val JavaScript = LanguageDefinition(
            id = "javascript",
            displayName = "JavaScript",
            fileExtensions = listOf("js", "mjs", "cjs"),
            lineCommentPrefix = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val TypeScript = LanguageDefinition(
            id = "typescript",
            displayName = "TypeScript",
            fileExtensions = listOf("ts", "tsx"),
            lineCommentPrefix = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val Rust = LanguageDefinition(
            id = "rust",
            displayName = "Rust",
            fileExtensions = listOf("rs"),
            lineCommentPrefix = "//",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val Go = LanguageDefinition(
            id = "go",
            displayName = "Go",
            fileExtensions = listOf("go"),
            lineCommentPrefix = "//",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val C = LanguageDefinition(
            id = "c",
            displayName = "C",
            fileExtensions = listOf("c", "h"),
            lineCommentPrefix = "//",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val Cpp = LanguageDefinition(
            id = "cpp",
            displayName = "C++",
            fileExtensions = listOf("cpp", "cc", "cxx", "hpp", "hh"),
            lineCommentPrefix = "//",
            supportsLsp = true,
            supportsTreeSitter = true
        )

        val Html = LanguageDefinition(
            id = "html",
            displayName = "HTML",
            fileExtensions = listOf("html", "htm"),
            blockCommentStart = "<!--",
            blockCommentEnd = "-->"
        )

        val Css = LanguageDefinition(
            id = "css",
            displayName = "CSS",
            fileExtensions = listOf("css", "scss", "sass"),
            lineCommentPrefix = "//",
            blockCommentStart = "/*",
            blockCommentEnd = "*/"
        )

        val Json = LanguageDefinition(
            id = "json",
            displayName = "JSON",
            fileExtensions = listOf("json")
        )

        val Markdown = LanguageDefinition(
            id = "markdown",
            displayName = "Markdown",
            fileExtensions = listOf("md", "markdown")
        )

        val Shell = LanguageDefinition(
            id = "shell",
            displayName = "Shell Script",
            fileExtensions = listOf("sh", "bash", "zsh"),
            lineCommentPrefix = "#"
        )

        val PlainText = LanguageDefinition(
            id = "plaintext",
            displayName = "Plain Text",
            fileExtensions = listOf("txt")
        )

        val AllLanguages = listOf(
            Kotlin, Java, Python, JavaScript, TypeScript,
            Rust, Go, C, Cpp, Html, Css, Json, Markdown, Shell, PlainText
        )
    }
}
