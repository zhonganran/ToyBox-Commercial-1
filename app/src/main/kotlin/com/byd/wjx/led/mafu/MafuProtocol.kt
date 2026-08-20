package com.byd.wjx.led.mafu

import java.util.Calendar
import java.util.UUID

internal data class MafuAcknowledgement(
    val sequence: Int,
    val accepted: Boolean,
    val responseType: Int,
)

internal object MafuProtocol {
    val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val notifyUuid: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val writeUuid: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val REQUESTED_MTU = 185
    const val MAX_WRITE_CHUNK_SIZE = 180
    const val WRITE_GAP_MS = 10L
    const val CONNECTION_SETTLE_BEFORE_NOTIFY_MS = 1_500L
    const val NOTIFY_SETTLE_BEFORE_INIT_MS = 20L
    const val ACK_TIMEOUT_MS = 5_000L

    fun acceptsDeviceName(name: String): Boolean =
        DEVICE_NAME_PREFIXES.any { name.startsWith(it, ignoreCase = true) }

    fun acceptsGattProfile(
        serviceUuid: UUID?,
        characteristicUuids: Set<UUID>,
    ): Boolean = serviceUuid == this.serviceUuid &&
        writeUuid in characteristicUuids &&
        notifyUuid in characteristicUuids

    fun brightness(level: Int, sequence: Int): ByteArray = frame(
        sequence = sequence,
        payload = byteArrayOf(0x06, 0x02, 0x00, level.coerceIn(1, 15).toByte()),
    )

    /** Controls the LED panel power state; this is the APK's `cmd.power` command. */
    fun screenPower(enabled: Boolean, sequence: Int): ByteArray = frame(
        sequence = sequence,
        payload = byteArrayOf(0x04, 0x02, 0x00, if (enabled) 1 else 0),
    )

    fun playRemoteKey(
        model: MafuRemoteModel,
        key: MafuRemoteKey,
        sequence: Int,
    ): ByteArray = playProgram(model.programId(key), sequence)

    fun playVehicleLinkage(slot: MafuVehicleLinkageSlot, sequence: Int): ByteArray =
        playProgram(slot.programId, sequence)

    fun playProgram(programId: Int, sequence: Int): ByteArray {
        require(programId in 1..255) { "马夫节目槽位必须在 1..255" }
        return frame(
            sequence = sequence,
            payload = byteArrayOf(
                0x36,
                0x03,
                0x01,
                0x00,
                (programId - 1).toByte(),
            ),
        )
    }

    fun queryDeviceInfoAll(sequence: Int): ByteArray = frame(
        sequence = sequence,
        requestType = REQUEST_TYPE_GET,
        payload = byteArrayOf(0x0A, 0x00, 0x1B, 0x00, 0x04, 0x00, 0x06, 0x00),
    )

    fun syncTime(
        sequence: Int,
        calendar: Calendar = Calendar.getInstance(),
    ): ByteArray {
        val year = (calendar.get(Calendar.YEAR) - 2000).coerceIn(0, 0xFFFF)
        val milliseconds = calendar.get(Calendar.MILLISECOND).coerceIn(0, 0xFFFF)
        return frame(
            sequence = sequence,
            payload = byteArrayOf(
                0x05,
                0x09,
                year.toByte(),
                (year shr 8).toByte(),
                (calendar.get(Calendar.MONTH) + 1).toByte(),
                calendar.get(Calendar.DAY_OF_MONTH).toByte(),
                calendar.get(Calendar.HOUR_OF_DAY).toByte(),
                calendar.get(Calendar.MINUTE).toByte(),
                calendar.get(Calendar.SECOND).toByte(),
                milliseconds.toByte(),
                (milliseconds shr 8).toByte(),
            ),
        )
    }

    fun queryRemote(model: MafuRemoteModel, sequence: Int): ByteArray = frame(
        sequence = sequence,
        requestType = REQUEST_TYPE_GET,
        payload = byteArrayOf(
            0x34,
            0x03,
            0x04,
            (model.modelCode shr 8).toByte(),
            model.modelCode.toByte(),
        ),
    )

    fun setRemoteBinding(
        model: MafuRemoteModel,
        bindState: Int,
        sequence: Int,
    ): ByteArray {
        require(bindState in 0..2) { "马夫遥控器绑定状态必须在 0..2" }
        return frame(
            sequence = sequence,
            payload = byteArrayOf(
                0x34,
                0x04,
                0x04,
                (model.modelCode shr 8).toByte(),
                model.modelCode.toByte(),
                bindState.toByte(),
            ),
        )
    }

