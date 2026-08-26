package com.byd.wjx.data.vehicle

import android.util.Log
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.local.entity.VehicleWriteLogEntity
import com.byd.wjx.log.WjxFileLogger

/** Persists vehicle-write attempts and outcomes without allowing audit failures to block commands. */
internal class VehicleWriteAuditLogger(
    private val writeLogDao: VehicleWriteLogDao,
) {
    suspend fun logAttempt(action: String, entry: WriteEntry, value: Int): Long? =
        runCatching {
            writeLogDao.insert(
                VehicleWriteLogEntity(
                    ts = System.currentTimeMillis(),
                    actionName = action,
                    dev = entry.dev,
                    fid = entry.writeFid,
                    requested = value,
                    readback = null,
                    status = ATTEMPT_STATUS,
                    error = "attempt",
                    validated = entry.validated,
                ),
            )
        }.onFailure {
            reportFailure("logAttempt DAO insert failed: ${it.message}")
        }.getOrNull()?.takeIf { it > 0L }

    suspend fun logOutcome(
        action: String,
        dev: Int,
        fid: Int,
        requested: Int,
        readback: Int?,
        ok: Boolean,
        error: String?,
        validated: Boolean,
        auditId: Long? = null,
    ) {
        runCatching {
            val status = if (ok) SUCCESS_STATUS else FAILURE_STATUS
            if (auditId != null) {
                writeLogDao.complete(auditId, readback, status, error)
            } else {
                writeLogDao.insert(
                    VehicleWriteLogEntity(
                        ts = System.currentTimeMillis(),
                        actionName = action,
                        dev = dev,
                        fid = fid,
                        requested = requested,
                        readback = readback,
                        status = status,
                        error = error,
                        validated = validated,
                    ),
                )
            }
        }.onFailure {
            reportFailure("logWrite DAO insert failed: ${it.message}")
        }
    }

    private fun reportFailure(message: String) {
        Log.w(TAG, message)
        WjxFileLogger.w(TAG, message)
    }

    private companion object {
        const val TAG = "VehicleApiImpl"
        const val ATTEMPT_STATUS = -2
        const val FAILURE_STATUS = -1
        const val SUCCESS_STATUS = 0
    }
}
