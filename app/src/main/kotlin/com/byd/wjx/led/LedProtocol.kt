package com.byd.wjx.led

import java.util.UUID

object LedProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("000000fa-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000fa03-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Selects one of the 20 firmware-resident iPixel remote-control presets. */
    fun playRemotePreset(presetId: Int, language: Int = LANGUAGE_CHINESE): ByteArray {
        require(presetId in 1..20) { "LED remote preset must be in 1..20" }
        require(language == LANGUAGE_ENGLISH || language == LANGUAGE_CHINESE) {
            "LED remote language must be 0 (English) or 1 (Chinese)"
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

    fun playScene(scene: LedScene): ByteArray = playRemotePreset(scene.remotePresetId)

    fun brightness(percent: Int): ByteArray = byteArrayOf(
        0x05,
        0x00,
        0x04,
        0x80.toByte(),
        percent.coerceIn(0, 100).toByte(),
    )

    private const val LANGUAGE_ENGLISH = 0
    private const val LANGUAGE_CHINESE = 1
}
