package com.byd.wjx.lyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class LyricFrame(
    val previous2: String = "",
    val previous: String = "",
    val current: String = "",
    val next: String = "",
    val next2: String = "",
    val title: String = "",
    val artist: String = "",
)

class LyricOverlayView constructor(
    context: Context,
) : View(context) {
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

    private var frame: LyricFrame = LyricFrame()
    private var pendingFrame: LyricFrame? = null
    private var animationStartedAt = 0L
    private var animationDirection = 0
    private var textSizeSp: Float = 18f
    private var visibleLineCount: Int = DEFAULT_VISIBLE_LINE_COUNT
    private var horizontalPositionPct: Float = 50f
    private var style: LyricDisplayStyle = LyricDisplayStyle(
        font = LyricStylePresets.font("soft"),
        motion = LyricStylePresets.motion("soft"),
        sungColor = Color.rgb(148, 216, 188),
        currentColor = Color.rgb(255, 242, 165),
        upcomingColor = Color.rgb(190, 214, 228),
        karaokeEnabled = true,
    )

    fun setFrame(value: LyricFrame, animate: Boolean = true) {
        if (frame == value || pendingFrame == value) return
        val direction = when {
            frame.next.isNotBlank() && frame.next == value.current -> 1
            frame.previous.isNotBlank() && frame.previous == value.current -> -1
            else -> 0
        }
        val shouldAnimate = frame.current.isNotBlank() &&
            value.current.isNotBlank() &&
            frame.current != value.current &&
            direction != 0 &&
            style.motion.scrollDurationMs > 0L &&
            animate
        if (shouldAnimate) {
            pendingFrame = value
            animationDirection = direction
            animationStartedAt = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        } else {
            frame = value
            pendingFrame = null
            animationDirection = 0
            invalidate()
        }
    }

    fun setTextSizeSp(value: Float) {
        val next = value.coerceIn(10f, 48f)
        if (textSizeSp == next) return
        textSizeSp = next
        requestLayout()
        invalidate()
    }

    fun setVisibleLineCount(value: Int) {
        val next = value.coerceIn(MIN_VISIBLE_LINE_COUNT, MAX_VISIBLE_LINE_COUNT)
        if (visibleLineCount == next) return
        visibleLineCount = next
        requestLayout()
        invalidate()
    }

    /** Positions each lyric line's visible bounds from the left edge (0%) to the right edge (100%). */
    fun setHorizontalPositionPct(value: Float) {
        val next = value.coerceIn(0f, 100f)
        if (horizontalPositionPct == next) return
        horizontalPositionPct = next
        invalidate()
    }

    fun setDisplayStyle(value: LyricDisplayStyle) {
        if (style == value) return
        style = value
        applyFontPreset(value.font)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(dp(120f).toInt())
        val desiredHeight = desiredHeightPx() + paddingTop + paddingBottom
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = pendingFrame
        canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        val focusCenterY = contentCenterY()
        val lineHeight = lineHeightPx()
        if (next != null) {
            val rawProgress = ((SystemClock.uptimeMillis() - animationStartedAt).toFloat() / style.motion.scrollDurationMs)
                .coerceIn(0f, 1f)
            val progress = ease(rawProgress, style.motion.easing)
            val offset = lineHeight * progress
            if (animationDirection >= 0) {
                drawFrame(canvas, frame, -offset, focusCenterY, lineHeight)
                if (2 in visibleOffsets()) {
                    drawIncomingLine(
                        canvas = canvas,
                        text = next.next2,
                        lineCenterY = focusCenterY + 3f * lineHeight - offset,
                        focusCenterY = focusCenterY,
                        lineHeight = lineHeight,
                        relativeIndex = 2,
                    )
                }
            } else {
                drawFrame(canvas, frame, offset, focusCenterY, lineHeight)
                if (-2 in visibleOffsets()) {
                    drawIncomingLine(
                        canvas = canvas,
                        text = next.previous2,
                        lineCenterY = focusCenterY - 3f * lineHeight + offset,
                        focusCenterY = focusCenterY,
                        lineHeight = lineHeight,
                        relativeIndex = -2,
                    )
                }
            }
            if (rawProgress >= 1f) {
                frame = next
                pendingFrame = null
                animationDirection = 0
                requestLayout()
            } else {
                postInvalidateOnAnimation()
            }
        } else {
            drawFrame(canvas, frame, 0f, focusCenterY, lineHeight)
        }
        canvas.restore()
    }

    private fun drawFrame(
        canvas: Canvas,
        value: LyricFrame,
        verticalOffset: Float,
        focusCenterY: Float,
        lineHeight: Float,
    ) {
        val lines = listOf(value.previous2, value.previous, value.current, value.next, value.next2)
        if (lines.all { it.isBlank() }) return

        val baseCenterY = focusCenterY + verticalOffset
        val allowedOffsets = transitionOffsets()
        lines.forEachIndexed { index, text ->
            if (text.isBlank()) return@forEachIndexed
            val offset = index - 2
            if (offset !in allowedOffsets) return@forEachIndexed
            drawLine(
                canvas = canvas,
                text = text,
                lineCenterY = baseCenterY + offset * lineHeight,
                focusCenterY = focusCenterY,
                lineHeight = lineHeight,
                maxWidth = width - paddingLeft - paddingRight - dp(18f),
                relativeIndex = offset,
            )
        }
    }

    private fun visibleOffsets(): Set<Int> = when (visibleLineCount) {
        1 -> setOf(0)
        2 -> setOf(0, 1)
        3 -> setOf(-1, 0, 1)
        4 -> setOf(-1, 0, 1, 2)
        else -> setOf(-2, -1, 0, 1, 2)
    }

    private fun transitionOffsets(): Set<Int> {
        val visible = visibleOffsets()
        if (pendingFrame == null || animationDirection == 0) return visible
        val entering = if (animationDirection > 0) {
            visible.map { it + 1 }
        } else {
            visible.map { it - 1 }
        }
        return visible + entering
    }

    private fun drawIncomingLine(
        canvas: Canvas,
        text: String,
        lineCenterY: Float,
        focusCenterY: Float,
        lineHeight: Float,
        relativeIndex: Int,
    ) {
        if (text.isBlank()) return
        drawLine(
            canvas = canvas,
            text = text,
            lineCenterY = lineCenterY,
            focusCenterY = focusCenterY,
            lineHeight = lineHeight,
            maxWidth = width - paddingLeft - paddingRight - dp(18f),
            relativeIndex = relativeIndex,
        )
    }

    private fun drawLine(
        canvas: Canvas,
        text: String,
        lineCenterY: Float,
        focusCenterY: Float,
        lineHeight: Float,
        maxWidth: Float,
        relativeIndex: Int,
    ) {
        val distanceRatio = (abs(lineCenterY - focusCenterY) / max(lineHeight, 1f)).coerceIn(0f, 1.2f)
        val centerAmount = (1f - distanceRatio).coerceIn(0f, 1f)
        val focus = smoothStep(centerAmount)
        val baseSize = sp(textSizeSp)
        val readableSideScale = style.motion.sideScale.coerceAtLeast(0.96f)
        val scale = lerp(readableSideScale, style.motion.currentScale, focus)
        val sideAlpha = style.motion.sideAlpha.coerceIn(0.92f, 1f)
        val alphaFactor = lerp(sideAlpha, 1f, focus).coerceIn(0f, 1f)
        val colorBase = blend(style.upcomingColor, style.sungColor, 0.28f)
        val colorSung = blend(colorBase, style.sungColor, 0.72f)
        val colorCurrent = if (style.karaokeEnabled) {
            blend(colorSung, style.currentColor, style.motion.highlightStrength.coerceIn(0.5f, 1f))
        } else {
            style.currentColor
        }
        val color = if (focus <= 0.35f) {
            blend(colorBase, colorSung, focus / 0.35f)
        } else {
            blend(colorSung, colorCurrent, ((focus - 0.35f) / 0.65f).coerceIn(0f, 1f))
        }
        val fittedSize = fittedTextSize(text, baseSize * scale, maxWidth)
        val signedOffset = relativeIndex.coerceIn(-1, 1).toFloat()
        val sideWeight = 1f - focus
        val textWidth = textPaint.measureText(text)
        val baseCenterX = horizontalCenterFor(
            containerWidth = width,
            paddingLeft = paddingLeft,
            paddingRight = paddingRight,
            textWidth = textWidth,
            positionPct = horizontalPositionPct,
        )
        val x = (baseCenterX +
            dp(style.motion.currentOffsetDp) * focus +
            dp(style.motion.sideOffsetDp) * signedOffset * sideWeight)
            .coerceIn(
                paddingLeft + textWidth / 2f,
                width - paddingRight - textWidth / 2f,
            )
        textPaint.textSize = fittedSize
        textPaint.color = color
        textPaint.alpha = (255f * alphaFactor).toInt().coerceIn(0, 255)
        strokePaint.textSize = fittedSize
        strokePaint.strokeWidth = dp(style.font.strokeWidthDp)
        strokePaint.color = style.font.strokeColor
        strokePaint.alpha = (255f * alphaFactor).toInt().coerceIn(0, 255)

        val fm = textPaint.fontMetrics
        val baseline = lineCenterY - (fm.ascent + fm.descent) / 2f
        canvas.save()
        val rotate = style.motion.tiltDegrees.coerceIn(-10f, 10f) * signedOffset * sideWeight
        if (rotate != 0f) canvas.rotate(rotate, x, baseline)

        if (style.motion.spotlightAlpha > 0f && focus > 0.55f) {
            val textWidth = textPaint.measureText(text)
            val padX = dp(18f)
            val padY = dp(7f)
            spotlightPaint.color = withAlpha(
                style.currentColor,
                (Color.alpha(style.currentColor) * style.motion.spotlightAlpha.coerceIn(0f, 0.45f) * focus).toInt(),
            )
            spotlightRect.set(
                x - textWidth / 2f - padX,
                baseline + fm.ascent - padY,
                x + textWidth / 2f + padX,
                baseline + fm.descent + padY,
            )
            val radius = dp(18f)
            canvas.drawRoundRect(spotlightRect, radius, radius, spotlightPaint)
        }

        if (focus > 0.55f && style.motion.glowRadiusDp > 0f && style.motion.glowAlpha > 0f) {
            glowPaint.set(textPaint)
            glowPaint.textSize = fittedSize
            glowPaint.color = color
            glowPaint.alpha = (255f * style.motion.glowAlpha * focus).toInt().coerceIn(0, 255)
            glowPaint.setShadowLayer(dp(style.motion.glowRadiusDp), 0f, 0f, withAlpha(color, glowPaint.alpha))
            canvas.drawText(text, x, baseline, glowPaint)
            glowPaint.clearShadowLayer()
        }
        if (style.font.strokeWidthDp > 0f) {
            canvas.drawText(text, x, baseline, strokePaint)
        }
        canvas.drawText(text, x, baseline, textPaint)
        canvas.restore()
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

    private fun lineHeightPx(): Float = sp(textSizeSp) * 1.42f + dp(style.font.lineSpacingExtraDp)

    @Suppress("DEPRECATION")
    private fun desiredHeightPx(): Int =
        desiredHeightPx(
            textSizeSp = textSizeSp,
            style = style,
            density = resources.displayMetrics.density,
            scaledDensity = resources.displayMetrics.scaledDensity,
            lineCount = visibleLineCount,
        )

    private fun contentCenterY(): Float {
        val center = ((height - paddingTop - paddingBottom) / 2f) + paddingTop
        return if (visibleLineCount % 2 == 0) center - lineHeightPx() / 2f else center
    }

    private fun applyFontPreset(preset: LyricFontPreset) {
        val typeface = Typeface.create(preset.typefaceFamily, preset.typefaceStyle)
        textPaint.typeface = typeface
        strokePaint.typeface = typeface
        glowPaint.typeface = typeface
        textPaint.letterSpacing = preset.letterSpacingEm
        strokePaint.letterSpacing = preset.letterSpacingEm
        glowPaint.letterSpacing = preset.letterSpacingEm
        textPaint.textSkewX = preset.textSkewX
        strokePaint.textSkewX = preset.textSkewX
        glowPaint.textSkewX = preset.textSkewX
        if (preset.shadowRadiusDp > 0f) {
            textPaint.setShadowLayer(dp(preset.shadowRadiusDp), 0f, dp(preset.shadowDyDp), preset.shadowColor)
        } else {
            textPaint.clearShadowLayer()
        }
    }

    private fun ease(value: Float, type: String): Float {
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

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return (3f - 2f * t) * t * t
    }

    private fun lerp(from: Float, to: Float, amount: Float): Float =
        from + (to - from) * amount.coerceIn(0f, 1f)

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val a = Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * t).toInt()
        val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * t).toInt()
        val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * t).toInt()
        val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {
        internal fun horizontalCenterFor(
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

        fun desiredHeightPx(
            textSizeSp: Float,
            style: LyricDisplayStyle,
            density: Float,
            scaledDensity: Float,
            lineCount: Int = DEFAULT_VISIBLE_LINE_COUNT,
        ): Int {
            val lineHeight = textSizeSp * scaledDensity * 1.42f + style.font.lineSpacingExtraDp * density
            val safeLineCount = lineCount.coerceIn(MIN_VISIBLE_LINE_COUNT, MAX_VISIBLE_LINE_COUNT)
            return (lineHeight * (safeLineCount + 0.45f)).roundToInt().coerceAtLeast(64)
        }

        private const val MIN_VISIBLE_LINE_COUNT = 1
        private const val MAX_VISIBLE_LINE_COUNT = 5
        private const val DEFAULT_VISIBLE_LINE_COUNT = 5
    }
}
