package com.byd.wjx.data.vehicle

import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.view.Surface
import com.byd.wjx.helper.HelperBinderProtocol
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class HelperShellResult(
    val status: Int,
    val exitCode: Int,
    val output: String,
) {
    val ok: Boolean get() = status == 0 && exitCode == 0
}

/**
 * Client for the in-vehicle helper daemon registered as the `wjx_helper`
 * binder service (ServiceManager.getService + IBinder.transact).
 *
 * read()/write()/isAlive() return null/false on any failure: daemon not
 * registered, dead binder, transact rejected by the daemon's uid gate, or
 * an autoservice error. Callers must treat null/false as "channel
 * unavailable, retry later".
 *
 * The hidden-API exemption for android.os.ServiceManager is installed in
 * SuperToyBoxApp.onCreate - do NOT add HiddenApiBypass here.
 */
interface HelperClient {
    suspend fun read(dev: Int, fid: Int, tx: Int = 5): Long?
    /** Ordered direct-Binder batch read. Null means the helper/protocol is unavailable. */
    suspend fun readBatch(specs: List<VehicleReadSpec>): List<VehicleRawRead>? = null
    suspend fun write(dev: Int, fid: Int, value: Int): Boolean
    /**
     * Raw autoservice status for a write:
     * 1 = physical action, 0 = accepted no-op, <0/null = error or helper unreachable.
     */
    suspend fun writeStatus(dev: Int, fid: Int, value: Int): Int?
    suspend fun isAlive(): Boolean
    /** Zero identifies a pre-version-handshake helper; null means unreachable. */
    suspend fun protocolVersion(): Int? = null

    /** Creates a VirtualDisplay backed by [surface]; returns its displayId (>0) or null. */
    suspend fun createVirtualDisplay(
        name: String, width: Int, height: Int, density: Int, flags: Int, surface: Surface,
    ): Int?
    suspend fun releaseVirtualDisplay(displayId: Int): Boolean
    suspend fun launchApp(packageName: String): Boolean
    /** Task id of [packageName]'s running task, or null if not running / channel unavailable. */
    suspend fun getTaskId(packageName: String): Int?
    suspend fun moveTaskToDisplay(taskId: Int, displayId: Int): Boolean
    suspend fun setTaskBounds(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): Boolean
    suspend fun setFocusedTask(taskId: Int): Boolean
    suspend fun setTaskWindowingMode(taskId: Int, windowingMode: Int): Boolean
    /**
     * Narrow appops grant to our own package: SYSTEM_ALERT_WINDOW (draw the cluster overlay)
     * and PROJECT_MEDIA (third-party access to the fission screen-projection display, else
     * getDisplay(clusterId) returns null). Returns true only if both grants succeed.
     */
    suspend fun grantOverlayPermission(): Boolean
    /** Launches [packageName] on [displayId] and pins it (move+bounds+focus loop). Long-running. */
    suspend fun launchAndForce(packageName: String, displayId: Int, width: Int, height: Int): Boolean
    /**
     * Enables our steering-wheel accessibility service via the daemon (force re-bind of our entry in
     * Settings.Secure enabled_accessibility_services, never clobbering other apps' services). DiLink
     * has no a11y settings UI, so this is how the star-control toggle self-enables key filtering.
     */
    suspend fun enableAccessibilityService(): Boolean
    /** Reads BYD bodywork VIN methods from the shell helper process. Raw text is returned for diagnostics. */
    suspend fun readVehicleVinRaw(): String? = null
    /** Syncs lock/unlock/sentry exterior-sound config into the shell helper. */
    suspend fun syncLockSoundConfig(json: String): Boolean = false
    /** Plays one vehicle-event sound in the shell helper. 0=unlock, 1=lock, 2=sentry. */
    suspend fun debugPlayLockSound(kind: Int): Boolean = false
    /** Stops one active helper-side vehicle-event sound. 0=unlock, 1=lock, 2=sentry. */
    suspend fun stopLockSound(kind: Int): Boolean = false
    /** Restricted sentry settings helper; writes only accept the daemon allowlist. */
    suspend fun sentryShell(op: String, arg: String): String? = null
    /** Generic helper-side shell execution with AutoTark-compatible status/exit/output fields. */
    suspend fun execShellResult(command: String): HelperShellResult? = null
    /** Generic helper-side shell execution. Keep call sites narrow and logged. */
    suspend fun execShell(command: String): String? = null
    /** Starts AVMCamera preview directly into [surface]. Viewpoints use the validated 2000-series map. */
    suspend fun startElectronicMirror(surface: Surface, viewpoint: Int, fps: Int): Boolean = false
    suspend fun switchElectronicMirrorView(viewpoint: Int): Boolean = false
    suspend fun stopElectronicMirror(): Boolean = false
}

