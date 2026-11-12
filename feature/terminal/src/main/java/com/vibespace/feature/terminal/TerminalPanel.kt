package com.vibespace.feature.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.runner.alpine.AlpineInstallationState
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.terminal.ansi.AnsiParser
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.model.TerminalLine

@Composable
fun TerminalPanel(
    viewModel: TerminalViewModel,
    onClosePanel: () -> Unit,
    onJumpToDiagnosticLine: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val terminalListState = rememberLazyListState()
    val outputListState = rememberLazyListState()
    val consoleListState = rememberLazyListState()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }

    val currentLines = uiState.activeSession?.lines ?: emptyList()

    LaunchedEffect(currentLines.size) {
        if (currentLines.isNotEmpty()) {
            terminalListState.animateScrollToItem(currentLines.size - 1)
        }
    }

    LaunchedEffect(uiState.outputLines.size) {
        if (uiState.outputLines.isNotEmpty()) {
            outputListState.animateScrollToItem(uiState.outputLines.size - 1)
        }
    }

    LaunchedEffect(uiState.consoleLines.size) {
        if (uiState.consoleLines.isNotEmpty()) {
            consoleListState.animateScrollToItem(uiState.consoleLines.size - 1)
        }
    }

    val isStandaloneMobilePanel = modifier == Modifier
    var panelHeightDp by remember { mutableFloatStateOf(280f) }
    var isMaximized by remember { mutableStateOf(false) }
    var isDraggingSash by remember { mutableStateOf(false) }

    val effectiveHeightDp = if (isMaximized) 560.dp else panelHeightDp.dp
    val panelModifier = if (isStandaloneMobilePanel) {
        Modifier
            .fillMaxWidth()
            .height(effectiveHeightDp)
    } else {
        modifier
    }

    Column(
        modifier = panelModifier
            .background(VibeTheme.colors.panelBackground)
    ) {
        // VS Code-style Draggable Top Resize Sash (on mobile stacked layout)
        if (isStandaloneMobilePanel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        if (isDraggingSash) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else VibeTheme.colors.gutterBackground
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                isMaximized = !isMaximized
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { isDraggingSash = true },
                            onDragEnd = { isDraggingSash = false },
                            onDragCancel = { isDraggingSash = false },
                            onVerticalDrag = { change, dragAmountPx ->
                                change.consume()
                                val deltaDp = with(density) { dragAmountPx.toDp().value }
                                isMaximized = false
                                panelHeightDp = (panelHeightDp - deltaDp).coerceIn(140f, 640f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Top border accent line + subtle pill grip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            if (isDraggingSash) MaterialTheme.colorScheme.primary
                            else VibeTheme.colors.borderSubtle
                        )
                )
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .background(
                            color = if (isDraggingSash) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        // VS Code Tab Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelTabButton(
                title = "TERMINAL",
                isSelected = uiState.activeTab == BottomPanelTab.TERMINAL,
                onClick = { viewModel.selectTab(BottomPanelTab.TERMINAL) }
            )
            PanelTabButton(
                title = "OUTPUT",
                isSelected = uiState.activeTab == BottomPanelTab.OUTPUT,
                onClick = { viewModel.selectTab(BottomPanelTab.OUTPUT) }
            )
            PanelTabButton(
                title = "PROBLEMS (${uiState.problemsCount})",
                isSelected = uiState.activeTab == BottomPanelTab.PROBLEMS,
                onClick = { viewModel.selectTab(BottomPanelTab.PROBLEMS) }
            )
            PanelTabButton(
                title = "CONSOLE",
                isSelected = uiState.activeTab == BottomPanelTab.CONSOLE,
                onClick = { viewModel.selectTab(BottomPanelTab.CONSOLE) }
            )

            Spacer(modifier = Modifier.weight(1f))

            var isRunnerMenuExpanded by remember { mutableStateOf(false) }

            Box {
                Surface(
                    onClick = { isRunnerMenuExpanded = true },
                    shape = RoundedCornerShape(4.dp),
                    color = VibeTheme.colors.activeLineBackground,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.activeRunnerMode.shortBadge,
                            style = VibeCodeTypography.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibeTheme.colors.accentPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = VibeIcons.ArrowDown,
                            contentDescription = "Switch Runner Mode",
                            tint = VibeTheme.colors.accentPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isRunnerMenuExpanded,
                    onDismissRequest = { isRunnerMenuExpanded = false }
                ) {
                    uiState.availableRunnerModes.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (mode == uiState.activeRunnerMode) FontWeight.Bold else FontWeight.Normal,
                                            color = if (mode == uiState.activeRunnerMode) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary
                                        )
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VibeTheme.colors.textMuted
                                    )
                                }
                            },
                            onClick = {
                                viewModel.switchRunnerMode(mode)
                                isRunnerMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Clear button (uses Trash/Delete icon so it is distinct from Close X)
            IconButton(
                onClick = { viewModel.clearActiveSession() },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.Delete,
                    contentDescription = "Clear panel output",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            // VS Code Maximize / Restore Panel Height button on mobile
            if (isStandaloneMobilePanel) {
                IconButton(
                    onClick = { isMaximized = !isMaximized },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isMaximized) VibeIcons.ArrowDown else VibeIcons.ArrowUp,
                        contentDescription = if (isMaximized) "Restore panel size" else "Maximize panel size",
                        tint = VibeTheme.colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            IconButton(
                onClick = onClosePanel,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.Close,
                    contentDescription = "Close panel",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Panel Body
        when (uiState.activeTab) {
            BottomPanelTab.TERMINAL -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Multiple Terminal Sessions Bar + Active Working Directory indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(VibeTheme.colors.gutterBackground)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(uiState.sessions, key = { index, session -> "${session.sessionId}_$index" }) { _, session ->
                                val isSelected = session.sessionId == uiState.activeSession?.sessionId
                                Surface(
                                    onClick = { viewModel.switchSession(session.sessionId) },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) VibeTheme.colors.panelBackground else Color.Transparent,
                                    border = BorderStroke(1.dp, if (isSelected) VibeTheme.colors.borderSubtle else Color.Transparent),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = session.title,
                                            style = VibeCodeTypography.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) VibeTheme.colors.accentPrimary else VibeTheme.colors.textSecondary
                                            )
                                        )
                                        if (uiState.sessions.size > 1 && isSelected) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = VibeIcons.Close,
                                                contentDescription = "Close session",
                                                tint = VibeTheme.colors.textMuted,
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clickable { viewModel.closeSession(session.sessionId) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.createNewSession() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = VibeIcons.Add,
                                contentDescription = "New Terminal",
                                tint = VibeTheme.colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Terminal Lines list
                    LazyColumn(
                        state = terminalListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable {
                                runCatching { inputFocusRequester.requestFocus() }
                                keyboardController?.show()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        itemsIndexed(currentLines, key = { index, line -> "${line.id}_$index" }) { _, line ->
                            TerminalLineView(line)
                        }
                    }

                    if (uiState.activeRunnerMode == RunnerMode.ALPINE && !uiState.isAlpineInstalled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VibeTheme.colors.activeLineBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            when (val state = uiState.alpineInstallationState) {
                                is AlpineInstallationState.Downloading -> {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = VibeIcons.RunnerAlpine,
                                                contentDescription = null,
                                                tint = VibeTheme.colors.accentPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Downloading verified Linux runtime (${(state.progress * 100).toInt()}%)...",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = VibeTheme.colors.accentPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = VibeTheme.colors.accentPrimary,
                                            trackColor = VibeTheme.colors.borderSubtle
                                        )
                                    }
                                }
                                is AlpineInstallationState.Extracting -> {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = VibeIcons.Package,
                                                contentDescription = null,
                                                tint = VibeTheme.colors.accentPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Unpacking bundled Alpine rootfs into app storage...",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = VibeTheme.colors.accentPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = VibeTheme.colors.accentPrimary,
                                            trackColor = VibeTheme.colors.borderSubtle
                                        )
                                    }
                                }
                                is AlpineInstallationState.Error -> {
                                    val unsupportedArchitecture = state.message.contains("No PRoot build is available")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (unsupportedArchitecture) Modifier else Modifier.clickable { viewModel.installAlpineSubsystem() })
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = VibeIcons.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (unsupportedArchitecture) "Alpine is unavailable on this device" else "Alpine setup failed: ${state.message}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = if (unsupportedArchitecture) state.message else "Tap to retry setup",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VibeTheme.colors.textMuted
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.installAlpineSubsystem() }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = VibeIcons.RunnerAlpine,
                                            contentDescription = null,
                                            tint = VibeTheme.colors.accentPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Prepare Alpine Python and Node runtime",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = VibeTheme.colors.accentPrimary
                                            )
                                            Text(
                                                text = "Extracts bundled Python, Node.js, and Alpine tools into app storage",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VibeTheme.colors.textMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.activeRunnerMode == RunnerMode.ALPINE && uiState.isAlpineInstalled && !uiState.isLanguageServersReady) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VibeTheme.colors.activeLineBackground,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (uiState.isInstallingLanguageServers) Modifier else Modifier.clickable { viewModel.installLanguageServers() })
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.isInstallingLanguageServers) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(VibeIcons.Package, contentDescription = null, tint = VibeTheme.colors.accentPrimary, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (uiState.isInstallingLanguageServers) "Installing Python and TypeScript LSP servers…" else "Set up Python and TypeScript language servers",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = VibeTheme.colors.accentPrimary
                                    )
                                    Text(
                                        text = uiState.languageServerMessage ?: "Installs real pylsp and typescript-language-server packages into Alpine.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VibeTheme.colors.textMuted
                                    )
                                }
                            }
                        }
                    }

                    // Mobile Terminal Quick-Keys Strip (VS Code Mobile style)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(VibeTheme.colors.gutterBackground)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val quickTokens = listOf("ls -la", "pwd", "cd ..", "python3 ", "node ", "clear", "/", "-", "|", ">", "&&")
                        item {
                            QuickKeyChip(label = "↑") { viewModel.navigateHistoryUp() }
                        }
                        item {
                            QuickKeyChip(label = "↓") { viewModel.navigateHistoryDown() }
                        }
                        itemsIndexed(quickTokens) { _, token ->
                            QuickKeyChip(label = token.trim()) {
                                if (token == "ls -la" || token == "pwd" || token == "cd .." || token == "clear") {
                                    viewModel.executeCommand(token)
                                } else {
                                    viewModel.onCommandInputChange(uiState.commandInput + token)
                                    runCatching { inputFocusRequester.requestFocus() }
                                }
                            }
                        }
                    }

                    // Command Input bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(VibeTheme.colors.gutterBackground)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$",
                            style = VibeCodeTypography.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (uiState.commandInput.isEmpty()) {
                                Text(
                                    text = "Enter Linux command (ls, cd, mkdir, cat, grep, python3, node...)",
                                    style = VibeCodeTypography.copy(
                                        color = VibeTheme.colors.textMuted.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            BasicTextField(
                                value = uiState.commandInput,
                                onValueChange = { viewModel.onCommandInputChange(it) },
                                textStyle = VibeCodeTypography.copy(
                                    color = VibeTheme.colors.textPrimary,
                                    fontSize = 12.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { viewModel.submitCommand() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocusRequester)
                            )
                        }

                        if (uiState.activeRunnerMode == RunnerMode.ALPINE) {
                            IconButton(
                                onClick = { viewModel.interruptTerminalCommand() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = VibeIcons.Interrupt,
                                    contentDescription = "Interrupt current command",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.submitCommand() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = VibeIcons.Send,
                                contentDescription = "Send command",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            BottomPanelTab.OUTPUT -> {
                if (uiState.outputLines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "No code or command output yet. Run a file or execute a terminal command to see its output here.",
                            style = VibeCodeTypography.copy(fontSize = 12.sp),
                            color = VibeTheme.colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = outputListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(uiState.outputLines, key = { index, line -> "${line.id}_$index" }) { _, line ->
                            TerminalLineView(line)
                        }
                    }
                }
            }
            BottomPanelTab.PROBLEMS -> {
                if (uiState.diagnostics.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = VibeIcons.Checkmark,
                                contentDescription = null,
                                tint = VibeTheme.colors.statusSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "No problems have been detected in the workspace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VibeTheme.colors.statusSuccess
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(uiState.diagnostics, key = { index, diag -> "${diag.id}_$index" }) { _, diag ->
                            val isErr = diag.severity == com.vibespace.core.language.model.DiagnosticSeverity.ERROR
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJumpToDiagnosticLine?.invoke(diag.range.start.line)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isErr) VibeIcons.Error else VibeIcons.Warning,
                                    contentDescription = if (isErr) "Error" else "Warning",
                                    tint = if (isErr) VibeTheme.colors.statusError else VibeTheme.colors.statusWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = diag.message,
                                        style = VibeCodeTypography.copy(fontSize = 12.sp),
                                        color = VibeTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "Ln ${diag.range.start.line}, Col ${diag.range.start.column}  •  ${diag.source ?: "syntax"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VibeTheme.colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
            BottomPanelTab.CONSOLE -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact Runtime Status Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VibeTheme.colors.gutterBackground)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Runner: ${uiState.activeRunnerMode.shortBadge}",
                            style = VibeCodeTypography.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = VibeTheme.colors.accentPrimary
                        )
                        Text(
                            text = "Alpine Rootfs: ${if (uiState.isAlpineInstalled) "Ready" else "Extracting"}",
                            style = VibeCodeTypography.copy(fontSize = 11.sp),
                            color = if (uiState.isAlpineInstalled) VibeTheme.colors.statusSuccess else VibeTheme.colors.textSecondary
                        )
                        Text(
                            text = "LSP: ${if (uiState.isLanguageServersReady) "Active" else "Standby"}",
                            style = VibeCodeTypography.copy(fontSize = 11.sp),
                            color = if (uiState.isLanguageServersReady) VibeTheme.colors.statusSuccess else VibeTheme.colors.textMuted
                        )
                    }

                    // Interactive Debug Console Output
                    LazyColumn(
                        state = consoleListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (uiState.consoleLines.isEmpty()) {
                            item {
                                Text(
                                    text = "Interactive Debug Console — evaluate Python/shell expressions below (e.g., 2 + 2, [x**2 for x in range(5)]).",
                                    style = VibeCodeTypography.copy(fontSize = 11.sp),
                                    color = VibeTheme.colors.textMuted
                                )
                            }
                        } else {
                            itemsIndexed(uiState.consoleLines, key = { index, line -> "${line.id}_$index" }) { _, line ->
                                TerminalLineView(line)
                            }
                        }
                    }

                    // Console Expression Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(VibeTheme.colors.gutterBackground)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ">",
                            style = VibeCodeTypography.copy(fontWeight = FontWeight.Bold),
                            color = VibeTheme.colors.accentSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = uiState.consoleInput,
                            onValueChange = { viewModel.onConsoleInputChange(it) },
                            textStyle = VibeCodeTypography.copy(
                                color = VibeTheme.colors.textPrimary,
                                fontSize = 12.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.submitConsoleInput() }),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.submitConsoleInput() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = VibeIcons.Send,
                                contentDescription = "Evaluate expression",
                                tint = VibeTheme.colors.accentSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickKeyChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = VibeTheme.colors.panelBackground,
        border = BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
        modifier = Modifier.height(22.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = VibeTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun PanelTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = fontWeight),
            color = textColor
        )
    }
}

