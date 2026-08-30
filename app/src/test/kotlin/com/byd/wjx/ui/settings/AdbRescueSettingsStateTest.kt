package com.byd.wjx.ui.settings

import com.byd.wjx.adbrescue.AdbRescueReport
import com.byd.wjx.adbrescue.AdbRescueValueResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbRescueSettingsStateTest {
    @Test
    fun `category status reflects guard permission and readback`() {
        assertEquals("守护关闭", adbRescueCategoryStatus(AdbRescueSettingsUiState()))
        assertEquals("待检查", adbRescueCategoryStatus(AdbRescueSettingsUiState(guardEnabled = true)))
        assertEquals(
            "权限缺失",
            adbRescueCategoryStatus(
                AdbRescueSettingsUiState(guardEnabled = true, report = report(enabled = true, permission = false)),
            ),
        )
        assertEquals(
            "需修复",
            adbRescueCategoryStatus(
                AdbRescueSettingsUiState(guardEnabled = true, report = report(enabled = false, permission = true)),
            ),
        )
        assertEquals(
            "守护正常",
            adbRescueCategoryStatus(
                AdbRescueSettingsUiState(guardEnabled = true, report = report(enabled = true, permission = true)),
            ),
        )
    }

    @Test
    fun `rescue remaining time follows wall clock deadline`() {
        assertEquals(180, remainingSeconds(deadlineMs = 180_000L, nowMs = 0L))
        assertEquals(179, remainingSeconds(deadlineMs = 180_000L, nowMs = 1_001L))
        assertEquals(1, remainingSeconds(deadlineMs = 180_000L, nowMs = 179_999L))
        assertEquals(0, remainingSeconds(deadlineMs = 180_000L, nowMs = 180_000L))
    }

    private fun report(enabled: Boolean, permission: Boolean): AdbRescueReport {
        val value = AdbRescueValueResult(
            key = "key",
            desiredValue = "1",
            before = if (enabled) "1" else "0",
            writeAttempted = false,
            writeAccepted = null,
            after = if (enabled) "1" else "0",
            enabled = enabled,
        )
        return AdbRescueReport(
            trigger = "test",
            userId = 0,
            uid = 0,
            writeSecureSettingsGranted = permission,
            standardAdb = value,
            bydWirelessEnabled = value,
            bydWirelessConnection = value,
            settingsWriteTestSucceeded = false,
        )
    }
}
