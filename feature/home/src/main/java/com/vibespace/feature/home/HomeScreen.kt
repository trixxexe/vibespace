package com.vibespace.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeDialog
import com.vibespace.core.designsystem.components.VibeEmptyState
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.model.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExtensions: (() -> Unit)? = null,
    onNavigateToOnboarding: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
            val folderName = folderDoc?.name ?: "Workspace"
            viewModel.openSafTreeUri(
                treeUri = treeUri,
                folderName = folderName,
                onProjectReady = { onNavigateToEditor() }
            )
        }
    }

    if (showNewProjectDialog) {
        VibeDialog(
            title = "Create New Project",
            onDismissRequest = { showNewProjectDialog = false },
            confirmButtonText = "Create",
            isConfirmEnabled = newProjectName.isNotBlank(),
            onConfirm = {
                val name = newProjectName
                showNewProjectDialog = false
                newProjectName = ""
                viewModel.createNewProject(name) {
                    onNavigateToEditor()
                }
            }
        ) {
            Column {
                Text(
                    text = "Enter a name for your mobile coding project:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    placeholder = { Text("e.g. MyVibeApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (uiState.errorMessage != null) {
        VibeDialog(
            title = "Project Error",
            onDismissRequest = { viewModel.dismissError() },
            confirmButtonText = "OK",
            onConfirm = { viewModel.dismissError() }
        ) {
            Text(
                text = uiState.errorMessage ?: "Unknown error occurred",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.statusError
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Branding Banner
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VibeIcons.Editor,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "VibeSpace",
                        style = MaterialTheme.typography.displayMedium,
                        color = VibeTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Professional Native Android Code Editor & Mobile IDE",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }

        // Quick Actions Grid
        item {
            Text(
                text = "START",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = VibeIcons.Add,
                    title = "New Project",
                    subtitle = "Create empty workspace",
                    modifier = Modifier.weight(1f),
                    onClick = { showNewProjectDialog = true }
                )
                QuickActionCard(
                    icon = VibeIcons.FolderOpen,
                    title = "Open Project",
                    subtitle = "Browse device folder (SAF)",
                    modifier = Modifier.weight(1f),
                    onClick = { openTreeLauncher.launch(null) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = VibeIcons.Editor,
                    title = "Try Demo Project",
                    subtitle = "Explore sample codebase",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.openDemoProject {
                            onNavigateToEditor()
                        }
                    }
                )
                QuickActionCard(
                    icon = VibeIcons.Extension,
                    title = "Extensions",
                    subtitle = "Themes & plugins",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToExtensions?.invoke() ?: onNavigateToSettings() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = VibeIcons.Settings,
                    title = "Settings",
                    subtitle = "Preferences & about",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSettings
                )
                QuickActionCard(
                    icon = VibeIcons.Logo,
                    title = "Guided Tour",
                    subtitle = "IDE setup & walkthrough",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToOnboarding?.invoke() }
                )
            }
        }

        // Recent Projects Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "RECENT PROJECTS",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (uiState.recentProjects.isEmpty()) {
            item {
                VibeEmptyState(
                    icon = VibeIcons.Explorer,
                    title = "No Recent Projects",
                    subtitle = "Create your first project or open an existing directory to start coding.",
                    actionButtonText = "Create New Project",
                    onActionClick = { showNewProjectDialog = true }
                )
            }
        } else {
            itemsIndexed(uiState.recentProjects, key = { index, project -> "${project.id}_${project.rootUri}_$index" }) { _, project ->
                RecentProjectItem(
                    project = project,
                    onClick = {
                        viewModel.openProject(
                            project = project,
                            onProjectReady = { onNavigateToEditor() }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VibeCard(
        modifier = modifier.clickable { onClick() },
        contentPadding = 12.dp
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VibeTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun RecentProjectItem(
    project: Project,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VibeCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = VibeIcons.Explorer,
                contentDescription = null,
                tint = VibeTheme.colors.accentSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = VibeTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = project.rootUri,
                    style = VibeCodeTypography.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = VibeTheme.colors.textMuted
                )
            }
            Text(
                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(project.lastOpenedTimestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textSecondary
            )
        }
    }
}
