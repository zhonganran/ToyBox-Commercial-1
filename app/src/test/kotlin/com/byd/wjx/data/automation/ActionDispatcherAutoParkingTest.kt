package com.byd.wjx.data.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatcherAutoParkingTest {
    @Test
    fun `ordinary automation sound is blocked during active auto parking`() {
        assertTrue(
            ActionDispatcher.shouldSkipAutomationOutsideSound(
                owner = "automation:1:enter:0",
                parkingStatus = 4,
                allowDuringAutoParking = false,
            ),
        )
    }

    @Test
    fun `auto parking automation sound is allowed when explicitly marked`() {
        assertFalse(
            ActionDispatcher.shouldSkipAutomationOutsideSound(
                owner = "automation:1:enter:0",
                parkingStatus = 4,
                allowDuringAutoParking = true,
            ),
        )
    }

    @Test
    fun `non automation playback keeps normal behavior`() {
        assertFalse(
            ActionDispatcher.shouldSkipAutomationOutsideSound(
                owner = null,
                parkingStatus = 4,
                allowDuringAutoParking = false,
            ),
        )
    }
}
