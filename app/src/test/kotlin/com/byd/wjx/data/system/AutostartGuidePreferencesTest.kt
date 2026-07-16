package com.byd.wjx.data.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AutostartGuidePreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun `guide remains pending until user confirms current version`() {
        assertTrue(AutostartGuidePreferences.shouldShow(context))

        AutostartGuidePreferences.markConfirmed(context)

        assertFalse(AutostartGuidePreferences.shouldShow(context))
    }

    private fun clearPreferences() {
        context.getSharedPreferences("autostart_guide", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
