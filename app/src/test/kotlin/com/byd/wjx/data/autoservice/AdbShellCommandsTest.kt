package com.byd.wjx.data.autoservice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbShellCommandsTest {
    @Test
    fun `read-only names reject shell metacharacters`() {
        assertRejected { AdbShellCommands.getProperty("ro.serialno;id") }
        assertRejected { AdbShellCommands.getSecureSetting("android_id\$(id)") }
        assertTrue(AdbShellCommands.getProperty("ro.build.version.release") == "getprop ro.build.version.release")
    }

    @Test
    fun `usage appop is restricted to this application`() {
        assertRejected { AdbShellCommands.grantUsageStats("com.example.other") }
        assertTrue(
            AdbShellCommands.grantUsageStats("com.byd.wjx") ==
                "appops set com.byd.wjx GET_USAGE_STATS allow",
        )
    }

    @Test
    fun `helper command uses signed apk classpath and bounded service poll`() {
        val command = AdbShellCommands.spawnHelper(
            packageCodePath = "/data/app/com.byd.wjx/base.apk",
            callerUid = 10_123,
            helperLogPath = "/sdcard/WJX/wjx.log",
            helperLogDirectory = "/sdcard/WJX",
        )

        assertTrue(command.contains("/data/app/com.byd.wjx/base.apk"))
        assertTrue(command.contains("com.byd.wjx.helper.HelperDaemon"))
        assertTrue(command.contains("10123"))
        assertTrue(command.contains("service list"))
        assertTrue(command.contains("for i in 1 2 3"))
        assertFalse(command.contains("helper.dex"))
        assertFalse(command.contains("base64"))
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
