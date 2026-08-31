package com.byd.wjx.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {

    @Test
    fun `every destination route is unique`() {
        val routes = AppDestination.entries.map(AppDestination::route)

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `detail destinations retain their navigation section`() {
        assertEquals(Screen.Home, AppDestination.TRIP_MAP.section)
        assertEquals(Screen.Home, AppDestination.CHARGES.section)
        assertEquals(Screen.Settings, AppDestination.BLIND_SPOT_SETTINGS.section)
        assertTrue(AppDestination.TRIP_MAP.isDetail)
        assertTrue(AppDestination.BLIND_SPOT_SETTINGS.isDetail)
        assertFalse(AppDestination.SETTINGS.isDetail)
    }

    @Test
    fun `unknown routes fall back to home`() {
        assertEquals(AppDestination.HOME, AppDestination.fromRoute("missing"))
        assertEquals(AppDestination.HOME, AppDestination.rootForRoute("missing"))
    }
}
