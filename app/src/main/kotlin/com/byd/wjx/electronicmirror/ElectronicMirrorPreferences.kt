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

    init {
        removeLegacyCloudConfiguration()
    }

    fun snapshot(): ElectronicMirrorConfig {
        val target = prefs.getString(KEY_DISPLAY_TARGET, null)
            ?.let { raw -> ElectronicMirrorDisplayTarget.entries.firstOrNull { it.name == raw } }
            ?: ElectronicMirrorDisplayTarget.CLUSTER
        val streamingTarget = prefs.getString(KEY_STREAMING_REAR_DISPLAY_TARGET, null)
            ?.let { raw -> ElectronicMirrorDisplayTarget.entries.firstOrNull { it.name == raw } }
            ?: ElectronicMirrorDisplayTarget.CENTER
        return ElectronicMirrorConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            displayTarget = target,
            leftTurnEnabled = prefs.getBoolean(KEY_LEFT_TURN_ENABLED, true),
            rightTurnEnabled = prefs.getBoolean(KEY_RIGHT_TURN_ENABLED, true),
            reverseEnabled = prefs.getBoolean(KEY_REVERSE_ENABLED, false),
            fps = ElectronicMirrorConfig.MAX_FPS,
            qualityWidth = ElectronicMirrorSurfaceProfile.MAX_QUALITY_WIDTH,
            clusterGeometry = readGeometry(PREFIX_CLUSTER, ElectronicMirrorGeometry.DEFAULT_CLUSTER),
            centerGeometry = readGeometry(PREFIX_CENTER, ElectronicMirrorGeometry.DEFAULT_CENTER),
            leftProjection = readProjection(
                PREFIX_PROJECTION_LEFT,
                ElectronicMirrorProjectionSpec.DEFAULT_LEFT,
            ),
            rightProjection = readProjection(
                PREFIX_PROJECTION_RIGHT,
                ElectronicMirrorProjectionSpec.DEFAULT_RIGHT,
            ),
            rearProjection = readProjection(
                PREFIX_PROJECTION_REAR,
                ElectronicMirrorProjectionSpec.DEFAULT_REAR,
            ),
            leftSource = readSource(KEY_LEFT_SOURCE, ElectronicMirrorCameraSource.SOURCE_A),
            rightSource = readSource(KEY_RIGHT_SOURCE, ElectronicMirrorCameraSource.SOURCE_B),
            rearSource = readSource(KEY_REAR_SOURCE, ElectronicMirrorCameraSource.SOURCE_C),
            streamingRearEnabled = prefs.getBoolean(KEY_STREAMING_REAR_ENABLED, false),
            streamingRearVisible = prefs.getBoolean(KEY_STREAMING_REAR_VISIBLE, true),
            streamingRearDisplayTarget = streamingTarget,
            streamingRearClusterGeometry = readGeometry(
                PREFIX_STREAMING_REAR_CLUSTER,
                ElectronicMirrorGeometry.DEFAULT_STREAMING_CLUSTER,
            ),
            streamingRearCenterGeometry = readGeometry(
                PREFIX_STREAMING_REAR_CENTER,
                ElectronicMirrorGeometry.DEFAULT_STREAMING_CENTER,
            ),
            cameraIdOverride = prefs.getInt(
                KEY_CAMERA_ID_OVERRIDE,
                ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID,
            ),
            panoramaSurfaceIndex = prefs.getInt(
                KEY_PANORAMA_SURFACE_INDEX,
                ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX,
            ),
        ).sanitized()
    }

    fun isAnyEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) || prefs.getBoolean(KEY_STREAMING_REAR_ENABLED, false)

    fun save(config: ElectronicMirrorConfig) {
        val safe = config.sanitized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, safe.enabled)
            .putString(KEY_DISPLAY_TARGET, safe.displayTarget.name)
            .putBoolean(KEY_LEFT_TURN_ENABLED, safe.leftTurnEnabled)
            .putBoolean(KEY_RIGHT_TURN_ENABLED, safe.rightTurnEnabled)
            .putBoolean(KEY_REVERSE_ENABLED, safe.reverseEnabled)
            .remove(KEY_FPS)
            .remove(KEY_QUALITY_WIDTH)
            .putGeometry(PREFIX_CLUSTER, safe.clusterGeometry)
            .putGeometry(PREFIX_CENTER, safe.centerGeometry)
            .putProjection(PREFIX_PROJECTION_LEFT, safe.leftProjection)
            .putProjection(PREFIX_PROJECTION_RIGHT, safe.rightProjection)
            .putProjection(PREFIX_PROJECTION_REAR, safe.rearProjection)
            .putString(KEY_LEFT_SOURCE, safe.leftSource.name)
            .putString(KEY_RIGHT_SOURCE, safe.rightSource.name)
            .putString(KEY_REAR_SOURCE, safe.rearSource.name)
            .putBoolean(KEY_STREAMING_REAR_ENABLED, safe.streamingRearEnabled)
            .putBoolean(KEY_STREAMING_REAR_VISIBLE, safe.streamingRearVisible)
            .putString(KEY_STREAMING_REAR_DISPLAY_TARGET, safe.streamingRearDisplayTarget.name)
            .putGeometry(PREFIX_STREAMING_REAR_CLUSTER, safe.streamingRearClusterGeometry)
            .putGeometry(PREFIX_STREAMING_REAR_CENTER, safe.streamingRearCenterGeometry)
            .putInt(KEY_CAMERA_ID_OVERRIDE, safe.cameraIdOverride)
            .putInt(KEY_PANORAMA_SURFACE_INDEX, safe.panoramaSurfaceIndex)
            .apply()
    }

    private fun removeLegacyCloudConfiguration() {
        val editor = prefs.edit()
        LEGACY_CLOUD_KEYS.forEach(editor::remove)
        editor.apply()
    }

    private fun readSource(
        key: String,
        default: ElectronicMirrorCameraSource,
    ): ElectronicMirrorCameraSource = prefs.getString(key, null)
        ?.let { raw -> ElectronicMirrorCameraSource.entries.firstOrNull { it.name == raw } }
        ?: default

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

    private fun readProjection(
        prefix: String,
        default: ElectronicMirrorProjectionSpec,
    ): ElectronicMirrorProjectionSpec = ElectronicMirrorProjectionSpec(
        horizontalPermille = prefs.getInt("${prefix}_horizontal", default.horizontalPermille),
        heightOffsetPermille = prefs.getInt(
            "${prefix}_height_offset",
            default.heightOffsetPermille,
        ),
        viewScalePermille = prefs.getInt("${prefix}_view_scale", default.viewScalePermille),
    ).sanitized()

    private fun android.content.SharedPreferences.Editor.putProjection(
        prefix: String,
        projection: ElectronicMirrorProjectionSpec,
    ): android.content.SharedPreferences.Editor = apply {
        putInt("${prefix}_horizontal", projection.horizontalPermille)
        putInt("${prefix}_height_offset", projection.heightOffsetPermille)
        putInt("${prefix}_view_scale", projection.viewScalePermille)
    }

    companion object {
        const val PREFS_NAME = "electronic_mirror"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DISPLAY_TARGET = "display_target"
        private const val KEY_LEFT_TURN_ENABLED = "left_turn_enabled"
        private const val KEY_RIGHT_TURN_ENABLED = "right_turn_enabled"
        private const val KEY_REVERSE_ENABLED = "reverse_enabled"
        private const val KEY_FPS = "fps"
        private const val KEY_QUALITY_WIDTH = "quality_width"
        private const val KEY_LEFT_SOURCE = "left_source"
        private const val KEY_RIGHT_SOURCE = "right_source"
        private const val KEY_REAR_SOURCE = "rear_source"
        private const val KEY_STREAMING_REAR_ENABLED = "streaming_rear_enabled"
        private const val KEY_STREAMING_REAR_VISIBLE = "streaming_rear_visible"
        private const val KEY_STREAMING_REAR_DISPLAY_TARGET = "streaming_rear_display_target"
        private const val KEY_CAMERA_ID_OVERRIDE = "camera_id_override"
        private const val KEY_PANORAMA_SURFACE_INDEX = "panorama_surface_index"
        private const val PREFIX_CLUSTER = "cluster"
        private const val PREFIX_CENTER = "center"
        private const val PREFIX_STREAMING_REAR_CLUSTER = "streaming_rear_cluster"
        private const val PREFIX_STREAMING_REAR_CENTER = "streaming_rear_center"
        private const val PREFIX_PROJECTION_LEFT = "projection_left"
        private const val PREFIX_PROJECTION_RIGHT = "projection_right"
        private const val PREFIX_PROJECTION_REAR = "projection_rear"

        // One-time cleanup for versions that previously cached camera configuration from the cloud.
        private val LEGACY_CLOUD_KEYS = listOf(
            "manual_profile_override",
            "remote_profile_id",
            "remote_profile_version",
            "remote_compatibility_key",
            "remote_camera_id",
            "remote_surface_index",
            "remote_left_source",
            "remote_right_source",
            "remote_rear_source",
            "remote_updated_at",
            "profile_sync_status",
            "profile_sync_message",
            "profile_synced_at",
        )
    }
}
