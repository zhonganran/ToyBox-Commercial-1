package com.byd.wjx.adbrescue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbRescuePreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(AdbRescuePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `guard defaults off and persists in device-protected storage`() {
        val preferences = AdbRescuePreferences(context)

        assertFalse(preferences.isGuardEnabled())
        assertTrue(preferences.setGuardEnabled(true))
        assertTrue(AdbRescuePreferences(context).isGuardEnabled())
    }
}
