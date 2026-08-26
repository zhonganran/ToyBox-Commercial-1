package com.byd.wjx.led.mafu

import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

internal class MafuNotificationFrameDecoder {
    private var buffer = byteArrayOf()

    @Synchronized
    fun append(value: ByteArray): List<ByteArray> {
        buffer += value
        val packets = mutableListOf<ByteArray>()
        while (buffer.isNotEmpty()) {
            val magicOffset = buffer.indexOfMagic()
            if (magicOffset < 0) {
                buffer = if (buffer.lastOrNull() == MAGIC_LOW) byteArrayOf(MAGIC_LOW) else byteArrayOf()
                break
            }
            if (magicOffset > 0) buffer = buffer.copyOfRange(magicOffset, buffer.size)
            if (buffer.size < PROTOCOL_HEADER_SIZE) break
            val packetSize = MafuProtocol.packetSize(buffer)
            if (packetSize == null) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < packetSize) break
            packets += buffer.copyOfRange(0, packetSize)
            buffer = buffer.copyOfRange(packetSize, buffer.size)
        }
        return packets
    }

    @Synchronized
    fun reset() {
        buffer = byteArrayOf()
    }

    private fun ByteArray.indexOfMagic(): Int {
        for (index in 0 until lastIndex) {
            if (this[index] == MAGIC_LOW && this[index + 1] == MAGIC_HIGH) return index
        }
        return -1
    }

    private companion object {
        private const val PROTOCOL_HEADER_SIZE = 10
        private val MAGIC_LOW = 0xAA.toByte()
        private val MAGIC_HIGH = 0x55.toByte()
    }
}

internal class MafuSequenceGenerator(initial: Int = 1) {
    private val next = AtomicInteger(initial.also { require(it in 1..MAX_SEQUENCE) })

    fun take(): Int {
        while (true) {
            val current = next.get()
            val following = if (current >= MAX_SEQUENCE) 1 else current + 1
            if (next.compareAndSet(current, following)) return current
        }
    }

    private companion object {
        private const val MAX_SEQUENCE = 0xFFFF
    }
}

internal class MafuCommandSession(
    private val writePacket: suspend (
        packet: ByteArray,
        description: String,
        requireReady: Boolean,
    ) -> Boolean,
    private val sequenceGenerator: MafuSequenceGenerator = MafuSequenceGenerator(),
    private val attempts: Int = DEFAULT_COMMAND_ATTEMPTS,
    private val ackTimeoutMs: Long = MafuProtocol.ACK_TIMEOUT_MS,
) {
    private val mutex = Mutex()
    @Volatile private var pendingAcknowledgement: PendingAcknowledgement? = null

    init {
        require(attempts > 0)
        require(ackTimeoutMs > 0)
    }

    suspend fun send(
        buildPacket: (Int) -> ByteArray,
        description: String,
        requireReady: Boolean = true,
    ): Boolean = mutex.withLock {
        val sequence = sequenceGenerator.take()
        val packet = buildPacket(sequence)
        repeat(attempts) { attempt ->
            val acknowledgement = CompletableDeferred<Boolean>()
            pendingAcknowledgement = PendingAcknowledgement(sequence, acknowledgement)
            val queued = writePacket(packet, description, requireReady)
            if (queued) {
                val accepted = withTimeoutOrNull(ackTimeoutMs) { acknowledgement.await() } == true
                clearIfPending(acknowledgement)
                if (accepted) {
                    WjxFileLogger.i(TAG, "command acknowledged $description sno=$sequence")
                    return@withLock true
                }
            } else {
                clearIfPending(acknowledgement)
            }
            WjxFileLogger.w(
                TAG,
                "command retry ${attempt + 1}/$attempts $description sno=$sequence",
            )
        }
        false
    }

    fun accept(response: MafuAcknowledgement) {
        pendingAcknowledgement
            ?.takeIf { it.sequence == response.sequence }
            ?.deferred
            ?.complete(response.accepted)
    }

    fun reset() {
        pendingAcknowledgement?.deferred?.complete(false)
        pendingAcknowledgement = null
    }

    private fun clearIfPending(deferred: CompletableDeferred<Boolean>) {
        if (pendingAcknowledgement?.deferred === deferred) pendingAcknowledgement = null
    }

    private data class PendingAcknowledgement(
        val sequence: Int,
        val deferred: CompletableDeferred<Boolean>,
    )

    private companion object {
        private const val TAG = "MafuBleClient"
        private const val DEFAULT_COMMAND_ATTEMPTS = 3
    }
}
