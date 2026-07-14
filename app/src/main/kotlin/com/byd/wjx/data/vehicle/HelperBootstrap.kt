package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.helper.HelperBinderProtocol
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the helper daemon's lifecycle. Idempotent — ensureRunning()
 * converges to "one alive helper": if the binder ping already succeeds it returns
 * immediately, otherwise it spawns via app_process and polls the ping. Duplicate
 * spawns are harmless — the daemon takes an exclusive file lock and a second
 * instance exits cleanly.
 */
@Singleton
class HelperBootstrap @Inject constructor(
    private val adb: AdbOnDeviceClient,
    private val helper: HelperClient,
) {
    private val mutex = Mutex()
    @Volatile private var currentProtocolReady = false

    /**
     * Returns true if the helper binder service is reachable after this call.
     *
     * Order of operations:
     *   1. Ping the daemon via binder. If alive, return true immediately.
     *   2. Dispatch spawn via app_process (CLASSPATH = app's own base.apk).
     *   3. Poll the binder ping up to [POLL_ATTEMPTS] times at [POLL_INTERVAL_MS] ms.
     *   4. On exhausted poll, read the daemon log for diagnostics and return false.
     */
    suspend fun ensureRunning(): Boolean = mutex.withLock {
        if (helper.isAlive()) return true
        WjxFileLogger.i(TAG, "helper missing, spawn requested")
        spawnAndWaitLocked("spawn")
    }

    /**
     * Startup gate for the new direct-Binder read path. An old helper can still answer TX_PING
     * after an APK update while not understanding TX_READ_BATCH; restart it before the first
     * full snapshot so Binder polling does not remain unavailable for the whole session.
     */
    suspend fun ensureCurrent(): Boolean = mutex.withLock {
        val alive = helper.isAlive() || spawnAndWaitLocked("spawn")
        if (!alive) return false
        if (currentProtocolReady) return true
        if (vehicleProtocolAvailable()) return true

        WjxFileLogger.w(TAG, "helper protocol is stale or incomplete; restarting after update")
        if (!spawnAndWaitLocked("protocol_upgrade")) return false
        val ready = vehicleProtocolAvailable()
        currentProtocolReady = ready
        if (!ready) {
            WjxFileLogger.w(TAG, "helper restarted but required protocol is still unavailable")
        }
        ready
    }

    suspend fun ensureVehicleReadsReady(): Boolean = ensureCurrent()

    /**
     * Force-restarts the helper daemon even when ping succeeds. This is needed after an APK update:
     * the old app_process instance can keep serving the old Binder protocol until it is killed,
     * so new transactions may be rejected even though TX_PING still works.
     */
    suspend fun restart(reason: String): Boolean = mutex.withLock {
        WjxFileLogger.w(TAG, "helper restart requested: $reason")
        currentProtocolReady = false
        spawnAndWaitLocked("restart:$reason")
    }

    /** Cheap reachability check — no side effects. */
    suspend fun isHealthy(): Boolean = helper.isAlive()

    private suspend fun spawnAndWaitLocked(reason: String): Boolean {
        currentProtocolReady = false
        val dispatched = HelperProcessGate.withRestart { adb.spawnHelper() }
        if (!dispatched) {
            WjxFileLogger.w(TAG, "spawnHelper dispatch failed reason=$reason")
        }
        repeat(POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            if (helper.isAlive()) {
                WjxFileLogger.i(TAG, "helper alive after $reason poll=${it + 1}")
                return true
            }
        }
        val log = adb.readHelperLog()
        WjxFileLogger.w(TAG, "helper unreachable after $reason; daemon log:\n${log.orEmpty().takeLast(2000)}")
        return false
    }

    private suspend fun batchProtocolAvailable(): Boolean {
        val values = helper.readBatch(VehicleGateway.AVAILABILITY_PROBES) ?: return false
        return values.size == VehicleGateway.AVAILABILITY_PROBES.size
    }

    private suspend fun vehicleProtocolAvailable(): Boolean {
        val version = helper.protocolVersion() ?: return false
        if (version < HelperBinderProtocol.CURRENT_VERSION) {
            WjxFileLogger.i(
                TAG,
                "helper protocol version=$version required=${HelperBinderProtocol.CURRENT_VERSION}",
            )
            return false
        }
        return batchProtocolAvailable().also { currentProtocolReady = it }
    }

    companion object {
        private const val TAG = "HelperBootstrap"
        private const val POLL_ATTEMPTS = 40
        private const val POLL_INTERVAL_MS = 200L
    }
}
