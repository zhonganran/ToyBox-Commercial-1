package com.byd.wjx.led.mafu

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
class MafuPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(MafuPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `fresh Mafu settings use APK defaults`() {
        val snapshot = MafuPreferences(context).state.value
        assertFalse(snapshot.enabled)
        assertEquals(12, snapshot.brightness)
        assertEquals(MafuRemoteModel.YS_17_KEY, snapshot.remoteModel)
        assertTrue(snapshot.rules.isEmpty())
    }

    @Test
    fun `binding brightness and remote model stay in the independent file`() {
        val preferences = MafuPreferences(context)
        preferences.bindDevice("YS:00", "YS-LED")
        preferences.setBrightness(99)
        preferences.setRemoteModel(MafuRemoteModel.YS_9_KEY)

        val snapshot = MafuPreferences(context).state.value
        assertTrue(snapshot.enabled)
        assertEquals("YS:00", snapshot.deviceAddress)
        assertEquals(15, snapshot.brightness)
        assertEquals(MafuRemoteModel.YS_9_KEY, snapshot.remoteModel)
    }

    @Test
    fun `switching to a smaller remote normalizes saved rule keys`() {
        val preferences = MafuPreferences(context)
        preferences.setRemoteModel(MafuRemoteModel.YS_20_KEY)
        preferences.saveRule(
            MafuLinkageRule(
                id = "rule",
                triggerType = LedLinkageTriggerType.LIGHT,
                conditionValue = "hazard",
                steps = listOf(MafuLinkageStep(MafuRemoteKey.KEY_20)),
            )
        )

        preferences.setRemoteModel(MafuRemoteModel.YS_9_KEY)

        assertEquals(MafuRemoteKey.KEY_1, preferences.state.value.rules.single().steps.single().remoteKey)
    }

    @Test
    fun `Mafu day night brightness schedule persists in protocol range`() {
        val preferences = MafuPreferences(context)
        val schedule = LedBrightnessSchedule(
            enabled = true,
            dayStartMinute = 8 * 60,
            nightStartMinute = 21 * 60 + 30,
            dayBrightness = 14,
            nightBrightness = 3,
        )

        preferences.setBrightnessSchedule(schedule)

        assertEquals(schedule, MafuPreferences(context).state.value.brightnessSchedule)
    }

}
