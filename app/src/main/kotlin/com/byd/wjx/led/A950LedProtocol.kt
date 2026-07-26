package com.byd.wjx.led

import java.util.UUID

object A950LedProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000a950-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("0000a951-0000-1000-8000-00805f9b34fb")
    val LONG_WRITE_UUID: UUID = UUID.fromString("0000a952-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000a953-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val prepareUploadPacket: ByteArray
        get() = command(command = 0x01, parameter1 = 0x08, parameter2 = 0x01)

    val initializePacket: ByteArray
        get() = command(command = 0x01, parameter1 = 0x01, parameter2 = 0x54)

    val keepalivePacket: ByteArray
        get() = command(command = 0x81, parameter1 = 0x01, parameter2 = 0x01)

    fun playScene(scene: LedScene): ByteArray = command(
        command = 0x01,
        parameter1 = 0x05,
        parameter2 = presetId(scene),
    )

    fun brightness(percent: Int): ByteArray = command(
        command = 0x01,
        parameter1 = 0x01,
        parameter2 = percent.coerceIn(0, 100),
    )

    fun presetId(scene: LedScene): Int = when (scene) {
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

    const val REQUESTED_MTU = 517
    const val KEEPALIVE_INTERVAL_MS = 2_000L
}
