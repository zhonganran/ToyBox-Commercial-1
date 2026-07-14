package com.byd.wjx.data.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryModeControllerTest {
    @Test
    fun `official sentry result codes match byd service semantics`() {
        assertTrue(SentryModeController.officialCodeAccepted(enabled = true, code = 1000))
        assertTrue(SentryModeController.officialCodeAccepted(enabled = true, code = 1017))
        assertTrue(SentryModeController.officialCodeAccepted(enabled = false, code = 1000))
        assertFalse(SentryModeController.officialCodeAccepted(enabled = false, code = 1017))
        assertFalse(SentryModeController.officialCodeAccepted(enabled = true, code = 1002))
    }
}
