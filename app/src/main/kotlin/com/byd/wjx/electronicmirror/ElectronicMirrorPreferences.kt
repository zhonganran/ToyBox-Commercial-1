package com.byd.wjx.electronicmirror

import android.content.Context
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.VehicleIdentityResolver
import com.byd.wjx.vehicle.VehicleMirrorProfile
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
        val streamingTarget = prefs.getString(KEY_STREAMING_REAR_DISPLAY_TARGET, null)
            ?.let { raw -> ElectronicMirrorDisplayTarget.entries.firstOrNull { it.name == raw } }
            ?: ElectronicMirrorDisplayTarget.CENTER
        val localLeft = readSource(KEY_LEFT_SOURCE, ElectronicMirrorCameraSource.SOURCE_A)
        val localRight = readSource(KEY_RIGHT_SOURCE, ElectronicMirrorCameraSource.SOURCE_B)
        val localRear = readSource(KEY_REAR_SOURCE, ElectronicMirrorCameraSource.SOURCE_C)
        val localCameraId = prefs.getInt(
            KEY_CAMERA_ID_OVERRIDE,
            ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID,
        )
        val localSurfaceIndex = prefs.getInt(
            KEY_PANORAMA_SURFACE_INDEX,
            ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX,
        )
        val inferredManualOverride = localCameraId != ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID ||
            localSurfaceIndex != ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX ||
            localLeft != ElectronicMirrorCameraSource.SOURCE_A ||
            localRight != ElectronicMirrorCameraSource.SOURCE_B ||
            localRear != ElectronicMirrorCameraSource.SOURCE_C
        val manualOverride = if (prefs.contains(KEY_MANUAL_PROFILE_OVERRIDE)) {
            prefs.getBoolean(KEY_MANUAL_PROFILE_OVERRIDE, false)
        } else {
            inferredManualOverride
        }
        val remoteProfile = readRemoteProfile()?.takeIf { profile ->
            profile.compatibilityKey == VehicleIdentityResolver.currentCompatibilityKey()
        }
        val useRemoteProfile = !manualOverride && remoteProfile != null
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
            leftSource = if (useRemoteProfile) remoteProfile.leftSource else localLeft,
            rightSource = if (useRemoteProfile) remoteProfile.rightSource else localRight,
            rearSource = if (useRemoteProfile) remoteProfile.rearSource else localRear,
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
            cameraIdOverride = if (useRemoteProfile) remoteProfile.cameraId else localCameraId,
            panoramaSurfaceIndex = if (useRemoteProfile) {
                remoteProfile.surfaceIndex
            } else {
                localSurfaceIndex
            },
            manualProfileOverride = manualOverride,
            remoteProfileId = remoteProfile?.profileId.orEmpty(),
            remoteProfileVersion = remoteProfile?.version ?: 0,
            profileSyncStatus = prefs.getString(KEY_PROFILE_SYNC_STATUS, "not_synced").orEmpty(),
            profileSyncMessage = prefs.getString(KEY_PROFILE_SYNC_MESSAGE, "").orEmpty(),
        ).sanitized()
    }

    fun isAnyEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) || prefs.getBoolean(KEY_STREAMING_REAR_ENABLED, false)

    fun save(config: ElectronicMirrorConfig) {
        val safe = config.sanitized()
        val installedRemoteId = prefs.getString(KEY_REMOTE_PROFILE_ID, "").orEmpty()
        val localProfileDiffersFromDefault = safe.cameraIdOverride !=
            ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID ||
            safe.panoramaSurfaceIndex != ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX ||
            safe.leftSource != ElectronicMirrorCameraSource.SOURCE_A ||
            safe.rightSource != ElectronicMirrorCameraSource.SOURCE_B ||
            safe.rearSource != ElectronicMirrorCameraSource.SOURCE_C
        val manualOverride = safe.manualProfileOverride ||
            (installedRemoteId.isBlank() && localProfileDiffersFromDefault)
        val profileFieldsChanged = safe.cameraIdOverride != prefs.getInt(
            KEY_CAMERA_ID_OVERRIDE,
            ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID,
        ) || safe.panoramaSurfaceIndex != prefs.getInt(
            KEY_PANORAMA_SURFACE_INDEX,
            ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX,
        ) || safe.leftSource != readSource(KEY_LEFT_SOURCE, ElectronicMirrorCameraSource.SOURCE_A) ||
            safe.rightSource != readSource(KEY_RIGHT_SOURCE, ElectronicMirrorCameraSource.SOURCE_B) ||
            safe.rearSource != readSource(KEY_REAR_SOURCE, ElectronicMirrorCameraSource.SOURCE_C)
        val editor = prefs.edit()
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
            .putBoolean(KEY_STREAMING_REAR_ENABLED, safe.streamingRearEnabled)
            .putBoolean(KEY_STREAMING_REAR_VISIBLE, safe.streamingRearVisible)
            .putString(KEY_STREAMING_REAR_DISPLAY_TARGET, safe.streamingRearDisplayTarget.name)
            .putGeometry(PREFIX_STREAMING_REAR_CLUSTER, safe.streamingRearClusterGeometry)
            .putGeometry(PREFIX_STREAMING_REAR_CENTER, safe.streamingRearCenterGeometry)
            .putBoolean(KEY_MANUAL_PROFILE_OVERRIDE, manualOverride)
        if (manualOverride || installedRemoteId.isBlank()) {
            editor
                .putString(KEY_LEFT_SOURCE, safe.leftSource.name)
                .putString(KEY_RIGHT_SOURCE, safe.rightSource.name)
                .putString(KEY_REAR_SOURCE, safe.rearSource.name)
                .putInt(KEY_CAMERA_ID_OVERRIDE, safe.cameraIdOverride)
                .putInt(KEY_PANORAMA_SURFACE_INDEX, safe.panoramaSurfaceIndex)
        }
        editor.apply()
        if (manualOverride && profileFieldsChanged) {
            WjxFileLogger.i(
                TAG,
                "manual profile confirmed compatibility=${VehicleIdentityResolver.currentCompatibilityKey()} " +
                    "camera=${safe.cameraIdOverride} surface=${safe.panoramaSurfaceIndex} " +
                    "left=${safe.leftSource.name} right=${safe.rightSource.name} rear=${safe.rearSource.name}",
            )
        }
    }

    fun restoreAutomaticProfile() {
        prefs.edit().putBoolean(KEY_MANUAL_PROFILE_OVERRIDE, false).apply()
        WjxFileLogger.i(TAG, "manual profile override cleared; automatic profile restored")
    }

    fun installRemoteProfile(profile: VehicleMirrorProfile): Boolean {
        val unchanged = profile.profileId == prefs.getString(KEY_REMOTE_PROFILE_ID, "") &&
            profile.version == prefs.getInt(KEY_REMOTE_PROFILE_VERSION, 0) &&
            profile.compatibilityKey == prefs.getString(KEY_REMOTE_COMPATIBILITY_KEY, "") &&
            profile.cameraId == prefs.getInt(KEY_REMOTE_CAMERA_ID, -1) &&
            profile.surfaceIndex == prefs.getInt(KEY_REMOTE_SURFACE_INDEX, -1) &&
            profile.leftSource == readSource(KEY_REMOTE_LEFT_SOURCE, ElectronicMirrorCameraSource.SOURCE_A) &&
            profile.rightSource == readSource(KEY_REMOTE_RIGHT_SOURCE, ElectronicMirrorCameraSource.SOURCE_B) &&
            profile.rearSource == readSource(KEY_REMOTE_REAR_SOURCE, ElectronicMirrorCameraSource.SOURCE_C)
        prefs.edit()
            .putString(KEY_REMOTE_PROFILE_ID, profile.profileId)
            .putInt(KEY_REMOTE_PROFILE_VERSION, profile.version)
            .putString(KEY_REMOTE_COMPATIBILITY_KEY, profile.compatibilityKey)
            .putInt(KEY_REMOTE_CAMERA_ID, profile.cameraId)
            .putInt(KEY_REMOTE_SURFACE_INDEX, profile.surfaceIndex)
            .putString(KEY_REMOTE_LEFT_SOURCE, profile.leftSource.name)
            .putString(KEY_REMOTE_RIGHT_SOURCE, profile.rightSource.name)
            .putString(KEY_REMOTE_REAR_SOURCE, profile.rearSource.name)
            .putString(KEY_REMOTE_UPDATED_AT, profile.updatedAt)
            .apply()
        return !unchanged
    }

    fun clearRemoteProfile(): Boolean {
        val hadProfile = prefs.getString(KEY_REMOTE_PROFILE_ID, "").orEmpty().isNotBlank()
        prefs.edit()
            .remove(KEY_REMOTE_PROFILE_ID)
            .remove(KEY_REMOTE_PROFILE_VERSION)
            .remove(KEY_REMOTE_COMPATIBILITY_KEY)
            .remove(KEY_REMOTE_CAMERA_ID)
            .remove(KEY_REMOTE_SURFACE_INDEX)
            .remove(KEY_REMOTE_LEFT_SOURCE)
            .remove(KEY_REMOTE_RIGHT_SOURCE)
            .remove(KEY_REMOTE_REAR_SOURCE)
            .remove(KEY_REMOTE_UPDATED_AT)
            .apply()
        if (hadProfile) {
            WjxFileLogger.i(TAG, "cached remote vehicle profile cleared")
        }
        return hadProfile
    }

    fun recordProfileSync(status: String, message: String) {
        prefs.edit()
            .putString(KEY_PROFILE_SYNC_STATUS, status.take(48))
            .putString(KEY_PROFILE_SYNC_MESSAGE, message.take(240))
            .putLong(KEY_PROFILE_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun readRemoteProfile(): VehicleMirrorProfile? {
        val profileId = prefs.getString(KEY_REMOTE_PROFILE_ID, "").orEmpty()
        val compatibilityKey = prefs.getString(KEY_REMOTE_COMPATIBILITY_KEY, "").orEmpty()
        val version = prefs.getInt(KEY_REMOTE_PROFILE_VERSION, 0)
        val cameraId = prefs.getInt(KEY_REMOTE_CAMERA_ID, -1)
        val surfaceIndex = prefs.getInt(KEY_REMOTE_SURFACE_INDEX, -1)
        if (profileId.isBlank() || compatibilityKey.isBlank() || version <= 0 ||
            cameraId !in 0..3 || surfaceIndex !in 0..15
        ) {
            return null
        }
        val left = readSource(KEY_REMOTE_LEFT_SOURCE, ElectronicMirrorCameraSource.SOURCE_A)
        val right = readSource(KEY_REMOTE_RIGHT_SOURCE, ElectronicMirrorCameraSource.SOURCE_B)
        val rear = readSource(KEY_REMOTE_REAR_SOURCE, ElectronicMirrorCameraSource.SOURCE_C)
        if (setOf(left, right, rear).size != 3) return null
        return VehicleMirrorProfile(
            profileId = profileId,
            version = version,
            compatibilityKey = compatibilityKey,
            cameraId = cameraId,
            surfaceIndex = surfaceIndex,
            leftSource = left,
            rightSource = right,
            rearSource = rear,
            updatedAt = prefs.getString(KEY_REMOTE_UPDATED_AT, "").orEmpty(),
        )
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
    ): ElectronicMirrorProjectionSpec {
        return ElectronicMirrorProjectionSpec(
            horizontalPermille = prefs.getInt("${prefix}_horizontal", default.horizontalPermille),
            heightOffsetPermille = prefs.getInt(
                "${prefix}_height_offset",
                default.heightOffsetPermille,
            ),
            viewScalePermille = prefs.getInt("${prefix}_view_scale", default.viewScalePermille),
        ).sanitized()
    }

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
        private const val KEY_MANUAL_PROFILE_OVERRIDE = "manual_profile_override"
        private const val KEY_REMOTE_PROFILE_ID = "remote_profile_id"
        private const val KEY_REMOTE_PROFILE_VERSION = "remote_profile_version"
        private const val KEY_REMOTE_COMPATIBILITY_KEY = "remote_compatibility_key"
        private const val KEY_REMOTE_CAMERA_ID = "remote_camera_id"
        private const val KEY_REMOTE_SURFACE_INDEX = "remote_surface_index"
        private const val KEY_REMOTE_LEFT_SOURCE = "remote_left_source"
        private const val KEY_REMOTE_RIGHT_SOURCE = "remote_right_source"
        private const val KEY_REMOTE_REAR_SOURCE = "remote_rear_source"
        private const val KEY_REMOTE_UPDATED_AT = "remote_updated_at"
        private const val KEY_PROFILE_SYNC_STATUS = "profile_sync_status"
        private const val KEY_PROFILE_SYNC_MESSAGE = "profile_sync_message"
        private const val KEY_PROFILE_SYNCED_AT = "profile_synced_at"
        private const val PREFIX_CLUSTER = "cluster"
        private const val PREFIX_CENTER = "center"
        private const val PREFIX_STREAMING_REAR_CLUSTER = "streaming_rear_cluster"
        private const val PREFIX_STREAMING_REAR_CENTER = "streaming_rear_center"
        private const val PREFIX_PROJECTION_LEFT = "projection_left"
        private const val PREFIX_PROJECTION_RIGHT = "projection_right"
        private const val PREFIX_PROJECTION_REAR = "projection_rear"
        private const val TAG = "ElectronicMirrorPrefs"
    }
}
