package com.byd.wjx.electronicmirror

import android.graphics.Matrix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Builds one static GPU texture transform; no camera frame is copied or decoded. */
internal fun electronicMirrorTextureMatrix(
    calibration: ElectronicMirrorCalibration,
    width: Int,
    height: Int,
): Matrix {
    if (width <= 0 || height <= 0) return Matrix()
    val source = electronicMirrorSourceQuad(calibration, width.toFloat(), height.toFloat())
    val destination = floatArrayOf(
        0f, 0f,
        width.toFloat(), 0f,
        width.toFloat(), height.toFloat(),
        0f, height.toFloat(),
    )
    return Matrix().apply {
        if (!setPolyToPoly(source, 0, destination, 0, 4)) reset()
    }
}

/**
 * Returns top-left, top-right, bottom-right and bottom-left source coordinates.
 *
 * Yaw and pitch pan the selected source area. A small proportional inset gives a full-frame
 * selection enough room to move without exposing transparent edges. Roll rotates around the
 * source-area centre and is fitted back inside the camera frame before the perspective mapping.
 */
internal fun electronicMirrorSourceQuad(
    calibration: ElectronicMirrorCalibration,
    width: Float,
    height: Float,
): FloatArray {
    if (width <= 0f || height <= 0f) return FloatArray(8)
    val safe = calibration.sanitized()
    val points = safe.points.map { point ->
        MutableNormalizedPoint(
            x = point.xPermille / ElectronicMirrorCalibration.COORDINATE_MAX.toFloat(),
            y = point.yPermille / ElectronicMirrorCalibration.COORDINATE_MAX.toFloat(),
        )
    }
    val centerX = points.map { it.x }.average().toFloat()
    val centerY = points.map { it.y }.average().toFloat()
    val yawFraction = safe.yawDegrees / ElectronicMirrorCalibration.MAX_ANGLE_DEGREES.toFloat()
    val pitchFraction = safe.pitchDegrees / ElectronicMirrorCalibration.MAX_ANGLE_DEGREES.toFloat()

    val horizontalInset = abs(yawFraction) * MAX_ANGLE_CROP_FRACTION
    val verticalInset = abs(pitchFraction) * MAX_ANGLE_CROP_FRACTION
    val horizontalScale = 1f - horizontalInset * 2f
    val verticalScale = 1f - verticalInset * 2f
    points.forEach { point ->
        point.x = centerX + (point.x - centerX) * horizontalScale
        point.y = centerY + (point.y - centerY) * verticalScale
    }

    if (safe.rollDegrees != 0) {
        val radians = safe.rollDegrees * PI.toFloat() / 180f
        val cosine = cos(radians)
        val sine = sin(radians)
        points.forEach { point ->
            val x = point.x - centerX
            val y = point.y - centerY
            point.x = centerX + x * cosine - y * sine
            point.y = centerY + x * sine + y * cosine
        }
        fitAroundCenter(points, centerX, centerY)
    }

    translateInsideFrame(
        points = points,
        desiredX = yawFraction * MAX_ANGLE_CROP_FRACTION,
        desiredY = pitchFraction * MAX_ANGLE_CROP_FRACTION,
    )

    return FloatArray(8) { index ->
        val point = points[index / 2]
        if (index % 2 == 0) point.x * width else point.y * height
    }
}

private fun fitAroundCenter(
    points: List<MutableNormalizedPoint>,
    centerX: Float,
    centerY: Float,
) {
    var scale = 1f
    points.forEach { point ->
        val dx = point.x - centerX
        val dy = point.y - centerY
        if (dx > 0f) scale = min(scale, (1f - centerX) / dx)
        if (dx < 0f) scale = min(scale, centerX / -dx)
        if (dy > 0f) scale = min(scale, (1f - centerY) / dy)
        if (dy < 0f) scale = min(scale, centerY / -dy)
    }
    if (scale >= 1f) return
    val safeScale = scale.coerceAtLeast(MIN_FIT_SCALE)
    points.forEach { point ->
        point.x = centerX + (point.x - centerX) * safeScale
        point.y = centerY + (point.y - centerY) * safeScale
    }
}

private fun translateInsideFrame(
    points: List<MutableNormalizedPoint>,
    desiredX: Float,
    desiredY: Float,
) {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val shiftX = desiredX.coerceIn(-minX, 1f - maxX)
    val shiftY = desiredY.coerceIn(-minY, 1f - maxY)
    points.forEach { point ->
        point.x = (point.x + shiftX).coerceIn(0f, 1f)
        point.y = (point.y + shiftY).coerceIn(0f, 1f)
    }
}

private data class MutableNormalizedPoint(var x: Float, var y: Float)

private const val MAX_ANGLE_CROP_FRACTION = 0.08f
private const val MIN_FIT_SCALE = 0.1f
