package com.byd.wjx.led.mafu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MafuProtocolTest {
    @Test
    fun `UUID directions and scan prefixes match the Mafu APK`() {
        assertEquals("0000fff0-0000-1000-8000-00805f9b34fb", MafuProtocol.serviceUuid.toString())
        assertEquals("0000fff1-0000-1000-8000-00805f9b34fb", MafuProtocol.notifyUuid.toString())
        assertEquals("0000fff2-0000-1000-8000-00805f9b34fb", MafuProtocol.writeUuid.toString())
        assertTrue(MafuProtocol.acceptsDeviceName("YS-64X32"))
        assertTrue(MafuProtocol.acceptsDeviceName("tl-led"))
        assertFalse(MafuProtocol.acceptsDeviceName("VIVID LED"))
    }

    @Test
    fun `brightness uses native level TLV without the transport length prefix`() {
        assertArrayEquals(
            hex("AA 55 FF FF 0A 00 34 12 C1 02 06 02 00 0C 24 04"),
            MafuProtocol.brightness(level = 12, sequence = 0x1234),
        )
        assertArrayEquals(
            hex("AA 55 FF FF 0A 00 01 00 C1 02 06 02 00 0F E2 03"),
            MafuProtocol.brightness(level = 99, sequence = 1),
        )
    }

    @Test
    fun `remote models map keys to the APK program slots`() {
        assertEquals(76..92, MafuRemoteModel.YS_17_KEY.programRange)
        assertEquals(76..95, MafuRemoteModel.YS_20_KEY.programRange)
        assertEquals(87..95, MafuRemoteModel.YS_9_KEY.programRange)
        assertEquals(76, MafuRemoteModel.YS_17_KEY.programId(MafuRemoteKey.KEY_1))
        assertEquals(95, MafuRemoteModel.YS_20_KEY.programId(MafuRemoteKey.KEY_20))
        assertEquals(95, MafuRemoteModel.YS_9_KEY.programId(MafuRemoteKey.KEY_9))

        assertArrayEquals(
            hex("AA 55 FF FF 0C 00 01 00 C1 02 18 04 02 4B 00 FF 35 05"),
            MafuProtocol.playRemoteKey(
                MafuRemoteModel.YS_17_KEY,
                MafuRemoteKey.KEY_1,
                sequence = 1,
            ),
        )
    }

    @Test
    fun `remote query encodes model code in big endian`() {
        assertArrayEquals(
            hex("AA 55 FF FF 0B 00 02 00 C1 02 34 03 04 AA BF 71 05"),
            MafuProtocol.queryRemote(MafuRemoteModel.YS_17_KEY, sequence = 2),
        )
    }

    @Test
    fun `initial device query and remote binding match APK TLVs`() {
        assertArrayEquals(
            hex("AA 55 FF FF 0E 00 03 00 C1 02 0A 00 1B 00 04 00 06 00 00 04"),
            MafuProtocol.queryDeviceInfoAll(sequence = 3),
        )
        assertArrayEquals(
            hex("AA 55 FF FF 0C 00 04 00 C1 02 34 04 04 AB BF 01 77 05"),
            MafuProtocol.setRemoteBinding(
                model = MafuRemoteModel.YS_20_KEY,
                bindState = 1,
                sequence = 4,
            ),
        )
    }

    @Test
    fun `fixed vehicle linkage slots match the APK`() {
        assertEquals(
            listOf(67, 68, 69, 70, 71),
            MafuVehicleLinkageSlot.entries.map(MafuVehicleLinkageSlot::programId),
        )
        assertEquals(
            listOf(59, 60, 61, 62, 63),
            MafuVehicleLinkageSlot.entries.map(MafuVehicleLinkageSlot::materialTag),
        )
    }

    @Test
    fun `ACK parser validates sequence status and checksum`() {
        val accepted = response(sequence = 0x1234, responseType = 0x82, payload = byteArrayOf(0))
        val rejected = response(sequence = 7, responseType = 0x82, payload = byteArrayOf(3))

        assertEquals(MafuAcknowledgement(0x1234, true, 0x82), MafuProtocol.parseAcknowledgement(accepted))
        assertEquals(MafuAcknowledgement(7, false, 0x82), MafuProtocol.parseAcknowledgement(rejected))
        assertNotNull(MafuProtocol.parseAcknowledgement(response(8, 0x83, byteArrayOf(0x06, 0x02))))

        accepted[accepted.lastIndex] = (accepted.last() + 1).toByte()
        assertNull(MafuProtocol.parseAcknowledgement(accepted))
    }

    private fun response(sequence: Int, responseType: Int, payload: ByteArray): ByteArray {
        val packet = ByteArray(10 + payload.size + 2)
        packet[0] = 0xAA.toByte()
        packet[1] = 0x55
        packet[2] = 0xFF.toByte()
        packet[3] = 0xFF.toByte()
        writeLittleEndian(packet, 4, payload.size + 6)
        writeLittleEndian(packet, 6, sequence)
        packet[8] = 0x81.toByte()
        packet[9] = responseType.toByte()
        payload.copyInto(packet, 10)
        val checksum = packet.dropLast(2).sumOf { it.toInt() and 0xFF } and 0xFFFF
        writeLittleEndian(packet, packet.size - 2, checksum)
        return packet
    }

    private fun writeLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value shr 8).toByte()
    }

    private fun hex(value: String): ByteArray = value
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
