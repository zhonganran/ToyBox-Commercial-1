@file:JvmName("HelperDaemon")
package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.VirtualDisplay
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.view.Surface
import com.byd.wjx.audio.BydLockSoundPlaybackState
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

// Lock path on the device filesystem (writable by shell uid).
private const val LOCK_PATH = "/data/local/tmp/wjx_helper.lock"
internal const val SENTRY_IDLE_BASELINE_INTERVAL_MS = 30_000L
internal const val SENTRY_WARN_ALARM = 2
internal const val SENTRY_FALLBACK_CONFIRM_WINDOW_MS = 15_000L
internal const val SENTRY_RISK_EXIT_CONFIRM_SAMPLES = 3
internal const val SENTRY_POST_LOCK_ARMING_MS = 15_000L
internal const val SENTRY_PLAYBACK_COOLDOWN_MS = 15_000L
internal const val LOCK_SOUND_RECOVERY_MAX_AGE_MS = 5_000L

internal fun confirmedLockSoundEvent(
    previousState: Int?,
    currentState: Int?,
    flashConfirmed: Boolean,
    powerBlocked: Boolean,
): LockEvent? {
    if (powerBlocked) return null
    return when {
        previousState == LOCK_SOUND_DOOR_UNLOCKED && currentState == LOCK_SOUND_DOOR_LOCKED -> LockEvent.Lock
        previousState == LOCK_SOUND_DOOR_LOCKED &&
            currentState == LOCK_SOUND_DOOR_UNLOCKED &&
            flashConfirmed -> LockEvent.Unlock
        else -> null
    }
}

internal fun shouldPlayColdStartUnlock(
    previousState: Int?,
    currentState: Int?,
    flash: Int?,
    powerBlocked: Boolean,
): Boolean =
    confirmedLockSoundEvent(
        previousState = previousState,
        currentState = currentState,
        flashConfirmed = flash == 9,
        powerBlocked = powerBlocked,
    ) == LockEvent.Unlock

internal fun shouldRecoverStartupLockDifference(
    previousState: Int?,
    currentState: Int?,
    flashConfirmed: Boolean,
    powerBlocked: Boolean,
    sampleAgeMs: Long,
): Boolean =
    sampleAgeMs in 0L..LOCK_SOUND_RECOVERY_MAX_AGE_MS ||
        confirmedLockSoundEvent(
            previousState = previousState,
            currentState = currentState,
            flashConfirmed = flashConfirmed,
            powerBlocked = powerBlocked,
        ) == LockEvent.Unlock

internal fun isSentryCooldownComplete(nowMs: Long, lastPlaybackAtMs: Long): Boolean =
    lastPlaybackAtMs <= 0L ||
        nowMs < lastPlaybackAtMs ||
        nowMs - lastPlaybackAtMs >= SENTRY_PLAYBACK_COOLDOWN_MS

internal class SentryVehicleAccessGate {
    private var accessObserved = false
    private var secure = false
    private var armingUntilMs = 0L

    fun reset() {
        accessObserved = false
        secure = false
        armingUntilMs = 0L
    }

    fun blockReason(snapshot: LockSoundVehicleAccessSnapshot, nowMs: Long): String? {
        val access = snapshot.accessDetected()
        val nextSecure = snapshot.secureForSentry()
        if (access) accessObserved = true
        if (nextSecure && !secure && accessObserved) {
            armingUntilMs = nowMs + SENTRY_POST_LOCK_ARMING_MS
            accessObserved = false
        }
        secure = nextSecure
        return when {
            access -> "vehicle_access"
            !nextSecure -> "vehicle_access_unconfirmed"
            nowMs < armingUntilMs -> "post_lock_arming"
            else -> null
        }
    }
}

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
    if (highRiskLogSeen) return true
    if (!alarmTimestampChanged) return false
    if (lastHighRiskAtMs <= 0L) return false
    val age = nowMs - lastHighRiskAtMs
    return age in 0L..SENTRY_FALLBACK_CONFIRM_WINDOW_MS
}

