package com.byd.wjx.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.byd.wjx.util.HomePackageResolver

object LauncherWindowClassifier {
    data class ActiveWindowSnapshot(
        val packageName: String?,
        val className: String?,
        val launcherSubpage: Boolean,
        val hasRoot: Boolean,
        val launcherOverlay: Boolean = false,
    )

    private val subpageMarkers = listOf(
        "全部应用",
        "所有应用",
        "应用列表",
        "All Apps",
        "App List",
    )
    private val overlayLock = Any()
    private var launcherOverlayActive = false
    private var launcherOverlayMissCount = 0

    fun isLauncherPackage(packageName: String?): Boolean {
        return HomePackageResolver.isKnownHomePackage(packageName?.trim())
    }

    fun inspectActiveWindow(service: AccessibilityService, fallbackPackageName: String?): ActiveWindowSnapshot {
        val root = runCatching { service.rootInActiveWindow }.getOrNull()
            ?: return ActiveWindowSnapshot(
                fallbackPackageName,
                null,
                launcherSubpage = detectLauncherOverlay(service, fallbackPackageName),
                hasRoot = false,
                launcherOverlay = currentLauncherOverlayActive(),
            )
        return try {
            val rootPackage = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: fallbackPackageName
            val rootClass = root.className?.toString()?.takeIf { it.isNotBlank() }
            val launcherOverlay = detectLauncherOverlay(service, rootPackage)
            val rootLauncherSubpage = isLauncherPackage(rootPackage) && containsSubpageMarker(root, depth = 0)
            ActiveWindowSnapshot(
                packageName = rootPackage,
                className = rootClass,
                launcherSubpage = rootLauncherSubpage || launcherOverlay,
                hasRoot = true,
                launcherOverlay = launcherOverlay,
            )
        } finally {
            root.recycle()
        }
    }

    private fun detectLauncherOverlay(service: AccessibilityService, activePackageName: String?): Boolean {
        if (activePackageName?.let(::shouldClearLauncherOverlayForPackage) == true) {
            clearLauncherOverlay()
            return false
        }
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        if (windows.isEmpty()) return updateLauncherOverlay(found = false)
        for (window in windows) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            try {
                val pkg = root.packageName?.toString()
                if (isLauncherPackage(pkg) && containsSubpageMarker(root, depth = 0)) {
                    return updateLauncherOverlay(found = true)
                }
            } finally {
                root.recycle()
            }
        }
        return updateLauncherOverlay(found = false)
    }

    private fun shouldClearLauncherOverlayForPackage(packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return false
        if (isLauncherPackage(pkg)) return false
        if (pkg == "android") return false
        if (pkg.contains("systemui", ignoreCase = true)) return false
        if (pkg.contains("widget", ignoreCase = true)) return false
        return true
    }

    private fun updateLauncherOverlay(found: Boolean): Boolean = synchronized(overlayLock) {
        if (found) {
            launcherOverlayActive = true
            launcherOverlayMissCount = 0
            true
        } else if (launcherOverlayActive) {
            launcherOverlayMissCount += 1
            if (launcherOverlayMissCount < LAUNCHER_OVERLAY_CLEAR_MISSES) {
                true
            } else {
                launcherOverlayActive = false
                launcherOverlayMissCount = 0
                false
            }
        } else {
            false
        }
    }

    private fun clearLauncherOverlay() = synchronized(overlayLock) {
        launcherOverlayActive = false
        launcherOverlayMissCount = 0
    }

    private fun currentLauncherOverlayActive(): Boolean = synchronized(overlayLock) {
        launcherOverlayActive
    }

    private fun containsSubpageMarker(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        if (matchesMarker(node.text) || matchesMarker(node.contentDescription)) return true
        val childCount = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            try {
                if (containsSubpageMarker(child, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun matchesMarker(value: CharSequence?): Boolean {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isBlank()) return false
        return subpageMarkers.any { text.contains(it, ignoreCase = true) }
    }

    private const val MAX_DEPTH = 10
    private const val MAX_CHILDREN_PER_NODE = 80
    private const val LAUNCHER_OVERLAY_CLEAR_MISSES = 2
}
