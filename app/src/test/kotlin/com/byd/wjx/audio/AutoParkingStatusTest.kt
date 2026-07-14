package com.byd.wjx.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoParkingStatusTest {

    @Test
    fun `only parking active status triggers auto parking sound`() {
        listOf(0, 1, 2, 3, 5, 6, 7, 99).forEach { status ->
            assertFalse("status $status should not trigger sound", AutoParkingStatus.isSoundActive(status))
        }
        assertTrue(AutoParkingStatus.isSoundActive(4))
    }
}
