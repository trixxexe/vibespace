package com.vibespace.core.editor

import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.editor.vsix.VsixPackageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VsixPackageParserTest {

    @Test
    fun parseThemeFromVsix_extracts_theme_definition_successfully() {
        val packageJson = """
            {
                "name": "dracula-theme",
                "displayName": "Dracula Official",
                "contributes": {
                    "themes": [
                        {
                            "label": "Dracula",
                            "uiTheme": "vs-dark",
                            "path": "./themes/dracula.json"
                        }
                    ]
                }
            }
        """.trimIndent()

        val themeJson = """
            {
                "name": "Dracula",
                "colors": {
                    "editor.background": "#282a36",
                    "editor.foreground": "#f8f8f2",
                    "editorCursor.foreground": "#ff79c6",
                    "editor.selectionBackground": "#44475a"
                }
            }
        """.trimIndent()

        val byteOut = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(byteOut)

        zipOut.putNextEntry(ZipEntry("extension/package.json"))
        zipOut.write(packageJson.toByteArray())
        zipOut.closeEntry()

        zipOut.putNextEntry(ZipEntry("extension/themes/dracula.json"))
        zipOut.write(themeJson.toByteArray())
        zipOut.closeEntry()

        zipOut.close()

        val inputStream = ByteArrayInputStream(byteOut.toByteArray())
        val result = VsixPackageParser.parseThemeFromVsix(inputStream)

        assertTrue(result.isSuccess)
        val theme = result.getOrNull()
        assertNotNull(theme)
        assertEquals("Dracula", theme!!.name)
        assertEquals("#282a36", theme.backgroundColor)
        assertEquals("#ff79c6", theme.cursorColor)
    }

    @Test
    fun parseThemeFromVsix_rejects_zip_slip_path_traversal() {
        val byteOut = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(byteOut)

        zipOut.putNextEntry(ZipEntry("../../etc/passwd"))
        zipOut.write("root:x:0:0:root:/root:/bin/sh".toByteArray())
        zipOut.closeEntry()
        zipOut.close()

        val inputStream = ByteArrayInputStream(byteOut.toByteArray())
        val result = VsixPackageParser.parseThemeFromVsix(inputStream)

        assertTrue(result is com.vibespace.core.common.result.VibeResult.Error)
        val errMsg = (result as com.vibespace.core.common.result.VibeResult.Error).message
        assertTrue(errMsg.contains("Path traversal detected") || errMsg.contains("Security violation"))
    }

    @Test
    fun builtinThemes_contain_classic_popular_presets() {
        val themes = EditorThemeDefinition.BUILTIN_THEMES
        assertEquals(11, themes.size)
        assertTrue(themes.any { it.name.contains("VS Code Blue") })
        assertTrue(themes.any { it.name.contains("Dracula") })
        assertTrue(themes.any { it.name.contains("One Dark Pro") })
        assertTrue(themes.any { it.name.contains("Monokai Pro") })
        assertTrue(themes.any { it.name.contains("Nord") })
    }
}
