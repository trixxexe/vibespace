package com.vibespace.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.components.VibeButton
import com.vibespace.core.designsystem.components.VibeButtonStyle
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun SearchPanel(
    viewModel: SearchViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VibeTheme.colors.panelBackground, VibeShapes.medium)
            .border(1.dp, VibeTheme.colors.borderSubtle, VibeShapes.medium)
            .padding(12.dp)
    ) {
        // Find row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text("Find in file...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (uiState.matchCount > 0) "${uiState.activeMatchIndex}/${uiState.matchCount}" else "No results",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.matchCount > 0) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { viewModel.findPrevious() },
                enabled = uiState.matchCount > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Previous match",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { viewModel.findNext() },
                enabled = uiState.matchCount > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next match",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Options & Replace toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VibeButton(
                    onClick = { viewModel.toggleMatchCase() },
                    style = if (uiState.matchCase) VibeButtonStyle.Primary else VibeButtonStyle.Outline,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Aa", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                VibeButton(
                    onClick = { viewModel.toggleReplaceMode() },
                    style = if (uiState.isReplaceMode) VibeButtonStyle.Primary else VibeButtonStyle.Outline,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Replace", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Replace row
        if (uiState.isReplaceMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.replacement,
                    onValueChange = { viewModel.onReplacementChange(it) },
                    placeholder = { Text("Replace with...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                VibeButton(
                    onClick = { viewModel.replaceAll() },
                    style = VibeButtonStyle.Secondary,
                    enabled = uiState.matchCount > 0,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("All", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