internal fun shouldTriggerSentryFlashFallback(
    previousFlash: Int?,
    currentFlash: Int?,
    locked: Boolean,
    powerBlocked: Boolean,
): Boolean =
    locked &&
        !powerBlocked &&
        previousFlash == 9 &&
        currentFlash != null &&
        currentFlash != 9

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
@SuppressLint("PrivateApi")
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
    val electronicMirrorCamera = ElectronicMirrorCameraRuntime(systemContext)

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
                    withAutoserviceTransactionLock {
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
                    // Narrow, hardcoded — NOT a generic shell passthrough. These permissions are
                    // only granted to our own package for the overlay and fission display.
                    // SYSTEM_ALERT_WINDOW lets us draw the cluster overlay; PROJECT_MEDIA gates
                    // third-party access to the fission screen-projection display (without it
                    // DisplayManager.getDisplay(clusterId) returns null in our process).
                    val pkg = HelperBinderProtocol.APP_PACKAGE
                    val r1 = shExec("appops set \"\$1\" SYSTEM_ALERT_WINDOW allow", pkg)
                    val r2 = shExec("appops set \"\$1\" PROJECT_MEDIA allow", pkg)
                    val r3 = shExec("pm grant \"\$1\" android.permission.CAPTURE_VIDEO_OUTPUT", pkg)
                    helperLog(
                        "Permission",
                        "grant overlay=${r1.code} project=${r2.code} videoOutput=${r3.code}",
                    )
                    // Some DiLink builds do not expose CAPTURE_VIDEO_OUTPUT to package manager.
                    // Overlay and PROJECT_MEDIA are the hard requirements; keep the third grant
                    // best-effort while preserving its result in the helper log.
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

                HelperBinderProtocol.TX_PLAY_SENTRY_EVENT -> runCatching {
                    val source = data.readString().orEmpty().ifBlank { "binder_sentry" }.take(200)
                    val ok = lockSoundRuntime.playSentryEventForBinder(source)
                    reply?.writeInt(0)
                    reply?.writeInt(if (ok) 1 else 0)
                    true
                }.getOrElse {
                    helperLog("LockSound", "sentry event failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_CAMERA_START -> runCatching {
                    val cameraTag = data.readString().orEmpty()
                    val cameraIdOverride = data.readInt()
                    val surfaceIndex = data.readInt()
                    val tileCount = data.readInt()
                    val bufferWidth = data.readInt()
                    val bufferHeight = data.readInt()
                    val fps = data.readInt()
                    val surface = Surface.CREATOR.createFromParcel(data)
                    val status = electronicMirrorCamera.start(
                        surface = surface,
                        cameraTag = cameraTag,
                        cameraIdOverride = cameraIdOverride,
                        surfaceIndex = surfaceIndex,
                        tileCount = tileCount,
                        bufferWidth = bufferWidth,
                        bufferHeight = bufferHeight,
                        fps = fps,
                    )
                    reply?.writeInt(status); reply?.writeInt(0); true
                }.getOrElse {
                    helperLog("ElectronicMirrorCamera", "binder start failed: ${throwableSummary(it)}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_CAMERA_STOP -> runCatching {
                    val status = electronicMirrorCamera.stop()
                    reply?.writeInt(status); reply?.writeInt(0); true
                }.getOrElse {
                    helperLog("ElectronicMirrorCamera", "binder stop failed: ${throwableSummary(it)}")
                    reply?.writeInt(-1); reply?.writeInt(0); true
                }

                HelperBinderProtocol.TX_SET_LOCK_SOUND_PLAYBACK_STATE -> runCatching {
                    val enabledValue = data.readInt()
                    require(enabledValue == 0 || enabledValue == 1) { "invalid enabled=$enabledValue" }
                    val enabled = enabledValue == 1
                    val ok = BydLockSoundPlaybackState.setEnabled(enabled)
                    helperLog("ExteriorAudio", "lock sound playback state enabled=$enabled ok=$ok")
                    reply?.writeInt(if (ok) 0 else -1); reply?.writeInt(0)
                    true
                }.getOrElse {
                    helperLog("ExteriorAudio", "lock sound playback state failed: ${throwableSummary(it)}")
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
    WjxRuntimeWatchdog.enable()
    lockSoundRuntime.start()

    // Keepalive: Looper.loop() blocks this thread indefinitely so main() never returns
    // and the process stays alive. Incoming binder transactions are dispatched by the
    // binder threadpool that app_process starts via ProcessState::startThreadPool() in
    // AppRuntime::onStarted — Looper.loop() plays NO role in transaction dispatch here;
    // it is purely a blocking keepalive for the main thread.
    Looper.loop()
}
