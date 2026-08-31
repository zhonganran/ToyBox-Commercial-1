package com.byd.wjx.camera.blindspot

import android.content.Context
import android.graphics.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class BlindSpotDisplayTarget { CENTER, CLUSTER }

enum class BlindSpotScaleMode { FIT, FILL, STRETCH }

enum class BlindSpotDewarpProjection { RECTILINEAR, CYLINDRICAL }

data class BlindSpotCalibration(
    val cropLeftPct: Int,
    val cropTopPct: Int,
    val cropWidthPct: Int,
    val cropHeightPct: Int,
    val correctedCropLeftPct: Int,
    val correctedCropTopPct: Int,
    val correctedCropWidthPct: Int,
    val correctedCropHeightPct: Int,
    val rotationDegrees: Int,
    val mirrorHorizontally: Boolean,
    val scaleMode: BlindSpotScaleMode,
    val dewarpEnabled: Boolean,
    val dewarpFovDegrees: Int,
    val dewarpProjection: BlindSpotDewarpProjection,
    val dewarpCenterXPct: Int,
    val dewarpCenterYPct: Int,
) {
    fun crop(corrected: Boolean): BlindSpotCrop = if (corrected) {
        BlindSpotCrop(
            correctedCropLeftPct,
            correctedCropTopPct,
            correctedCropWidthPct,
            correctedCropHeightPct,
        )
    } else {
        BlindSpotCrop(cropLeftPct, cropTopPct, cropWidthPct, cropHeightPct)
    }

    fun withCrop(corrected: Boolean, crop: BlindSpotCrop): BlindSpotCalibration = if (corrected) {
        copy(
            correctedCropLeftPct = crop.leftPct,
            correctedCropTopPct = crop.topPct,
            correctedCropWidthPct = crop.widthPct,
            correctedCropHeightPct = crop.heightPct,
        )
    } else {
        copy(
            cropLeftPct = crop.leftPct,
            cropTopPct = crop.topPct,
            cropWidthPct = crop.widthPct,
            cropHeightPct = crop.heightPct,
        )
    }
}

data class BlindSpotCrop(
    val leftPct: Int,
    val topPct: Int,
    val widthPct: Int,
    val heightPct: Int,
)

data class BlindSpotSideConfig(
    val side: BlindSpotSide,
    val previewIndex: Int,
    val target: BlindSpotDisplayTarget,
    val widthPct: Int,
    val heightPct: Int,
    val xPct: Int,
    val yPct: Int,
    val calibration: BlindSpotCalibration,
)

