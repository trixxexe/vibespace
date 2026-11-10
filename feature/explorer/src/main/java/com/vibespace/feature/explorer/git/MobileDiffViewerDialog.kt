package com.vibespace.feature.explorer.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.git.DiffLine
import com.vibespace.core.workspace.git.DiffLineType
import com.vibespace.core.workspace.git.FileDiff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDiffViewerDialog(
    diff: FileDiff,
    onStageFile: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VibeTheme.colors.panelBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = diff.filePath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            color = VibeTheme.colors.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "+${diff.addedCount}",
                                color = VibeTheme.colors.statusSuccess,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "-${diff.deletedCount}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = VibeTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Diff lines list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .background(VibeTheme.colors.editorBackground, RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp)
                ) {
                    items(diff.lines) { line ->
                        DiffLineRow(line)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons (touch target >= 44dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onStageFile()
                            onDismiss()
                        },
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Stage Changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val bgColor = when (line.type) {
        DiffLineType.INSERT -> Color(0x2E2E7D32)
        DiffLineType.DELETE -> Color(0x2EC62828)
        DiffLineType.EQUAL -> Color.Transparent
    }

    val textColor = when (line.type) {
        DiffLineType.INSERT -> Color(0xFF81C784)
        DiffLineType.DELETE -> Color(0xFFE57373)
        DiffLineType.EQUAL -> VibeTheme.colors.textPrimary
    }

    val prefix = when (line.type) {
        DiffLineType.INSERT -> "+"
        DiffLineType.DELETE -> "-"
        DiffLineType.EQUAL -> " "
    }

    val lineNum = (line.newLineNumber ?: line.oldLineNumber)?.toString() ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = lineNum.padStart(3, ' '),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = VibeTheme.colors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
            fontSize = 11.sp,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
            fontSize = 11.sp
        )
    }
}
