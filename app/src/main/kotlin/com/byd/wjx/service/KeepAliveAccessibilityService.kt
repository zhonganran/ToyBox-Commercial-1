package com.byd.wjx.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.overlay.DesktopOverlayVisibilityEvents

/**
 * User-visible keepalive binding. It only consumes window-state package/class
 * metadata, matching AutoTark's desktop-overlay foreground tracking approach.
 */
class KeepAliveAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val launcherRechecks = mutableListOf<Runnable>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        WjxFileLogger.i(TAG, "connected")
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        ChainLog.append(this, "KeepAliveAccessibility connected")
        KeepAliveService.start(applicationContext, "a11y_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val eventPackageName = event.packageName?.toString()
        val eventClassName = event.className?.toString()
        val snapshot = LauncherWindowClassifier.inspectActiveWindow(this, eventPackageName)
        val packageName = snapshot.packageName ?: eventPackageName
        val className = snapshot.className ?: eventClassName
        DesktopOverlayVisibilityEvents.notifyAccessibilityForeground(
            applicationContext,
            packageName,
            className,
            launcherSubpage = snapshot.launcherSubpage,
            launcherOverlay = snapshot.launcherOverlay,
            source = DesktopOverlayVisibilityEvents.A11Y_SOURCE_KEEPALIVE,
        )
        scheduleLauncherRechecks(packageName)
    }

    override fun onInterrupt() {
        // Intentionally no-op.
    }

    override fun onDestroy() {
        isConnected = false
        clearLauncherRechecks()
        DesktopOverlayVisibilityEvents.clearAccessibilityForeground(DesktopOverlayVisibilityEvents.A11Y_SOURCE_KEEPALIVE)
        WjxFileLogger.i(TAG, "destroyed")
        ChainLog.append(this, "KeepAliveAccessibility destroyed")
        KeepAliveService.delayedKick(applicationContext, "a11y_destroyed")
        super.onDestroy()
    }

    private fun scheduleLauncherRechecks(packageName: String?) {
        clearLauncherRechecks()
        if (!LauncherWindowClassifier.isLauncherPackage(packageName)) return
        LAUNCHER_SETTLE_RECHECK_MS.forEach { delayMs ->
            val runnable = Runnable {
                val snapshot = LauncherWindowClassifier.inspectActiveWindow(this, packageName)
                DesktopOverlayVisibilityEvents.notifyAccessibilityForeground(
                    applicationContext,
                    snapshot.packageName,
                    snapshot.className,
                    launcherSubpage = snapshot.launcherSubpage,
                    launcherOverlay = snapshot.launcherOverlay,
                    source = DesktopOverlayVisibilityEvents.A11Y_SOURCE_KEEPALIVE,
                )
            }
            launcherRechecks += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun clearLauncherRechecks() {
        launcherRechecks.forEach(handler::removeCallbacks)
        launcherRechecks.clear()
    }

    companion object {
        private const val TAG = "KeepAliveA11y"
        private val LAUNCHER_SETTLE_RECHECK_MS = longArrayOf(150L, 400L, 900L, 1500L)
        @Volatile var isConnected: Boolean = false
            private set
    }
}
