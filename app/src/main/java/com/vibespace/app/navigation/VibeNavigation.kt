package com.vibespace.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    // Instantiate view models connected to application dependencies, remembered across recompositions
    val homeViewModel = remember {
        HomeViewModel(
            workspaceManager = app.workspaceManager,
            recentProjectsRepository = app.recentProjectsRepository,
            dispatchers = app.dispatchers
        )
    }

    val editorViewModel = remember {
        EditorViewModel(
            editorEngine = app.editorEngine,
            workspaceManager = app.workspaceManager,
            languageDetector = app.languageDetector,
            dispatchers = app.dispatchers
        )
    }

    val explorerViewModel = remember {
        ExplorerViewModel(
            workspaceManager = app.workspaceManager,
            dispatchers = app.dispatchers
        )
    }

    val terminalViewModel = remember {
        TerminalViewModel(
            terminalService = app.terminalService,
            runnerManager = app.runnerManager,
            dispatchers = app.dispatchers
        )
    }

    val aiChatViewModel = remember {
        AIChatViewModel(
            aiService = app.aiService,
            editorEngine = app.editorEngine,
            dispatchers = app.dispatchers
        )
    }

    val searchViewModel = remember {
        SearchViewModel(
            editorEngine = app.editorEngine,
            dispatchers = app.dispatchers
        )
    }

    val settingsViewModel = remember {
        SettingsViewModel(
            repository = app.settingsRepository,
            dispatchers = app.dispatchers
        )
    }

    val gitViewModel = remember {
        com.vibespace.feature.explorer.git.GitViewModel(
            gitManager = app.gitManager,
            dispatchers = app.dispatchers
        )
    }

    val extensionsViewModel = remember {
        com.vibespace.feature.settings.extensions.ExtensionsViewModel(
            extensionManager = app.extensionManager,
            dispatchers = app.dispatchers
        )
    }

    val settingsState by settingsViewModel.uiState.collectAsState()
    LaunchedEffect(settingsState.settings.editorThemeId) {
        editorViewModel.setEditorTheme(settingsState.settings.editorThemeId)
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

    NavHost(
        navController = navController,
        startDestination = VibeDestination.Home.route,
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
                    navController.navigate("extensions")
                }
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
                    navController.navigate("extensions")
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

        composable("extensions") {
            com.vibespace.feature.settings.extensions.ExtensionsScreen(
                viewModel = extensionsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
