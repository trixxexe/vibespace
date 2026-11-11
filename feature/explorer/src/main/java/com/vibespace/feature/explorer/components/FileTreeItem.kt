package com.vibespace.feature.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.git.GitStatusType
import com.vibespace.core.workspace.model.VirtualFile

@Composable
fun FileTreeItem(
    file: VirtualFile,
    level: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    gitStatus: GitStatusType? = null,
    onDeleteClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDuplicateClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val indentPadding = (level * 16 + 8).dp
    val itemBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else VibeTheme.colors.textPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(itemBg, VibeShapes.extraSmall)
            .clickable { onClick() }
            .padding(start = indentPadding, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (file.isDirectory) {
            Icon(
                imageVector = if (isExpanded) VibeIcons.ArrowDown else VibeIcons.ArrowRight,
                contentDescription = null,
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) VibeIcons.FolderOpen else VibeIcons.Explorer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
            Icon(
                imageVector = getFileIcon(file.extension),
                contentDescription = null,
                tint = getFileIconColor(file.extension),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (gitStatus != null) {
            val (badgeText, badgeColor) = when (gitStatus) {
                GitStatusType.MODIFIED -> Pair("M", Color(0xFF64B5F6))
                GitStatusType.UNTRACKED -> Pair("U", Color(0xFF81C784))
                GitStatusType.ADDED -> Pair("A", Color(0xFF81C784))
                GitStatusType.DELETED -> Pair("D", Color(0xFFE57373))
            }
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        var showMenu by remember { mutableStateOf(false) }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.More,
                    contentDescription = "Options",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (onRenameClick != null) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRenameClick()
                        }
                    )
                }
                if (file.isFile && onDuplicateClick != null) {
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            showMenu = false
                            onDuplicateClick()
                        }
                    )
                }
                if (onDeleteClick != null) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

private fun getFileIcon(extension: String): ImageVector {
    return when (extension.lowercase()) {
        "kt", "kts", "java", "py", "js", "ts", "rs", "go", "c", "cpp" -> VibeIcons.Editor
        else -> VibeIcons.File
    }
}

@Composable
private fun getFileIconColor(extension: String): Color {
    return when (extension.lowercase()) {
        "kt", "kts" -> Color(0xFF7F52FF)
        "py" -> Color(0xFF3776AB)
        "js", "ts" -> Color(0xFFF7DF1E)
        "rs" -> Color(0xFFDEA584)
        "go" -> Color(0xFF00ADD8)
        "md" -> Color(0xFF083FA1)
        else -> VibeTheme.colors.textSecondary
    }
}
