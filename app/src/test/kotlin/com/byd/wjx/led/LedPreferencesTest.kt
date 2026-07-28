package com.byd.wjx.led

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LedPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(LedPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `fresh installs receive editable default linkage rules`() {
        val rules = LedPreferences(context).state.value.rules

        assertEquals(LedLinkageDefaults.rules, rules)
        assertEquals("hazard", rules.first().conditionValue)
        assertEquals("trunk", rules.last().conditionValue)
    }

    @Test
    fun `deleting every rule persists an intentionally empty list`() {
        val preferences = LedPreferences(context)
        preferences.state.value.rules.forEach { preferences.deleteRule(it.id) }

        assertTrue(preferences.state.value.rules.isEmpty())
        assertTrue(LedPreferences(context).state.value.rules.isEmpty())
    }

    @Test
    fun `rule edits persist and duplicate conditions are rejected`() {
        val preferences = LedPreferences(context)
        val custom = LedLinkageRule(
            id = "custom",
            triggerType = LedLinkageTriggerType.SPEED,
            conditionValue = "gt_100",
            steps = listOf(LedLinkageStep(LedScene.AUTOPILOT, 9)),
        )

        assertTrue(preferences.saveRule(custom))
        assertFalse(preferences.saveRule(custom.copy(id = "duplicate")))
        assertEquals(custom, LedPreferences(context).state.value.rules.last())
    }

    @Test
    fun `corrupt rule JSON restores defaults`() {
        context.getSharedPreferences(LedPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LedPreferences.KEY_RULES_V2, "broken")
            .commit()

        assertEquals(LedLinkageDefaults.rules, LedPreferences(context).state.value.rules)
    }

    @Test
    fun `legacy binding migrates into its detected protocol profile`() {
        context.getSharedPreferences(LedPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("device_address", "AA:BB:CC:DD:EE:FF")
            .putString("device_name", "LED")
            .putString("device_kind", LedProtocolKind.A950.name)
            .putBoolean("enabled", true)
            .putInt("brightness", 63)
            .commit()

        val snapshot = LedPreferences(context).state.value
        assertEquals(LedProtocolKind.A950, snapshot.selectedProtocol)
        assertEquals(
            LedProtocolPreference(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "LED",
                brightness = 63,
            ),
            snapshot.a950,
        )
        assertTrue(snapshot.ipixelFa.deviceAddress.isBlank())
    }

    @Test
    fun `detected protocol bindings keep independent devices and brightness`() {
        val preferences = LedPreferences(context)
        preferences.bindDetectedDevice(LedProtocolKind.IPIXEL_FA, "FA:00", "FA LED")
        preferences.setBrightness(LedProtocolKind.IPIXEL_FA, 35)
        preferences.bindDetectedDevice(LedProtocolKind.A950, "A9:50", "A950 LED")
        preferences.setBrightness(LedProtocolKind.A950, 85)

        val snapshot = LedPreferences(context).state.value
        assertEquals(LedProtocolKind.A950, snapshot.selectedProtocol)
        assertEquals("FA:00", snapshot.ipixelFa.deviceAddress)
        assertEquals(35, snapshot.ipixelFa.brightness)
        assertEquals("A9:50", snapshot.a950.deviceAddress)
        assertEquals(85, snapshot.a950.brightness)

        preferences.clearDevice(LedProtocolKind.A950)
        assertTrue(preferences.state.value.a950.deviceAddress.isBlank())
        assertEquals("FA:00", preferences.state.value.ipixelFa.deviceAddress)
    }
}
