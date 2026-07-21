package com.byd.wjx.desktopcard

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun DesktopQuickCardIconImage(
    icon: DesktopQuickCardIcon,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val drawable = remember(icon.id, icon.drawableRes, icon.filePath) {
        loadDesktopQuickCardIcon(context, icon)
    }
    DisposableEffect(drawable) {
        drawable.startForever()
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.contentDescription = contentDescription
            }
        },
        update = { imageView ->
            imageView.contentDescription = contentDescription
            if (imageView.drawable !== drawable) imageView.setImageDrawable(drawable)
            drawable.startForever()
        },
    )
}

private fun Drawable?.startForever() {
    (this as? AnimatedImageDrawable)?.apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (!isRunning) start()
    }
}

private fun loadDesktopQuickCardIcon(
    context: android.content.Context,
    icon: DesktopQuickCardIcon,
): Drawable? {
    icon.drawableRes?.let { resource ->
        return ContextCompat.getDrawable(context, resource)
    }
    val file = icon.filePath?.let(::File)?.takeIf(File::isFile)
        ?: return ContextCompat.getDrawable(context, DesktopQuickCardIconCatalog.default.drawableRes!!)
    return runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longestSide = maxOf(info.size.width, info.size.height)
            if (longestSide > MAX_DECODE_SIZE_PX) {
                val scale = MAX_DECODE_SIZE_PX.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrElse {
        ContextCompat.getDrawable(context, DesktopQuickCardIconCatalog.default.drawableRes!!)
    }
}

private const val MAX_DECODE_SIZE_PX = 512
