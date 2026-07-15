package com.byd.wjx.service

import android.content.Context
import com.byd.wjx.log.WjxFileLogger

/**
 * Persistent chain log for autostart diagnostics — survives logcat rollover
 * (DiLink rotates logcat aggressively). Used by BootReceiver / ServiceStartWorker /
 * TrackingService to trace boot → WorkManager → service-start chain.
 */
object ChainLog {
    private const val TAG = "ChainLog"

    fun append(context: Context, entry: String) {
        if (!BootDiagnosticsStore.appendChainEntry(context, entry)) {
            WjxFileLogger.w(TAG, "Failed to append chain log")
        }
        WjxFileLogger.i(TAG, entry)
    }
}
