package com.vibespace.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vibespace.app.VibeSpaceApplication
import com.vibespace.app.ui.VibeMainScreen
import com.vibespace.feature.aichat.AIChatViewModel
import com.vibespace.feature.editor.EditorViewModel
import com.vibespace.feature.explorer.ExplorerViewModel
import com.vibespace.feature.home.HomeScreen
import com.vibespace.feature.home.HomeViewModel
import com.vibespace.feature.home.onboarding.OnboardingScreen
import com.vibespace.feature.search.SearchViewModel
import com.vibespace.feature.settings.SettingsScreen
import com.vibespace.feature.settings.SettingsViewModel
import com.vibespace.feature.terminal.TerminalViewModel

@Composable
fun VibeNavigation(
    app: VibeSpaceApplication,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val factory = remember(app) { VibeViewModelFactory(app) }

    val homeViewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val editorViewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val explorerViewModel: ExplorerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val terminalViewModel: TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val aiChatViewModel: AIChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val gitViewModel: com.vibespace.feature.explorer.git.GitViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val extensionsViewModel: com.vibespace.feature.settings.extensions.ExtensionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)

    val settingsState by settingsViewModel.uiState.collectAsState()
    LaunchedEffect(
        settingsState.settings.editorThemeId,
        settingsState.settings.editorFontSizeSp,
        settingsState.settings.tabSizeSpaces,
        settingsState.settings.showLineNumbers,
        settingsState.settings.wordWrap,
        settingsState.settings.autoSave
    ) {
        editorViewModel.updateEditorPreferences(
            fontSizeSp = settingsState.settings.editorFontSizeSp,
            tabSize = settingsState.settings.tabSizeSpaces,
            showLineNumbers = settingsState.settings.showLineNumbers,
            wordWrap = settingsState.settings.wordWrap,
            themeId = settingsState.settings.editorThemeId,
            autoSave = settingsState.settings.autoSave
        )
    }

    LaunchedEffect(
        settingsState.settings.aiProviderId,
        settingsState.settings.aiApiKey,
        settingsState.settings.aiModel,
        settingsState.settings.aiCustomEndpoint
    ) {
        app.aiService.updateConfiguration(
            com.vibespace.core.ai.model.AIConfiguration(
                providerId = settingsState.settings.aiProviderId,
                apiKey = settingsState.settings.aiApiKey,
                model = settingsState.settings.aiModel,
                customEndpoint = settingsState.settings.aiCustomEndpoint
            )
        )
    }

    if (!settingsState.isInitialized) {
        androidx.compose.material3.Surface(
            modifier = modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = com.vibespace.core.designsystem.components.VibeIcons.Logo,
                    contentDescription = "VibeSpace",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        return
    }

    val startDestination = if (settingsState.settings.hasCompletedOnboarding) {
        VibeDestination.Home.route
    } else {
        VibeDestination.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(VibeDestination.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToEditor = {
                    navController.navigate(VibeDestination.Editor.route)
                },
                onNavigateToSettings = {
                    navController.navigate(VibeDestination.Settings.route)
                },
                onNavigateToExtensions = {
                    navController.navigate(VibeDestination.Extensions.route)
                },
                onNavigateToOnboarding = {
                    navController.navigate(VibeDestination.Onboarding.route)
                }
            )
        }

        composable(VibeDestination.Onboarding.route) {
            val context = LocalContext.current
            var onboardingError by remember { mutableStateOf<String?>(null) }
            val openTreeLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { treeUri: Uri? ->
                if (treeUri != null) {
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(treeUri, flags)
                    } catch (e: SecurityException) {
                        onboardingError = "Storage Access Framework permission error: ${e.message}"
                        return@rememberLauncherForActivityResult
                    } catch (t: Throwable) {
                        onboardingError = "Failed to access folder: ${t.message}"
                        return@rememberLauncherForActivityResult
                    }

                    val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (folderDoc == null || !folderDoc.canRead()) {
                        onboardingError = "Selected folder cannot be read or permission was denied."
                        return@rememberLauncherForActivityResult
                    }

                    val folderName = folderDoc.name ?: "Workspace"
                    homeViewModel.openSafTreeUri(
                        treeUri = treeUri,
                        folderName = folderName,
                        onProjectReady = {
                            settingsViewModel.setOnboardingCompleted(true)
                            navController.navigate(VibeDestination.Editor.route) {
                                popUpTo(VibeDestination.Home.route) { inclusive = false }
                            }
                        },
                        onProjectError = { err ->
                            onboardingError = err
                        }
                    )
                }
            }

            OnboardingScreen(
                currentStep = settingsState.settings.onboardingStep,
                onStepChange = { step ->
                    settingsViewModel.setOnboardingStep(step)
                },
                onFinish = {
                    settingsViewModel.setOnboardingCompleted(true)
                    navController.navigate(VibeDestination.Home.route) {
                        popUpTo(VibeDestination.Onboarding.route) { inclusive = true }
                    }
                },
                onSkip = {
                    settingsViewModel.setOnboardingCompleted(true)
                    navController.navigate(VibeDestination.Home.route) {
                        popUpTo(VibeDestination.Onboarding.route) { inclusive = true }
                    }
                },
                onOpenSafPicker = {
                    openTreeLauncher.launch(null)
                },
                onCreateNewProject = { name ->
                    homeViewModel.createNewProject(name) {
                        settingsViewModel.setOnboardingCompleted(true)
                        navController.navigate(VibeDestination.Editor.route) {
                            popUpTo(VibeDestination.Home.route) { inclusive = false }
                        }
                    }
                },
                onTrySampleProject = {
                    homeViewModel.openDemoProject {
                        settingsViewModel.setOnboardingCompleted(true)
                        navController.navigate(VibeDestination.Editor.route) {
                            popUpTo(VibeDestination.Home.route) { inclusive = false }
                        }
                    }
                },
                currentTheme = when (settingsState.settings.themeMode) {
                    com.vibespace.feature.settings.repository.ThemeMode.LIGHT -> com.vibespace.feature.home.onboarding.OnboardingThemeMode.LIGHT
                    com.vibespace.feature.settings.repository.ThemeMode.SYSTEM -> com.vibespace.feature.home.onboarding.OnboardingThemeMode.SYSTEM
                    else -> com.vibespace.feature.home.onboarding.OnboardingThemeMode.DARK
                },
                onSelectTheme = { onboardingMode ->
                    val themeMode = when (onboardingMode) {
                        com.vibespace.feature.home.onboarding.OnboardingThemeMode.LIGHT -> com.vibespace.feature.settings.repository.ThemeMode.LIGHT
                        com.vibespace.feature.home.onboarding.OnboardingThemeMode.SYSTEM -> com.vibespace.feature.settings.repository.ThemeMode.SYSTEM
                        else -> com.vibespace.feature.settings.repository.ThemeMode.DARK
                    }
                    settingsViewModel.setThemeMode(themeMode)
                },
                onConfigureAi = { provider, key, model ->
                    app.settingsRepository.setAiConfig(provider, key, model)
                },
                errorMessage = onboardingError,
                onDismissError = { onboardingError = null }
            )
        }

        composable(VibeDestination.Editor.route) {
            VibeMainScreen(
                editorViewModel = editorViewModel,
                explorerViewModel = explorerViewModel,
                terminalViewModel = terminalViewModel,
                aiChatViewModel = aiChatViewModel,
                searchViewModel = searchViewModel,
                gitViewModel = gitViewModel,
                onNavigateToHome = {
                    navController.navigate(VibeDestination.Home.route)
                },
                onNavigateToSettings = {
                    navController.navigate(VibeDestination.Settings.route)
                },
                onNavigateToExtensions = {
                    navController.navigate(VibeDestination.Extensions.route)
                }
            )
        }

        composable(VibeDestination.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(VibeDestination.Extensions.route) {
            com.vibespace.feature.settings.extensions.ExtensionsScreen(
                viewModel = extensionsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
