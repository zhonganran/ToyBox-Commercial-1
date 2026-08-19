package com.byd.wjx.lyrics

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.roundToInt

class ClusterLyricOverlayController(
    private val context: Context,
    private val settings: LyricSettings,
) {
    private val appContext = context.applicationContext
    private var displayContext: Context? = null
    private var wm: WindowManager? = null
    private var container: FrameLayout? = null
    private var lyricView: LyricOverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var displayId: Int = -1
    private var lastDisplayCheckAt = 0L
    private var clusterWidth = 1280
    private var clusterHeight = 480

    fun update(frame: LyricFrame, animate: Boolean = true) {
        ensureDisplayStillValid()
        if (container == null) show()
        lyricView?.apply {
            setTextSizeSp(18f * settings.clusterFontScale)
            setVisibleLineCount(settings.clusterLineCount)
            setDisplayStyle(settings.clusterDisplayStyle())
            setHorizontalPositionPct(settings.clusterXPct.toFloat())
            setFrame(frame, animate)
        }
        val lp = params ?: return
        val newWidth = windowWidth()
        val newHeight = windowHeight()
        val oldX = lp.x
        val oldY = lp.y
        var changed = false
        if (lp.width != newWidth || lp.height != newHeight || lp.alpha != settings.clusterAlpha) {
            lp.width = newWidth
            lp.height = newHeight
            lp.alpha = settings.clusterAlpha
            changed = true
        }
        positionByPercent(lp)
        if (changed || lp.x != oldX || lp.y != oldY) {
            runCatching { wm?.updateViewLayout(container, lp) }
        }
    }

    fun hide() {
        val view = container ?: return
        runCatching { wm?.removeView(view) }
            .onFailure { WjxFileLogger.w(TAG, "remove failed: ${it.message}") }
        container = null
        lyricView = null
        params = null
        wm = null
        displayContext = null
        displayId = -1
    }

    fun refreshPosition() {
        hide()
    }

    private fun show() {
        val display = resolveClusterDisplay() ?: run {
            WjxFileLogger.w(TAG, "cluster display not found")
            return
        }
        val ctx = appContext.createDisplayContext(display)
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayContext = ctx
        wm = windowManager
        val view = FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val lyric = LyricOverlayView(ctx)
        lyric.setHorizontalPositionPct(settings.clusterXPct.toFloat())
        view.addView(
            lyric,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER),
        )

        val lp = WindowManager.LayoutParams(
            windowWidth(),
            windowHeight(),
            overlayType(),
            CLUSTER_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            alpha = settings.clusterAlpha
            positionByPercent(this)
        }
        runCatching {
            windowManager.addView(view, lp)
            container = view
            lyricView = lyric
            params = lp
            displayId = display.displayId
            WjxFileLogger.i(TAG, "cluster lyric overlay shown display=${display.displayId} ${clusterWidth}x$clusterHeight")
        }.onFailure {
            WjxFileLogger.w(TAG, "show failed: ${it.message}", it)
            wm = null
            displayContext = null
            displayId = -1
        }
    }

    private fun ensureDisplayStillValid() {
        if (container == null || displayId < 0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDisplayCheckAt < DISPLAY_CHECK_INTERVAL_MS) return
        lastDisplayCheckAt = now
        val current = resolveClusterDisplay()
        if (current == null || current.displayId != displayId) {
            WjxFileLogger.w(TAG, "cluster display changed old=$displayId new=${current?.displayId}; rebuilding")
            hide()
        }
    }

    private fun positionByPercent(lp: WindowManager.LayoutParams) {
        val maxX = (clusterWidth - lp.width).coerceAtLeast(0)
        val maxY = (clusterHeight - lp.height).coerceAtLeast(0)
        lp.x = (maxX * settings.clusterXPct / 100f).roundToInt()
        lp.y = (maxY * settings.clusterYPct / 100f).roundToInt()
        clamp(lp)
    }

    private fun clamp(lp: WindowManager.LayoutParams) {
        lp.x = lp.x.coerceIn(0, (clusterWidth - lp.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (clusterHeight - lp.height).coerceAtLeast(0))
    }

    private fun windowWidth(): Int =
        (density() * 420f * settings.clusterFontScale)
            .roundToInt()
            .coerceIn(120, clusterWidth)

    private fun windowHeight(): Int =
        LyricOverlayView.desiredHeightPx(
            textSizeSp = 18f * settings.clusterFontScale,
            style = settings.clusterDisplayStyle(),
            density = density(),
            scaledDensity = scaledDensity(),
            lineCount = settings.clusterLineCount,
        ).coerceIn(64, clusterHeight)

    private fun density(): Float =
        displayContext?.resources?.displayMetrics?.density ?: appContext.resources.displayMetrics.density

    @Suppress("DEPRECATION")
    private fun scaledDensity(): Float =
        displayContext?.resources?.displayMetrics?.scaledDensity ?: appContext.resources.displayMetrics.scaledDensity

    private fun resolveClusterDisplay(): Display? {
        val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val projectionDisplays = dm.displays.filter { it.name.contains("XDJAScreenProjection", ignoreCase = true) }
        val display = projectionDisplays.firstOrNull { it.name.endsWith("_1") }
            ?: projectionDisplays.firstOrNull()
            ?: dm.getDisplay(DEFAULT_CLUSTER_DISPLAY_ID)
        if (display != null) {
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            if (point.x > 0 && point.y > 0) {
                clusterWidth = point.x
                clusterHeight = point.y
            }
        }
        return display
    }

    private fun overlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    companion object {
        private const val TAG = "ClusterLyricOverlay"
        private const val DEFAULT_CLUSTER_DISPLAY_ID = 2
        private const val DISPLAY_CHECK_INTERVAL_MS = 2_000L
        private const val CLUSTER_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
