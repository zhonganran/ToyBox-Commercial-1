package com.byd.wjx.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.byd.wjx.data.foreground.AccessibilityForegroundBridge
import com.byd.wjx.log.WjxFileLogger

/**
 * User-visible accessibility binding used only to keep the runtime alive.
 */
class KeepAliveAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        AccessibilityForegroundBridge.onServiceConnected(this)
        WjxFileLogger.i(TAG, "connected")
        ChainLog.append(this, "KeepAliveAccessibility connected")
        KeepAliveService.start(applicationContext, "a11y_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AccessibilityForegroundBridge.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        // Intentionally no-op.
    }

    override fun onDestroy() {
        isConnected = false
        AccessibilityForegroundBridge.onServiceDisconnected(this)
        WjxFileLogger.i(TAG, "destroyed")
        ChainLog.append(this, "KeepAliveAccessibility destroyed")
        KeepAliveService.delayedKick(applicationContext, "a11y_destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KeepAliveA11y"
        @Volatile var isConnected: Boolean = false
            private set
    }
}
