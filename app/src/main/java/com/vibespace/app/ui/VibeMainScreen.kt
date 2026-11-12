package com.vibespace.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.components.SplitOrientation
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.components.VibeSplitLayout
import com.vibespace.core.designsystem.layout.WindowWidthSizeClass
import com.vibespace.core.designsystem.layout.rememberVibeAdaptiveInfo
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.feature.aichat.AIChatScreen
import com.vibespace.feature.aichat.AIChatViewModel
import com.vibespace.feature.editor.EditorScreen
import com.vibespace.feature.editor.EditorViewModel
import com.vibespace.feature.explorer.ExplorerScreen
import com.vibespace.feature.explorer.ExplorerViewModel
import com.vibespace.feature.search.CommandPaletteDialog
import com.vibespace.feature.search.SearchPanel
import com.vibespace.feature.search.SearchViewModel
import com.vibespace.feature.terminal.TerminalPanel
import com.vibespace.feature.terminal.TerminalViewModel
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeMainScreen(
    editorViewModel: EditorViewModel,
    explorerViewModel: ExplorerViewModel,
    terminalViewModel: TerminalViewModel,
    aiChatViewModel: AIChatViewModel,
    searchViewModel: SearchViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToggleLineNumbers: (() -> Unit)? = null,
    gitViewModel: com.vibespace.feature.explorer.git.GitViewModel? = null,
    onNavigateToExtensions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val adaptiveInfo = rememberVibeAdaptiveInfo()
    val isTablet = adaptiveInfo.isTabletOrExpanded

    val editorUiState by editorViewModel.uiState.collectAsState()
    LaunchedEffect(editorUiState.diagnostics) {
        terminalViewModel.updateDiagnostics(editorUiState.diagnostics)
    }

    var isTerminalVisible by remember { mutableStateOf(false) }
    var isAiChatVisible by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var isExplorerVisibleOnTablet by remember { mutableStateOf(true) }
    var isSourceControlVisible by remember { mutableStateOf(false) }
    var phoneDrawerTab by remember { mutableStateOf(0) } // 0: Explorer, 1: Git

    val isExpanded = adaptiveInfo.widthSizeClass == WindowWidthSizeClass.Expanded
    var splitOrientation by remember {
        mutableStateOf(if (isExpanded) SplitOrientation.HORIZONTAL else SplitOrientation.VERTICAL)
    }
    var splitRatio by remember {
        mutableFloatStateOf(if (isExpanded) 0.55f else 0.65f)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isCommandPaletteVisible by remember { mutableStateOf(false) }

    val rootModifier = modifier
        .fillMaxSize()
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                    when (keyEvent.key) {
                        Key.P -> {
                            isCommandPaletteVisible = true
                            true
                        }
                        Key.S -> {
                            editorViewModel.saveActiveDocument()
                            gitViewModel?.refreshStatus()
                            true
                        }
                        Key.F -> {
                            isSearchVisible = !isSearchVisible
                            true
                        }
                        Key.Enter -> {
                            val active = editorViewModel.uiState.value.activeDocument
                            if (active != null) {
                                isTerminalVisible = true
                                editorViewModel.saveActiveDocument { saved ->
                                    if (saved) {
                                        gitViewModel?.refreshStatus()
                                        terminalViewModel.runActiveDocument(active.fileName, active.language.id, active.uri)
                                    }
                                }
                            }
                            true
                        }
                        Key.W -> {
                            val active = editorViewModel.uiState.value.activeDocument
                            if (active != null) {
                                editorViewModel.closeTab(active.documentId)
                            }
                            true
                        }
                        else -> false
                    }
                } else if (keyEvent.isShiftPressed && keyEvent.isAltPressed && keyEvent.key == Key.F) {
                    editorViewModel.formatDocument()
                    true
                } else if (keyEvent.key == Key.F12) {
                    editorViewModel.requestDefinition(onNavigateToFile = { editorViewModel.openFile(it) })
                    true
                } else false
            } else false
        }

    if (isTablet) {
        // Tablet / Large Screen Layout: Navigation Rail + Sidebar + Editor + Dockable Terminal & AI
        Row(
            modifier = rootModifier
                .background(VibeTheme.colors.editorBackground)
        ) {
            // Left Navigation Rail
            NavigationRail(
                containerColor = VibeTheme.colors.gutterBackground,
                contentColor = VibeTheme.colors.textSecondary,
                modifier = Modifier.width(56.dp)
            ) {
                NavigationRailItem(
                    selected = false,
                    onClick = onNavigateToHome,
                    icon = { Icon(VibeIcons.Home, contentDescription = "Home", modifier = Modifier.size(20.dp)) },
                    label = { Text("Home") }
                )
                NavigationRailItem(
                    selected = false,
                    onClick = { isCommandPaletteVisible = true },
                    icon = { Icon(VibeIcons.Search, contentDescription = "Palette", modifier = Modifier.size(20.dp)) },
                    label = { Text("Palette") }
                )
                NavigationRailItem(
                    selected = isExplorerVisibleOnTablet,
                    onClick = {
                        isExplorerVisibleOnTablet = !isExplorerVisibleOnTablet
                        if (isExplorerVisibleOnTablet) isSourceControlVisible = false
                    },
                    icon = { Icon(VibeIcons.Explorer, contentDescription = "Explorer", modifier = Modifier.size(20.dp)) },
                    label = { Text("Files") }
                )
                NavigationRailItem(
                    selected = isSourceControlVisible,
                    onClick = {
                        isSourceControlVisible = !isSourceControlVisible
                        if (isSourceControlVisible) isExplorerVisibleOnTablet = false
                    },
                    icon = { Icon(VibeIcons.SourceControl, contentDescription = "Git", modifier = Modifier.size(20.dp)) },
                    label = { Text("Git") }
                )
                NavigationRailItem(
                    selected = isSearchVisible,
                    onClick = { isSearchVisible = !isSearchVisible },
                    icon = { Icon(VibeIcons.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                    label = { Text("Search") }
                )
                NavigationRailItem(
                    selected = isTerminalVisible,
                    onClick = { isTerminalVisible = !isTerminalVisible },
                    icon = { Icon(VibeIcons.Terminal, contentDescription = "Terminal", modifier = Modifier.size(20.dp)) },
                    label = { Text("Terminal") }
                )
                if (isTerminalVisible) {
                    NavigationRailItem(
                        selected = splitOrientation == SplitOrientation.HORIZONTAL,
                        onClick = {
                            splitOrientation = if (splitOrientation == SplitOrientation.HORIZONTAL) {
                                SplitOrientation.VERTICAL
                            } else {
                                SplitOrientation.HORIZONTAL
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (splitOrientation == SplitOrientation.HORIZONTAL) VibeIcons.SplitHorizontal else VibeIcons.SplitVertical,
                                contentDescription = if (splitOrientation == SplitOrientation.HORIZONTAL) "Side by Side Split" else "Stacked Split",
                                tint = if (splitOrientation == SplitOrientation.HORIZONTAL) MaterialTheme.colorScheme.primary else VibeTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = { Text(if (splitOrientation == SplitOrientation.HORIZONTAL) "Side" else "Stack") }
                    )
                }
                NavigationRailItem(
                    selected = isAiChatVisible,
                    onClick = { isAiChatVisible = !isAiChatVisible },
                    icon = { Icon(VibeIcons.AI, contentDescription = "AI", modifier = Modifier.size(20.dp)) },
                    label = { Text("AI") }
                )

                Spacer(modifier = Modifier.weight(1f))

                if (onNavigateToExtensions != null) {
                    NavigationRailItem(
                        selected = false,
                        onClick = onNavigateToExtensions,
                        icon = { Icon(VibeIcons.Extension, contentDescription = "Extensions", modifier = Modifier.size(20.dp)) },
                        label = { Text("Plugins") }
                    )
                }

                NavigationRailItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(VibeIcons.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) },
                    label = { Text("Config") }
                )
            }

            // Optional Explorer or Git Sidebar (260dp-280dp)
            if (isExplorerVisibleOnTablet) {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .border(1.dp, VibeTheme.colors.borderSubtle)
                ) {
                    ExplorerScreen(
                        viewModel = explorerViewModel,
                        onOpenFile = { file ->
                            editorViewModel.openFile(file)
                        }
                    )
                }
            } else if (isSourceControlVisible && gitViewModel != null) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .border(1.dp, VibeTheme.colors.borderSubtle)
                ) {
                    com.vibespace.feature.explorer.git.GitSourceControlPanel(
                        viewModel = gitViewModel,
                        onClose = { isSourceControlVisible = false }
                    )
                }
            }

            // Central Area: Editor + Bottom Terminal Panel + Search overlay
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Search panel overlay if active
                if (isSearchVisible) {
                    SearchPanel(
                        viewModel = searchViewModel,
                        onClose = { isSearchVisible = false },
                        onOpenResult = { file, line ->
                            editorViewModel.openFile(file, line)
                            isSearchVisible = false
                        }
                    )
                }

                // Resizable Dual Split View (Side-by-side or stacked)
                if (isTerminalVisible) {
                    VibeSplitLayout(
                        splitRatio = splitRatio,
                        onSplitRatioChange = { splitRatio = it },
                        orientation = splitOrientation,
                        modifier = Modifier.weight(1f),
                        firstContent = {
                            EditorScreen(
                                viewModel = editorViewModel,
                                onSearchClick = { isSearchVisible = !isSearchVisible },
                                onRunClick = {
                                    isTerminalVisible = true
                                    val active = editorViewModel.uiState.value.activeDocument
                                    if (active != null) {
                                        editorViewModel.saveActiveDocument { saved ->
                                            if (saved) {
                                                gitViewModel?.refreshStatus()
                                                terminalViewModel.runActiveDocument(
                                                    fileName = active.fileName,
                                                    languageId = active.language.id,
                                                    documentUri = active.uri
                                                )
                                            }
                                        }
                                    }
                                },
                                onAiAssistantClick = { isAiChatVisible = !isAiChatVisible },
                                onToggleTerminalClick = { isTerminalVisible = !isTerminalVisible },
                                onOpenExplorerClick = { isExplorerVisibleOnTablet = true }
                            )
                        },
                        secondContent = {
                            TerminalPanel(
                                viewModel = terminalViewModel,
                                onClosePanel = { isTerminalVisible = false },
                                onJumpToDiagnosticLine = { targetLine ->
                                    val doc = editorViewModel.uiState.value.activeDocument
                                    if (doc != null) {
                                        val safeLine = targetLine.coerceIn(1, doc.content.count { it == '\n' } + 1)
                                        val offset = doc.content.lineSequence().take(safeLine - 1).sumOf { it.length + 1 }.coerceAtMost(doc.content.length)
                                        editorViewModel.onCursorPositionChanged(
                                            com.vibespace.core.editor.model.CursorPosition(safeLine, 1, offset)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        EditorScreen(
                            viewModel = editorViewModel,
                            onSearchClick = { isSearchVisible = !isSearchVisible },
                            onRunClick = {
                                isTerminalVisible = true
                                val active = editorViewModel.uiState.value.activeDocument
                                if (active != null) {
                                    editorViewModel.saveActiveDocument { saved ->
                                        if (saved) {
                                            gitViewModel?.refreshStatus()
                                            terminalViewModel.runActiveDocument(
                                                fileName = active.fileName,
                                                languageId = active.language.id,
                                                documentUri = active.uri
                                            )
                                        }
                                    }
                                }
                            },
                            onAiAssistantClick = { isAiChatVisible = !isAiChatVisible },
                            onToggleTerminalClick = { isTerminalVisible = !isTerminalVisible },
                            onOpenExplorerClick = { isExplorerVisibleOnTablet = true }
                        )
                    }
                }
            }

            // Right side AI Companion Panel (320dp)
            if (isAiChatVisible) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .border(1.dp, VibeTheme.colors.borderSubtle)
                ) {
                    AIChatScreen(
                        viewModel = aiChatViewModel,
                        onClose = { isAiChatVisible = false },
                        onRunInTerminal = { command ->
                            terminalViewModel.executeCommand(command)
                            isTerminalVisible = true
                        },
                        onOpenSettings = onNavigateToSettings
                    )
                }
            }
        }
    } else {
        // Mobile / Compact Phone Layout: Drawer for Explorer, Editor Canvas, Bottom Sheet / Drawer for Terminal and AI
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = VibeTheme.colors.gutterBackground,
                    modifier = Modifier.width(300.dp)
                ) {
                    // Drawer Header with Tabs: Files vs Git
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(VibeTheme.colors.panelBackground)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (phoneDrawerTab == 0) VibeTheme.colors.activeLineBackground else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clickable { phoneDrawerTab = 0 }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Files",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (phoneDrawerTab == 0) VibeTheme.colors.accentPrimary else VibeTheme.colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (phoneDrawerTab == 1) VibeTheme.colors.activeLineBackground else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clickable { phoneDrawerTab = 1 }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Git",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (phoneDrawerTab == 1) VibeTheme.colors.accentPrimary else VibeTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    if (phoneDrawerTab == 0) {
                        ExplorerScreen(
                            viewModel = explorerViewModel,
                            onOpenFile = { file ->
                                editorViewModel.openFile(file)
                                scope.launch { drawerState.close() }
                            }
                        )
                    } else if (gitViewModel != null) {
                        com.vibespace.feature.explorer.git.GitSourceControlPanel(
                            viewModel = gitViewModel,
                            onClose = { scope.launch { drawerState.close() } }
                        )
                    }
                }
            }
        ) {
            Column(
                modifier = rootModifier
                    .background(VibeTheme.colors.editorBackground)
            ) {
                // Top Navigation bar for Phone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(VibeTheme.colors.gutterBackground)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToHome,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(VibeIcons.Home, contentDescription = "Home", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(VibeIcons.Explorer, contentDescription = "Explorer", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    Text(
                        text = "VibeSpace",
                        style = MaterialTheme.typography.titleMedium,
                        color = VibeTheme.colors.textPrimary,
                        modifier = Modifier.padding(start = 6.dp).weight(1f)
                    )

                    IconButton(
                        onClick = { isCommandPaletteVisible = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(VibeIcons.Search, contentDescription = "Command Palette", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { isAiChatVisible = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(VibeIcons.AI, contentDescription = "AI Assistant", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }

                    if (onNavigateToExtensions != null) {
                        IconButton(
                            onClick = onNavigateToExtensions,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(VibeIcons.Extension, contentDescription = "Extensions", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(VibeIcons.Settings, contentDescription = "Settings", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                // Search Overlay if active
                if (isSearchVisible) {
                    SearchPanel(
                        viewModel = searchViewModel,
                        onClose = { isSearchVisible = false },
                        onOpenResult = { file, line ->
                            editorViewModel.openFile(file, line)
                            isSearchVisible = false
                        }
                    )
                }

                // Main Editor
                Box(modifier = Modifier.weight(1f)) {
                    EditorScreen(
                        viewModel = editorViewModel,
                        onSearchClick = { isSearchVisible = !isSearchVisible },
                        onRunClick = {
                            isTerminalVisible = true
                            val active = editorViewModel.uiState.value.activeDocument
                            if (active != null) {
                                editorViewModel.saveActiveDocument { saved ->
                                    if (saved) {
                                        gitViewModel?.refreshStatus()
                                        terminalViewModel.runActiveDocument(
                                            fileName = active.fileName,
                                            languageId = active.language.id,
                                            documentUri = active.uri
                                        )
                                    }
                                }
                            }
                        },
                        onAiAssistantClick = { isAiChatVisible = true },
                        onToggleTerminalClick = { isTerminalVisible = !isTerminalVisible },
                        onOpenExplorerClick = { scope.launch { drawerState.open() } }
                    )
                }

                // Collapsible Terminal on Phone
                AnimatedVisibility(
                    visible = isTerminalVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    TerminalPanel(
                        viewModel = terminalViewModel,
                        onClosePanel = { isTerminalVisible = false },
                        onJumpToDiagnosticLine = { targetLine ->
                            val doc = editorViewModel.uiState.value.activeDocument
                            if (doc != null) {
                                val safeLine = targetLine.coerceIn(1, doc.content.count { it == '\n' } + 1)
                                val offset = doc.content.lineSequence().take(safeLine - 1).sumOf { it.length + 1 }.coerceAtMost(doc.content.length)
                                editorViewModel.onCursorPositionChanged(
                                    com.vibespace.core.editor.model.CursorPosition(safeLine, 1, offset)
                                )
                            }
                        }
                    )
                }
            }

            // AI Assistant Bottom Sheet on Phone
            if (isAiChatVisible) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { isAiChatVisible = false },
                    sheetState = sheetState,
                    containerColor = VibeTheme.colors.panelBackground
                ) {
                    Box(modifier = Modifier.height(500.dp)) {
                        AIChatScreen(
                            viewModel = aiChatViewModel,
                            onClose = { isAiChatVisible = false },
                            onRunInTerminal = { command ->
                                terminalViewModel.executeCommand(command)
                                isTerminalVisible = true
                            },
                            onOpenSettings = onNavigateToSettings
                        )
                    }
                }
            }
        }
    }

    if (isCommandPaletteVisible) {
        val explorerUiState by explorerViewModel.uiState.collectAsState()
        val allFiles = explorerUiState.allProjectFiles.ifEmpty { explorerUiState.rootNodes.map { it.file } }
        CommandPaletteDialog(
            files = allFiles,
            hasActiveDocument = editorUiState.activeDocument != null,
            onOpenFile = { file ->
                editorViewModel.openFile(file)
                isCommandPaletteVisible = false
            },
            onRunScript = {
                val active = editorViewModel.uiState.value.activeDocument
                if (active != null) {
                    editorViewModel.saveActiveDocument { saved ->
                        if (saved) {
                            gitViewModel?.refreshStatus()
                            terminalViewModel.runActiveDocument(active.fileName, active.language.id, active.uri)
                            isTerminalVisible = true
                        }
                    }
                }
                isCommandPaletteVisible = false
            },
            onSaveFile = {
                editorViewModel.saveActiveDocument()
                gitViewModel?.refreshStatus()
                isCommandPaletteVisible = false
            },
            onOpenSearch = {
                isSearchVisible = true
                isCommandPaletteVisible = false
            },
            onOpenAi = {
                isAiChatVisible = true
                isCommandPaletteVisible = false
            },
            onOpenSettings = {
                onNavigateToSettings()
                isCommandPaletteVisible = false
            },
            onToggleTerminal = {
                isTerminalVisible = !isTerminalVisible
                isCommandPaletteVisible = false
            },
            onToggleLineNumbers = onToggleLineNumbers?.let { toggle ->
                {
                    toggle()
                    isCommandPaletteVisible = false
                }
            },
            onFormatDocument = if (editorViewModel.hasRunningLanguageServer) ({
                editorViewModel.formatDocument()
                isCommandPaletteVisible = false
            }) else null,
            onShowHover = if (editorViewModel.hasRunningLanguageServer) ({
                editorViewModel.requestHover()
                isCommandPaletteVisible = false
            }) else null,
            onGoToDefinition = if (editorViewModel.hasRunningLanguageServer) ({
                editorViewModel.requestDefinition(onNavigateToFile = { editorViewModel.openFile(it) })
                isCommandPaletteVisible = false
            }) else null,
            onDismiss = { isCommandPaletteVisible = false }
        )
    }
}
