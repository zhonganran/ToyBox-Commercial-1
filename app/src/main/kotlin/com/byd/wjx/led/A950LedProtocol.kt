package com.byd.wjx.led

import java.util.UUID

internal object A950LedProtocol : LedGattProtocol {
    override val kind = LedProtocolKind.A950
    override val serviceUuid: UUID = UUID.fromString("0000a950-0000-1000-8000-00805f9b34fb")
    override val writeUuid: UUID = UUID.fromString("0000a951-0000-1000-8000-00805f9b34fb")
    val LONG_WRITE_UUID: UUID = UUID.fromString("0000a952-0000-1000-8000-00805f9b34fb")
    override val notifyUuid: UUID = UUID.fromString("0000a953-0000-1000-8000-00805f9b34fb")
    override val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    override val requestedMtu = 517
    override val forceWriteWithoutResponse = true
    override val writeGapMs = 90L
    override val readyDelayMs = 200L
    override val keepaliveIntervalMs = 2_000L

    val prepareUploadPacket: ByteArray
        get() = command(command = 0x01, parameter1 = 0x08, parameter2 = 0x01)

    val initializePacket: ByteArray
        get() = command(command = 0x01, parameter1 = 0x01, parameter2 = 0x54)

    override val keepalivePacket: ByteArray
        get() = command(command = 0x81, parameter1 = 0x01, parameter2 = 0x01)

    override fun playPreset(presetId: Int): ByteArray = command(
        command = 0x01,
        parameter1 = 0x05,
        parameter2 = presetId,
    )

    override fun brightness(percent: Int): ByteArray = command(
        command = 0x01,
        parameter1 = 0x01,
        parameter2 = percent.coerceIn(0, 100),
    )

    override fun presetId(scene: LedScene): Int = when (scene) {
        LedScene.DEFAULT -> 5
        LedScene.LEFT_TURN -> 2
        LedScene.RIGHT_TURN -> 3
        LedScene.TEMPORARY_STOP -> 4
        LedScene.REVERSE_PARKING -> 10
        LedScene.AUTOPILOT -> 13
        LedScene.EXIT_VEHICLE -> 18
        LedScene.HAZARD -> 19
        LedScene.BRAKE_WARNING -> 0
    }

    fun command(command: Int, parameter1: Int, parameter2: Int): ByteArray {
        require(command in 0..0xFF) { "A950 command must fit in one byte" }
        require(parameter1 in 0..0xFF) { "A950 parameter1 must fit in one byte" }
        require(parameter2 in 0..0xFF) { "A950 parameter2 must fit in one byte" }
        val packet = byteArrayOf(
            0x4E,
            0x52,
            0x04,
            command.toByte(),
            parameter1.toByte(),
            parameter2.toByte(),
            0x00,
        )
        packet[6] = packet.take(6)
            .sumOf { it.toInt() and 0xFF }
            .toByte()
        return packet
    }

    override val initializationSteps: List<LedProtocolInitializationStep>
        get() = listOf(
            LedProtocolInitializationStep(prepareUploadPacket, "prepare 1/3", 200L),
            LedProtocolInitializationStep(prepareUploadPacket, "prepare 2/3", 500L),
            LedProtocolInitializationStep(prepareUploadPacket, "prepare 3/3", 500L),
            LedProtocolInitializationStep(initializePacket, "initialize", 800L),
        )
}