@Singleton
class BlindSpotPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(): BlindSpotPreferenceSnapshot {
        val leftIndex = prefs.getInt(key(BlindSpotSide.LEFT, FIELD_PREVIEW_INDEX), DEFAULT_LEFT_PREVIEW_INDEX)
            .coerceIn(MIN_PREVIEW_INDEX, MAX_PREVIEW_INDEX)
        val storedRight = prefs.getInt(key(BlindSpotSide.RIGHT, FIELD_PREVIEW_INDEX), DEFAULT_RIGHT_PREVIEW_INDEX)
            .coerceIn(MIN_PREVIEW_INDEX, MAX_PREVIEW_INDEX)
        val rightIndex = storedRight.takeUnless { it == leftIndex }
            ?: if (leftIndex == DEFAULT_RIGHT_PREVIEW_INDEX) DEFAULT_LEFT_PREVIEW_INDEX else DEFAULT_RIGHT_PREVIEW_INDEX
        return BlindSpotPreferenceSnapshot(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            minSpeedKmh = prefs.getInt(KEY_MIN_SPEED_KMH, DEFAULT_MIN_SPEED_KMH)
                .coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH),
            maxSpeedKmh = prefs.getInt(KEY_MAX_SPEED_KMH, DEFAULT_MAX_SPEED_KMH)
                .coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH),
            bsdGlow = prefs.getBoolean(KEY_BSD_GLOW, true),
            bsdOnly = prefs.getBoolean(KEY_BSD_ONLY, false),
            sharpTurnEnabled = prefs.getBoolean(KEY_SHARP_TURN_ENABLED, false),
            sharpTurnAngleDeg = prefs.getInt(KEY_SHARP_TURN_ANGLE_DEG, DEFAULT_SHARP_TURN_ANGLE_DEG)
                .coerceIn(MIN_SHARP_TURN_ANGLE_DEG, MAX_SHARP_TURN_ANGLE_DEG),
            left = sideSnapshot(BlindSpotSide.LEFT, leftIndex),
            right = sideSnapshot(BlindSpotSide.RIGHT, rightIndex),
        ).normalized()
    }

    fun setEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun setSpeedRange(minimum: Int, maximum: Int) {
        val safeMin = minimum.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)
        val safeMax = maximum.coerceIn(safeMin, MAX_SPEED_KMH)
        prefs.edit()
            .putInt(KEY_MIN_SPEED_KMH, safeMin)
            .putInt(KEY_MAX_SPEED_KMH, safeMax)
            .apply()
    }

    fun setBsdGlow(value: Boolean) = prefs.edit().putBoolean(KEY_BSD_GLOW, value).apply()

    fun setBsdOnly(value: Boolean) = prefs.edit().putBoolean(KEY_BSD_ONLY, value).apply()

    fun setSharpTurnEnabled(value: Boolean) = prefs.edit()
        .putBoolean(KEY_SHARP_TURN_ENABLED, value)
        .apply()

    fun setSharpTurnAngleDeg(value: Int) = prefs.edit()
        .putInt(
            KEY_SHARP_TURN_ANGLE_DEG,
            value.coerceIn(MIN_SHARP_TURN_ANGLE_DEG, MAX_SHARP_TURN_ANGLE_DEG),
        )
        .apply()

    fun setPreviewIndexes(left: Int, right: Int) {
        val safeLeft = left.coerceIn(MIN_PREVIEW_INDEX, MAX_PREVIEW_INDEX)
        val safeRight = right.coerceIn(MIN_PREVIEW_INDEX, MAX_PREVIEW_INDEX)
        if (safeLeft == safeRight) return
        prefs.edit()
            .putInt(key(BlindSpotSide.LEFT, FIELD_PREVIEW_INDEX), safeLeft)
            .putInt(key(BlindSpotSide.RIGHT, FIELD_PREVIEW_INDEX), safeRight)
            .apply()
    }

    fun setTarget(side: BlindSpotSide, target: BlindSpotDisplayTarget) {
        if (side == BlindSpotSide.NONE) return
        prefs.edit().putString(key(side, FIELD_TARGET), target.name).apply()
    }

    fun setGeometry(side: BlindSpotSide, widthPct: Int, heightPct: Int, xPct: Int, yPct: Int) {
        if (side == BlindSpotSide.NONE) return
        prefs.edit()
            .putInt(key(side, FIELD_WIDTH_PCT), widthPct.coerceIn(MIN_WINDOW_PCT, MAX_WINDOW_PCT))
            .putInt(key(side, FIELD_HEIGHT_PCT), heightPct.coerceIn(MIN_WINDOW_PCT, MAX_WINDOW_PCT))
            .putInt(key(side, FIELD_X_PCT), xPct.coerceIn(0, POSITION_SCALE))
            .putInt(key(side, FIELD_Y_PCT), yPct.coerceIn(0, POSITION_SCALE))
            .apply()
    }

    fun setCalibration(side: BlindSpotSide, calibration: BlindSpotCalibration) {
        if (side == BlindSpotSide.NONE) return
        val safe = calibration.normalized()
        prefs.edit()
            .putInt(key(side, FIELD_CROP_LEFT_PCT), safe.cropLeftPct)
            .putInt(key(side, FIELD_CROP_TOP_PCT), safe.cropTopPct)
            .putInt(key(side, FIELD_CROP_WIDTH_PCT), safe.cropWidthPct)
            .putInt(key(side, FIELD_CROP_HEIGHT_PCT), safe.cropHeightPct)
            .putInt(key(side, FIELD_CORRECTED_CROP_LEFT_PCT), safe.correctedCropLeftPct)
            .putInt(key(side, FIELD_CORRECTED_CROP_TOP_PCT), safe.correctedCropTopPct)
            .putInt(key(side, FIELD_CORRECTED_CROP_WIDTH_PCT), safe.correctedCropWidthPct)
            .putInt(key(side, FIELD_CORRECTED_CROP_HEIGHT_PCT), safe.correctedCropHeightPct)
            .putInt(key(side, FIELD_ROTATION_DEGREES), safe.rotationDegrees)
            .putBoolean(key(side, FIELD_MIRROR), safe.mirrorHorizontally)
            .putString(key(side, FIELD_SCALE_MODE), safe.scaleMode.name)
            .putBoolean(key(side, FIELD_DEWARP_ENABLED), safe.dewarpEnabled)
            .putInt(key(side, FIELD_DEWARP_FOV), safe.dewarpFovDegrees)
            .putString(key(side, FIELD_DEWARP_PROJECTION), safe.dewarpProjection.name)
            .putInt(key(side, FIELD_DEWARP_CENTER_X), safe.dewarpCenterXPct)
            .putInt(key(side, FIELD_DEWARP_CENTER_Y), safe.dewarpCenterYPct)
            .apply()
    }

    fun resetCalibration(side: BlindSpotSide) = setCalibration(side, defaultCalibration(side))

    private fun sideSnapshot(side: BlindSpotSide, previewIndex: Int): BlindSpotSideConfig {
        val defaults = defaultSideConfig(side)
        return defaults.copy(
            previewIndex = previewIndex,
            target = enumValue(prefs.getString(key(side, FIELD_TARGET), null), defaults.target),
            widthPct = prefs.getInt(key(side, FIELD_WIDTH_PCT), defaults.widthPct)
                .coerceIn(MIN_WINDOW_PCT, MAX_WINDOW_PCT),
            heightPct = prefs.getInt(key(side, FIELD_HEIGHT_PCT), defaults.heightPct)
                .coerceIn(MIN_WINDOW_PCT, MAX_WINDOW_PCT),
            xPct = prefs.getInt(key(side, FIELD_X_PCT), defaults.xPct).coerceIn(0, POSITION_SCALE),
            yPct = prefs.getInt(key(side, FIELD_Y_PCT), defaults.yPct).coerceIn(0, POSITION_SCALE),
            calibration = BlindSpotCalibration(
                cropLeftPct = prefs.getInt(
                    key(side, FIELD_CROP_LEFT_PCT),
                    defaults.calibration.cropLeftPct,
                ),
                cropTopPct = prefs.getInt(
                    key(side, FIELD_CROP_TOP_PCT),
                    defaults.calibration.cropTopPct,
                ),
                cropWidthPct = prefs.getInt(
                    key(side, FIELD_CROP_WIDTH_PCT),
                    defaults.calibration.cropWidthPct,
                ),
                cropHeightPct = prefs.getInt(
                    key(side, FIELD_CROP_HEIGHT_PCT),
                    defaults.calibration.cropHeightPct,
                ),
                correctedCropLeftPct = prefs.getInt(
                    key(side, FIELD_CORRECTED_CROP_LEFT_PCT),
                    defaults.calibration.correctedCropLeftPct,
                ),
                correctedCropTopPct = prefs.getInt(
                    key(side, FIELD_CORRECTED_CROP_TOP_PCT),
                    defaults.calibration.correctedCropTopPct,
                ),
                correctedCropWidthPct = prefs.getInt(
                    key(side, FIELD_CORRECTED_CROP_WIDTH_PCT),
                    defaults.calibration.correctedCropWidthPct,
                ),
                correctedCropHeightPct = prefs.getInt(
                    key(side, FIELD_CORRECTED_CROP_HEIGHT_PCT),
                    defaults.calibration.correctedCropHeightPct,
                ),
                rotationDegrees = prefs.getInt(
                    key(side, FIELD_ROTATION_DEGREES),
                    defaults.calibration.rotationDegrees,
                ),
                mirrorHorizontally = prefs.getBoolean(
                    key(side, FIELD_MIRROR),
                    defaults.calibration.mirrorHorizontally,
                ),
                scaleMode = enumValue(
                    prefs.getString(key(side, FIELD_SCALE_MODE), null),
                    defaults.calibration.scaleMode,
                ),
                dewarpEnabled = prefs.getBoolean(
                    key(side, FIELD_DEWARP_ENABLED),
                    defaults.calibration.dewarpEnabled,
                ),
                dewarpFovDegrees = prefs.getInt(
                    key(side, FIELD_DEWARP_FOV),
                    defaults.calibration.dewarpFovDegrees,
                ),
                dewarpProjection = enumValue(
                    prefs.getString(key(side, FIELD_DEWARP_PROJECTION), null),
                    defaults.calibration.dewarpProjection,
                ),
                dewarpCenterXPct = prefs.getInt(
                    key(side, FIELD_DEWARP_CENTER_X),
                    defaults.calibration.dewarpCenterXPct,
                ),
                dewarpCenterYPct = prefs.getInt(
                    key(side, FIELD_DEWARP_CENTER_Y),
                    defaults.calibration.dewarpCenterYPct,
                ),
            ).normalized(),
        )
    }

    companion object {
        const val PREFS_NAME = "blind_spot"
        const val KEY_ENABLED = "enabled"
        const val KEY_MIN_SPEED_KMH = "min_speed_kmh"
        const val KEY_MAX_SPEED_KMH = "max_speed_kmh"
        const val KEY_BSD_GLOW = "bsd_glow"
        const val KEY_BSD_ONLY = "bsd_only"
        const val KEY_SHARP_TURN_ENABLED = "sharp_turn_enabled"
        const val KEY_SHARP_TURN_ANGLE_DEG = "sharp_turn_angle_deg"

        const val DEFAULT_MIN_SPEED_KMH = 10
        const val DEFAULT_MAX_SPEED_KMH = 300
        const val DEFAULT_SHARP_TURN_ANGLE_DEG = 180
        const val DEFAULT_LEFT_PREVIEW_INDEX = 2
        const val DEFAULT_RIGHT_PREVIEW_INDEX = 3
        const val MIN_SPEED_KMH = 0
        const val MAX_SPEED_KMH = 300
        const val MIN_SHARP_TURN_ANGLE_DEG = 30
        const val MAX_SHARP_TURN_ANGLE_DEG = 780
        const val MIN_WINDOW_PCT = 20
        const val MAX_WINDOW_PCT = 100
        const val MIN_CROP_PCT = 10
        const val MAX_ROTATION_DEGREES = 180
        const val MIN_DEWARP_FOV_DEGREES = 60
        const val DEFAULT_DEWARP_FOV_DEGREES = 165
        const val MAX_DEWARP_FOV_DEGREES = 170
        const val MIN_DEWARP_CENTER_PCT = 5
        const val MAX_DEWARP_CENTER_PCT = 95
        const val MIN_PREVIEW_INDEX = 0
        const val MAX_PREVIEW_INDEX = 7
        const val POSITION_SCALE = 1_000

        private const val FIELD_PREVIEW_INDEX = "preview_index"
        private const val FIELD_TARGET = "target"
        private const val FIELD_WIDTH_PCT = "width_pct"
        private const val FIELD_HEIGHT_PCT = "height_pct"
        private const val FIELD_X_PCT = "x_pct"
        private const val FIELD_Y_PCT = "y_pct"
        private const val FIELD_CROP_LEFT_PCT = "crop_left_pct"
        private const val FIELD_CROP_TOP_PCT = "crop_top_pct"
        private const val FIELD_CROP_WIDTH_PCT = "crop_width_pct"
        private const val FIELD_CROP_HEIGHT_PCT = "crop_height_pct"
        private const val FIELD_CORRECTED_CROP_LEFT_PCT = "corrected_crop_left_pct"
        private const val FIELD_CORRECTED_CROP_TOP_PCT = "corrected_crop_top_pct"
        private const val FIELD_CORRECTED_CROP_WIDTH_PCT = "corrected_crop_width_pct"
        private const val FIELD_CORRECTED_CROP_HEIGHT_PCT = "corrected_crop_height_pct"
        private const val FIELD_ROTATION_DEGREES = "rotation_degrees"
        private const val FIELD_MIRROR = "mirror"
        private const val FIELD_SCALE_MODE = "scale_mode"
        private const val FIELD_DEWARP_ENABLED = "dewarp_enabled"
        private const val FIELD_DEWARP_FOV = "dewarp_fov"
        private const val FIELD_DEWARP_PROJECTION = "dewarp_projection"
        private const val FIELD_DEWARP_CENTER_X = "dewarp_center_x"
        private const val FIELD_DEWARP_CENTER_Y = "dewarp_center_y"

        fun defaultSideConfig(side: BlindSpotSide): BlindSpotSideConfig = when (side) {
            BlindSpotSide.LEFT -> BlindSpotSideConfig(
                side = side,
                previewIndex = DEFAULT_LEFT_PREVIEW_INDEX,
                target = BlindSpotDisplayTarget.CLUSTER,
                widthPct = 100,
                heightPct = 100,
                xPct = 0,
                yPct = 0,
                calibration = defaultCalibration(side),
            )
            BlindSpotSide.RIGHT -> BlindSpotSideConfig(
                side = side,
                previewIndex = DEFAULT_RIGHT_PREVIEW_INDEX,
                target = BlindSpotDisplayTarget.CENTER,
                widthPct = 36,
                heightPct = 36,
                xPct = POSITION_SCALE,
                yPct = POSITION_SCALE / 2,
                calibration = defaultCalibration(side),
            )
            BlindSpotSide.NONE -> error("A visible side is required")
        }

        fun defaultCalibration(side: BlindSpotSide = BlindSpotSide.NONE): BlindSpotCalibration {
            val raw = when (side) {
                BlindSpotSide.LEFT -> BlindSpotCrop(7, 19, 64, 61)
                BlindSpotSide.RIGHT -> BlindSpotCrop(29, 19, 64, 61)
                BlindSpotSide.NONE -> BlindSpotCrop(0, 0, 100, 100)
            }
            val corrected = when (side) {
                BlindSpotSide.LEFT, BlindSpotSide.RIGHT -> BlindSpotCrop(18, 19, 64, 61)
                BlindSpotSide.NONE -> raw
            }
            return BlindSpotCalibration(
                cropLeftPct = raw.leftPct,
                cropTopPct = raw.topPct,
                cropWidthPct = raw.widthPct,
                cropHeightPct = raw.heightPct,
                correctedCropLeftPct = corrected.leftPct,
                correctedCropTopPct = corrected.topPct,
                correctedCropWidthPct = corrected.widthPct,
                correctedCropHeightPct = corrected.heightPct,
                rotationDegrees = when (side) {
                    BlindSpotSide.LEFT -> -30
                    BlindSpotSide.RIGHT -> 30
                    BlindSpotSide.NONE -> 0
                },
                mirrorHorizontally = side != BlindSpotSide.NONE,
                scaleMode = BlindSpotScaleMode.FILL,
                dewarpEnabled = true,
                dewarpFovDegrees = DEFAULT_DEWARP_FOV_DEGREES,
                dewarpProjection = BlindSpotDewarpProjection.CYLINDRICAL,
                dewarpCenterXPct = 50,
                dewarpCenterYPct = 50,
            )
        }

        fun windowRect(displayWidth: Int, displayHeight: Int, config: BlindSpotSideConfig): Rect {
            val width = (displayWidth * config.widthPct / 100f).roundToInt()
                .coerceIn(1, displayWidth.coerceAtLeast(1))
            val height = (displayHeight * config.heightPct / 100f).roundToInt()
                .coerceIn(1, displayHeight.coerceAtLeast(1))
            val maxX = (displayWidth - width).coerceAtLeast(0)
            val maxY = (displayHeight - height).coerceAtLeast(0)
            val left = (maxX * config.xPct / POSITION_SCALE.toFloat()).roundToInt().coerceIn(0, maxX)
            val top = (maxY * config.yPct / POSITION_SCALE.toFloat()).roundToInt().coerceIn(0, maxY)
            return Rect(left, top, left + width, top + height)
        }

        private fun key(side: BlindSpotSide, field: String): String =
            "${side.name.lowercase()}_$field"

        private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }
}

