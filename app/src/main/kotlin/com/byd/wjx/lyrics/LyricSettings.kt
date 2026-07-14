package com.byd.wjx.lyrics

import android.content.Context
import kotlin.math.roundToInt

class LyricSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var desktopEnabled: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_ENABLED, value).apply()

    var allowOnOtherApps: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_OTHER_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_OTHER_APPS, value).apply()

    var clusterEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLUSTER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLUSTER_ENABLED, value).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1f).coerceIn(FONT_MIN, FONT_MAX)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(FONT_MIN, FONT_MAX)).apply()

    var clusterFontScale: Float
        get() = prefs.getFloat(KEY_CLUSTER_FONT_SCALE, 0.78f).coerceIn(CLUSTER_FONT_MIN, CLUSTER_FONT_MAX)
        set(value) = prefs.edit().putFloat(KEY_CLUSTER_FONT_SCALE, value.coerceIn(CLUSTER_FONT_MIN, CLUSTER_FONT_MAX)).apply()

    var clusterLineCount: Int
        get() = prefs.getInt(KEY_CLUSTER_LINE_COUNT, CLUSTER_LINE_COUNT_DEFAULT)
            .coerceIn(CLUSTER_LINE_COUNT_MIN, CLUSTER_LINE_COUNT_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_CLUSTER_LINE_COUNT, value.coerceIn(CLUSTER_LINE_COUNT_MIN, CLUSTER_LINE_COUNT_MAX))
            .apply()

    var clusterWidthScale: Float
        get() = prefs.getFloat(KEY_CLUSTER_WIDTH_SCALE, 1f).coerceIn(CLUSTER_WIDTH_MIN, CLUSTER_WIDTH_MAX)
        set(value) = prefs.edit().putFloat(KEY_CLUSTER_WIDTH_SCALE, value.coerceIn(CLUSTER_WIDTH_MIN, CLUSTER_WIDTH_MAX)).apply()

    var clusterAlpha: Float
        get() = prefs.getFloat(KEY_CLUSTER_ALPHA, 1f).coerceIn(ALPHA_MIN, ALPHA_MAX)
        set(value) = prefs.edit().putFloat(KEY_CLUSTER_ALPHA, value.coerceIn(ALPHA_MIN, ALPHA_MAX)).apply()

    var clusterXPct: Int
        get() = prefs.getInt(KEY_CLUSTER_X_PCT, 50).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CLUSTER_X_PCT, value.coerceIn(0, 100)).apply()

    var clusterYPct: Int
        get() = prefs.getInt(KEY_CLUSTER_Y_PCT, 50).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CLUSTER_Y_PCT, value.coerceIn(0, 100)).apply()

    var syncOffsetMs: Int
        get() = prefs.getInt(KEY_SYNC_OFFSET_MS, 0).coerceIn(SYNC_MIN_MS, SYNC_MAX_MS)
        set(value) = prefs.edit().putInt(KEY_SYNC_OFFSET_MS, value.coerceIn(SYNC_MIN_MS, SYNC_MAX_MS)).apply()

    var fontStyleId: String
        get() = prefs.getString(KEY_FONT_STYLE_ID, "soft")?.takeIf { id ->
            LyricStylePresets.fonts.any { it.id == id }
        } ?: "soft"
        set(value) = prefs.edit().putString(KEY_FONT_STYLE_ID, LyricStylePresets.font(value).id).apply()

    var motionPresetId: String
        get() = prefs.getString(KEY_MOTION_PRESET_ID, "soft")?.takeIf { id ->
            LyricStylePresets.motions.any { it.id == id }
        } ?: "soft"
        set(value) = prefs.edit().putString(KEY_MOTION_PRESET_ID, LyricStylePresets.motion(value).id).apply()

    var colorPresetId: String
        get() = colorPreset().id
        set(value) {
            val preset = LyricStylePresets.color(value)
            prefs.edit()
                .putString(KEY_COLOR_PRESET_ID, preset.id)
                .putInt(KEY_COLOR_SUNG, preset.sungColor)
                .putInt(KEY_COLOR_CURRENT, preset.currentColor)
                .putInt(KEY_COLOR_UPCOMING, preset.upcomingColor)
                .apply()
        }

    var karaokeEnabled: Boolean
        get() = prefs.getBoolean(KEY_KARAOKE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_KARAOKE_ENABLED, value).apply()

    var desktopX: Int
        get() = prefs.getInt(KEY_DESKTOP_X, 0)
        set(value) = prefs.edit().putInt(KEY_DESKTOP_X, value).apply()

    var desktopY: Int
        get() = prefs.getInt(KEY_DESKTOP_Y, 0)
        set(value) = prefs.edit().putInt(KEY_DESKTOP_Y, value).apply()

    var desktopPositionInitialized: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_INITED, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_INITED, value).apply()

    fun resetDesktopPosition() {
        prefs.edit()
            .remove(KEY_DESKTOP_X)
            .remove(KEY_DESKTOP_Y)
            .putBoolean(KEY_DESKTOP_INITED, false)
            .apply()
    }

    fun resetClusterPosition() {
        prefs.edit()
            .putInt(KEY_CLUSTER_X_PCT, 50)
            .putInt(KEY_CLUSTER_Y_PCT, 50)
            .apply()
    }

    fun displayStyle(): LyricDisplayStyle {
        val color = colorPreset()
        return LyricDisplayStyle(
            font = LyricStylePresets.font(fontStyleId),
            motion = LyricStylePresets.motion(motionPresetId),
            sungColor = color.sungColor,
            currentColor = color.currentColor,
            upcomingColor = color.upcomingColor,
            karaokeEnabled = karaokeEnabled,
        )
    }

    private fun colorPreset(): LyricColorPreset {
        val rawId = prefs.getString(KEY_COLOR_PRESET_ID, DEFAULT_COLOR_PRESET_ID)
        val preset = LyricStylePresets.colors.firstOrNull { it.id == rawId }
        if (preset != null) return preset
        val fallback = LyricStylePresets.color(DEFAULT_COLOR_PRESET_ID)
        prefs.edit()
            .putString(KEY_COLOR_PRESET_ID, fallback.id)
            .putInt(KEY_COLOR_SUNG, fallback.sungColor)
            .putInt(KEY_COLOR_CURRENT, fallback.currentColor)
            .putInt(KEY_COLOR_UPCOMING, fallback.upcomingColor)
            .apply()
        return fallback
    }

    companion object {
        const val PREFS_NAME = "wjx_lyrics"

        const val FONT_MIN = 0.6f
        const val FONT_MAX = 2.4f
        const val CLUSTER_FONT_MIN = 0.45f
        const val CLUSTER_FONT_MAX = 1.6f
        const val CLUSTER_LINE_COUNT_MIN = 1
        const val CLUSTER_LINE_COUNT_MAX = 5
        const val CLUSTER_LINE_COUNT_DEFAULT = 5
        const val CLUSTER_WIDTH_MIN = 0.6f
        const val CLUSTER_WIDTH_MAX = 1.5f
        const val ALPHA_MIN = 0.3f
        const val ALPHA_MAX = 1f
        const val SYNC_MIN_MS = -800
        const val SYNC_MAX_MS = 800

        private const val KEY_DESKTOP_ENABLED = "lyric_enabled"
        private const val KEY_ALLOW_OTHER_APPS = "lyric_allow_on_other_apps"
        private const val KEY_CLUSTER_ENABLED = "lyric_cluster_enabled"
        private const val KEY_FONT_SCALE = "lyric_font_scale"
        private const val KEY_CLUSTER_FONT_SCALE = "lyric_cluster_font_scale"
        private const val KEY_CLUSTER_LINE_COUNT = "lyric_cluster_line_count"
        private const val KEY_CLUSTER_WIDTH_SCALE = "lyric_cluster_width_scale"
        private const val KEY_CLUSTER_ALPHA = "lyric_cluster_alpha"
        private const val KEY_CLUSTER_X_PCT = "lyric_cluster_x_pct"
        private const val KEY_CLUSTER_Y_PCT = "lyric_cluster_y_pct"
        private const val KEY_SYNC_OFFSET_MS = "lyric_sync_offset_ms"
        private const val KEY_FONT_STYLE_ID = "lyric_font_style_id"
        private const val KEY_MOTION_PRESET_ID = "lyric_motion_preset_id"
        private const val KEY_COLOR_PRESET_ID = "lyric_color_preset_id"
        private const val KEY_COLOR_SUNG = "lyric_color_sung"
        private const val KEY_COLOR_CURRENT = "lyric_color_current"
        private const val KEY_COLOR_UPCOMING = "lyric_color_upcoming"
        private const val KEY_KARAOKE_ENABLED = "lyric_karaoke_enabled"
        private const val KEY_DESKTOP_X = "lyric_x"
        private const val KEY_DESKTOP_Y = "lyric_y"
        private const val KEY_DESKTOP_INITED = "lyric_inited"
        private const val DEFAULT_COLOR_PRESET_ID = "jade_gold"

        fun pctText(value: Float): String = "${(value * 100).roundToInt()}%"

        fun formatOffset(value: Int): String = when {
            value > 0 -> "提前 ${value}ms"
            value < 0 -> "延后 ${-value}ms"
            else -> "0ms"
        }
    }
}
