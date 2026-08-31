package com.byd.wjx.camera.blindspot

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
class BlindSpotPreferencesStorageTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BlindSpotPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `rear preview channel is selectable and stays independent`() {
        val preferences = BlindSpotPreferences(context)

        preferences.setPreviewIndexes(left = 2, right = 3, rear = 6)

        val snapshot = preferences.snapshot()
        assertEquals(2, snapshot.left.previewIndex)
        assertEquals(3, snapshot.right.previewIndex)
        assertEquals(6, snapshot.rear.previewIndex)
    }

    @Test
    fun `shortcut toggle enables master feature and only closes rear stream`() {
        val preferences = BlindSpotPreferences(context)

        assertTrue(preferences.toggleRearStream())
        assertTrue(preferences.snapshot().enabled)
        assertTrue(preferences.snapshot().rearStreamEnabled)

        assertFalse(preferences.toggleRearStream())
        assertTrue(preferences.snapshot().enabled)
        assertFalse(preferences.snapshot().rearStreamEnabled)
    }

    @Test
    fun `radar auto popup settings persist and cooldown is clamped to one through sixty seconds`() {
        val preferences = BlindSpotPreferences(context)

        preferences.setBsdAutoPopup(true)
        preferences.setBsdAutoCooldownSeconds(12)

        assertTrue(preferences.snapshot().bsdAutoPopup)
        assertEquals(12, preferences.snapshot().bsdAutoCooldownSeconds)

        preferences.setBsdAutoCooldownSeconds(2)
        assertEquals(2, preferences.snapshot().bsdAutoCooldownSeconds)

        preferences.setBsdAutoCooldownSeconds(3)
        assertEquals(3, preferences.snapshot().bsdAutoCooldownSeconds)

        preferences.setBsdAutoCooldownSeconds(100)
        assertEquals(60, preferences.snapshot().bsdAutoCooldownSeconds)

        preferences.setBsdAutoCooldownSeconds(1)
        assertEquals(1, preferences.snapshot().bsdAutoCooldownSeconds)
    }
}
