package com.vibespace.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.workspace.model.VirtualFile

@Composable
fun SearchPanel(
    viewModel: SearchViewModel,
    onClose: () -> Unit,
    onOpenResult: (VirtualFile, Int) -> Unit = { _, _ -> },
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
                placeholder = {
                    Text(
                        if (uiState.scope == SearchScope.FILE) "Find in current file..." else "Search workspace...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = when {
                    uiState.scope == SearchScope.WORKSPACE && uiState.isSearchingWorkspace -> "Searching…"
                    uiState.scope == SearchScope.FILE && uiState.matchCount > 0 -> "${uiState.activeMatchIndex}/${uiState.matchCount}"
                    uiState.matchCount > 0 -> "${uiState.matchCount} matches"
                    uiState.query.isBlank() -> ""
                    else -> "No results"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.matchCount > 0) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { viewModel.findPrevious() },
                enabled = uiState.scope == SearchScope.FILE && uiState.matchCount > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.ArrowUp,
                    contentDescription = "Previous match",
                    tint = VibeTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { viewModel.findNext() },
                enabled = uiState.scope == SearchScope.FILE && uiState.matchCount > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.ArrowDown,
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
                    imageVector = VibeIcons.Close,
                    contentDescription = "Close search",
                    tint = VibeTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search scope
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VibeButton(
                onClick = { viewModel.setScope(SearchScope.FILE) },
                style = if (uiState.scope == SearchScope.FILE) VibeButtonStyle.Primary else VibeButtonStyle.Outline
            ) { Text("Current file", style = MaterialTheme.typography.labelSmall) }
            VibeButton(
                onClick = { viewModel.setScope(SearchScope.WORKSPACE) },
                style = if (uiState.scope == SearchScope.WORKSPACE) VibeButtonStyle.Primary else VibeButtonStyle.Outline
            ) { Text("Workspace", style = MaterialTheme.typography.labelSmall) }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VibeButton(
                onClick = { viewModel.toggleMatchCase() },
                style = if (uiState.matchCase) VibeButtonStyle.Primary else VibeButtonStyle.Outline
            ) { Text("Aa", style = MaterialTheme.typography.labelSmall) }
            VibeButton(
                onClick = { viewModel.toggleReplaceMode() },
                enabled = uiState.scope == SearchScope.FILE,
                style = if (uiState.isReplaceMode) VibeButtonStyle.Primary else VibeButtonStyle.Outline
            ) { Text("Replace", style = MaterialTheme.typography.labelSmall) }
            if (uiState.scope == SearchScope.WORKSPACE) {
                VibeButton(
                    onClick = { viewModel.toggleRegex() },
                    style = if (uiState.useRegex) VibeButtonStyle.Primary else VibeButtonStyle.Outline
                ) { Text(".* Regex", style = MaterialTheme.typography.labelSmall) }
            }
        }

        if (uiState.scope == SearchScope.WORKSPACE && uiState.useRegex) {
            Text("Linear-time regex is used; backreferences are not supported.", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
        }

        uiState.searchError?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }

        if (uiState.scope == SearchScope.WORKSPACE && uiState.workspaceMatches.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(uiState.workspaceMatches, key = { index, match -> "${match.file.uri}_${match.lineNumber}_$index" }) { _, match ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenResult(match.file, match.lineNumber) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${match.relativePath}:${match.lineNumber}:${match.column}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = match.lineText,
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textPrimary,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        if (uiState.scope == SearchScope.WORKSPACE && uiState.skippedFileCount > 0) {
            Text(
                text = "Skipped ${uiState.skippedFileCount} files that were too large or could not be read.",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
        }

        if (uiState.scope == SearchScope.WORKSPACE && uiState.resultsTruncated) {
            Text(
                text = "Results are limited to 1,000 matches or a 24 MB scan budget.",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
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
