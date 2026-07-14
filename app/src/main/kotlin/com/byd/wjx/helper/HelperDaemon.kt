@file:JvmName("HelperDaemon")
package com.byd.wjx.helper

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.view.Surface
import com.byd.wjx.audio.ExteriorChannel
import com.byd.wjx.audio.WjxAudioAttributes
import com.byd.wjx.audio.WjxExteriorSpeakerController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.exitProcess
import org.json.JSONObject

// Lock path on the device filesystem (writable by shell uid).
private const val LOCK_PATH = "/data/local/tmp/wjx_helper.lock"
internal const val SENTRY_IDLE_BASELINE_INTERVAL_MS = 30_000L
internal const val SENTRY_WARN_ALARM = 2
internal const val SENTRY_FALLBACK_CONFIRM_WINDOW_MS = 15_000L
internal const val SENTRY_RISK_EXIT_CONFIRM_SAMPLES = 3

internal fun shouldRunSentryIdleBaseline(
    nowMs: Long,
    lastBaselineAtMs: Long,
    wasGuarding: Boolean,
): Boolean = wasGuarding ||
    lastBaselineAtMs <= 0L ||
    nowMs - lastBaselineAtMs >= SENTRY_IDLE_BASELINE_INTERVAL_MS

internal fun shouldTriggerSentryFallback(
    nowMs: Long,
    lastHighRiskAtMs: Long,
    alarmTimestampChanged: Boolean,
    highRiskLogSeen: Boolean,
): Boolean {
    if (!alarmTimestampChanged && !highRiskLogSeen) return false
    if (lastHighRiskAtMs <= 0L) return false
    val age = nowMs - lastHighRiskAtMs
    return age in 0L..SENTRY_FALLBACK_CONFIRM_WINDOW_MS
}

/**
 * Acquires an exclusive file lock on [path]. Returns a (FileChannel, FileLock) pair on
 * success, or null if the lock is already held (either by another process or by the same JVM).
 *
 * Callers must keep the returned pair live until the lock should be released — do NOT close
 * the channel while the lock must be held.
 *
 * Internal so the JVM unit test can reach it from the same package.
 */