@Singleton
open class HelperClientImpl @Inject constructor() : HelperClient {
    // Vehicle reads are frequent and must not queue behind long projection/audio shell commands.
    // Binder itself is thread-safe; separate lanes preserve write ordering while allowing snapshots
    // to continue during an unrelated long-running helper transaction.
    private val readMutex = Mutex()
    private val commandMutex = Mutex()
    @Volatile private var cached: IBinder? = null

    override suspend fun read(dev: Int, fid: Int, tx: Int): Long? =
        transact(HelperBinderProtocol.TX_READ) { it.writeInt(tx); it.writeInt(dev); it.writeInt(fid) }
            ?.let { (status, value) -> if (readAccepted(status)) value.toLong() else null }

    override suspend fun readBatch(specs: List<VehicleReadSpec>): List<VehicleRawRead>? {
        if (specs.isEmpty()) return emptyList()
        if (specs.size > HelperBinderProtocol.MAX_BATCH_READS) {
            WjxFileLogger.w(TAG, "readBatch rejected size=${specs.size}")
            return null
        }
        val timeoutMs = (BATCH_TIMEOUT_BASE_MS + specs.size * BATCH_TIMEOUT_PER_READ_MS)
            .coerceAtMost(BATCH_TIMEOUT_MAX_MS)
        return transactParsed(HelperBinderProtocol.TX_READ_BATCH, { data ->
            data.writeInt(specs.size)
            specs.forEach { spec ->
                data.writeInt(spec.transaction)
                data.writeInt(spec.device)
                data.writeInt(spec.fid)
            }
        }, timeoutMs = timeoutMs) { reply ->
            val batchStatus = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            if (batchStatus != 0) return@transactParsed null
            val count = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            if (count != specs.size || count !in 0..HelperBinderProtocol.MAX_BATCH_READS) {
                WjxFileLogger.w(TAG, "readBatch count mismatch requested=${specs.size} reply=$count")
                return@transactParsed null
            }
            buildList(count) {
                repeat(count) {
                    if (reply.dataAvail() < 8) return@transactParsed null
                    add(VehicleRawRead(status = reply.readInt(), value = reply.readInt()))
                }
            }
        }
    }

    override suspend fun write(dev: Int, fid: Int, value: Int): Boolean =
        writeStatus(dev, fid, value)?.let { writeAccepted(it) } ?: false

    override suspend fun writeStatus(dev: Int, fid: Int, value: Int): Int? {
        val status = transact(HelperBinderProtocol.TX_WRITE) {
            it.writeInt(dev); it.writeInt(fid); it.writeInt(value)
        }?.first
        // status forwarded from the autoservice setInt return code: 1 = real action,
        // 0 = accepted no-op (fid ineffective on this trim), <0 = error, null =
        // daemon unreachable. Logged at INFO so a "green" automation that physically
        // did nothing (no-op) is distinguishable from one that actually moved the actuator.
        if (isExteriorAudioWrite(dev, fid) || isAutoDoorWrite(dev, fid) || status == null || !writeAccepted(status)) {
            WjxFileLogger.i(
                TAG,
                "write dev=$dev fid=$fid value=$value status=$status accepted=${status != null && writeAccepted(status)}"
            )
        } else {
            Log.i(TAG, "write dev=$dev fid=$fid value=$value status=$status accepted=true")
        }
        return status
    }

    override suspend fun isAlive(): Boolean =
        transact(HelperBinderProtocol.TX_PING) { }
            ?.let { (status, _) -> readAccepted(status) } ?: false

    override suspend fun protocolVersion(): Int? =
        transact(HelperBinderProtocol.TX_PING) { }
            ?.let { (status, version) -> version.takeIf { readAccepted(status) } }

    override suspend fun createVirtualDisplay(
        name: String, width: Int, height: Int, density: Int, flags: Int, surface: Surface,
    ): Int? = transactParsed(HelperBinderProtocol.TX_CREATE_VIRTUAL_DISPLAY, { d ->
        d.writeString(name); d.writeInt(width); d.writeInt(height); d.writeInt(density); d.writeInt(flags)
        surface.writeToParcel(d, 0)
    }) { reply ->
        val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
        val id = if (reply.dataAvail() >= 4) reply.readInt() else -1
        if (readAccepted(status) && id > 0) id else null
    }

    override suspend fun releaseVirtualDisplay(displayId: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_RELEASE_VIRTUAL_DISPLAY) { it.writeInt(displayId) }

    override suspend fun launchApp(packageName: String): Boolean =
        statusOk(HelperBinderProtocol.TX_LAUNCH_APP) { it.writeString(packageName) }

    override suspend fun getTaskId(packageName: String): Int? =
        transact(HelperBinderProtocol.TX_GET_TASK_ID) { it.writeString(packageName) }
            ?.let { (status, value) -> if (readAccepted(status) && value > 0) value else null }

