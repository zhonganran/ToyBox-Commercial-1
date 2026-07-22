package com.byd.wjx.desktopcard

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.compose.runtime.Composable
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

private class RestartableAnimatedImageView(context: android.content.Context) : ImageView(context) {
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
private const val ANIMATION_WATCHDOG_INTERVAL_MS = 1_500L
