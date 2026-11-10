package com.vibespace.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun VibePanel(
    title: String,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    headerActions: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(VibeTheme.colors.panelBackground)
            .border(1.dp, VibeTheme.colors.borderSubtle, VibeShapes.small)
    ) {
        // Panel Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textSecondary,
                modifier = Modifier.weight(1f)
            )

            headerActions?.invoke(this)

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = VibeIcons.Close,
                        contentDescription = "Close panel",
                        tint = VibeTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Panel Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            content()
        }
    }
}
