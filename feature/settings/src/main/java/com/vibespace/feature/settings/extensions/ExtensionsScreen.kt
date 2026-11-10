package com.vibespace.feature.settings.extensions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vibespace.core.designsystem.components.VibeIcons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.editor.extension.ExtensionCategory
import com.vibespace.core.editor.extension.ExtensionMetadata

@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // VSIX file picker launcher
    val vsixLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.installFromVsix(inputStream)
                }
            } catch (e: Exception) {
                // Handled gracefully in viewmodel state
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VibeTheme.colors.editorBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.Back,
                    contentDescription = "Back",
                    tint = VibeTheme.colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = VibeIcons.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Extensions & Themes",
                style = MaterialTheme.typography.titleMedium,
                color = VibeTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            // Install from VSIX button
            OutlinedButton(
                onClick = { vsixLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = VibeIcons.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Install .VSIX", fontSize = 12.sp)
            }
        }

        // Search bar
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = {
                    Text(
                        text = "Search Open-VSX extensions & themes...",
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
        }

        // Category filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.activeFilter == ExtensionFilter.ALL,
                    onClick = { viewModel.setFilter(ExtensionFilter.ALL) },
                    label = { Text("All") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeFilter == ExtensionFilter.INSTALLED,
                    onClick = { viewModel.setFilter(ExtensionFilter.INSTALLED) },
                    label = { Text("Installed (${uiState.installedExtensions.size})") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeFilter == ExtensionFilter.THEMES,
                    onClick = { viewModel.setFilter(ExtensionFilter.THEMES) },
                    label = { Text("Themes") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeFilter == ExtensionFilter.LANGUAGES,
                    onClick = { viewModel.setFilter(ExtensionFilter.LANGUAGES) },
                    label = { Text("Languages") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeFilter == ExtensionFilter.ICONS,
                    onClick = { viewModel.setFilter(ExtensionFilter.ICONS) },
                    label = { Text("Icons") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeFilter == ExtensionFilter.FORMATTERS,
                    onClick = { viewModel.setFilter(ExtensionFilter.FORMATTERS) },
                    label = { Text("Formatters") }
                )
            }
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val filteredList = when (uiState.activeFilter) {
            ExtensionFilter.ALL -> uiState.extensions
            ExtensionFilter.INSTALLED -> uiState.installedExtensions
            ExtensionFilter.THEMES -> uiState.extensions.filter { it.category == ExtensionCategory.THEME }
            ExtensionFilter.LANGUAGES -> uiState.extensions.filter { it.category == ExtensionCategory.LANGUAGE }
            ExtensionFilter.ICONS -> uiState.extensions.filter { it.category == ExtensionCategory.ICON_PACK }
            ExtensionFilter.FORMATTERS -> uiState.extensions.filter { it.category == ExtensionCategory.FORMATTER }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.id }) { ext ->
                    ExtensionCard(
                        extension = ext,
                        downloadProgress = uiState.downloadingExtensions[ext.id],
                        onInstall = { viewModel.installExtension(ext) },
                        onUninstall = { viewModel.uninstallExtension(ext.id) },
                        onToggle = { enabled -> viewModel.toggleExtension(ext.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: ExtensionMetadata,
    downloadProgress: Float? = null,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VibeTheme.colors.panelBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = VibeTheme.colors.textPrimary
                    )
                    Text(
                        text = "${extension.publisher} • v${extension.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeTheme.colors.textSecondary
                    )
                }

                // Category Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = VibeTheme.colors.activeLineBackground,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = extension.category.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = extension.description,
                style = MaterialTheme.typography.bodySmall,
                color = VibeTheme.colors.textSecondary,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action row (Touch targets >= 44dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (extension.downloadCount > 0) {
                    Text(
                        text = formatDownloads(extension.downloadCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = VibeTheme.colors.textMuted
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (extension.isInstalled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (extension.isEnabled) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (extension.isEnabled) VibeTheme.colors.statusSuccess else VibeTheme.colors.textMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = extension.isEnabled,
                                onCheckedChange = onToggle,
                                modifier = Modifier.height(32.dp)
                            )
                        }

                        if (!extension.isBuiltIn) {
                            TextButton(
                                onClick = onUninstall,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    text = "Uninstall",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else if (downloadProgress != null) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.width(140.dp)
                        ) {
                            Text(
                                text = "Downloading ${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = VibeTheme.colors.borderSubtle
                            )
                        }
                    } else {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                imageVector = VibeIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Install")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDownloads(count: Long): String {
    return when {
        count >= 1000000 -> "${count / 1000000}M downloads"
        count >= 1000 -> "${count / 1000}K downloads"
        else -> "$count downloads"
    }
}
