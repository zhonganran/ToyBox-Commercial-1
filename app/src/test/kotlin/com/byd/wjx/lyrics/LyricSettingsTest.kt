package com.byd.wjx.lyrics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricSettingsTest {
    @Test
    fun sharedLineCount_defaultsToFiveAndClampsToSupportedRange() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val settings = LyricSettings(context)

        assertEquals(5, settings.sharedLineCount)
        settings.sharedLineCount = 0
        assertEquals(1, settings.sharedLineCount)
        settings.sharedLineCount = 9
        assertEquals(5, settings.sharedLineCount)
    }

    @Test
    fun sharedStyleIsReadByBothLyricRenderers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val settings = LyricSettings(context)

        settings.sharedTextColor = LyricSolidColorPalette.colors.first { it.id == "green" }.color
        settings.sharedHighlightColor = LyricSolidColorPalette.colors.first { it.id == "yellow" }.color
        settings.sharedFontStyleId = "soft"
        settings.sharedMotionPresetId = "soft"

        assertEquals(settings.displayStyle(), settings.clusterDisplayStyle())
    }

    @Test
    fun desktopAndClusterFontScalesSaveIndependently() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .commit()
        val settings = LyricSettings(context)

        assertEquals(1f, settings.desktopFontScale, 0.001f)
        assertEquals(1f, settings.clusterFontScale, 0.001f)

        settings.desktopFontScale = 1.4f
        assertEquals(1.4f, settings.desktopFontScale, 0.001f)
        assertEquals(1f, settings.clusterFontScale, 0.001f)

        settings.clusterFontScale = 0.7f
        assertEquals(1.4f, settings.desktopFontScale, 0.001f)
        assertEquals(0.7f, settings.clusterFontScale, 0.001f)
    }
}
