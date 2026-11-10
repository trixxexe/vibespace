package com.vibespace.core.editor

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.extension.DefaultExtensionManager
import com.vibespace.core.editor.extension.ExtensionCategory
import com.vibespace.core.editor.extension.ExtensionMetadata
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
    fun installExtension_and_toggle_and_uninstall() = runTest {
        val ext = ExtensionMetadata(
            id = "test.custom-theme",
            name = "custom-theme",
            displayName = "Custom Theme",
            publisher = "tester",
            version = "1.0.0",
            description = "Test theme",
            category = ExtensionCategory.THEME
        )

        extensionManager.installExtension(ext)
        var installed = extensionManager.installedExtensions.first()
        assertTrue(installed.any { it.id == ext.id && it.isEnabled })

        // Toggle disable
        extensionManager.toggleExtension(ext.id, false)
        installed = extensionManager.installedExtensions.first()
        assertFalse(installed.first { it.id == ext.id }.isEnabled)

        // Uninstall
        extensionManager.uninstallExtension(ext.id)
        installed = extensionManager.installedExtensions.first()
        assertFalse(installed.any { it.id == ext.id })
    }

    @Test
    fun searchOpenVsx_offline_filters_curated() = runTest {
        val result = extensionManager.searchOpenVsx("Dracula")
        assertTrue(result.isSuccess)
        val matches = result.getOrNull()!!
        assertTrue(matches.any { it.displayName.contains("Dracula") })
    }
}
