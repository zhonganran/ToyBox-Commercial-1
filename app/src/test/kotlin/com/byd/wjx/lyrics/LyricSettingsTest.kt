package com.byd.wjx.lyrics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricSettingsTest {
    @Test
    fun desktopAndClusterLineCountsSaveIndependentlyAndClamp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val settings = LyricSettings(context)

        assertEquals(5, settings.desktopLineCount)
        assertEquals(5, settings.clusterLineCount)

        settings.desktopLineCount = 0
        assertEquals(1, settings.desktopLineCount)
        assertEquals(5, settings.clusterLineCount)

        settings.clusterLineCount = 9
        assertEquals(1, settings.desktopLineCount)
        assertEquals(5, settings.clusterLineCount)
    }

    @Test
    fun desktopAndClusterStylesAndOffsetsSaveIndependently() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val settings = LyricSettings(context)

        val green = LyricSolidColorPalette.colors.first { it.id == "green" }.color
        val pink = LyricSolidColorPalette.colors.first { it.id == "pink" }.color
        settings.desktopTextColor = green
        settings.desktopHighlightColor = pink
        settings.desktopFontStyleId = "rounded"
        settings.desktopMotionPresetId = "bounce"
        settings.desktopSyncOffsetMs = 350

        assertEquals(green, settings.displayStyle().sungColor)
        assertEquals(pink, settings.displayStyle().currentColor)
        assertEquals("rounded", settings.displayStyle().font.id)
        assertEquals("bounce", settings.displayStyle().motion.id)
        assertEquals(350, settings.desktopSyncOffsetMs)

        assertEquals(LyricSettings.DEFAULT_TEXT_COLOR, settings.clusterDisplayStyle().sungColor)
        assertEquals(LyricSettings.DEFAULT_HIGHLIGHT_COLOR, settings.clusterDisplayStyle().currentColor)
        assertEquals("soft", settings.clusterDisplayStyle().font.id)
        assertEquals("soft", settings.clusterDisplayStyle().motion.id)
        assertEquals(0, settings.clusterSyncOffsetMs)
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
