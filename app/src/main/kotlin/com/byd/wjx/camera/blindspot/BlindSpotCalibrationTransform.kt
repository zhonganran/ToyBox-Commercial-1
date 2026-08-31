package com.byd.wjx.camera.blindspot

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun blindSpotTransformValues(
    viewWidth: Int,
    viewHeight: Int,
    sourceWidth: Float,
    sourceHeight: Float,
    calibration: BlindSpotCalibration,
    corrected: Boolean = calibration.dewarpEnabled,
): FloatArray? {
    if (viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0f || sourceHeight <= 0f) return null
    val crop = calibration.crop(corrected)
    val cropWidth = sourceWidth * crop.widthPct / 100f
    val cropHeight = sourceHeight * crop.heightPct / 100f
    if (cropWidth <= 0f || cropHeight <= 0f) return null
    val cropCenterX = viewWidth *
        (crop.leftPct + crop.widthPct / 2f) / 100f
    val cropCenterY = viewHeight *
        (crop.topPct + crop.heightPct / 2f) / 100f
    val radians = Math.toRadians(calibration.rotationDegrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val rotatedWidth = abs(cosine) * cropWidth + abs(sine) * cropHeight
    val rotatedHeight = abs(sine) * cropWidth + abs(cosine) * cropHeight
    val scaleX: Float
    val scaleY: Float
    when (calibration.scaleMode) {
        BlindSpotScaleMode.FIT -> {
            val scale = minOf(viewWidth / rotatedWidth, viewHeight / rotatedHeight)
            scaleX = scale
            scaleY = scale
        }
        BlindSpotScaleMode.FILL -> {
            val scale = maxOf(viewWidth / rotatedWidth, viewHeight / rotatedHeight)
            scaleX = scale
            scaleY = scale
        }
        BlindSpotScaleMode.STRETCH -> {
            scaleX = viewWidth / rotatedWidth
            scaleY = viewHeight / rotatedHeight
        }
    }
    val mirror = if (calibration.mirrorHorizontally) -1f else 1f
    val sourceScaleX = sourceWidth / viewWidth
    val sourceScaleY = sourceHeight / viewHeight
    val a = scaleX * cosine * mirror * sourceScaleX
    val b = -scaleX * sine * sourceScaleY
    val d = scaleY * sine * mirror * sourceScaleX
    val e = scaleY * cosine * sourceScaleY
    return floatArrayOf(
        a,
        b,
        viewWidth / 2f - a * cropCenterX - b * cropCenterY,
        d,
        e,
        viewHeight / 2f - d * cropCenterX - e * cropCenterY,
        0f,
        0f,
        1f,
    )
}
