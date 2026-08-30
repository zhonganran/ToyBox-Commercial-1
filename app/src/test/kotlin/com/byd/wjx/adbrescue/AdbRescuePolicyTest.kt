package com.byd.wjx.adbrescue

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbRescuePolicyTest {
    @Test
    fun `boot and BYD power-on events repair and schedule retries`() {
        listOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "com.byd.action.ACC_ON",
            "com.byd.action.IGN_ON",
        ).forEach { action ->
            assertTrue(action, AdbRescueTriggerPolicy.shouldRun(action))
            assertTrue(action, AdbRescueTriggerPolicy.shouldScheduleRetries(action))
        }
    }

    @Test
    fun `ambiguous and noisy wake events do not open ADB`() {
        assertFalse(AdbRescueTriggerPolicy.shouldRun(Intent.ACTION_SCREEN_ON))
        assertFalse(AdbRescueTriggerPolicy.shouldRun(Intent.ACTION_USER_PRESENT))
        assertFalse(AdbRescueTriggerPolicy.shouldRun(Intent.ACTION_POWER_CONNECTED))
        assertFalse(AdbRescueTriggerPolicy.shouldRun(Intent.ACTION_POWER_DISCONNECTED))
        assertFalse(AdbRescueTriggerPolicy.shouldRun("com.byd.accmode.ACC_MODE_CHANGED"))
    }

    @Test
    fun `only locked boot recovery runs before user unlock`() {
        assertTrue(AdbRescueTriggerPolicy.shouldRunWhileUserLocked(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertFalse(AdbRescueTriggerPolicy.shouldRunWhileUserLocked(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(AdbRescueTriggerPolicy.shouldRunWhileUserLocked("com.byd.action.ACC_ON"))
    }

    @Test
    fun `retry schedule matches upstream recovery intervals`() {
        assertEquals(
            listOf(10L, 30L, 60L, 120L, 300L),
            AdbRescueScheduler.retryDelaysMs.map { it / 1_000L },
        )
    }

    @Test
    fun `helper commands are fixed and validate package input`() {
        assertEquals(
            "pm grant --user 0 com.byd.wjx android.permission.WRITE_SECURE_SETTINGS " +
                ">/dev/null 2>&1 || true; pm grant --user 10 com.byd.wjx " +
                "android.permission.WRITE_SECURE_SETTINGS",
            AdbRescueShellCommands.grantWriteSecureSettings("com.byd.wjx", 10),
        )
        runCatching {
            AdbRescueShellCommands.grantWriteSecureSettings("com.byd.wjx; reboot", 10)
        }.onSuccess {
            throw AssertionError("unsafe package name was accepted")
        }
        assertFalse(AdbRescueShellCommands.ENABLE_BOTH_MODES.contains("&&"))
        assertTrue(AdbRescueShellCommands.ENABLE_BOTH_MODES.contains("settings put global adb_enabled 1"))
        assertTrue(AdbRescueShellCommands.ENABLE_BOTH_MODES.contains("setprop persist.sys.adb.wiress.enable true"))
        assertTrue(AdbRescueShellCommands.ENABLE_BOTH_MODES.contains("setprop sys.connect.adb.wiress 1"))
        assertFalse(AdbRescueShellCommands.DISABLE_BOTH_MODES.contains("&&"))
    }

    @Test
    fun `vendor ADB triggers are throttled independently`() {
        AdbRescueTriggerThrottle.resetForTest()

        assertTrue(AdbRescueTriggerThrottle.shouldRun("com.byd.action.ACC_ON", nowMs = 1_000L))
        assertFalse(AdbRescueTriggerThrottle.shouldRun("com.byd.action.ACC_ON", nowMs = 20_000L))
        assertTrue(AdbRescueTriggerThrottle.shouldRun("com.byd.action.IGN_ON", nowMs = 20_000L))
        assertTrue(AdbRescueTriggerThrottle.shouldRun("com.byd.action.ACC_ON", nowMs = 31_000L))
        assertTrue(AdbRescueTriggerThrottle.shouldRun(Intent.ACTION_BOOT_COMPLETED, nowMs = 31_001L))
    }

    @Test
    fun `BYD truthy property values are recognized`() {
        listOf("1", "true", "TRUE", "yes", "on", "y").forEach {
            assertTrue(it, isTruthyAdbProperty(it))
        }
        listOf("0", "false", "off", "").forEach {
            assertFalse(it, isTruthyAdbProperty(it))
        }
    }
}
