package com.byd.wjx.led.a950

import java.util.UUID

internal data class A950InitializationStep(
    val packet: ByteArray,
    val label: String,
    val delayBeforeMs: Long,
)

internal object A950Protocol {
    val serviceUuid: UUID = UUID.fromString("0000a950-0000-1000-8000-00805f9b34fb")
    val writeUuid: UUID = UUID.fromString("0000a951-0000-1000-8000-00805f9b34fb")
    val longWriteUuid: UUID = UUID.fromString("0000a952-0000-1000-8000-00805f9b34fb")
    val notifyUuid: UUID = UUID.fromString("0000a953-0000-1000-8000-00805f9b34fb")

    const val REQUESTED_MTU = 517
    const val WRITE_GAP_MS = 90L
    const val READY_DELAY_MS = 200L
    const val KEEPALIVE_INTERVAL_MS = 2_000L

    val prepareUploadPacket: ByteArray
        get() = command(command = 0x01, parameter1 = 0x08, parameter2 = 0x01)

    val initializePacket: ByteArray
        get() = command(command = 0x01, parameter1 = 0x01, parameter2 = 0x54)

    val keepalivePacket: ByteArray
        get() = command(command = 0x81, parameter1 = 0x01, parameter2 = 0x01)

    val initializationSteps: List<A950InitializationStep>
        get() = listOf(
            A950InitializationStep(prepareUploadPacket, "prepare 1/3", 200L),
            A950InitializationStep(prepareUploadPacket, "prepare 2/3", 500L),
            A950InitializationStep(prepareUploadPacket, "prepare 3/3", 500L),
            A950InitializationStep(initializePacket, "initialize", 800L),
        )

    fun acceptsGattProfile(
        serviceUuid: UUID?,
        characteristicUuids: Set<UUID>,
    ): Boolean = serviceUuid == this.serviceUuid && writeUuid in characteristicUuids

    fun playPreset(preset: A950Preset): ByteArray = remoteControl(
        commandIndex = preset.commandCode,
        value = 0x00,
    )

    val enableRemoteControlPacket: ByteArray
        get() = remoteControl(commandIndex = 0xFF, value = 0x01)

    val resumeProgramListPacket: ByteArray
        get() = remoteControl(commandIndex = 0xFF, value = 0x00)

    fun brightness(percent: Int): ByteArray = command(
        command = 0x01,
        parameter1 = 0x01,
        parameter2 = percent.coerceIn(0, 100),
    )

    private fun remoteControl(commandIndex: Int, value: Int): ByteArray {
        require(commandIndex in 0..0xFF) { "A950 remote command index must fit in one byte" }
        require(value in 0..0xFF) { "A950 remote command value must fit in one byte" }
        return checksumPacket(
            0x4E,
            0x52,
            0x04,
            0x01,
            0x05,
            commandIndex,
            value,
        )
    }

    fun command(command: Int, parameter1: Int, parameter2: Int): ByteArray {
        require(command in 0..0xFF) { "A950 command must fit in one byte" }
        require(parameter1 in 0..0xFF) { "A950 parameter1 must fit in one byte" }
        require(parameter2 in 0..0xFF) { "A950 parameter2 must fit in one byte" }
        return checksumPacket(
            0x4E,
            0x52,
            0x04,
            command,
            parameter1,
            parameter2,
        )
    }

    private fun checksumPacket(vararg values: Int): ByteArray {
        val packet = ByteArray(values.size + 1)
        values.forEachIndexed { index, value ->
            require(value in 0..0xFF) { "A950 packet value must fit in one byte" }
            packet[index] = value.toByte()
        }
        packet[packet.lastIndex] = values.sum().toByte()
        return packet
    }
}
