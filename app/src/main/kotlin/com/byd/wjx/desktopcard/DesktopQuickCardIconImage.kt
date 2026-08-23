package com.byd.wjx.desktopcard

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopQuickCardIconImage(
    icon: DesktopQuickCardIcon,
    requestedSizeDp: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val requestedSizePx = with(density) { requestedSizeDp.dp.roundToPx() }
    val decodeTargetPx = decodeTargetSizePx(requestedSizePx)
    var drawable by remember(icon.id, icon.drawableRes, icon.filePath) {
        mutableStateOf<Drawable?>(null)
    }
    var decodedSizePx by remember(icon.id, icon.drawableRes, icon.filePath) {
        mutableIntStateOf(0)
    }
    LaunchedEffect(icon.id, icon.drawableRes, icon.filePath, decodeTargetPx) {
        if (drawable != null && decodedSizePx >= decodeTargetPx) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            loadDesktopQuickCardIcon(context.applicationContext, icon, decodeTargetPx)
        }
        drawable = loaded
        decodedSizePx = decodeTargetPx
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            RestartableAnimatedImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.contentDescription = contentDescription
            }
        },
        update = { imageView ->
            imageView.contentDescription = contentDescription
            imageView.setIconDrawable(drawable)
        },
    )
}

private class RestartableAnimatedImageView(context: android.content.Context) : AppCompatImageView(context) {
    private val animationWatchdog = object : Runnable {
        override fun run() {
            ensureAnimationRunning()
            if (isAttachedToWindow) postDelayed(this, ANIMATION_WATCHDOG_INTERVAL_MS)
        }
    }

    fun setIconDrawable(value: Drawable?) {
        if (drawable !== value) {
            (drawable as? AnimatedImageDrawable)?.stop()
            setImageDrawable(value)
        }
        ensureAnimationRunning()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(animationWatchdog)
        ensureAnimationRunning()
        postDelayed(animationWatchdog, ANIMATION_WATCHDOG_INTERVAL_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animationWatchdog)
        (drawable as? AnimatedImageDrawable)?.stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) post(::ensureAnimationRunning)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) post(::ensureAnimationRunning)
    }

    private fun ensureAnimationRunning() {
        val animated = drawable as? AnimatedImageDrawable ?: return
        animated.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (isAttachedToWindow && windowVisibility == View.VISIBLE && visibility == View.VISIBLE) {
            if (!animated.isRunning) animated.start()
            invalidate()
        }
    }
}

private fun loadDesktopQuickCardIcon(
    context: android.content.Context,
    icon: DesktopQuickCardIcon,
    requestedSizePx: Int,
): Drawable? {
    icon.drawableRes?.let { resource ->
        return runCatching {
            decodeDesktopQuickCardDrawable(
                source = ImageDecoder.createSource(context.resources, resource),
                requestedSizePx = requestedSizePx,
            )
        }.getOrElse { ContextCompat.getDrawable(context, resource) }
    }
    val file = icon.filePath?.let(::File)?.takeIf(File::isFile)
        ?: return ContextCompat.getDrawable(context, DesktopQuickCardIconCatalog.default.drawableRes!!)
    return runCatching {
        decodeDesktopQuickCardDrawable(
            source = ImageDecoder.createSource(file),
            requestedSizePx = requestedSizePx,
        )
    }.getOrElse {
        ContextCompat.getDrawable(context, DesktopQuickCardIconCatalog.default.drawableRes!!)
    }
}

private fun decodeDesktopQuickCardDrawable(
    source: ImageDecoder.Source,
    requestedSizePx: Int,
): Drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
    val longestSide = maxOf(info.size.width, info.size.height)
    val targetLongestSide = requestedSizePx.coerceIn(
        MIN_DECODE_SIZE_PX,
        MAX_DECODE_SIZE_PX,
    )
    if (longestSide > targetLongestSide) {
        val scale = targetLongestSide.toFloat() / longestSide
        decoder.setTargetSize(
            (info.size.width * scale).roundToInt().coerceAtLeast(1),
            (info.size.height * scale).roundToInt().coerceAtLeast(1),
        )
    }
}

private fun decodeTargetSizePx(requestedSizePx: Int): Int = when {
    requestedSizePx <= 128 -> 128
    requestedSizePx <= 256 -> 256
    requestedSizePx <= 512 -> 512
    requestedSizePx <= 1_024 -> 1_024
    else -> MAX_DECODE_SIZE_PX
}

private const val MIN_DECODE_SIZE_PX = 128
private const val MAX_DECODE_SIZE_PX = 2_048
private const val ANIMATION_WATCHDOG_INTERVAL_MS = 1_500L
