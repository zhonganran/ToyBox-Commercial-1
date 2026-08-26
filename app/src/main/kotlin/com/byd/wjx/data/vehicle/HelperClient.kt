package com.byd.wjx.data.vehicle

import android.view.Surface

data class HelperShellResult(
    val status: Int,
    val exitCode: Int,
    val output: String,
) {
    val ok: Boolean get() = status == 0 && exitCode == 0
}

enum class HeadlightMode(val serviceValue: Int, val label: String) {
    OFF(16, "关闭"),
    AUTO(2, "自动"),
    LOW_BEAM(4, "近光"),
    HIGH_BEAM(8, "远光");

    companion object {
        fun fromServiceValue(value: Int): HeadlightMode? = entries.firstOrNull {
            it.serviceValue == value
        }
    }
}

data class HelperHeadlightReply(
    val transportStatus: Int,
    val serviceCode: Int,
    val value: Int,
) {
    val transportOk: Boolean get() = transportStatus == 0
    val serviceOk: Boolean get() = serviceCode == 0
}

/**
 * Client contract for the in-vehicle helper daemon registered as `wjx_helper`.
 * Transport and platform details are owned by [HelperClientImpl].
 *
 * read()/write()/isAlive() return null/false on any failure: daemon not
 * registered, dead binder, transact rejected by the daemon's uid gate, or
 * an autoservice error. Callers must treat null/false as "channel
 * unavailable, retry later".
 *
 * Platform access prerequisites are installed centrally in SuperToyBoxApp.onCreate.
 */
interface HelperClient {
    suspend fun read(dev: Int, fid: Int, tx: Int = 5): Long?
    /** Ordered batch read. Null means the helper channel or protocol is unavailable. */
    suspend fun readBatch(specs: List<VehicleReadSpec>): List<VehicleRawRead>? = null
    suspend fun write(dev: Int, fid: Int, value: Int): Boolean
    /**
     * Raw autoservice status for a write:
     * 1 = physical action, 0 = accepted no-op, <0/null = error or helper unreachable.
     */
    suspend fun writeStatus(dev: Int, fid: Int, value: Int): Int?
    suspend fun isAlive(): Boolean
    /** Returns the helper's exact wire-protocol version, or null for an invalid reply. */
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
     * getDisplay(clusterId) returns null). Returns true when both required app-ops succeed.
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
    /** Submits a real sentry incident; helper enforces guarding, access, arming and persistent cooldown gates. */
    suspend fun playSentryEvent(source: String): Boolean = false
    /** Restricted sentry settings helper; writes only accept the daemon allowlist. */
    suspend fun sentryShell(op: String, arg: String): String? = null
    /** Generic helper-side shell execution with structured status, exit and output fields. */
    suspend fun execShellResult(command: String): HelperShellResult? = null
    /** Generic helper-side shell execution. Keep call sites narrow and logged. */
    suspend fun execShell(command: String): String? = null
    /** Checks BYD's official external-light capability bit for headlight mode control. */
    suspend fun queryHeadlightSupport(): HelperHeadlightReply? = null
    /** Reads the current mode from BYD's official external-light service. */
    suspend fun getHeadlightMode(): HelperHeadlightReply? = null
    /** Sets one official mode and returns the actual mode read back by the helper. */
    suspend fun setHeadlightMode(mode: HeadlightMode): HelperHeadlightReply? = null
    /** Applies BYD's privileged lock-sound playback route state in the helper process. */
    suspend fun setLockSoundPlaybackState(enabled: Boolean): Boolean = false
}
