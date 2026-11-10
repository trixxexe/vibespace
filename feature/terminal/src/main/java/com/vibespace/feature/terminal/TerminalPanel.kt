package com.vibespace.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.model.TerminalLine

@Composable
fun TerminalPanel(
    viewModel: TerminalViewModel,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val currentLines = uiState.activeSession?.lines ?: emptyList()

    LaunchedEffect(currentLines.size) {
        if (currentLines.isNotEmpty()) {
            listState.animateScrollToItem(currentLines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(VibeTheme.colors.panelBackground)
    ) {
        // Tab Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 8.dp),
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
                title = "PROBLEMS (0)",
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
                    modifier = Modifier.padding(end = 6.dp)
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
                            imageVector = Icons.Default.ArrowDropDown,
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

            IconButton(
                onClick = { viewModel.clearActiveSession() },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear terminal",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(
                onClick = onClosePanel,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
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
                    // Lines list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        items(currentLines, key = { it.id }) { line ->
                            TerminalLineView(line)
                        }
                    }

                    if (uiState.activeRunnerMode == com.vibespace.core.runner.model.RunnerMode.ALPINE && !uiState.isAlpineInstalled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VibeTheme.colors.activeLineBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { viewModel.installAlpineSubsystem() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🐧", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Set Up Alpine Linux Subsystem",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = VibeTheme.colors.accentPrimary
                                    )
                                    Text(
                                        text = "Tap to initialize in-app PRoot container for pip, npm, and apk (~60MB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VibeTheme.colors.textMuted
                                    )
                                }
                            }
                        }
                    }

                    // Input bar
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
                            style = VibeCodeTypography,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))

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
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { viewModel.submitCommand() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send command",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            BottomPanelTab.OUTPUT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Build output and task executions will be streamed here.\nNo tasks currently running.",
                        style = VibeCodeTypography,
                        color = VibeTheme.colors.textSecondary
                    )
                }
            }
            BottomPanelTab.PROBLEMS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "No problems detected in workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.statusSuccess
                    )
                }
            }
            BottomPanelTab.CONSOLE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "VibeSpace System Console ready.\nAll modules initialized successfully.",
                        style = VibeCodeTypography,
                        color = VibeTheme.colors.textSecondary
                    )
                }
            }
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
            .padding(horizontal = 8.dp, vertical = 6.dp)
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
    val color = when (line.streamType) {
        StreamType.COMMAND -> VibeTheme.colors.textPrimary
        StreamType.STDOUT -> VibeTheme.colors.textSecondary
        StreamType.STDERR -> VibeTheme.colors.statusError
        StreamType.SYSTEM -> VibeTheme.colors.accentSecondary
    }

    Text(
        text = line.text,
        style = VibeCodeTypography.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = color,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
