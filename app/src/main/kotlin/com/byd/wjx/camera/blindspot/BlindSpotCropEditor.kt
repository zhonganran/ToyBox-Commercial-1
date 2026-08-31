package com.byd.wjx.camera.blindspot

internal enum class BlindSpotCropGesture {
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

internal fun adjustBlindSpotCrop(
    crop: BlindSpotCrop,
    gesture: BlindSpotCropGesture,
    deltaXPct: Int,
    deltaYPct: Int,
): BlindSpotCrop {
    val left = crop.leftPct
    val top = crop.topPct
    val right = crop.leftPct + crop.widthPct
    val bottom = crop.topPct + crop.heightPct
    val minimum = BlindSpotPreferences.MIN_CROP_PCT

    return when (gesture) {
        BlindSpotCropGesture.MOVE -> crop.copy(
            leftPct = (left + deltaXPct).coerceIn(0, 100 - crop.widthPct),
            topPct = (top + deltaYPct).coerceIn(0, 100 - crop.heightPct),
        )
        BlindSpotCropGesture.TOP_LEFT -> {
            val nextLeft = (left + deltaXPct).coerceIn(0, right - minimum)
            val nextTop = (top + deltaYPct).coerceIn(0, bottom - minimum)
            BlindSpotCrop(nextLeft, nextTop, right - nextLeft, bottom - nextTop)
        }
        BlindSpotCropGesture.TOP_RIGHT -> {
            val nextRight = (right + deltaXPct).coerceIn(left + minimum, 100)
            val nextTop = (top + deltaYPct).coerceIn(0, bottom - minimum)
            BlindSpotCrop(left, nextTop, nextRight - left, bottom - nextTop)
        }
        BlindSpotCropGesture.BOTTOM_LEFT -> {
            val nextLeft = (left + deltaXPct).coerceIn(0, right - minimum)
            val nextBottom = (bottom + deltaYPct).coerceIn(top + minimum, 100)
            BlindSpotCrop(nextLeft, top, right - nextLeft, nextBottom - top)
        }
        BlindSpotCropGesture.BOTTOM_RIGHT -> {
            val nextRight = (right + deltaXPct).coerceIn(left + minimum, 100)
            val nextBottom = (bottom + deltaYPct).coerceIn(top + minimum, 100)
            BlindSpotCrop(left, top, nextRight - left, nextBottom - top)
        }
    }
}
