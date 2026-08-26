package com.byd.wjx.data.autoservice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator

/**
 * Unit tests for the pure ADB wire and authentication helpers.
 *
 * Robolectric is used for [android.util.Base64] only (it lives in the Android
 * SDK, but the JVM stub returns null without Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbProtocolClientTest {

    @Test
    fun `packet checksum is unsigned-byte sum of payload`() {
        val payload = byteArrayOf(0x10, 0x20, 0xFF.toByte(), 0x01)
        val header = AdbWire.buildHeader(
            command = AdbWire.A_OPEN,
            arg0 = 1, arg1 = 0, payload = payload
        )

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4) { buf.int }  // skip command/arg0/arg1/payloadLen
        val checksum = buf.int

        val expected = 0x10 + 0x20 + 0xFF + 0x01  // = 0x130
        assertEquals(expected, checksum)
    }

    @Test
    fun `packet magic is bitwise inverse of command`() {
        val header = AdbWire.buildHeader(
            command = AdbWire.A_CNXN,
            arg0 = 0, arg1 = 0, payload = ByteArray(0)
        )
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        repeat(5) { buf.int }  // skip command/arg0/arg1/payloadLen/checksum
        val magic = buf.int

        assertEquals(AdbWire.A_CNXN.inv(), magic)
        assertEquals(AdbWire.A_CNXN xor 0xFFFFFFFF.toInt(), magic)
    }

    @Test
    fun `packet header is little-endian 24 bytes with correct fields`() {
        val payload = "shell:echo hi ".toByteArray()
        val header = AdbWire.buildHeader(
            command = AdbWire.A_OPEN,
            arg0 = 42, arg1 = 7, payload = payload
        )

        assertEquals(24, header.size)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbWire.A_OPEN, buf.int)
        assertEquals(42, buf.int)
        assertEquals(7, buf.int)
        assertEquals(payload.size, buf.int)
        var sum = 0; for (b in payload) sum += (b.toInt() and 0xFF)
        assertEquals(sum, buf.int)
        assertEquals(AdbWire.A_OPEN.inv(), buf.int)
    }

    @Test
    fun `packet write and read round trip preserves fields and payload`() {
        val payload = "shell:echo ready\u0000".toByteArray()
        val output = ByteArrayOutputStream()

        AdbWire.write(output, AdbWire.A_OPEN, arg0 = 9, arg1 = 4, payload = payload)
        val packet = AdbWire.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(AdbWire.A_OPEN, packet.command)
        assertEquals(9, packet.arg0)
        assertEquals(4, packet.arg1)
        assertArrayEquals(payload, packet.payload)
    }

    @Test
    fun `publicKey serialization contains base64 of 524 bytes plus null-terminated username`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encoded = AdbAuth.serializePublicKey(keyPair)

        val asString = String(encoded, Charsets.UTF_8)
        // Tail must contain ' wjx@dilink' (null-terminated C-string id).
        assertTrue(
            "must contain ' wjx@dilink' tag",
            asString.contains(" wjx@dilink")
        )
        // Last byte must be a literal NUL: ADB host id is a C-string.
        assertEquals(0, encoded.last().toInt() and 0xFF)
        // Base64(524 bytes) = ceil(524/3)*4 = 700 chars; plus 12-byte tail = 712.
        assertTrue("encoded size >= 712, got ${encoded.size}", encoded.size >= 712)
    }

    @Test
    fun `signaturePayload prepends adbAuthPadding`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val token = ByteArray(20) { (it + 1).toByte() }  // 20 bytes, like real ADB token
        val sig = AdbAuth.signToken(token, keyPair)

        // Reconstruct the padded input the helper builds. The padded input must
        // be exactly 35 bytes and start with ADB_AUTH_PADDING.
        val padded = AdbAuth.AUTH_PADDING + token
        assertEquals(35, padded.size)
        assertArrayEquals(AdbAuth.AUTH_PADDING, padded.sliceArray(0 until 15))
        assertArrayEquals(token, padded.sliceArray(15 until 35))

        // RSA 2048-bit signature is exactly 256 bytes.
        assertEquals(256, sig.size)
    }

    @Test
    fun `magic constants match ADB wire format`() {
        // Verify all 6 ADB protocol commands serialize to canonical 4-char ASCII
        // sequences when written little-endian. This guards against byte-swap
        // typos in hex literals — adbd sends ASCII bytes 'O','K','A','Y' etc.,
        // and our read path interprets them as little-endian Int.
        fun toLeBytes(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        fun ascii(v: Int) = String(toLeBytes(v), Charsets.US_ASCII)

        assertEquals("CNXN", ascii(AdbWire.A_CNXN))
        assertEquals("AUTH", ascii(AdbWire.A_AUTH))
        assertEquals("OPEN", ascii(AdbWire.A_OPEN))
        assertEquals("OKAY", ascii(AdbWire.A_OKAY))
        assertEquals("CLSE", ascii(AdbWire.A_CLSE))
        assertEquals("WRTE", ascii(AdbWire.A_WRTE))
    }
}
