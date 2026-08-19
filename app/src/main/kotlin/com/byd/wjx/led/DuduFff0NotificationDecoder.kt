package com.byd.wjx.led

internal data class DuduFff0Response(
    val command: Int,
    val payload: ByteArray,
) {
    val isSuccessAcknowledgement: Boolean
        get() = payload.firstOrNull() == 0.toByte()

    fun sceneSlots(): Map<Int, Boolean>? {
        if (command != DuduFff0LedProtocol.COMMAND_QUERY_SCENES || payload.firstOrNull() != 0x35.toByte()) {
            return null
        }
        val entryOffset = when (payload.getOrNull(1)?.toInt()?.and(0xFF)) {
            0x81 -> 3
            0x82 -> 4
            else -> 2
        }
        if (entryOffset > payload.size) return emptyMap()

        val result = linkedMapOf<Int, Boolean>()
        var offset = entryOffset
        while (offset + SCENE_ENTRY_SIZE <= payload.size) {
            val slot = (payload[offset].toInt() and 0xFF) + 1
            if (slot in DuduFff0LedProtocol.SCENE_SLOT_RANGE) {
                val hasContent = (offset + 1 until offset + SCENE_ENTRY_SIZE)
                    .any { payload[it] != 0.toByte() }
                result[slot] = hasContent
            }
            offset += SCENE_ENTRY_SIZE
        }
        return result
    }

    private companion object {
        private const val SCENE_ENTRY_SIZE = 21
    }
}

/** Reassembles FFF0 notifications that may arrive fragmented or concatenated. */
internal class DuduFff0NotificationDecoder {
    private var buffer = byteArrayOf()

    @Synchronized
    fun append(chunk: ByteArray): List<DuduFff0Response> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk
        if (buffer.size > MAX_BUFFER_SIZE) {
            buffer = buffer.copyOfRange(buffer.size - MAX_FRAME_SIZE, buffer.size)
        }

        val responses = mutableListOf<DuduFff0Response>()
        while (true) {
            val headerOffset = findHeader(buffer)
            if (headerOffset < 0) {
                buffer = retainHeaderPrefix(buffer)
                break
            }
            if (headerOffset > 0) buffer = buffer.copyOfRange(headerOffset, buffer.size)
            if (buffer.size < FRAME_PREFIX_SIZE) break

            val remainingLength = readLittleEndian(buffer, 4)
            val frameSize = remainingLength + FRAME_PREFIX_SIZE
            if (frameSize !in MIN_FRAME_SIZE..MAX_FRAME_SIZE) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < frameSize) break

            val frame = buffer.copyOfRange(0, frameSize)
            buffer = buffer.copyOfRange(frameSize, buffer.size)
            parse(frame)?.let(responses::add)
        }
        return responses
    }

    @Synchronized
    fun reset() {
        buffer = byteArrayOf()
    }

    private fun parse(frame: ByteArray): DuduFff0Response? {
        if (frame.size < MIN_FRAME_SIZE || !hasHeader(frame, 0)) return null
        if (readLittleEndian(frame, 4) + FRAME_PREFIX_SIZE != frame.size) return null
        val expectedChecksum = readLittleEndian(frame, frame.lastIndex - 1)
        val actualChecksum = (0 until frame.size - 2).sumOf { frame[it].toInt() and 0xFF } and 0xFFFF
        if (expectedChecksum != actualChecksum) return null

        return DuduFff0Response(
            command = readLittleEndian(frame, 6),
            payload = frame.copyOfRange(10, frame.size - 2),
        )
    }

    private fun findHeader(source: ByteArray): Int {
        for (index in 0..source.size - HEADER_SIZE) {
            if (hasHeader(source, index)) return index
        }
        return -1
    }

    private fun retainHeaderPrefix(source: ByteArray): ByteArray {
        for (length in minOf(HEADER_SIZE - 1, source.size) downTo 1) {
            val offset = source.size - length
            if ((0 until length).all { source[offset + it] == HEADER[it] }) {
                return source.copyOfRange(offset, source.size)
            }
        }
        return byteArrayOf()
    }

    private fun hasHeader(source: ByteArray, offset: Int): Boolean =
        source.getOrNull(offset) == HEADER_1 &&
            source.getOrNull(offset + 1) == HEADER_2 &&
            source.getOrNull(offset + 2) == HEADER_3 &&
            source.getOrNull(offset + 3) == HEADER_4

    private fun readLittleEndian(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)

    private companion object {
        private val HEADER_1 = 0xAA.toByte()
        private val HEADER_2 = 0x55.toByte()
        private val HEADER_3 = 0xFF.toByte()
        private val HEADER_4 = 0xFF.toByte()
        private val HEADER = byteArrayOf(HEADER_1, HEADER_2, HEADER_3, HEADER_4)
        private const val HEADER_SIZE = 4
        private const val FRAME_PREFIX_SIZE = 6
        private const val MIN_FRAME_SIZE = 12
        private const val MAX_FRAME_SIZE = 2_048
        private const val MAX_BUFFER_SIZE = MAX_FRAME_SIZE * 2
    }
}
