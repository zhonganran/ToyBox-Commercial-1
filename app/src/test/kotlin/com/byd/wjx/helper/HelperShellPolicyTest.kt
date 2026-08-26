package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperShellPolicyTest {
    @Test
    fun `sentry reads bind safe keys as argv`() {
        val plan = planSentryShellCommand("get_global", "sentrymode_enabled")

        assertEquals("settings get global \"\$1\"", plan.command?.script)
        assertEquals(listOf("sentrymode_enabled"), plan.command?.args)
        assertNull(plan.rejectionCode)
    }

    @Test
    fun `sentry rejects shell syntax in read keys`() {
        val plan = planSentryShellCommand("get_prop", "safe;reboot")

        assertNull(plan.command)
        assertEquals(-2, plan.rejectionCode)
    }

    @Test
    fun `sentry writes only approved global booleans`() {
        val allowed = planSentryShellCommand("put_global", "sentrymode_enabled_switch=1")
        val unknownKey = planSentryShellCommand("put_global", "airplane_mode_on=1")
        val unknownValue = planSentryShellCommand("put_global", "sentrymode_enabled=2")

        assertEquals(listOf("sentrymode_enabled_switch", "1"), allowed.command?.args)
        assertEquals(-2, unknownKey.rejectionCode)
        assertEquals(-2, unknownValue.rejectionCode)
    }

    @Test
    fun `sentry property write is limited to the BYD sentry state`() {
        val allowed = planSentryShellCommand("set_prop", "sys.byd.sentrymode_state=enable")
        val rejected = planSentryShellCommand("set_prop", "persist.sys.usb.config=adb")

        assertEquals(listOf("sys.byd.sentrymode_state", "enable"), allowed.command?.args)
        assertEquals(-2, rejected.rejectionCode)
    }

    @Test
    fun `unknown sentry operation has a distinct rejection code`() {
        assertEquals(-3, planSentryShellCommand("exec", "id").rejectionCode)
    }

    @Test
    fun `developer shell blocks destructive disk commands`() {
        assertTrue(isBlockedDevShellCommand("rm -rf / "))
        assertTrue(isBlockedDevShellCommand("rm -rf /*"))
        assertTrue(isBlockedDevShellCommand("dd if=/tmp/a of=/dev/block/sda"))
        assertTrue(isBlockedDevShellCommand("mkfs.ext4 /dev/block/sda"))
        assertFalse(isBlockedDevShellCommand("settings get secure accessibility_enabled"))
    }
}
