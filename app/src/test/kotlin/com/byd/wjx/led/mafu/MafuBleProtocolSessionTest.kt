package com.byd.wjx.led.mafu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MafuBleProtocolSessionTest {
    @Test
    fun `notification decoder keeps a fragmented frame until it is complete`() {
        val decoder = MafuNotificationFrameDecoder()
        val packet = response(sequence = 7, accepted = true)

        assertTrue(decoder.append(packet.copyOfRange(0, 3)).isEmpty())
        assertTrue(decoder.append(packet.copyOfRange(3, 9)).isEmpty())
        val decoded = decoder.append(packet.copyOfRange(9, packet.size))

        assertEquals(1, decoded.size)
        assertArrayEquals(packet, decoded.single())
    }

    @Test
    fun `notification decoder separates sticky frames and preserves split magic`() {
        val decoder = MafuNotificationFrameDecoder()
        val first = response(sequence = 1, accepted = true)
        val second = response(sequence = 2, accepted = false)
        val third = response(sequence = 3, accepted = true)

        val decoded = decoder.append(
            byteArrayOf(0x01, 0x02) + first + second + byteArrayOf(0xAA.toByte()),
        )

        assertEquals(2, decoded.size)
        assertArrayEquals(first, decoded[0])
        assertArrayEquals(second, decoded[1])
        val completedThird = decoder.append(byteArrayOf(0x55) + third.copyOfRange(2, third.size))
        assertEquals(1, completedThird.size)
        assertArrayEquals(third, completedThird.single())
    }

    @Test
    fun `notification decoder skips an invalid length and resynchronizes on the next frame`() {
        val decoder = MafuNotificationFrameDecoder()
        val invalidHeader = byteArrayOf(
            0xAA.toByte(), 0x55, 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x01, 0x00, 0x81.toByte(), 0x82.toByte(),
        )
        val valid = response(sequence = 9, accepted = true)

        val decoded = decoder.append(invalidHeader + valid)

        assertEquals(1, decoded.size)
        assertArrayEquals(valid, decoded.single())
    }

    @Test
    fun `notification decoder reset discards a partial connection buffer`() {
        val decoder = MafuNotificationFrameDecoder()
        val oldPacket = response(sequence = 4, accepted = true)
        val newPacket = response(sequence = 5, accepted = true)
        assertTrue(decoder.append(oldPacket.copyOfRange(0, 8)).isEmpty())

        decoder.reset()

        val decoded = decoder.append(newPacket)
        assertEquals(1, decoded.size)
        assertArrayEquals(newPacket, decoded.single())
    }

    @Test
    fun `sequence generator wraps from unsigned short max back to one`() {
        val sequence = MafuSequenceGenerator(initial = 0xFFFF)

        assertEquals(0xFFFF, sequence.take())
        assertEquals(1, sequence.take())
        assertEquals(2, sequence.take())
    }

    private fun response(sequence: Int, accepted: Boolean): ByteArray {
        val packet = ByteArray(13)
        packet[0] = 0xAA.toByte()
        packet[1] = 0x55
        packet[2] = 0xFF.toByte()
        packet[3] = 0xFF.toByte()
        writeLittleEndian(packet, 4, 7)
        writeLittleEndian(packet, 6, sequence)
        packet[8] = 0x81.toByte()
        packet[9] = 0x82.toByte()
        packet[10] = if (accepted) 0 else 3
        val checksum = packet.dropLast(2).sumOf { it.toInt() and 0xFF } and 0xFFFF
        writeLittleEndian(packet, packet.size - 2, checksum)
        return packet
    }

    private fun writeLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value shr 8).toByte()
    }
}
