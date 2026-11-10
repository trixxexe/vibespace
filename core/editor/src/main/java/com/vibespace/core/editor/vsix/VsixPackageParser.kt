package com.vibespace.core.editor.vsix

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.theme.EditorThemeDefinition
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Parser for VS Code Extension packages (.vsix).
 * Unpacks the internal zip archive and extracts declarative themes and TextMate grammars.
 */
object VsixPackageParser {

    private const val MAX_ENTRY_BYTES = 5 * 1024 * 1024L // 5 MB per entry
    private const val MAX_TOTAL_BYTES = 50 * 1024 * 1024L // 50 MB total archive
    private const val MAX_ENTRIES_COUNT = 500

    fun parseThemeFromVsix(inputStream: InputStream): VibeResult<EditorThemeDefinition> {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            var packageJsonStr: String? = null
            val themeFiles = mutableMapOf<String, String>()
            var totalBytesRead = 0L
            var entryCount = 0

            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES_COUNT) {
                    return VibeResult.Error("Security violation: VSIX archive contains too many entries (exceeds $MAX_ENTRIES_COUNT limit).")
                }

                val name = entry.name
                // Zip Slip protection
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    return VibeResult.Error("Security violation: Path traversal detected in entry '$name'.")
                }

                if (name == "extension/package.json" || name.endsWith("/package.json")) {
                    val content = readEntryText(zip) { bytesRead ->
                        totalBytesRead += bytesRead
                        if (totalBytesRead > MAX_TOTAL_BYTES) {
                            throw IllegalStateException("VSIX archive uncompressed size exceeds $MAX_TOTAL_BYTES bytes limit.")
                        }
                    }
                    packageJsonStr = content
                } else if (name.endsWith(".json") && name.contains("theme", ignoreCase = true)) {
                    val normalizedPath = name.removePrefix("extension/")
                    val content = readEntryText(zip) { bytesRead ->
                        totalBytesRead += bytesRead
                        if (totalBytesRead > MAX_TOTAL_BYTES) {
                            throw IllegalStateException("VSIX archive uncompressed size exceeds $MAX_TOTAL_BYTES bytes limit.")
                        }
                    }
                    themeFiles[normalizedPath] = content
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
                try {
                    val pkgJson = JSONObject(packageJsonStr)
                    themeLabel = pkgJson.optString("displayName", pkgJson.optString("name", "Imported Theme"))
                    val contributes = pkgJson.optJSONObject("contributes")
                    val themesArray = contributes?.optJSONArray("themes")
                    if (themesArray != null && themesArray.length() > 0) {
                        val firstTheme = themesArray.getJSONObject(0)
                        themeLabel = firstTheme.optString("label", themeLabel)
                        themePath = firstTheme.optString("path", "").removePrefix("./").removePrefix("/")
                    }
                } catch (t: Throwable) {
                    val labelMatch = Regex(""""label"\s*:\s*"([^"]+)"""").find(packageJsonStr)
                    if (labelMatch != null) themeLabel = labelMatch.groupValues[1]
                    val pathMatch = Regex(""""path"\s*:\s*"([^"]+)"""").find(packageJsonStr)
                    if (pathMatch != null) themePath = pathMatch.groupValues[1].removePrefix("./").removePrefix("/")
                }
            }

            // Find matching theme content
            val themeContent = themeFiles[themePath] ?: themeFiles.values.firstOrNull()
            if (themeContent == null) {
                return VibeResult.Error("No valid color theme found in .vsix archive.")
            }

            var bg = "#1E1E1E"
            var gutter = bg
            var currentLine = "#282828"
            var cursor = "#00E5FF"
            var text = "#D4D4D4"
            var selection = "#264F78"

            try {
                val themeJson = JSONObject(themeContent)
                val colors = themeJson.optJSONObject("colors")
                if (colors != null) {
                    bg = colors.optString("editor.background", bg)
                    gutter = colors.optString("editorGutter.background", bg)
                    currentLine = colors.optString("editor.lineHighlightBackground", currentLine)
                    cursor = colors.optString("editorCursor.foreground", cursor)
                    text = colors.optString("editor.foreground", text)
                    selection = colors.optString("editor.selectionBackground", selection)
                }
            } catch (t: Throwable) {
                Regex(""""editor\.background"\s*:\s*"([^"]+)"""").find(themeContent)?.let { bg = it.groupValues[1] }
                Regex(""""editorGutter\.background"\s*:\s*"([^"]+)"""").find(themeContent)?.let { gutter = it.groupValues[1] }
                Regex(""""editor\.lineHighlightBackground"\s*:\s*"([^"]+)"""").find(themeContent)?.let { currentLine = it.groupValues[1] }
                Regex(""""editorCursor\.foreground"\s*:\s*"([^"]+)"""").find(themeContent)?.let { cursor = it.groupValues[1] }
                Regex(""""editor\.foreground"\s*:\s*"([^"]+)"""").find(themeContent)?.let { text = it.groupValues[1] }
                Regex(""""editor\.selectionBackground"\s*:\s*"([^"]+)"""").find(themeContent)?.let { selection = it.groupValues[1] }
            }

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

    private fun readEntryText(zip: ZipInputStream, onBytesRead: (Int) -> Unit = {}): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len: Int
        var entryBytes = 0L
        while (zip.read(buffer).also { len = it } != -1) {
            entryBytes += len
            if (entryBytes > MAX_ENTRY_BYTES) {
                throw IllegalStateException("VSIX archive entry exceeds $MAX_ENTRY_BYTES bytes limit.")
            }
            onBytesRead(len)
            out.write(buffer, 0, len)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
