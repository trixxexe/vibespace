package com.vibespace.core.editor.vsix

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.theme.EditorThemeDefinition
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Parser for VS Code Extension packages (.vsix).
 * Unpacks the internal zip archive and extracts declarative themes and TextMate grammars.
 */
object VsixPackageParser {

    fun parseThemeFromVsix(inputStream: InputStream): VibeResult<EditorThemeDefinition> {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            var packageJsonStr: String? = null
            val themeFiles = mutableMapOf<String, String>()

            while (entry != null) {
                val name = entry.name
                if (name == "extension/package.json" || name.endsWith("/package.json")) {
                    packageJsonStr = zip.bufferedReader().readText()
                } else if (name.endsWith(".json") && name.contains("theme", ignoreCase = true)) {
                    val normalizedPath = name.removePrefix("extension/")
                    themeFiles[normalizedPath] = zip.bufferedReader().readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            if (packageJsonStr == null && themeFiles.isEmpty()) {
                return VibeResult.Error("Not a valid VS Code extension package: package.json missing.")
            }

            var themeLabel = "Imported Theme"
            var themePath: String? = null

            if (packageJsonStr != null) {
                val pkgJson = JSONObject(packageJsonStr)
                themeLabel = pkgJson.optString("displayName", pkgJson.optString("name", "Imported Theme"))
                val contributes = pkgJson.optJSONObject("contributes")
                val themesArray = contributes?.optJSONArray("themes")
                if (themesArray != null && themesArray.length() > 0) {
                    val firstTheme = themesArray.getJSONObject(0)
                    themeLabel = firstTheme.optString("label", themeLabel)
                    themePath = firstTheme.optString("path", "").removePrefix("./").removePrefix("/")
                }
            }

            // Find matching theme content
            val themeContent = themeFiles[themePath] ?: themeFiles.values.firstOrNull()
            if (themeContent == null) {
                return VibeResult.Error("No valid color theme found in .vsix archive.")
            }

            val themeJson = JSONObject(themeContent)
            val colors = themeJson.optJSONObject("colors")

            val bg = colors?.optString("editor.background", "#1E1E1E") ?: "#1E1E1E"
            val gutter = colors?.optString("editorGutter.background", bg) ?: bg
            val currentLine = colors?.optString("editor.lineHighlightBackground", "#282828") ?: "#282828"
            val cursor = colors?.optString("editorCursor.foreground", "#00E5FF") ?: "#00E5FF"
            val text = colors?.optString("editor.foreground", "#D4D4D4") ?: "#D4D4D4"
            val selection = colors?.optString("editor.selectionBackground", "#264F78") ?: "#264F78"

            val definition = EditorThemeDefinition(
                id = themeLabel.lowercase().replace("\\s+".toRegex(), "_"),
                name = themeLabel,
                author = "VS Code Extension",
                backgroundColor = bg,
                gutterColor = gutter,
                currentLineColor = currentLine,
                selectionColor = selection,
                cursorColor = cursor,
                textColor = text
            )

            VibeResult.Success(definition)
        } catch (e: Throwable) {
            VibeResult.Error("Failed to parse .vsix package: ${e.message}", e)
        }
    }
}
