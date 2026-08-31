package com.byd.wjx.camera.blindspot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class BlindSpotCropOverlayView(
    context: Context,
    private val onCropChanged: (BlindSpotCrop, Boolean) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT_COLOR }
    private val handleCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
    }
    private var crop = BlindSpotCrop(0, 0, 100, 100)
    private var activeGesture: BlindSpotCropGesture? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartCrop = crop

    var dragging: Boolean = false
        private set

    fun setCrop(value: BlindSpotCrop) {
        if (dragging || crop == value) return
        crop = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val selection = selectionRect()
        canvas.drawRect(0f, 0f, width.toFloat(), selection.top, dimPaint)
        canvas.drawRect(0f, selection.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, dimPaint)
        canvas.drawRect(selection.right, selection.top, width.toFloat(), selection.bottom, dimPaint)
        canvas.drawRect(selection, borderPaint)
        cornerPoints(selection).forEach { (x, y) ->
            canvas.drawCircle(x, y, HANDLE_RADIUS_DP * density, handlePaint)
            canvas.drawCircle(x, y, HANDLE_CENTER_RADIUS_DP * density, handleCenterPaint)
        }

        val label = "${crop.widthPct}% × ${crop.heightPct}%"
        val textWidth = labelPaint.measureText(label)
        val labelHeight = 28f * density
        val labelLeft = selection.left.coerceIn(0f, (width - textWidth - 20f * density).coerceAtLeast(0f))
        val labelTop = (selection.top + 8f * density).coerceAtMost(height - labelHeight)
        canvas.drawRoundRect(
            labelLeft,
            labelTop,
            labelLeft + textWidth + 20f * density,
            labelTop + labelHeight,
            6f * density,
            6f * density,
            labelBackgroundPaint,
        )
        canvas.drawText(
            label,
            labelLeft + 10f * density,
            labelTop + 19f * density,
            labelPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val gesture = hitTest(event.x, event.y) ?: return false
                activeGesture = gesture
                dragStartX = event.x
                dragStartY = event.y
                dragStartCrop = crop
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val gesture = activeGesture ?: return false
                val deltaXPct = ((event.x - dragStartX) / width * 100f).roundToInt()
                val deltaYPct = ((event.y - dragStartY) / height * 100f).roundToInt()
                val next = adjustBlindSpotCrop(dragStartCrop, gesture, deltaXPct, deltaYPct)
                if (next != crop) {
                    crop = next
                    invalidate()
                    onCropChanged(next, false)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeGesture == null) return false
                val committedCrop = crop
                activeGesture = null
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onCropChanged(committedCrop, true)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitTest(x: Float, y: Float): BlindSpotCropGesture? {
        val rect = selectionRect()
        val hitRadius = HANDLE_HIT_RADIUS_DP * density
        val corners = cornerPoints(rect)
        val gestures = listOf(
            BlindSpotCropGesture.TOP_LEFT,
            BlindSpotCropGesture.TOP_RIGHT,
            BlindSpotCropGesture.BOTTOM_LEFT,
            BlindSpotCropGesture.BOTTOM_RIGHT,
        )
        corners.forEachIndexed { index, point ->
            if (hypot(x - point.first, y - point.second) <= hitRadius) return gestures[index]
        }
        return BlindSpotCropGesture.MOVE.takeIf { rect.contains(x, y) }
    }

    private fun selectionRect() = RectF(
        width * crop.leftPct / 100f,
        height * crop.topPct / 100f,
        width * (crop.leftPct + crop.widthPct) / 100f,
        height * (crop.topPct + crop.heightPct) / 100f,
    )

    private fun cornerPoints(rect: RectF) = listOf(
        rect.left to rect.top,
        rect.right to rect.top,
        rect.left to rect.bottom,
        rect.right to rect.bottom,
    )

    private companion object {
        const val ACCENT_COLOR = 0xFF20D99A.toInt()
        const val HANDLE_RADIUS_DP = 8f
        const val HANDLE_CENTER_RADIUS_DP = 3f
        const val HANDLE_HIT_RADIUS_DP = 28f
    }
}