    override suspend fun moveTaskToDisplay(taskId: Int, displayId: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_MOVE_TASK_TO_DISPLAY) { it.writeInt(taskId); it.writeInt(displayId) }

    override suspend fun setTaskBounds(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_SET_TASK_BOUNDS) {
            it.writeInt(taskId); it.writeInt(left); it.writeInt(top); it.writeInt(right); it.writeInt(bottom)
        }

    override suspend fun setFocusedTask(taskId: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_SET_FOCUSED_TASK) { it.writeInt(taskId) }

    override suspend fun setTaskWindowingMode(taskId: Int, windowingMode: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_SET_TASK_WINDOWING_MODE) { it.writeInt(taskId); it.writeInt(windowingMode) }

    override suspend fun grantOverlayPermission(): Boolean =
        statusOk(HelperBinderProtocol.TX_GRANT_OVERLAY_PERMISSION) { }

    override suspend fun launchAndForce(packageName: String, displayId: Int, width: Int, height: Int): Boolean =
        transactParsed(HelperBinderProtocol.TX_LAUNCH_AND_FORCE, { d ->
            d.writeString(packageName); d.writeInt(displayId); d.writeInt(width); d.writeInt(height)
        }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
            readAccepted(status)
        } ?: false

    // FORCE_TIMEOUT_MS, not the default 2s: the daemon does a 200ms re-bind pause plus several
    // `settings` process spawns, which can outrun REQ_TIMEOUT_MS on a cold device. status 0 = ok.
    override suspend fun enableAccessibilityService(): Boolean =
        transactParsed(HelperBinderProtocol.TX_ENABLE_ACCESSIBILITY, { }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
            status == 0
        } ?: false

    override suspend fun readVehicleVinRaw(): String? =
        transactParsed(HelperBinderProtocol.TX_READ_VEHICLE_VIN, { }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            val raw = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
            WjxFileLogger.i(TAG, "readVehicleVinRaw status=$status rawLength=${raw.length}")
            raw.takeIf { it.isNotBlank() }
        }

    override suspend fun syncLockSoundConfig(json: String): Boolean =
        transactParsed(HelperBinderProtocol.TX_SYNC_LOCK_SOUND_CONFIG, { d ->
            d.writeString(json)
        }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
            status == 0
        } ?: false

    override suspend fun debugPlayLockSound(kind: Int): Boolean =
        transactParsed(HelperBinderProtocol.TX_DEBUG_PLAY_LOCK_SOUND, { d ->
            d.writeInt(kind)
        }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
            val played = if (reply.dataAvail() >= 4) reply.readInt() else 0
            status == 0 && played == 1
        } ?: false

    override suspend fun stopLockSound(kind: Int): Boolean =
        transactParsed(HelperBinderProtocol.TX_STOP_LOCK_SOUND, { d ->
            d.writeInt(kind)
        }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
            val stopped = if (reply.dataAvail() >= 4) reply.readInt() else 0
            status == 0 && stopped == 1
        } ?: false

    override suspend fun sentryShell(op: String, arg: String): String? =
        transactParsed(HelperBinderProtocol.TX_SENTRY_SHELL, { d ->
            d.writeString(op)
            d.writeString(arg)
        }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            val value = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
            if (status == 0) value else null
        }

    override suspend fun execShellResult(command: String): HelperShellResult? =
        transactParsed(HelperBinderProtocol.TX_EXEC_SHELL, { d ->
            d.writeString(command)
        }, timeoutMs = DEV_SHELL_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            val exitCode = if (reply.dataAvail() >= 4) reply.readInt() else -1
            val output = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
            WjxFileLogger.i(TAG, "execShell status=$status exit=$exitCode outputLength=${output.length}")
            HelperShellResult(status, exitCode, output)
        }

    override suspend fun execShell(command: String): String? =
        execShellResult(command)?.takeIf { it.status == 0 }?.output

    override suspend fun startElectronicMirror(surface: Surface, viewpoint: Int, fps: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_CAMERA_START, timeoutMs = FORCE_TIMEOUT_MS) { data ->
            data.writeInt(viewpoint)
            data.writeInt(fps)
            surface.writeToParcel(data, 0)
        }

    override suspend fun switchElectronicMirrorView(viewpoint: Int): Boolean =
        statusOk(HelperBinderProtocol.TX_CAMERA_SWITCH_VIEW, timeoutMs = FORCE_TIMEOUT_MS) { data ->
            data.writeInt(viewpoint)
        }

    override suspend fun stopElectronicMirror(): Boolean =
        statusOk(HelperBinderProtocol.TX_CAMERA_STOP) { }

