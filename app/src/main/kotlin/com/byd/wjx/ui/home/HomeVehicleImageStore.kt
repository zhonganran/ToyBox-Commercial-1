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
    return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
}

internal fun saveVehicleImage(context: Context, uri: Uri): Boolean = runCatching {
    val decoded = decodeUriSampled(context, uri, 1600) ?: return@runCatching false
    val normalized = normalizeVehicleBitmap(decoded, 1600)
    val file = vehicleImageFile(context)
    file.parentFile?.mkdirs()
    file.outputStream().use { output ->
        normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    if (normalized !== decoded) decoded.recycle()
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
