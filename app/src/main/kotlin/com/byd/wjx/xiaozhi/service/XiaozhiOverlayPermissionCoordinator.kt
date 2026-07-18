package com.byd.wjx.xiaozhi.service

import android.content.Context
import android.provider.Settings
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Grants the one overlay permission shared by Xiaozhi standby and conversation indicators. */
@Singleton
class XiaozhiOverlayPermissionCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    private val appContext = context.applicationContext
    private val grantMutex = Mutex()

    suspend fun ensureGranted(): Boolean = grantMutex.withLock {
        if (Settings.canDrawOverlays(appContext)) return@withLock true
        val requested = runCatching {
            helperBootstrap.ensureCurrent() && helperClient.grantOverlayPermission()
        }.onFailure { error ->
            WjxFileLogger.w(TAG, "overlay grant failed: ${error.message}", error)
        }.getOrDefault(false)
        if (requested) {
            repeat(MAX_PERMISSION_CHECKS) {
                if (Settings.canDrawOverlays(appContext)) return@withLock true
                delay(PERMISSION_CHECK_INTERVAL_MS)
            }
        }
        val applied = Settings.canDrawOverlays(appContext)
        WjxFileLogger.i(TAG, "overlay grant requested=$requested applied=$applied")
        applied
    }

    private companion object {
        const val TAG = "XiaozhiOverlayPermission"
        const val MAX_PERMISSION_CHECKS = 10
        const val PERMISSION_CHECK_INTERVAL_MS = 200L
    }
}
