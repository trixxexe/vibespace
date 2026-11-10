package com.vibespace.app

import com.vibespace.app.navigation.VibeDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {

    @Test
    fun destinationRoutes_are_unique_and_defined() {
        val routes = listOf(
            VibeDestination.Home.route,
            VibeDestination.Editor.route,
            VibeDestination.Settings.route,
            VibeDestination.Extensions.route,
            VibeDestination.Onboarding.route
        )

        assertEquals(5, routes.distinct().size)
        assertEquals("home", VibeDestination.Home.route)
        assertEquals("editor", VibeDestination.Editor.route)
        assertEquals("settings", VibeDestination.Settings.route)
        assertEquals("extensions", VibeDestination.Extensions.route)
        assertEquals("onboarding", VibeDestination.Onboarding.route)
    }
}
