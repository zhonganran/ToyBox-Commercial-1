package com.byd.wjx.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuduFff0NotificationDecoderTest {
    @Test
    fun `fragmented response confirms a matching device command`() {
        val decoder = DuduFff0NotificationDecoder()
        val response = response(
            command = DuduFff0LedProtocol.COMMAND_POWER,
            payload = byteArrayOf(0),
        )

        assertTrue(decoder.append(byteArrayOf(0x44) + response.copyOfRange(0, 2)).isEmpty())
        assertTrue(decoder.append(response.copyOfRange(2, 9)).isEmpty())
        val decoded = decoder.append(response.copyOfRange(9, response.size)).single()

        assertEquals(DuduFff0LedProtocol.COMMAND_POWER, decoded.command)
        assertTrue(decoded.isSuccessAcknowledgement)
    }

    @Test
    fun `concatenated scene response reports installed and empty slots`() {
        val decoder = DuduFff0NotificationDecoder()
        val scenePayload = byteArrayOf(0x35, 0x02) +
            sceneEntry(slotIndex = 0, installed = true) +
            sceneEntry(slotIndex = 1, installed = false)
        val sceneResponse = response(DuduFff0LedProtocol.COMMAND_QUERY_SCENES, scenePayload)
        val acknowledgement = response(DuduFff0LedProtocol.COMMAND_SHOW_SCENE, byteArrayOf(0))

        val decoded = decoder.append(sceneResponse + acknowledgement)

        assertEquals(2, decoded.size)
        assertEquals(mapOf(1 to true, 2 to false), decoded.first().sceneSlots())
        assertTrue(decoded.last().isSuccessAcknowledgement)
    }

    @Test
    fun `bad checksum is discarded without losing the following frame`() {
        val decoder = DuduFff0NotificationDecoder()
        val invalid = response(DuduFff0LedProtocol.COMMAND_POWER, byteArrayOf(0)).also {
            it[it.lastIndex] = (it.last() + 1).toByte()
        }
        val valid = response(DuduFff0LedProtocol.COMMAND_BRIGHTNESS, byteArrayOf(0))

        val decoded = decoder.append(invalid + valid)

        assertEquals(1, decoded.size)
        assertEquals(DuduFff0LedProtocol.COMMAND_BRIGHTNESS, decoded.single().command)
    }

    @Test
    fun `scene response metadata variants keep the slot entry aligned`() {
        val decoder = DuduFff0NotificationDecoder()
        val payload = byteArrayOf(0x35, 0x81.toByte(), 0x00) + sceneEntry(slotIndex = 59, installed = true)

        val decoded = decoder.append(response(DuduFff0LedProtocol.COMMAND_QUERY_SCENES, payload)).single()

        assertEquals(mapOf(60 to true), decoded.sceneSlots())
    }

    private fun sceneEntry(slotIndex: Int, installed: Boolean): ByteArray =
        byteArrayOf(slotIndex.toByte()) + ByteArray(20).also { if (installed) it[0] = 1 }

    private fun response(command: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(payload.size + 12)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x55
        frame[2] = 0xFF.toByte()
        frame[3] = 0xFF.toByte()
        writeLittleEndian(frame, 4, payload.size + 6)
        writeLittleEndian(frame, 6, command)
        frame[8] = 0xC1.toByte()
        frame[9] = 0x01
        payload.copyInto(frame, destinationOffset = 10)
        val checksum = (0 until frame.size - 2).sumOf { frame[it].toInt() and 0xFF }
        writeLittleEndian(frame, frame.size - 2, checksum)
        return frame
    }

    private fun writeLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value shr 8).toByte()
    }
}
