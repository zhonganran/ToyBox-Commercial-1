package com.byd.wjx.ui.lyrics

import android.content.Context

internal enum class LyricDisplayPanel(val preferenceKey: String) {
    LYRIC("lyric_controls_expanded"),
}

/** Single owner of the desktop editor's panel expansion persistence schema. */
internal class LyricDisplayPanelPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun initialExpanded(panel: LyricDisplayPanel, featureEnabled: Boolean): Boolean =
        featureEnabled && preferences.getBoolean(panel.preferenceKey, true)

    fun setExpanded(panel: LyricDisplayPanel, expanded: Boolean) {
        preferences.edit().putBoolean(panel.preferenceKey, expanded).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "lyric_display_controls"
    }
}
