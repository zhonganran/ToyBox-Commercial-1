package com.byd.wjx.data.autoservice

import android.content.Context
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to the on-device ADB daemon at 127.0.0.1:5555 (DiLink has WiFi
 * ADB enabled in dev settings) using a persistent RSA keypair stored in
 * `filesDir/adb_keys/`. Once paired, exposes only the narrow shell operations
 * required to bootstrap and support the helper daemon.
 *
 * The production vehicle read/write path does not use this client. Vehicle
 * operations go through the versioned helper Binder and [com.byd.wjx.data.vehicle.VehicleGateway].
 *
 * Implementation: hand-rolled binary ADB protocol in [AdbProtocolClient]
 * (no external deps, no `adblib`). Auth flow uses standard RSA pubkey on
 * port 5555 — no TLS pairing / 6-digit code, the user accepts the «Allow USB
 * debugging?» dialog directly on DiLink.
 */
interface AdbOnDeviceClient {
    /** Initiates the ADB handshake. Suspends until handshake completes or fails. */
    suspend fun connect(): Result<Unit>
    suspend fun isConnected(): Boolean
    /** Strict read-only getprop helper for activation/device identity reads. */
    suspend fun getprop(name: String): String? = null

    /** Strict read-only settings helper for stable device identity fallback. */
    suspend fun getSecureSetting(name: String): String? = null

    /** Read-only media-session dump for lyric playback fallback. */
    suspend fun readMediaSessionDump(): String? = null

    /** Best-effort self grant for the app's notification-listener service. */
    suspend fun grantMusicNotificationListenerAccess(): Boolean = false
    /**
     * Grants PACKAGE_USAGE_STATS appop to our own package via shell uid. The
     * only non-autoservice write we permit through this client — needed so
     * UsageStatsManager.queryEvents returns data for camera-overlay detection.
     * No-op effect if already granted. Returns true on success.
     */
    suspend fun grantUsageStatsAppop(packageName: String): Boolean

    /**
     * Spawns the helper daemon under shell uid via app_process, using the app's
     * own signed base.apk as CLASSPATH (no dex push — integrity comes from the
     * APK signature). The daemon registers itself as the `wjx_helper` binder
     * service; reachability is verified separately by the binder client's ping,
     * not here. Returns true if the spawn command was dispatched without error
     * (NOT a liveness guarantee).
     *
     * Hardcoded cmdline + process name — caller cannot inject. Uses the private
     * protocol command path and is not exposed as a general shell API.
     */
    suspend fun spawnHelper(): Boolean

    /** Reads the daemon's stdout/stderr log (READY / ERR lines) for diagnostics. Null on transport error. */
    suspend fun readHelperLog(): String?

    /** Read-only check: is a process named `wjx_helper` running? */
    suspend fun helperHeartbeat(): Boolean

    /** Closes any underlying socket. Idempotent. */
    suspend fun shutdown()
}

