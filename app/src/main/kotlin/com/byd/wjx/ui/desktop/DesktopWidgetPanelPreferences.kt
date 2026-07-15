package com.byd.wjx.ui.desktop

import android.content.Context

internal enum class DesktopWidgetPanel(val preferenceKey: String) {
    SOUND("sound_controls_expanded"),
    AUTO_DOOR("auto_door_controls_expanded"),
    LYRIC("lyric_controls_expanded"),
    BATTERY("widget_controls_expanded"),
}

/** Single owner of the desktop editor's panel expansion persistence schema. */
internal class DesktopWidgetPanelPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun initialExpanded(panel: DesktopWidgetPanel, featureEnabled: Boolean): Boolean =
        featureEnabled && preferences.getBoolean(panel.preferenceKey, true)

    fun setExpanded(panel: DesktopWidgetPanel, expanded: Boolean) {
        preferences.edit().putBoolean(panel.preferenceKey, expanded).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "desktop_widget_controls"
    }
}
