package com.byd.wjx.led.ipixelfa

import java.util.UUID

internal object IpixelFaProtocol {
    val serviceUuid: UUID = UUID.fromString("000000fa-0000-1000-8000-00805f9b34fb")
    val writeUuid: UUID = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb")
    val notifyUuid: UUID = UUID.fromString("0000fa03-0000-1000-8000-00805f9b34fb")
    const val WRITE_GAP_MS = 70L
    const val REMOTE_PRESET_REFRESH_MS = 9_000L

    fun acceptsGattProfile(
        serviceUuid: UUID?,
        characteristicUuids: Set<UUID>,
    ): Boolean = serviceUuid == this.serviceUuid && writeUuid in characteristicUuids

    fun playPreset(preset: IpixelFaPreset): ByteArray = playRemotePreset(preset.presetId)

    /** Immediately enter program mode and rotate through every stored FA program buffer. */
    fun playProgramList(): ByteArray {
        val slots = (MIN_PROGRAM_SLOT..MAX_PROGRAM_SLOT).map(Int::toByte)
        return byteArrayOf(
            (PROGRAM_MODE_HEADER_SIZE + slots.size).toByte(),
            0x00,
            0x08,
            0x80.toByte(),
            slots.size.toByte(),
            0x00,
            *slots.toByteArray(),
        )
    }

    fun playRemotePreset(presetId: Int, language: Int = LANGUAGE_CHINESE): ByteArray {
        require(presetId in 1..20) { "FA remote preset must be in 1..20" }
        require(language == LANGUAGE_ENGLISH || language == LANGUAGE_CHINESE) {
            "FA remote language must be 0 (English) or 1 (Chinese)"
        }
        return byteArrayOf(
            0x06,
            0x00,
            0x07,
            0x80.toByte(),
            presetId.toByte(),
            language.toByte(),
        )
    }

    fun brightness(percent: Int): ByteArray = byteArrayOf(
        0x05,
        0x00,
        0x04,
        0x80.toByte(),
        percent.coerceIn(0, 100).toByte(),
    )

    private const val LANGUAGE_ENGLISH = 0
    private const val LANGUAGE_CHINESE = 1
    private const val PROGRAM_MODE_HEADER_SIZE = 6
    private const val MIN_PROGRAM_SLOT = 1
    private const val MAX_PROGRAM_SLOT = 9
}
