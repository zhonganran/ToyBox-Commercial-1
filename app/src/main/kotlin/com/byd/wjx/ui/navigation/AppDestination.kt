package com.byd.wjx.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "首页", Icons.Outlined.Home),
    Sound("sound", "个性音效", Icons.AutoMirrored.Outlined.VolumeUp),
    DesktopComponents("desktop_components", "桌面组件", Icons.Outlined.Apps),
    Automation("automation", "智能联动", Icons.Outlined.Bolt),
    Settings("settings", "设置", Icons.Outlined.Settings),
}

/** A single source of truth for root screens and their detail destinations. */
enum class AppDestination(
    val route: String,
    val section: Screen,
) {
    HOME(Screen.Home.route, Screen.Home),
    SOUND(Screen.Sound.route, Screen.Sound),
    DESKTOP_COMPONENTS(Screen.DesktopComponents.route, Screen.DesktopComponents),
    AUTOMATION(Screen.Automation.route, Screen.Automation),
    SETTINGS(Screen.Settings.route, Screen.Settings),
    BLIND_SPOT_SETTINGS("blind_spot_settings", Screen.Settings),
    TRIP_MAP("trip_map", Screen.Home),
    CHARGES("charges", Screen.Home),
    ENERGY_RECOVERY("energy_recovery", Screen.Home),
    VEHICLE_SYSTEM("vehicle_system", Screen.Home),
    BATTERY_HEALTH("battery_health", Screen.Home),
    VEHICLE_ENVIRONMENT("vehicle_environment", Screen.Home),
    ;

    val isDetail: Boolean
        get() = route != section.route

    companion object {
        private val destinationsByRoute = entries.associateBy(AppDestination::route)

        fun fromRoute(route: String): AppDestination = destinationsByRoute[route] ?: HOME

        fun rootForRoute(route: String): AppDestination =
            Screen.entries.firstOrNull { it.route == route }
                ?.let { fromRoute(it.route) }
                ?: HOME
    }
}
