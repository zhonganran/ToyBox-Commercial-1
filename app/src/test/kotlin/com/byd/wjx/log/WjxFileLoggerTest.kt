package com.byd.wjx.log

import com.byd.wjx.security.EndpointPrivacy
import com.byd.wjx.storage.WjxDirectories
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WjxFileLoggerTest {
    @After
    fun tearDown() {
        WjxFileLogger.clear()
    }

    @Test
    fun `historical local log is scrubbed before display or upload`() {
        WjxDirectories.ensureBaseDirs()
        WjxDirectories.appLog.writeText(
            "legacy https://wjx.689588.xyz/private 203.0.113.8:443\n" +
                "controller=57.1.3.2501010.1",
        )

        WjxFileLogger.sanitizeStoredLog()

        val stored = WjxDirectories.appLog.readText()
        assertFalse(stored.contains("689588.xyz"))
        assertFalse(stored.contains("203.0.113.8"))
        assertTrue(stored.contains(EndpointPrivacy.HIDDEN_ENDPOINT))
        assertTrue(stored.contains("57.1.3.2501010.1"))
    }

    @Test
    fun `oversized externally modified log is bounded before sanitizing`() {
        WjxDirectories.ensureBaseDirs()
        WjxDirectories.appLog.writeText(
            "x".repeat((WjxFileLogger.MAX_LOG_BYTES + 1024L).toInt()) +
                "\nlegacy https://wjx.689588.xyz/private 203.0.113.8:443\n",
        )

        WjxFileLogger.sanitizeStoredLog()

        assertTrue(WjxDirectories.appLog.length() <= WjxFileLogger.MAX_LOG_BYTES)
        val stored = WjxDirectories.appLog.readText()
        assertFalse(stored.contains("689588.xyz"))
        assertFalse(stored.contains("203.0.113.8"))
    }
}
