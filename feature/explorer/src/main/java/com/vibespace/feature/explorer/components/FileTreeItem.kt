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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.model.VirtualFile

@Composable
fun FileTreeItem(
    file: VirtualFile,
    level: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
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
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
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

        if (onDeleteClick != null) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private fun getFileIcon(extension: String): ImageVector {
    return when (extension.lowercase()) {
        "kt", "kts", "java", "py", "js", "ts", "rs", "go", "c", "cpp" -> Icons.Default.Code
        else -> Icons.Default.Description
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
