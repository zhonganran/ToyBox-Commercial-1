package com.byd.wjx.helper

import android.content.Context

/** Shell-helper bridge that removes only native status-bar visuals. Shade gestures stay enabled. */
internal class BydNativeStatusBarController(
    private val systemContext: Context?,
) {
    private val lock = Any()
    private var statusBarManager: Any? = null

    fun setReplaced(enabled: Boolean): Boolean = synchronized(lock) {
        val manager = resolveManager() ?: return@synchronized false
        val flags = if (enabled) NativeStatusBarPolicy.REPLACEMENT_DISABLE_FLAGS else 0
        return@synchronized runCatching {
            val method = manager.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "disable" &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType
            } ?: error("StatusBarManager.disable(int) unavailable")
            method.invoke(manager, flags)
            helperLog(
                TAG,
                "native visuals ${if (enabled) "hidden" else "restored"} flags=0x${flags.toString(16)} shade=enabled",
            )
            true
        }.onFailure {
            helperLog(TAG, "setReplaced($enabled) failed: ${throwableSummary(it)}")
            statusBarManager = null
        }.getOrDefault(false)
    }

    private fun resolveManager(): Any? {
        statusBarManager?.let { return it }
        val context = systemContext ?: run {
            helperLog(TAG, "system context unavailable")
            return null
        }
        return runCatching { context.getSystemService(STATUS_BAR_SERVICE) }
            .onFailure { helperLog(TAG, "manager init failed: ${throwableSummary(it)}") }
            .getOrNull()
            ?.also { statusBarManager = it }
    }

    private companion object {
        const val TAG = "NativeStatusBar"
        const val STATUS_BAR_SERVICE = "statusbar"
    }
}

internal object NativeStatusBarPolicy {
    const val DISABLE_EXPAND = 0x00010000
    const val DISABLE_NOTIFICATION_ICONS = 0x00020000
    const val DISABLE_SYSTEM_INFO = 0x00100000
    const val DISABLE_CLOCK = 0x00800000

    // No DISABLE_EXPAND and no disable2 flags: notification shade and quick settings remain usable.
    const val REPLACEMENT_DISABLE_FLAGS =
        DISABLE_NOTIFICATION_ICONS or DISABLE_SYSTEM_INFO or DISABLE_CLOCK
}
