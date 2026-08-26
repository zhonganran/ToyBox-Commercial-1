package com.byd.wjx.data.autoservice

import android.content.Context
import android.provider.Settings
import com.byd.wjx.data.system.AppScopedDeviceBindingProvider
import com.byd.wjx.data.system.AppSigningCertificateDigestProvider
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores an encrypted, device-bound copy of the app's ADB identity outside app data.
 *
 * The encrypted blob is written to [SETTING_NAME] only through an already authenticated ADB
 * shell. It can therefore survive app uninstall while the raw private key never leaves app data.
 */
@Singleton
class AdbIdentityBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceBindingProvider: AppScopedDeviceBindingProvider,
    private val signingDigestProvider: AppSigningCertificateDigestProvider,
) {
    fun restore(): KeyPair? {
        val encoded = runCatching {
            Settings.System.getString(context.contentResolver, SETTING_NAME)
        }.getOrNull()?.takeIf(String::isNotBlank) ?: return null

        return runCatching {
            AdbIdentityBackupCodec.decrypt(encoded, deviceBinding())
        }.onFailure { error ->
            WjxFileLogger.w(TAG, "ADB identity backup could not be restored: ${error.message}")
        }.getOrNull()
    }

    /** Reuses a valid existing blob so normal reconnects do not rewrite the system setting. */
    fun getOrCreateEncryptedBackup(keyPair: KeyPair): String? = runCatching {
        val binding = deviceBinding()
        val existing = Settings.System.getString(context.contentResolver, SETTING_NAME)
            ?.takeIf(String::isNotBlank)
        if (existing != null) {
            val restored = runCatching {
                AdbIdentityBackupCodec.decrypt(existing, binding)
            }.getOrNull()
            if (restored != null && restored.hasSameKeyMaterial(keyPair)) return@runCatching existing
        }
        AdbIdentityBackupCodec.encrypt(keyPair, binding)
    }.onFailure { error ->
        WjxFileLogger.w(TAG, "ADB identity backup could not be created: ${error.message}")
    }.getOrNull()

    private fun deviceBinding(): AdbIdentityBinding {
        val deviceBinding = deviceBindingProvider.derive(DEVICE_BINDING_PURPOSE)
            ?: error("App-scoped device binding unavailable")
        val signingDigest = signingDigestProvider.currentSha256()
            ?: error("Signing certificate unavailable")
        return AdbIdentityBinding(deviceBinding, signingDigest)
    }

    companion object {
        internal const val SETTING_NAME = "wjx_adb_identity_v1"
        private const val DEVICE_BINDING_PURPOSE = "adb-identity-backup-v1"
        private const val TAG = "AdbIdentityBackup"
    }
}

internal data class AdbIdentityBinding(
    val deviceBindingSha256: ByteArray,
    val signingCertificateSha256: ByteArray,
) {
    init {
        require(deviceBindingSha256.size == SHA_256_BYTES) {
            "Device binding must be SHA-256"
        }
        require(signingCertificateSha256.size == SHA_256_BYTES) {
            "Signing certificate digest must be SHA-256"
        }
    }

    fun deriveAesKey(): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(KEY_DERIVATION_DOMAIN)
        update(0)
        update(deviceBindingSha256)
        update(0)
        update(signingCertificateSha256)
        digest()
    }

    private companion object {
        const val SHA_256_BYTES = 32
        val KEY_DERIVATION_DOMAIN = "com.byd.wjx.adb-identity-backup.v1"
            .toByteArray(StandardCharsets.US_ASCII)
    }
}

internal object AdbIdentityBackupCodec {
    private val MAGIC = "WJXADBID".toByteArray(StandardCharsets.US_ASCII)
    private val AAD = MAGIC + byteArrayOf(VERSION)
    private const val VERSION: Byte = 1
    private const val NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_BLOB_BYTES = 8 * 1024
    private const val MAX_PRIVATE_KEY_BYTES = 4 * 1024
    private const val MAX_PUBLIC_KEY_BYTES = 1024

