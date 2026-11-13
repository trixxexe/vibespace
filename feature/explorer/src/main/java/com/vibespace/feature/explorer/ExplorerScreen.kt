package com.vibespace.feature.explorer

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibespace.core.common.notification.VibeNotificationHelper
import com.vibespace.core.designsystem.components.VibeDialog
import com.vibespace.core.designsystem.components.VibeEmptyState
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.model.VirtualFile
import com.vibespace.feature.explorer.components.FileTreeItem

@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    onOpenFile: (VirtualFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<VirtualFile?>(null) }
    var fileToRename by remember { mutableStateOf<VirtualFile?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var filePendingExport by remember { mutableStateOf<VirtualFile?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                var resolvedName = "imported_file.txt"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            resolvedName = cursor.getString(nameIdx) ?: resolvedName
                        }
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.importExternalFile(resolvedName, bytes, onFileImported = onOpenFile)
                    Toast.makeText(context, "Imported $resolvedName into project", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri ->
        val sourceFile = filePendingExport
        filePendingExport = null
        if (targetUri != null && sourceFile != null) {
            viewModel.readFileBytesForExport(sourceFile.uri) { bytes ->
                runCatching {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        out.write(bytes)
                    }
                    VibeNotificationHelper.notify(
                        context = context,
                        title = "File Exported to Storage",
                        message = "Saved ${sourceFile.name} to device storage."
                    )
                    Toast.makeText(context, "Exported ${sourceFile.name} to storage", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showNewFileDialog) {
        VibeDialog(
            title = "New File",
            onDismissRequest = { showNewFileDialog = false },
            confirmButtonText = "Create",
            isConfirmEnabled = newFileName.isNotBlank(),
            onConfirm = {
                viewModel.createNewFile(newFileName, onFileCreated = onOpenFile)
                showNewFileDialog = false
                newFileName = ""
            }
        ) {
            Column {
                Text("Enter file name (e.g., Script.py, styles.css):", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showNewFolderDialog) {
        VibeDialog(
            title = "New Folder",
            onDismissRequest = { showNewFolderDialog = false },
            confirmButtonText = "Create",
            isConfirmEnabled = newFolderName.isNotBlank(),
            onConfirm = {
                viewModel.createNewFolder(newFolderName)
                showNewFolderDialog = false
                newFolderName = ""
            }
        ) {
            Column {
                Text("Enter folder name:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    fileToDelete?.let { file ->
        VibeDialog(
            title = "Delete ${file.name}?",
            onDismissRequest = { fileToDelete = null },
            confirmButtonText = "Delete",
            onConfirm = {
                viewModel.deleteFile(file.uri)
                fileToDelete = null
            }
        ) {
            Text("Are you sure you want to delete '${file.name}'? This cannot be undone.", style = MaterialTheme.typography.bodySmall)
        }
    }

    fileToRename?.let { file ->
        VibeDialog(
            title = "Rename ${file.name}",
            onDismissRequest = { fileToRename = null },
            confirmButtonText = "Rename",
            isConfirmEnabled = renameNewName.isNotBlank() && renameNewName.trim() != file.name,
            onConfirm = {
                viewModel.renameFile(file.uri, renameNewName.trim())
                fileToRename = null
            }
        ) {
            Column {
                Text("Enter new name:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VibeTheme.colors.gutterBackground)
    ) {
        // Explorer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(VibeTheme.colors.panelBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (uiState.currentProject?.name ?: "EXPLORER").uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showNewFileDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.Add,
                    contentDescription = "New File",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { showNewFolderDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.NewFolder,
                    contentDescription = "New Folder",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { importFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.FolderOpen,
                    contentDescription = "Import File from Storage",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { viewModel.refreshFiles() },
                modifier = Modifier.size(28.dp),
                enabled = uiState.currentProject != null
            ) {
                Icon(
                    imageVector = VibeIcons.Refresh,
                    contentDescription = "Refresh",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Tree List
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (uiState.currentProject == null) {
            VibeEmptyState(
                icon = VibeIcons.Explorer,
                title = "No Project Open",
                subtitle = "Open a workspace from the dashboard or create a new file to get started.",
                actionButtonText = "New File",
                onActionClick = { showNewFileDialog = true }
            )
        } else if (uiState.rootNodes.isEmpty()) {
            VibeEmptyState(
                icon = VibeIcons.File,
                title = "Empty Directory",
                subtitle = "Click '+' to create a file or import one from device storage.",
                actionButtonText = "New File",
                onActionClick = { showNewFileDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
            ) {
                fun renderNode(node: ExplorerNode) {
                    item(key = "${node.file.uri}_${node.file.name}_${node.level}") {
                        FileTreeItem(
                            file = node.file,
                            level = node.level,
                            isExpanded = node.isExpanded,
                            isSelected = node.file.uri == uiState.selectedFileUri,
                            gitStatus = uiState.fileGitStatuses[node.file.uri] ?: node.file.path?.let { uiState.fileGitStatuses[it] },
                            onClick = {
                                viewModel.selectFile(node.file, onOpenFile)
                            },
                            onDeleteClick = {
                                fileToDelete = node.file
                            },
                            onRenameClick = {
                                fileToRename = node.file
                                renameNewName = node.file.name
                            },
                            onDuplicateClick = {
                                viewModel.duplicateFile(node.file.uri)
                            },
                            onExportClick = {
                                filePendingExport = node.file
                                exportFileLauncher.launch(node.file.name)
                            },
                            onCopyPathClick = {
                                val path = node.file.path ?: node.file.name
                                clipboardManager.setText(AnnotatedString(path))
                            }
                        )
                    }
                    if (node.isExpanded) {
                        node.children.forEach { renderNode(it) }
                    }
                }

                uiState.rootNodes.forEach { renderNode(it) }
            }
        }
    }
}