internal fun acquireSingleOwnerLock(path: String): Pair<FileChannel, FileLock>? {
    return try {
        val channel = RandomAccessFile(path, "rw").channel
        val lock = channel.tryLock()   // null when another PROCESS holds the region lock
        if (lock == null) {
            runCatching { channel.close() }
            null
        } else {
            channel to lock
        }
    } catch (e: OverlappingFileLockException) {
        // Same JVM already holds the lock on this file.
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Shell-uid binder daemon entry point. Spawned by the app via:
 *   CLASSPATH=<apk> app_process /system/bin \
 *     --nice-name=wjx_helper com.byd.wjx.helper.HelperDaemon <appUid>
 *
 * Lifecycle:
 *   1. Parse expectedUid from args[0].
 *   2. Acquire single-owner file lock — exits with ALREADY_RUNNING if held.
 *   3. Resolve autoservice IBinder reflectively.
 *   4. Register a Binder stub under SERVICE_NAME via ServiceManager.addService.
 *   5. Print READY and keepalive with Looper.loop().
 *
 * Hidden-API note: this daemon runs under app_process (tool context), NOT a normal
 * app process. The hidden-API enforcement layer is only active for app processes, so
 * plain reflection is fine here — do NOT use HiddenApiBypass in this file.
 */
fun main(args: Array<String>) {
    val expectedUid = args.getOrNull(0)?.toIntOrNull() ?: run {
        System.err.println("ERR: usage: HelperDaemon <appUid>")
        // exitProcess (not return): app_process keeps the binder threadpool's
        // non-daemon threads alive, so a bare `return` from main() would hang the JVM.
        exitProcess(2)
    }

    // Step 1: single-owner lock — prevents duplicate daemons.
    val lockPair = acquireSingleOwnerLock(LOCK_PATH)
    if (lockPair == null) {
        println("ALREADY_RUNNING")
        // Clean exit: another owner already holds the service. Must exitProcess
        // rather than return so this spawn does not linger as a zombie process.
        exitProcess(0)
    }
    // lockChannel and lockHandle stay referenced past Looper.loop() because the
    // stack frame is never unwound (loop() blocks forever). The lock stays live.
    @Suppress("UNUSED_VARIABLE") val lockChannel = lockPair.first
    @Suppress("UNUSED_VARIABLE") val lockHandle = lockPair.second

    // Prepare the main looper BEFORE ActivityThread.systemMain (OpenBYD EntryPoint order),
    // then acquire a system Context for the projection DisplayManager calls.
    @Suppress("DEPRECATION")
    Looper.prepareMainLooper()
    val systemContext: Context? = acquireSystemContext()

    // Step 2: resolve autoservice Binder.
    val smCls = Class.forName("android.os.ServiceManager")
    val svc: IBinder = smCls.getMethod("getService", String::class.java)
        .invoke(null, "autoservice") as? IBinder
        ?: run {
            System.err.println("ERR: autoservice not found")
            // exitProcess so the OS releases the file lock we already hold above;
            // a bare return would leave a hung daemon holding the lock forever.
            exitProcess(3)
        }
    val autoIface: String = svc.interfaceDescriptor ?: ""
    // Keeps created VirtualDisplays alive (their backing Surface comes from the app overlay).
    // Keyed by displayId so TX_RELEASE_VIRTUAL_DISPLAY can release the right one.
    val virtualDisplays = ConcurrentHashMap<Int, VirtualDisplay>()
    val lockSoundRuntime = WjxLockSoundRuntime(svc, autoIface, systemContext)

    // Step 3: build our stub Binder.
    val helperBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            // Uid gate first — only our app may call.
            if (Binder.getCallingUid() != expectedUid) return false

            data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)

            return when (code) {
                HelperBinderProtocol.TX_PING -> runCatching {
                    reply?.writeInt(0)
                    reply?.writeInt(HelperBinderProtocol.CURRENT_VERSION)
                    true
                }.getOrElse {
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_READ -> runCatching {
                    val tx = data.readInt()
                    val dev = data.readInt()
                    val fid = data.readInt()
                    val (status, retInt) = autoserviceTransact(svc, autoIface, tx, dev, fid, 0, writeValue = false)
                    reply?.writeInt(status)
                    reply?.writeInt(retInt)
                    true
                }.getOrElse {
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_READ_BATCH -> runCatching {
                    val count = data.readInt()
                    require(count in 0..HelperBinderProtocol.MAX_BATCH_READS) {
                        "invalid batch size=$count"
                    }
                    reply?.writeInt(0)
                    reply?.writeInt(count)
                    repeat(count) {
                        val tx = data.readInt()
                        val dev = data.readInt()
                        val fid = data.readInt()
                        val (status, retInt) = autoserviceTransact(
                            svc,
                            autoIface,
                            tx,
                            dev,
                            fid,
                            0,
                            writeValue = false,
                        )
                        reply?.writeInt(status)
                        reply?.writeInt(retInt)
                    }
                    true
                }.getOrElse {
                    helperLog("VehicleBatch", "read failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_WRITE -> runCatching {
                    val dev = data.readInt()
                    val fid = data.readInt()
                    val value = data.readInt()
                    val (status, retInt) = autoserviceTransact(svc, autoIface, 6, dev, fid, value, writeValue = true)
                    reply?.writeInt(status)
                    reply?.writeInt(retInt)
                    true
                }.getOrElse {
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_GET_TASK_ID -> runCatching {
                    val pkg = data.readString() ?: ""
                    val taskId = findTaskId(pkg)
                    reply?.writeInt(0); reply?.writeInt(taskId)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_MOVE_TASK_TO_DISPLAY -> runCatching {
                    val taskId = data.readInt(); val displayId = data.readInt()
                    moveTaskToDisplayReflect(taskId, displayId)
                    reply?.writeInt(0); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_SET_TASK_BOUNDS -> runCatching {
                    val taskId = data.readInt()
                    val l = data.readInt(); val t = data.readInt(); val r = data.readInt(); val b = data.readInt()
                    setTaskBoundsReflect(taskId, l, t, r, b)
                    reply?.writeInt(0); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_SET_FOCUSED_TASK -> runCatching {
                    setFocusedTaskReflect(data.readInt())
                    reply?.writeInt(0); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_SET_TASK_WINDOWING_MODE -> runCatching {
                    val taskId = data.readInt(); val mode = data.readInt()
                    setTaskWindowingModeReflect(taskId, mode)
                    reply?.writeInt(0); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_CREATE_VIRTUAL_DISPLAY -> runCatching {
                    val name = data.readString() ?: "SuperToyBox_VD"
                    val width = data.readInt(); val height = data.readInt()
                    val density = data.readInt(); val flags = data.readInt()
                    val surface = Surface.CREATOR.createFromParcel(data)
                    val ctx = systemContext
                    val id = if (ctx == null || !surface.isValid) -1
                            else createVirtualDisplay(ctx, virtualDisplays, name, width, height, density, surface, flags)
                    if (id > 0) { reply?.writeInt(0); reply?.writeInt(id) }
                    else { surface.release(); reply?.writeInt(-1); reply?.writeInt(0) }
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_RELEASE_VIRTUAL_DISPLAY -> runCatching {
                    val displayId = data.readInt()
                    val vd = virtualDisplays.remove(displayId)
                    vd?.release()
                    // Release is intentionally idempotent. After an app/process restart the app may
                    // only have a persisted display id while this daemon no longer owns it; returning
                    // success lets the app clear the stale handle instead of blocking projection.
                    reply?.writeInt(0); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_LAUNCH_APP -> runCatching {
                    val ok = launchApp(data.readString() ?: "")
                    reply?.writeInt(if (ok) 0 else -1); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_GRANT_OVERLAY_PERMISSION -> runCatching {
                    // Narrow, hardcoded — NOT a generic shell passthrough. Two appops plus the
                    // hidden capture grant AutoTark uses for cluster/lyric projection.
                    // SYSTEM_ALERT_WINDOW lets us draw the cluster overlay; PROJECT_MEDIA gates
                    // third-party access to the fission screen-projection display (without it
                    // DisplayManager.getDisplay(clusterId) returns null in our process).
                    val pkg = HelperBinderProtocol.APP_PACKAGE
                    val r1 = shExec("appops set \"\$1\" SYSTEM_ALERT_WINDOW allow", pkg)
                    val r2 = shExec("appops set \"\$1\" PROJECT_MEDIA allow", pkg)
                    val r3 = shExec("pm grant \"\$1\" android.permission.CAPTURE_VIDEO_OUTPUT", pkg)
                    helperLog("Permission", "grant overlay=${r1.code} project=${r2.code} capture=${r3.code}")
                    val ok = r1.code == 0 && r2.code == 0
                    reply?.writeInt(if (ok) 0 else -1); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_LAUNCH_AND_FORCE -> runCatching {
                    val pkg = data.readString() ?: ""
                    val displayId = data.readInt(); val width = data.readInt(); val height = data.readInt()
                    val ok = launchAndForce(pkg, displayId, width, height)
                    reply?.writeInt(if (ok) 0 else -1); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_ENABLE_ACCESSIBILITY -> runCatching {
                    val ok = enableAccessibilityService()
                    reply?.writeInt(if (ok) 0 else -1); reply?.writeInt(0)
                    true
                }.getOrElse { reply?.writeInt(-1); reply?.writeInt(0); true }

                HelperBinderProtocol.TX_READ_VEHICLE_VIN -> runCatching {
                    val raw = readVehicleVinFromBodywork(systemContext)
                    reply?.writeInt(if (rawHasVin(raw)) 0 else -1)
                    reply?.writeString(raw)
                    true
                }.getOrElse {
                    reply?.writeInt(-1)
                    reply?.writeString("error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    true
                }

                HelperBinderProtocol.TX_SENTRY_SHELL -> runCatching {
                    val op = data.readString().orEmpty()
                    val arg = data.readString().orEmpty()
                    val result = sentryShellRead(op, arg)
                    reply?.writeInt(result.code)
                    reply?.writeString(result.stdout)
                    true
                }.getOrElse {
                    reply?.writeInt(-1)
                    reply?.writeString("error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    true
                }

                HelperBinderProtocol.TX_EXEC_SHELL -> runCatching {
                    val command = data.readString().orEmpty()
                    val result = execShellRead(command)
                    reply?.writeInt(result.status)
                    reply?.writeInt(result.exitCode)
                    reply?.writeString(result.output)
                    true
                }.getOrElse {
                    reply?.writeInt(-1)
                    reply?.writeInt(-1)
                    reply?.writeString("error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    true
                }

                HelperBinderProtocol.TX_SYNC_LOCK_SOUND_CONFIG -> runCatching {
                    val json = data.readString().orEmpty()
                    val ok = lockSoundRuntime.syncConfig(json)
                    reply?.writeInt(if (ok) 0 else -1)
                    reply?.writeInt(0)
                    true
                }.getOrElse {
                    helperLog("LockSound", "sync failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_DEBUG_PLAY_LOCK_SOUND -> runCatching {
                    val kind = data.readInt()
                    val ok = lockSoundRuntime.playEventForBinder(kind, "binder")
                    reply?.writeInt(if (kind in 0..2) 0 else -2)
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }.getOrElse {
                    helperLog("LockSound", "debug play failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_STOP_LOCK_SOUND -> runCatching {
                    val kind = data.readInt()
                    val ok = lockSoundRuntime.stopEventForBinder(kind, "binder_stop")
                    reply?.writeInt(if (kind in 0..2) 0 else -2)
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }.getOrElse {
                    helperLog("LockSound", "stop failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }
    helperBinder.attachInterface(null, HelperBinderProtocol.DESCRIPTOR)

    // Step 4: register the stub with ServiceManager.
    try {
        smCls.getMethod("addService", String::class.java, IBinder::class.java)
            .invoke(null, HelperBinderProtocol.SERVICE_NAME, helperBinder)
    } catch (e: Exception) {
        System.err.println("ERR: addService ${e.message}")
        // exitProcess so the OS releases the file lock we hold; a bare return would
        // leave a hung lock-holding daemon and block every future spawn.
        exitProcess(4)
    }

    System.out.println("READY pid=${android.os.Process.myPid()}")
    System.out.flush()
    lockSoundRuntime.start()

    // Keepalive: Looper.loop() blocks this thread indefinitely so main() never returns
    // and the process stays alive. Incoming binder transactions are dispatched by the
    // binder threadpool that app_process starts via ProcessState::startThreadPool() in
    // AppRuntime::onStarted — Looper.loop() plays NO role in transaction dispatch here;
    // it is purely a blocking keepalive for the main thread.
    Looper.loop()
}

private class WjxLockSoundRuntime(
    private val svc: IBinder,
    private val autoIface: String,
    private val systemContext: Context?,
) {
    private val started = AtomicBoolean(false)
    private val playLock = Any()
    private val sentryStateLock = Any()
    private val speakerController = systemContext?.let { WjxExteriorSpeakerController(it) }
    @Volatile private var config: RuntimeConfig = readRuntimeConfig()
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var pcmThread: Thread? = null
    @Volatile private var enhancer: LoudnessEnhancer? = null
    @Volatile private var activeChannel: ExteriorChannel? = null
    @Volatile private var activeEvent: LockEvent? = null
    @Volatile private var activePlaybackToken: Long = 0L
    private var playbackTokenSeed = 0L
    private var sentryIncidentLatched = false

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (config.anyEnabled()) {
            runCatching { File(LOCK_SOUND_WATCHDOG_DISABLED_PATH).delete() }
            installWatchdog()
        }
        Thread({ lockLoop() }, "WJX-LockSoundPoll").apply { isDaemon = true; start() }
        Thread({ sentryLoop() }, "WJX-SentrySoundPoll").apply { isDaemon = true; start() }
        helperLog(TAG, "runtime started config=${config.summary()}")
    }

    fun syncConfig(json: String): Boolean {
        if (json.isBlank()) return false
        val incoming = JSONObject(json)
        val channel = parseChannel(incoming.optString("channel", ExteriorChannel.Channel1.name))
        val next = RuntimeConfig(
            channel = channel,
            unlock = copyEventConfig(incoming.optJSONObject(KEY_UNLOCK), LOCK_SOUND_UNLOCK_PATH, LOCK_SOUND_UNLOCK_PCM_PATH, "unlock"),
            lock = copyEventConfig(incoming.optJSONObject(KEY_LOCK), LOCK_SOUND_LOCK_PATH, LOCK_SOUND_LOCK_PCM_PATH, "lock"),
            sentry = copyEventConfig(incoming.optJSONObject(KEY_SENTRY), LOCK_SOUND_SENTRY_PATH, LOCK_SOUND_SENTRY_PCM_PATH, "sentry"),
        )
        writeRuntimeConfig(next)
        config = next
        if (next.anyEnabled()) {
            runCatching { File(LOCK_SOUND_WATCHDOG_DISABLED_PATH).delete() }
            installWatchdog()
        } else {
            runCatching { File(LOCK_SOUND_WATCHDOG_DISABLED_PATH).writeText("disabled\n") }
        }
        helperLog(TAG, "config synced ${next.summary()}")
        return true
    }

    fun playEvent(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        if (event == LockEvent.Sentry && !reserveSentryEvent(reason)) return false
        Thread({ playEventBlocking(event, reason) }, "WJX-LockSoundPlay-${event.key}").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun playEventForBinder(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        if (event == LockEvent.Sentry && !reserveSentryEvent(reason)) return false
        return playEventBlocking(event, reason)
    }

    fun stopEventForBinder(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        synchronized(playLock) {
            if (activeEvent != event) {
                helperLog(TAG, "stop ${event.key} ignored active=$activeEvent reason=$reason")
                return true
            }
            stopActiveLocked(reason, releaseRoute = true)
            return true
        }
    }

    private fun reserveSentryEvent(reason: String): Boolean {
        synchronized(sentryStateLock) {
            if (sentryIncidentLatched) {
                helperLog(TAG, "suppress sentry same incident reason=$reason")
                return false
            }
            sentryIncidentLatched = true
            helperLog(TAG, "reserve sentry incident reason=$reason")
            return true
        }
    }

    private fun resetSentryIncident(reason: String) {
        val reset = synchronized(sentryStateLock) {
            if (!sentryIncidentLatched) return@synchronized false
            sentryIncidentLatched = false
            true
        }
        if (reset) helperLog(TAG, "reset sentry incident reason=$reason")
    }

    private fun lockLoop() {
        var stableLock: Int? = readPersistedLockState()?.state
        var pendingLock: Int? = null
        var pendingSince = 0L
        var lastFlash: Int? = null
        var flashAt = 0L
        var flashStartLock: Int? = null
        var lastLockAt = 0L
        var lastUnlockAt = 0L
        var lastDriverDoorOpen = false
        var coldStartChecked = false
        fun updateStableLock(value: Int, reason: String, force: Boolean = false) {
            if (stableLock == value && !force) return
            stableLock = value
            writePersistedLockState(value, reason)
        }
        while (true) {
            try {
                val cfg = config
                if (!cfg.lock.enabled && !cfg.unlock.enabled) {
                    readLockState()?.let { stableLock = it }
                    sleepQuietly(1_000L)
                    continue
                }
                val now = System.currentTimeMillis()
                val lockState = readLockState()
                val flash = readTurnSignalFlash()
                val powerOn = (readPowerState() ?: 0) > 0
                val driverDoorOpen = readDriverDoorOpen() == true
                val driverDoorOpenEdge = driverDoorOpen && !lastDriverDoorOpen
                if (activeEvent == LockEvent.Unlock && driverDoorOpen) {
                    synchronized(playLock) {
                        if (activeEvent == LockEvent.Unlock) {
                            helperLog(
                                TAG,
                                "stop unlock: driver door ${if (driverDoorOpenEdge) "opened" else "already open"} channel=$activeChannel",
                            )
                            stopActiveLocked(
                                reason = "driver_door_open",
                                releaseRoute = true,
                                routeReleaseDelayMs = 0L,
                            )
                        }
                    }
                }
                lastDriverDoorOpen = driverDoorOpen
                if (lockState != null && stableLock == null) {
                    updateStableLock(lockState, "seed")
                }

                if (!coldStartChecked && lockState != null) {
                    coldStartChecked = true
                    val previous = readPersistedLockState()
                    if (
                        previous?.state == DOOR_LOCKED &&
                        lockState == DOOR_UNLOCKED &&
                        !powerOn &&
                        now - previous.updatedAtMs <= COLD_UNLOCK_MAX_PREV_AGE_MS &&
                        cfg.unlock.enabled &&
                        shouldPlayLockEdge(LockEvent.Unlock, now, lastLockAt, lastUnlockAt, "cold_start prev=${previous.state} age=${now - previous.updatedAtMs} flash=$flash")
                    ) {
                        lastUnlockAt = now
                        helperLog(TAG, "cold-start unlock compensation prev=${previous.state} age=${now - previous.updatedAtMs} flash=$flash")
                        playEvent(LockEvent.Unlock.kind, "cold_start prev=${previous.state} current=$lockState flash=$flash")
                    }
                    updateStableLock(lockState, "cold_start", force = true)
                }

                if (flash == TURN_FLASH_CONFIRM && lastFlash != TURN_FLASH_CONFIRM) {
                    flashAt = now
                    flashStartLock = lockState ?: stableLock
                    helperLog(TAG, "flash9 start lock=$flashStartLock")
                }

                val inFlashWindow = flash == TURN_FLASH_CONFIRM || (flashAt > 0L && now - flashAt <= LOCK_FLASH_WINDOW_MS)

                if (lockState != null && stableLock != null && lockState != stableLock && !powerOn) {
                    if (pendingLock != lockState) {
                        pendingLock = lockState
                        pendingSince = now
                    }
                    if (inFlashWindow) {
                        if (now - pendingSince >= LOCK_CONFIRM_MS) {
                            val event = eventForLockState(lockState)
                            if (event != null && shouldPlayLockEdge(event, now, lastLockAt, lastUnlockAt, "old=$stableLock new=$lockState flash=$flash")) {
                                if (event == LockEvent.Lock) lastLockAt = now else lastUnlockAt = now
                                playEvent(event.kind, "lock_state old=$stableLock new=$lockState flash=$flash")
                            }
                            updateStableLock(lockState, "lock_state")
                            pendingLock = null
                        }
                    } else if (now - pendingSince > LOCK_PENDING_EXPIRE_MS) {
                        val event = eventForLockState(lockState)
                        if (event != null && shouldPlayLockEdge(event, now, lastLockAt, lastUnlockAt, "no_flash old=$stableLock new=$lockState flash=$flash")) {
                            if (event == LockEvent.Lock) lastLockAt = now else lastUnlockAt = now
                            helperLog(TAG, "no-flash fallback play ${event.key} old=$stableLock new=$lockState flash=$flash")
                            playEvent(event.kind, "no_flash old=$stableLock new=$lockState flash=$flash")
                        } else {
                            helperLog(TAG, "lock state changed without flash old=$stableLock new=$lockState")
                        }
                        updateStableLock(lockState, "changed_without_flash")
                        pendingLock = null
                    }
                } else {
                    if (lockState != null && powerOn) {
                        updateStableLock(lockState, "power_on")
                    }
                    pendingLock = null
                }

                if (lastFlash == TURN_FLASH_CONFIRM && flash != TURN_FLASH_CONFIRM && !powerOn) {
                    val start = flashStartLock
                    if (start != null && lockState != null && lockState != start) {
                        val event = eventForLockState(lockState)
                        if (event != null && shouldPlayLockEdge(event, now, lastLockAt, lastUnlockAt, "flash_end start=$start new=$lockState flash=$flash")) {
                            if (event == LockEvent.Lock) lastLockAt = now else lastUnlockAt = now
                            playEvent(event.kind, "flash_end start=$start new=$lockState flash=$flash")
                            updateStableLock(lockState, "flash_end")
                        }
                    }
                    flashStartLock = null
                }
                lastFlash = flash
                sleepQuietly(if (activeEvent == LockEvent.Unlock || flash == TURN_FLASH_CONFIRM) 50L else 150L)
            } catch (t: Throwable) {
                helperLog(TAG, "lockLoop error ${throwableSummary(t)}")
                sleepQuietly(1_000L)
            }
        }
    }

    private fun eventForLockState(lockState: Int): LockEvent? =
        when (lockState) {
            DOOR_LOCKED -> LockEvent.Lock
            DOOR_UNLOCKED -> LockEvent.Unlock
            else -> null
        }

    private fun shouldPlayLockEdge(
        event: LockEvent,
        now: Long,
        lastLockAt: Long,
        lastUnlockAt: Long,
        detail: String,
    ): Boolean {
        val lastAt = if (event == LockEvent.Lock) lastLockAt else lastUnlockAt
        if (now - lastAt >= EVENT_SUPPRESS_MS) return true
        helperLog(TAG, "suppress ${event.key} duplicate $detail")
        return false
    }

    private fun sentryLoop() {
        var lastLogScanAt = 0L
        var lastIdleBaselineAt = 0L
        var lastHighRiskAt = 0L
        var riskExitSamples = 0
        var wasGuarding = false
        while (true) {
            try {
                if (!config.sentry.enabled) {
                    resetSentryIncident("disabled")
                    lastHighRiskAt = 0L
                    riskExitSamples = 0
                    lastIdleBaselineAt = 0L
                    wasGuarding = false
                    sleepQuietly(2_000L)
                    continue
                }
                val now = System.currentTimeMillis()
                val guarding = isSentryGuarding()
                if (!guarding) {
                    resetSentryIncident("not_guarding")
                    lastHighRiskAt = 0L
                    riskExitSamples = 0
                    if (shouldRunSentryIdleBaseline(now, lastIdleBaselineAt, wasGuarding)) {
                        val warning = readSentryWarning()
                        val propTs = shExec("getprop sys.byd.sentry_alarm_timestamp").stdout.trim()
                        consumeSentryAlarmTimestamp(propTs, allowTrigger = false)
                        lastLogScanAt = now
                        readFreshSentryLogMarker(allowTrigger = false)
                        lastIdleBaselineAt = now
                        helperLog(
                            TAG,
                            "sentry idle baseline warning=${warning.level} source=${warning.source} propTs=$propTs",
                        )
                    }
                    wasGuarding = false
                    sleepQuietly(2_000L)
                    continue
                }

                val warningReading = readSentryWarning()
                val warning = warningReading.level
                val propTs = shExec("getprop sys.byd.sentry_alarm_timestamp").stdout.trim()
                if (!wasGuarding) {
                    consumeSentryAlarmTimestamp(propTs, allowTrigger = false)
                    readFreshSentryLogMarker(allowTrigger = false)
                    lastLogScanAt = now
                    wasGuarding = true
                    helperLog(
                        TAG,
                        "sentry guarding baseline warning=$warning source=${warningReading.source} propTs=$propTs",
                    )
                }
                wasGuarding = true

                if (warning == SENTRY_WARN_ALARM) {
                    if (lastHighRiskAt <= 0L) {
                        helperLog(TAG, "sentry high risk observed source=${warningReading.source}")
                    }
                    lastHighRiskAt = now
                    riskExitSamples = 0
                } else if (warning != null) {
                    riskExitSamples = (riskExitSamples + 1).coerceAtMost(SENTRY_RISK_EXIT_CONFIRM_SAMPLES)
                    if (riskExitSamples >= SENTRY_RISK_EXIT_CONFIRM_SAMPLES && lastHighRiskAt > 0L) {
                        helperLog(TAG, "sentry risk exited warning=$warning source=${warningReading.source}")
                        lastHighRiskAt = 0L
                        resetSentryIncident("risk_exit warning=$warning")
                    }
                } else {
                    riskExitSamples = 0
                }

                val alarmTimestampChanged = consumeSentryAlarmTimestamp(propTs, allowTrigger = true)
                var highRiskLogMarker = ""
                if (now - lastLogScanAt >= SENTRY_LOG_SCAN_MS) {
                    lastLogScanAt = now
                    highRiskLogMarker = readFreshSentryLogMarker(allowTrigger = true)
                }

                if (shouldTriggerSentryFallback(
                        nowMs = now,
                        lastHighRiskAtMs = lastHighRiskAt,
                        alarmTimestampChanged = alarmTimestampChanged,
                        highRiskLogSeen = highRiskLogMarker.isNotBlank(),
                    )
                ) {
                    val reasons = ArrayList<String>(3)
                    reasons += "warning=$warning source=${warningReading.source}"
                    if (alarmTimestampChanged) reasons += "prop_ts=$propTs"
                    if (highRiskLogMarker.isNotBlank()) reasons += "logcat=${highRiskLogMarker.take(80)}"
                    playEvent(LockEvent.Sentry.kind, "sentry ${reasons.joinToString(" | ")}")
                }
                sleepQuietly(750L)
            } catch (t: Throwable) {
                helperLog(TAG, "sentryLoop error ${throwableSummary(t)}")
                sleepQuietly(2_000L)
            }
        }
    }

    private fun playEventBlocking(event: LockEvent, reason: String): Boolean = synchronized(playLock) {
        val cfg = config
        val eventCfg = cfg.eventConfig(event)
        if (event == LockEvent.Unlock && readDriverDoorOpen() == true) {
            helperLog(TAG, "skip unlock: driver door already open reason=$reason")
            return@synchronized false
        }
        if (!eventCfg.enabled) {
            helperLog(TAG, "skip ${event.key}: disabled reason=$reason")
            return@synchronized false
        }
        val file = File(eventCfg.path)
        if (!file.isFile || file.length() <= 0L) {
            helperLog(TAG, "skip ${event.key}: file missing path=${eventCfg.path} reason=$reason")
            return@synchronized false
        }
        if (event.looping && activeEvent == event && (player != null || audioTrack != null)) {
            helperLog(TAG, "keep ${event.key}: already playing reason=$reason")
            return@synchronized true
        }
        val routes = routeCandidates(cfg.channel)
        helperLog(
            TAG,
            "play ${event.key} file=${file.name} routes=$routes " +
                "playerVolume=1.0 playerGain=$LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB pcmGain=$LOCK_SOUND_PCM_ENHANCER_GAIN_MB reason=$reason"
        )
        stopActiveLocked("new_${event.key}", releaseRoute = true)
        routes.forEachIndexed { index, channel ->
            val routeReason = if (index == 0) reason else "$reason retry=${channel.shortLabel}"
            if (tryPlayEventOnChannelLocked(event, eventCfg, file, channel, routeReason)) {
                return@synchronized true
            }
            helperLog(TAG, "route failed event=${event.key} channel=$channel index=$index")
        }
        false
    }

    private fun routeCandidates(preferred: ExteriorChannel): List<ExteriorChannel> =
        listOf(preferred, if (preferred == ExteriorChannel.Channel0) ExteriorChannel.Channel1 else ExteriorChannel.Channel0)
            .distinct()

    private fun tryPlayEventOnChannelLocked(
        event: LockEvent,
        eventCfg: EventConfig,
        file: File,
        channel: ExteriorChannel,
        reason: String,
    ): Boolean {
        activeChannel = channel
        activeEvent = event
        val armed = armRoute(channel)
        if (!armed) {
            helperLog(TAG, "route arm failed event=${event.key} channel=$channel")
            disarmRoute(channel)
            activeChannel = null
            activeEvent = null
            return false
        }
        sleepQuietly(if (channel == ExteriorChannel.Channel0) 300L else 120L)
        if (event == LockEvent.Unlock && readDriverDoorOpen() == true) {
            helperLog(TAG, "cancel unlock before playback: driver door opened during route setup channel=$channel")
            stopActiveLocked(
                reason = "driver_door_open_before_playback",
                releaseRoute = true,
                routeReleaseDelayMs = 0L,
            )
            return true
        }
        val token = nextPlaybackTokenLocked()
        if (startPcmPlaybackLocked(event, eventCfg, file, channel, token)) {
            return true
        }
        if (startMediaPlayerLocked(file, event, channel, token, "pcm_unavailable")) {
            return true
        }
        stopActiveLocked("start_failed_${channel.shortLabel}", releaseRoute = true)
        return false
    }

    private fun nextPlaybackTokenLocked(): Long {
        playbackTokenSeed += 1
        activePlaybackToken = playbackTokenSeed
        return playbackTokenSeed
    }

    private fun startPcmPlaybackLocked(
        event: LockEvent,
        eventCfg: EventConfig,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
    ): Boolean {
        val pcm = eventCfg.pcm
        val pcmFile = File(pcm.path)
        if (!pcm.usable() || !pcmFile.isFile || pcmFile.length() <= 0L) {
            helperLog(
                TAG,
                "pcm unavailable event=${event.key} path=${pcm.path.ifBlank { "none" }} " +
                    "fallbackFile=${fallbackFile.name} exists=${fallbackFile.isFile} fallback=MediaPlayer"
            )
            return false
        }
        val channelMask = if (pcm.channelCount <= 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            helperLog(TAG, "pcm buffer invalid event=${event.key} rate=${pcm.sampleRate} channels=${pcm.channelCount} min=$minBuffer fallback=MediaPlayer")
            return false
        }
        val bufferBytes = max(minBuffer, PCM_TRACK_BUFFER_BYTES)
        if (!event.looping && startPcmStaticPlaybackLocked(event, fallbackFile, channel, token, pcm, pcmFile, channelMask, bufferBytes)) {
            return true
        }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.getOrElse {
            helperLog(TAG, "pcm AudioTrack build failed event=${event.key}: ${throwableSummary(it)} fallback=MediaPlayer")
            return false
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            helperLog(TAG, "pcm AudioTrack not initialized event=${event.key} state=${track.state} fallback=MediaPlayer")
            runCatching { track.release() }
            return false
        }
        return runCatching {
            applyTrackVolume(track)
            audioTrack = track
            enableEnhancer(track, channel)
            track.play()
            val writer = Thread({
                writePcmPlayback(token, event, fallbackFile, channel, pcm, pcmFile, track)
            }, "WJX-LockSoundPcm-${event.key}").apply {
                isDaemon = true
            }
            pcmThread = writer
            writer.start()
            helperLog(
                TAG,
                "pcm playback started event=${event.key} file=${pcmFile.name} bytes=${pcmFile.length()} " +
                    "rate=${pcm.sampleRate} channels=${pcm.channelCount} buffer=$bufferBytes"
            )
            true
        }.getOrElse {
            helperLog(TAG, "pcm start failed event=${event.key}: ${throwableSummary(it)} fallback=MediaPlayer")
            releaseAudioTrackOnly()
            false
        }
    }

    private fun startPcmStaticPlaybackLocked(
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
        pcm: PcmConfig,
        pcmFile: File,
        channelMask: Int,
        minBufferBytes: Int,
    ): Boolean {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        val leadBytes = alignPcmBytes(
            bytes = (pcm.sampleRate.toLong() * frameBytes * PCM_LEAD_SILENCE_MS / 1000L).toInt(),
            frameBytes = frameBytes,
        )
        val totalBytesLong = leadBytes.toLong() + pcmFile.length()
        if (totalBytesLong <= 0L || totalBytesLong > PCM_STATIC_MAX_BYTES || totalBytesLong > Int.MAX_VALUE) {
            helperLog(TAG, "pcm static skipped event=${event.key} bytes=$totalBytesLong")
            return false
        }
        val payload = ByteArray(totalBytesLong.toInt())
        val readOk = runCatching {
            pcmFile.inputStream().use { input ->
                var offset = leadBytes
                while (offset < payload.size) {
                    val read = input.read(payload, offset, payload.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                offset == payload.size
            }
        }.getOrDefault(false)
        if (!readOk) {
            helperLog(TAG, "pcm static read failed event=${event.key} file=${pcmFile.name}")
            return false
        }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(max(minBufferBytes, payload.size))
                .build()
        }.getOrElse {
            helperLog(TAG, "pcm static AudioTrack build failed event=${event.key}: ${throwableSummary(it)}")
            return false
        }
        if (track.state != AudioTrack.STATE_INITIALIZED && track.state != AudioTrack.STATE_NO_STATIC_DATA) {
            helperLog(TAG, "pcm static AudioTrack not initialized event=${event.key} state=${track.state}")
            runCatching { track.release() }
            return false
        }
        return runCatching {
            val written = track.write(payload, 0, payload.size)
            if (written < payload.size) throw IllegalStateException("static write=$written/${payload.size}")
            if (track.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("static state after write=${track.state}")
            applyTrackVolume(track)
            audioTrack = track
            enableEnhancer(track, channel)
            track.play()
            val watcher = Thread({
                waitForStaticPcmCompletion(token, event, fallbackFile, channel, payload.size, pcm, track)
            }, "WJX-LockSoundPcmStatic-${event.key}").apply { isDaemon = true }
            pcmThread = watcher
            watcher.start()
            helperLog(
                TAG,
                "pcm static started event=${event.key} file=${pcmFile.name} bytes=${payload.size} " +
                    "leadMs=$PCM_LEAD_SILENCE_MS rate=${pcm.sampleRate} channels=${pcm.channelCount}"
            )
            true
        }.getOrElse {
            helperLog(TAG, "pcm static start failed event=${event.key}: ${throwableSummary(it)} fallback=stream")
            releaseAudioTrackOnly()
            false
        }
    }

    private fun waitForStaticPcmCompletion(
        token: Long,
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        totalBytes: Int,
        pcm: PcmConfig,
        track: AudioTrack,
    ) {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        val bytesPerSecond = (pcm.sampleRate.toLong() * frameBytes).coerceAtLeast(1L)
        var remaining = ((totalBytes.toLong() * 1000L) / bytesPerSecond + 300L).coerceAtLeast(300L)
        try {
            while (remaining > 0L && activePlaybackToken == token && audioTrack === track) {
                val sleep = minOf(100L, remaining)
                sleepQuietly(sleep)
                remaining -= sleep
            }
            if (activePlaybackToken == token && audioTrack === track) {
                completePlayback("pcm_static_completion", token)
            }
        } catch (t: Throwable) {
            helperLog(TAG, "pcm static watcher error event=${event.key}: ${throwableSummary(t)} fallback=MediaPlayer")
            fallbackToMediaPlayerFromPcm(token, event, fallbackFile, channel, t)
        }
    }

    private fun writePcmPlayback(
        token: Long,
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        pcm: PcmConfig,
        pcmFile: File,
        track: AudioTrack,
    ) {
        val buffer = ByteArray(PCM_WRITE_BUFFER_BYTES)
        var completed = false
        var totalWritten = 0L
        try {
            totalWritten += writePcmLeadSilence(token, track, pcm)
            do {
                completed = false
                pcmFile.inputStream().use { input ->
                    while (activePlaybackToken == token && audioTrack === track) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            completed = true
                            break
                        }
                        var offset = 0
                        while (offset < read && activePlaybackToken == token && audioTrack === track) {
                            val written = track.write(buffer, offset, read - offset, AudioTrack.WRITE_BLOCKING)
                            if (written < 0) throw IllegalStateException("AudioTrack.write=$written")
                            if (written == 0) {
                                sleepQuietly(5L)
                            } else {
                                offset += written
                                totalWritten += written
                            }
                        }
                    }
                }
                if (completed && event.looping && activePlaybackToken == token && audioTrack === track) {
                    helperLog(TAG, "pcm loop restart event=${event.key}")
                }
            } while (event.looping && completed && activePlaybackToken == token && audioTrack === track)
            if (completed && !event.looping && activePlaybackToken == token && audioTrack === track) {
                waitForStreamPcmDrain(token, event, pcm, track, totalWritten)
                completePlayback("pcm_completion", token)
            }
        } catch (t: Throwable) {
            helperLog(TAG, "pcm playback error event=${event.key}: ${throwableSummary(t)} fallback=MediaPlayer")
            fallbackToMediaPlayerFromPcm(token, event, fallbackFile, channel, t)
        }
    }

    private fun waitForStreamPcmDrain(
        token: Long,
        event: LockEvent,
        pcm: PcmConfig,
        track: AudioTrack,
        totalBytes: Long,
    ) {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        val totalFrames = (totalBytes / frameBytes).coerceAtLeast(1L)
        val bytesPerSecond = (pcm.sampleRate.toLong() * frameBytes).coerceAtLeast(1L)
        val maxWaitMs = ((totalBytes * 1000L) / bytesPerSecond + 600L).coerceAtLeast(300L)
        val startedAt = System.currentTimeMillis()
        var lastHead = 0L
        while (activePlaybackToken == token && audioTrack === track) {
            val head = runCatching { track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(lastHead)
            lastHead = head
            if (head >= totalFrames) break
            if (System.currentTimeMillis() - startedAt >= maxWaitMs) break
            sleepQuietly(40L)
        }
        helperLog(
            TAG,
            "pcm stream drain event=${event.key} frames=$lastHead/$totalFrames wait=${System.currentTimeMillis() - startedAt}ms"
        )
    }

    private fun writePcmLeadSilence(token: Long, track: AudioTrack, pcm: PcmConfig): Long {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        var remaining = alignPcmBytes(
            bytes = (pcm.sampleRate.toLong() * frameBytes * PCM_LEAD_SILENCE_MS / 1000L).toInt(),
            frameBytes = frameBytes,
        )
        if (remaining <= 0) return 0L
        val chunk = ByteArray(alignPcmBytes(minOf(PCM_WRITE_BUFFER_BYTES, remaining), frameBytes))
        var totalWritten = 0L
        while (remaining > 0 && activePlaybackToken == token && audioTrack === track) {
            val toWrite = minOf(chunk.size, remaining)
            var offset = 0
            while (offset < toWrite && activePlaybackToken == token && audioTrack === track) {
                val written = track.write(chunk, offset, toWrite - offset, AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw IllegalStateException("AudioTrack.write silence=$written")
                if (written == 0) {
                    sleepQuietly(5L)
                } else {
                    offset += written
                    totalWritten += written
                }
            }
            remaining -= toWrite
        }
        return totalWritten
    }

    private fun alignPcmBytes(bytes: Int, frameBytes: Int): Int {
        if (bytes <= 0) return 0
        val frame = frameBytes.coerceAtLeast(1)
        return bytes - (bytes % frame)
    }

    private fun fallbackToMediaPlayerFromPcm(
        token: Long,
        event: LockEvent,
        file: File,
        channel: ExteriorChannel,
        cause: Throwable,
    ) {
        synchronized(playLock) {
            if (activePlaybackToken != token || activeEvent != event) {
                helperLog(TAG, "ignore stale pcm fallback event=${event.key} cause=${throwableSummary(cause)}")
                return
            }
            releaseAudioTrackOnly()
            if (!startMediaPlayerLocked(file, event, channel, token, "pcm_error:${cause.javaClass.simpleName}")) {
                stopActiveLocked("pcm_fallback_failed", releaseRoute = true)
            }
        }
    }

    private fun startMediaPlayerLocked(
        file: File,
        event: LockEvent,
        channel: ExteriorChannel,
        token: Long,
        reason: String,
    ): Boolean = runCatching {
        val mp = MediaPlayer().apply {
            setAudioAttributes(WjxAudioAttributes.forOutside(channel))
            setDataSource(file.absolutePath)
            setVolume(1.0f, 1.0f)
            isLooping = event.looping
            setOnCompletionListener { completePlayback("completion", token) }
            setOnErrorListener { _, what, extra ->
                helperLog(TAG, "player error event=${event.key} what=$what extra=$extra")
                completePlayback("error:$what/$extra", token)
                true
            }
            prepare()
        }
        player = mp
        enableEnhancer(mp, channel)
        mp.start()
        helperLog(TAG, "MediaPlayer started event=${event.key} reason=$reason file=${file.name}")
        true
    }.getOrElse {
        helperLog(TAG, "MediaPlayer failed event=${event.key} reason=$reason: ${throwableSummary(it)}")
        false
    }

    private fun installWatchdog() {
        runCatching {
            val pidFile = File(LOCK_SOUND_WATCHDOG_PID_PATH)
            val versionFile = File(LOCK_SOUND_WATCHDOG_VERSION_PATH)
            val installedVersion = runCatching { versionFile.readText().trim().toInt() }.getOrNull()
            if (installedVersion != LOCK_SOUND_WATCHDOG_VERSION) {
                val oldPid = runCatching { pidFile.readText().trim() }.getOrDefault("")
                if (oldPid.matches(Regex("""\d+"""))) {
                    runCatching {
                        Runtime.getRuntime().exec(arrayOf("kill", oldPid)).waitFor(2, TimeUnit.SECONDS)
                    }
                }
                pidFile.delete()
            }
            val script = File(LOCK_SOUND_WATCHDOG_PATH)
            script.writeText(
                """
                #!/system/bin/sh
                mkdir -p /sdcard/WJX 2>/dev/null
                LOG="/sdcard/WJX/wjx.log"
                if [ -f "/data/local/tmp/wjx_lock_watch.pid" ]; then
                  OLD=${'$'}(cat "/data/local/tmp/wjx_lock_watch.pid" 2>/dev/null)
                  if [ -n "${'$'}OLD" ] && kill -0 "${'$'}OLD" 2>/dev/null; then exit 0; fi
                fi
                echo ${'$'}${'$'} > "/data/local/tmp/wjx_lock_watch.pid"
                LAST_PACKAGE_CHECK=0
                while true; do
                  if [ -f "/data/local/tmp/wjx_lock_watch.disabled" ]; then
                    rm -f /data/local/tmp/wjx_lock_watch.pid 2>/dev/null
                    exit 0
                  fi
                  NOW=${'$'}(date +%s)
                  if [ ${'$'}((NOW - LAST_PACKAGE_CHECK)) -ge 300 ]; then
                    APK=${'$'}(pm path com.byd.wjx 2>/dev/null | head -1 | cut -d: -f2)
                    if [ -z "${'$'}APK" ]; then
                      echo "${'$'}(date) I/LockSoundWatch package missing; cleanup wjx helper artifacts" >> "${'$'}LOG"
                      pkill -9 -f wjx_helper 2>/dev/null
                      rm -f /data/local/tmp/wjx_helper.lock /data/local/tmp/wjx_lock_watch.pid /data/local/tmp/wjx_lock_watch.version /data/local/tmp/wjx_lock_sound.json 2>/dev/null
                      exit 0
                    fi
                    LAST_PACKAGE_CHECK=${'$'}NOW
                  fi
                  PIDS=${'$'}(pidof wjx_helper 2>/dev/null)
                  COUNT=${'$'}(echo "${'$'}PIDS" | tr ' ' '\n' | grep -c '^[0-9][0-9]*${'$'}')
                  if [ "${'$'}COUNT" -gt 1 ]; then
                    echo "${'$'}(date) I/LockSoundWatch duplicate helper pids=${'$'}PIDS; restart single owner" >> "${'$'}LOG"
                    pkill -9 -f wjx_helper 2>/dev/null
                    rm -f /data/local/tmp/wjx_helper.lock 2>/dev/null
                    sleep 1
                    COUNT=0
                  fi
                  if [ "${'$'}COUNT" -eq 0 ]; then
                    if [ -f "/data/local/tmp/wjx_helper_last_restart" ]; then
                      PREV=${'$'}(cat "/data/local/tmp/wjx_helper_last_restart" 2>/dev/null)
                      if [ -n "${'$'}PREV" ]; then
                        AGE=${'$'}((NOW - PREV))
                        if [ "${'$'}AGE" -lt 15 ]; then
                          sleep 2
                          continue
                        fi
                      fi
                    fi
                    if [ -f "/data/local/tmp/wjx_helper_start.sh" ]; then
                      sh "/data/local/tmp/wjx_helper_start.sh" >/dev/null 2>&1
                    else
                      am start-foreground-service -n com.byd.wjx/com.byd.wjx.service.KeepAliveService --es reason lock_watch >/dev/null 2>&1 \
                        || am startservice -n com.byd.wjx/com.byd.wjx.service.KeepAliveService --es reason lock_watch >/dev/null 2>&1
                    fi
                    date +%s > "/data/local/tmp/wjx_helper_last_restart"
                    sleep 2
                    HPID2=${'$'}(pidof wjx_helper 2>/dev/null)
                    if [ -n "${'$'}HPID2" ]; then
                      echo "${'$'}(date) I/LockSoundWatch helper restarted pid=${'$'}HPID2" >> "${'$'}LOG"
                    else
                      echo "${'$'}(date) I/LockSoundWatch helper restart requested via service" >> "${'$'}LOG"
                    fi
                  fi
                  sleep 5
                done
                """.trimIndent(),
            )
            versionFile.writeText("$LOCK_SOUND_WATCHDOG_VERSION\n")
            Runtime.getRuntime().exec(arrayOf("chmod", "755", LOCK_SOUND_WATCHDOG_PATH)).waitFor(2, TimeUnit.SECONDS)
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "setsid sh $LOCK_SOUND_WATCHDOG_PATH </dev/null >/dev/null 2>&1 &"))
            helperLog(TAG, "watchdog installed path=$LOCK_SOUND_WATCHDOG_PATH")
        }.onFailure {
            helperLog(TAG, "watchdog install failed: ${throwableSummary(it)}")
        }
    }

    private fun completePlayback(reason: String, token: Long? = null) {
        Thread({
            synchronized(playLock) {
                if (token != null && token != activePlaybackToken) {
                    helperLog(TAG, "ignore stale completion reason=$reason token=$token active=$activePlaybackToken")
                    return@synchronized
                }
                helperLog(TAG, "complete playback reason=$reason")
                stopActiveLocked(reason, releaseRoute = true)
            }
        }, "WJX-LockSoundStop").apply { isDaemon = true; start() }
    }

    private fun stopActiveLocked(
        reason: String,
        releaseRoute: Boolean,
        routeReleaseDelayMs: Long = ROUTE_RELEASE_DRAIN_MS,
    ) {
        val channel = activeChannel
        muteAndReleasePlayer()
        playbackTokenSeed += 1
        activePlaybackToken = playbackTokenSeed
        if (releaseRoute && channel != null) {
            if (routeReleaseDelayMs > 0L) sleepQuietly(routeReleaseDelayMs)
            disarmRoute(channel)
        }
        activeChannel = null
        activeEvent = null
        helperLog(TAG, "stopped reason=$reason channel=$channel")
    }

    private fun muteAndReleasePlayer() {
        releaseMediaPlayerOnly()
        releaseAudioTrackOnly()
        pcmThread = null
    }

    private fun releaseMediaPlayerOnly() {
        val mp = player
        if (mp == null) return
        runCatching { mp?.setOnCompletionListener(null) }
        runCatching { mp?.setOnErrorListener(null) }
        runCatching { mp?.setVolume(0f, 0f) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.setTargetGain(0) }
        sleepQuietly(220L)
        runCatching { mp?.pause() }
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        runCatching { enhancer?.release() }
        player = null
        enhancer = null
    }

    private fun releaseAudioTrackOnly() {
        val track = audioTrack
        if (track == null) return
        runCatching { track?.setVolume(0f) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.setTargetGain(0) }
        sleepQuietly(80L)
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        runCatching { enhancer?.release() }
        audioTrack = null
        enhancer = null
    }

    private fun enableEnhancer(mediaPlayer: MediaPlayer, channel: ExteriorChannel) {
        runCatching {
            val gain = LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB
            enhancer = LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            helperLog(TAG, "enhancer enabled gain=$gain playerVolume=1.0 channel=$channel mode=player")
        }.onFailure {
            helperLog(TAG, "enhancer failed: ${throwableSummary(it)}")
        }
    }

    private fun enableEnhancer(track: AudioTrack, channel: ExteriorChannel) {
        runCatching {
            val gain = LOCK_SOUND_PCM_ENHANCER_GAIN_MB
            enhancer = LoudnessEnhancer(track.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            helperLog(TAG, "pcm enhancer enabled gain=$gain trackVolume=full channel=$channel mode=pcm")
        }.onFailure {
            helperLog(TAG, "pcm enhancer failed: ${throwableSummary(it)}")
        }
    }

    private fun applyTrackVolume(track: AudioTrack) {
        val volume = AudioTrack.getMaxVolume()
        runCatching { track.setVolume(volume) }.onFailure {
            helperLog(TAG, "pcm volume failed: ${throwableSummary(it)}")
        }
    }

    private fun armRoute(channel: ExteriorChannel): Boolean {
        runCatching {
            speakerController?.acquire(
                channel,
                volumePercent = LOCK_SOUND_ROUTE_VOLUME_PERCENT,
                fallbackVolume = if (channel == ExteriorChannel.Channel0) {
                    WjxExteriorSpeakerController.CHANNEL0_PLAYBACK_PRESET_VOLUME
                } else {
                    -1
                },
            )
        }
        val fidOk = when (channel) {
            ExteriorChannel.Channel1 -> writeAny(
                Triple(DEV_AUDIO, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET, LOCK_CAR_SOUND_PLAYBACK_ON),
                Triple(DEV_AUDIO_COMPAT, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET_COMPAT, 1),
                Triple(DEV_AUDIO, EXTERIOR_SWITCH_SET, SWITCH_ON),
                Triple(DEV_AUDIO, AVAS_ROUTE_SET, AVAS_ROUTE_ON),
                Triple(DEV_AUDIO, AUDIO_KARAOKE_MODE_SET, KARAOKE_MODE_EXTERIOR),
            )
            ExteriorChannel.Channel0 -> writeAny(
                Triple(DEV_ADAS, ADAS_UE_BROADCAST_POSITION_SET, ADAS_BROADCAST_POSITION_ON),
                Triple(DEV_ADAS_COMPAT, ADAS_UE_BROADCAST_POSITION_SET_COMPAT, 1),
            )
        }
        if (channel == ExteriorChannel.Channel1) setCarLockSoundPlayState(true)
        return fidOk
    }

    private fun disarmRoute(channel: ExteriorChannel) {
        runCatching {
            when (channel) {
                ExteriorChannel.Channel1 -> {
                    writeAny(
                        Triple(DEV_AUDIO, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET, LOCK_CAR_SOUND_PLAYBACK_OFF),
                        Triple(DEV_AUDIO_COMPAT, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET_COMPAT, 0),
                        Triple(DEV_AUDIO, EXTERIOR_SWITCH_SET, SWITCH_OFF),
                        Triple(DEV_AUDIO, AVAS_ROUTE_SET, AVAS_ROUTE_OFF),
                    )
                    setCarLockSoundPlayState(false)
                }
                ExteriorChannel.Channel0 -> writeAny(
                    Triple(DEV_ADAS, ADAS_UE_BROADCAST_POSITION_SET, ADAS_BROADCAST_POSITION_OFF),
                    Triple(DEV_ADAS_COMPAT, ADAS_UE_BROADCAST_POSITION_SET_COMPAT, 0),
                )
            }
            speakerController?.release(channel)
        }.onFailure {
            helperLog(TAG, "disarm failed channel=$channel ${throwableSummary(it)}")
        }
    }

    private fun writeAny(vararg writes: Triple<Int, Int, Int>): Boolean {
        var ok = false
        writes.distinct().forEach { (dev, fid, value) ->
            val status = autoserviceTransact(svc, autoIface, 6, dev, fid, value, writeValue = true).first
            helperLog(TAG, "route write dev=$dev fid=$fid value=$value status=$status")
            ok = status >= 0 || ok
        }
        return ok
    }

    private fun readAny(vararg reads: Pair<Int, Int>): Int? {
        reads.forEach { (dev, fid) ->
            val (status, value) = autoserviceTransact(svc, autoIface, 5, dev, fid, 0, writeValue = false)
            if (status == 0 && isUsefulVehicleValue(value)) return value
        }
        return null
    }

    private fun readLockState(): Int? =
        normalizeLockState(readAny(
            1032 to 1081081864,
            1041 to -1066401784,
            1041 to 960495668,
        ) ?: readDoorLockBySdk())

    private fun normalizeLockState(raw: Int?): Int? =
        when (raw) {
            0, 1 -> DOOR_UNLOCKED
            2 -> DOOR_LOCKED
            else -> null
        }

    private fun readDriverDoorOpen(): Boolean? =
        readAny(1001 to DRIVER_DOOR_OPEN_STATE)?.let { it == 1 }

    private fun readTurnSignalFlash(): Int? =
        readAny(1004 to 950009900) ?: readTurnSignalFlashBySdk()

    private fun readPowerState(): Int? =
        readAny(
            1023 to 315621408,
            1001 to 315621418,
        ) ?: readPowerStateBySdk()

    private fun readSentryWarning(): SentryWarningReading {
        val candidates = arrayOf(
            1049 to 1128267797,
            1049 to 1128267829,
            1049 to 861929534,
            1049 to 861929530,
        )
        candidates.forEach { (dev, fid) ->
            val (status, value) = autoserviceTransact(svc, autoIface, 5, dev, fid, 0, writeValue = false)
            if (status == 0 && value in 0..SENTRY_WARN_ALARM) {
                return SentryWarningReading(value, "fid:$dev/$fid")
            }
        }
        val setting = shExec("settings get global sentrymode_warning_level").stdout
            .trim()
            .toIntOrNull()
            ?.takeIf { it in 0..SENTRY_WARN_ALARM }
        return SentryWarningReading(
            level = setting,
            source = if (setting != null) "global:sentrymode_warning_level" else "unavailable",
        )
    }

    private fun isSentryGuarding(): Boolean {
        val enabled = shExec("settings get global sentrymode_enabled").stdout.trim()
        val state = shExec("getprop sys.byd.sentrymode_state").stdout.trim()
        return enabled == "1" || state.equals("enabled", ignoreCase = true)
    }

    private fun consumeSentryAlarmTimestamp(value: String, allowTrigger: Boolean): Boolean {
        if (value.isBlank() || value == "0" || value.equals("null", ignoreCase = true)) return false
        val cursor = File(SENTRY_PROP_CURSOR_PATH)
        val previous = runCatching { cursor.takeIf { it.isFile }?.readText()?.trim().orEmpty() }.getOrDefault("")
        if (previous.isBlank()) {
            runCatching { cursor.writeText(value) }
            if (allowTrigger && isRecentSentryAlarmTimestamp(value)) {
                helperLog(TAG, "sentry prop first fresh=$value")
                return true
            }
            helperLog(TAG, "sentry prop baseline=$value allowTrigger=$allowTrigger")
            return false
        }
        if (value == previous) return false
        runCatching { cursor.writeText(value) }
        if (!allowTrigger) {
            helperLog(TAG, "sentry prop baseline update=$value")
            return false
        }
        helperLog(TAG, "sentry prop changed previous=$previous current=$value")
        return true
    }

    private fun isRecentSentryAlarmTimestamp(value: String): Boolean {
        val raw = value.toLongOrNull() ?: return true
        val seconds = if (raw > 10_000_000_000L) raw / 1_000L else raw
        val nowSeconds = System.currentTimeMillis() / 1_000L
        return abs(nowSeconds - seconds) <= SENTRY_PROP_FIRST_FRESH_SECONDS
    }

    private fun readFreshSentryLogMarker(allowTrigger: Boolean): String {
        val out = shExec("logcat -d -v threadtime -t 300 -s SENTRYMODE:D").stdout
        val marker = out.lineSequence()
            .lastOrNull(::isHighRiskSentryLogLine)
            .orEmpty()
        if (marker.isBlank()) return ""

        val cursor = File(SENTRY_LOG_CURSOR_PATH)
        val previous = runCatching { cursor.takeIf { it.isFile }?.readText()?.trim().orEmpty() }.getOrDefault("")
        if (previous.isBlank()) {
            runCatching { cursor.writeText(marker) }
            helperLog(TAG, "sentry logcat skip backlog cursor=${marker.take(80)}")
            return ""
        }
        if (marker == previous) return ""
        runCatching { cursor.writeText(marker) }
        if (!allowTrigger) {
            helperLog(TAG, "sentry logcat baseline update=${marker.take(80)}")
            return ""
        }
        helperLog(TAG, "sentry logcat high marker=${marker.take(120)}")
        return marker
    }

    private fun isHighRiskSentryLogLine(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        return lower.contains("lockvideohandler") &&
            lower.contains("msg.what = 3") &&
            !lower.contains("responseinform warn:")
    }

    private fun readTurnSignalFlashBySdk(): Int? =
        invokeBydDeviceInt(
            classNames = listOf("android.hardware.bydauto.light.BYDAutoLightDevice"),
            methodName = "getTurnLightFlashState",
        )

    private fun readDoorLockBySdk(): Int? =
        invokeBydDeviceInt(
            classNames = listOf(
                "android.hardware.bydauto.bodywork.BYDAutoDoorLockDevice",
                "android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice",
            ),
            methodName = "getDoorLockStatus",
            intArg = 1,
        ) ?: invokeBydDeviceInt(
            classNames = listOf(
                "android.hardware.bydauto.ota.BYDAutoOtaDevice",
                "android.hardware.bydauto.bodywork.BYDAutoOtaDevice",
            ),
            methodName = "getLFDoorLockState",
        )

    private fun readPowerStateBySdk(): Int? =
        invokeBydDeviceInt(
            classNames = listOf("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"),
            methodName = "getPowerLevel",
        )

    private fun invokeBydDeviceInt(classNames: List<String>, methodName: String, intArg: Int? = null): Int? {
        val ctx = systemContext ?: return null
        classNames.forEach { className ->
            val value = runCatching {
                val clazz = Class.forName(className)
                val out = StringBuilder()
                val instance = createBydBodyworkInstance(clazz, ctx, out) ?: return@runCatching null
                val method = instance.javaClass.methods.firstOrNull {
                    it.name == methodName &&
                        ((intArg == null && it.parameterTypes.isEmpty()) ||
                            (intArg != null && it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE))
                } ?: return@runCatching null
                method.isAccessible = true
                val raw = if (intArg == null) method.invoke(instance) else method.invoke(instance, intArg)
                (raw as? Number)?.toInt() ?: raw?.toString()?.toIntOrNull()
            }.getOrNull()
            if (value != null && isUsefulVehicleValue(value)) return value
        }
        return null
    }

    private fun setCarLockSoundPlayState(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        for (name in BYD_AUDIO_MANAGER_CLASSES) {
            val ok = runCatching {
                val cls = Class.forName(name)
                val target = runCatching { cls.getDeclaredConstructor().newInstance() }.getOrNull()
                val method = cls.methods.firstOrNull {
                    it.name == "setCarLockSoundPlayState" && it.parameterTypes.size == 1
                } ?: return@runCatching false
                method.isAccessible = true
                method.invoke(target, value)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun copyEventConfig(json: JSONObject?, destPath: String, pcmPath: String, key: String): EventConfig {
        if (json == null || !json.optBoolean("enabled", false)) return EventConfig(enabled = false, path = "")
        val sourcePath = json.optString("path").trim()
        val source = File(sourcePath)
        if (!source.isFile || source.length() <= 0L) {
            helperLog(TAG, "sync $key missing source=$sourcePath")
            return EventConfig(enabled = false, path = "")
        }
        val dest = File(destPath)
        runCatching {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            helperLog(TAG, "sync $key copy failed: ${throwableSummary(it)}")
            return EventConfig(enabled = false, path = "")
        }
        val enabled = dest.isFile && dest.length() > 0L
        val pcm = if (enabled) cachePcm(dest, pcmPath, key) else PcmConfig()
        return EventConfig(enabled = enabled, path = dest.absolutePath, pcm = pcm)
    }

    private fun cachePcm(source: File, pcmPath: String, key: String): PcmConfig {
        val pcmFile = File(pcmPath)
        runCatching { pcmFile.delete() }
        val cached = decodeToPcm16(source, pcmFile, key)
        if (cached != null && cached.usable()) {
            helperLog(TAG, "pcm cache ready key=$key path=${pcmFile.name} bytes=${cached.bytes} rate=${cached.sampleRate} channels=${cached.channelCount}")
            return cached
        }
        runCatching { pcmFile.delete() }
        helperLog(TAG, "pcm cache unavailable key=$key fallback=MediaPlayer")
        return PcmConfig()
    }

    private fun decodeToPcm16(source: File, output: File, key: String): PcmConfig? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return runCatching {
            val mediaExtractor = MediaExtractor()
            extractor = mediaExtractor
            mediaExtractor.setDataSource(source.absolutePath)
            var selectedTrack = -1
            var selectedFormat: MediaFormat? = null
            for (index in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    selectedTrack = index
                    selectedFormat = format
                    break
                }
            }
            val inputFormat = selectedFormat ?: return@runCatching null
            if (selectedTrack < 0) return@runCatching null
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
            mediaExtractor.selectTrack(selectedTrack)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputRate = mediaFormatInt(inputFormat, MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var outputChannels = mediaFormatInt(inputFormat, MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var outputEncoding = mediaFormatInt(inputFormat, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            var totalBytes = 0L
            val startedAt = System.currentTimeMillis()
            output.parentFile?.mkdirs()
            output.outputStream().use { out ->
                while (!outputDone) {
                    if (System.currentTimeMillis() - startedAt > PCM_DECODE_TIMEOUT_MS) {
                        throw IllegalStateException("pcm decode timeout key=$key")
                    }
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(PCM_CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                            if (inputBuffer == null) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                inputBuffer.clear()
                                val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(inputIndex, 0, sampleSize, mediaExtractor.sampleTime, 0)
                                    mediaExtractor.advance()
                                }
                            }
                        }
                    }

                    when (val outputIndex = decoder.dequeueOutputBuffer(info, PCM_CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            outputRate = mediaFormatInt(format, MediaFormat.KEY_SAMPLE_RATE, outputRate)
                            outputChannels = mediaFormatInt(format, MediaFormat.KEY_CHANNEL_COUNT, outputChannels).coerceAtLeast(1)
                            outputEncoding = mediaFormatInt(format, MediaFormat.KEY_PCM_ENCODING, outputEncoding)
                            helperLog(TAG, "pcm format key=$key rate=$outputRate channels=$outputChannels encoding=$outputEncoding")
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (inputDone) sleepQuietly(2L)
                        }
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                val chunk = ByteArray(info.size)
                                outputBuffer.get(chunk)
                                totalBytes += writePcm16Chunk(
                                    out = out,
                                    chunk = chunk,
                                    encoding = outputEncoding,
                                    sourceChannels = outputChannels,
                                    outputChannels = outputChannels.coerceIn(1, 2),
                                )
                                if (totalBytes > PCM_MAX_CACHE_BYTES) {
                                    throw IllegalStateException("pcm cache too large key=$key bytes=$totalBytes")
                                }
                            }
                            outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            if (totalBytes <= 0L) {
                null
            } else {
                PcmConfig(
                    path = output.absolutePath,
                    sampleRate = outputRate,
                    channelCount = outputChannels.coerceIn(1, 2),
                    bytes = totalBytes,
                )
            }
        }.getOrElse {
            helperLog(TAG, "pcm decode failed key=$key source=${source.name}: ${throwableSummary(it)}")
            runCatching { output.delete() }
            null
        }.also {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun mediaFormatInt(format: MediaFormat, key: String, fallback: Int): Int =
        if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrDefault(fallback) else fallback

    private fun writePcm16Chunk(
        out: OutputStream,
        chunk: ByteArray,
        encoding: Int,
        sourceChannels: Int,
        outputChannels: Int,
    ): Long = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> writePcm16Bytes(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_FLOAT -> writePcmFloatAs16(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_8BIT -> writePcm8As16(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> writePcm24PackedAs16(out, chunk, sourceChannels, outputChannels)
        AudioFormat.ENCODING_PCM_32BIT -> writePcm32As16(out, chunk, sourceChannels, outputChannels)
        else -> 0L
    }

    private fun writePcm16Bytes(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val sourceFrameBytes = srcChannels * PCM_BYTES_PER_SAMPLE
        val outputFrameBytes = dstChannels * PCM_BYTES_PER_SAMPLE
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        if (srcChannels == dstChannels) {
            val aligned = chunk.size - (chunk.size % sourceFrameBytes)
            if (aligned <= 0) return 0L
            out.write(chunk, 0, aligned)
            return aligned.toLong()
        }
        val frame = ByteArray(outputFrameBytes)
        var pos = 0
        var written = 0L
        while (pos + sourceFrameBytes <= chunk.size) {
            System.arraycopy(chunk, pos, frame, 0, outputFrameBytes.coerceAtMost(sourceFrameBytes))
            out.write(frame, 0, outputFrameBytes)
            written += outputFrameBytes
            pos += sourceFrameBytes
        }
        return written
    }

    private fun writePcm24PackedAs16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val bytesPerSourceSample = 3
        val sourceFrameBytes = srcChannels * bytesPerSourceSample
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var pos = 0
        var written = 0L
        while (pos + sourceFrameBytes <= chunk.size) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                val samplePos = pos + channel * bytesPerSourceSample
                val sample24 =
                    (chunk[samplePos].toInt() and 0xff) or
                        ((chunk[samplePos + 1].toInt() and 0xff) shl 8) or
                        ((chunk[samplePos + 2].toInt() and 0xff) shl 16)
                val signed24 = if ((sample24 and 0x800000) != 0) sample24 or -0x1000000 else sample24
                if (channel < dstChannels) {
                    writeInt16ToFrame(frame, channel, signed24 shr 8)
                }
            }
            out.write(frame)
            written += frame.size
            pos += sourceFrameBytes
        }
        return written
    }

    private fun writePcm32As16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val bytesPerSourceSample = 4
        val sourceFrameBytes = srcChannels * bytesPerSourceSample
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var written = 0L
        while (bb.remaining() >= sourceFrameBytes) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                val sample32 = bb.int
                if (channel < dstChannels) {
                    writeInt16ToFrame(frame, channel, sample32 shr 16)
                }
            }
            out.write(frame)
            written += frame.size
        }
        return written
    }

    private fun writePcmFloatAs16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        val sourceFrameBytes = srcChannels * 4
        if (sourceFrameBytes <= 0 || chunk.size < sourceFrameBytes) return 0L
        val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var written = 0L
        while (bb.remaining() >= sourceFrameBytes) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                val sample = bb.float
                if (channel < dstChannels) {
                    val intSample = (sample.coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    writeInt16ToFrame(frame, channel, intSample)
                }
            }
            out.write(frame)
            written += frame.size
        }
        return written
    }

    private fun writePcm8As16(out: OutputStream, chunk: ByteArray, sourceChannels: Int, outputChannels: Int): Long {
        val srcChannels = sourceChannels.coerceAtLeast(1)
        val dstChannels = outputChannels.coerceIn(1, 2)
        if (chunk.size < srcChannels) return 0L
        val frame = ByteArray(dstChannels * PCM_BYTES_PER_SAMPLE)
        var pos = 0
        var written = 0L
        while (pos + srcChannels <= chunk.size) {
            frame.fill(0)
            for (channel in 0 until srcChannels) {
                if (channel < dstChannels) {
                    val centered = (chunk[pos + channel].toInt() and 0xff) - 128
                    val intSample = (centered shl 8).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    writeInt16ToFrame(frame, channel, intSample)
                }
            }
            out.write(frame)
            written += frame.size
            pos += srcChannels
        }
        return written
    }

    private fun writeInt16ToFrame(frame: ByteArray, channel: Int, sample: Int) {
        val intSample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val dst = channel * PCM_BYTES_PER_SAMPLE
        if (dst + 1 >= frame.size) return
        frame[dst] = (intSample and 0xff).toByte()
        frame[dst + 1] = ((intSample ushr 8) and 0xff).toByte()
    }

    private data class RuntimeConfig(
        val channel: ExteriorChannel = ExteriorChannel.Channel1,
        val unlock: EventConfig = EventConfig(),
        val lock: EventConfig = EventConfig(),
        val sentry: EventConfig = EventConfig(),
    ) {
        fun anyEnabled(): Boolean = unlock.enabled || lock.enabled || sentry.enabled

        fun eventConfig(event: LockEvent): EventConfig =
            when (event) {
                LockEvent.Unlock -> unlock
                LockEvent.Lock -> lock
                LockEvent.Sentry -> sentry
            }

        fun summary(): String =
            "channel=$channel playerVolume=1.0 playerGain=$LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB pcmGain=$LOCK_SOUND_PCM_ENHANCER_GAIN_MB " +
                "unlock=${unlock.enabled}/pcm=${unlock.pcm.usable()} " +
                "lock=${lock.enabled}/pcm=${lock.pcm.usable()} " +
                "sentry=${sentry.enabled}/pcm=${sentry.pcm.usable()}"
    }

    private data class SentryWarningReading(
        val level: Int?,
        val source: String,
    )

    private data class EventConfig(
        val enabled: Boolean = false,
        val path: String = "",
        val pcm: PcmConfig = PcmConfig(),
    )

    private data class PcmConfig(
        val path: String = "",
        val sampleRate: Int = 0,
        val channelCount: Int = 0,
        val bytes: Long = 0L,
    ) {
        fun usable(): Boolean =
            path.isNotBlank() && sampleRate in 8_000..192_000 && channelCount in 1..2 && bytes > 0L
    }

    private data class PersistedLockState(
        val state: Int,
        val updatedAtMs: Long,
    )

    private enum class LockEvent(val kind: Int, val key: String, val looping: Boolean = false) {
        Unlock(0, KEY_UNLOCK),
        Lock(1, KEY_LOCK),
        Sentry(2, KEY_SENTRY);

        companion object {
            fun fromKind(kind: Int): LockEvent? = entries.firstOrNull { it.kind == kind }
        }
    }

    private companion object {
        private const val TAG = "LockSound"
        private const val KEY_UNLOCK = "unlock"
        private const val KEY_LOCK = "lock"
        private const val KEY_SENTRY = "sentry"
        private const val TURN_FLASH_CONFIRM = 9
        private const val DOOR_UNLOCKED = 1
        private const val DOOR_LOCKED = 2
        private const val LOCK_CONFIRM_MS = 400L
        private const val LOCK_PENDING_EXPIRE_MS = 3_000L
        private const val EVENT_SUPPRESS_MS = 5_000L
        private const val SENTRY_LOG_SCAN_MS = 1_000L
        private const val SENTRY_PROP_FIRST_FRESH_SECONDS = 120L
        private const val LOCK_FLASH_WINDOW_MS = 5_000L
        private const val ROUTE_RELEASE_DRAIN_MS = 180L
        private const val COLD_UNLOCK_MAX_PREV_AGE_MS = 72L * 60L * 60L * 1000L
        private const val LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB = 1_500
        private const val LOCK_SOUND_PCM_ENHANCER_GAIN_MB = 2_800
        private const val LOCK_SOUND_ROUTE_VOLUME_PERCENT = 100
        private const val LOCK_SOUND_CONFIG_PATH = "/data/local/tmp/wjx_lock_sound.json"
        private const val LOCK_STATE_CACHE_PATH = "/data/local/tmp/wjx_lock_state.txt"
        private const val LOCK_SOUND_UNLOCK_PATH = "/data/local/tmp/wjx_unlock.wav"
        private const val LOCK_SOUND_LOCK_PATH = "/data/local/tmp/wjx_lock.wav"
        private const val LOCK_SOUND_SENTRY_PATH = "/data/local/tmp/wjx_sentry.wav"
        private const val LOCK_SOUND_UNLOCK_PCM_PATH = "/data/local/tmp/wjx_unlock.pcm"
        private const val LOCK_SOUND_LOCK_PCM_PATH = "/data/local/tmp/wjx_lock.pcm"
        private const val LOCK_SOUND_SENTRY_PCM_PATH = "/data/local/tmp/wjx_sentry.pcm"
        private const val LOCK_SOUND_WATCHDOG_PATH = "/data/local/tmp/wjx_lock_watch.sh"
        private const val LOCK_SOUND_WATCHDOG_PID_PATH = "/data/local/tmp/wjx_lock_watch.pid"
        private const val LOCK_SOUND_WATCHDOG_VERSION_PATH = "/data/local/tmp/wjx_lock_watch.version"
        private const val LOCK_SOUND_WATCHDOG_DISABLED_PATH = "/data/local/tmp/wjx_lock_watch.disabled"
        private const val LOCK_SOUND_WATCHDOG_VERSION = 2
        private const val SENTRY_LOG_CURSOR_PATH = "/data/local/tmp/wjx_sentry_msg3_cursor.txt"
        private const val SENTRY_PROP_CURSOR_PATH = "/data/local/tmp/wjx_sentry_alarm_ts_cursor.txt"
        private const val PCM_BYTES_PER_SAMPLE = 2
        private const val PCM_CODEC_TIMEOUT_US = 10_000L
        private const val PCM_DECODE_TIMEOUT_MS = 8_000L
        private const val PCM_MAX_CACHE_BYTES = 24L * 1024L * 1024L
        private const val PCM_TRACK_BUFFER_BYTES = 64 * 1024
        private const val PCM_WRITE_BUFFER_BYTES = 16 * 1024
        private const val PCM_LEAD_SILENCE_MS = 420L
        private const val PCM_STATIC_MAX_BYTES = 8L * 1024L * 1024L

        private const val DEV_AUDIO = 1000
        private const val DEV_ADAS = 3
        private const val DEV_AUDIO_COMPAT = 1002
        private const val DEV_ADAS_COMPAT = 1000
        private const val EXTERIOR_SWITCH_SET = 891293750
        private const val AVAS_ROUTE_SET = 891293760
        private const val AUDIO_KARAOKE_MODE_SET = 372244513
        private const val SWITCH_ON = 1339031619
        private const val SWITCH_OFF = 3
        private const val AVAS_ROUTE_ON = 2
        private const val AVAS_ROUTE_OFF = 1
        private const val KARAOKE_MODE_EXTERIOR = 2
        private const val LOCK_CAR_SOUND_PLAYBACK_STATUS_SET = 13
        private const val LOCK_CAR_SOUND_PLAYBACK_ON = -1442839775
        private const val LOCK_CAR_SOUND_PLAYBACK_OFF = 1
        private const val ADAS_UE_BROADCAST_POSITION_SET = -1442839934
        private const val ADAS_BROADCAST_POSITION_ON = 1
        private const val ADAS_BROADCAST_POSITION_OFF = 0
        private const val LOCK_CAR_SOUND_PLAYBACK_STATUS_SET_COMPAT = -1442839775
        private const val ADAS_UE_BROADCAST_POSITION_SET_COMPAT = -1796699511
        private const val DRIVER_DOOR_OPEN_STATE = 692060168
        private val BYD_AUDIO_MANAGER_CLASSES = listOf(
            "android.media.BydAudioManager",
            "com.byd.audio.BYDAudioManager",
            "android.media.byd.BYDAudioManager",
            "com.byd.car.audio.BydAudioManager",
            "com.byd.mediacenter.audio.BydAudioManager",
            "com.byd.ivi.audio.BydAudioManager",
        )

        private fun parseChannel(value: String): ExteriorChannel =
            runCatching { ExteriorChannel.valueOf(value) }.getOrDefault(ExteriorChannel.Channel1)

        private fun readRuntimeConfig(): RuntimeConfig {
            val file = File(LOCK_SOUND_CONFIG_PATH)
            if (!file.isFile) return RuntimeConfig()
            return runCatching {
                val json = JSONObject(file.readText())
                RuntimeConfig(
                    channel = parseChannel(json.optString("channel", ExteriorChannel.Channel1.name)),
                    unlock = json.readEvent(KEY_UNLOCK),
                    lock = json.readEvent(KEY_LOCK),
                    sentry = json.readEvent(KEY_SENTRY),
                )
            }.getOrDefault(RuntimeConfig())
        }

        private fun writeRuntimeConfig(config: RuntimeConfig) {
            val json = JSONObject()
                .put("version", 1)
                .put("channel", config.channel.name)
                .putEvent(KEY_UNLOCK, config.unlock)
                .putEvent(KEY_LOCK, config.lock)
                .putEvent(KEY_SENTRY, config.sentry)
            File(LOCK_SOUND_CONFIG_PATH).writeText(json.toString())
        }

        private fun readPersistedLockState(): PersistedLockState? {
            val file = File(LOCK_STATE_CACHE_PATH)
            if (!file.isFile) return null
            return runCatching {
                val parts = file.readText().trim().split(",")
                val state = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
                val updatedAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (state == DOOR_LOCKED || state == DOOR_UNLOCKED) {
                    PersistedLockState(state, updatedAt)
                } else {
                    null
                }
            }.getOrNull()
        }

        private fun writePersistedLockState(state: Int, reason: String) {
            if (state != DOOR_LOCKED && state != DOOR_UNLOCKED) return
            runCatching {
                File(LOCK_STATE_CACHE_PATH).writeText("$state,${System.currentTimeMillis()}")
            }.onFailure {
                helperLog(TAG, "lock state cache write failed reason=$reason ${throwableSummary(it)}")
            }
        }

        private fun JSONObject.readEvent(key: String): EventConfig {
            val obj = optJSONObject(key) ?: return EventConfig()
            val path = obj.optString("path")
            val pcm = obj.optJSONObject("pcm")?.let {
                PcmConfig(
                    path = it.optString("path"),
                    sampleRate = it.optInt("sampleRate", 0),
                    channelCount = it.optInt("channelCount", 0),
                    bytes = it.optLong("bytes", 0L),
                )
            } ?: PcmConfig()
            return EventConfig(
                enabled = obj.optBoolean("enabled", false) && File(path).isFile,
                path = path,
                pcm = pcm.takeIf { it.usable() && File(it.path).isFile } ?: PcmConfig(),
            )
        }

        private fun JSONObject.putEvent(key: String, config: EventConfig): JSONObject =
            put(
                key,
                JSONObject()
                    .put("enabled", config.enabled)
                    .put("path", config.path)
                    .put(
                        "pcm",
                        JSONObject()
                            .put("path", config.pcm.path)
                            .put("sampleRate", config.pcm.sampleRate)
                            .put("channelCount", config.pcm.channelCount)
                            .put("bytes", config.pcm.bytes),
                    ),
            )

        private fun isUsefulVehicleValue(value: Int): Boolean =
            value != 0x0000FFFF && value != Int.MAX_VALUE && value != Int.MIN_VALUE

        private fun sleepQuietly(ms: Long) {
            runCatching { Thread.sleep(ms) }
        }
    }
}

private fun sentryShellRead(op: String, arg: String): CmdResult {
    return when (op) {
        "get_global" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("settings get global \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "get_secure" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("settings get secure \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "get_system" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("settings get system \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "get_prop" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("getprop \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "put_global" -> parseSentryAssignment(arg)?.takeIf { (key, value) ->
            key in SENTRY_WRITABLE_GLOBALS && value in SENTRY_SETTING_VALUES
        }?.let { (key, value) ->
            shExec("settings put global \"\$1\" \"\$2\"", key, value)
        } ?: CmdResult(-2, "")
        "set_prop" -> parseSentryAssignment(arg)?.takeIf { (key, value) ->
            key == SENTRY_STATE_PROP && value in SENTRY_PROP_VALUES
        }?.let { (key, value) ->
            shExec("setprop \"\$1\" \"\$2\"", key, value)
        } ?: CmdResult(-2, "")
        else -> CmdResult(-3, "")
    }
}

private fun isSafeSentryKey(value: String): Boolean =
    value.matches(Regex("""[A-Za-z0-9_.:-]{1,96}"""))

private fun parseSentryAssignment(value: String): Pair<String, String>? {
    val separator = value.indexOf('=')
    if (separator !in 1 until value.lastIndex) return null
    val key = value.substring(0, separator)
    val setting = value.substring(separator + 1)
    if (!isSafeSentryKey(key) || !setting.matches(Regex("""[A-Za-z0-9_.:-]{1,32}"""))) return null
    return key to setting
}

private val SENTRY_WRITABLE_GLOBALS = setOf(
    "sentrymode_enabled_switch",
    "sentrymode_enabled",
)
private val SENTRY_SETTING_VALUES = setOf("0", "1")
private const val SENTRY_STATE_PROP = "sys.byd.sentrymode_state"
private val SENTRY_PROP_VALUES = setOf("enable", "disable")

private fun execShellRead(command: String): DevShellResult {
    val trimmed = command.trim()
    if (trimmed.isBlank()) return DevShellResult(-2, -1, "命令不能为空")
    if (trimmed.length > EXEC_SHELL_MAX_CHARS) {
        return DevShellResult(-2, -1, "命令过长（上限 $EXEC_SHELL_MAX_CHARS 字符）")
    }
    if (EXEC_SHELL_BLOCKLIST.any { it.containsMatchIn(trimmed) }) {
        return DevShellResult(-2, -1, "已拦截：疑似破坏性命令")
    }
    helperLog("ExecShell", "run ${trimmed.lineSequence().firstOrNull().orEmpty().take(180)}")
    return runCatching {
        val process = ProcessBuilder("sh", "-c", trimmed).start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = Thread({ process.inputStream.use { it.copyTo(stdout) } }, "WJX-ExecShell-stdout")
        val stderrThread = Thread({ process.errorStream.use { it.copyTo(stderr) } }, "WJX-ExecShell-stderr")
        stdoutThread.isDaemon = true
        stderrThread.isDaemon = true
        stdoutThread.start()
        stderrThread.start()
        val completed = process.waitFor(EXEC_SHELL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutThread.interrupt()
            stderrThread.interrupt()
            return DevShellResult(0, SHELL_EXEC_TIMEOUT_CODE, "(超时 ${EXEC_SHELL_TIMEOUT_MS / 1000}s，进程已终止)")
        }
        stdoutThread.join(500L)
        stderrThread.join(500L)
        val out = stdout.toString(Charsets.UTF_8.name()).trim()
        val err = stderr.toString(Charsets.UTF_8.name()).trim()
        val combined = buildString {
            if (out.isNotBlank()) append(out)
            if (err.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("[stderr] ")
                append(err)
            }
            if (isEmpty()) append("(无输出)")
        }.let {
            if (it.length > EXEC_SHELL_OUTPUT_MAX_CHARS) {
                it.take(EXEC_SHELL_OUTPUT_MAX_CHARS) + "\n...(输出已截断至 $EXEC_SHELL_OUTPUT_MAX_CHARS 字符)"
            } else {
                it
            }
        }
        DevShellResult(0, process.exitValue(), combined)
    }.getOrElse {
        DevShellResult(-1, -1, "error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
    }
}

private fun helperLog(tag: String, message: String) {
    val ts = synchronized(HELPER_LOG_DATE) { HELPER_LOG_DATE.format(Date()) }
    val line = "$ts I/$tag $message\n"
    runCatching {
        val dir = File("/sdcard/WJX")
        dir.mkdirs()
        val log = File(dir, "wjx.log")
        trimLogIfNeeded(log)
        log.appendText(line)
    }
}

private fun trimLogIfNeeded(file: File) {
    if (file.exists() && file.length() > HELPER_LOG_MAX_BYTES) {
        val tail = file.readLines().takeLast(HELPER_LOG_KEEP_LINES).joinToString("\n", postfix = "\n")
        file.writeText(tail)
    }
}

/**
 * Performs a single autoservice Binder transact and returns (status, retInt).
 *
 * [tx] is the transact code (5=getInt, 6=setInt, 7=getDouble raw int, 9=getBuffer).
 * [writeValue] controls whether [value] is written into the parcel (true for setInt/tx=6 only).
 *
 * Semantics match the proven logic from the TCP daemon:
 *   avail >= 4 → status = reply.readInt() else -999
 *   avail >= 8 → retInt = reply.readInt() else 0
 */
private fun autoserviceTransact(
    svc: IBinder,
    autoIface: String,
    tx: Int,
    dev: Int,
    fid: Int,
    value: Int,
    writeValue: Boolean
): Pair<Int, Int> {
    val data2 = Parcel.obtain()
    val reply2 = Parcel.obtain()
    return try {
        data2.writeInterfaceToken(autoIface)
        data2.writeInt(dev)
        data2.writeInt(fid)
        if (writeValue) data2.writeInt(value)
        svc.transact(tx, data2, reply2, 0)
        val avail = reply2.dataAvail()
        val status = if (avail >= 4) reply2.readInt() else -999
        val retInt = if (avail >= 8) reply2.readInt() else 0
        status to retInt
    } finally {
        data2.recycle()
        reply2.recycle()
    }
}

/** Resolves IActivityTaskManager via ActivityTaskManager.getService() (hidden API, ok under app_process). */
private fun activityTaskManager(): Any =
    Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        ?: throw IllegalStateException("ActivityTaskManager.getService() returned null")

/** Walks the superclass chain for a declared field [name] (mirrors CarControlImpl.findField). */
private fun fieldByName(target: Any, name: String): java.lang.reflect.Field? {
    var cls: Class<*>? = target.javaClass
    while (cls != null) {
        try { return cls.getDeclaredField(name) } catch (e: NoSuchFieldException) { cls = cls.superclass }
    }
    return null
}

/**
 * Finds the running task id of [packageName]. Reconstructed from CarControlImpl.getTopActivityPackage:
 * iAtm.getTasks(maxNum, false, false) -> List<RunningTaskInfo>; match topActivity/baseActivity package;
 * read int field "taskId" (fallback "id"). Returns -1 when not found.
 * NOTE: field names validated on-car in Phase 2.
 */
private fun findTaskId(packageName: String): Int {
    val iAtm = activityTaskManager()
    val getTasks = iAtm.javaClass.getMethod(
        "getTasks", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
    )
    val tasks = getTasks.invoke(iAtm, 100, false, false) as? List<*> ?: return -1
    for (task in tasks) {
        if (task == null) continue
        val pkg = listOf("topActivity", "baseActivity").firstNotNullOfOrNull { fieldName ->
            fieldByName(task, fieldName)?.let { f ->
                f.isAccessible = true
                (f.get(task) as? android.content.ComponentName)?.packageName
            }
        }
        if (pkg == packageName) {
            val idField = fieldByName(task, "taskId") ?: fieldByName(task, "id") ?: continue
            idField.isAccessible = true
            return idField.getInt(task)
        }
    }
    return -1
}

/** moveRootTaskToDisplay(int,int) preferred, fallback moveTaskToDisplay(int,int). */
private fun moveTaskToDisplayReflect(taskId: Int, displayId: Int) {
    val iAtm = activityTaskManager()
    val m = iAtm.javaClass.methods.firstOrNull { it.name == "moveRootTaskToDisplay" && it.parameterTypes.size == 2 }
        ?: iAtm.javaClass.methods.firstOrNull { it.name == "moveTaskToDisplay" && it.parameterTypes.size == 2 }
        ?: throw NoSuchMethodException("moveTaskToDisplay")
    m.invoke(iAtm, taskId, displayId)
}

private fun setTaskWindowingModeReflect(taskId: Int, windowingMode: Int) {
    val iAtm = activityTaskManager()
    iAtm.javaClass.getMethod("setTaskWindowingMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        .invoke(iAtm, taskId, windowingMode, true)
}

private fun setTaskBoundsReflect(taskId: Int, left: Int, top: Int, right: Int, bottom: Int) {
    val iAtm = activityTaskManager()
    val resizeTask = iAtm.javaClass.getMethod("resizeTask", Int::class.javaPrimitiveType, Rect::class.java, Int::class.javaPrimitiveType)
    val rect = if (left == 0 && top == 0 && right == 0 && bottom == 0) null else Rect(left, top, right, bottom)
    resizeTask.invoke(iAtm, taskId, rect, 1)
}

private fun setFocusedTaskReflect(taskId: Int) {
    val iAtm = activityTaskManager()
    iAtm.javaClass.getMethod("setFocusedRootTask", Int::class.javaPrimitiveType).invoke(iAtm, taskId)
}

/**
 * Acquires a system Context via ActivityThread, exactly as OpenBYD's EntryPoint does.
 * Returns null on failure — read paths that need it degrade to an error status rather
 * than crashing the daemon (autoservice read/write do NOT need a Context and keep working).
 *
 * Reflection (no HiddenApiBypass): the daemon runs under app_process, where hidden-API
 * enforcement is inactive.
 */
private fun acquireSystemContext(): Context? = try {
    val atCls = Class.forName("android.app.ActivityThread")
    val activityThread = atCls.getMethod("systemMain").invoke(null)
    atCls.getMethod("getSystemContext").invoke(activityThread) as? Context
} catch (e: Throwable) {
    System.err.println("WARN: systemContext unavailable: ${e.message}")
    null
}

/**
 * Shell-side VIN read. This mirrors Modi's key detail: use the system/shell process Context to
 * create BYDAutoBodyworkDevice, then read both getRealAutoVIN and getAutoVIN.
 */
private fun readVehicleVinFromBodywork(systemContext: Context?): String {
    val out = StringBuilder()
    out.append(HELPER_VIN_READER_MARKER).append('\n')
    out.append("systemContext=").append(systemContext?.javaClass?.name ?: "null").append('\n')
    val ctx = systemContext ?: return out.append("bodywork=skip:no_context").toString()
    val clazz = try {
        Class.forName(BYD_BODYWORK_DEVICE_CLASS)
    } catch (e: Throwable) {
        return out.append("bodyworkClass=missing:").append(throwableSummary(e)).toString()
    }
    out.append("bodyworkClass=").append(clazz.name).append('\n')
    val contexts = buildBodyworkContextCandidates(ctx)

    contexts.forEachIndexed { index, candidate ->
        out.append("context[").append(index).append("]=")
            .append(candidate.packageName).append('/').append(candidate.javaClass.name).append('\n')
        val instance = createBydBodyworkInstance(clazz, candidate, out)
        if (instance == null) {
            out.append("instance[").append(index).append("]=null").append('\n')
            return@forEachIndexed
        }
        out.append("instance[").append(index).append("]=").append(instance.javaClass.name).append('\n')
        BYD_VIN_METHODS.forEach { methodName ->
            val value = invokeBodyworkNoArg(instance, methodName)
            out.append(methodName).append('=').append(value.orEmpty()).append('\n')
        }
    }
    return out.toString().take(4000)
}

private fun buildBodyworkContextCandidates(systemContext: Context): List<Context> {
    val baseContexts = mutableListOf<Context>()
    baseContexts += systemContext
    BYD_CONTEXT_PACKAGES.forEach { pkg ->
        runCatching {
            systemContext.createPackageContext(
                pkg,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrNull()?.let { baseContexts += it }
    }
    runCatching {
        systemContext.createPackageContext("android", Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()?.let { baseContexts += it }
    runCatching {
        systemContext.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()?.let { baseContexts += it }

    return baseContexts
        .distinctBy { "${it.javaClass.name}:${it.packageName}" }
        .flatMap { listOf(it, PermissionBypassContext(it)) }
        .distinctBy { "${it.javaClass.name}:${it.packageName}:${it.baseContextKey()}" }
}

private fun Context.baseContextKey(): String =
    (this as? ContextWrapper)?.baseContext?.let { "${it.javaClass.name}:${it.packageName}" }.orEmpty()

private class PermissionBypassContext(base: Context) : ContextWrapper(base) {
    override fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun checkCallingPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED

    override fun enforceCallingOrSelfPermission(permission: String, message: String?) = Unit

    override fun enforceCallingPermission(permission: String, message: String?) = Unit

    override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = Unit
}

private fun createBydBodyworkInstance(clazz: Class<*>, context: Context, out: StringBuilder): Any? {
    val contextMethods = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
        .filter {
            it.name == "getInstance" &&
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].isAssignableFrom(context.javaClass)
        }
        .distinctBy { "${it.declaringClass.name}:${it.name}:${it.parameterTypes.joinToString(",") { type -> type.name }}" }
        .sortedBy { inheritanceDistance(context.javaClass, it.parameterTypes[0]) }
        .toList()
    contextMethods.forEach { contextMethod ->
        try {
            contextMethod.isAccessible = true
            val instance = contextMethod.invoke(null, context)
            if (instance != null) return instance
        } catch (e: Throwable) {
            out.append("getInstance(").append(context.javaClass.simpleName).append(")=error:")
                .append(throwableSummary(e)).append('\n')
        }
    }
    val noArgMethod = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence()).firstOrNull {
        it.name == "getInstance" &&
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
            it.parameterTypes.isEmpty()
    } ?: return null
    return try {
        noArgMethod.isAccessible = true
        noArgMethod.invoke(null)
    } catch (e: Throwable) {
        out.append("getInstance()=error:").append(throwableSummary(e)).append('\n')
        null
    }
}

private fun inheritanceDistance(child: Class<*>, parent: Class<*>): Int {
    var distance = 0
    var current: Class<*>? = child
    while (current != null) {
        if (current == parent) return distance
        distance++
        current = current.superclass
    }
    return Int.MAX_VALUE
}

private fun invokeBodyworkNoArg(instance: Any, methodName: String): String? {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        val method = cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
        if (method != null) {
            return try {
                method.isAccessible = true
                method.invoke(instance)?.toString()
            } catch (e: Throwable) {
                "ERR:${throwableSummary(e)}"
            }
        }
        cls = cls.superclass
    }
    return try {
        instance.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
            ?.invoke(instance)
            ?.toString()
    } catch (e: Throwable) {
        "ERR:${throwableSummary(e)}"
    }
}

private fun rawHasVin(raw: String): Boolean =
    VIN_TOKEN.findAll(raw).any { it.value.startsWith("L", ignoreCase = true) }

private fun throwableSummary(error: Throwable): String {
    val unwrapped = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
    return "${unwrapped.javaClass.simpleName}:${unwrapped.message.orEmpty()}".take(220)
}

/**
 * Creates a VirtualDisplay backed by [surface]. Uses a com.android.shell package Context so the
 * shell uid's privilege applies to the requested [flags] (incl. TRUSTED / SYSTEM_DECORATIONS).
 * Returns the new displayId (>0) on success, or -1 (releasing any invalid display). Mirrors
 * CarControlImpl.createVirtualDisplay.
 */
private fun createVirtualDisplay(
    ctx: Context,
    store: ConcurrentHashMap<Int, VirtualDisplay>,
    name: String, width: Int, height: Int, density: Int, surface: Surface, flags: Int,
): Int {
    val shellCtx = ctx.createPackageContext("com.android.shell", 0)
    val dm = shellCtx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val vd = dm.createVirtualDisplay(name, width, height, density, surface, flags) ?: return -1
    val id = vd.display?.displayId ?: -1
    if (id <= 0) { vd.release(); return -1 }
    store[id] = vd
    return id
}

/** Runs a shell command (shell uid) and returns combined stdout/stderr. Mirrors CarControlImpl.exec. */
private fun execShell(command: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    val out = process.inputStream.bufferedReader().use { it.readText().trim() }
    val err = process.errorStream.bufferedReader().use { it.readText().trim() }
    process.waitFor()
    return buildString {
        if (out.isNotBlank()) append(out)
        if (err.isNotBlank()) { if (isNotEmpty()) append(" | STDERR: "); append(err) }
    }.ifEmpty { "OK" }
}

private class CmdResult(val code: Int, val stdout: String)
private data class DevShellResult(val status: Int, val exitCode: Int, val output: String)

/**
 * Runs [script] under sh with [args] bound to positional params ($1, $2, …) so untrusted values
 * (e.g. an existing accessibility list read off the device) are passed as argv and NEVER re-parsed
 * by the shell — no injection, no quote-breakage. Returns ONLY stdout + exit code (stderr is sent
 * to /dev/null, so there is no second pipe to deadlock on and stdout can never be corrupted by an
 * stderr line — unlike [execShell], which merges them). Use this whenever success/output matters.
 */
private fun shExec(script: String, vararg args: String): CmdResult {
    return shExecTimed(SHELL_EXEC_TIMEOUT_MS, script, *args)
}

private fun shExecTimed(timeoutMs: Long, script: String, vararg args: String): CmdResult {
    val cmd = arrayListOf("sh", "-c", script, "sh")
    cmd.addAll(args)
    val process = ProcessBuilder(cmd)
        .redirectError(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
        .start()
    val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!completed) {
        process.destroyForcibly()
        process.waitFor(200L, TimeUnit.MILLISECONDS)
    }
    val out = process.inputStream.bufferedReader().use { it.readText().trim() }
    return CmdResult(if (completed) process.exitValue() else SHELL_EXEC_TIMEOUT_CODE, out)
}

/**
 * Enables our steering-wheel accessibility service so it starts filtering steering-wheel keys.
 * DiLink has no a11y settings UI, so the user cannot toggle it; we do it under shell uid via the
 * `settings` binary (the in-process Settings.Secure ContentResolver does not stick for an
 * app_process-spawned daemon).
 *
 * Force re-bind, mirroring OpenBYD AccessibilitySetupHelper.buildEnableCommands: write the list
 * WITHOUT our component, pause, then write it back WITH our component, then accessibility_enabled=1.
 * A plain append does NOT make the framework bind a service that is already listed but not running
 * (the common case after an APK reinstall/upgrade) — the remove+re-add is what triggers the bind.
 * Read-modify-write preserves other apps' services (our component is the only one we ever touch).
 * App-scoped, reversible Secure settings only; touches nothing on the vehicle (no autoservice/CAN).
 */
private fun enableAccessibilityService(): Boolean {
    val components = listOf(
        HelperBinderProtocol.STAR_ACCESSIBILITY_SERVICE_COMPONENT,
        HelperBinderProtocol.KEEPALIVE_ACCESSIBILITY_SERVICE_COMPONENT,
    )
    val targets = components.map { canonicalComponent(it) }.toSet()
    // Abort on a failed READ — never write a guessed/garbled list back, that would clobber others.
    val current = readSecure("enabled_accessibility_services") ?: return false
    // Strip EVERY spelling of our component. The short ("pkg/.Cls") and full ("pkg/pkg.Cls") forms
    // both canonicalise to the same ComponentName, so a literal-string filter would leave the other
    // form behind. The framework would then see no change to the enabled SET and never re-bind a
    // crashed service — the exact failure that kept star control dead after a reboot.
    val others = current.split(':').filter { it.isNotEmpty() && canonicalComponent(it) !in targets }
    // $1 = the list, passed as argv (not interpolated) — safe even if an existing entry is odd.
    if (shExec("settings put secure enabled_accessibility_services \"\$1\"", others.joinToString(":")).code != 0) return false
    Thread.sleep(200L)  // let the framework observe the removal before we re-add (OpenBYD uses 0.2s)
    if (shExec("settings put secure enabled_accessibility_services \"\$1\"", (others + components).joinToString(":")).code != 0) return false
    if (shExec("settings put secure accessibility_enabled 1").code != 0) return false
    // Verify read-back: our component is now listed AND accessibility is enabled.
    val after = (readSecure("enabled_accessibility_services") ?: return false).split(':').filter { it.isNotEmpty() }
    val afterSet = after.map { canonicalComponent(it) }.toSet()
    return targets.all { it in afterSet } && readSecure("accessibility_enabled") == "1"
}

/**
 * Expands a flattened component string to a canonical `pkg/fully.qualified.Class`, mirroring
 * ComponentName.unflattenFromString's leading-dot rule (`pkg/.A.B` -> `pkg/pkg.A.B`). Lets the short
 * and full spellings of one service compare equal. Returns the input unchanged when it has no '/'.
 */
internal fun canonicalComponent(flattened: String): String {
    val sep = flattened.indexOf('/')
    if (sep < 0) return flattened
    val pkg = flattened.substring(0, sep)
    val cls = flattened.substring(sep + 1)
    val fqcn = if (cls.startsWith(".")) pkg + cls else cls
    return "$pkg/$fqcn"
}

/**
 * Reads a secure setting via the `settings` binary: "" when unset (`settings get` prints "null"),
 * or null on a non-zero exit so callers can abort instead of acting on a bad read.
 */
private fun readSecure(key: String): String? {
    val r = shExec("settings get secure \"\$1\"", key)
    if (r.code != 0) return null
    return if (r.stdout == "null") "" else r.stdout
}

/**
 * Launches [packageName] via am/monkey strategies. Returns true if a launch command ran without an
 * obvious "Error". Mirrors CarControlImpl.launchApp (simplified to a boolean).
 */
private fun launchApp(packageName: String): Boolean {
    val resolve = execShell("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $packageName")
    val component = resolve.lineSequence().firstOrNull { it.contains("/") && !it.startsWith("No ") }?.trim()
    if (component != null) {
        val r = execShell("am start -n $component")
        if (!r.contains("Error")) return true
    }
    val r2 = execShell("am start -a android.intent.action.MAIN $packageName")
    if (!r2.contains("Error")) return true
    val r3 = execShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    return !r3.contains("Error") && !r3.contains("error")
}

/**
 * Launches [packageName] on [displayId] and pins it there with a short persistence loop
 * (move -> bounds -> focus, x2). Returns true once redirection ran. Mirrors CarControlImpl.launchAndForce.
 * Blocking (Thread.sleep) — runs on a binder threadpool thread; the app side uses a 15s timeout.
 */
private fun launchAndForce(packageName: String, displayId: Int, width: Int, height: Int): Boolean {
    var taskId = findTaskId(packageName)
    if (taskId <= 0) {
        launchApp(packageName)
        var attempt = 1
        while (attempt < 16) {
            taskId = findTaskId(packageName)
            if (taskId > 0) break
            Thread.sleep(500L)
            attempt++
        }
        Thread.sleep(1500L)
    }
    if (taskId <= 0) return false
    // Each redirect op is best-effort, mirroring CarControlImpl (every reflective call there returns
    // a status string and swallows its own exception). resizeTask in particular throws "not allowed"
    // on a fullscreen task — that must NOT abort the move/focus or bubble up as a launchAndForce
    // failure, otherwise the caller tears down the VirtualDisplay Navi was just moved onto before it
    // can render. The VD size (mini=640 / full=1280) already sets the geometry, so a resize failure
    // is harmless.
    repeat(2) {
        runCatching { moveTaskToDisplayReflect(taskId, displayId) }
        runCatching { setTaskBoundsReflect(taskId, 0, 0, width, height) }
        runCatching { setFocusedTaskReflect(taskId) }
        Thread.sleep(200L)
    }
    return true
}

private const val BYD_BODYWORK_DEVICE_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
private const val HELPER_VIN_READER_MARKER = "vinReader=v2"
private const val SHELL_EXEC_TIMEOUT_MS = 3000L
private const val SHELL_EXEC_TIMEOUT_CODE = -124
private const val EXEC_SHELL_TIMEOUT_MS = 600_000L
private const val EXEC_SHELL_MAX_CHARS = 4096
private const val EXEC_SHELL_OUTPUT_MAX_CHARS = 65_536
private const val HELPER_LOG_MAX_BYTES = 8 * 1024 * 1024L
private const val HELPER_LOG_KEEP_LINES = 8_000
private val HELPER_LOG_DATE = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
private val EXEC_SHELL_BLOCKLIST = listOf(
    Regex("""rm\s+-[^\n]*\s+/\s"""),
    Regex("""rm\s+-[^\n]*\s+/\*"""),
    Regex("""\bdd\b[^\n]*\bof=/dev/block"""),
    Regex("""\bmkfs\b"""),
)
private val BYD_CONTEXT_PACKAGES = listOf(
    "com.byd.launcher",
    "com.byd.car.server",
    "com.byd.clusterdebug",
    "com.byd.vehicleconfig",
    "com.xy.instrumentnavigation",
    "com.android.launcher3",
)
private val BYD_VIN_METHODS = listOf("getRealAutoVIN", "getAutoVIN")
private val VIN_TOKEN = Regex("""[A-HJ-NPR-Z0-9]{17}""", RegexOption.IGNORE_CASE)
