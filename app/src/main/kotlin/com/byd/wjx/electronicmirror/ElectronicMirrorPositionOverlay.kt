package com.byd.wjx.electronicmirror

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.roundToInt

/** Draggable stand-in used to select the electronic rearview mirror PiP position. */
class ElectronicMirrorPositionOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable(::hide)
    private val retryHide = Runnable(::hide)

    private var context: Context? = null
    private var windowManager: WindowManager? = null
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var grabX = 0f
    private var grabY = 0f
    private var removeAttempts = 0

    var onHidden: (() -> Unit)? = null
    val isShown: Boolean get() = view != null

    fun show(context: Context): Boolean {
        if (view != null) return true
        val metrics = realMetrics(context)
        val rect = placement(context, metrics)
        val layout = WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }
        val frame = buildView(context, metrics)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return try {
            wm.addView(frame, layout)
            this.context = context
            windowManager = wm
            view = frame
            params = layout
            removeAttempts = 0
            handler.postDelayed(autoHide, AUTO_HIDE_MS)
            true
        } catch (error: Throwable) {
            WjxFileLogger.w(TAG, "position overlay failed: ${error.javaClass.simpleName}:${error.message}")
            false
        }
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        handler.removeCallbacks(retryHide)
        val frame = view ?: return
        frame.setOnTouchListener(null)
        val error = runCatching { windowManager?.removeView(frame) }.exceptionOrNull()
        if (error != null && error !is IllegalArgumentException) {
            removeAttempts++
            if (removeAttempts < MAX_REMOVE_ATTEMPTS) {
                handler.postDelayed(retryHide, REMOVE_RETRY_MS)
                return
            }
        }
        removeAttempts = 0
        view = null
        params = null
        windowManager = null
        context = null
        onHidden?.invoke()
    }

    fun refreshSize() {
        val currentContext = context ?: return
        val frame = view ?: return
        val layout = params ?: return
        val metrics = realMetrics(currentContext)
        val size = ElectronicMirrorPreferences.pipSize(metrics.widthPixels, prefs(currentContext).widthPct())
        layout.width = size.width
        layout.height = size.height
        layout.x = layout.x.coerceIn(
            0,
            (metrics.widthPixels - size.width).coerceAtLeast(0),
        )
        layout.y = layout.y.coerceIn(0, (metrics.heightPixels - size.height).coerceAtLeast(0))
        runCatching { windowManager?.updateViewLayout(frame, layout) }
    }

    private fun buildView(context: Context, metrics: DisplayMetrics): View {
        val hint = TextView(context).apply {
            text = "拖动调整电子后视镜位置"
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val border = View(context).apply {
            background = GradientDrawable().apply {
                setStroke(
                    (BORDER_DP * context.resources.displayMetrics.density).roundToInt(),
                    BORDER_COLOR,
                )
                setColor(Color.TRANSPARENT)
            }
        }
        return FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                hint,
                FrameLayout.LayoutParams(-2, -2, Gravity.CENTER),
            )
            addView(border, FrameLayout.LayoutParams(-1, -1))
            setOnTouchListener { _, event -> onTouch(event, metrics) }
        }
    }

    private fun onTouch(event: MotionEvent, metrics: DisplayMetrics): Boolean {
        val frame = view ?: return false
        val layout = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                grabX = event.rawX - layout.x
                grabY = event.rawY - layout.y
                handler.removeCallbacks(autoHide)
            }
            MotionEvent.ACTION_MOVE -> {
                layout.x = (event.rawX - grabX).roundToInt()
                    .coerceIn(
                        0,
                        (metrics.widthPixels - layout.width).coerceAtLeast(0),
                    )
                layout.y = (event.rawY - grabY).roundToInt()
                    .coerceIn(0, (metrics.heightPixels - layout.height).coerceAtLeast(0))
                runCatching { windowManager?.updateViewLayout(frame, layout) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                context?.let {
                    prefs(it).edit()
                        .putInt(ElectronicMirrorPreferences.KEY_PIP_X_PX, layout.x)
                        .putInt(ElectronicMirrorPreferences.KEY_PIP_Y_PX, layout.y)
                        .apply()
                }
                handler.postDelayed(autoHide, AUTO_HIDE_MS)
            }
            else -> return false
        }
        return true
    }

    private fun placement(context: Context, metrics: DisplayMetrics): Rect {
        val prefs = prefs(context)
        val widthPct = prefs.widthPct()
        val x = prefs.getInt(ElectronicMirrorPreferences.KEY_PIP_X_PX, ElectronicMirrorPreferences.UNSET_PX)
        val y = prefs.getInt(ElectronicMirrorPreferences.KEY_PIP_Y_PX, ElectronicMirrorPreferences.UNSET_PX)
        if (x == ElectronicMirrorPreferences.UNSET_PX || y == ElectronicMirrorPreferences.UNSET_PX) {
            return ElectronicMirrorPreferences.defaultPipRect(metrics.widthPixels, metrics.heightPixels, widthPct)
        }
        val size = ElectronicMirrorPreferences.pipSize(metrics.widthPixels, widthPct)
        val left = x.coerceIn(
            0,
            (metrics.widthPixels - size.width).coerceAtLeast(0),
        )
        val top = y.coerceIn(0, (metrics.heightPixels - size.height).coerceAtLeast(0))
        return Rect(left, top, left + size.width, top + size.height)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(ElectronicMirrorPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    private fun SharedPreferences.widthPct(): Int = getInt(
        ElectronicMirrorPreferences.KEY_PIP_WIDTH_PCT,
        ElectronicMirrorPreferences.DEFAULT_PIP_WIDTH_PCT,
    ).coerceIn(
        ElectronicMirrorPreferences.MIN_PIP_WIDTH_PCT,
        ElectronicMirrorPreferences.MAX_PIP_WIDTH_PCT,
    )

    private fun realMetrics(context: Context): DisplayMetrics {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return context.resources.displayMetrics
        @Suppress("DEPRECATION")
        return DisplayMetrics().also { display.getRealMetrics(it) }
    }

    private companion object {
        const val TAG = "ElectronicMirror"
        const val AUTO_HIDE_MS = 60_000L
        const val REMOVE_RETRY_MS = 1_000L
        const val MAX_REMOVE_ATTEMPTS = 5
        const val BORDER_DP = 12f
        const val BORDER_COLOR = 0xFFFF7A00.toInt()
    }
}