@Composable
private fun TerminalLineView(line: TerminalLine) {
    val defaultColor = when (line.streamType) {
        StreamType.COMMAND -> VibeTheme.colors.textPrimary
        StreamType.STDOUT -> VibeTheme.colors.textSecondary
        StreamType.STDERR -> VibeTheme.colors.statusError
        StreamType.SYSTEM -> VibeTheme.colors.accentSecondary
    }

    val spans = remember(line.text) {
        AnsiParser.parse(line.text)
    }

    val annotatedString = remember(spans, defaultColor) {
        buildAnnotatedString {
            for (span in spans) {
                val spanFgColor = span.style.foregroundColor?.let { Color(it.toInt()) } ?: defaultColor
                val spanBgColor = span.style.backgroundColor?.let { Color(it.toInt()) } ?: Color.Transparent
                val fontWeight = if (span.style.isBold) FontWeight.Bold else FontWeight.Normal
                val fontStyle = if (span.style.isItalic) FontStyle.Italic else FontStyle.Normal
                val textDecoration = when {
                    span.style.isUnderline && span.style.isStrikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    span.style.isUnderline -> TextDecoration.Underline
                    span.style.isStrikethrough -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                }

                val start = length
                append(span.text)
                addStyle(
                    style = SpanStyle(
                        color = spanFgColor,
                        background = spanBgColor,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        textDecoration = textDecoration
                    ),
                    start = start,
                    end = length
                )
            }
        }
    }

    Text(
        text = annotatedString,
        style = VibeCodeTypography.copy(fontSize = 12.sp, lineHeight = 16.sp),
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
