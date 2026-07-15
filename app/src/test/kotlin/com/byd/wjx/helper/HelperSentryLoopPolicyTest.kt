package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperSentryLoopPolicyTest {
    @Test
    fun firstIdlePass_runsBaseline() {
        assertTrue(
            shouldRunSentryIdleBaseline(
                nowMs = 100_000L,
                lastBaselineAtMs = 0L,
                wasGuarding = false,
            ),
        )
    }

    @Test
    fun idleBaseline_isThrottledUntilInterval() {
        assertFalse(
            shouldRunSentryIdleBaseline(
                nowMs = 129_999L,
                lastBaselineAtMs = 100_000L,
                wasGuarding = false,
            ),
        )
        assertTrue(
            shouldRunSentryIdleBaseline(
                nowMs = 130_000L,
                lastBaselineAtMs = 100_000L,
                wasGuarding = false,
            ),
        )
    }

    @Test
    fun leavingGuarding_runsImmediateBaseline() {
        assertTrue(
            shouldRunSentryIdleBaseline(
                nowMs = 101_000L,
                lastBaselineAtMs = 100_000L,
                wasGuarding = true,
            ),
        )
    }

    @Test
    fun rawHighRiskWithoutConfirmation_doesNotTriggerFallback() {
        assertFalse(
            shouldTriggerSentryFallback(
                nowMs = 10_000L,
                lastHighRiskAtMs = 10_000L,
                alarmTimestampChanged = false,
                highRiskLogSeen = false,
            ),
        )
    }

    @Test
    fun recentHighRiskWithAlarmTimestamp_triggersFallback() {
        assertTrue(
            shouldTriggerSentryFallback(
                nowMs = 18_000L,
                lastHighRiskAtMs = 10_000L,
                alarmTimestampChanged = true,
                highRiskLogSeen = false,
            ),
        )
    }

    @Test
    fun recentHighRiskWithHighRiskLog_triggersFallback() {
        assertTrue(
            shouldTriggerSentryFallback(
                nowMs = 18_000L,
                lastHighRiskAtMs = 10_000L,
                alarmTimestampChanged = false,
                highRiskLogSeen = true,
            ),
        )
    }

    @Test
    fun staleHighRiskConfirmation_doesNotTriggerFallback() {
        assertFalse(
            shouldTriggerSentryFallback(
                nowMs = 25_001L,
                lastHighRiskAtMs = 10_000L,
                alarmTimestampChanged = true,
                highRiskLogSeen = true,
            ),
        )
    }

    @Test
    fun confirmationWithoutHighRisk_doesNotTriggerFallback() {
        assertFalse(
            shouldTriggerSentryFallback(
                nowMs = 10_000L,
                lastHighRiskAtMs = 0L,
                alarmTimestampChanged = true,
                highRiskLogSeen = true,
            ),
        )
    }

    @Test
    fun sentryAccessGate_blocksOpenOrUnlockedVehicle() {
        val gate = SentryVehicleAccessGate()
        assertEquals(
            "vehicle_access",
            gate.blockReason(secureAccessSnapshot(lockFrontLeft = LOCK_SOUND_DOOR_UNLOCKED), nowMs = 1_000L),
        )
        assertEquals(
            "vehicle_access",
            gate.blockReason(secureAccessSnapshot(doorFrontLeftOpen = true), nowMs = 2_000L),
        )
    }

    @Test
    fun sentryAccessGate_failsClosedWhenAccessStateIsUnavailable() {
        val gate = SentryVehicleAccessGate()
        assertEquals(
            "vehicle_access_unconfirmed",
            gate.blockReason(emptyAccessSnapshot(), nowMs = 1_000L),
        )
    }

    @Test
    fun sentryAccessGate_waitsFifteenSecondsAfterObservedAccessLocks() {
        val gate = SentryVehicleAccessGate()
        assertEquals(
            "vehicle_access",
            gate.blockReason(secureAccessSnapshot(lockFrontLeft = LOCK_SOUND_DOOR_UNLOCKED), nowMs = 1_000L),
        )
        assertEquals("post_lock_arming", gate.blockReason(secureAccessSnapshot(), nowMs = 2_000L))
        assertEquals("post_lock_arming", gate.blockReason(secureAccessSnapshot(), nowMs = 16_999L))
        assertNull(gate.blockReason(secureAccessSnapshot(), nowMs = 17_000L))
    }

    @Test
    fun sentryAccessGate_allowsAlreadySecureVehicleAtStartup() {
        val gate = SentryVehicleAccessGate()
        assertNull(gate.blockReason(secureAccessSnapshot(), nowMs = 1_000L))
    }

    @Test
    fun sentryPlaybackCooldown_isFifteenSeconds() {
        assertTrue(isSentryCooldownComplete(nowMs = 1_000L, lastPlaybackAtMs = 0L))
        assertFalse(isSentryCooldownComplete(nowMs = 15_999L, lastPlaybackAtMs = 1_000L))
        assertTrue(isSentryCooldownComplete(nowMs = 16_000L, lastPlaybackAtMs = 1_000L))
        assertTrue(isSentryCooldownComplete(nowMs = 900L, lastPlaybackAtMs = 1_000L))
    }

    private fun secureAccessSnapshot(
        lockFrontLeft: Int? = LOCK_SOUND_DOOR_LOCKED,
        doorFrontLeftOpen: Boolean? = false,
    ): LockSoundVehicleAccessSnapshot =
        LockSoundVehicleAccessSnapshot(
            lockFrontLeft = lockFrontLeft,
            lockFrontRight = LOCK_SOUND_DOOR_LOCKED,
            lockRearLeft = LOCK_SOUND_DOOR_LOCKED,
            lockRearRight = LOCK_SOUND_DOOR_LOCKED,
            doorFrontLeftOpen = doorFrontLeftOpen,
            doorFrontRightOpen = false,
            doorRearLeftOpen = false,
            doorRearRightOpen = false,
            trunkOpen = false,
            hoodOpen = false,
        )

    private fun emptyAccessSnapshot(): LockSoundVehicleAccessSnapshot =
        LockSoundVehicleAccessSnapshot(
            lockFrontLeft = null,
            lockFrontRight = null,
            lockRearLeft = null,
            lockRearRight = null,
            doorFrontLeftOpen = null,
            doorFrontRightOpen = null,
            doorRearLeftOpen = null,
            doorRearRightOpen = null,
            trunkOpen = null,
            hoodOpen = null,
        )
}
