package com.byd.wjx.data.autoservice

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ADB packet constants and little-endian frame encoding. */
internal object AdbWire {
    const val A_CNXN: Int = 0x4E584E43
    const val A_AUTH: Int = 0x48545541
    const val A_OPEN: Int = 0x4E45504F
    const val A_OKAY: Int = 0x59414B4F
    const val A_CLSE: Int = 0x45534C43
    const val A_WRTE: Int = 0x45545257

    const val A_VERSION_AUTH: Int = 0x01000001
    const val MAX_PAYLOAD: Int = 262_144
    const val AUTH_TOKEN: Int = 1
    const val AUTH_SIGNATURE: Int = 2
    const val AUTH_RSAPUBLICKEY: Int = 3

    val EMPTY: ByteArray = ByteArray(0)

    data class Packet(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray,
    )

    fun buildHeader(command: Int, arg0: Int, arg1: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payload.size)
        buffer.putInt(payload.sumOf { byte -> byte.toInt() and 0xFF })
        buffer.putInt(command.inv())
        return buffer.array()
    }

    fun write(output: OutputStream, command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        output.write(buildHeader(command, arg0, arg1, payload))
        if (payload.isNotEmpty()) output.write(payload)
        output.flush()
    }

    fun read(input: InputStream): Packet {
        val header = ByteBuffer.wrap(readExactly(input, HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        val arg0 = header.int
        val arg1 = header.int
        val payloadLength = header.int
        header.int
        header.int
        val payload = if (payloadLength > 0) readExactly(input, payloadLength) else EMPTY
        return Packet(command, arg0, arg1, payload)
    }

    private fun readExactly(input: InputStream, byteCount: Int): ByteArray {
        val bytes = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = input.read(bytes, offset, byteCount - offset)
            if (read < 0) throw IOException("EOF after $offset of $byteCount bytes")
            offset += read
        }
        return bytes
    }

    private const val HEADER_BYTES = 24
}
