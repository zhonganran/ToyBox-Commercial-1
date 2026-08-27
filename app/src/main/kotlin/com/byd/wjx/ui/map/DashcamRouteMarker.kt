package com.byd.wjx.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

internal fun dashcamRouteMarker(
    mapView: MapView,
    position: GeoPoint,
    timestamp: Long,
    onClick: () -> Unit,
): Marker {
    val timeLabel = formatDashcamRouteMarkerTime(timestamp)
    return Marker(mapView).apply {
    this.position = position
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    title = timeLabel
    icon = dashcamRouteMarkerDrawable(mapView, timeLabel)
    infoWindow = null
    setOnMarkerClickListener { _, _ ->
        onClick()
        true
    }
}
}

private fun dashcamRouteMarkerDrawable(
    mapView: MapView,
    timeLabel: String,
): BitmapDrawable {
    val density = mapView.resources.displayMetrics.density.coerceAtLeast(1f)
    val width = (76f * density).roundToInt()
    val height = (42f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val body = RectF(2f * density, 2f * density, width - 2f * density, 33f * density)

    paint.style = Paint.Style.FILL
    paint.color = Color.argb(238, 18, 27, 31)
    canvas.drawRoundRect(body, 8f * density, 8f * density, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    paint.color = Color.rgb(53, 220, 214)
    canvas.drawRoundRect(body, 8f * density, 8f * density, paint)

    paint.style = Paint.Style.FILL
    val pointer = Path().apply {
        moveTo(width / 2f - 5f * density, 32f * density)
        lineTo(width / 2f + 5f * density, 32f * density)
        lineTo(width / 2f, height - 2f * density)
        close()
    }
    canvas.drawPath(pointer, paint)

    paint.color = Color.rgb(53, 220, 214)
    val play = Path().apply {
        moveTo(13f * density, 10f * density)
        lineTo(13f * density, 25f * density)
        lineTo(24f * density, 17.5f * density)
        close()
    }
    canvas.drawPath(play, paint)

    paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 13f * density
    val textCenterX = 49f * density
    val textCenterY = 17.5f * density
    val textBaseline = textCenterY - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(timeLabel, textCenterX, textBaseline, paint)

    return BitmapDrawable(mapView.resources, bitmap).apply {
        setBounds(0, 0, width, height)
    }
}

private fun formatDashcamRouteMarkerTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
