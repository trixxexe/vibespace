package com.vibespace.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vibespace.core.designsystem.components.VibeEmptyState
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.components.VibeStatusBar
import com.vibespace.core.designsystem.components.VibeTabBar
import com.vibespace.core.designsystem.components.VibeTabItem
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.language.snippets.SnippetRepository
import com.vibespace.feature.editor.components.EditorToolbar
import com.vibespace.feature.editor.components.MobileSymbolBar
import com.vibespace.feature.editor.components.SnippetCompletionBar
import com.vibespace.feature.editor.components.SoraEditorCanvas
import com.vibespace.feature.editor.components.SoraEditorController
import com.vibespace.feature.editor.components.TemplateBottomSheet

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onSearchClick: () -> Unit,
    onRunClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onToggleTerminalClick: () -> Unit,
    onOpenExplorerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeDoc = uiState.activeDocument
    val soraController = remember { SoraEditorController() }
    var showTemplateSheet by remember { mutableStateOf(false) }

    val currentLanguageId = activeDoc?.language?.id ?: "text"
    val languageDisplayName = activeDoc?.language?.displayName ?: "Plain Text"
    val suggestions = remember(currentLanguageId) {
        SnippetRepository.getSnippetsForLanguage(currentLanguageId)
    }

    if (showTemplateSheet) {
        TemplateBottomSheet(
            languageName = languageDisplayName,
            snippets = suggestions,
            onSelectSnippet = { soraController.insertSymbol(it) },
            onDismiss = { showTemplateSheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VibeTheme.colors.editorBackground)
    ) {
        // Multi-tab bar
        if (uiState.openDocuments.isNotEmpty()) {
            VibeTabBar(
                tabs = uiState.openDocuments.map { doc ->
                    VibeTabItem(
                        id = doc.documentId,
                        title = doc.fileName,
                        isDirty = doc.isDirty
                    )
                },
                activeTabId = activeDoc?.documentId,
                onSelectTab = { viewModel.switchActiveTab(it) },
                onCloseTab = { viewModel.closeTab(it) }
            )
        }

        // Toolbar
        EditorToolbar(
            onSaveClick = { viewModel.saveActiveDocument() },
            onUndoClick = {
                soraController.undo()
                viewModel.undo()
            },
            onRedoClick = {
                soraController.redo()
                viewModel.redo()
            },
            onSearchClick = onSearchClick,
            onRunClick = onRunClick,
            onAiAssistantClick = onAiAssistantClick,
            onToggleTerminalClick = onToggleTerminalClick,
            isDocumentDirty = activeDoc?.isDirty ?: false
        )

        // Main Editor Surface or Empty State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (activeDoc != null) {
                SoraEditorCanvas(
                    content = activeDoc.content,
                    language = activeDoc.language,
                    fontSizeSp = uiState.fontSizeSp,
                    theme = com.vibespace.core.editor.theme.EditorThemeDefinition.fromId(uiState.themeId),
                    onContentChange = { viewModel.onContentChanged(it) },
                    onCursorChange = { viewModel.onCursorPositionChanged(it) },
                    controller = soraController
                )
            } else {
                VibeEmptyState(
                    icon = VibeIcons.Editor,
                    title = "No Document Open",
                    subtitle = "Select a file from the Project Explorer or create a new file to begin editing.",
                    actionButtonText = "Open Explorer",
                    onActionClick = onOpenExplorerClick
                )
            }
        }

        // Mobile Predictive Snippet Bar
        if (activeDoc != null && suggestions.isNotEmpty()) {
            SnippetCompletionBar(
                suggestions = suggestions,
                onSelectSnippet = { soraController.insertSymbol(it) },
                onOpenTemplates = { showTemplateSheet = true }
            )
        }

        // Mobile Virtual Symbol Accessory Bar
        if (activeDoc != null) {
            MobileSymbolBar(
                onInsertSymbol = { soraController.insertSymbol(it) },
                onMoveCursorLeft = { soraController.moveCursorLeft() },
                onMoveCursorRight = { soraController.moveCursorRight() },
                onUndo = { soraController.undo() },
                onRedo = { soraController.redo() },
                modifier = Modifier.imePadding()
            )
        }

        // Bottom Status Bar
        VibeStatusBar(
            line = uiState.cursorPosition.line,
            column = uiState.cursorPosition.column,
            language = activeDoc?.language?.displayName ?: "Plain Text",
            encoding = "UTF-8",
            indentation = "Spaces: 4",
            statusMessage = uiState.statusMessage
        )
    }
}
