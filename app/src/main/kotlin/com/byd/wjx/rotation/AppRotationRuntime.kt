package com.byd.wjx.rotation

import android.content.Context
import com.byd.wjx.data.foreground.ForegroundAppMonitor
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Singleton
class AppRotationRuntime @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: AppRotationPreferences,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val ownership = AppRotationOwnershipStore(context)
    private val lifecycleLock = Any()
    private var job: Job? = null
    private var landscapeApplied = ownership.isOwned()

    fun start() = synchronized(lifecycleLock) {
        if (job?.isActive == true) return
        foregroundAppMonitor.start(OWNER)
        job = applicationScope.launch {
            combine(preferences.config, foregroundAppMonitor.foreground) { config, foreground ->
                config.shouldForceLandscape(foreground.packageName)
            }
                .distinctUntilChanged()
                .collectLatest { shouldForce ->
                    if (shouldForce) {
                        applyLandscape()
                    } else {
                        delay(EXIT_GRACE_MS)
                        releaseLandscape("target_left")
                    }
                }
        }
        WjxFileLogger.i(TAG, "started owned=$landscapeApplied")
    }

    private suspend fun applyLandscape() {
        if (landscapeApplied) return
        val applied = executeWithRetry("force") { helperClient.forceSystemLandscape() }
        if (!applied) return
        landscapeApplied = true
        ownership.setOwned(true)
        WjxFileLogger.i(TAG, "target app entered; system landscape applied")
    }

    private suspend fun releaseLandscape(reason: String) {
        if (!landscapeApplied) return
        val restored = executeWithRetry("restore:$reason") { helperClient.restoreSystemRotation() }
        if (!restored) return
        landscapeApplied = false
        ownership.setOwned(false)
        WjxFileLogger.i(TAG, "system rotation restored reason=$reason")
    }

    private suspend fun executeWithRetry(
        operation: String,
        block: suspend () -> Boolean,
    ): Boolean {
        repeat(OPERATION_ATTEMPTS) { attempt ->
            try {
                if (helperBootstrap.ensureCurrent() && block()) return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                WjxFileLogger.w(TAG, "$operation attempt=${attempt + 1} failed: ${error.message}")
            }
            if (attempt + 1 < OPERATION_ATTEMPTS) delay(OPERATION_RETRY_MS)
        }
        WjxFileLogger.w(TAG, "$operation failed after $OPERATION_ATTEMPTS attempts")
        return false
    }

    private companion object {
        const val TAG = "AppRotationRuntime"
        const val OWNER = "app_rotation"
        const val EXIT_GRACE_MS = 500L
        const val OPERATION_ATTEMPTS = 3
        const val OPERATION_RETRY_MS = 600L
    }
}

private class AppRotationOwnershipStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOwned(): Boolean = preferences.getBoolean(KEY_OWNED, false)

    fun setOwned(owned: Boolean) {
        preferences.edit().putBoolean(KEY_OWNED, owned).apply()
    }

    private companion object {
        const val PREFS_NAME = "wjx_app_rotation_runtime"
        const val KEY_OWNED = "landscape_owned"
    }
}
