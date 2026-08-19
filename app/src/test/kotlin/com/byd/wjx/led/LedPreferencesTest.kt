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
            .putString(LedPreferences.KEY_RULES, "broken")
            .commit()

        assertEquals(LedLinkageDefaults.rules, LedPreferences(context).state.value.rules)
    }

    @Test
    fun `detected protocol bindings keep independent devices and brightness`() {
        val preferences = LedPreferences(context)
        preferences.bindDetectedDevice(LedProtocolKind.IPIXEL_FA, "FA:00", "FA LED")
        preferences.setBrightness(LedProtocolKind.IPIXEL_FA, 35)
        preferences.bindDetectedDevice(LedProtocolKind.A950, "A9:50", "A950 LED")
        preferences.setBrightness(LedProtocolKind.A950, 85)
        preferences.bindDetectedDevice(LedProtocolKind.DUDU_FFF0, "FF:F0", "LedSpace")
        preferences.setSceneSlot(LedProtocolKind.DUDU_FFF0, LedScene.LEFT_TURN, 17)

        val snapshot = LedPreferences(context).state.value
        assertEquals(LedProtocolKind.DUDU_FFF0, snapshot.selectedProtocol)
        assertEquals("FA:00", snapshot.ipixelFa.deviceAddress)
        assertEquals(35, snapshot.ipixelFa.brightness)
        assertEquals("A9:50", snapshot.a950.deviceAddress)
        assertEquals(85, snapshot.a950.brightness)
        assertEquals("FF:F0", snapshot.duduFff0.deviceAddress)
        assertEquals(100, snapshot.duduFff0.brightness)
        assertEquals(17, snapshot.duduFff0.sceneSlots[LedScene.LEFT_TURN])
        assertEquals(1, snapshot.duduFff0.sceneSlots[LedScene.RIGHT_TURN])

        preferences.clearDevice(LedProtocolKind.A950)
        assertTrue(preferences.state.value.a950.deviceAddress.isBlank())
        assertEquals("FA:00", preferences.state.value.ipixelFa.deviceAddress)
        assertEquals("FF:F0", preferences.state.value.duduFff0.deviceAddress)
    }

    @Test
    fun `Dudu FFF0 scene slots are clamped to the device range`() {
        val preferences = LedPreferences(context)

        preferences.setSceneSlot(LedProtocolKind.DUDU_FFF0, LedScene.DEFAULT, 0)
        preferences.setSceneSlot(LedProtocolKind.DUDU_FFF0, LedScene.HAZARD, 99)

        val profile = LedPreferences(context).state.value.duduFff0
        assertEquals(1, profile.sceneSlots[LedScene.DEFAULT])
        assertEquals(60, profile.sceneSlots[LedScene.HAZARD])
    }
}
