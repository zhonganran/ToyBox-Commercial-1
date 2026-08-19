package com.byd.wjx.led

import java.util.UUID

internal data class LedProtocolInitializationStep(
    val packet: ByteArray,
    val label: String,
    val delayBeforeMs: Long,
)

internal interface LedGattProtocol {
    val kind: LedProtocolKind
    val serviceUuid: UUID
    val writeUuid: UUID
    val notifyUuid: UUID
    val cccdUuid: UUID
    val requestedMtu: Int? get() = null
    val forceWriteWithoutResponse: Boolean get() = false
    val requiresNotifications: Boolean get() = false
    val maxWriteChunkSize: Int? get() = null
    val writeGapMs: Long
    val initializationSteps: List<LedProtocolInitializationStep> get() = emptyList()
    val readyDelayMs: Long get() = 0L
    val keepalivePacket: ByteArray? get() = null
    val keepaliveIntervalMs: Long? get() = null
    val sceneRefreshIntervalMs: Long? get() = null

    fun playPreset(presetId: Int): ByteArray
    fun playScene(scene: LedScene, presetIdOverride: Int? = null): ByteArray =
        playPreset(presetIdOverride ?: presetId(scene))
    fun presetId(scene: LedScene): Int
    fun brightness(percent: Int): ByteArray

    fun splitForWrite(packet: ByteArray): List<ByteArray> {
        val chunkSize = maxWriteChunkSize ?: return listOf(packet)
        require(chunkSize > 0) { "LED write chunk size must be positive" }
        return packet.indices.step(chunkSize).map { offset ->
            packet.copyOfRange(offset, minOf(offset + chunkSize, packet.size))
        }
    }
}

internal object IPixelFaLedProtocol : LedGattProtocol {
    override val kind = LedProtocolKind.IPIXEL_FA
    override val serviceUuid: UUID = UUID.fromString("000000fa-0000-1000-8000-00805f9b34fb")
    override val writeUuid: UUID = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb")
    override val notifyUuid: UUID = UUID.fromString("0000fa03-0000-1000-8000-00805f9b34fb")
    override val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    override val writeGapMs = 70L

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

    override fun playPreset(presetId: Int): ByteArray = playRemotePreset(presetId)

    override fun presetId(scene: LedScene): Int = when (scene) {
        LedScene.DEFAULT -> 6
        LedScene.LEFT_TURN -> 3
        LedScene.RIGHT_TURN -> 5
        LedScene.TEMPORARY_STOP -> 4
        LedScene.REVERSE_PARKING -> 11
        LedScene.AUTOPILOT -> 14
        LedScene.EXIT_VEHICLE -> 19
        LedScene.HAZARD -> 20
        LedScene.BRAKE_WARNING -> 8
    }

    override fun brightness(percent: Int): ByteArray = byteArrayOf(
        0x05,
        0x00,
        0x04,
        0x80.toByte(),
        percent.coerceIn(0, 100).toByte(),
    )

    private const val LANGUAGE_ENGLISH = 0
    private const val LANGUAGE_CHINESE = 1
}

internal object LedProtocols {
    fun forKind(kind: LedProtocolKind): LedGattProtocol = when (kind) {
        LedProtocolKind.IPIXEL_FA -> IPixelFaLedProtocol
        LedProtocolKind.A950 -> A950LedProtocol
        LedProtocolKind.DUDU_FFF0 -> DuduFff0LedProtocol
    }
}

internal fun detectLedProtocol(
    expected: LedProtocolKind?,
    available: Set<LedProtocolKind>,
): LedProtocolKind? = when {
    expected != null && expected in available -> expected
    available.size == 1 -> available.first()
    else -> null
}
