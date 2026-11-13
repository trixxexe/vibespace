package com.vibespace.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.vibespace.core.common.notification.VibeNotificationHelper
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
    val clipboard = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneUrl by remember { mutableStateOf("") }
    var cloneUsername by remember { mutableStateOf("") }
    var cloneToken by remember { mutableStateOf("") }

    // Prevent hardware back press from exiting the app on HomeScreen; close any open dialog or return to active editor if a project was opened
    BackHandler(enabled = true) {
        when {
            showNewProjectDialog -> showNewProjectDialog = false
            showCloneDialog -> showCloneDialog = false
            uiState.errorMessage != null -> viewModel.dismissError()
            uiState.recentProjects.isNotEmpty() -> onNavigateToEditor()
            else -> Toast.makeText(context, "Select or create a project to start coding", Toast.LENGTH_SHORT).show()
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
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
                viewModel.createNewProject(name) { project ->
                    VibeNotificationHelper.notify(
                        context = context,
                        title = "Project Created",
                        message = "Workspace '${project.name}' is ready."
                    )
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

    if (showCloneDialog) {
        VibeDialog(
            title = "Clone Git Repository",
            onDismissRequest = { showCloneDialog = false },
            confirmButtonText = "Clone into Workspace",
            isConfirmEnabled = cloneUrl.isNotBlank() && !uiState.isLoading,
            onConfirm = {
                val urlToClone = cloneUrl
                val userToUse = cloneUsername
                val tokenToUse = cloneToken
                showCloneDialog = false
                cloneToken = ""
                viewModel.cloneGitRepository(urlToClone, userToUse, tokenToUse) { _, summary ->
                    VibeNotificationHelper.notify(
                        context = context,
                        title = "Git Repository Cloned",
                        message = summary
                    )
                    onNavigateToEditor()
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste a GitHub, GitLab, or HTTP(S) repository link to clone it into a new VibeSpace project:",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeTheme.colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = cloneUrl,
                        onValueChange = { cloneUrl = it },
                        placeholder = { Text("https://github.com/user/repo.git") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val clip = clipboard.getText()?.text?.trim().orEmpty()
                            if (clip.isNotEmpty()) cloneUrl = clip
                        }
                    ) {
                        Text("Paste")
                    }
                }
                OutlinedTextField(
                    value = cloneUsername,
                    onValueChange = { cloneUsername = it },
                    placeholder = { Text("Username (optional for public repos)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cloneToken,
                    onValueChange = { cloneToken = it },
                    placeholder = { Text("Access Token / Password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
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
                    text = "Professional Native Android Code Editor & Mobile IDE • by StarDevs",
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
                    icon = VibeIcons.SourceControl,
                    title = "Clone Git Repo",
                    subtitle = "Paste link & clone repo",
                    modifier = Modifier.weight(1f),
                    onClick = { showCloneDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = VibeIcons.FolderOpen,
                    title = "Open Folder",
                    subtitle = "Browse device storage (SAF)",
                    modifier = Modifier.weight(1f),
                    onClick = { openTreeLauncher.launch(null) }
                )
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = VibeIcons.Logo,
                    title = "A-to-Z Guide & Tour",
                    subtitle = "Interactive IDE tutorial",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToOnboarding?.invoke() }
                )
                QuickActionCard(
                    icon = VibeIcons.Settings,
                    title = "Settings & Themes",
                    subtitle = "Themes, AI keys & about",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = VibeIcons.Extension,
                    title = "Extensions",
                    subtitle = "VS Code themes & tools",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigateToExtensions?.invoke() ?: onNavigateToSettings() }
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
                    subtitle = "Create your first project, clone a Git repo, or open an existing directory to start coding.",
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
