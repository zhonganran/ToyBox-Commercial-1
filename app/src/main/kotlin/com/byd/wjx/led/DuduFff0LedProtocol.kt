package com.byd.wjx.led

import java.util.UUID

internal object DuduFff0LedProtocol : LedGattProtocol {
    override val kind = LedProtocolKind.DUDU_FFF0
    override val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    override val writeUuid: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    override val notifyUuid: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    override val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    override val maxWriteChunkSize = 20
    override val writeGapMs = 25L
    override val readyDelayMs = 100L
    override val sceneRefreshIntervalMs = 60_000L
    override val requiresNotifications = true

    override val initializationSteps: List<LedProtocolInitializationStep>
        get() = listOf(
            LedProtocolInitializationStep(power(enabled = true), "power on", 0L),
        )

    override fun playPreset(presetId: Int): ByteArray {
        require(presetId in SCENE_SLOT_RANGE) { "FFF0 scene slot must be in 1..60" }
        return frame(
            command = COMMAND_SHOW_SCENE,
            request = true,
            payload = byteArrayOf(
                0x18,
                0x06,
                0x02,
                presetId.toByte(),
                0x00,
                0x01,
                0xF0.toByte(),
                0x00,
            ),
        )
    }

    override fun presetId(scene: LedScene): Int = DEFAULT_SCENE_SLOT

    override fun brightness(percent: Int): ByteArray {
        val level = ((percent.coerceIn(0, 100) * MAX_BRIGHTNESS_LEVEL) + 50) / 100
        return frame(
            command = COMMAND_BRIGHTNESS,
            request = true,
            payload = byteArrayOf(0x06, 0x02, 0x00, level.toByte()),
        )
    }

    fun power(enabled: Boolean): ByteArray = frame(
        command = COMMAND_POWER,
        request = true,
        payload = byteArrayOf(0x04, 0x02, 0x00, if (enabled) 0x01 else 0x00),
    )

    fun querySceneSlots(slots: List<Int>): ByteArray {
        require(slots.isNotEmpty() && slots.size <= MAX_QUERY_SLOTS) {
            "FFF0 scene query must contain 1..8 slots"
        }
        require(slots.all { it in SCENE_SLOT_RANGE }) { "FFF0 scene slot must be in 1..60" }
        val payload = ByteArray(slots.size + 2)
        payload[0] = 0x35
        payload[1] = slots.size.toByte()
        slots.forEachIndexed { index, slot -> payload[index + 2] = (slot - 1).toByte() }
        return frame(command = COMMAND_QUERY_SCENES, request = false, payload = payload)
    }

    fun deviceAcknowledgementCommand(packet: ByteArray): Int? {
        if (packet.size < MIN_REQUEST_FRAME_SIZE || packet[2] != 0xAA.toByte() || packet[3] != 0x55.toByte()) {
            return null
        }
        if (packet[10] != 0xC1.toByte() || packet[11] != REQUEST_TYPE) return null
        return readLittleEndian(packet, 8)
    }

    private fun frame(command: Int, request: Boolean, payload: ByteArray): ByteArray {
        val body = ByteArray(payload.size + FRAME_BODY_OVERHEAD)
        writeLittleEndian(body, 0, payload.size + FRAME_REMAINING_OVERHEAD)
        body[2] = 0xAA.toByte()
        body[3] = 0x55
        body[4] = 0xFF.toByte()
        body[5] = 0xFF.toByte()
        writeLittleEndian(body, 6, payload.size + INNER_FRAME_OVERHEAD)
        writeLittleEndian(body, 8, command)
        body[10] = 0xC1.toByte()
        body[11] = if (request) 0x02 else 0x03
        payload.copyInto(body, destinationOffset = 12)

        val checksum = (2 until body.size).sumOf { body[it].toInt() and 0xFF }
        return body + byteArrayOf(checksum.toByte(), (checksum shr 8).toByte())
    }

    private fun writeLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value shr 8).toByte()
    }

    private fun readLittleEndian(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)

    const val DEFAULT_SCENE_SLOT = 1
    val SCENE_SLOT_RANGE = 1..60

    private const val MAX_BRIGHTNESS_LEVEL = 15
    private const val MAX_QUERY_SLOTS = 8
    internal const val COMMAND_SHOW_SCENE = 0x000A
    internal const val COMMAND_POWER = 0x000B
    internal const val COMMAND_BRIGHTNESS = 0x000C
    internal const val COMMAND_QUERY_SCENES = 0x000C
    private val REQUEST_TYPE = 0x02.toByte()
    private const val MIN_REQUEST_FRAME_SIZE = 14
    private const val FRAME_BODY_OVERHEAD = 12
    private const val FRAME_REMAINING_OVERHEAD = 12
    private const val INNER_FRAME_OVERHEAD = 6
}
