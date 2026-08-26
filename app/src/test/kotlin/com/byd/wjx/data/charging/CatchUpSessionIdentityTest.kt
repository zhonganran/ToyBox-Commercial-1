package com.byd.wjx.data.charging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatchUpSessionIdentityTest {
    @Test
    fun `source session key is stable for retries and changes with the baseline`() {
        val first = catchUpSourceSessionKey(1_700_000_000_000L, 20, 80)

        assertEquals(first, catchUpSourceSessionKey(1_700_000_000_000L, 20, 80))
        assertNotEquals(first, catchUpSourceSessionKey(1_700_000_001_000L, 20, 80))
    }
}
