package com.vibespace.feature.settings

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.extension.DefaultExtensionManager
import com.vibespace.core.editor.extension.ExtensionCategory
import com.vibespace.core.editor.extension.ExtensionMetadata
import com.vibespace.feature.settings.extensions.ExtensionFilter
import com.vibespace.feature.settings.extensions.ExtensionsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionsViewModelTest {

    private val dispatchers = TestDispatcherProvider()
    private val extensionManager = DefaultExtensionManager(dispatchers = dispatchers)
    private val viewModel = ExtensionsViewModel(extensionManager = extensionManager, dispatchers = dispatchers)

    @Test
    fun loadExtensions_populates_curated_list() = runTest {
        viewModel.loadExtensions()
        val state = viewModel.uiState.value
        assertFalse(state.extensions.isEmpty())
        assertTrue(state.extensions.any { it.displayName == "One Dark Pro" })
    }

    @Test
    fun setFilter_updates_activeFilter() {
        viewModel.setFilter(ExtensionFilter.THEMES)
        assertEquals(ExtensionFilter.THEMES, viewModel.uiState.value.activeFilter)

        viewModel.setFilter(ExtensionFilter.FORMATTERS)
        assertEquals(ExtensionFilter.FORMATTERS, viewModel.uiState.value.activeFilter)
    }

    @Test
    fun install_and_uninstall_flow_works() = runTest {
        val ext = ExtensionMetadata(
            id = "nord.theme",
            name = "nord",
            displayName = "Nord Theme",
            publisher = "arctic",
            version = "1.0.0",
            description = "Nord color palette",
            category = ExtensionCategory.THEME
        )

        viewModel.installExtension(ext)
        var state = viewModel.uiState.value
        assertTrue(state.installedExtensions.any { it.id == ext.id })

        viewModel.uninstallExtension(ext.id)
        state = viewModel.uiState.value
        assertFalse(state.installedExtensions.any { it.id == ext.id })
    }
}
