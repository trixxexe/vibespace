package com.vibespace.feature.explorer.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vibespace.core.designsystem.components.VibeDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.git.GitFileStatus
import com.vibespace.core.workspace.git.GitStatusType

@Composable
fun GitSourceControlPanel(
    viewModel: GitViewModel,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var isStagedExpanded by remember { mutableStateOf(true) }
    var isUnstagedExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VibeTheme.colors.panelBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = VibeIcons.SourceControl,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Local Changes",
                style = MaterialTheme.typography.titleSmall,
                color = VibeTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { viewModel.refreshStatus() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.Refresh,
                    contentDescription = "Refresh",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = VibeIcons.Close,
                        contentDescription = "Close",
                        tint = VibeTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Commit Box Section
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Local snapshots only. This workspace is not a Git repository and cannot sync with a remote.",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = uiState.commitMessage,
                onValueChange = { viewModel.onCommitMessageChange(it) },
                placeholder = {
                    Text(
                        text = "Snapshot message...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.errorMessage ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (uiState.syncMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VibeIcons.Checkmark,
                        contentDescription = "Success",
                        tint = VibeTheme.colors.statusSuccess,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = uiState.syncMessage ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeTheme.colors.statusSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.commit() },
                enabled = !uiState.isCommitting && uiState.status.stagedFiles.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (uiState.isCommitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = VibeIcons.Checkmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save snapshot (${uiState.status.stagedFiles.size} staged)")
                }
            }
        }

        // Changes List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Staged Changes Section
            if (uiState.status.stagedFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "STAGED CHANGES",
                        count = uiState.status.stagedFiles.size,
                        isExpanded = isStagedExpanded,
                        onToggle = { isStagedExpanded = !isStagedExpanded },
                        onAction = { viewModel.unstageAll() },
                        actionIcon = VibeIcons.Close,
                        actionTooltip = "Unstage All"
                    )
                }

                if (isStagedExpanded) {
                    items(uiState.status.stagedFiles) { file ->
                        GitFileRow(
                            file = file,
                            onItemClick = { viewModel.openDiff(file.path) },
                            onPrimaryAction = { viewModel.unstageFile(file.path) },
                            primaryIcon = VibeIcons.Close,
                            primaryTooltip = "Unstage"
                        )
                    }
                }
            }

            // Unstaged Changes Section
            item {
                SectionHeader(
                    title = "CHANGES",
                    count = uiState.status.unstagedFiles.size,
                    isExpanded = isUnstagedExpanded,
                    onToggle = { isUnstagedExpanded = !isUnstagedExpanded },
                    onAction = { viewModel.stageAll() },
                    actionIcon = VibeIcons.Add,
                    actionTooltip = "Stage All"
                )
            }

            if (isUnstagedExpanded) {
                if (uiState.status.unstagedFiles.isEmpty() && uiState.status.stagedFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = VibeIcons.Checkmark,
                                    contentDescription = null,
                                    tint = VibeTheme.colors.statusSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Working tree is clean",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VibeTheme.colors.textMuted
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.status.unstagedFiles) { file ->
                        GitFileRow(
                            file = file,
                            onItemClick = { viewModel.openDiff(file.path) },
                            onPrimaryAction = { viewModel.stageFile(file.path) },
                            primaryIcon = VibeIcons.Add,
                            primaryTooltip = "Stage",
                            onSecondaryAction = { viewModel.discardChanges(file.path) },
                            secondaryIcon = VibeIcons.Undo,
                            secondaryTooltip = "Discard Changes"
                        )
                    }
                }
            }
        }
    }

    // Diff Viewer Dialog if active
    if (uiState.activeDiff != null) {
        MobileDiffViewerDialog(
            diff = uiState.activeDiff!!,
            onStageFile = {
                uiState.activeDiffFile?.let { viewModel.stageFile(it) }
            },
            onDismiss = { viewModel.closeDiff() }
        )
    }

    uiState.pendingDiscardPath?.let {
        VibeDialog(
            title = "Discard local changes?",
            onDismissRequest = { viewModel.cancelDiscardChanges() },
            confirmButtonText = "Discard",
            onConfirm = { viewModel.confirmDiscardChanges() },
            dismissButtonText = "Cancel",
            onDismiss = { viewModel.cancelDiscardChanges() }
        ) {
            Text(
                text = "This removes an untracked file or restores a tracked file to its staged or saved content. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAction: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionTooltip: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(VibeTheme.colors.activeLineBackground)
            .clickable { onToggle() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) VibeIcons.ArrowDown else VibeIcons.ArrowRight,
            contentDescription = null,
            tint = VibeTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = VibeTheme.colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = VibeTheme.colors.textMuted,
            modifier = Modifier.padding(end = 4.dp)
        )
        IconButton(
            onClick = onAction,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionTooltip,
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun GitFileRow(
    file: GitFileStatus,
    onItemClick: () -> Unit,
    onPrimaryAction: () -> Unit,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryTooltip: String,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    secondaryTooltip: String? = null
) {
    val statusColor = when (file.statusType) {
        GitStatusType.MODIFIED -> Color(0xFF64B5F6)
        GitStatusType.ADDED -> Color(0xFF81C784)
        GitStatusType.DELETED -> Color(0xFFE57373)
        GitStatusType.UNTRACKED -> Color(0xFF81C784)
    }

    val statusBadge = when (file.statusType) {
        GitStatusType.MODIFIED -> "M"
        GitStatusType.ADDED -> "A"
        GitStatusType.DELETED -> "D"
        GitStatusType.UNTRACKED -> "U"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { onItemClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = VibeTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        // Status Badge (M / A / D / U)
        Text(
            text = statusBadge,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = statusColor,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        if (onSecondaryAction != null && secondaryIcon != null) {
            IconButton(
                onClick = onSecondaryAction,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = secondaryIcon,
                    contentDescription = secondaryTooltip,
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(
            onClick = onPrimaryAction,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = primaryIcon,
                contentDescription = primaryTooltip,
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
