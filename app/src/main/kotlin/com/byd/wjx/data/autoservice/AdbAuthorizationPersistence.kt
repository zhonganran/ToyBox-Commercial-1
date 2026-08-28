package com.byd.wjx.data.autoservice

import android.util.Log
import com.byd.wjx.log.WjxFileLogger

/** Persists the daemon public key and encrypted reinstall identity after authentication. */
internal class AdbAuthorizationPersistence(
    private val authorizedKeyLineProvider: () -> String,
    private val encryptedIdentityBackupProvider: () -> String?,
) {
    fun persist(active: AdbProtocol) {
        val identitySaved = persistIdentityBackup(active)
        if (identitySaved) {
            WjxFileLogger.i(TAG, "ADB encrypted reinstall identity verified in system settings")
        } else {
            WjxFileLogger.w(TAG, "ADB encrypted reinstall identity could not be verified")
        }
        val daemonKeySaved = persistAuthorizedKey(active)
        if (daemonKeySaved) {
            WjxFileLogger.i(TAG, "ADB authorized key verified in daemon key store")
        } else {
            WjxFileLogger.d(
                TAG,
                "ADB signature authorization confirmed; daemon key store is unavailable " +
                    "and current authorization remains usable",
            )
        }
    }

    private fun persistAuthorizedKey(active: AdbProtocol): Boolean = runFailSoft("authorized key") {
        val keyLine = authorizedKeyLineProvider().trim { it <= ' ' || it == '\u0000' }
        if (keyLine.isBlank()) return@runFailSoft false
        val output = active.exec(AdbShellCommands.persistAuthorizedKey(keyLine))
        output?.lineSequence()?.any { line ->
            line.trim() == AdbShellCommands.AUTHORIZED_KEY_SAVED_MARKER
        } == true
    }

    private fun persistIdentityBackup(active: AdbProtocol): Boolean =
        runFailSoft("encrypted identity backup") {
            val encryptedBackup = encryptedIdentityBackupProvider()?.takeIf(String::isNotBlank)
                ?: return@runFailSoft false
            val output = active.exec(AdbShellCommands.persistIdentityBackup(encryptedBackup))
            output?.lineSequence()?.any { line ->
                line.trim() == AdbShellCommands.IDENTITY_BACKUP_SAVED_MARKER
            } == true
        }

    private inline fun runFailSoft(operation: String, block: () -> Boolean): Boolean =
        try {
            block()
        } catch (error: Exception) {
            Log.d(TAG, "ADB $operation persist skipped: ${error.message}")
            WjxFileLogger.d(TAG, "ADB $operation persist skipped: ${error.message}")
            false
        }

    private companion object {
        const val TAG = "AdbAuthPersistence"
    }
}