data class BlindSpotPreferenceSnapshot(
    val enabled: Boolean,
    val minSpeedKmh: Int,
    val maxSpeedKmh: Int,
    val bsdGlow: Boolean,
    val bsdOnly: Boolean,
    val sharpTurnEnabled: Boolean,
    val sharpTurnAngleDeg: Int,
    val left: BlindSpotSideConfig,
    val right: BlindSpotSideConfig,
) {
    fun side(side: BlindSpotSide): BlindSpotSideConfig = when (side) {
        BlindSpotSide.LEFT -> left
        BlindSpotSide.RIGHT -> right
        BlindSpotSide.NONE -> error("A visible side is required")
    }

    fun normalized(): BlindSpotPreferenceSnapshot = if (minSpeedKmh <= maxSpeedKmh) this else copy(
        minSpeedKmh = maxSpeedKmh,
    )
}

internal fun blindSpotDisplayGeometry(
    config: BlindSpotSideConfig,
    useCluster: Boolean,
): BlindSpotSideConfig {
    if (config.target != BlindSpotDisplayTarget.CLUSTER || useCluster) return config
    return config.copy(
        widthPct = minOf(config.widthPct, 45),
        heightPct = minOf(config.heightPct, 50),
        xPct = if (config.side == BlindSpotSide.LEFT) 0 else BlindSpotPreferences.POSITION_SCALE,
        yPct = BlindSpotPreferences.POSITION_SCALE / 2,
    )
}

