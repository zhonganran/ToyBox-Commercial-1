package com.byd.wjx.lyrics

import android.content.Context

/** Independent desktop and cluster lyric presentation settings. */
class LyricSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var desktopEnabled: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_ENABLED, value).apply()

    var allowOnOtherApps: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_OTHER_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_OTHER_APPS, value).apply()

    var desktopTouchThrough: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_TOUCH_THROUGH, true)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_TOUCH_THROUGH, value).apply()

    var clusterEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLUSTER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLUSTER_ENABLED, value).apply()

    var desktopLineCount: Int
        get() = prefs.getInt(
            KEY_DESKTOP_LINE_COUNT,
            prefs.getInt(KEY_LEGACY_SHARED_LINE_COUNT, LINE_COUNT_DEFAULT),
        ).coerceIn(LINE_COUNT_MIN, LINE_COUNT_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_DESKTOP_LINE_COUNT, value.coerceIn(LINE_COUNT_MIN, LINE_COUNT_MAX))
            .apply()

    var clusterLineCount: Int
        get() = prefs.getInt(
            KEY_CLUSTER_LINE_COUNT,
            prefs.getInt(KEY_LEGACY_SHARED_LINE_COUNT, LINE_COUNT_DEFAULT),
        ).coerceIn(LINE_COUNT_MIN, LINE_COUNT_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_CLUSTER_LINE_COUNT, value.coerceIn(LINE_COUNT_MIN, LINE_COUNT_MAX))
            .apply()

    var desktopFontScale: Float
        get() = prefs.getFloat(KEY_DESKTOP_FONT_SCALE, 1f).coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
        set(value) = prefs.edit()
            .putFloat(KEY_DESKTOP_FONT_SCALE, value.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX))
            .apply()

    var clusterFontScale: Float
        get() = prefs.getFloat(KEY_CLUSTER_FONT_SCALE, 1f).coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
        set(value) = prefs.edit()
            .putFloat(KEY_CLUSTER_FONT_SCALE, value.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX))
            .apply()

    var desktopTextColor: Int
        get() = LyricSolidColorPalette.normalize(
            prefs.getInt(
                KEY_DESKTOP_TEXT_COLOR,
                prefs.getInt(KEY_LEGACY_SHARED_TEXT_COLOR, DEFAULT_TEXT_COLOR),
            ),
            DEFAULT_TEXT_COLOR,
        )
        set(value) = prefs.edit()
            .putInt(KEY_DESKTOP_TEXT_COLOR, LyricSolidColorPalette.normalize(value, DEFAULT_TEXT_COLOR))
            .apply()

    var clusterTextColor: Int
        get() = LyricSolidColorPalette.normalize(
            prefs.getInt(
                KEY_CLUSTER_TEXT_COLOR,
                prefs.getInt(KEY_LEGACY_SHARED_TEXT_COLOR, DEFAULT_TEXT_COLOR),
            ),
            DEFAULT_TEXT_COLOR,
        )
        set(value) = prefs.edit()
            .putInt(KEY_CLUSTER_TEXT_COLOR, LyricSolidColorPalette.normalize(value, DEFAULT_TEXT_COLOR))
            .apply()

    var desktopHighlightColor: Int
        get() = LyricSolidColorPalette.normalize(
            prefs.getInt(
                KEY_DESKTOP_HIGHLIGHT_COLOR,
                prefs.getInt(KEY_LEGACY_SHARED_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR),
            ),
            DEFAULT_HIGHLIGHT_COLOR,
        )
        set(value) = prefs.edit()
            .putInt(KEY_DESKTOP_HIGHLIGHT_COLOR, LyricSolidColorPalette.normalize(value, DEFAULT_HIGHLIGHT_COLOR))
            .apply()

    var clusterHighlightColor: Int
        get() = LyricSolidColorPalette.normalize(
            prefs.getInt(
                KEY_CLUSTER_HIGHLIGHT_COLOR,
                prefs.getInt(KEY_LEGACY_SHARED_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR),
            ),
            DEFAULT_HIGHLIGHT_COLOR,
        )
        set(value) = prefs.edit()
            .putInt(KEY_CLUSTER_HIGHLIGHT_COLOR, LyricSolidColorPalette.normalize(value, DEFAULT_HIGHLIGHT_COLOR))
            .apply()

    var desktopFontStyleId: String
        get() = prefs.getString(
            KEY_DESKTOP_FONT_STYLE_ID,
            prefs.getString(KEY_LEGACY_SHARED_FONT_STYLE_ID, DEFAULT_FONT_STYLE_ID),
        )
            ?.takeIf { id -> LyricStylePresets.fonts.any { it.id == id } }
            ?: DEFAULT_FONT_STYLE_ID
        set(value) = prefs.edit()
            .putString(KEY_DESKTOP_FONT_STYLE_ID, LyricStylePresets.font(value).id)
            .apply()

    var clusterFontStyleId: String
        get() = prefs.getString(
            KEY_CLUSTER_FONT_STYLE_ID,
            prefs.getString(KEY_LEGACY_SHARED_FONT_STYLE_ID, DEFAULT_FONT_STYLE_ID),
        )
            ?.takeIf { id -> LyricStylePresets.fonts.any { it.id == id } }
            ?: DEFAULT_FONT_STYLE_ID
        set(value) = prefs.edit()
            .putString(KEY_CLUSTER_FONT_STYLE_ID, LyricStylePresets.font(value).id)
            .apply()

    var desktopMotionPresetId: String
        get() = prefs.getString(
            KEY_DESKTOP_MOTION_PRESET_ID,
            prefs.getString(KEY_LEGACY_SHARED_MOTION_PRESET_ID, DEFAULT_MOTION_PRESET_ID),
        )
            ?.takeIf { id -> LyricStylePresets.motions.any { it.id == id } }
            ?: DEFAULT_MOTION_PRESET_ID
        set(value) = prefs.edit()
            .putString(KEY_DESKTOP_MOTION_PRESET_ID, LyricStylePresets.motion(value).id)
            .apply()

    var clusterMotionPresetId: String
        get() = prefs.getString(
            KEY_CLUSTER_MOTION_PRESET_ID,
            prefs.getString(KEY_LEGACY_SHARED_MOTION_PRESET_ID, DEFAULT_MOTION_PRESET_ID),
        )
            ?.takeIf { id -> LyricStylePresets.motions.any { it.id == id } }
            ?: DEFAULT_MOTION_PRESET_ID
        set(value) = prefs.edit()
            .putString(KEY_CLUSTER_MOTION_PRESET_ID, LyricStylePresets.motion(value).id)
            .apply()

    var clusterAlpha: Float
        get() = prefs.getFloat(KEY_CLUSTER_ALPHA, 1f).coerceIn(ALPHA_MIN, ALPHA_MAX)
        set(value) = prefs.edit().putFloat(KEY_CLUSTER_ALPHA, value.coerceIn(ALPHA_MIN, ALPHA_MAX)).apply()

    var clusterXPct: Int
        get() = prefs.getInt(KEY_CLUSTER_X_PCT, 50).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CLUSTER_X_PCT, value.coerceIn(0, 100)).apply()

    var clusterYPct: Int
        get() = prefs.getInt(KEY_CLUSTER_Y_PCT, 50).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CLUSTER_Y_PCT, value.coerceIn(0, 100)).apply()

    var desktopSyncOffsetMs: Int
        get() = prefs.getInt(
            KEY_DESKTOP_SYNC_OFFSET_MS,
            prefs.getInt(KEY_LEGACY_SYNC_OFFSET_MS, 0),
        ).coerceIn(SYNC_MIN_MS, SYNC_MAX_MS)
        set(value) = prefs.edit()
            .putInt(KEY_DESKTOP_SYNC_OFFSET_MS, value.coerceIn(SYNC_MIN_MS, SYNC_MAX_MS))
            .apply()

    var clusterSyncOffsetMs: Int
        get() = prefs.getInt(
            KEY_CLUSTER_SYNC_OFFSET_MS,
            prefs.getInt(KEY_LEGACY_SYNC_OFFSET_MS, 0),
        ).coerceIn(SYNC_MIN_MS, SYNC_MAX_MS)
        set(value) = prefs.edit()
            .putInt(KEY_CLUSTER_SYNC_OFFSET_MS, value.coerceIn(SYNC_MIN_MS, SYNC_MAX_MS))
            .apply()

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

    fun displayStyle(): LyricDisplayStyle = LyricDisplayStyle(
        font = LyricStylePresets.font(desktopFontStyleId),
        motion = LyricStylePresets.motion(desktopMotionPresetId),
        sungColor = desktopTextColor,
        currentColor = desktopHighlightColor,
        upcomingColor = desktopTextColor,
        karaokeEnabled = true,
    )

    fun clusterDisplayStyle(): LyricDisplayStyle = LyricDisplayStyle(
        font = LyricStylePresets.font(clusterFontStyleId),
        motion = LyricStylePresets.motion(clusterMotionPresetId),
        sungColor = clusterTextColor,
        currentColor = clusterHighlightColor,
        upcomingColor = clusterTextColor,
        karaokeEnabled = true,
    )

    companion object {
        const val PREFS_NAME = "wjx_lyrics"

        const val FONT_SCALE_MIN = 0.6f
        const val FONT_SCALE_MAX = 1.6f
        const val LINE_COUNT_MIN = 1
        const val LINE_COUNT_MAX = 5
        const val LINE_COUNT_DEFAULT = 5
        const val ALPHA_MIN = 0.3f
        const val ALPHA_MAX = 1f
        const val SYNC_MIN_MS = -800
        const val SYNC_MAX_MS = 800

        private const val KEY_DESKTOP_ENABLED = "lyric_enabled"
        private const val KEY_ALLOW_OTHER_APPS = "lyric_allow_on_other_apps"
        private const val KEY_DESKTOP_TOUCH_THROUGH = "lyric_desktop_touch_through"
        private const val KEY_CLUSTER_ENABLED = "lyric_cluster_enabled"
        private const val KEY_DESKTOP_LINE_COUNT = "lyric_desktop_line_count"
        private const val KEY_CLUSTER_LINE_COUNT = "lyric_cluster_line_count"
        private const val KEY_DESKTOP_FONT_SCALE = "lyric_desktop_font_scale"
        private const val KEY_CLUSTER_FONT_SCALE = "lyric_cluster_font_scale"
        private const val KEY_DESKTOP_TEXT_COLOR = "lyric_desktop_text_color"
        private const val KEY_CLUSTER_TEXT_COLOR = "lyric_cluster_text_color"
        private const val KEY_DESKTOP_HIGHLIGHT_COLOR = "lyric_desktop_highlight_color"
        private const val KEY_CLUSTER_HIGHLIGHT_COLOR = "lyric_cluster_highlight_color"
        private const val KEY_DESKTOP_FONT_STYLE_ID = "lyric_desktop_font_style_id"
        private const val KEY_CLUSTER_FONT_STYLE_ID = "lyric_cluster_font_style_id"
        private const val KEY_DESKTOP_MOTION_PRESET_ID = "lyric_desktop_motion_preset_id"
        private const val KEY_CLUSTER_MOTION_PRESET_ID = "lyric_cluster_motion_preset_id"
        private const val KEY_CLUSTER_ALPHA = "lyric_cluster_alpha"
        private const val KEY_CLUSTER_X_PCT = "lyric_cluster_x_pct"
        private const val KEY_CLUSTER_Y_PCT = "lyric_cluster_y_pct"
        private const val KEY_DESKTOP_SYNC_OFFSET_MS = "lyric_desktop_sync_offset_ms"
        private const val KEY_CLUSTER_SYNC_OFFSET_MS = "lyric_cluster_sync_offset_ms"
        private const val KEY_DESKTOP_X = "lyric_x"
        private const val KEY_DESKTOP_Y = "lyric_y"
        private const val KEY_DESKTOP_INITED = "lyric_inited"
        private const val KEY_LEGACY_SHARED_LINE_COUNT = "lyric_shared_line_count"
        private const val KEY_LEGACY_SHARED_TEXT_COLOR = "lyric_shared_text_color"
        private const val KEY_LEGACY_SHARED_HIGHLIGHT_COLOR = "lyric_shared_highlight_color"
        private const val KEY_LEGACY_SHARED_FONT_STYLE_ID = "lyric_shared_font_style_id"
        private const val KEY_LEGACY_SHARED_MOTION_PRESET_ID = "lyric_shared_motion_preset_id"
        private const val KEY_LEGACY_SYNC_OFFSET_MS = "lyric_sync_offset_ms"
        private const val DEFAULT_FONT_STYLE_ID = "soft"
        private const val DEFAULT_MOTION_PRESET_ID = "soft"
        val DEFAULT_TEXT_COLOR = LyricSolidColorPalette.colors.first { it.id == "white" }.color
        val DEFAULT_HIGHLIGHT_COLOR = LyricSolidColorPalette.colors.first { it.id == "yellow" }.color

        fun formatOffset(value: Int): String = when {
            value > 0 -> "提前 ${value}ms"
            value < 0 -> "延后 ${-value}ms"
            else -> "0ms"
        }
    }
}
