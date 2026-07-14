package com.byd.wjx.helper

import org.junit.Assert.assertFalse
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
}
