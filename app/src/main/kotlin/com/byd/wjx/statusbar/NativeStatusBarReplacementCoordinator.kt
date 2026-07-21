package com.byd.wjx.statusbar

import android.content.Context
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object NativeStatusBarReplacementPolicy {
    fun shouldReplace(configEnabled: Boolean, replacementReady: Boolean): Boolean =
        configEnabled && replacementReady
}

@Singleton
class NativeStatusBarReplacementCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var initialized = false
    private var lastApplied: Boolean? = null

    suspend fun reconcile(
        replace: Boolean,
        reason: String,
        force: Boolean = false,
    ): Boolean = mutex.withLock {
        val owned = prefs.getBoolean(KEY_OWNED, false)
        val mustCall = force || !initialized || lastApplied != replace || owned != replace
        initialized = true
        if (!mustCall) return@withLock true

        val helperReady = runCatching { helperBootstrap.ensureCurrent() }
            .onFailure { WjxFileLogger.w(TAG, "helper unavailable reason=$reason", it) }
            .getOrDefault(false)
        if (!helperReady) return@withLock false

        val applied = runCatching { helperClient.setNativeStatusBarReplaced(replace) }
            .onFailure { WjxFileLogger.w(TAG, "control failed replace=$replace reason=$reason", it) }
            .getOrDefault(false)
        if (applied) {
            lastApplied = replace
            prefs.edit().putBoolean(KEY_OWNED, replace).apply()
            WjxFileLogger.i(TAG, "replace=$replace reason=$reason shade=enabled")
        }
        applied
    }

    fun restoreAsync(reason: String) {
        controlScope.launch {
            mutex.withLock {
                val shouldRestore = initialized || prefs.getBoolean(KEY_OWNED, false)
                initialized = true
                if (!shouldRestore) return@withLock
                val helperReady = runCatching { helperBootstrap.ensureCurrent() }.getOrDefault(false)
                val restored = helperReady && runCatching {
                    helperClient.setNativeStatusBarReplaced(false)
                }.getOrDefault(false)
                if (restored) {
                    lastApplied = false
                    prefs.edit().putBoolean(KEY_OWNED, false).apply()
                    WjxFileLogger.i(TAG, "replace=false reason=$reason shade=enabled")
                } else {
                    WjxFileLogger.w(TAG, "restore deferred reason=$reason")
                }
            }
        }
    }

    private companion object {
        const val TAG = "NativeStatusBar"
        const val PREFS_NAME = "wjx_custom_statusbar_runtime"
        const val KEY_OWNED = "native_status_bar_owned"
    }
}
