package com.byd.wjx.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.byd.wjx.log.WjxFileLogger

/**
 * WorkManager fallback that starts the single background-stack entrypoint.
 *
 * BootReceiver uses this only if the direct KeepAliveService request throws.
 */
class ServiceStartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ServiceStartWorker"
        const val WORK_NAME = "ServiceStart"
    }

    override suspend fun doWork(): Result {
        WjxFileLogger.i(TAG, "Starting KeepAliveService via WorkManager fallback")
        ChainLog.append(applicationContext, "Worker doWork started")
        return try {
            KeepAliveService.start(applicationContext, "worker_fallback")
            ChainLog.append(applicationContext, "KeepAliveService fallback requested")
            WjxFileLogger.i(TAG, "KeepAliveService fallback requested")
            Result.success()
        } catch (e: Exception) {
            ChainLog.append(applicationContext, "startForegroundService FAILED: ${e.message}")
            WjxFileLogger.e(TAG, "Failed to start TrackingService: ${e.message}", e)
            Result.retry()
        }
    }
}