@Singleton
class AdbOnDeviceClientImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val keyStore: AdbKeyStore,
) : AdbOnDeviceClient {

    /**
     * Test seam — UNIT TESTS ONLY. Lets the test layer swap the real
     * socket-backed protocol for a fake. Default factory creates the real
     * [AdbProtocolClient] with the persisted keypair.
     */
    @Suppress("unused")  // assigned via internal setter from tests
    internal var protocolFactory: () -> AdbProtocol = {
        AdbProtocolClient(keyStore.loadOrGenerate())
    }

    @Volatile private var protocol: AdbProtocol? = null
    private val connectMutex = Mutex()

    // used for packageCodePath when spawning the helper daemon
    private val ctx = context
    private val adbPrefs = ctx.getSharedPreferences(PREFS_ADB_CONFIG, Context.MODE_PRIVATE)

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        connectMutex.withLock {
            protocol?.takeIf { it.isConnected() }?.let {
                return@withLock Result.success(Unit)
            }

            var candidate: AdbProtocol? = null
            try {
                val p = protocol ?: protocolFactory().also { protocol = it }
                candidate = p
                val ok = p.connect()
                if (ok) {
                    saveAuthorizedState(true)
                    persistAuthorizedKey(p)
                    if (p.isConnected()) {
                        Result.success(Unit)
                    } else {
                        protocol = null
                        saveAuthorizedState(false)
                        Result.failure(IOException("ADB disconnected during setup"))
                    }
                } else {
                    protocol = null
                    saveAuthorizedState(false)
                    Result.failure(IOException("ADB connect refused"))
                }
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "connect failed: ${e.message}", e)
                runCatching { candidate?.disconnect() }
                protocol = null
                saveAuthorizedState(false)
                Result.failure(e)
            }
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        runCatching { protocol?.isConnected() ?: false }.getOrDefault(false)
    }

    override suspend fun getprop(name: String): String? = withContext(Dispatchers.IO) {
        require(name.matches(GETPROP_NAME_REGEX)) {
            "AdbOnDeviceClient: refused getprop name: $name"
        }
        execWithReconnect("getprop $name", "getprop:$name")
    }

    override suspend fun getSecureSetting(name: String): String? = withContext(Dispatchers.IO) {
        require(name.matches(SETTING_NAME_REGEX)) {
            "AdbOnDeviceClient: refused secure setting name: $name"
        }
        execWithReconnect("settings get secure $name", "secure_setting:$name")
    }

    override suspend fun readMediaSessionDump(): String? = withContext(Dispatchers.IO) {
        execWithReconnect("dumpsys media_session", "media_session")
    }

    override suspend fun grantMusicNotificationListenerAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val component = "com.byd.wjx/com.byd.wjx.lyrics.MusicNotificationListenerService"
            val quotedComponent = shellQuote(component)
            val cmd =
                "current=\$(settings get secure enabled_notification_listeners 2>/dev/null); " +
                    "case \"\$current\" in *$component*) ;; null|\"\") settings put secure enabled_notification_listeners $quotedComponent ;; " +
                    "*) settings put secure enabled_notification_listeners \"\$current:$component\" ;; esac; " +
                    "cmd notification allow_listener $quotedComponent 2>/dev/null || true; " +
                    "echo ok"
            val out = execWithReconnect(cmd, "grant_notification_listener")
                ?: return@withContext false
            out.contains("ok")
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "grantMusicNotificationListenerAccess failed: ${e.message}", e)
            false
        }
    }

    override suspend fun grantUsageStatsAppop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        // Only permit our own package — never grant appops to anything else.
        require(packageName.matches(PACKAGE_NAME_REGEX)) {
            "grantUsageStatsAppop: refused package $packageName"
        }
        val cmd = "appops set $packageName GET_USAGE_STATS allow"
        try {
            // appops prints nothing on success; null means transport error.
            // Treat any non-null output as failure (e.g. "Bad permission") too.
            val out = execWithReconnect(cmd, "grant_usage_stats") ?: return@withContext false
            out.isBlank()
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "grantUsageStatsAppop failed: ${e.message}", e)
            false
        }
    }

    override suspend fun spawnHelper(): Boolean = withContext(Dispatchers.IO) {
        try {
            val killOut = execWithReconnect(
                "pkill -9 -f $HELPER_PROCESS_NAME 2>/dev/null; sleep 0.5",
                "pkill_helper",
            ) ?: return@withContext false
            WjxFileLogger.i(TAG, "pkill helper result=${killOut.ifBlank { "<empty>" }}")
            // Raw protocol exec — bypasses the public exec() write barrier by design
            // (this is not an autoservice GET). Hardcoded, no caller input.
            // CLASSPATH = the app's own signed base.apk; setsid detaches the daemon
            // into its own session. The trailing poll-loop keeps THIS shell alive until
            // the daemon registers (or 3s elapse): the on-device ADB closes the exec
            // stream the instant `&` backgrounds the job, and adbd SIGHUPs the subprocess
            // — without the loop the still-booting JVM dies before its first println
            // (empty log, no registration). Mirrors the proven BYD EV Pro / aps_diplus
            // spawn recipe; see reference_autoservice_write_channel.md.
            WjxDirectories.ensureBaseDirs()
            val helperClasspathScript = helperClasspathShell()
            val helperLogPath = shellQuote(WjxFileLogger.logFilePath)
            val helperLogDir = shellQuote(WjxDirectories.baseDir.absolutePath)
            val helperStartScript = shellQuote(helperStartScript(helperClasspathScript, helperLogPath))
            val spawnCmd =
                "mkdir -p $helperLogDir; printf '%s\\n' '[wjx_helper spawn]' >>$helperLogPath; " +
                "printf '%s\\n' $helperStartScript >/data/local/tmp/wjx_helper_start.sh; " +
                "chmod 755 /data/local/tmp/wjx_helper_start.sh; " +
                "sh /data/local/tmp/wjx_helper_start.sh"
            // Do not retry this command automatically: a timeout can happen after app_process
            // has already started. HelperBootstrap polls Binder liveness and decides the result.
            execWithReconnect(spawnCmd, "spawn_helper", retryOnce = false) != null
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "spawnHelper failed: ${e.message}", e)
            false
        }
    }

    override suspend fun readHelperLog(): String? = withContext(Dispatchers.IO) {
        WjxFileLogger.tail(120).joinToString("\n")
    }

    override suspend fun helperHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        val out = execWithReconnect("ps -A -o NAME", "helper_heartbeat")
            ?: return@withContext false
        out.lineSequence().any { it.trim() == HELPER_PROCESS_NAME }
    }

    override suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            connectMutex.withLock {
                try {
                    protocol?.disconnect()
                } catch (_: Exception) { /* idempotent */ }
                protocol = null
            }
        }
    }

    private suspend fun connectedProtocol(): AdbProtocol? {
        val current = protocol
        if (current != null && runCatching { current.isConnected() }.getOrDefault(false)) {
            return current
        }
        if (connect().isFailure) return null
        return protocol?.takeIf { runCatching { it.isConnected() }.getOrDefault(false) }
    }

    private suspend fun execWithReconnect(
        command: String,
        operation: String,
        retryOnce: Boolean = true,
    ): String? {
        val attempts = if (retryOnce) 2 else 1
        repeat(attempts) { attempt ->
            val active = connectedProtocol() ?: return null
            val output = try {
                kotlinx.coroutines.runInterruptible { active.exec(command) }
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "$operation failed: ${e.message}", e)
                null
            }
            if (output != null) return output

            invalidateProtocol(active, operation)
            if (attempt + 1 < attempts) {
                WjxFileLogger.w(TAG, "$operation transport lost; reconnecting attempt=${attempt + 2}")
            }
        }
        return null
    }

    private suspend fun invalidateProtocol(failed: AdbProtocol, operation: String) {
        val invalidated = connectMutex.withLock {
            if (protocol !== failed || runCatching { failed.isConnected() }.getOrDefault(false)) {
                return@withLock false
            }
            runCatching { failed.disconnect() }
            protocol = null
            saveAuthorizedState(false)
            true
        }
        if (invalidated) {
            WjxFileLogger.w(TAG, "$operation invalidated disconnected ADB protocol")
        }
    }

    /**
     * Mirrors the proven on-car pattern: after the user accepts the first ADB
     * prompt, try to add our public key to adbd's authorized-key file so future
     * reconnects can pass by signature without showing another prompt. This is
     * best-effort because some firmware builds keep /data/misc/adb read-only for
     * shell; a failure must never block autoservice or helper startup.
     */
    private fun persistAuthorizedKey(p: AdbProtocol) {
        try {
            val keyLine = String(
                AdbProtocolClient.serializePublicKey(keyStore.loadOrGenerate()),
                Charsets.UTF_8
            ).trim { it <= ' ' || it == '\u0000' }
            if (keyLine.isBlank()) return

            val keyPrefix = keyLine.substringBefore(' ')
            val cmd =
                "grep -qF ${shellQuote(keyPrefix)} /data/misc/adb/adb_keys 2>/dev/null || " +
                    "echo ${shellQuote(keyLine)} >> /data/misc/adb/adb_keys"
            val out = p.exec(cmd)
            if (out == null) {
                Log.d(TAG, "ADB authorized-key persist skipped or unavailable")
                WjxFileLogger.d(TAG, "ADB authorized-key persist skipped or unavailable")
            }
        } catch (e: Exception) {
            Log.d(TAG, "ADB authorized-key persist failed: ${e.message}")
            WjxFileLogger.d(TAG, "ADB authorized-key persist failed: ${e.message}")
        }
    }

    private fun saveAuthorizedState(authorized: Boolean) {
        adbPrefs.edit().putBoolean(PREF_ADB_AUTHORIZED, authorized).apply()
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun helperStartScript(helperClasspathScript: String, helperLogPath: String): String =
        """
        #!/system/bin/sh
        mkdir -p /sdcard/WJX 2>/dev/null
        printf '%s\n' '[wjx_helper start-script]' >>$helperLogPath
        $helperClasspathScript
        CLASSPATH="${'$'}HELPER_CP" setsid app_process /system/bin \
          --nice-name=$HELPER_PROCESS_NAME com.byd.wjx.helper.HelperDaemon \
          ${android.os.Process.myUid()} </dev/null >>$helperLogPath 2>&1 &
        for i in 1 2 3; do
          service list 2>/dev/null | grep -q $HELPER_PROCESS_NAME && break
          sleep 1
        done
        """.trimIndent()

    private fun helperClasspathShell(): String {
        val appApk = shellQuote(ctx.packageCodePath)
        val packageLoop = BYD_HELPER_CLASSPATH_PACKAGES.joinToString(" ") { shellQuote(it) }
        val fileLoop = BYD_HELPER_CLASSPATH_FILES.joinToString(" ") { shellQuote(it) }
        return "HELPER_CP=$appApk; " +
            "for pkg in $packageLoop; do " +
            "path=\$(pm path \"\$pkg\" 2>/dev/null | sed -n 's/^package://p' | head -1); " +
            "[ -n \"\$path\" ] && HELPER_CP=\"\$HELPER_CP:\$path\"; " +
            "done; " +
            "for path in $fileLoop; do " +
            "[ -f \"\$path\" ] && HELPER_CP=\"\$HELPER_CP:\$path\"; " +
            "done"
    }

    companion object {
        private const val TAG = "AdbOnDevice"
        private const val PREFS_ADB_CONFIG = "adb_config"
        private const val PREF_ADB_AUTHORIZED = "adb_authorized"

        private val GETPROP_NAME_REGEX = Regex("""^[A-Za-z0-9_.-]{1,96}$""")
        private val SETTING_NAME_REGEX = Regex("""^[A-Za-z0-9_.-]{1,96}$""")

        // Narrow whitelist for grantUsageStatsAppop — only our own package.
        private val PACKAGE_NAME_REGEX = Regex("""^com\.byd\.wjx$""")

        // Helper daemon — hardcoded so neither caller can inject paths/cmdlines.
        private const val HELPER_PROCESS_NAME = "wjx_helper"
        private val BYD_HELPER_CLASSPATH_PACKAGES = listOf(
            "com.byd.launcher",
            "com.byd.car.server",
            "com.byd.clusterdebug",
            "com.byd.vehicleconfig",
            "com.xy.instrumentnavigation",
            "com.android.launcher3",
        )
        private val BYD_HELPER_CLASSPATH_FILES = listOf(
            "/system/app/BydAutoVoiceTTS/BydAutoVoiceTTS.apk",
        )
    }
}
