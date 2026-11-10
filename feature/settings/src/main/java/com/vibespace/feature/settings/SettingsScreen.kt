package com.vibespace.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

            // VS Code Color Themes
            item {
                SectionHeader("VS CODE COLOR THEMES")
                VibeCard {
                    Column {
                        val themes = listOf(
                            Triple("vibe_dark", "Vibe Dark (Default)", "#0F1115"),
                            Triple("one_dark_pro", "One Dark Pro", "#282C34"),
                            Triple("dracula", "Dracula", "#282A36"),
                            Triple("monokai_pro", "Monokai Pro", "#2D2A2E"),
                            Triple("nord", "Nord", "#2E3440")
                        )

                        themes.forEach { (id, name, bgHex) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setEditorTheme(id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.editorThemeId == id,
                                    onClick = { viewModel.setEditorTheme(id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(bgHex)), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, VibeTheme.colors.borderSubtle, RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VibeTheme.colors.textPrimary
                                )
                            }
                        }
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

            // Execution Runtimes Section
            item {
                SectionHeader("EXECUTION RUNTIMES (TRI-MODE)")
                VibeCard {
                    Column {
                        Text(
                            text = "Tri-Mode Runner Architecture",
                            style = MaterialTheme.typography.titleSmall,
                            color = VibeTheme.colors.textPrimary
                        )
                        Text(
                            text = "Choose your preferred engine to execute code on Android",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerInstant,
                            badge = "Instant",
                            title = "Instant Embedded",
                            subtitle = "Zero download. Pre-bundled JS, Python, HTML & Shell."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerAlpine,
                            badge = "Alpine PRoot",
                            title = "Alpine Linux Subsystem",
                            subtitle = "PRoot container inside app. Real apk, pip install, and npm install."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerTermux,
                            badge = "Termux Bridge",
                            title = "Termux Bridge",
                            subtitle = "Direct bridge to Termux app for hardware-accelerated binaries."
                        )
                    }
                }
            }

            // AI Assistant Configuration (BYOK - Bring Your Own Key)
            item {
                SectionHeader("AI ASSISTANT (BYOK - BRING YOUR OWN KEY)")
                VibeCard {
                    var inputKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
                    var selectedProvider by remember(settings.aiProviderId) { mutableStateOf(settings.aiProviderId) }
                    var selectedModel by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
                    var isKeyVisible by remember { mutableStateOf(false) }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bring Your Own Key (BYOK)",
                                style = MaterialTheme.typography.titleSmall,
                                color = VibeTheme.colors.textPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (settings.aiApiKey.isNotBlank()) VibeTheme.colors.statusSuccess.copy(alpha = 0.15f) else VibeTheme.colors.statusWarning.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (settings.aiApiKey.isNotBlank()) VibeIcons.Checkmark else VibeIcons.Warning,
                                        contentDescription = null,
                                        tint = if (settings.aiApiKey.isNotBlank()) VibeTheme.colors.statusSuccess else VibeTheme.colors.statusWarning,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (settings.aiApiKey.isNotBlank()) "Configured" else "Key Required",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (settings.aiApiKey.isNotBlank()) VibeTheme.colors.statusSuccess else VibeTheme.colors.statusWarning,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Connect Google Gemini or OpenAI to activate intelligent coding, code explanation, and bug fixing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Provider Selection
                        Text(
                            text = "AI PROVIDER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VibeTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedProvider == "gemini") VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "gemini") VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "gemini"
                                        selectedModel = "gemini-2.0-flash"
                                        viewModel.setAiConfig(selectedProvider, inputKey, selectedModel)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Google Gemini", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("Free via AI Studio", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedProvider == "openai") VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "openai") VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "openai"
                                        selectedModel = "gpt-4o-mini"
                                        viewModel.setAiConfig(selectedProvider, inputKey, selectedModel)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("OpenAI / Custom", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("GPT-4o, Claude, etc.", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // API Key Input
                        Text(
                            text = "API KEY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VibeTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = {
                                inputKey = it
                                viewModel.setAiConfig(selectedProvider, it, selectedModel)
                            },
                            placeholder = { Text("Paste your API key here (e.g. AIzaSy...)") },
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = VibeTheme.colors.textSecondary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (selectedProvider == "gemini") "Get your free Gemini API key at aistudio.google.com" else "Enter your standard OpenAI or compatible endpoint API key",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibeTheme.colors.accentPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Model Selection
                        Text(
                            text = "MODEL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VibeTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val models = if (selectedProvider == "gemini") {
                            listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
                        } else {
                            listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            models.forEach { modelName ->
                                val isSelected = selectedModel == modelName
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) VibeTheme.colors.accentPrimary else VibeTheme.colors.gutterBackground,
                                    modifier = Modifier.clickable {
                                        selectedModel = modelName
                                        viewModel.setAiConfig(selectedProvider, inputKey, modelName)
                                    }
                                ) {
                                    Text(
                                        text = modelName,
                                        style = VibeCodeTypography.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.Black else VibeTheme.colors.textPrimary
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
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

@Composable
private fun RuntimeOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VibeTheme.colors.panelBackground,
        border = BorderStroke(0.5.dp, VibeTheme.colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VibeTheme.colors.accentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                }
                Text(badge, style = VibeCodeTypography.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = VibeTheme.colors.accentPrimary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
        }
    }
}
