package com.byd.wjx.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal fun loadVehicleImageBitmap(context: Context): ImageBitmap? {
    val file = vehicleImageFile(context)
    if (!file.isFile) return null
    return runCatching {
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        val trimmed = trimTransparentEdges(decoded)
        if (trimmed !== decoded) decoded.recycle()
        trimmed.asImageBitmap()
    }.getOrNull()
}

internal fun saveVehicleImage(context: Context, uri: Uri): Boolean = runCatching {
    val decoded = decodeUriSampled(context, uri, 1600) ?: return@runCatching false
    val trimmed = trimTransparentEdges(decoded)
    val normalized = normalizeVehicleBitmap(trimmed, 1600)
    val file = vehicleImageFile(context)
    file.parentFile?.mkdirs()
    file.outputStream().use { output ->
        normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    if (normalized !== trimmed) trimmed.recycle()
    if (trimmed !== decoded) decoded.recycle()
    normalized.recycle()
    true
}.getOrDefault(false)

internal fun deleteVehicleImage(context: Context): Boolean = runCatching {
    val file = vehicleImageFile(context)
    file.isFile && file.delete()
}.getOrDefault(false)

private fun vehicleImageFile(context: Context): File =
    File(context.filesDir, "ui/vehicle/home_vehicle.png")

private fun decodeUriSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun normalizeVehicleBitmap(source: Bitmap, maxSide: Int): Bitmap {
    val largest = max(source.width, source.height)
    if (largest <= maxSide) return source
    val scale = maxSide.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).roundToInt().coerceAtLeast(1),
        (source.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

private fun trimTransparentEdges(source: Bitmap): Bitmap {
    if (!source.hasAlpha() || source.width < 4 || source.height < 4) return source
    var left = source.width
    var top = source.height
    var right = -1
    var bottom = -1
    val row = IntArray(source.width)
    repeat(source.height) { y ->
        source.getPixels(row, 0, source.width, 0, y, source.width, 1)
        row.forEachIndexed { x, color ->
            if ((color ushr 24) > MIN_VISIBLE_ALPHA) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < left || bottom < top) return source

    val margin = (max(source.width, source.height) * CROP_MARGIN_RATIO).roundToInt().coerceAtLeast(4)
    left = (left - margin).coerceAtLeast(0)
    top = (top - margin).coerceAtLeast(0)
    right = (right + margin).coerceAtMost(source.width - 1)
    bottom = (bottom + margin).coerceAtMost(source.height - 1)
    val width = right - left + 1
    val height = bottom - top + 1
    if (width >= source.width * CROP_SKIP_RATIO && height >= source.height * CROP_SKIP_RATIO) return source
    return Bitmap.createBitmap(source, left, top, width, height)
}

private const val MIN_VISIBLE_ALPHA = 8
private const val CROP_MARGIN_RATIO = 0.025f
private const val CROP_SKIP_RATIO = 0.96f
