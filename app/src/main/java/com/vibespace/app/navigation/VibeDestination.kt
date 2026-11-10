package com.vibespace.app.navigation

/**
 * Type-safe navigation routes for VibeSpace.
 */
sealed class VibeDestination(val route: String) {
    data object Home : VibeDestination("home")
    data object Editor : VibeDestination("editor")
    data object Settings : VibeDestination("settings")
    data object Extensions : VibeDestination("extensions")
}
