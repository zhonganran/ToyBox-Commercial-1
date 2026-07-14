package com.byd.wjx.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object HomePackageResolver {
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
        return pkg in resolvedPackages || isKnownHomePackage(pkg)
    }

    fun isKnownHomePackage(packageName: String?): Boolean {
        val pkg = packageName.orEmpty()
        return pkg in knownHomePackages ||
            (pkg.contains("launcher", ignoreCase = true) &&
                !pkg.contains("launchermap", ignoreCase = true))
    }

    internal fun resolvedPackages(defaultHomePackage: String?): Set<String> = buildSet {
        addAll(knownHomePackages)
        defaultHomePackage
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "android" }
            ?.let(::add)
    }
}
