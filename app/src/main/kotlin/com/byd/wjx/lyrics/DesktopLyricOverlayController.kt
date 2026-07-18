package com.byd.wjx.lyrics

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.roundToInt

class DesktopLyricOverlayController(
    private val context: Context,
    private val settings: LyricSettings,
) {
    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dragSlop = ViewConfiguration.get(appContext).scaledTouchSlop.toFloat()

    private var container: FrameLayout? = null
    private var lyricView: LyricOverlayView? = null
    private var lyricParams: WindowManager.LayoutParams? = null
    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var lastDesktopPositionInitialized = settings.desktopPositionInitialized
    private var visible = false

    fun update(frame: LyricFrame, animate: Boolean = true) {
        if (container == null) show()
        showSoft()
        lyricView?.apply {
            setTextSizeSp(DESKTOP_TEXT_SIZE_SP * settings.sharedFontScale)
            setVisibleLineCount(settings.sharedLineCount)
            setDisplayStyle(settings.displayStyle())
            setFrame(frame, animate)
        }
        val lp = lyricParams ?: return
        var changed = applyWindowInputConfig(lp)
        val newWidth = windowWidth()
        val newHeight = windowHeight()
        if (lp.width != newWidth || lp.height != newHeight) {
            lp.x += (lp.width - newWidth) / 2
            lp.y += (lp.height - newHeight) / 2
            lp.width = newWidth
            lp.height = newHeight
            changed = true
        }
        if (lastDesktopPositionInitialized && !settings.desktopPositionInitialized) {
            applyDefaultPosition(lp)
            changed = true
        } else {
            val oldX = lp.x
            val oldY = lp.y
            clampToScreenEdges(lp)
            changed = changed || lp.x != oldX || lp.y != oldY
        }
        if (changed) updateViewLayout(lp)
        lastDesktopPositionInitialized = settings.desktopPositionInitialized
    }

    fun hide() {
        hideHard()
    }

    fun hideSoft() {
        val lyricContainer = container ?: return
        lyricContainer.visibility = View.GONE
        visible = false
    }

    fun refreshPosition() {
        hide()
    }

    private fun hideHard() {
        container?.let { runCatching { wm.removeView(it) } }
        container = null
        lyricView = null
        lyricParams = null
        dragging = false
        visible = false
    }

    private fun show() {
        val view = FrameLayout(appContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnTouchListener(::onLyricTouch)
        }
        val lyric = LyricOverlayView(appContext).apply {
            setOnTouchListener(::onLyricTouch)
        }
        view.addView(
            lyric,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        val lp = WindowManager.LayoutParams(
            windowWidth(),
            windowHeight(),
            overlayType(),
            windowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            alpha = windowAlpha()
            if (settings.desktopPositionInitialized) {
                x = settings.desktopX
                y = settings.desktopY
                clampToScreenEdges(this)
            } else {
                applyDefaultPosition(this)
            }
        }
        runCatching {
            wm.addView(view, lp)
            container = view
            lyricView = lyric
            lyricParams = lp
            visible = true
            lastDesktopPositionInitialized = settings.desktopPositionInitialized
            WjxFileLogger.i(TAG, "desktop lyric overlay shown touchThrough=${settings.desktopTouchThrough}")
        }.onFailure {
            WjxFileLogger.w(TAG, "show failed: ${it.message}", it)
            container = null
            lyricView = null
            lyricParams = null
        }
    }

    private fun showSoft() {
        val lyricContainer = container ?: return
        if (!visible) {
            lyricContainer.visibility = View.VISIBLE
            visible = true
        }
    }

    private fun onLyricTouch(view: View, event: MotionEvent): Boolean {
        if (settings.desktopTouchThrough) return false
        val lp = lyricParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                startRawX = event.rawX
                startRawY = event.rawY
                startX = lp.x
                startY = lp.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> dragging = false

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (dragging || kotlin.math.hypot(dx, dy) > dragSlop) {
                        dragging = true
                        lp.x = startX + dx.roundToInt()
                        lp.y = startY + dy.roundToInt()
                        clampToScreenEdges(lp)
                        updateViewLayout(lp)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) savePosition()
                dragging = false
            }
        }
        view.parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun savePosition() {
        val lp = lyricParams ?: return
        clampToScreenEdges(lp)
        settings.desktopX = lp.x
        settings.desktopY = lp.y
        settings.desktopPositionInitialized = true
        lastDesktopPositionInitialized = true
    }

    private fun applyDefaultPosition(lp: WindowManager.LayoutParams) {
        val (screenWidth, screenHeight) = screenSize()
        lp.gravity = Gravity.START or Gravity.TOP
        lp.x = (screenWidth - lp.width) / 2
        lp.y = screenHeight - lp.height - dp(DEFAULT_BOTTOM_MARGIN_DP)
        clampToScreenEdges(lp)
    }

    /** Allows the lyric center to reach every screen edge while keeping half of the overlay visible. */
    private fun clampToScreenEdges(lp: WindowManager.LayoutParams) {
        val (screenWidth, screenHeight) = screenSize()
        val halfWidth = lp.width.coerceAtLeast(1) / 2
        val halfHeight = lp.height.coerceAtLeast(1) / 2
        lp.x = lp.x.coerceIn(-halfWidth, screenWidth - halfWidth)
        lp.y = lp.y.coerceIn(-halfHeight, screenHeight - halfHeight)
    }

    private fun applyWindowInputConfig(lp: WindowManager.LayoutParams): Boolean {
        val flags = windowFlags()
        val alpha = windowAlpha()
        var changed = false
        if (lp.flags != flags) {
            lp.flags = flags
            changed = true
        }
        if (lp.alpha != alpha) {
            lp.alpha = alpha
            changed = true
        }
        return changed
    }

    private fun windowFlags(): Int = BASE_FLAGS or if (settings.desktopTouchThrough) {
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    } else {
        0
    }

    /** Android 12+ only forwards touches through an app overlay below the obscuring-opacity limit. */
    private fun windowAlpha(): Float = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.desktopTouchThrough
    ) {
        MAX_TOUCH_THROUGH_ALPHA
    } else {
        1f
    }

    private fun windowWidth(): Int =
        (appContext.resources.displayMetrics.density * WINDOW_WIDTH_DP * settings.sharedFontScale)
            .roundToInt()
            .coerceAtLeast(dp(MIN_WINDOW_WIDTH_DP))

    @Suppress("DEPRECATION")
    private fun windowHeight(): Int = LyricOverlayView.desiredHeightPx(
        textSizeSp = DESKTOP_TEXT_SIZE_SP * settings.sharedFontScale,
        style = settings.displayStyle(),
        density = appContext.resources.displayMetrics.density,
        scaledDensity = appContext.resources.displayMetrics.scaledDensity,
        lineCount = settings.sharedLineCount,
    )

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        @Suppress("DEPRECATION")
        val metrics = appContext.resources.displayMetrics
        metrics.widthPixels to metrics.heightPixels
    }

    private fun updateViewLayout(lp: WindowManager.LayoutParams) {
        runCatching { wm.updateViewLayout(container, lp) }
            .onFailure { WjxFileLogger.w(TAG, "update layout failed: ${it.message}") }
    }

    private fun overlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun dp(value: Float): Int = (value * appContext.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "DesktopLyricOverlay"
        private const val DESKTOP_TEXT_SIZE_SP = 18f
        private const val WINDOW_WIDTH_DP = 420f
        private const val MIN_WINDOW_WIDTH_DP = 280f
        private const val DEFAULT_BOTTOM_MARGIN_DP = 92f
        private const val MAX_TOUCH_THROUGH_ALPHA = 0.8f
        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
