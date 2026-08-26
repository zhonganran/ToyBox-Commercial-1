package com.byd.wjx.helper

import com.byd.wjx.audio.ExteriorChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperLockSoundPlaybackQueueTest {

    @Test
    fun `idle request starts immediately without entering pending queue`() {
        val queue = LockSoundPlaybackQueue()

        assertEquals(LockSoundQueueDecision.StartNow, queue.submit(request(LockEvent.Lock), activeEvent = null))
        assertEquals(0, queue.size)
        assertNull(queue.poll())
    }

    @Test
    fun `active or queued event is merged`() {
        val queue = LockSoundPlaybackQueue()
        assertEquals(
            LockSoundQueueDecision.Merged,
            queue.submit(request(LockEvent.Lock), activeEvent = LockEvent.Lock),
        )
        assertEquals(
            LockSoundQueueDecision.Queued,
            queue.submit(request(LockEvent.Unlock), activeEvent = LockEvent.Lock),
        )
        assertEquals(
            LockSoundQueueDecision.Merged,
            queue.submit(request(LockEvent.Unlock), activeEvent = LockEvent.Lock),
        )
        assertEquals(1, queue.size)
    }

    @Test
    fun `pending requests keep fifo order`() {
        val queue = LockSoundPlaybackQueue()
        queue.submit(request(LockEvent.Unlock), activeEvent = LockEvent.Lock)
        queue.submit(request(LockEvent.Sentry), activeEvent = LockEvent.Lock)

        assertEquals(LockEvent.Unlock, queue.poll()?.event)
        assertEquals(LockEvent.Sentry, queue.poll()?.event)
        assertNull(queue.poll())
    }

    @Test
    fun `queue rejects requests after configured capacity`() {
        val queue = LockSoundPlaybackQueue(maxPendingRequests = 2)
        assertEquals(LockSoundQueueDecision.Queued, queue.submit(request(LockEvent.Unlock), LockEvent.Lock))
        assertEquals(LockSoundQueueDecision.Queued, queue.submit(request(LockEvent.Sentry), LockEvent.Lock))

        assertEquals(
            LockSoundQueueDecision.Full,
            queue.submit(request(LockEvent.Lock), activeEvent = LockEvent.Unlock),
        )
        assertEquals(2, queue.size)
    }

    @Test
    fun `stop removes matching queued event only`() {
        val queue = LockSoundPlaybackQueue()
        queue.submit(request(LockEvent.Unlock), activeEvent = LockEvent.Lock)
        queue.submit(request(LockEvent.Sentry), activeEvent = LockEvent.Lock)

        assertTrue(queue.remove(LockEvent.Unlock))
        assertFalse(queue.remove(LockEvent.Unlock))
        assertEquals(LockEvent.Sentry, queue.poll()?.event)
    }

    private fun request(event: LockEvent): LockSoundPlaybackRequest =
        LockSoundPlaybackRequest(
            event = event,
            eventConfig = EventConfig(enabled = true, path = "/tmp/${event.key}.wav"),
            preferredChannel = ExteriorChannel.Channel1,
            reason = "test",
        )
}
