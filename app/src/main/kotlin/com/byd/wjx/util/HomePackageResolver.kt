package com.byd.wjx.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object HomePackageResolver {
    private val excludedHomePackages = setOf(
        "com.byd.launchermap",
    )

    private val knownHomePackages = setOf(
        "com.android.launcher",
        "com.android.launcher3",
        "com.byd.launcher",
        "com.byd.launcher3",
        "com.byd.carlauncher",
        "com.byd.home",
        "com.uncube.launcher3",
        "com.bluestacks.launcher",
        "com.bluestacks.home",
        "com.bluestacks.gamepophome",
    )

    fun resolve(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = runCatching {
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeIf { it.isNotBlank() && it != "android" }
        }.getOrNull()
        return resolvedPackages(defaultHome)
    }

    fun isHomePackage(packageName: String?, resolvedPackages: Set<String>): Boolean {
        val pkg = packageName.orEmpty()
        if (pkg in excludedHomePackages) return false
        return pkg in resolvedPackages || isKnownHomePackage(pkg)
    }

    fun isKnownHomePackage(packageName: String?): Boolean {
        val pkg = packageName.orEmpty()
        if (pkg in excludedHomePackages) return false
        return pkg in knownHomePackages ||
            (pkg.contains("launcher", ignoreCase = true) &&
                !pkg.contains("launchermap", ignoreCase = true))
    }

    /** Launcher-owned pages that cover the wallpaper desktop and must not keep overlays visible. */
    fun isLauncherSubpage(className: String?): Boolean {
        val cls = className?.trim().orEmpty()
        if (cls.isBlank() || isFrameworkSurfaceClass(cls)) return false
        return launcherSubpageKeywords.any { keyword -> cls.contains(keyword, ignoreCase = true) }
    }

    private fun isFrameworkSurfaceClass(className: String): Boolean =
        className.startsWith("android.widget.") ||
            className.startsWith("android.view.") ||
            className == "androidx.compose.ui.platform.ComposeView"

    private val launcherSubpageKeywords = listOf(
        "Setting",
        "AppList",
        "AppGrid",
        "AllApps",
        "AppsPage",
        "AppCenter",
        "ApplicationCenter",
        "AppStore",
        "AppDrawer",
        "AppManage",
        "ApplicationList",
        "SoftwareList",
        "EditPage",
        "WidgetPage",
        "AppWidget",
        "WidgetHost",
        "Notification",
        "VehicleView",
        "SurroundView",
        "Panorama",
        "Car360",
        "Navigation",
        "MapActivity",
        "MainMap",
        "MapPage",
        "Route",
    )

    internal fun resolvedPackages(defaultHomePackage: String?): Set<String> = buildSet {
        addAll(knownHomePackages)
        defaultHomePackage
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "android" }
            ?.let(::add)
    }
}
