package com.byd.wjx.data.autoservice

import android.content.Context
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Maps the public ADB capabilities to the strict command catalog and connection manager. */
@Singleton
class AdbOnDeviceClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: AdbKeyStore,
) : AdbOnDeviceClient {
    @Suppress("unused")
    internal var protocolFactory: () -> AdbProtocol = {
        AdbProtocolClient(keyStore.loadOrGenerate())
    }

    private val connectionManager = AdbConnectionManager(
        preferences = context.getSharedPreferences(PREFERENCES_ADB_CONFIG, Context.MODE_PRIVATE),
        protocolFactory = { protocolFactory() },
        authorizedKeyLineProvider = {
            String(AdbAuth.serializePublicKey(keyStore.loadOrGenerate()), Charsets.UTF_8)
        },
    )

    override suspend fun connect(): Result<Unit> = connectionManager.connect()

    override suspend fun isConnected(): Boolean = connectionManager.isConnected()

    override suspend fun getprop(name: String): String? = withContext(Dispatchers.IO) {
        connectionManager.execute(AdbShellCommands.getProperty(name), "getprop:$name")
    }

    override suspend fun getSecureSetting(name: String): String? = withContext(Dispatchers.IO) {
        connectionManager.execute(AdbShellCommands.getSecureSetting(name), "secure_setting:$name")
    }

    override suspend fun readMediaSessionDump(): String? = withContext(Dispatchers.IO) {
        connectionManager.execute("dumpsys media_session", "media_session")
    }

    override suspend fun grantMusicNotificationListenerAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            connectionManager.execute(
                AdbShellCommands.grantMusicNotificationListener(),
                "grant_notification_listener",
            )?.contains("ok") == true
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "grantMusicNotificationListenerAccess failed: ${error.message}", error)
            false
        }
    }

    override suspend fun grantUsageStatsAppop(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                connectionManager.execute(
                    AdbShellCommands.grantUsageStats(packageName),
                    "grant_usage_stats",
                )?.isBlank() == true
            } catch (error: Exception) {
                WjxFileLogger.w(TAG, "grantUsageStatsAppop failed: ${error.message}", error)
                false
            }
        }

    override suspend fun spawnHelper(): Boolean = withContext(Dispatchers.IO) {
        try {
            val killOutput = connectionManager.execute(
                AdbShellCommands.killHelper(),
                "pkill_helper",
            ) ?: return@withContext false
            WjxFileLogger.i(TAG, "pkill helper result=${killOutput.ifBlank { "<empty>" }}")

            WjxDirectories.ensureBaseDirs()
            val command = AdbShellCommands.spawnHelper(
                packageCodePath = context.packageCodePath,
                callerUid = android.os.Process.myUid(),
                helperLogPath = WjxFileLogger.logFilePath,
                helperLogDirectory = WjxDirectories.baseDir.absolutePath,
            )
            connectionManager.execute(command, "spawn_helper", retryOnce = false) != null
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "spawnHelper failed: ${error.message}", error)
            false
        }
    }

    override suspend fun readHelperLog(): String? = withContext(Dispatchers.IO) {
        WjxFileLogger.tail(120).joinToString("\n")
    }

    override suspend fun helperHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        connectionManager.execute(
            AdbShellCommands.helperHeartbeat(),
            "helper_heartbeat",
        )?.lineSequence()?.any { line -> line.trim() == AdbShellCommands.HELPER_PROCESS_NAME } == true
    }

    override suspend fun shutdown() = connectionManager.shutdown()

    private companion object {
        const val TAG = "AdbOnDevice"
        const val PREFERENCES_ADB_CONFIG = "adb_config"
    }
}
