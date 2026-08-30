package com.byd.wjx.adbrescue

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbRescueManagerTest {
    private lateinit var context: Context
    private lateinit var manager: AdbRescueManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Global.putInt(context.contentResolver, AdbRescueManager.ADB_ENABLED, 0)
        manager = AdbRescueManager(context)
    }

    @Test
    fun `enable both writes and verifies all three ADB values`() {
        val properties = FakePropertyAccess(
            mutableMapOf(
                AdbRescueManager.BYD_WIRELESS_ENABLED to "false",
                AdbRescueManager.BYD_WIRELESS_CONNECTION to "0",
            ),
        )
        manager.propertyAccess = properties

        val report = manager.enableBoth("test")

        assertTrue(report.writeSecureSettingsGranted)
        assertTrue(report.standardAdb.enabled)
        assertEquals("1", report.standardAdb.after)
        assertTrue(report.bydWirelessEnabled.enabled)
        assertEquals("true", report.bydWirelessEnabled.after)
        assertTrue(report.bydWirelessConnection.enabled)
        assertEquals("1", report.bydWirelessConnection.after)
        assertTrue(report.allModesEnabled)
    }

    @Test
    fun `accepted property call is still failure when readback did not change`() {
        manager.propertyAccess = object : AdbRescueSystemPropertyAccess {
            override fun get(key: String, fallback: String): String = "0"
            override fun set(key: String, value: String): Boolean = true
        }

        val report = manager.enableBoth("readback-test")

        assertEquals(true, report.bydWirelessConnection.writeAccepted)
        assertEquals("0", report.bydWirelessConnection.after)
        assertFalse(report.bydWirelessConnection.enabled)
        assertFalse(report.allModesEnabled)
    }

    @Test
    fun `disable both attempts and verifies all three ADB values`() {
        val properties = FakePropertyAccess(
            mutableMapOf(
                AdbRescueManager.BYD_WIRELESS_ENABLED to "true",
                AdbRescueManager.BYD_WIRELESS_CONNECTION to "1",
            ),
        )
        manager.propertyAccess = properties
        Settings.Global.putInt(context.contentResolver, AdbRescueManager.ADB_ENABLED, 1)

        val report = manager.disableBoth("disable-test")

        assertEquals(0, Settings.Global.getInt(context.contentResolver, AdbRescueManager.ADB_ENABLED))
        assertEquals("false", properties.get(AdbRescueManager.BYD_WIRELESS_ENABLED, ""))
        assertEquals("0", properties.get(AdbRescueManager.BYD_WIRELESS_CONNECTION, ""))
        assertTrue(report.allModesDisabled)
    }

    private class FakePropertyAccess(
        private val values: MutableMap<String, String>,
    ) : AdbRescueSystemPropertyAccess {
        override fun get(key: String, fallback: String): String = values[key] ?: fallback

        override fun set(key: String, value: String): Boolean {
            values[key] = value
            return true
        }
    }
}
