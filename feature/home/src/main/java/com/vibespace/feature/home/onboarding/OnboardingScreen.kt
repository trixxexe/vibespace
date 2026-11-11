package com.vibespace.feature.home.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Visual theme mode selection options for onboarding.
 * Self-contained to preserve feature isolation.
 */
enum class OnboardingThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Professional, native first-launch onboarding flow for VibeSpace.
 * Persistent across process restarts, resumable, and skippable at any time.
 * Requests zero upfront permissions; explains SAF when user selects device folder.
 */
@Composable
fun OnboardingScreen(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onOpenSafPicker: () -> Unit,
    onCreateNewProject: (String) -> Unit,
    onTrySampleProject: () -> Unit,
    onSelectTheme: (OnboardingThemeMode) -> Unit,
    currentTheme: OnboardingThemeMode,
    onConfigureAi: (suspend (provider: String, key: String, model: String) -> VibeResult<Unit>)? = null,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalSteps = 4
    val safeStep = currentStep.coerceIn(0, totalSteps - 1)

    if (errorMessage != null) {
        com.vibespace.core.designsystem.components.VibeDialog(
            title = "Setup Notice",
            onDismissRequest = onDismissError,
            confirmButtonText = "OK",
            onConfirm = onDismissError
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.statusError
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar: Step Progress & Skip Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = VibeIcons.Logo,
                    contentDescription = "VibeSpace",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VibeSpace Setup",
                    style = MaterialTheme.typography.titleSmall,
                    color = VibeTheme.colors.textPrimary
                )
            }

            // Step Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalSteps) {
                    val isActive = i == safeStep
                    val isPast = i < safeStep
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 6.dp)
                            .background(
                                color = when {
                                    isActive -> MaterialTheme.colorScheme.primary
                                    isPast -> VibeTheme.colors.accentSecondary
                                    else -> VibeTheme.colors.borderSubtle
                                },
                                shape = CircleShape
                            )
                    )
                }
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }

        // Main Animated Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = safeStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "OnboardingStepAnimation"
            ) { step ->
                when (step) {
                    0 -> StepWelcome(
                        onNext = { onStepChange(1) }
                    )
                    1 -> StepProjectChoice(
                        onOpenSafPicker = onOpenSafPicker,
                        onCreateNewProject = onCreateNewProject,
                        onTrySampleProject = onTrySampleProject,
                        onNext = { onStepChange(2) }
                    )
                    2 -> StepIdeTour(
                        onNext = { onStepChange(3) }
                    )
                    3 -> StepPreferencesAndFinish(
                        currentTheme = currentTheme,
                        onSelectTheme = onSelectTheme,
                        onConfigureAi = onConfigureAi,
                        onFinish = onFinish
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWelcome(
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Icon(
                imageVector = VibeIcons.Logo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Professional Android Coding IDE",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "VibeSpace is a native coding workspace designed for Android phones, tablets, foldables, and desktop-mode displays.",
                style = MaterialTheme.typography.bodyLarge,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            FeaturePillar(
                icon = VibeIcons.Editor,
                title = "100% Native Architecture",
                description = "The interface is built with native Android UI using Jetpack Compose and Material 3."
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeaturePillar(
                icon = VibeIcons.Terminal,
                title = "Script Runner & Termux Bridge",
                description = "Run supported Python and JavaScript snippets with the in-app evaluator, or use Termux when it is installed and configured. Alpine execution is unavailable in this build because it does not include PRoot."
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeaturePillar(
                icon = VibeIcons.SourceControl,
                title = "Local Snapshots & Code Checks",
                description = "Review and save workspace-local change snapshots. Built-in syntax checks are available; language-server features require a separately configured server."
            )
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Get Started", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StepProjectChoice(
    onOpenSafPicker: () -> Unit,
    onCreateNewProject: (String) -> Unit,
    onTrySampleProject: () -> Unit,
    onNext: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectNameInput by remember { mutableStateOf("") }

    if (showCreateDialog) {
        com.vibespace.core.designsystem.components.VibeDialog(
            title = "New Project Name",
            onDismissRequest = { showCreateDialog = false },
            confirmButtonText = "Create",
            isConfirmEnabled = projectNameInput.isNotBlank(),
            onConfirm = {
                val name = projectNameInput.trim()
                showCreateDialog = false
                onCreateNewProject(name)
            }
        ) {
            Column {
                Text(
                    text = "Enter a name for your new workspace:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = projectNameInput,
                    onValueChange = { projectNameInput = it },
                    placeholder = { Text("e.g. MyMobileApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Choose Workspace",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start coding immediately with a sample project, open your existing code, or initialize a new folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Sample Project
            WorkspaceOptionCard(
                icon = VibeIcons.Logo,
                badge = "Recommended for First Launch",
                title = "Explore Sample Project",
                description = "Opens sample Python and web files in VibeSpace's private workspace so you can explore the editor without changing another project.",
                onClick = onTrySampleProject
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Open Device Folder via SAF
            WorkspaceOptionCard(
                icon = VibeIcons.FolderOpen,
                badge = "Storage Access Framework",
                title = "Open Existing Folder",
                description = "Select a directory on your device using Android's Document Tree picker. You grant persistable read/write access.",
                onClick = onOpenSafPicker
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 3: Create New Project
            WorkspaceOptionCard(
                icon = VibeIcons.NewFolder,
                badge = "App Storage",
                title = "Create Empty Workspace",
                description = "Initializes a blank coding directory stored safely inside VibeSpace's private storage.",
                onClick = { showCreateDialog = true }
            )
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Continue to IDE Tour", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StepIdeTour(
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "IDE Workflows & Controls",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Here is what the current build can do and where its limits are.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            TourItem(
                icon = VibeIcons.Explorer,
                title = "File Explorer & Multi-Directory Tree",
                description = "Navigate files, create directories, rename, and manage projects. Swipe drawer on phones or side-panel on tablets."
            )

            Spacer(modifier = Modifier.height(12.dp))

            TourItem(
                icon = VibeIcons.Editor,
                title = "Multi-Tab Sora Code Canvas",
                description = "Hardware-accelerated text canvas with configurable line numbers, tab size, word wrap, and syntax highlighting."
            )

            Spacer(modifier = Modifier.height(12.dp))

            TourItem(
                icon = VibeIcons.CommandPalette,
                title = "Command Palette (Ctrl + P)",
                description = "Search file names and paths, or run available editor commands. Open it from the top bar or with Ctrl+P on a hardware keyboard."
            )

            Spacer(modifier = Modifier.height(12.dp))

            TourItem(
                icon = VibeIcons.Terminal,
                title = "Runner Output & Problems",
                description = "View output from the selected runner and lightweight syntax diagnostics. The in-app command interpreter supports a limited set of commands; it is not a general Linux shell or build system."
            )

            Spacer(modifier = Modifier.height(12.dp))

            TourItem(
                icon = VibeIcons.SourceControl,
                title = "Local Change Tracking",
                description = "Review workspace changes, stage them into local snapshots, and inspect line-by-line diffs. This is not a Git repository and does not provide branches or remote sync."
            )
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Next: Preferences & Setup", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StepPreferencesAndFinish(
    currentTheme: OnboardingThemeMode,
    onSelectTheme: (OnboardingThemeMode) -> Unit,
    onConfigureAi: (suspend (provider: String, key: String, model: String) -> VibeResult<Unit>)?,
    onFinish: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Preferences & Security",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fine-tune your visual theme and optional AI coding assistant.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Theme Selection Row
            Text(
                text = "THEME PREFERENCE",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOptionPill(
                    title = "Dark (Default)",
                    isSelected = currentTheme == OnboardingThemeMode.DARK,
                    onClick = { onSelectTheme(OnboardingThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionPill(
                    title = "Light",
                    isSelected = currentTheme == OnboardingThemeMode.LIGHT,
                    onClick = { onSelectTheme(OnboardingThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionPill(
                    title = "System",
                    isSelected = currentTheme == OnboardingThemeMode.SYSTEM,
                    onClick = { onSelectTheme(OnboardingThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Coding Assistant (Optional BYOK)
            Text(
                text = "AI CODING ASSISTANT (OPTIONAL BYOK)",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            VibeCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = VibeIcons.AI,
                            contentDescription = null,
                            tint = VibeTheme.colors.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bring Your Own Key",
                            style = MaterialTheme.typography.titleSmall,
                            color = VibeTheme.colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AI is optional. Connect a Gemini key now, or choose another provider later in Settings. Provider limits and charges may apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Enter API Key (or leave blank to skip)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saveError != null) {
                        Text(
                            text = saveError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (apiKeyInput.isBlank()) {
                            onFinish()
                            return@launch
                        }
                        isSaving = true
                        saveError = null
                        try {
                            val saveResult = onConfigureAi?.invoke(
                                "gemini",
                                apiKeyInput.trim(),
                                "gemini-3.8-flash"
                            ) ?: VibeResult.Error("AI settings are unavailable. Clear the key to continue without AI.")
                            when (saveResult) {
                                is VibeResult.Success -> onFinish()
                                is VibeResult.Error -> saveError = "Could not save the API key: ${saveResult.message}"
                                else -> saveError = "Could not save the API key."
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            saveError = "Could not save the API key: ${failure.message ?: "unexpected error"}"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (isSaving) "Saving secure settings…" else "Finish Setup & Start Coding",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun FeaturePillar(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VibeTheme.colors.panelBackground,
            border = BorderStroke(0.5.dp, VibeTheme.colors.borderSubtle),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VibeTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun WorkspaceOptionCard(
    icon: ImageVector,
    badge: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    VibeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VibeTheme.colors.activeLineBackground,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VibeTheme.colors.textPrimary
                    )
                }
                Text(
                    text = badge,
                    style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = VibeTheme.colors.accentSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TourItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    VibeCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VibeTheme.colors.accentPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = VibeTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else VibeTheme.colors.panelBackground,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else VibeTheme.colors.borderSubtle
        ),
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else VibeTheme.colors.textSecondary
            )
        }
    }
}
