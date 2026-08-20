package com.byd.wjx.led.a950

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.led.LedLinkageTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class A950PreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(A950Preferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(LEGACY_MIXED_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `fresh A950 settings start with no linkage rules`() {
        val rules = A950Preferences(context).state.value.rules

        assertTrue(rules.isEmpty())
    }

    @Test
    fun `deleting every A950 rule persists an intentionally empty list`() {
        val preferences = A950Preferences(context)
        preferences.saveRule(
            A950LinkageRule(
                id = "temporary",
                triggerType = LedLinkageTriggerType.LIGHT,
                conditionValue = "hazard",
                steps = listOf(A950LinkageStep(A950Preset.HAZARD_WARNING)),
            )
        )
        preferences.state.value.rules.forEach { preferences.deleteRule(it.id) }

        assertTrue(preferences.state.value.rules.isEmpty())
        assertTrue(A950Preferences(context).state.value.rules.isEmpty())
    }

    @Test
    fun `A950 rule edits persist and duplicate conditions are rejected`() {
        val preferences = A950Preferences(context)
        val custom = A950LinkageRule(
            id = "custom",
            triggerType = LedLinkageTriggerType.SPEED,
            conditionValue = "gt_100",
            steps = listOf(A950LinkageStep(A950Preset.AUTOPILOT, 9)),
        )

        assertTrue(preferences.saveRule(custom))
        assertFalse(preferences.saveRule(custom.copy(id = "duplicate")))
        assertEquals(custom, A950Preferences(context).state.value.rules.last())
    }

    @Test
    fun `corrupt A950 rule JSON restores an empty rule list`() {
        context.getSharedPreferences(A950Preferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(A950Preferences.KEY_RULES, "broken")
            .commit()

        assertTrue(A950Preferences(context).state.value.rules.isEmpty())
    }

    @Test
    fun `A950 binding and brightness stay inside the A950 preference file`() {
        val preferences = A950Preferences(context)
        preferences.bindDevice("A950:00", "A950 LED")
        preferences.setBrightness(35)

        val snapshot = A950Preferences(context).state.value
        assertTrue(snapshot.enabled)
        assertEquals("A950:00", snapshot.deviceAddress)
        assertEquals("A950 LED", snapshot.deviceName)
        assertEquals(35, snapshot.brightness)
        assertTrue(
            context.getSharedPreferences(LEGACY_MIXED_PREFS, Context.MODE_PRIVATE)
                .all
                .isEmpty(),
        )
    }

    @Test
    fun `legacy mixed LED settings are not migrated into A950`() {
        context.getSharedPreferences(LEGACY_MIXED_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("a950_enabled", true)
            .putString("a950_device_address", "OLD:A950")
            .putInt("a950_brightness", 99)
            .commit()

        val snapshot = A950Preferences(context).state.value
        assertFalse(snapshot.enabled)
        assertTrue(snapshot.deviceAddress.isBlank())
        assertEquals(70, snapshot.brightness)
    }

    private companion object {
        const val LEGACY_MIXED_PREFS = "wjx_led_link"
    }
}
