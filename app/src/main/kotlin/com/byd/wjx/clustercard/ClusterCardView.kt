package com.byd.wjx.clustercard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.byd.wjx.data.remote.DiParsData

internal class ClusterCardView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 0.6f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private var metrics: List<ClusterCardMetric> = emptyList()
    private var baseData: DiParsData? = null
    private var telemetryData: DiParsData? = null
    private var cardScale: Float = 0.65f
    private var nightMode: Boolean? = null

    fun bind(
        metrics: List<ClusterCardMetric>,
        baseData: DiParsData?,
        telemetryData: DiParsData?,
        scale: Float,
        nightMode: Boolean,
    ) {
        this.metrics = metrics
        this.baseData = baseData
        this.telemetryData = telemetryData
        cardScale = scale
        applyNightMode(nightMode)
        invalidate()
    }

    private fun applyNightMode(nightMode: Boolean) {
        if (this.nightMode == nightMode) return
        this.nightMode = nightMode
        if (nightMode) {
            dividerPaint.color = Color.argb(70, 255, 255, 255)
            labelPaint.color = Color.argb(210, 255, 255, 255)
            valuePaint.color = Color.WHITE
            labelPaint.setShadowLayer(density * 1.5f, 0f, density * 0.5f, Color.BLACK)
            valuePaint.setShadowLayer(density * 2f, 0f, density * 0.7f, Color.BLACK)
        } else {
            dividerPaint.color = Color.argb(70, 0, 0, 0)
            labelPaint.color = Color.argb(210, 0, 0, 0)
            valuePaint.color = Color.BLACK
            labelPaint.setShadowLayer(density * 1.5f, 0f, density * 0.5f, Color.WHITE)
            valuePaint.setShadowLayer(density * 2f, 0f, density * 0.7f, Color.WHITE)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (metrics.isEmpty() || width <= 0 || height <= 0) return

        val rows = ClusterCardLayout.rowCount(metrics.size)
        val contentLeft = density * 4f
        val contentRight = width - density * 10f
        val contentTop = density * 6f
        val contentBottom = height - density * 6f
        val rowHeight = (contentBottom - contentTop) / rows

        repeat(rows - 1) { row ->
            val y = contentTop + rowHeight * (row + 1)
            canvas.drawLine(contentLeft, y, contentRight, y, dividerPaint)
        }

        metrics.forEachIndexed { index, metric ->
            val row = index / 2
            val isOddLast = metrics.size % 2 == 1 && index == metrics.lastIndex
            val columns = if (metrics.size == 1 || isOddLast) 1 else 2
            val column = if (columns == 1) 0 else index % 2
            val cellWidth = (contentRight - contentLeft) / columns
            val left = contentLeft + column * cellWidth
            val right = left + cellWidth
            val top = contentTop + row * rowHeight
            val bottom = top + rowHeight

            if (columns == 2 && column == 0) {
                canvas.drawLine(right, top + density * 7f, right, bottom - density * 7f, dividerPaint)
            }
            drawMetric(canvas, metric, left, top, right, bottom)
        }
    }

    private fun drawMetric(
        canvas: Canvas,
        metric: ClusterCardMetric,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val horizontalPadding = density * 10f
        val availableWidth = (right - left - horizontalPadding * 2).coerceAtLeast(density * 30f)
        labelPaint.textSize = density * 11f * cardScale.coerceAtLeast(0.62f)
        valuePaint.textSize = density * 18f * cardScale.coerceAtLeast(0.62f)

        val label = metric.label
        val value = metric.displayValue(baseData, telemetryData)
        fitText(labelPaint, label, availableWidth, density * 8f)
        fitText(valuePaint, value, availableWidth, density * 11f)

        val labelBaseline = top + (bottom - top) * 0.38f - (labelPaint.ascent() + labelPaint.descent()) / 2f
        val valueBaseline = top + (bottom - top) * 0.70f - (valuePaint.ascent() + valuePaint.descent()) / 2f
        canvas.drawText(label, left + horizontalPadding, labelBaseline, labelPaint)
        canvas.drawText(value, left + horizontalPadding, valueBaseline, valuePaint)
    }

    private fun fitText(paint: Paint, text: String, maxWidth: Float, minSize: Float) {
        while (paint.textSize > minSize && paint.measureText(text) > maxWidth) {
            paint.textSize -= density * 0.5f
        }
    }
}
