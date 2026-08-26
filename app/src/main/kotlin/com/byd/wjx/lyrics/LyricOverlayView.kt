package com.byd.wjx.lyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.TypedValue
import android.view.View

class LyricOverlayView constructor(
    context: Context,
) : View(context) {
    private var frame = LyricFrame()
    private var pendingFrame: LyricFrame? = null
    private var animationStartedAt = 0L
    private var animationDirection = 0
    private var textSizeSp = 18f
    private var visibleLineCount = DEFAULT_LYRIC_VISIBLE_LINE_COUNT
    private var horizontalPositionPct = 50f
    private var style = LyricDisplayStyle(
        font = LyricStylePresets.font("soft"),
        motion = LyricStylePresets.motion("soft"),
        sungColor = Color.rgb(148, 216, 188),
        currentColor = Color.rgb(255, 242, 165),
        upcomingColor = Color.rgb(190, 214, 228),
        karaokeEnabled = true,
    )
    private val renderer = LyricLineRenderer(resources.displayMetrics)

    fun setFrame(value: LyricFrame, animate: Boolean = true) {
        if (frame == value || pendingFrame == value) return
        val direction = lyricAnimationDirection(frame, value)
        if (
            shouldAnimateLyricTransition(
                current = frame,
                next = value,
                direction = direction,
                durationMs = style.motion.scrollDurationMs,
                animate = animate,
            )
        ) {
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
        val next = value.coerceIn(MIN_LYRIC_VISIBLE_LINE_COUNT, MAX_LYRIC_VISIBLE_LINE_COUNT)
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
        renderer.applyFontPreset(value.font)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(dp(120f).toInt())
        val desiredHeight = measuredLyricHeight() + paddingTop + paddingBottom
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = pendingFrame
        canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        val focusCenterY = contentCenterY()
        val lineHeight = lineHeightPx()
        if (next == null) {
            drawFrame(canvas, frame, 0f, focusCenterY, lineHeight)
        } else {
            drawTransition(canvas, next, focusCenterY, lineHeight)
        }
        canvas.restore()
    }

    private fun drawTransition(
        canvas: Canvas,
        next: LyricFrame,
        focusCenterY: Float,
        lineHeight: Float,
    ) {
        val rawProgress = (
            (SystemClock.uptimeMillis() - animationStartedAt).toFloat() /
                style.motion.scrollDurationMs
            ).coerceIn(0f, 1f)
        val offset = lineHeight * lyricEase(rawProgress, style.motion.easing)
        if (animationDirection >= 0) {
            drawFrame(canvas, frame, -offset, focusCenterY, lineHeight)
            if (2 in visibleOffsets()) {
                drawLine(
                    canvas,
                    next.next2,
                    focusCenterY + 3f * lineHeight - offset,
                    focusCenterY,
                    lineHeight,
                    2,
                )
            }
        } else {
            drawFrame(canvas, frame, offset, focusCenterY, lineHeight)
            if (-2 in visibleOffsets()) {
                drawLine(
                    canvas,
                    next.previous2,
                    focusCenterY - 3f * lineHeight + offset,
                    focusCenterY,
                    lineHeight,
                    -2,
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
    }

    private fun drawFrame(
        canvas: Canvas,
        value: LyricFrame,
        verticalOffset: Float,
        focusCenterY: Float,
        lineHeight: Float,
    ) {
        val lines = listOf(value.previous2, value.previous, value.current, value.next, value.next2)
        if (lines.all(String::isBlank)) return
        val baseCenterY = focusCenterY + verticalOffset
        val allowedOffsets = lyricTransitionOffsets(
            visibleLineCount,
            transitioning = pendingFrame != null,
            direction = animationDirection,
        )
        lines.forEachIndexed { index, text ->
            val offset = index - 2
            if (text.isNotBlank() && offset in allowedOffsets) {
                drawLine(
                    canvas,
                    text,
                    baseCenterY + offset * lineHeight,
                    focusCenterY,
                    lineHeight,
                    offset,
                )
            }
        }
    }

    private fun drawLine(
        canvas: Canvas,
        text: String,
        lineCenterY: Float,
        focusCenterY: Float,
        lineHeight: Float,
        relativeIndex: Int,
    ) {
        if (text.isBlank()) return
        renderer.drawLine(
            canvas = canvas,
            text = text,
            lineCenterY = lineCenterY,
            focusCenterY = focusCenterY,
            lineHeight = lineHeight,
            maxWidth = width - paddingLeft - paddingRight - dp(18f),
            relativeIndex = relativeIndex,
            containerWidth = width,
            paddingLeft = paddingLeft,
            paddingRight = paddingRight,
            contentTop = paddingTop.toFloat(),
            contentBottom = (height - paddingBottom).toFloat(),
            textSizeSp = textSizeSp,
            horizontalPositionPct = horizontalPositionPct,
            style = style,
        )
    }

    private fun visibleOffsets(): Set<Int> = lyricVisibleOffsets(visibleLineCount)

    private fun lineHeightPx(): Float = sp(textSizeSp) * 1.42f + dp(style.font.lineSpacingExtraDp)

    @Suppress("DEPRECATION")
    private fun measuredLyricHeight(): Int = lyricDesiredHeightPx(
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
        ): Float = lyricHorizontalCenterFor(
            containerWidth,
            paddingLeft,
            paddingRight,
            textWidth,
            positionPct,
        )

        internal fun verticalEdgeAlpha(
            centerY: Float,
            contentTop: Float,
            contentBottom: Float,
            effectHalfExtent: Float,
            fadeDistance: Float,
        ): Float = lyricVerticalEdgeAlpha(
            centerY,
            contentTop,
            contentBottom,
            effectHalfExtent,
            fadeDistance,
        )

        fun desiredHeightPx(
            textSizeSp: Float,
            style: LyricDisplayStyle,
            density: Float,
            scaledDensity: Float,
            lineCount: Int = DEFAULT_LYRIC_VISIBLE_LINE_COUNT,
        ): Int = lyricDesiredHeightPx(textSizeSp, style, density, scaledDensity, lineCount)
    }
}
