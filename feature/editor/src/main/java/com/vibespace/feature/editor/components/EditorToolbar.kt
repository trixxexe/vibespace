package com.vibespace.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun EditorToolbar(
    onSaveClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRunClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onToggleTerminalClick: () -> Unit,
    isDocumentDirty: Boolean,
    onFormatClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(VibeTheme.colors.gutterBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSaveClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.Save,
                contentDescription = "Save file",
                tint = if (isDocumentDirty) VibeTheme.colors.accentPrimary else VibeTheme.colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onUndoClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.Undo,
                contentDescription = "Undo",
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onRedoClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.Redo,
                contentDescription = "Redo",
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (onFormatClick != null) {
            IconButton(
                onClick = onFormatClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.Editor,
                    contentDescription = "Format Document",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.Search,
                contentDescription = "Find and Replace",
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onAiAssistantClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.AI,
                contentDescription = "AI Assistant",
                tint = VibeTheme.colors.accentPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onToggleTerminalClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.Terminal,
                contentDescription = "Terminal Panel",
                tint = VibeTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onRunClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = VibeIcons.Run,
                contentDescription = "Run Code",
                tint = VibeTheme.colors.statusSuccess,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
