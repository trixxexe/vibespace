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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
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
                text = "Source Control",
                style = MaterialTheme.typography.titleSmall,
                color = VibeTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            // Interactive Branch Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VibeTheme.colors.activeLineBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { viewModel.toggleBranchDialog(true) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = " ${uiState.status.currentBranch}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch Branch",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Sync Button (Ahead/Behind counts)
            IconButton(
                onClick = { viewModel.push() },
                enabled = !uiState.isSyncing,
                modifier = Modifier.size(36.dp)
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "↑${uiState.status.aheadCount}↓${uiState.status.behindCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.status.aheadCount > 0 || uiState.status.behindCount > 0) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted
                    )
                }
            }

            IconButton(
                onClick = { viewModel.refreshStatus() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
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
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Close",
                        tint = VibeTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Commit Box Section
        Column(modifier = Modifier.padding(10.dp)) {
            OutlinedTextField(
                value = uiState.commitMessage,
                onValueChange = { viewModel.onCommitMessageChange(it) },
                placeholder = {
                    Text(
                        text = "Commit message...",
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
                Text(
                    text = "✓ ${uiState.syncMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeTheme.colors.statusSuccess
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.commit() },
                enabled = !uiState.isCommitting && (uiState.status.stagedFiles.isNotEmpty() || uiState.status.unstagedFiles.isNotEmpty()),
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
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Commit (${uiState.status.stagedFiles.size} staged)")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pull / Push Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.pull() },
                    enabled = !uiState.isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Text("Pull (${uiState.status.behindCount})", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.push() },
                    enabled = !uiState.isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Text("Push (${uiState.status.aheadCount})", fontSize = 12.sp)
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
                        actionIcon = Icons.Default.Clear,
                        actionTooltip = "Unstage All"
                    )
                }

                if (isStagedExpanded) {
                    items(uiState.status.stagedFiles) { file ->
                        GitFileRow(
                            file = file,
                            onItemClick = { viewModel.openDiff(file.path) },
                            onPrimaryAction = { viewModel.unstageFile(file.path) },
                            primaryIcon = Icons.Default.Clear,
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
                    actionIcon = Icons.Default.Add,
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
                            Text(
                                text = "Working tree is clean ✓",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VibeTheme.colors.textMuted
                            )
                        }
                    }
                } else {
                    items(uiState.status.unstagedFiles) { file ->
                        GitFileRow(
                            file = file,
                            onItemClick = { viewModel.openDiff(file.path) },
                            onPrimaryAction = { viewModel.stageFile(file.path) },
                            primaryIcon = Icons.Default.Add,
                            primaryTooltip = "Stage",
                            onSecondaryAction = { viewModel.discardChanges(file.path) },
                            secondaryIcon = Icons.Default.Restore,
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

    // Branch Switcher Dialog
    if (uiState.showBranchDialog) {
        var newBranchName by remember { mutableStateOf("") }
        VibeDialog(
            title = "Git Branches",
            onDismissRequest = { viewModel.toggleBranchDialog(false) },
            confirmButtonText = "Done",
            onConfirm = { viewModel.toggleBranchDialog(false) },
            dismissButtonText = null,
            onDismiss = null
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Branches in workspace",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = VibeTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(uiState.status.branches) { branch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clickable { viewModel.checkoutBranch(branch.name) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (branch.isCurrent) "●" else "○",
                                color = if (branch.isCurrent) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = branch.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (branch.isCurrent) MaterialTheme.colorScheme.primary else VibeTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (branch.isCurrent) {
                                Text(
                                    text = "Current",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VibeTheme.colors.textMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Create New Branch",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = VibeTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        placeholder = { Text("feature/my-branch", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newBranchName.isNotBlank()) {
                                viewModel.createBranch(newBranchName.trim())
                                newBranchName = ""
                            }
                        },
                        enabled = newBranchName.isNotBlank(),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }

    // Git Commit Dialog
    if (uiState.showCommitDialog) {
        var dialogCommitMsg by remember { mutableStateOf(uiState.commitMessage) }
        VibeDialog(
            title = "Commit to ${uiState.status.currentBranch}",
            onDismissRequest = { viewModel.toggleCommitDialog(false) },
            confirmButtonText = "Commit",
            isConfirmEnabled = dialogCommitMsg.isNotBlank() && (uiState.status.stagedFiles.isNotEmpty() || uiState.status.unstagedFiles.isNotEmpty()),
            onConfirm = {
                viewModel.onCommitMessageChange(dialogCommitMsg)
                viewModel.commit()
                viewModel.toggleCommitDialog(false)
            },
            dismissButtonText = "Cancel",
            onDismiss = { viewModel.toggleCommitDialog(false) }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${uiState.status.stagedFiles.size} staged, ${uiState.status.unstagedFiles.size} unstaged",
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeTheme.colors.textMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dialogCommitMsg,
                    onValueChange = { dialogCommitMsg = it },
                    placeholder = { Text("Commit message...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }
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
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
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
