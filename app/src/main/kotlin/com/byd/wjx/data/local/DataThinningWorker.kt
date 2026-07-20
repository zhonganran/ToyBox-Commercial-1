package com.byd.wjx.data.local

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.log.WjxFileLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily worker that thins old data points to save disk space.
 *
 * Thinning tiers:
 * - < 7 days: full resolution (every 3 sec)
 * - 7-30 days: ~1 point per 15 sec
 * - > 30 days: ~1 point per 60 sec
 */
@HiltWorker
class DataThinningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tripPointDao: TripPointDao,
    private val ruleLogDao: RuleLogDao,
    private val vehicleWriteLogDao: VehicleWriteLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DataThinning"
        const val WORK_NAME = "data_thinning"
        private const val DAYS_7_MS = 7L * 24 * 60 * 60 * 1000
        private const val DAYS_30_MS = 30L * 24 * 60 * 60 * 1000
        private const val INTERVAL_15S = 15_000L
        private const val INTERVAL_60S = 60_000L
        private const val WRITE_LOG_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val WRITE_LOG_MAX_ROWS = 2_000
        private const val RULE_LOG_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val RULE_LOG_MAX_ROWS = 2_000
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val tripsBefore = tripPointDao.getCount()

            // Tier 1: 7-30 days old → keep 1 per 15 sec
            val cutoff30 = now - DAYS_30_MS
            val cutoff7 = now - DAYS_7_MS
            tripPointDao.thinOldPoints(cutoff7, INTERVAL_15S)

            // Tier 2: > 30 days old → keep 1 per 60 sec
            tripPointDao.thinOldPoints(cutoff30, INTERVAL_60S)

            vehicleWriteLogDao.deleteOlderThan(now - WRITE_LOG_RETENTION_MS)
            vehicleWriteLogDao.trimToLatest(WRITE_LOG_MAX_ROWS)
            ruleLogDao.deleteOlderThan(now - RULE_LOG_RETENTION_MS)
            ruleLogDao.trimToLatest(RULE_LOG_MAX_ROWS)

            val tripsAfter = tripPointDao.getCount()
            val tripsDeleted = tripsBefore - tripsAfter

            if (tripsDeleted > 0) {
                Log.i(TAG, "Thinned: trip_points $tripsBefore→$tripsAfter (-$tripsDeleted)")
                WjxFileLogger.i(TAG, "Thinned: trip_points $tripsBefore->$tripsAfter (-$tripsDeleted)")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Data thinning failed: ${e.message}", e)
            WjxFileLogger.e(TAG, "Data thinning failed: ${e.message}", e)
            Result.retry()
        }
    }
}
