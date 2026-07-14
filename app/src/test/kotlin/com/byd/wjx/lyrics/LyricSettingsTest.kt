package com.byd.wjx.lyrics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricSettingsTest {
    @Test
    fun displayStyle_migratesInvalidColorPresetToDefaultPalette() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("lyric_color_preset_id", "green")
            .putInt("lyric_color_current", 0xFF00FF00.toInt())
            .apply()

        val settings = LyricSettings(context)
        val expected = LyricStylePresets.color("jade_gold")
        val style = settings.displayStyle()

        assertEquals("jade_gold", settings.colorPresetId)
        assertEquals(expected.sungColor, style.sungColor)
        assertEquals(expected.currentColor, style.currentColor)
        assertEquals(expected.upcomingColor, style.upcomingColor)
    }

    @Test
    fun clusterLineCount_defaultsToFiveAndClampsToSupportedRange() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val settings = LyricSettings(context)

        assertEquals(5, settings.clusterLineCount)
        settings.clusterLineCount = 0
        assertEquals(1, settings.clusterLineCount)
        settings.clusterLineCount = 9
        assertEquals(5, settings.clusterLineCount)
    }
}
