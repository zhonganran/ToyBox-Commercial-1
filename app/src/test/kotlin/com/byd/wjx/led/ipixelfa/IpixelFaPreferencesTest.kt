package com.byd.wjx.led.ipixelfa

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.led.LedBrightnessSchedule
import com.byd.wjx.led.LedLinkageTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IpixelFaPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(IpixelFaPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `fresh FA settings include default linkage rules`() {
        val rules = IpixelFaPreferences(context).state.value.rules

        assertEquals(11, rules.size)
        assertEquals(
            listOf(
                "default-light-hazard",
                "default-auto-parking",
                "default-light-reverse",
                "default-gear-reverse",
                "default-light-left-turn",
                "default-light-right-turn",
                "default-door-front-left",
                "default-door-front-right",
                "default-gear-park",
                "default-pilot-assist",
                "default-acc",
            ),
            rules.map { it.id },
        )
        assertEquals(IpixelFaPreset.GET_OFF, rules[6].steps.single().preset)
    }

    @Test
    fun `deleting every FA rule persists an intentionally empty list`() {
        val preferences = IpixelFaPreferences(context)
        preferences.saveRule(
            IpixelFaLinkageRule(
                id = "temporary",
                triggerType = LedLinkageTriggerType.LIGHT,
                conditionValue = "hazard",
                steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.HAZARD_WARNING)),
            )
        )
        preferences.state.value.rules.forEach { preferences.deleteRule(it.id) }

        assertTrue(preferences.state.value.rules.isEmpty())
        assertTrue(IpixelFaPreferences(context).state.value.rules.isEmpty())
    }

    @Test
    fun `explicit empty rule JSON remains empty`() {
        context.getSharedPreferences(IpixelFaPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(IpixelFaPreferences.KEY_RULES, "[]")
            .commit()

        assertTrue(IpixelFaPreferences(context).state.value.rules.isEmpty())
    }

    @Test
    fun `FA rule edits persist and duplicate conditions are rejected`() {
        val preferences = IpixelFaPreferences(context)
        val custom = IpixelFaLinkageRule(
            id = "custom",
            triggerType = LedLinkageTriggerType.SPEED,
            conditionValue = "gt_100",
            steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.AUTOPILOT)),
        )

        assertTrue(preferences.saveRule(custom))
        assertFalse(preferences.saveRule(custom.copy(id = "duplicate")))
        assertEquals(custom.normalized(), IpixelFaPreferences(context).state.value.rules.last())
    }

    @Test
    fun `corrupt FA rule JSON restores an empty rule list`() {
        context.getSharedPreferences(IpixelFaPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(IpixelFaPreferences.KEY_RULES, "broken")
            .commit()

        assertTrue(IpixelFaPreferences(context).state.value.rules.isEmpty())
    }

    @Test
    fun `FA binding and brightness stay inside the FA preference file`() {
        val preferences = IpixelFaPreferences(context)
        preferences.bindDevice("FA:00", "FA LED")
        preferences.setBrightness(35)

        val snapshot = IpixelFaPreferences(context).state.value
        assertTrue(snapshot.enabled)
        assertEquals("FA:00", snapshot.deviceAddress)
        assertEquals("FA LED", snapshot.deviceName)
        assertEquals(35, snapshot.brightness)
    }

    @Test
    fun `FA day night brightness schedule persists and clamps values`() {
        val preferences = IpixelFaPreferences(context)
        preferences.setBrightnessSchedule(
            LedBrightnessSchedule(
                enabled = true,
                dayStartMinute = -5,
                nightStartMinute = 2_000,
                dayBrightness = 150,
                nightBrightness = -10,
            ),
        )

        assertEquals(
            LedBrightnessSchedule(
                enabled = true,
                dayStartMinute = 0,
                nightStartMinute = 1_439,
                dayBrightness = 100,
                nightBrightness = 0,
            ),
            IpixelFaPreferences(context).state.value.brightnessSchedule,
        )
    }
}
