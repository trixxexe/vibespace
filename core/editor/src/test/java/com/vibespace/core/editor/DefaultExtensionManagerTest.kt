package com.vibespace.core.editor

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.extension.DefaultExtensionManager
import com.vibespace.core.editor.extension.ExtensionCategory
import com.vibespace.core.editor.extension.ExtensionMetadata
import com.vibespace.core.editor.theme.EditorThemeDefinition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultExtensionManagerTest {

    private val dispatchers = TestDispatcherProvider()
    private val extensionManager = DefaultExtensionManager(dispatchers = dispatchers)

    @Test
    fun getCuratedExtensions_returns_popular_extensions() = runTest {
        val result = extensionManager.getCuratedExtensions()
        assertTrue(result.isSuccess)
        val list = result.getOrNull()!!
        assertTrue(list.any { it.displayName == "One Dark Pro" })
        assertTrue(list.any { it.displayName == "Dracula Official" })
        assertTrue(list.any { it.displayName == "Material Icon Theme" })
    }

    @Test
    fun installExtension_rejects_metadata_without_a_package() = runTest {
        val ext = ExtensionMetadata(
            id = "test.custom-theme",
            name = "custom-theme",
            displayName = "Custom Theme",
            publisher = "tester",
            version = "1.0.0",
            description = "Test theme",
            category = ExtensionCategory.THEME
        )

        val result = extensionManager.installExtension(ext)
        assertTrue(result is com.vibespace.core.common.result.VibeResult.Error)
        assertFalse(extensionManager.installedExtensions.first().any { it.id == ext.id })
    }

    @Test
    fun built_in_extensions_cannot_be_toggled_or_uninstalled() = runTest {
        val builtInId = "dracula-theme.theme-dracula"

        val toggleResult = extensionManager.toggleExtension(builtInId, false)
        assertTrue(toggleResult is com.vibespace.core.common.result.VibeResult.Error)
        val installed = extensionManager.installedExtensions.first()
        assertTrue(installed.first { it.id == builtInId }.isEnabled)

        val uninstallResult = extensionManager.uninstallExtension(builtInId)
        assertTrue(uninstallResult is com.vibespace.core.common.result.VibeResult.Error)
        assertTrue(extensionManager.installedExtensions.first().any { it.id == builtInId })
    }

    @Test
    fun importing_a_theme_registers_it_for_the_editor_and_uninstall_removes_it() = runTest {
        val result = extensionManager.installFromVsix(ByteArrayInputStream(createThemeVsix()))
        assertTrue(result.isSuccess)
        val extension = result.getOrNull()!!
        val theme = EditorThemeDefinition.fromId(extension.id)
        assertEquals("#112233", theme.backgroundColor)

        assertTrue(extensionManager.toggleExtension(extension.id, false).isSuccess)
        assertEquals(EditorThemeDefinition.VIBE_DARK, EditorThemeDefinition.fromId(extension.id))

        assertTrue(extensionManager.toggleExtension(extension.id, true).isSuccess)
        assertEquals("#112233", EditorThemeDefinition.fromId(extension.id).backgroundColor)

        assertTrue(extensionManager.uninstallExtension(extension.id).isSuccess)
        assertEquals(EditorThemeDefinition.VIBE_DARK, EditorThemeDefinition.fromId(extension.id))
    }

    @Test
    fun searchOpenVsx_empty_query_returns_curated_extensions() = runTest {
        val result = extensionManager.searchOpenVsx("")
        assertTrue(result.isSuccess)
        val matches = result.getOrNull()!!
        assertTrue(matches.any { it.displayName.contains("Dracula") })
    }

    @Test
    fun downloadAndInstall_without_a_package_returns_error_and_does_not_install() = runTest {
        val ext = ExtensionMetadata(
            id = "test.progress-ext",
            name = "progress-ext",
            displayName = "Progress Ext",
            publisher = "tester",
            version = "1.0.0",
            description = "Download test",
            category = ExtensionCategory.THEME
        )

        val progressValues = mutableListOf<Float>()
        val result = extensionManager.downloadAndInstall(ext) { progress ->
            progressValues.add(progress)
        }

        assertTrue(result is com.vibespace.core.common.result.VibeResult.Error)
        assertTrue(progressValues.isEmpty())

        val installed = extensionManager.installedExtensions.first()
        assertFalse(installed.any { it.id == ext.id })
    }

    @Test
    fun downloadAndInstall_with_invalid_network_url_returns_error() = runTest {
        val ext = ExtensionMetadata(
            id = "test.network-ext",
            name = "network-ext",
            displayName = "Network Ext",
            publisher = "tester",
            version = "1.0.0",
            description = "Download network test",
            category = ExtensionCategory.THEME,
            downloadUrl = "http://127.0.0.1:9999/nonexistent.vsix"
        )

        val result = extensionManager.downloadAndInstall(ext) {}
        assertTrue(result is com.vibespace.core.common.result.VibeResult.Error)
        val installed = extensionManager.installedExtensions.first()
        assertFalse(installed.any { it.id == ext.id })
    }

    private fun createThemeVsix(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("extension/package.json"))
            zip.write(
                """{"displayName":"Custom Test","contributes":{"themes":[{"label":"Custom Test","path":"themes/custom-color-theme.json"}]}}"""
                    .toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("extension/themes/custom-color-theme.json"))
            zip.write("""{"colors":{"editor.background":"#112233"}}""".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
