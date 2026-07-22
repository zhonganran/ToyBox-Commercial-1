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
    fun legacySharedStyleSeedsBothIndependentTargets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val green = LyricSolidColorPalette.colors.first { it.id == "green" }.color
        val yellow = LyricSolidColorPalette.colors.first { it.id == "yellow" }.color
        context.getSharedPreferences(LyricSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putInt("lyric_shared_line_count", 3)
            .putInt("lyric_shared_text_color", green)
            .putInt("lyric_shared_highlight_color", yellow)
            .putString("lyric_shared_font_style_id", "rounded")
            .putString("lyric_shared_motion_preset_id", "glow")
            .putInt("lyric_sync_offset_ms", -250)
            .commit()
        val settings = LyricSettings(context)

        assertEquals(3, settings.desktopLineCount)
        assertEquals(3, settings.clusterLineCount)
        assertEquals(settings.displayStyle(), settings.clusterDisplayStyle())
        assertEquals(-250, settings.desktopSyncOffsetMs)
        assertEquals(-250, settings.clusterSyncOffsetMs)

        settings.desktopFontStyleId = "caption"
        settings.desktopSyncOffsetMs = 100
        assertEquals("caption", settings.displayStyle().font.id)
        assertEquals("rounded", settings.clusterDisplayStyle().font.id)
        assertEquals(100, settings.desktopSyncOffsetMs)
        assertEquals(-250, settings.clusterSyncOffsetMs)
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
