package com.byd.wjx.data.autoservice

import android.content.SharedPreferences
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns one authenticated protocol instance, serialized connects, reconnects, and invalidation. */
internal class AdbConnectionManager(
    private val preferences: SharedPreferences,
    private val protocolFactory: () -> AdbProtocol,
    private val authorizedKeyLineProvider: () -> String,
) {
    @Volatile private var protocol: AdbProtocol? = null
    private val connectMutex = Mutex()

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        connectMutex.withLock {
            protocol?.takeIf(AdbProtocol::isConnected)?.let {
                return@withLock Result.success(Unit)
            }

            var candidate: AdbProtocol? = null
            try {
                val active = protocol ?: protocolFactory().also { protocol = it }
                candidate = active
                if (!active.connect()) {
                    protocol = null
                    saveAuthorizedState(false)
                    return@withLock Result.failure(IOException("ADB connect refused"))
                }

                saveAuthorizedState(true)
                persistAuthorizedKey(active)
                if (active.isConnected()) {
                    Result.success(Unit)
                } else {
                    protocol = null
                    saveAuthorizedState(false)
                    Result.failure(IOException("ADB disconnected during setup"))
                }
            } catch (error: Exception) {
                WjxFileLogger.w(TAG, "connect failed: ${error.message}", error)
                runCatching { candidate?.disconnect() }
                protocol = null
                saveAuthorizedState(false)
                Result.failure(error)
            }
        }
    }

    suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        runCatching { protocol?.isConnected() ?: false }.getOrDefault(false)
    }

    suspend fun execute(
        command: String,
        operation: String,
        retryOnce: Boolean = true,
    ): String? {
        val attempts = if (retryOnce) 2 else 1
        repeat(attempts) { attempt ->
            val active = connectedProtocol() ?: return null
            val output = try {
                runInterruptible { active.exec(command) }
            } catch (error: Exception) {
                WjxFileLogger.w(TAG, "$operation failed: ${error.message}", error)
                null
            }
            if (output != null) return output

            invalidate(active, operation)
            if (attempt + 1 < attempts) {
                WjxFileLogger.w(TAG, "$operation transport lost; reconnecting attempt=${attempt + 2}")
            }
        }
        return null
    }

    suspend fun shutdown() = withContext(Dispatchers.IO) {
        connectMutex.withLock {
            runCatching { protocol?.disconnect() }
            protocol = null
        }
    }

    private suspend fun connectedProtocol(): AdbProtocol? {
        val current = protocol
        if (current != null && runCatching(current::isConnected).getOrDefault(false)) return current
        if (connect().isFailure) return null
        return protocol?.takeIf { active -> runCatching(active::isConnected).getOrDefault(false) }
    }

    private suspend fun invalidate(failed: AdbProtocol, operation: String) {
        val invalidated = connectMutex.withLock {
            if (protocol !== failed || runCatching(failed::isConnected).getOrDefault(false)) {
                return@withLock false
            }
            runCatching(failed::disconnect)
            protocol = null
            saveAuthorizedState(false)
            true
        }
        if (invalidated) WjxFileLogger.w(TAG, "$operation invalidated disconnected ADB protocol")
    }

    private fun persistAuthorizedKey(active: AdbProtocol) {
        try {
            val keyLine = authorizedKeyLineProvider().trim { it <= ' ' || it == '\u0000' }
            if (keyLine.isBlank()) return
            val keyPrefix = keyLine.substringBefore(' ')
            val command =
                "grep -qF ${AdbShellCommands.shellQuote(keyPrefix)} /data/misc/adb/adb_keys 2>/dev/null || " +
                    "echo ${AdbShellCommands.shellQuote(keyLine)} >> /data/misc/adb/adb_keys"
            if (active.exec(command) == null) {
                Log.d(TAG, "ADB authorized-key persist skipped or unavailable")
                WjxFileLogger.d(TAG, "ADB authorized-key persist skipped or unavailable")
            }
        } catch (error: Exception) {
            Log.d(TAG, "ADB authorized-key persist failed: ${error.message}")
            WjxFileLogger.d(TAG, "ADB authorized-key persist failed: ${error.message}")
        }
    }

    private fun saveAuthorizedState(authorized: Boolean) {
        preferences.edit().putBoolean(PREFERENCE_AUTHORIZED, authorized).apply()
    }

    private companion object {
        const val TAG = "AdbConnectionManager"
        const val PREFERENCE_AUTHORIZED = "adb_authorized"
    }
}
