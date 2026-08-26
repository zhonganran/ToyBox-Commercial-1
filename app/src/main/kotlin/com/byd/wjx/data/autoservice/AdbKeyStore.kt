package com.byd.wjx.data.autoservice

import android.content.Context
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the SuperToyBox-side ADB RSA keypair under `filesDir/adb_keys/`.
 *
 * Files use raw `PKCS#8` private and X.509 public key bytes.
 *
 * The keystore is intentionally NOT the Android Keystore: standard ADB pubkey
 * auth needs raw modulus/exponent in a non-standard 524-byte ADB layout
 * ([AdbProtocolClient]), which Android Keystore-protected keys cannot expose.
 */
@Singleton
class AdbKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityBackupStore: AdbIdentityBackupStore,
) {
    private val keyFiles = AdbKeyFiles(context.filesDir)
    @Volatile private var cached: KeyPair? = null

    /**
     * Loads app files, then the device-bound reinstall backup, before generating a new keypair.
     */
    @Synchronized
    fun loadOrGenerate(): KeyPair {
        cached?.let { return it }

        try {
            keyFiles.load()?.let { pair ->
                cached = pair
                return pair
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to load saved keypair, trying device backup: $error")
            WjxFileLogger.w(TAG, "Failed to load saved keypair: ${error.message}")
        }

        identityBackupStore.restore()?.let { restored ->
            return try {
                keyFiles.persist(restored)
                cached = restored
                WjxFileLogger.i(
                    TAG,
                    "restored adb keypair fingerprint=${getFingerprintFor(restored)}",
                )
                restored
            } catch (error: Exception) {
                Log.w(TAG, "Failed to persist restored keypair, regenerating: $error")
                WjxFileLogger.w(TAG, "Failed to persist restored keypair: ${error.message}")
                generateAndPersist()
            }
        }

        return generateAndPersist()
    }

    internal fun getOrCreateEncryptedIdentityBackup(): String? =
        identityBackupStore.getOrCreateEncryptedBackup(loadOrGenerate())

    private fun generateAndPersist(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = gen.generateKeyPair()
        keyFiles.persist(pair)
        cached = pair
        WjxFileLogger.i(TAG, "generated new adb keypair fingerprint=${getFingerprintFor(pair)}")
        return pair
    }

    /**
     * SHA-1 hex (uppercase) of the encoded public key bytes. For diagnostic
     * logs only — NOT the same fingerprint format the ADB daemon shows in the
     * "Allow USB debugging?" dialog (that one is computed from the 524-byte
     * ADB pubkey blob, not X.509).
     */
    fun getFingerprint(): String {
        val pair = loadOrGenerate()
        return getFingerprintFor(pair)
    }

    private fun getFingerprintFor(pair: KeyPair): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(pair.public.encoded)
        return digest.joinToString("") { "%02X".format(it) }
    }

    private companion object {
        const val TAG = "AdbKeyStore"
    }
}