    fun encrypt(
        keyPair: KeyPair,
        binding: AdbIdentityBinding,
        secureRandom: SecureRandom = SecureRandom(),
    ): String {
        validateKeyPair(keyPair)
        val privateKey = keyPair.private.encoded
        val publicKey = keyPair.public.encoded
        require(privateKey.size <= MAX_PRIVATE_KEY_BYTES) { "ADB private key is too large" }
        require(publicKey.size <= MAX_PUBLIC_KEY_BYTES) { "ADB public key is too large" }

        val plaintext = ByteBuffer.allocate(
            Int.SIZE_BYTES + privateKey.size + Int.SIZE_BYTES + publicKey.size,
        ).putInt(privateKey.size)
            .put(privateKey)
            .putInt(publicKey.size)
            .put(publicKey)
            .array()
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(binding.deriveAesKey(), "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            updateAAD(AAD)
        }
        val ciphertext = cipher.doFinal(plaintext)
        val envelope = ByteBuffer.allocate(AAD.size + nonce.size + ciphertext.size)
            .put(AAD)
            .put(nonce)
            .put(ciphertext)
            .array()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
    }

    fun decrypt(encoded: String, binding: AdbIdentityBinding): KeyPair {
        require(encoded.length <= MAX_BLOB_BYTES * 2) { "ADB identity backup text is too large" }
        val envelope = Base64.getUrlDecoder().decode(encoded)
        require(envelope.size in (AAD.size + NONCE_BYTES + 17)..MAX_BLOB_BYTES) {
            "ADB identity backup has an invalid size"
        }
        val buffer = ByteBuffer.wrap(envelope)
        val header = ByteArray(AAD.size).also(buffer::get)
        require(header.contentEquals(AAD)) { "ADB identity backup version is unsupported" }
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(binding.deriveAesKey(), "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            updateAAD(AAD)
        }
        return decodeKeyPair(cipher.doFinal(ciphertext))
    }

    private fun decodeKeyPair(plaintext: ByteArray): KeyPair {
        val buffer = ByteBuffer.wrap(plaintext)
        require(buffer.remaining() >= Int.SIZE_BYTES * 2) { "ADB identity payload is truncated" }
        val privateLength = buffer.int
        require(privateLength in 1..MAX_PRIVATE_KEY_BYTES) { "Invalid ADB private-key length" }
        require(buffer.remaining() >= privateLength + Int.SIZE_BYTES) { "ADB private key is truncated" }
        val privateKey = ByteArray(privateLength).also(buffer::get)
        val publicLength = buffer.int
        require(publicLength in 1..MAX_PUBLIC_KEY_BYTES) { "Invalid ADB public-key length" }
        require(buffer.remaining() == publicLength) { "ADB public key is truncated or has trailing data" }
        val publicKey = ByteArray(publicLength).also(buffer::get)
        val factory = KeyFactory.getInstance("RSA")
        return KeyPair(
            factory.generatePublic(X509EncodedKeySpec(publicKey)),
            factory.generatePrivate(PKCS8EncodedKeySpec(privateKey)),
        ).also(::validateKeyPair)
    }

    fun validateKeyPair(keyPair: KeyPair) {
        val publicKey = keyPair.public as? RSAPublicKey ?: error("ADB public key is not RSA")
        val privateKey = keyPair.private as? RSAPrivateKey ?: error("ADB private key is not RSA")
        require(publicKey.modulus == privateKey.modulus) { "ADB keypair does not match" }
        require(publicKey.modulus.bitLength() == 2048) { "ADB keypair must use 2048-bit RSA" }
    }
}

private fun KeyPair.hasSameKeyMaterial(other: KeyPair): Boolean =
    public.encoded.contentEquals(other.public.encoded) &&
        private.encoded.contentEquals(other.private.encoded)
