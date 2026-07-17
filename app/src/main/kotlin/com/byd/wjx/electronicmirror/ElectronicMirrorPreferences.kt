package com.byd.wjx.electronicmirror

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ElectronicMirrorPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(): ElectronicMirrorConfig {
        val target = prefs.getString(KEY_DISPLAY_TARGET, null)
            ?.let { raw -> ElectronicMirrorDisplayTarget.entries.firstOrNull { it.name == raw } }
            ?: ElectronicMirrorDisplayTarget.CLUSTER
        return ElectronicMirrorConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            displayTarget = target,
            leftTurnEnabled = prefs.getBoolean(KEY_LEFT_TURN_ENABLED, true),
            rightTurnEnabled = prefs.getBoolean(KEY_RIGHT_TURN_ENABLED, true),
            reverseEnabled = prefs.getBoolean(KEY_REVERSE_ENABLED, false),
            fps = prefs.getInt(KEY_FPS, ElectronicMirrorConfig.DEFAULT_FPS),
            clusterGeometry = readGeometry(PREFIX_CLUSTER, ElectronicMirrorGeometry.DEFAULT_CLUSTER),
            centerGeometry = readGeometry(PREFIX_CENTER, ElectronicMirrorGeometry.DEFAULT_CENTER),
        ).sanitized()
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun save(config: ElectronicMirrorConfig) {
        val safe = config.sanitized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, safe.enabled)
            .putString(KEY_DISPLAY_TARGET, safe.displayTarget.name)
            .putBoolean(KEY_LEFT_TURN_ENABLED, safe.leftTurnEnabled)
            .putBoolean(KEY_RIGHT_TURN_ENABLED, safe.rightTurnEnabled)
            .putBoolean(KEY_REVERSE_ENABLED, safe.reverseEnabled)
            .putInt(KEY_FPS, safe.fps)
            .putGeometry(PREFIX_CLUSTER, safe.clusterGeometry)
            .putGeometry(PREFIX_CENTER, safe.centerGeometry)
            .apply()
    }

    private fun readGeometry(prefix: String, default: ElectronicMirrorGeometry): ElectronicMirrorGeometry =
        ElectronicMirrorGeometry(
            widthPct = prefs.getInt("${prefix}_width_pct", default.widthPct),
            heightPct = prefs.getInt("${prefix}_height_pct", default.heightPct),
            offsetXPct = prefs.getInt("${prefix}_offset_x_pct", default.offsetXPct),
            offsetYPct = prefs.getInt("${prefix}_offset_y_pct", default.offsetYPct),
        ).sanitized()

    private fun android.content.SharedPreferences.Editor.putGeometry(
        prefix: String,
        geometry: ElectronicMirrorGeometry,
    ): android.content.SharedPreferences.Editor = apply {
        putInt("${prefix}_width_pct", geometry.widthPct)
        putInt("${prefix}_height_pct", geometry.heightPct)
        putInt("${prefix}_offset_x_pct", geometry.offsetXPct)
        putInt("${prefix}_offset_y_pct", geometry.offsetYPct)
    }

    companion object {
        const val PREFS_NAME = "electronic_mirror"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DISPLAY_TARGET = "display_target"
        private const val KEY_LEFT_TURN_ENABLED = "left_turn_enabled"
        private const val KEY_RIGHT_TURN_ENABLED = "right_turn_enabled"
        private const val KEY_REVERSE_ENABLED = "reverse_enabled"
        private const val KEY_FPS = "fps"
        private const val PREFIX_CLUSTER = "cluster"
        private const val PREFIX_CENTER = "center"
    }
}
