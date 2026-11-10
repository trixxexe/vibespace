package com.vibespace.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.feature.settings.repository.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Settings Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VibeTheme.colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                color = VibeTheme.colors.textPrimary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Section
            item {
                SectionHeader("APPEARANCE")
                VibeCard {
                    Column {
                        ThemeOptionRow(
                            title = "Dark Theme (Default)",
                            subtitle = "Deep charcoal developer aesthetic",
                            isSelected = settings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                        )
                        ThemeOptionRow(
                            title = "Light Theme",
                            subtitle = "High contrast light surfaces",
                            isSelected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        )
                        ThemeOptionRow(
                            title = "Follow System",
                            subtitle = "Matches Android system dark theme settings",
                            isSelected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                        )
                    }
                }
            }

            // Editor Preferences
            item {
                SectionHeader("EDITOR PREFERENCES")
                VibeCard {
                    Column {
                        Text(
                            text = "Font Size: ${settings.editorFontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.titleSmall,
                            color = VibeTheme.colors.textPrimary
                        )
                        Slider(
                            value = settings.editorFontSizeSp,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 10f..22f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "fun hello() { println(\"VibeSpace preview\") }",
                            style = VibeCodeTypography.copy(fontSize = settings.editorFontSizeSp.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VibeTheme.colors.gutterBackground)
                                .padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Show Line Numbers", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                                Text("Display gutter line numbers in editor canvas", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                            }
                            Switch(
                                checked = settings.showLineNumbers,
                                onCheckedChange = { viewModel.toggleLineNumbers(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Word Wrap", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                                Text("Wrap long lines horizontally", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                            }
                            Switch(
                                checked = settings.wordWrap,
                                onCheckedChange = { viewModel.toggleWordWrap(it) }
                            )
                        }
                    }
                }
            }

            // About VibeSpace
            item {
                SectionHeader("ABOUT VIBESPACE")
                VibeCard {
                    Column {
                        Text(
                            text = "VibeSpace Mobile IDE",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version ${uiState.appVersion} (Build ${uiState.buildNumber})",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Native Android IDE inspired by modern development workflows. Designed for mobile coding on smartphones and tablets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Kotlin ${uiState.kotlinVersion} • Jetpack Compose • Material 3",
                            style = VibeCodeTypography.copy(fontSize = 11.sp),
                            color = VibeTheme.colors.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = VibeTheme.colors.textMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
        }
    }
}
