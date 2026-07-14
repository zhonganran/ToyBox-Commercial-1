package com.byd.wjx.lyrics

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.hypot
import kotlin.math.roundToInt

class DesktopLyricOverlayController(
    private val context: Context,
    private val settings: LyricSettings,
) {
    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var container: FrameLayout? = null
    private var lyricView: LyricOverlayView? = null
    private var lyricParams: WindowManager.LayoutParams? = null
    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0
    private var startY = 0
    private var lastDesktopPositionInitialized = settings.desktopPositionInitialized
    private var dragging = false
    private var longPressTriggered = false
    private var longPressRunnable: Runnable? = null
    private var visible = false

    fun update(frame: LyricFrame, animate: Boolean = true) {
        if (container == null) show()
        showSoft()
        lyricView?.apply {
            setTextSizeSp(18f * settings.fontScale)
            setDisplayStyle(settings.displayStyle())
            if (!dragging) setFrame(frame, animate)
        }
        val lp = lyricParams ?: return
        val newWidth = windowWidth()
        if (lp.width != newWidth) {
            lp.width = newWidth
            runCatching { wm.updateViewLayout(container, lp) }
        }
        if (lastDesktopPositionInitialized && !settings.desktopPositionInitialized) {
            applyDefaultPosition(lp)
            runCatching { wm.updateViewLayout(container, lp) }
        }
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

    private fun hideHard() {
        cancelLongPress()
        container?.let { runCatching { wm.removeView(it) } }
        container = null
        lyricView = null
        lyricParams = null
        visible = false
    }

    fun refreshPosition() {
        hide()
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
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        val lp = WindowManager.LayoutParams(
            windowWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            DESKTOP_LYRIC_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (settings.desktopPositionInitialized) {
                gravity = Gravity.START or Gravity.TOP
                x = settings.desktopX
                y = settings.desktopY
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
            WjxFileLogger.i(TAG, "desktop lyric overlay shown directDrag=true")
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

    private fun onLyricTouch(v: View, event: MotionEvent): Boolean {
        val lp = lyricParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ensureTopLeftGravity(lp)
                dragging = false
                longPressTriggered = false
                startRawX = event.rawX
                startRawY = event.rawY
                startX = lp.x
                startY = lp.y
                scheduleLongPress()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressTriggered) return true
                val dx = event.rawX - startRawX
                val dy = event.rawY - startRawY
                if (!dragging && hypot(dx, dy) <= dp(DRAG_SLOP_DP)) return true
                cancelLongPress()
                dragging = true
                lp.x = startX + dx.roundToInt()
                lp.y = startY + dy.roundToInt()
                clampToScreen(lp)
                runCatching { wm.updateViewLayout(container, lp) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (longPressTriggered) {
                    longPressTriggered = false
                    dragging = false
                    return true
                }
                clampToScreen(lp)
                settings.desktopX = lp.x
                settings.desktopY = lp.y
                settings.desktopPositionInitialized = true
                dragging = false
                return true
            }
        }
        return false
    }

    private fun closeDesktopLyricsByGesture(reason: String) {
        cancelLongPress()
        settings.desktopEnabled = false
        hideHard()
        WjxFileLogger.i(TAG, "desktop lyric closed by $reason")
    }

    private fun scheduleLongPress() {
        cancelLongPress()
        longPressRunnable = Runnable {
            longPressTriggered = true
            dragging = false
            closeDesktopLyricsByGesture("long_press_lyric")
        }.also {
            longPressHandler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    private fun cancelLongPress() {
        longPressRunnable?.let(longPressHandler::removeCallbacks)
        longPressRunnable = null
    }

    private fun applyDefaultPosition(lp: WindowManager.LayoutParams) {
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.x = 0
        lp.y = dp(92f)
    }

    private fun ensureTopLeftGravity(lp: WindowManager.LayoutParams) {
        if (lp.gravity == (Gravity.START or Gravity.TOP)) return
        val view = container ?: return
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        lp.gravity = Gravity.START or Gravity.TOP
        lp.x = location[0]
        lp.y = location[1]
        clampToScreen(lp)
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun clampToScreen(lp: WindowManager.LayoutParams) {
        val metrics = appContext.resources.displayMetrics
        val width = container?.width?.takeIf { it > 0 } ?: lp.width
        val height = container?.height?.takeIf { it > 0 } ?: dp(140f)
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
    }

    private fun windowWidth(): Int =
        (appContext.resources.displayMetrics.density * 420f * settings.fontScale)
            .roundToInt()
            .coerceAtLeast(dp(280f))

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Float): Int = (value * appContext.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "DesktopLyricOverlay"
        private const val DRAG_SLOP_DP = 8f
        private const val DESKTOP_LYRIC_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
