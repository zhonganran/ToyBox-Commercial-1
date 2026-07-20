package com.byd.wjx.helper

import com.byd.wjx.audio.ExteriorChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperLockSoundPlaybackPolicyTest {

    @Test
    fun `binder event kinds keep their existing mapping`() {
        assertEquals(LockEvent.Unlock, LockEvent.fromKind(0))
        assertEquals(LockEvent.Lock, LockEvent.fromKind(1))
        assertEquals(LockEvent.Sentry, LockEvent.fromKind(2))
        assertNull(LockEvent.fromKind(-1))
        assertNull(LockEvent.fromKind(3))
    }

    @Test
    fun `configured lock sound events remain one shot`() {
        assertFalse(LockEvent.Unlock.looping)
        assertFalse(LockEvent.Lock.looping)
        assertFalse(LockEvent.Sentry.looping)
    }

    @Test
    fun `preferred route is tried before the alternate route`() {
        assertEquals(
            listOf(ExteriorChannel.Channel0, ExteriorChannel.Channel1),
            lockSoundRouteCandidates(ExteriorChannel.Channel0),
        )
        assertEquals(
            listOf(ExteriorChannel.Channel1, ExteriorChannel.Channel0),
            lockSoundRouteCandidates(ExteriorChannel.Channel1),
        )
    }

    @Test
    fun `open door edge refreshes immediately and held door refreshes every four hundred milliseconds`() {
        assertTrue(shouldRefreshOpenDoorRoute(openedEdge = true, nowMs = 1_050L, lastRefreshAtMs = 1_000L))
        assertTrue(shouldRefreshOpenDoorRoute(openedEdge = false, nowMs = 1_000L, lastRefreshAtMs = null))
        assertFalse(shouldRefreshOpenDoorRoute(openedEdge = false, nowMs = 1_399L, lastRefreshAtMs = 1_000L))
        assertTrue(shouldRefreshOpenDoorRoute(openedEdge = false, nowMs = 1_400L, lastRefreshAtMs = 1_000L))
        assertTrue(shouldRefreshOpenDoorRoute(openedEdge = false, nowMs = 900L, lastRefreshAtMs = 1_000L))
    }

    @Test
    fun `door lock readings keep the existing normalization`() {
        assertNull(validateLockSoundDoorState(0))
        assertEquals(LOCK_SOUND_DOOR_UNLOCKED, validateLockSoundDoorState(1))
        assertEquals(LOCK_SOUND_DOOR_LOCKED, validateLockSoundDoorState(2))
        assertNull(validateLockSoundDoorState(null))
        assertNull(validateLockSoundDoorState(3))
    }

    @Test
    fun `power gate blocks any powered source and fails closed when every source is unavailable`() {
        assertTrue(shouldBlockLockSoundForPower(vehicleState = 1, bodyworkLevel = 0, sdkPower = 0))
        assertTrue(shouldBlockLockSoundForPower(vehicleState = 5, bodyworkLevel = 0, sdkPower = 0))
        assertTrue(shouldBlockLockSoundForPower(vehicleState = 0, bodyworkLevel = 2, sdkPower = 0))
        assertTrue(shouldBlockLockSoundForPower(vehicleState = null, bodyworkLevel = null, sdkPower = null))
        assertFalse(shouldBlockLockSoundForPower(vehicleState = 0, bodyworkLevel = null, sdkPower = null))
        assertFalse(shouldBlockLockSoundForPower(vehicleState = 0, bodyworkLevel = 0, sdkPower = 0))
    }

    @Test
    fun `lock transition does not require flash while unlock still does and power blocks both`() {
        assertEquals(
            LockEvent.Lock,
            confirmedLockSoundEvent(LOCK_SOUND_DOOR_UNLOCKED, LOCK_SOUND_DOOR_LOCKED, flashConfirmed = false, powerBlocked = false),
        )
        assertEquals(
            LockEvent.Unlock,
            confirmedLockSoundEvent(LOCK_SOUND_DOOR_LOCKED, LOCK_SOUND_DOOR_UNLOCKED, flashConfirmed = true, powerBlocked = false),
        )
        assertNull(confirmedLockSoundEvent(LOCK_SOUND_DOOR_LOCKED, LOCK_SOUND_DOOR_UNLOCKED, flashConfirmed = false, powerBlocked = false))
        assertNull(confirmedLockSoundEvent(LOCK_SOUND_DOOR_LOCKED, LOCK_SOUND_DOOR_UNLOCKED, flashConfirmed = true, powerBlocked = true))
        assertNull(confirmedLockSoundEvent(LOCK_SOUND_DOOR_UNLOCKED, LOCK_SOUND_DOOR_LOCKED, flashConfirmed = false, powerBlocked = true))
        assertNull(confirmedLockSoundEvent(LOCK_SOUND_DOOR_LOCKED, LOCK_SOUND_DOOR_LOCKED, flashConfirmed = true, powerBlocked = false))
    }

    @Test
    fun `cold start unlock also requires active flash nine`() {
        assertTrue(
            shouldPlayColdStartUnlock(
                previousState = LOCK_SOUND_DOOR_LOCKED,
                currentState = LOCK_SOUND_DOOR_UNLOCKED,
                flash = 9,
                powerBlocked = false,
            ),
        )
        assertFalse(
            shouldPlayColdStartUnlock(
                previousState = LOCK_SOUND_DOOR_LOCKED,
                currentState = LOCK_SOUND_DOOR_UNLOCKED,
                flash = 0,
                powerBlocked = false,
            ),
        )
        assertFalse(
            shouldPlayColdStartUnlock(
                previousState = LOCK_SOUND_DOOR_LOCKED,
                currentState = LOCK_SOUND_DOOR_UNLOCKED,
                flash = 9,
                powerBlocked = true,
            ),
        )
    }

    @Test
    fun `startup recovery accepts recent edges and stale confirmed unlock only`() {
        assertTrue(
            shouldRecoverStartupLockDifference(
                previousState = LOCK_SOUND_DOOR_UNLOCKED,
                currentState = LOCK_SOUND_DOOR_LOCKED,
                flashConfirmed = false,
                powerBlocked = false,
                sampleAgeMs = LOCK_SOUND_RECOVERY_MAX_AGE_MS,
            ),
        )
        assertFalse(
            shouldRecoverStartupLockDifference(
                previousState = LOCK_SOUND_DOOR_UNLOCKED,
                currentState = LOCK_SOUND_DOOR_LOCKED,
                flashConfirmed = false,
                powerBlocked = false,
                sampleAgeMs = LOCK_SOUND_RECOVERY_MAX_AGE_MS + 1L,
            ),
        )
        assertTrue(
            shouldRecoverStartupLockDifference(
                previousState = LOCK_SOUND_DOOR_LOCKED,
                currentState = LOCK_SOUND_DOOR_UNLOCKED,
                flashConfirmed = true,
                powerBlocked = false,
                sampleAgeMs = 60_000L,
            ),
        )
        assertFalse(
            shouldRecoverStartupLockDifference(
                previousState = LOCK_SOUND_DOOR_LOCKED,
                currentState = LOCK_SOUND_DOOR_UNLOCKED,
                flashConfirmed = false,
                powerBlocked = false,
                sampleAgeMs = 60_000L,
            ),
        )
    }

    @Test
    fun `vehicle access lock state accepts zero and one as unlocked`() {
        assertEquals(LOCK_SOUND_DOOR_UNLOCKED, normalizeVehicleAccessLockState(0))
        assertEquals(LOCK_SOUND_DOOR_UNLOCKED, normalizeVehicleAccessLockState(1))
        assertEquals(LOCK_SOUND_DOOR_LOCKED, normalizeVehicleAccessLockState(2))
        assertNull(normalizeVehicleAccessLockState(3))
    }
}
