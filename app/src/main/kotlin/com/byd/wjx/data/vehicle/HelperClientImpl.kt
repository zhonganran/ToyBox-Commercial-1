package com.byd.wjx.data.vehicle

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.view.Surface
import com.byd.wjx.helper.HelperBinderProtocol
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
open class HelperClientImpl @Inject constructor() : HelperClient {
    private val transport = HelperBinderTransport(::resolveBinder)

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
        transactParsed(HelperBinderProtocol.TX_PING, { }) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            val version = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            version.takeIf { readAccepted(status) }
        }

    override suspend fun runtimeVersionCode(): Int? =
        transactParsed(HelperBinderProtocol.TX_PING, { }) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            if (reply.dataAvail() < 4) return@transactParsed null
            reply.readInt()
            val versionCode = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            versionCode.takeIf { readAccepted(status) && it > 0 }
        }

    override suspend fun runtimeInstallIdentity(): String? =
        transactParsed(HelperBinderProtocol.TX_PING, { }) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            if (reply.dataAvail() < 8) return@transactParsed null
            reply.readInt()
            reply.readInt()
            val identity = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
            identity.takeIf { readAccepted(status) && it.isNotBlank() }
        }

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
        statusOk(
            HelperBinderProtocol.TX_GRANT_OVERLAY_PERMISSION,
            timeoutMs = PERMISSION_GRANT_TIMEOUT_MS,
        ) { }

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

    override suspend fun playSentryEvent(source: String): Boolean =
        transactParsed(HelperBinderProtocol.TX_PLAY_SENTRY_EVENT, { d ->
            d.writeString(source.take(200))
        }, timeoutMs = FORCE_TIMEOUT_MS) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed false
            val played = if (reply.dataAvail() >= 4) reply.readInt() else 0
            status == 0 && played == 1
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

    override suspend fun setLockSoundPlaybackState(enabled: Boolean): Boolean =
        statusOk(HelperBinderProtocol.TX_SET_LOCK_SOUND_PLAYBACK_STATE) { data ->
            data.writeInt(if (enabled) 1 else 0)
        }

    override suspend fun setClusterContainerMode(enabled: Boolean): Boolean =
        statusOk(
            HelperBinderProtocol.TX_SET_CLUSTER_CONTAINER_MODE,
            timeoutMs = CLUSTER_CONTAINER_TIMEOUT_MS,
        ) { data ->
            data.writeInt(if (enabled) 1 else 0)
        }

    /** (status,value) reply; true iff status == 0. Shared by the boolean projection ops. */
    private suspend fun statusOk(
        code: Int,
        timeoutMs: Long = HelperBinderTransport.DEFAULT_TIMEOUT_MS,
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
        timeoutMs: Long = HelperBinderTransport.DEFAULT_TIMEOUT_MS,
        parse: (Parcel) -> T?,
    ): T? = transport.transactParsed(code, writeArgs, timeoutMs, parse)

    /** (status, value) wrapper used by read/write/ping. */
    private suspend fun transact(code: Int, writeArgs: (Parcel) -> Unit): Pair<Int, Int>? =
        transactParsed(code, writeArgs) { reply ->
            val status = if (reply.dataAvail() >= 4) reply.readInt() else return@transactParsed null
            val value = if (reply.dataAvail() >= 4) reply.readInt() else 0
            status to value
        }

    /** Production: look the daemon up by name. Overridable so tests can inject a fake IBinder. */
    @SuppressLint("PrivateApi")
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
        private const val PERMISSION_GRANT_TIMEOUT_MS = 5000L
        private const val BATCH_TIMEOUT_BASE_MS = 3000L
        private const val BATCH_TIMEOUT_PER_READ_MS = 50L
        private const val BATCH_TIMEOUT_MAX_MS = 15000L
        private const val FORCE_TIMEOUT_MS = 15000L
        private const val DEV_SHELL_TIMEOUT_MS = 620000L
        private const val CLUSTER_CONTAINER_TIMEOUT_MS = 5_000L
    }
}
