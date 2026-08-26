package com.byd.wjx.lyrics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import kotlin.math.abs
import kotlin.math.max

/** Owns lyric typography and line effects; the View only schedules frames and positions lines. */
internal class LyricLineRenderer(
    private val displayMetrics: DisplayMetrics,
) {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.02f
        setShadowLayer(dp(4f), 0f, dp(1.5f), 0xAA000000.toInt())
    }
    private val strokePaint = TextPaint(textPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = 0x66000000
    }
    private val glowPaint = TextPaint(textPaint)
    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spotlightRect = RectF()

    fun applyFontPreset(preset: LyricFontPreset) {
        val typeface = Typeface.create(preset.typefaceFamily, preset.typefaceStyle)
        listOf(textPaint, strokePaint, glowPaint).forEach { paint ->
            paint.typeface = typeface
            paint.letterSpacing = preset.letterSpacingEm
            paint.textSkewX = preset.textSkewX
        }
        if (preset.shadowRadiusDp > 0f) {
            textPaint.setShadowLayer(
                dp(preset.shadowRadiusDp),
                0f,
                dp(preset.shadowDyDp),
                preset.shadowColor,
            )
        } else {
            textPaint.clearShadowLayer()
        }
    }

    fun drawLine(
        canvas: Canvas,
        text: String,
        lineCenterY: Float,
        focusCenterY: Float,
        lineHeight: Float,
        maxWidth: Float,
        relativeIndex: Int,
        containerWidth: Int,
        paddingLeft: Int,
        paddingRight: Int,
        contentTop: Float,
        contentBottom: Float,
        textSizeSp: Float,
        horizontalPositionPct: Float,
        style: LyricDisplayStyle,
    ) {
        val focus = lyricFocus(lineCenterY, focusCenterY, lineHeight)
        val readableSideScale = style.motion.sideScale.coerceAtLeast(0.96f)
        val scale = lyricLerp(readableSideScale, style.motion.currentScale, focus)
        val sideAlpha = style.motion.sideAlpha.coerceIn(0.92f, 1f)
        val color = lyricLineColor(style, focus)
        val fittedSize = fittedTextSize(text, sp(textSizeSp) * scale, maxWidth)
        val signedOffset = relativeIndex.coerceIn(-1, 1).toFloat()
        val sideWeight = 1f - focus
        val textWidth = textPaint.measureText(text)
        val baseCenterX = lyricHorizontalCenterFor(
            containerWidth,
            paddingLeft,
            paddingRight,
            textWidth,
            horizontalPositionPct,
        )
        val x = (baseCenterX +
            dp(style.motion.currentOffsetDp) * focus +
            dp(style.motion.sideOffsetDp) * signedOffset * sideWeight)
            .coerceIn(paddingLeft + textWidth / 2f, containerWidth - paddingRight - textWidth / 2f)
        textPaint.textSize = fittedSize
        val fm = textPaint.fontMetrics
        val effectOutset = dp(
            maxOf(
                style.font.shadowRadiusDp + abs(style.font.shadowDyDp),
                style.motion.glowRadiusDp,
                style.font.strokeWidthDp / 2f,
            ),
        )
        val edgeAlpha = lyricVerticalEdgeAlpha(
            lineCenterY,
            contentTop,
            contentBottom,
            (fm.descent - fm.ascent) / 2f + effectOutset,
            dp(LYRIC_EDGE_FADE_DISTANCE_DP),
        )
        val alphaFactor = (lyricLerp(sideAlpha, 1f, focus) * edgeAlpha).coerceIn(0f, 1f)
        if (alphaFactor <= 0f) return
        val paintAlpha = (255f * alphaFactor).toInt().coerceIn(0, 255)
        textPaint.color = color
        textPaint.alpha = paintAlpha
        strokePaint.textSize = fittedSize
        strokePaint.strokeWidth = dp(style.font.strokeWidthDp)
        strokePaint.color = style.font.strokeColor
        strokePaint.alpha = paintAlpha

        val baseline = lineCenterY - (fm.ascent + fm.descent) / 2f
        canvas.save()
        val rotation = style.motion.tiltDegrees.coerceIn(-10f, 10f) * signedOffset * sideWeight
        if (rotation != 0f) canvas.rotate(rotation, x, baseline)
        drawSpotlight(canvas, text, x, baseline, fm, focus, style)
        drawGlow(canvas, text, x, baseline, fittedSize, color, focus, style)
        if (style.font.strokeWidthDp > 0f) canvas.drawText(text, x, baseline, strokePaint)
        canvas.drawText(text, x, baseline, textPaint)
        canvas.restore()
    }

    private fun drawSpotlight(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        metrics: Paint.FontMetrics,
        focus: Float,
        style: LyricDisplayStyle,
    ) {
        if (style.motion.spotlightAlpha <= 0f || focus <= 0.55f) return
        val width = textPaint.measureText(text)
        spotlightPaint.color = lyricWithAlpha(
            style.currentColor,
            (Color.alpha(style.currentColor) *
                style.motion.spotlightAlpha.coerceIn(0f, 0.45f) * focus).toInt(),
        )
        spotlightRect.set(
            x - width / 2f - dp(18f),
            baseline + metrics.ascent - dp(7f),
            x + width / 2f + dp(18f),
            baseline + metrics.descent + dp(7f),
        )
        canvas.drawRoundRect(spotlightRect, dp(18f), dp(18f), spotlightPaint)
    }

    private fun drawGlow(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        fittedSize: Float,
        color: Int,
        focus: Float,
        style: LyricDisplayStyle,
    ) {
        if (focus <= 0.55f || style.motion.glowRadiusDp <= 0f || style.motion.glowAlpha <= 0f) return
        glowPaint.set(textPaint)
        glowPaint.textSize = fittedSize
        glowPaint.color = color
        glowPaint.alpha = (255f * style.motion.glowAlpha * focus).toInt().coerceIn(0, 255)
        glowPaint.setShadowLayer(
            dp(style.motion.glowRadiusDp),
            0f,
            0f,
            lyricWithAlpha(color, glowPaint.alpha),
        )
        canvas.drawText(text, x, baseline, glowPaint)
        glowPaint.clearShadowLayer()
    }

    private fun fittedTextSize(text: String, startSize: Float, maxWidth: Float): Float {
        var size = startSize
        textPaint.textSize = size
        while (size > sp(10f) && textPaint.measureText(text) > maxWidth) {
            size *= 0.92f
            textPaint.textSize = size
        }
        return max(size, sp(10f))
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, displayMetrics)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, displayMetrics)
}
