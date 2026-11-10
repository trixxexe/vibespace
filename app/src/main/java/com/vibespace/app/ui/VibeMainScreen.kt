package com.vibespace.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.components.VibeIcons
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
import com.vibespace.feature.search.SearchPanel
import com.vibespace.feature.search.SearchViewModel
import com.vibespace.feature.terminal.TerminalPanel
import com.vibespace.feature.terminal.TerminalViewModel
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
    modifier: Modifier = Modifier
) {
    val adaptiveInfo = rememberVibeAdaptiveInfo()
    val isTablet = adaptiveInfo.isTabletOrExpanded

    var isTerminalVisible by remember { mutableStateOf(false) }
    var isAiChatVisible by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var isExplorerVisibleOnTablet by remember { mutableStateOf(true) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (isTablet) {
        // Tablet / Large Screen Layout: Navigation Rail + Sidebar + Editor + Dockable Terminal & AI
        Row(
            modifier = modifier
                .fillMaxSize()
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
                    label = { Text("Home", style = VibeTheme.colors.textMuted.let { androidx.compose.ui.text.TextStyle() }) }
                )
                NavigationRailItem(
                    selected = isExplorerVisibleOnTablet,
                    onClick = { isExplorerVisibleOnTablet = !isExplorerVisibleOnTablet },
                    icon = { Icon(VibeIcons.Explorer, contentDescription = "Explorer", modifier = Modifier.size(20.dp)) },
                    label = { Text("Files") }
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
                NavigationRailItem(
                    selected = isAiChatVisible,
                    onClick = { isAiChatVisible = !isAiChatVisible },
                    icon = { Icon(VibeIcons.AI, contentDescription = "AI", modifier = Modifier.size(20.dp)) },
                    label = { Text("AI") }
                )

                Spacer(modifier = Modifier.weight(1f))

                NavigationRailItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(VibeIcons.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) },
                    label = { Text("Config") }
                )
            }

            // Optional Explorer Sidebar (260dp)
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
                        onClose = { isSearchVisible = false }
                    )
                }

                // Editor Canvas
                Box(modifier = Modifier.weight(1f)) {
                    EditorScreen(
                        viewModel = editorViewModel,
                        onSearchClick = { isSearchVisible = !isSearchVisible },
                        onRunClick = {
                            isTerminalVisible = true
                            terminalViewModel.submitCommand()
                        },
                        onAiAssistantClick = { isAiChatVisible = !isAiChatVisible },
                        onToggleTerminalClick = { isTerminalVisible = !isTerminalVisible },
                        onOpenExplorerClick = { isExplorerVisibleOnTablet = true }
                    )
                }

                // Bottom Dockable Terminal
                if (isTerminalVisible) {
                    TerminalPanel(
                        viewModel = terminalViewModel,
                        onClosePanel = { isTerminalVisible = false }
                    )
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
                        onClose = { isAiChatVisible = false }
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
                    ExplorerScreen(
                        viewModel = explorerViewModel,
                        onOpenFile = { file ->
                            editorViewModel.openFile(file)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(VibeTheme.colors.editorBackground)
            ) {
                // Top Navigation bar for Phone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(VibeTheme.colors.gutterBackground)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToHome,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(VibeIcons.Home, contentDescription = "Home", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(VibeIcons.Explorer, contentDescription = "Explorer", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = "VibeSpace",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        color = VibeTheme.colors.textPrimary,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )

                    IconButton(
                        onClick = { isAiChatVisible = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(VibeIcons.AI, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(VibeIcons.Settings, contentDescription = "Settings", tint = VibeTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Search Overlay if active
                if (isSearchVisible) {
                    SearchPanel(
                        viewModel = searchViewModel,
                        onClose = { isSearchVisible = false }
                    )
                }

                // Main Editor
                Box(modifier = Modifier.weight(1f)) {
                    EditorScreen(
                        viewModel = editorViewModel,
                        onSearchClick = { isSearchVisible = !isSearchVisible },
                        onRunClick = { isTerminalVisible = true },
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
                        onClosePanel = { isTerminalVisible = false }
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
                            onClose = { isAiChatVisible = false }
                        )
                    }
                }
            }
        }
    }
}
