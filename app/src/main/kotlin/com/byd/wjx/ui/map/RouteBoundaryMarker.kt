package com.byd.wjx.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

enum class RouteBoundaryKind(
    val label: String,
    val color: Int
) {
    Start("起", Color.rgb(35, 169, 98)),
    End("终", Color.rgb(244, 107, 69))
}

fun routeBoundaryMarker(
    mapView: MapView,
    position: GeoPoint,
    title: String,
    kind: RouteBoundaryKind
): Marker = Marker(mapView).apply {
    this.position = position
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    this.title = title
    icon = routeBoundaryDrawable(mapView, kind)
    infoWindow = null
}

private fun routeBoundaryDrawable(
    mapView: MapView,
    kind: RouteBoundaryKind
): BitmapDrawable {
    val density = mapView.resources.displayMetrics.density.coerceAtLeast(1f)
    val width = (44f * density).roundToInt()
    val height = (52f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val cx = width / 2f
    val cy = 18f * density
    val radius = 15f * density

    paint.style = Paint.Style.FILL
    paint.color = Color.argb(235, 255, 255, 255)
    canvas.drawCircle(cx, cy, radius + 4f * density, paint)

    paint.color = kind.color
    canvas.drawCircle(cx, cy, radius, paint)

    val pointer = Path().apply {
        moveTo(cx - 6f * density, cy + radius - 2f * density)
        lineTo(cx + 6f * density, cy + radius - 2f * density)
        lineTo(cx, height - 3f * density)
        close()
    }
    canvas.drawPath(pointer, paint)

    paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 16f * density
    val textY = cy - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(kind.label, cx, textY, paint)

    return BitmapDrawable(mapView.resources, bitmap).apply {
        setBounds(0, 0, width, height)
    }
}
