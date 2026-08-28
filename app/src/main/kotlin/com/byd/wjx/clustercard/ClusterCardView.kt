package com.byd.wjx.clustercard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.byd.wjx.data.remote.DiParsData

internal class ClusterCardView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 0.6f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var metrics: List<ClusterCardMetric> = emptyList()
    private var baseData: DiParsData? = null
    private var telemetryData: DiParsData? = null
    private var cardScale: Float = 0.65f
    private var fontScale: Float = 1f
    private var heightScale: Float = 1f
    private var isNightMode = false

    fun bind(
        metrics: List<ClusterCardMetric>,
        baseData: DiParsData?,
        telemetryData: DiParsData?,
        config: ClusterCardConfig,
        nightMode: Boolean,
    ) {
        this.metrics = metrics
        this.baseData = baseData
        this.telemetryData = telemetryData
        cardScale = config.scale
        fontScale = config.fontScale
        heightScale = config.heightScale
        isNightMode = nightMode
        applyTextColor()
        invalidate()
    }

    init {
        applyTextColor()
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
        labelPaint.textSize = density * 11f * cardScale.coerceAtLeast(0.62f) * fontScale
        valuePaint.textSize = density * 18f * cardScale.coerceAtLeast(0.62f) * fontScale

        val label = metric.label
        val value = metric.displayValue(baseData, telemetryData)
        fitText(labelPaint, label, availableWidth, density * 8f)
        fitText(valuePaint, value, availableWidth, density * 11f)

        val labelHeight = labelPaint.descent() - labelPaint.ascent()
        val valueHeight = valuePaint.descent() - valuePaint.ascent()
        val lineGap = density * 4f * cardScale.coerceAtLeast(0.62f) * fontScale * heightScale
        val textBlockHeight = labelHeight + lineGap + valueHeight
        val textTop = top + ((bottom - top - textBlockHeight) / 2f).coerceAtLeast(0f)
        val labelBaseline = textTop - labelPaint.ascent()
        val valueBaseline = textTop + labelHeight + lineGap - valuePaint.ascent()
        canvas.drawText(label, left + horizontalPadding, labelBaseline, labelPaint)
        canvas.drawText(value, left + horizontalPadding, valueBaseline, valuePaint)
    }

    private fun fitText(paint: Paint, text: String, maxWidth: Float, minSize: Float) {
        while (paint.textSize > minSize && paint.measureText(text) > maxWidth) {
            paint.textSize -= density * 0.5f
        }
    }

    private fun applyTextColor() {
        val color = if (isNightMode) Color.WHITE else Color.BLACK
        dividerPaint.color = withAlpha(color, 70)
        labelPaint.color = color
        valuePaint.color = color
        labelPaint.clearShadowLayer()
        valuePaint.clearShadowLayer()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

}
