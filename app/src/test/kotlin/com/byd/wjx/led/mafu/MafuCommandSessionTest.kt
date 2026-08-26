package com.byd.wjx.led.mafu

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MafuCommandSessionTest {
    @Test
    fun `only the matching protocol acknowledgement completes a command`() = runTest {
        val writes = mutableListOf<ByteArray>()
        val session = MafuCommandSession(
            writePacket = { packet, _, _ ->
                writes += packet
                true
            },
            sequenceGenerator = MafuSequenceGenerator(initial = 0x1234),
            attempts = 1,
            ackTimeoutMs = 1_000,
        )
        val result = async {
            session.send(
                buildPacket = { sequence -> byteArrayOf(sequence.toByte(), (sequence shr 8).toByte()) },
                description = "test command",
            )
        }
        testScheduler.runCurrent()

        session.accept(MafuAcknowledgement(sequence = 0x4321, accepted = true, responseType = 0x82))
        testScheduler.runCurrent()
        assertFalse(result.isCompleted)

        session.accept(MafuAcknowledgement(sequence = 0x1234, accepted = true, responseType = 0x82))
        assertTrue(result.await())
        assertEquals(1, writes.size)
        assertArrayEquals(byteArrayOf(0x34, 0x12), writes.single())
    }

    @Test
    fun `a rejected acknowledgement retries the identical sequence and packet`() = runTest {
        val writes = mutableListOf<ByteArray>()
        val session = MafuCommandSession(
            writePacket = { packet, _, _ ->
                writes += packet.copyOf()
                true
            },
            sequenceGenerator = MafuSequenceGenerator(initial = 8),
            attempts = 2,
            ackTimeoutMs = 1_000,
        )
        val result = async {
            session.send(
                buildPacket = { sequence -> byteArrayOf(sequence.toByte()) },
                description = "retry command",
            )
        }
        testScheduler.runCurrent()

        session.accept(MafuAcknowledgement(sequence = 8, accepted = false, responseType = 0x82))
        testScheduler.runCurrent()
        assertEquals(2, writes.size)
        assertArrayEquals(writes[0], writes[1])

        session.accept(MafuAcknowledgement(sequence = 8, accepted = true, responseType = 0x82))
        assertTrue(result.await())
    }
}
