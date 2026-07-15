package com.byd.wjx.cluster

import android.content.Context

/**
 * Single schema owner for cluster-projection preferences.
 *
 * Callers may keep their own lightweight wrapper, but key names, defaults and multi-field writes
 * live here so UI, accessibility and projection runtime cannot silently drift apart.
 */
internal class ClusterProjectionPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(): ClusterProjectionPreferenceSnapshot {
        val layoutName = prefs.getString(KEY_PROJECTION_LAYOUT, ProjectionLayout.INDEPENDENT.name)
        val layout = runCatching {
            ProjectionLayout.valueOf(layoutName ?: ProjectionLayout.INDEPENDENT.name)
        }.getOrDefault(ProjectionLayout.INDEPENDENT)
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, NAVI_PACKAGE) ?: NAVI_PACKAGE
        return ClusterProjectionPreferenceSnapshot(
            enabled = prefs.getBoolean(KEY_MIRROR_ENABLED, false),
            triggerKeyCode = prefs.getInt(KEY_TRIGGER_KEYCODE, DEFAULT_TRIGGER_KEYCODE),
            widthPct = prefs.getInt(KEY_WIDTH_PCT, MAX_PROJECTION_PCT),
            heightPct = prefs.getInt(KEY_HEIGHT_PCT, MAX_PROJECTION_PCT),
            offsetXPct = prefs.getInt(KEY_OFFSET_X_PCT, CENTER_OFFSET_PCT),
            offsetYPct = prefs.getInt(KEY_OFFSET_Y_PCT, CENTER_OFFSET_PCT),
            scalePct = prefs.getInt(KEY_SCALE_PCT, DEFAULT_SCALE_PCT),
            layout = layout,
            targetPackage = targetPackage,
            targetLabel = prefs.getString(KEY_TARGET_LABEL, null),
            lastVirtualDisplayId = prefs.getInt(KEY_LAST_VD_ID, NO_VIRTUAL_DISPLAY),
        )
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_MIRROR_ENABLED, false)

    fun triggerKeyCode(): Int = prefs.getInt(KEY_TRIGGER_KEYCODE, DEFAULT_TRIGGER_KEYCODE)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIRROR_ENABLED, enabled).apply()
    }

    fun setTriggerKeyCode(keyCode: Int) {
        prefs.edit().putInt(KEY_TRIGGER_KEYCODE, keyCode).apply()
    }

    fun setLayout(layout: ProjectionLayout) {
        prefs.edit().putString(KEY_PROJECTION_LAYOUT, layout.name).apply()
    }

    fun setTarget(packageName: String, label: String) {
        prefs.edit()
            .putString(KEY_TARGET_PACKAGE, packageName)
            .putString(KEY_TARGET_LABEL, label)
            .apply()
    }

    fun setGeometry(widthPct: Int, heightPct: Int, offsetXPct: Int, offsetYPct: Int, scalePct: Int) {
        prefs.edit()
            .putInt(KEY_WIDTH_PCT, widthPct)
            .putInt(KEY_HEIGHT_PCT, heightPct)
            .putInt(KEY_OFFSET_X_PCT, offsetXPct)
            .putInt(KEY_OFFSET_Y_PCT, offsetYPct)
            .putInt(KEY_SCALE_PCT, scalePct)
            .apply()
    }

    fun saveLastVirtualDisplayId(id: Int) {
        prefs.edit().putInt(KEY_LAST_VD_ID, id).apply()
    }

    fun clearLastVirtualDisplayId() {
        prefs.edit().remove(KEY_LAST_VD_ID).apply()
    }

    companion object {
        const val PREFS_NAME = "cluster_projection"
        const val KEY_MIRROR_ENABLED = "mirror_enabled"
        const val KEY_TRIGGER_KEYCODE = "trigger_keycode"
        const val KEY_WIDTH_PCT = "width_pct"
        const val KEY_HEIGHT_PCT = "height_pct"
        const val KEY_OFFSET_X_PCT = "offset_x_pct"
        const val KEY_OFFSET_Y_PCT = "offset_y_pct"
        const val KEY_SCALE_PCT = "scale_pct"
        const val KEY_PROJECTION_LAYOUT = "projection_layout"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_TARGET_LABEL = "target_label"
        const val KEY_LAST_VD_ID = "last_vd_id"
        const val NO_VIRTUAL_DISPLAY = -1
    }
}

internal data class ClusterProjectionPreferenceSnapshot(
    val enabled: Boolean,
    val triggerKeyCode: Int,
    val widthPct: Int,
    val heightPct: Int,
    val offsetXPct: Int,
    val offsetYPct: Int,
    val scalePct: Int,
    val layout: ProjectionLayout,
    val targetPackage: String,
    val targetLabel: String?,
    val lastVirtualDisplayId: Int,
)
