package com.byd.wjx.lyrics

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val MIN_LYRIC_VISIBLE_LINE_COUNT = 1
internal const val MAX_LYRIC_VISIBLE_LINE_COUNT = 5
internal const val DEFAULT_LYRIC_VISIBLE_LINE_COUNT = 5
internal const val LYRIC_EDGE_FADE_DISTANCE_DP = 6f

internal fun lyricAnimationDirection(current: LyricFrame, next: LyricFrame): Int = when {
    current.next.isNotBlank() && current.next == next.current -> 1
    current.previous.isNotBlank() && current.previous == next.current -> -1
    else -> 0
}

internal fun shouldAnimateLyricTransition(
    current: LyricFrame,
    next: LyricFrame,
    direction: Int,
    durationMs: Long,
    animate: Boolean,
): Boolean = animate &&
    current.current.isNotBlank() &&
    next.current.isNotBlank() &&
    current.current != next.current &&
    direction != 0 &&
    durationMs > 0L

internal fun lyricVisibleOffsets(lineCount: Int): Set<Int> = when (
    lineCount.coerceIn(MIN_LYRIC_VISIBLE_LINE_COUNT, MAX_LYRIC_VISIBLE_LINE_COUNT)
) {
    1 -> setOf(0)
    2 -> setOf(0, 1)
    3 -> setOf(-1, 0, 1)
    4 -> setOf(-1, 0, 1, 2)
    else -> setOf(-2, -1, 0, 1, 2)
}

internal fun lyricTransitionOffsets(
    lineCount: Int,
    transitioning: Boolean,
    direction: Int,
): Set<Int> {
    val visible = lyricVisibleOffsets(lineCount)
    if (!transitioning || direction == 0) return visible
    val entering = if (direction > 0) visible.map { it + 1 } else visible.map { it - 1 }
    return visible + entering
}

internal fun lyricEase(value: Float, type: String): Float {
    val t = value.coerceIn(0f, 1f)
    return when (type) {
        "sine" -> sin(t * Math.PI / 2.0).toFloat()
        "quart" -> 1f - (1f - t).pow(4)
        "back" -> {
            val x = t - 1f
            1f + 2.70158f * x.pow(3) + 1.70158f * x.pow(2)
        }
        else -> 1f - (1f - t).pow(3)
    }.coerceIn(0f, 1.08f)
}

internal fun lyricFocus(centerY: Float, focusCenterY: Float, lineHeight: Float): Float {
    val distanceRatio =
        (kotlin.math.abs(centerY - focusCenterY) / lineHeight.coerceAtLeast(1f)).coerceIn(0f, 1.2f)
    val t = (1f - distanceRatio).coerceIn(0f, 1f)
    return (3f - 2f * t) * t * t
}

internal fun lyricLerp(from: Float, to: Float, amount: Float): Float =
    from + (to - from) * amount.coerceIn(0f, 1f)

internal fun lyricLineColor(style: LyricDisplayStyle, focus: Float): Int {
    val base = blendArgb(style.upcomingColor, style.sungColor, 0.28f)
    val sung = blendArgb(base, style.sungColor, 0.72f)
    val current = if (style.karaokeEnabled) {
        blendArgb(sung, style.currentColor, style.motion.highlightStrength.coerceIn(0.5f, 1f))
    } else {
        style.currentColor
    }
    return if (focus <= 0.35f) {
        blendArgb(base, sung, focus / 0.35f)
    } else {
        blendArgb(sung, current, ((focus - 0.35f) / 0.65f).coerceIn(0f, 1f))
    }
}

internal fun lyricWithAlpha(color: Int, alpha: Int): Int =
    argb(alpha.coerceIn(0, 255), red(color), green(color), blue(color))

internal fun lyricHorizontalCenterFor(
    containerWidth: Int,
    paddingLeft: Int,
    paddingRight: Int,
    textWidth: Float,
    positionPct: Float,
): Float {
    val safeWidth = (containerWidth - paddingLeft - paddingRight - textWidth).coerceAtLeast(0f)
    val pct = (positionPct / 100f).coerceIn(0f, 1f)
    return paddingLeft + safeWidth * pct + textWidth / 2f
}

internal fun lyricVerticalEdgeAlpha(
    centerY: Float,
    contentTop: Float,
    contentBottom: Float,
    effectHalfExtent: Float,
    fadeDistance: Float,
): Float {
    if (contentBottom <= contentTop) return 0f
    val nearestEdgeDistance = minOf(centerY - contentTop, contentBottom - centerY)
    return ((nearestEdgeDistance - effectHalfExtent.coerceAtLeast(0f)) / fadeDistance.coerceAtLeast(1f))
        .coerceIn(0f, 1f)
}

internal fun lyricDesiredHeightPx(
    textSizeSp: Float,
    style: LyricDisplayStyle,
    density: Float,
    scaledDensity: Float,
    lineCount: Int = DEFAULT_LYRIC_VISIBLE_LINE_COUNT,
): Int {
    val lineHeight = textSizeSp * scaledDensity * 1.42f + style.font.lineSpacingExtraDp * density
    val safeLineCount = lineCount.coerceIn(MIN_LYRIC_VISIBLE_LINE_COUNT, MAX_LYRIC_VISIBLE_LINE_COUNT)
    return (lineHeight * (safeLineCount + 0.45f)).roundToInt().coerceAtLeast(64)
}

private fun blendArgb(from: Int, to: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    val alpha = alpha(from) + ((alpha(to) - alpha(from)) * t).toInt()
    val red = red(from) + ((red(to) - red(from)) * t).toInt()
    val green = green(from) + ((green(to) - green(from)) * t).toInt()
    val blue = blue(from) + ((blue(to) - blue(from)) * t).toInt()
    return argb(alpha, red, green, blue)
}

private fun alpha(color: Int): Int = color ushr 24
private fun red(color: Int): Int = color shr 16 and 0xff
private fun green(color: Int): Int = color shr 8 and 0xff
private fun blue(color: Int): Int = color and 0xff
private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    alpha shl 24 or (red shl 16) or (green shl 8) or blue
