package com.vibespace.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.model.VirtualFile

data class CommandAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val shortcut: String? = null,
    val action: () -> Unit
)

/**
 * Mobile-First VS Code-style Command Palette (Ctrl+P / Quick Open).
 * Allows instant navigation to any project file or executing IDE actions with zero friction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteDialog(
    files: List<VirtualFile>,
    onOpenFile: (VirtualFile) -> Unit,
    onRunScript: () -> Unit,
    onSaveFile: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onFormatDocument: (() -> Unit)? = null,
    onShowHover: (() -> Unit)? = null,
    onGoToDefinition: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    val allActions = listOfNotNull(
        CommandAction(
            title = "Run Active File",
            subtitle = "Execute current file via Tri-Mode Runner",
            icon = VibeIcons.Run,
            shortcut = "Ctrl+Enter",
            action = onRunScript
        ),
        CommandAction(
            title = "Save Document",
            subtitle = "Save changes to storage",
            icon = VibeIcons.Save,
            shortcut = "Ctrl+S",
            action = onSaveFile
        ),
        onFormatDocument?.let {
            CommandAction(
                title = "Format Document",
                subtitle = "Format active file via Language Server (LSP)",
                icon = VibeIcons.Editor,
                shortcut = "Shift+Alt+F",
                action = it
            )
        },
        onShowHover?.let {
            CommandAction(
                title = "Show Documentation",
                subtitle = "Semantic hover docs for symbol at cursor",
                icon = VibeIcons.File,
                shortcut = "Ctrl+K Ctrl+I",
                action = it
            )
        },
        onGoToDefinition?.let {
            CommandAction(
                title = "Go to Definition",
                subtitle = "Jump to declaration/definition of symbol",
                icon = VibeIcons.Run,
                shortcut = "F12",
                action = it
            )
        },
        CommandAction(
            title = "Find in Document",
            subtitle = "Search and replace text",
            icon = VibeIcons.Search,
            shortcut = "Ctrl+F",
            action = onOpenSearch
        ),
        CommandAction(
            title = "Ask AI Assistant",
            subtitle = "Open AI coding assistant with code context",
            icon = VibeIcons.AI,
            shortcut = null,
            action = onOpenAi
        ),
        CommandAction(
            title = "Open Preferences",
            subtitle = "Theme, fonts, and execution settings",
            icon = VibeIcons.Settings,
            shortcut = null,
            action = onOpenSettings
        )
    )

    val matchingFiles: List<VirtualFile> = remember(query, files) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            files.filter { !it.isDirectory }
        } else {
            files.filter { !it.isDirectory && (it.name.lowercase().contains(cleanQuery) || (it.path ?: it.uri).lowercase().contains(cleanQuery)) }
                .sortedWith(compareBy(
                    { !it.name.lowercase().startsWith(cleanQuery) },
                    { !it.name.lowercase().contains(cleanQuery) },
                    { it.name.length }
                ))
        }
    }

    val matchingActions = remember(query) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            allActions
        } else {
            allActions.filter {
                it.title.lowercase().contains(cleanQuery) || it.subtitle.lowercase().contains(cleanQuery)
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VibeTheme.colors.panelBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Search Input Header
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Search files or commands...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = VibeIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (matchingFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "FILES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = VibeTheme.colors.textMuted,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }

                        items(matchingFiles) { file ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VibeTheme.colors.gutterBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenFile(file)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = VibeIcons.File,
                                        contentDescription = null,
                                        tint = VibeTheme.colors.accentSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = VibeCodeTypography.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = VibeTheme.colors.textPrimary
                                            )
                                        )
                                        val displayPath = (file.path ?: file.parentUri ?: file.uri)
                                            .removePrefix("vibe://projects/")
                                            .removePrefix("file://")
                                        if (displayPath.isNotEmpty() && displayPath != file.name) {
                                            Text(
                                                text = displayPath,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VibeTheme.colors.textMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (matchingActions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "COMMANDS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = VibeTheme.colors.textMuted,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }

                        items(matchingActions) { action ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VibeTheme.colors.gutterBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        action.action()
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = action.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = VibeTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = action.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VibeTheme.colors.textMuted
                                        )
                                    }
                                    if (action.shortcut != null) {
                                        Text(
                                            text = action.shortcut,
                                            style = VibeCodeTypography.copy(fontSize = 11.sp),
                                            color = VibeTheme.colors.accentPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
