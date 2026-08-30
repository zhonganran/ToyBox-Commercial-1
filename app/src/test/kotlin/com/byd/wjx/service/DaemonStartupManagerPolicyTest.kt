package com.byd.wjx.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaemonStartupManagerPolicyTest {
    @Test
    fun `ADB recovery requires an enabled guard and skips health checks`() {
        assertFalse(shouldRunAdbRescue("boot", guardEnabled = false))
        assertTrue(shouldRunAdbRescue("boot", guardEnabled = true))
        assertFalse(
            shouldRunAdbRescue(
                DaemonStartupManager.REASON_HEALTH_CHECK,
                guardEnabled = true,
            ),
        )
    }
}
