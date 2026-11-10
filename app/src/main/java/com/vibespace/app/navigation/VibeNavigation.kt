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
                    navController.navigate(VibeDestination.Extensions.route)
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
