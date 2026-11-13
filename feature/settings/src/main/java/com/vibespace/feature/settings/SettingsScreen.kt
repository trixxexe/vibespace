package com.vibespace.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.common.notification.VibeNotificationHelper
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.feature.settings.repository.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenGuide: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    BackHandler {
        onNavigateBack()
    }

    var notificationsGranted by remember {
        mutableStateOf(VibeNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            VibeNotificationHelper.notify(
                context = context,
                title = "VibeSpace Notifications Active",
                message = "You will now receive alerts for Git clones, builds, and file exports."
            )
        } else {
            Toast.makeText(context, "Notification permission was not granted", Toast.LENGTH_SHORT).show()
        }
    }

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
                            title = "Blue VS Code Theme",
                            subtitle = "Classic Tomorrow Night Blue IDE & editor palette (#002451)",
                            isSelected = settings.themeMode == ThemeMode.BLUE_VSC,
                            onClick = { viewModel.setThemeMode(ThemeMode.BLUE_VSC) }
                        )
                        ThemeOptionRow(
                            title = "Light Theme",
                            subtitle = "High-contrast light surfaces and VS Code Light+ editor",
                            isSelected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        )
                        ThemeOptionRow(
                            title = "Follow System",
                            subtitle = "Matches Android system dark/light theme settings",
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
                            Triple("vscode_blue", "VS Code Blue (Tomorrow Night Blue)", "#002451"),
                            Triple("vibe_light", "Vibe Light (VS Code Light+)", "#FFFFFF"),
                            Triple("one_dark_pro", "One Dark Pro", "#282C34"),
                            Triple("dracula", "Dracula", "#282A36"),
                            Triple("monokai_pro", "Monokai Pro", "#2D2A2E"),
                            Triple("nord", "Nord", "#2E3440"),
                            Triple("github_dark", "GitHub Dark Default", "#0D1117"),
                            Triple("tokyo_night", "Tokyo Night", "#1A1B26"),
                            Triple("catppuccin_mocha", "Catppuccin Mocha", "#1E1E2E"),
                            Triple("solarized_light", "Solarized Light", "#FDF6E3")
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
                                        .background(Color(android.graphics.Color.parseColor(bgHex)), RoundedCornerShape(4.dp))
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

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Tab Size", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                        Text("Number of spaces per indentation level", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 8).forEach { size ->
                                val selected = settings.tabSizeSpaces == size
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) VibeTheme.colors.accentPrimary.copy(alpha = 0.2f) else VibeTheme.colors.gutterBackground,
                                    border = BorderStroke(1.dp, if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                    modifier = Modifier.clickable { viewModel.setTabSize(size) }
                                ) {
                                    Text(
                                        text = "$size spaces",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Font Family", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                        Text("Code editor typeface family", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Monospace", "Fira Code", "JetBrains Mono", "System").forEach { font ->
                                val selected = settings.fontFamily == font
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) VibeTheme.colors.accentPrimary.copy(alpha = 0.2f) else VibeTheme.colors.gutterBackground,
                                    border = BorderStroke(1.dp, if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                    modifier = Modifier.clickable { viewModel.setFontFamily(font) }
                                ) {
                                    Text(
                                        text = font,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Auto-Save Interval", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                        Text("Automatically persist modified files in background", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Off", "5s", "10s", "30s").forEach { interval ->
                                val selected = settings.autoSaveInterval == interval
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) VibeTheme.colors.accentPrimary.copy(alpha = 0.2f) else VibeTheme.colors.gutterBackground,
                                    border = BorderStroke(1.dp, if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                    modifier = Modifier.clickable { viewModel.setAutoSaveInterval(interval) }
                                ) {
                                    Text(
                                        text = interval,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Cursor Style", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                        Text("Caret appearance in editor buffer", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Line", "Block", "Underline").forEach { style ->
                                val selected = settings.cursorStyle == style
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) VibeTheme.colors.accentPrimary.copy(alpha = 0.2f) else VibeTheme.colors.gutterBackground,
                                    border = BorderStroke(1.dp, if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                    modifier = Modifier.clickable { viewModel.setCursorStyle(style) }
                                ) {
                                    Text(
                                        text = style,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (selected) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Format On Save", style = MaterialTheme.typography.bodyMedium, color = VibeTheme.colors.textPrimary)
                                Text("Automatically format code upon saving document", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                            }
                            Switch(
                                checked = settings.formatOnSave,
                                onCheckedChange = { viewModel.toggleFormatOnSave(it) }
                            )
                        }
                    }
                }
            }

            // Notifications & Permissions Section
            item {
                SectionHeader("NOTIFICATIONS & PERMISSIONS")
                VibeCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "IDE Task & Git Notifications",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VibeTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Receive system alerts when Git clones, file exports, or background builds complete.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VibeTheme.colors.textSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (notificationsGranted) VibeTheme.colors.statusSuccess.copy(alpha = 0.15f) else VibeTheme.colors.statusWarning.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (notificationsGranted) "Granted" else "Permission Needed",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (notificationsGranted) VibeTheme.colors.statusSuccess else VibeTheme.colors.statusWarning
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(
                                    onClick = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Allow Notifications")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!VibeNotificationHelper.hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val sent = VibeNotificationHelper.notify(
                                            context = context,
                                            title = "VibeSpace IDE Ready",
                                            message = "Notifications are active for Git clones, Alpine Linux tasks, and file exports."
                                        )
                                        Toast.makeText(
                                            context,
                                            if (sent) "Test notification sent!" else "Enable notifications in Android Settings.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Send Test Notification")
                            }
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
                            text = "Alpine Linux Subsystem provides a full bundled PRoot environment with Python 3, Node.js, BusyBox, and apk package manager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerAlpine,
                            badge = "Bundled + Self-Healing",
                            title = "Alpine Linux Subsystem",
                            subtitle = "Full PRoot Linux userspace with Python 3, Node.js, BusyBox, and apk package manager ready out of the box."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerInstant,
                            badge = "Instant",
                            title = "Instant Embedded",
                            subtitle = "Fast embedded Python, JavaScript, and POSIX shell evaluator with zero container startup overhead."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        RuntimeOptionRow(
                            icon = VibeIcons.RunnerTermux,
                            badge = "Termux Bridge",
                            title = "Termux Bridge",
                            subtitle = "Optional external execution bridge when the Termux app and RUN_COMMAND permission are installed."
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

                    val providerModels = mapOf(
                        "gemini" to listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro"),
                        "openai" to listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
                        "anthropic" to listOf("claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
                        "deepseek" to listOf("deepseek-chat", "deepseek-reasoner"),
                        "groq" to listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
                        "openrouter" to listOf("anthropic/claude-3.7-sonnet", "google/gemini-2.5-flash", "openai/gpt-4o-mini", "deepseek/deepseek-chat")
                    )

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
                                        text = if (settings.aiApiKey.isNotBlank()) "Configured (${settings.aiProviderId})" else "Key Required",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (settings.aiApiKey.isNotBlank()) VibeTheme.colors.statusSuccess else VibeTheme.colors.statusWarning,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Connect Google Gemini, OpenAI, Anthropic Claude, DeepSeek, Groq, or OpenRouter.",
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
                                        selectedModel = "gemini-2.5-flash"
                                        inputEndpoint = ""
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Google Gemini", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("Gemini 2.5 Flash / Pro", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
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
                                    Text("GPT-4o / Compatible", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
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
                                color = if (selectedProvider == "deepseek") VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "deepseek") VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "deepseek"
                                        selectedModel = "deepseek-chat"
                                        inputEndpoint = "https://api.deepseek.com/chat/completions"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("DeepSeek", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("DeepSeek V3 / R1", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedProvider == "groq") VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "groq") VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "groq"
                                        selectedModel = "llama-3.3-70b-versatile"
                                        inputEndpoint = "https://api.groq.com/openai/v1/chat/completions"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Groq", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("Ultra-fast Llama 3.3", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
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
                                color = if (selectedProvider == "anthropic") VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "anthropic") VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "anthropic"
                                        selectedModel = "claude-3-7-sonnet-latest"
                                        inputEndpoint = "https://api.anthropic.com/v1/messages"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Anthropic Claude", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("Claude 3.7 / 3.5 Sonnet", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedProvider == "openrouter") VibeTheme.colors.accentPrimary.copy(alpha = 0.15f) else VibeTheme.colors.gutterBackground,
                                border = BorderStroke(1.dp, if (selectedProvider == "openrouter") VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = "openrouter"
                                        selectedModel = "anthropic/claude-3.7-sonnet"
                                        inputEndpoint = "https://openrouter.ai/api/v1/chat/completions"
                                    }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("OpenRouter", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                    Text("All Models Unified", style = MaterialTheme.typography.labelSmall, color = VibeTheme.colors.textMuted)
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
                            onValueChange = { inputKey = it },
                            placeholder = { Text("Paste your $selectedProvider API key here...") },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Model Selection
                        Text(
                            text = "MODEL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VibeTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val suggestedModels = providerModels[selectedProvider].orEmpty()
                        if (suggestedModels.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                suggestedModels.forEach { modelOption ->
                                    val isCurrentModel = selectedModel == modelOption
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isCurrentModel) VibeTheme.colors.accentPrimary.copy(alpha = 0.2f) else VibeTheme.colors.gutterBackground,
                                        border = BorderStroke(1.dp, if (isCurrentModel) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                        modifier = Modifier.clickable { selectedModel = modelOption }
                                    ) {
                                        Text(
                                            text = modelOption,
                                            style = VibeCodeTypography.copy(fontSize = 11.sp, fontWeight = if (isCurrentModel) FontWeight.Bold else FontWeight.Normal),
                                            color = if (isCurrentModel) VibeTheme.colors.accentPrimary else VibeTheme.colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it.trim() },
                            placeholder = { Text(if (selectedProvider == "gemini") "gemini-2.5-flash" else "gpt-4o-mini") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selectedProvider != "gemini") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "API ENDPOINT (OPTIONAL)",
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
                            Text(if (uiState.isSavingAiConfig) "Saving…" else "Save AI Configuration")
                        }
                    }
                }
            }

            // About VibeSpace & StarDevs Credits
            item {
                SectionHeader("ABOUT VIBESPACE & STARDEVS")
                VibeCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "VibeSpace Mobile IDE",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Version ${uiState.appVersion} (Build ${uiState.buildNumber}) • Developed by StarDevs",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = VibeTheme.colors.textPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VibeTheme.colors.accentPrimary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, VibeTheme.colors.accentPrimary)
                            ) {
                                Text(
                                    text = "v${uiState.appVersion}",
                                    style = VibeCodeTypography.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = VibeTheme.colors.accentPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Native Android coding IDE built with Jetpack Compose, Material 3, Sora Editor, JGit, and an embedded Alpine Linux PRoot container.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VibeTheme.colors.gutterBackground,
                            border = BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "STARDEVS CORE TEAM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = VibeTheme.colors.accentPrimary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Studio / Organization", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                                    Text("StarDevs", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Lead Developer & Systems Architect", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                                    Text("Ritam", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Product Planning, QA, UX & Testing", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                                    Text("Ankit", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Community Discord", style = MaterialTheme.typography.bodySmall, color = VibeTheme.colors.textSecondary)
                                    Text("discord.gg/stardevs", style = VibeCodeTypography.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = VibeTheme.colors.accentPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString("https://discord.gg/stardevs"))
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/stardevs"))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "Copied discord.gg/stardevs to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Join Discord (StarDevs)")
                            }

                            if (onOpenGuide != null) {
                                OutlinedButton(
                                    onClick = onOpenGuide,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open A-to-Z Guide")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Kotlin ${uiState.kotlinVersion} • Jetpack Compose • Material 3 • StarDevs",
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
