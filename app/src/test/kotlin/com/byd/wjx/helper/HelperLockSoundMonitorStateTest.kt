package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperLockSoundMonitorStateTest {

    @Test
    fun `fresh cache restores stable state while old and future samples are ignored`() {
        val cached = persisted(sampledAtMs = 10_000L)

        val fresh = LockSoundMonitorState.restore(10_001L, cached, pollingEnabled = true)
        val old = LockSoundMonitorState.restore(259_210_001L, cached, pollingEnabled = true)
        val future = LockSoundMonitorState.restore(9_999L, cached, pollingEnabled = true)

        assertEquals(LOCK_SOUND_DOOR_LOCKED, fresh.stableLock)
        assertTrue(fresh.startupSamplePending)
        assertNull(old.stableLock)
        assertFalse(old.startupSamplePending)
        assertNull(future.stableLock)
    }

    @Test
    fun `pending transition restore expires after three seconds`() {
        val cached = persisted(
            sampledAtMs = 10_000L,
            pendingState = LOCK_SOUND_DOOR_UNLOCKED,
            pendingSinceMs = 9_000L,
        )

        val fresh = LockSoundMonitorState.restore(12_000L, cached, pollingEnabled = true)
        val expired = LockSoundMonitorState.restore(12_001L, cached, pollingEnabled = true)

        assertEquals(LOCK_SOUND_DOOR_UNLOCKED, fresh.pendingLock)
        assertEquals(9_000L, fresh.pendingSince)
        assertNull(expired.pendingLock)
        assertEquals(0L, expired.pendingSince)
    }

    @Test
    fun `flash confirmation restore expires after five seconds`() {
        val cached = persisted(sampledAtMs = 10_000L, flashValue = 9, flashAtMs = 8_000L)

        val fresh = LockSoundMonitorState.restore(13_000L, cached, pollingEnabled = true)
        val expired = LockSoundMonitorState.restore(13_001L, cached, pollingEnabled = true)

        assertEquals(8_000L, fresh.flashAt)
        assertTrue(fresh.isFlashConfirmed(value = 0, nowMs = 13_000L))
        assertEquals(0L, expired.flashAt)
        assertFalse(expired.isFlashConfirmed(value = 0, nowMs = 13_001L))
    }

    @Test
    fun `pending state expires and clears when signal returns to stable state`() {
        val state = LockSoundMonitorState.restore(10_000L, persisted(sampledAtMs = 10_000L), pollingEnabled = true)
        assertTrue(state.startPending(LOCK_SOUND_DOOR_UNLOCKED, nowMs = 11_000L))
        assertFalse(state.isPendingExpired(14_000L))
        assertTrue(state.isPendingExpired(14_001L))

        assertTrue(state.clearPending())
        assertNull(state.pendingLock)
        assertEquals(0L, state.pendingSince)
        assertFalse(state.clearPending())
    }

    @Test
    fun `power sampling keeps three hundred millisecond cadence and recovers from clock rollback`() {
        val state = LockSoundMonitorState.restore(10_000L, null, pollingEnabled = true)
        assertTrue(state.shouldSamplePower(10_000L))
        state.lastPowerAt = 10_000L

        assertFalse(state.shouldSamplePower(10_299L))
        assertTrue(state.shouldSamplePower(10_300L))
        assertTrue(state.shouldSamplePower(9_999L))
    }

    @Test
    fun `stable transition records change time and removes pending data from snapshot`() {
        val state = LockSoundMonitorState.restore(10_000L, persisted(sampledAtMs = 10_000L), pollingEnabled = true)
        state.startPending(LOCK_SOUND_DOOR_UNLOCKED, nowMs = 11_000L)

        assertTrue(state.updateStableLock(LOCK_SOUND_DOOR_UNLOCKED, nowMs = 11_500L))
        val snapshot = state.snapshot(11_500L)

        assertEquals(LOCK_SOUND_DOOR_UNLOCKED, snapshot?.state)
        assertEquals(11_500L, snapshot?.changedAtMs)
        assertNull(snapshot?.pendingState)
        assertEquals(0L, snapshot?.pendingSinceMs)
    }

    private fun persisted(
        sampledAtMs: Long,
        pendingState: Int? = null,
        pendingSinceMs: Long = 0L,
        flashValue: Int? = null,
        flashAtMs: Long = 0L,
    ): PersistedLockState =
        PersistedLockState(
            state = LOCK_SOUND_DOOR_LOCKED,
            changedAtMs = 5_000L,
            sampledAtMs = sampledAtMs,
            flashValue = flashValue,
            flashAtMs = flashAtMs,
            pendingState = pendingState,
            pendingSinceMs = pendingSinceMs,
        )
}