    /** (status,value) reply; true iff status == 0. Shared by the boolean projection ops. */
    private suspend fun statusOk(
        code: Int,
        timeoutMs: Long = REQ_TIMEOUT_MS,
        writeArgs: (Parcel) -> Unit,
    ): Boolean = transactParsed(code, writeArgs, timeoutMs = timeoutMs) { reply ->
        val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
        if (reply.dataAvail() >= 4) reply.readInt()
        readAccepted(status)
    } ?: false

    /** Returns the parsed reply or null on any failure. Retries ONCE on a dead cached binder. */
    private suspend fun <T> transactParsed(
        code: Int,
        writeArgs: (Parcel) -> Unit,
        timeoutMs: Long = REQ_TIMEOUT_MS,
        parse: (Parcel) -> T?,
    ): T? = HelperProcessGate.withOperation {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                mutexFor(code).withLock {
                    repeat(2) { attempt ->
                        val binder = ensureBinder() ?: return@withLock null
                        val data = Parcel.obtain()
                        val reply = Parcel.obtain()
                        try {
                            data.writeInterfaceToken(HelperBinderProtocol.DESCRIPTOR)
                            writeArgs(data)
                            val ok = binder.transact(code, data, reply, 0)
                            if (!ok) return@withLock null           // uid gate rejected / not handled
                            return@withLock parse(reply)
                        } catch (e: DeadObjectException) {
                            cached = null                            // stale binder; loop re-resolves once
                            if (attempt == 1) { WjxFileLogger.w(TAG, "binder dead after retry: ${e.message}", e); return@withLock null }
                        } catch (e: Exception) {
                            WjxFileLogger.w(TAG, "transact failed code=$code: ${e.message}", e); return@withLock null
                        } finally {
                            data.recycle(); reply.recycle()
                        }
                    }
                    null
                }
            }
        }
    }

    private fun mutexFor(code: Int): Mutex = when (code) {
        HelperBinderProtocol.TX_READ,
        HelperBinderProtocol.TX_READ_BATCH,
        HelperBinderProtocol.TX_PING -> readMutex
        else -> commandMutex
    }

    /** (status, value) wrapper used by read/write/ping. */
    private suspend fun transact(code: Int, writeArgs: (Parcel) -> Unit): Pair<Int, Int>? =
        transactParsed(code, writeArgs) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            val value = if (reply.dataAvail() >= 4) reply.readInt() else 0
            status to value
        }

    private fun ensureBinder(): IBinder? {
        cached?.takeIf { it.isBinderAlive }?.let { return it }
        return resolveBinder()?.also { cached = it }
    }

    /** Production: look the daemon up by name. Overridable so tests can inject a fake IBinder. */
    internal open fun resolveBinder(): IBinder? = try {
        val sm = Class.forName("android.os.ServiceManager")
        (sm.getMethod("getService", String::class.java)
            .invoke(null, HelperBinderProtocol.SERVICE_NAME) as? IBinder)
    } catch (e: Exception) {
        WjxFileLogger.w(TAG, "getService failed: ${e.message}", e); null
    }

    companion object {
        /**
         * The daemon forwards the raw autoservice transact return code in status.
         * Validated on Leopard 3 2026-05-28:
         *   setInt real action → status = 1
         *   setInt no-op       → status = 0
         *   getInt success     → status = 0 (value follows)
         *   error / no reply   → status < 0 (-1 daemon exception, -999 no reply data)
         *
         * A write is accepted on status >= 0 (using == 0 marked every real action
         * as a failure). A read is accepted only on status == 0.
         * See HelperClientStatusTest.
         */
        internal fun writeAccepted(status: Int): Boolean = status >= 0
        internal fun readAccepted(status: Int): Boolean = status == 0
        private fun isExteriorAudioWrite(dev: Int, fid: Int): Boolean =
            (dev == 3 && fid == -1442839934) ||
                (dev == 1000 && fid == -1796699511) ||
                (dev == 1000 && fid == 13) ||
                (dev == 1002 && fid == -1442839775) ||
                (dev == 1000 && fid == 891293750) ||
                (dev == 1000 && fid == 891293760) ||
                (dev == 1000 && fid == 372244513)
        private fun isAutoDoorWrite(dev: Int, fid: Int): Boolean =
            dev == 1001 && (
                fid == 1312817200 || // 0x4E400030 左后电滑门
                    fid == 1312817202    // 0x4E400032 右后电滑门
                )

        private const val TAG = "HelperClient"
        private const val REQ_TIMEOUT_MS = 2000L
        private const val BATCH_TIMEOUT_BASE_MS = 3000L
        private const val BATCH_TIMEOUT_PER_READ_MS = 50L
        private const val BATCH_TIMEOUT_MAX_MS = 15000L
        private const val FORCE_TIMEOUT_MS = 15000L
        private const val DEV_SHELL_TIMEOUT_MS = 620000L
    }
}
