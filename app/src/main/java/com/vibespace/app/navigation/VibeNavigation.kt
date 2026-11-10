package com.vibespace.app.navigation

import androidx.compose.runtime.Composable
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
    // Instantiate view models connected to application dependencies
    val homeViewModel = HomeViewModel(
        workspaceManager = app.workspaceManager,
        recentProjectsRepository = app.recentProjectsRepository,
        dispatchers = app.dispatchers
    )

    val editorViewModel = EditorViewModel(
        editorEngine = app.editorEngine,
        workspaceManager = app.workspaceManager,
        languageDetector = app.languageDetector,
        dispatchers = app.dispatchers
    )

    val explorerViewModel = ExplorerViewModel(
        workspaceManager = app.workspaceManager,
        dispatchers = app.dispatchers
    )

    val terminalViewModel = TerminalViewModel(
        terminalService = app.terminalService,
        runnerManager = app.runnerManager,
        dispatchers = app.dispatchers
    )

    val aiChatViewModel = AIChatViewModel(
        aiService = app.aiService,
        editorEngine = app.editorEngine,
        dispatchers = app.dispatchers
    )

    val searchViewModel = SearchViewModel(
        editorEngine = app.editorEngine,
        dispatchers = app.dispatchers
    )

    val settingsViewModel = SettingsViewModel(
        repository = app.settingsRepository,
        dispatchers = app.dispatchers
    )

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
                onNavigateToHome = {
                    navController.navigate(VibeDestination.Home.route)
                },
                onNavigateToSettings = {
                    navController.navigate(VibeDestination.Settings.route)
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
    }
}
