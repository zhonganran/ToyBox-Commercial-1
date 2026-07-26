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
    fun `legacy bound devices migrate to automatic protocol detection`() {
        context.getSharedPreferences(LedPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("device_address", "AA:BB:CC:DD:EE:FF")
            .putString("device_name", "LED")
            .commit()

        assertEquals(LedDeviceKind.UNKNOWN, LedPreferences(context).state.value.deviceKind)
    }

    @Test
    fun `detected device kind persists with the binding`() {
        val preferences = LedPreferences(context)
        preferences.setDevice("AA:BB:CC:DD:EE:FF", "LED", LedDeviceKind.A950)

        assertEquals(LedDeviceKind.A950, LedPreferences(context).state.value.deviceKind)
        preferences.clearDevice()
        assertEquals(LedDeviceKind.UNKNOWN, preferences.state.value.deviceKind)
    }
}
