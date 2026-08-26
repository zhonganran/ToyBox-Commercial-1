package com.byd.wjx.data.autoservice

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.system.AppScopedDeviceBindingProvider
import com.byd.wjx.data.system.AppSigningCertificateDigestProvider
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbIdentityBackupStoreTest {
    private val signingDigest = ByteArray(32) { index -> index.toByte() }
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        Settings.System.putString(
            context.contentResolver,
            AdbIdentityBackupStore.SETTING_NAME,
            null,
        )
    }

    @Test
    fun `store systemSetting roundTrip restoresKeypair`() {
        val original = newKeyPair()
        val deviceBindingProvider = mockk<AppScopedDeviceBindingProvider>()
        every { deviceBindingProvider.derive(any()) } returns deviceDigest("device-a")
        val signingDigestProvider = mockk<AppSigningCertificateDigestProvider>()
        every { signingDigestProvider.currentSha256() } returns signingDigest
        val store = AdbIdentityBackupStore(
            context,
            deviceBindingProvider,
            signingDigestProvider,
        )
        val encoded = store.getOrCreateEncryptedBackup(original)
        assertNotNull(encoded)
        Settings.System.putString(
            context.contentResolver,
            AdbIdentityBackupStore.SETTING_NAME,
            encoded,
        )

        val restored = store.restore()

        assertNotNull(restored)
        assertArrayEquals(original.public.encoded, restored?.public?.encoded)
        assertArrayEquals(original.private.encoded, restored?.private?.encoded)
    }

    @Test
    fun `codec roundTrip restoresBothKeyHalves`() {
        val original = newKeyPair()
        val binding = binding("device-a")

        val encoded = AdbIdentityBackupCodec.encrypt(original, binding)
        val restored = AdbIdentityBackupCodec.decrypt(encoded, binding)

        assertArrayEquals(original.public.encoded, restored.public.encoded)
        assertArrayEquals(original.private.encoded, restored.private.encoded)
    }

    @Test
    fun `codec differentDevice rejectsBackup`() {
        val encoded = AdbIdentityBackupCodec.encrypt(
            newKeyPair(),
            binding("device-a"),
        )

        assertThrows(Exception::class.java) {
            AdbIdentityBackupCodec.decrypt(
                encoded,
                binding("device-b"),
            )
        }
    }

    @Test
    fun `codec differentSigningCertificate rejectsBackup`() {
        val encoded = AdbIdentityBackupCodec.encrypt(
            newKeyPair(),
            binding("device-a"),
        )

        assertThrows(Exception::class.java) {
            AdbIdentityBackupCodec.decrypt(
                encoded,
                AdbIdentityBinding(deviceDigest("device-a"), ByteArray(32) { 7 }),
            )
        }
    }

    @Test
    fun `codec corruptedCiphertext rejectsBackup`() {
        val binding = binding("device-a")
        val encoded = AdbIdentityBackupCodec.encrypt(newKeyPair(), binding)
        val corrupted = Base64.getUrlDecoder().decode(encoded).also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }

        assertThrows(Exception::class.java) {
            AdbIdentityBackupCodec.decrypt(
                Base64.getUrlEncoder().withoutPadding().encodeToString(corrupted),
                binding,
            )
        }
    }

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }

    private fun binding(device: String): AdbIdentityBinding =
        AdbIdentityBinding(deviceDigest(device), signingDigest)

    private fun deviceDigest(device: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(device.toByteArray())
}
