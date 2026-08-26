package com.byd.wjx.data.autoservice

import android.util.Log
import com.byd.wjx.log.WjxFileLogger

/** Persists the daemon public key and encrypted reinstall identity after authentication. */
internal class AdbAuthorizationPersistence(
    private val authorizedKeyLineProvider: () -> String,
    private val encryptedIdentityBackupProvider: () -> String?,
) {
    fun persist(active: AdbProtocol) {
        persistAuthorizedKey(active)
        persistIdentityBackup(active)
    }

    private fun persistAuthorizedKey(active: AdbProtocol) = runFailSoft("authorized key") {
        val keyLine = authorizedKeyLineProvider().trim { it <= ' ' || it == '\u0000' }
        if (keyLine.isBlank()) return@runFailSoft
        val output = active.exec(AdbShellCommands.persistAuthorizedKey(keyLine))
        check(output?.lineSequence()?.any { line ->
            line.trim() == AdbShellCommands.AUTHORIZED_KEY_SAVED_MARKER
        } == true) { "daemon key verification failed" }
    }

    private fun persistIdentityBackup(active: AdbProtocol) = runFailSoft("encrypted identity backup") {
        val encryptedBackup = encryptedIdentityBackupProvider()?.takeIf(String::isNotBlank)
            ?: return@runFailSoft
        val output = active.exec(AdbShellCommands.persistIdentityBackup(encryptedBackup))
        check(output?.lineSequence()?.any { line ->
            line.trim() == AdbShellCommands.IDENTITY_BACKUP_SAVED_MARKER
        } == true) { "system setting verification failed" }
    }

    private inline fun runFailSoft(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            Log.d(TAG, "ADB $operation persist skipped: ${error.message}")
            WjxFileLogger.d(TAG, "ADB $operation persist skipped: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "AdbAuthPersistence"
    }
}
