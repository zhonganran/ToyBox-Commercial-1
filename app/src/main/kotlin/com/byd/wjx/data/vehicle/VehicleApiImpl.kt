package com.byd.wjx.data.vehicle

import android.util.Log
import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.local.entity.VehicleWriteLogEntity
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleApiImpl @Inject constructor(
    private val parsReader: ParsReader,
    private val gateway: VehicleGateway,
    private val allowlist: WriteAllowlist,
    private val writeLogDao: VehicleWriteLogDao,
    private val seatChannelStore: SeatChannelStore = InMemorySeatChannelStore(),
) : VehicleApi {

    private val adaptiveSeatChannel = AdaptiveSeatChannel(
        writer = SeatWriter { actionName, value -> doWriteOutcome(actionName, value) },
        store = seatChannelStore,
    )

    // On-demand snapshots — passthroughs.
    override suspend fun readBatterySnapshot(): BatteryReading? = gateway.readBatterySnapshot()
    override suspend fun readSnapshot(): DiParsData? = parsReader.fetch()

    // ─── Writes ────────────────────────────────────────────────────────────────

    override suspend fun dispatch(commandString: String): Result<Unit> {
        val resolved = CommandTranslator.resolve(commandString)
        if (resolved.isEmpty()) {
            WjxFileLogger.w(TAG, "dispatch: unknown command '$commandString'")
            logWrite(commandString, -1, -1, 0, null, false, "no_translator_mapping", validated = false)
            return Result.failure(VehicleWriteError.AllowlistMiss(commandString, "no translator mapping"))
        }
        if (resolved.size == 1) {
            val r = resolved[0]
            resolveSeatTarget(r.actionName, r.value)?.let { target ->
                val ok = adaptiveSeatChannel.actuate(target.group, target.level)
                return if (ok) {
                    Result.success(Unit)
                } else {
                    Result.failure(VehicleWriteError.Unsupported(r.actionName))
                }
            }
            return doWrite(r.actionName, r.value)
        }
        // Composite command (e.g. window aggregates) → fan out to several
        // per-door writes. Attempt every sub-write (no short-circuit: a partial
        // open beats stopping at the first stuck pane); succeed only if all do.
        var firstError: Throwable? = null
        for (r in resolved) {
            val res = doWrite(r.actionName, r.value)
            if (res.isFailure && firstError == null) firstError = res.exceptionOrNull()
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    // Climate
    override suspend fun writeAcOn(): Result<Unit> = doWrite("ac_on", 0)
    override suspend fun writeAcOff(): Result<Unit> = doWrite("ac_off", 1)
    override suspend fun writeSetDriverTemp(celsius: Int): Result<Unit> =
        doWrite("ac_temp_main", celsius)

    // Windows
    override suspend fun writeWindowDriver(percent: Int): Result<Unit> =
        doWrite("window_driver_pos", percent)
    override suspend fun writeWindowPassenger(percent: Int): Result<Unit> =
        doWrite("window_passenger_pos", percent)
    override suspend fun writeWindowRearLeft(percent: Int): Result<Unit> =
        doWrite("window_rear_left_pos", percent)
    override suspend fun writeWindowRearRight(percent: Int): Result<Unit> =
        doWrite("window_rear_right_pos", percent)

    // Locks
    override suspend fun writeLockDoors(): Result<Unit> = doWrite("doors_lock", 2)
    override suspend fun writeUnlockDoors(): Result<Unit> = doWrite("doors_unlock", 1)

    // Sunroof — one allowlist entry per mode (sunroof_open, sunroof_close, etc.)
    override suspend fun writeSunroof(mode: SunroofMode): Result<Unit> =
        doWrite("sunroof_${mode.name.lowercase()}", mode.value)

    // Sunshade
    override suspend fun writeSunshade(open: Boolean): Result<Unit> =
        doWrite(if (open) "sunshade_open" else "sunshade_close", if (open) 1 else 2)

    // ─── Core write helper ─────────────────────────────────────────────────────

    /**
     * Fail-soft write via WriteAllowlist + HelperClient. Never throws — wraps
     * all failures in Result.failure(VehicleWriteError). Audit entry always
     * written via [logWrite]. Validated failures additionally logged via
     * [maybeReportValidatedFailure].
     *
     * Flow (matches plan C.6 step 3):
     * 1. Allowlist miss      → AllowlistMiss
     * 2. Out of range        → OutOfRange
     * 3. helper.write throws → HelperUnreachable (IOException or other)
     * 4. helper.write false  → HelperUnreachable (validated) / Unsupported (non-validated)
     * 5. Readback == -10011  → Sentinel
     * 6. Readback != value   → ReadbackMismatch
     * 7. Otherwise           → Result.success(Unit)
     *
     * Sentinel -10011 in readback = "no data / permission denied" (transient).
     * Readback null = entry has no readbackFid → trust the write result.
     *
     * Best-effort semantics: Result.success(Unit) means the helper daemon accepted
     * the setInt call with status>=0. For entries without readbackFid (windows %,
     * climate, sunroof/sunshade), there is no independent verification that the
     * physical actuator moved. Locks and select climate flags do have readback.
     */
    // internal for testing the Unsupported path (non-validated helper-false flow).
    internal suspend fun doWrite(actionName: String, value: Int): Result<Unit> {
        val entry = allowlist.find(actionName) ?: run {
            WjxFileLogger.w(TAG, "doWrite: action=$actionName not in allowlist")
            logWrite(actionName, -1, -1, value, null, false, "allowlist_miss", validated = false)
            val err = VehicleWriteError.AllowlistMiss(actionName)
            return Result.failure(err)
        }

        if (value < entry.valueMin || value > entry.valueMax) {
            WjxFileLogger.w(TAG, "doWrite: action=$actionName value=$value out of range [${entry.valueMin}..${entry.valueMax}]")
            logWrite(actionName, entry.dev, entry.writeFid, value, null, false, "range", entry.validated)
            val err = VehicleWriteError.OutOfRange(actionName, "value=$value range=[${entry.valueMin}..${entry.valueMax}]")
            return Result.failure(err)
        }

        val auditId = logAttempt(actionName, entry, value)

        val writeStatus: Int? = try {
            gateway.writeStatus(entry.dev, entry.writeFid, value)
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "doWrite: action=$actionName gateway.writeStatus threw: ${e.message}", e)
            logWrite(actionName, entry.dev, entry.writeFid, value, null, false, "helper_exception", entry.validated, auditId)
            val err = VehicleWriteError.HelperUnreachable(actionName, e.message ?: "io error")
            maybeReportValidatedFailure(actionName, err, entry)
            return Result.failure(err)
        }
        val wrote = writeStatus != null && writeStatus >= 0

        if (!wrote) {
            return if (entry.validated) {
                WjxFileLogger.w(TAG, "doWrite: action=$actionName helper.write returned false (validated)")
                logWrite(actionName, entry.dev, entry.writeFid, value, null, false, "helper_fail", entry.validated, auditId)
                val err = VehicleWriteError.HelperUnreachable(actionName, "helper.write returned false")
                maybeReportValidatedFailure(actionName, err, entry)
                Result.failure(err)
            } else {
                WjxFileLogger.w(TAG, "doWrite: action=$actionName helper.write returned false (non-validated)")
                logWrite(actionName, entry.dev, entry.writeFid, value, null, false, "helper_fail_nonvalidated", entry.validated, auditId)
                val err = VehicleWriteError.Unsupported(actionName)
                Result.failure(err)
            }
        }

        val readback: Long? = entry.readbackFid?.let { readbackFid ->
            gateway.readRaw(VehicleReadSpec(transaction = 5, device = entry.dev, fid = readbackFid))
                ?.value
                ?.toLong()
        }

        if (readback == -10011L) {
            // For non-validated entries, sentinel is expected (crowd-validation in progress) — surface as Unsupported.
            // DAO error string stays "readback_sentinel" for diagnostic analysis.
            val err = if (entry.validated) VehicleWriteError.Sentinel(actionName) else VehicleWriteError.Unsupported(actionName)
            WjxFileLogger.w(TAG, "doWrite: action=$actionName readback sentinel validated=${entry.validated}")
            logWrite(actionName, entry.dev, entry.writeFid, value, readback.toInt(), false, "readback_sentinel", entry.validated, auditId)
            maybeReportValidatedFailure(actionName, err, entry)
            return Result.failure(err)
        }

        if (readback != null && readback.toInt() != value) {
            // For non-validated entries, mismatch is expected (crowd-validation in progress) — surface as Unsupported.
            // DAO error string stays "readback_mismatch" for diagnostic analysis.
            val err = if (entry.validated) VehicleWriteError.ReadbackMismatch(actionName, "expected=$value got=$readback") else VehicleWriteError.Unsupported(actionName)
            WjxFileLogger.w(TAG, "doWrite: action=$actionName readback mismatch expected=$value got=$readback validated=${entry.validated}")
            logWrite(actionName, entry.dev, entry.writeFid, value, readback.toInt(), false, "readback_mismatch", entry.validated, auditId)
            maybeReportValidatedFailure(actionName, err, entry)
            return Result.failure(err)
        }

        // INFO so a successful dispatch is visible in logcat (the DAO row is private).
        // Pair with the HelperClient "status=" line to tell a real action from a no-op.
        WjxFileLogger.i(TAG, "doWrite OK: action=$actionName dev=${entry.dev} fid=${entry.writeFid} value=$value readback=$readback validated=${entry.validated}")
        logWrite(actionName, entry.dev, entry.writeFid, value, readback?.toInt(), true, null, entry.validated, auditId)
        return Result.success(Unit)
    }

    internal suspend fun doWriteOutcome(actionName: String, value: Int): WriteOutcome {
        val entry = allowlist.find(actionName) ?: run {
            WjxFileLogger.w(TAG, "doWriteOutcome: action=$actionName not in allowlist")
            logWrite(actionName, -1, -1, value, null, false, "allowlist_miss", validated = false)
            return WriteOutcome.NOOP
        }

        if (value < entry.valueMin || value > entry.valueMax) {
            WjxFileLogger.w(TAG, "doWriteOutcome: action=$actionName value=$value out of range [${entry.valueMin}..${entry.valueMax}]")
            logWrite(actionName, entry.dev, entry.writeFid, value, null, false, "range", entry.validated)
            return WriteOutcome.NOOP
        }

        val auditId = logAttempt(actionName, entry, value)

        val status = try {
            gateway.writeStatus(entry.dev, entry.writeFid, value)
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "doWriteOutcome: action=$actionName helper.writeStatus threw: ${e.message}", e)
            logWrite(actionName, entry.dev, entry.writeFid, value, null, false, "helper_exception", entry.validated, auditId)
            return WriteOutcome.TRANSIENT
        }
        val outcome = WriteOutcome.fromStatus(status)
        val ok = outcome == WriteOutcome.REAL
        logWrite(
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
        WjxFileLogger.i(TAG, "doWriteOutcome action=$actionName value=$value status=$status outcome=$outcome")
        return outcome
    }

    private data class SeatTarget(val group: SeatGroup, val level: Int)

    private fun resolveSeatTarget(actionName: String, value: Int): SeatTarget? {
        val group = when {
            actionName.startsWith("driver_seat_heat") -> SeatGroup.DRIVER_HEAT
            actionName.startsWith("driver_seat_vent") -> SeatGroup.DRIVER_VENT
            actionName.startsWith("passenger_seat_heat") -> SeatGroup.PASSENGER_HEAT
            actionName.startsWith("passenger_seat_vent") -> SeatGroup.PASSENGER_VENT
            else -> return null
        }
        val level = when {
            actionName.endsWith("_off") -> 0
            actionName.endsWith("_on") -> 1
            actionName.endsWith("_lvl2") -> 2
            actionName.endsWith("_lvl3") -> 3
            actionName.endsWith("_lvl4") -> 4
            actionName.endsWith("_lvl5") -> 5
            value == 1 -> 0
            value in 2..6 -> value - 1
            value in 0..5 -> value
            else -> return null
        }
        return SeatTarget(group, level.coerceIn(0, 5))
    }

    // ─── Observability hook ────────────────────────────────────────────────────

    /**
     * Logs a WARN for validated actions that unexpectedly fail with
     * HelperUnreachable or ReadbackMismatch. These are the highest-signal
     * failures: the action was live-confirmed on Leopard 3, so a failure means
     * the helper daemon is down or the vehicle state machine rejected the command.
     *
     * TODO: route to Crashlytics when Firebase is integrated
     *   (requires google-services.json + Firebase gradle plugin — out of scope for Phase 2).
     */
    private fun maybeReportValidatedFailure(actionName: String, err: VehicleWriteError, entry: WriteEntry) {
        if (entry.validated && (err is VehicleWriteError.HelperUnreachable || err is VehicleWriteError.ReadbackMismatch)) {
            WjxFileLogger.w(VALIDATED_FAILURE_TAG, "action=$actionName error=${err::class.simpleName}: ${err.message}")
        }
    }

    // ─── Audit logger ──────────────────────────────────────────────────────────

    /**
     * Inserts a "pending" audit row (status=-2) before calling helper.write.
     * Ensures that if the coroutine is cancelled mid-helper (timeout, shutdown),
     * the attempted-write record is not lost. Wrapped in runCatching so a DAO
     * failure does not abort the write itself.
     *
     * Callers: doWrite, after range gate passes, BEFORE helper.write.
     * Skip on AllowlistMiss / OutOfRange — no helper call → no cancellation risk.
     */
    private suspend fun logAttempt(action: String, entry: WriteEntry, value: Int): Long? =
        runCatching {
            writeLogDao.insert(
                VehicleWriteLogEntity(
                    ts = System.currentTimeMillis(),
                    actionName = action,
                    dev = entry.dev,
                    fid = entry.writeFid,
                    requested = value,
                    readback = null,
                    status = -2, // sentinel: attempted, outcome unknown
                    error = "attempt",
                    validated = entry.validated,
                )
            )
        }.onFailure {
            Log.w(TAG, "logAttempt DAO insert failed: ${it.message}")
            WjxFileLogger.w(TAG, "logAttempt DAO insert failed: ${it.message}")
        }.getOrNull()?.takeIf { it > 0L }

    /**
     * Best-effort audit logger. Wrapped in runCatching so a DAO failure (full
     * disk, DB locked, etc.) does not surface as VehicleWriteError to the
     * caller. The "never throws" contract of doWrite depends on this.
     */
    private suspend fun logWrite(
        action: String, dev: Int, fid: Int, requested: Int, readback: Int?,
        ok: Boolean, error: String?, validated: Boolean, auditId: Long? = null,
    ) {
        runCatching {
            val status = if (ok) 0 else -1
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
                    )
                )
            }
        }.onFailure {
            Log.w(TAG, "logWrite DAO insert failed: ${it.message}")
            WjxFileLogger.w(TAG, "logWrite DAO insert failed: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "VehicleApiImpl"
        private const val VALIDATED_FAILURE_TAG = "VehicleApi.ValidatedFailure"
        // TODO: route to Crashlytics when Firebase is integrated
    }
}
