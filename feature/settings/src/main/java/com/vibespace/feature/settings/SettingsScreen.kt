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
import com.vibespace.core.designsystem.components.VibeIcons
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
                    imageVector = VibeIcons.Back,
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
                SectionHeader("EXECUTION OPTIONS")
                VibeCard {
                    Column {
                        Text(
                            text = "Available execution options",
                            style = MaterialTheme.typography.titleSmall,
                            color = VibeTheme.colors.textPrimary
                        )
                        Text(
                            text = "Embedded evaluation is limited to supported Python and JavaScript snippets. Alpine downloads a Linux userspace; Termux runs separately and requires the Termux app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerInstant,
                            badge = "Instant",
                            title = "Instant Embedded",
                            subtitle = "Limited Python and JavaScript evaluators with safe built-in commands. Not full language runtimes."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerAlpine,
                            badge = "Optional download",
                            title = "Alpine Linux Subsystem",
                            subtitle = "Downloads a checksum-verified PRoot runtime and Alpine userspace on first setup. Requires a supported device ABI and network access."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerTermux,
                            badge = "Termux Bridge",
                            title = "Termux Bridge",
                            subtitle = "Requires Termux installed, its RUN_COMMAND permission enabled, and project files accessible to Termux."
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
                    var inputEndpoint by remember(settings.aiCustomEndpoint) { mutableStateOf(settings.aiCustomEndpoint) }
                    var isKeyVisible by remember { mutableStateOf(false) }
                    val aiConfigChanged = inputKey != settings.aiApiKey ||
                            selectedProvider != settings.aiProviderId ||
                            selectedModel != settings.aiModel ||
                            inputEndpoint != settings.aiCustomEndpoint

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
                                        selectedModel = "gemini-3.8-flash"
                                        inputEndpoint = ""
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Google Gemini", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("Official Gemini Studio", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
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
                                        inputEndpoint = "https://api.openai.com/v1/chat/completions"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("OpenAI / Custom", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("GPT-4o or Proxy", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedProvider == "openai" && inputEndpoint.contains("deepseek")) VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "openai" && inputEndpoint.contains("deepseek")) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "openai"
                                        selectedModel = "deepseek-chat"
                                        inputEndpoint = "https://api.deepseek.com/chat/completions"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("DeepSeek", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("DeepSeek-V3/R1", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedProvider == "openai" && inputEndpoint.contains("groq")) VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "openai" && inputEndpoint.contains("groq")) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "openai"
                                        selectedModel = "llama-3.3-70b-versatile"
                                        inputEndpoint = "https://api.groq.com/openai/v1/chat/completions"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Groq", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("Ultra-fast Llama 3", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
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
                            },
                            placeholder = { Text("Paste your API key here (e.g. AIzaSy...)") },
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) VibeIcons.VisibilityOff else VibeIcons.Visibility,
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
                            text = if (selectedProvider == "gemini") "Create a Gemini API key in Google AI Studio. Provider usage limits and charges may apply." else "Enter an API key for OpenAI or your compatible provider.",
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

                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it.trim() },
                            placeholder = { Text(if (selectedProvider == "gemini") "gemini-3.8-flash" else "gpt-4o-mini") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selectedProvider == "openai") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "COMPATIBLE ENDPOINT (OPTIONAL)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = VibeTheme.colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inputEndpoint,
                            onValueChange = { inputEndpoint = it },
                                placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.aiConfigError != null) {
                            Text(
                                text = uiState.aiConfigError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.setAiConfig(selectedProvider, inputKey, selectedModel, inputEndpoint) },
                            enabled = aiConfigChanged && !uiState.isSavingAiConfig && selectedModel.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(if (uiState.isSavingAiConfig) "Saving…" else "Save AI configuration")
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
