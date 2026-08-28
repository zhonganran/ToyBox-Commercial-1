package com.byd.wjx.data.vehicle

import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.delay

/** Owns allowlist validation, helper writes, bounded readback, and failure classification. */
internal class VehicleWriteExecutor(
    private val gateway: VehicleGateway,
    private val allowlist: WriteAllowlist,
    private val auditLogger: VehicleWriteAuditLogger,
) {
    suspend fun rejectUnknownCommand(commandString: String): Result<Unit> {
        auditLogger.logOutcome(
            commandString, -1, -1, 0, null, false, "no_translator_mapping", false,
        )
        return Result.failure(
            VehicleWriteError.AllowlistMiss(commandString, "no translator mapping"),
        )
    }

    suspend fun write(actionName: String, value: Int): Result<Unit> {
        val entry = allowlist.find(actionName) ?: run {
            WjxFileLogger.w(TAG, "doWrite: action=$actionName not in allowlist")
            auditLogger.logOutcome(
                actionName, -1, -1, value, null, false, "allowlist_miss", false,
            )
            return Result.failure(VehicleWriteError.AllowlistMiss(actionName))
        }

        if (value !in entry.valueMin..entry.valueMax) {
            WjxFileLogger.w(
                TAG,
                "doWrite: action=$actionName value=$value out of range " +
                    "[${entry.valueMin}..${entry.valueMax}]",
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, null, false, "range", entry.validated,
            )
            return Result.failure(
                VehicleWriteError.OutOfRange(
                    actionName,
                    "value=$value range=[${entry.valueMin}..${entry.valueMax}]",
                ),
            )
        }

        val auditId = auditLogger.logAttempt(actionName, entry, value)
        val writeStatus = try {
            gateway.writeStatus(entry.dev, entry.writeFid, value)
        } catch (error: Exception) {
            WjxFileLogger.w(
                TAG,
                "doWrite: action=$actionName gateway.writeStatus threw: ${error.message}",
                error,
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, null, false,
                "helper_exception", entry.validated, auditId,
            )
            val failure = VehicleWriteError.HelperUnreachable(
                actionName,
                error.message ?: "io error",
            )
            reportValidatedFailure(actionName, failure, entry)
            return Result.failure(failure)
        }

        if (writeStatus == null || writeStatus < 0) {
            val errorCode = if (entry.validated) "helper_fail" else "helper_fail_nonvalidated"
            WjxFileLogger.w(
                TAG,
                "doWrite: action=$actionName helper.write returned false " +
                    "(validated=${entry.validated})",
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, null, false,
                errorCode, entry.validated, auditId,
            )
            val failure = if (entry.validated) {
                VehicleWriteError.HelperUnreachable(actionName, "helper.write returned false")
            } else {
                VehicleWriteError.Unsupported(actionName)
            }
            reportValidatedFailure(actionName, failure, entry)
            return Result.failure(failure)
        }

        val readback = readBackAfterWrite(entry, value)
        if (readback == READBACK_SENTINEL) {
            if (entry.readbackPolicy == ReadbackPolicy.BEST_EFFORT) {
                return acceptWithoutReadback(
                    actionName = actionName,
                    entry = entry,
                    value = value,
                    readback = readback,
                    diagnostic = "readback_sentinel",
                    auditId = auditId,
                )
            }
            val failure = if (entry.validated) {
                VehicleWriteError.Sentinel(actionName)
            } else {
                VehicleWriteError.Unsupported(actionName)
            }
            WjxFileLogger.w(
                TAG,
                "doWrite: action=$actionName readback sentinel validated=${entry.validated}",
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, readback.toInt(), false,
                "readback_sentinel", entry.validated, auditId,
            )
            reportValidatedFailure(actionName, failure, entry)
            return Result.failure(failure)
        }

        if (readback != null && readback.toInt() != value) {
            if (entry.readbackPolicy == ReadbackPolicy.BEST_EFFORT) {
                return acceptWithoutReadback(
                    actionName = actionName,
                    entry = entry,
                    value = value,
                    readback = readback,
                    diagnostic = "readback_mismatch",
                    auditId = auditId,
                )
            }
            val failure = if (entry.validated) {
                VehicleWriteError.ReadbackMismatch(
                    actionName,
                    "expected=$value got=$readback",
                )
            } else {
                VehicleWriteError.Unsupported(actionName)
            }
            WjxFileLogger.w(
                TAG,
                "doWrite: action=$actionName readback mismatch expected=$value " +
                    "got=$readback validated=${entry.validated}",
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, readback.toInt(), false,
                "readback_mismatch", entry.validated, auditId,
            )
            reportValidatedFailure(actionName, failure, entry)
            return Result.failure(failure)
        }

        WjxFileLogger.i(
            TAG,
            "doWrite OK: action=$actionName dev=${entry.dev} fid=${entry.writeFid} " +
                "value=$value readback=$readback validated=${entry.validated}",
        )
        auditLogger.logOutcome(
            actionName, entry.dev, entry.writeFid, value, readback?.toInt(), true,
            null, entry.validated, auditId,
        )
        return Result.success(Unit)
    }

    private suspend fun acceptWithoutReadback(
        actionName: String,
        entry: WriteEntry,
        value: Int,
        readback: Long?,
        diagnostic: String,
        auditId: Long?,
    ): Result<Unit> {
        WjxFileLogger.i(
            TAG,
            "doWrite accepted: action=$actionName dev=${entry.dev} fid=${entry.writeFid} " +
                "value=$value readback=$readback diagnostic=$diagnostic policy=best_effort",
        )
        auditLogger.logOutcome(
            actionName,
            entry.dev,
            entry.writeFid,
            value,
            readback?.toInt(),
            true,
            null,
            entry.validated,
            auditId,
        )
        return Result.success(Unit)
    }

    suspend fun writeOutcome(actionName: String, value: Int): WriteOutcome {
        val entry = allowlist.find(actionName) ?: run {
            WjxFileLogger.w(TAG, "doWriteOutcome: action=$actionName not in allowlist")
            auditLogger.logOutcome(
                actionName, -1, -1, value, null, false, "allowlist_miss", false,
            )
            return WriteOutcome.NOOP
        }
        if (value !in entry.valueMin..entry.valueMax) {
            WjxFileLogger.w(
                TAG,
                "doWriteOutcome: action=$actionName value=$value out of range " +
                    "[${entry.valueMin}..${entry.valueMax}]",
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, null, false,
                "range", entry.validated,
            )
            return WriteOutcome.NOOP
        }

        val auditId = auditLogger.logAttempt(actionName, entry, value)
        val status = try {
            gateway.writeStatus(entry.dev, entry.writeFid, value)
        } catch (error: Exception) {
            WjxFileLogger.w(
                TAG,
                "doWriteOutcome: action=$actionName helper.writeStatus threw: ${error.message}",
                error,
            )
            auditLogger.logOutcome(
                actionName, entry.dev, entry.writeFid, value, null, false,
                "helper_exception", entry.validated, auditId,
            )
            return WriteOutcome.TRANSIENT
        }
        val outcome = WriteOutcome.fromStatus(status)
        val ok = outcome == WriteOutcome.REAL
        auditLogger.logOutcome(
            action = actionName,
            dev = entry.dev,
            fid = entry.writeFid,
            requested = value,
            readback = null,
            ok = ok,
            error = if (ok) null else "outcome_${outcome.name.lowercase()}",
            validated = entry.validated,
            auditId = auditId,
        )
        WjxFileLogger.i(
            TAG,
            "doWriteOutcome action=$actionName value=$value status=$status outcome=$outcome",
        )
        return outcome
    }

    private suspend fun readBackAfterWrite(entry: WriteEntry, expectedValue: Int): Long? {
        val readbackFid = entry.readbackFid ?: return null
        val spec = VehicleReadSpec(transaction = 5, device = entry.dev, fid = readbackFid)
        var latest: Long? = null
        for (delayMs in READBACK_RETRY_DELAYS_MS) {
            if (delayMs > 0L) delay(delayMs)
            latest = gateway.readRaw(spec)?.value?.toLong()
            if (latest != READBACK_SENTINEL && latest?.toInt() == expectedValue) return latest
        }
        return latest
    }

    private fun reportValidatedFailure(
        actionName: String,
        error: VehicleWriteError,
        entry: WriteEntry,
    ) {
        val reportable = error is VehicleWriteError.HelperUnreachable ||
            error is VehicleWriteError.ReadbackMismatch
        if (entry.validated && reportable) {
            WjxFileLogger.w(
                VALIDATED_FAILURE_TAG,
                "action=$actionName error=${error::class.simpleName}: ${error.message}",
            )
        }
    }

    private companion object {
        const val TAG = "VehicleApiImpl"
        const val VALIDATED_FAILURE_TAG = "VehicleApi.ValidatedFailure"
        const val READBACK_SENTINEL = -10011L
        val READBACK_RETRY_DELAYS_MS = longArrayOf(80L, 160L, 320L)
    }
}
