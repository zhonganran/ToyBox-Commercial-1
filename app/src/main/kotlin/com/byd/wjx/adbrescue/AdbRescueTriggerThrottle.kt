package com.byd.wjx.adbrescue

import android.os.SystemClock

internal object AdbRescueTriggerThrottle {
    private const val VENDOR_ACTION_INTERVAL_MS = 30_000L
    private val lock = Any()
    private val lastVendorRunByAction = mutableMapOf<String, Long>()

    fun shouldRun(action: String, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!action.startsWith("com.byd.")) return true
        return synchronized(lock) {
            val previous = lastVendorRunByAction[action]
            if (previous != null && nowMs - previous < VENDOR_ACTION_INTERVAL_MS) {
                return@synchronized false
            }
            lastVendorRunByAction[action] = nowMs
            true
        }
    }

    internal fun resetForTest() = synchronized(lock) {
        lastVendorRunByAction.clear()
    }
}