    fun splitForWrite(packet: ByteArray): List<ByteArray> =
        packet.indices.step(MAX_WRITE_CHUNK_SIZE).map { offset ->
            packet.copyOfRange(offset, minOf(offset + MAX_WRITE_CHUNK_SIZE, packet.size))
        }

    fun packetSize(buffer: ByteArray, offset: Int = 0): Int? {
        if (buffer.size - offset < HEADER_SIZE) return null
        if (buffer[offset] != MAGIC_LOW || buffer[offset + 1] != MAGIC_HIGH) return null
        val encodedLength = readLittleEndian(buffer, offset + 4)
        val total = encodedLength + LENGTH_FIELD_OVERHEAD
        return total.takeIf { it in MIN_PACKET_SIZE..MAX_PACKET_SIZE }
    }

    fun parseAcknowledgement(packet: ByteArray): MafuAcknowledgement? {
        val size = packetSize(packet) ?: return null
        if (packet.size != size) return null
        val includesChecksum = packet[8].toInt() and CHECKSUM_FLAG != 0
        if (includesChecksum && !hasValidChecksum(packet)) return null
        val sequence = readLittleEndian(packet, 6)
        val responseType = packet[9].toInt() and 0xFF
        val accepted = when (responseType) {
            RESPONSE_ACK, RESPONSE_ACK_ALT -> packet.size > HEADER_SIZE && packet[10].toInt() == 0
            RESPONSE_DATA, RESPONSE_UPLOAD -> true
            else -> return null
        }
        return MafuAcknowledgement(sequence, accepted, responseType)
    }

    fun hasValidChecksum(packet: ByteArray): Boolean {
        if (packet.size < MIN_PACKET_SIZE) return false
        val expected = readLittleEndian(packet, packet.size - CHECKSUM_SIZE)
        val actual = packet.dropLast(CHECKSUM_SIZE).sumOf { it.toInt() and 0xFF } and 0xFFFF
        return expected == actual
    }

    private fun frame(
        sequence: Int,
        payload: ByteArray,
        requestType: Byte = REQUEST_TYPE_SET,
    ): ByteArray {
        require(sequence in 0..0xFFFF) { "马夫协议序号必须在 0..65535" }
        val packet = ByteArray(HEADER_SIZE + payload.size + CHECKSUM_SIZE)
        packet[0] = MAGIC_LOW
        packet[1] = MAGIC_HIGH
        packet[2] = 0xFF.toByte()
        packet[3] = 0xFF.toByte()
        writeLittleEndian(packet, 4, payload.size + INNER_LENGTH_OVERHEAD)
        writeLittleEndian(packet, 6, sequence)
        packet[8] = REQUEST_FLAGS
        packet[9] = requestType
        payload.copyInto(packet, destinationOffset = HEADER_SIZE)
        val checksum = packet.dropLast(CHECKSUM_SIZE).sumOf { it.toInt() and 0xFF } and 0xFFFF
        writeLittleEndian(packet, packet.size - CHECKSUM_SIZE, checksum)
        return packet
    }

    private fun writeLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value shr 8).toByte()
    }

    private fun readLittleEndian(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)

    private val DEVICE_NAME_PREFIXES = listOf("YS", "TL")
    private val MAGIC_LOW = 0xAA.toByte()
    private val MAGIC_HIGH = 0x55.toByte()
    private val REQUEST_FLAGS = 0xC1.toByte()
    private val REQUEST_TYPE_SET = 0x02.toByte()
    private val REQUEST_TYPE_GET = 0x03.toByte()
    private const val CHECKSUM_FLAG = 0x80
    private const val RESPONSE_UPLOAD = 0x06
    private const val RESPONSE_ACK = 0x82
    private const val RESPONSE_DATA = 0x83
    private const val RESPONSE_ACK_ALT = 0x84
    private const val HEADER_SIZE = 10
    private const val CHECKSUM_SIZE = 2
    private const val INNER_LENGTH_OVERHEAD = 6
    private const val LENGTH_FIELD_OVERHEAD = 6
    private const val MIN_PACKET_SIZE = 10
    private const val MAX_PACKET_SIZE = 4_096
}
