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
        fun readSource(key: String, default: ElectronicMirrorCameraSource): ElectronicMirrorCameraSource =
            prefs.getString(key, null)
                ?.let { raw -> ElectronicMirrorCameraSource.entries.firstOrNull { it.name == raw } }
                ?: default
        return ElectronicMirrorConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            displayTarget = target,
            leftTurnEnabled = prefs.getBoolean(KEY_LEFT_TURN_ENABLED, true),
            rightTurnEnabled = prefs.getBoolean(KEY_RIGHT_TURN_ENABLED, true),
            reverseEnabled = prefs.getBoolean(KEY_REVERSE_ENABLED, false),
            fps = prefs.getInt(KEY_FPS, ElectronicMirrorConfig.DEFAULT_FPS),
            clusterGeometry = readGeometry(PREFIX_CLUSTER, ElectronicMirrorGeometry.DEFAULT_CLUSTER),
            centerGeometry = readGeometry(PREFIX_CENTER, ElectronicMirrorGeometry.DEFAULT_CENTER),
            leftCalibration = readCalibration(PREFIX_CALIBRATION_LEFT),
            rightCalibration = readCalibration(PREFIX_CALIBRATION_RIGHT),
            rearCalibration = readCalibration(PREFIX_CALIBRATION_REAR),
            leftSource = readSource(KEY_LEFT_SOURCE, ElectronicMirrorCameraSource.SOURCE_A),
            rightSource = readSource(KEY_RIGHT_SOURCE, ElectronicMirrorCameraSource.SOURCE_B),
            rearSource = readSource(KEY_REAR_SOURCE, ElectronicMirrorCameraSource.SOURCE_C),
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
            .putCalibration(PREFIX_CALIBRATION_LEFT, safe.leftCalibration)
            .putCalibration(PREFIX_CALIBRATION_RIGHT, safe.rightCalibration)
            .putCalibration(PREFIX_CALIBRATION_REAR, safe.rearCalibration)
            .putString(KEY_LEFT_SOURCE, safe.leftSource.name)
            .putString(KEY_RIGHT_SOURCE, safe.rightSource.name)
            .putString(KEY_REAR_SOURCE, safe.rearSource.name)
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

    private fun readCalibration(prefix: String): ElectronicMirrorCalibration {
        val default = ElectronicMirrorCalibration.DEFAULT
        return ElectronicMirrorCalibration(
            topLeft = readCalibrationPoint(prefix, "top_left", default.topLeft),
            topRight = readCalibrationPoint(prefix, "top_right", default.topRight),
            bottomRight = readCalibrationPoint(prefix, "bottom_right", default.bottomRight),
            bottomLeft = readCalibrationPoint(prefix, "bottom_left", default.bottomLeft),
            yawDegrees = prefs.getInt("${prefix}_yaw_degrees", default.yawDegrees),
            rollDegrees = prefs.getInt("${prefix}_roll_degrees", default.rollDegrees),
            pitchDegrees = prefs.getInt("${prefix}_pitch_degrees", default.pitchDegrees),
        ).sanitized()
    }

    private fun readCalibrationPoint(
        prefix: String,
        name: String,
        default: ElectronicMirrorCalibrationPoint,
    ): ElectronicMirrorCalibrationPoint = ElectronicMirrorCalibrationPoint(
        xPermille = prefs.getInt("${prefix}_${name}_x", default.xPermille),
        yPermille = prefs.getInt("${prefix}_${name}_y", default.yPermille),
    )

    private fun android.content.SharedPreferences.Editor.putCalibration(
        prefix: String,
        calibration: ElectronicMirrorCalibration,
    ): android.content.SharedPreferences.Editor = apply {
        putCalibrationPoint(prefix, "top_left", calibration.topLeft)
        putCalibrationPoint(prefix, "top_right", calibration.topRight)
        putCalibrationPoint(prefix, "bottom_right", calibration.bottomRight)
        putCalibrationPoint(prefix, "bottom_left", calibration.bottomLeft)
        putInt("${prefix}_yaw_degrees", calibration.yawDegrees)
        putInt("${prefix}_roll_degrees", calibration.rollDegrees)
        putInt("${prefix}_pitch_degrees", calibration.pitchDegrees)
    }

    private fun android.content.SharedPreferences.Editor.putCalibrationPoint(
        prefix: String,
        name: String,
        point: ElectronicMirrorCalibrationPoint,
    ) {
        putInt("${prefix}_${name}_x", point.xPermille)
        putInt("${prefix}_${name}_y", point.yPermille)
    }

    companion object {
        const val PREFS_NAME = "electronic_mirror"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DISPLAY_TARGET = "display_target"
        private const val KEY_LEFT_TURN_ENABLED = "left_turn_enabled"
        private const val KEY_RIGHT_TURN_ENABLED = "right_turn_enabled"
        private const val KEY_REVERSE_ENABLED = "reverse_enabled"
        private const val KEY_FPS = "fps"
        private const val KEY_LEFT_SOURCE = "left_source"
        private const val KEY_RIGHT_SOURCE = "right_source"
        private const val KEY_REAR_SOURCE = "rear_source"
        private const val PREFIX_CLUSTER = "cluster"
        private const val PREFIX_CENTER = "center"
        private const val PREFIX_CALIBRATION_LEFT = "calibration_left"
        private const val PREFIX_CALIBRATION_RIGHT = "calibration_right"
        private const val PREFIX_CALIBRATION_REAR = "calibration_rear"
    }
}