private fun BlindSpotCalibration.normalized(): BlindSpotCalibration {
    val raw = crop(false).normalized()
    val corrected = crop(true).normalized()
    return copy(
        cropLeftPct = raw.leftPct,
        cropTopPct = raw.topPct,
        cropWidthPct = raw.widthPct,
        cropHeightPct = raw.heightPct,
        correctedCropLeftPct = corrected.leftPct,
        correctedCropTopPct = corrected.topPct,
        correctedCropWidthPct = corrected.widthPct,
        correctedCropHeightPct = corrected.heightPct,
        rotationDegrees = rotationDegrees.coerceIn(
            -BlindSpotPreferences.MAX_ROTATION_DEGREES,
            BlindSpotPreferences.MAX_ROTATION_DEGREES,
        ),
        dewarpFovDegrees = dewarpFovDegrees.coerceIn(
            BlindSpotPreferences.MIN_DEWARP_FOV_DEGREES,
            BlindSpotPreferences.MAX_DEWARP_FOV_DEGREES,
        ),
        dewarpCenterXPct = dewarpCenterXPct.coerceIn(
            BlindSpotPreferences.MIN_DEWARP_CENTER_PCT,
            BlindSpotPreferences.MAX_DEWARP_CENTER_PCT,
        ),
        dewarpCenterYPct = dewarpCenterYPct.coerceIn(
            BlindSpotPreferences.MIN_DEWARP_CENTER_PCT,
            BlindSpotPreferences.MAX_DEWARP_CENTER_PCT,
        ),
    )
}

private fun BlindSpotCrop.normalized(): BlindSpotCrop {
    val safeWidth = widthPct.coerceIn(BlindSpotPreferences.MIN_CROP_PCT, 100)
    val safeHeight = heightPct.coerceIn(BlindSpotPreferences.MIN_CROP_PCT, 100)
    return copy(
        leftPct = leftPct.coerceIn(0, 100 - safeWidth),
        topPct = topPct.coerceIn(0, 100 - safeHeight),
        widthPct = safeWidth,
        heightPct = safeHeight,
    )
}
