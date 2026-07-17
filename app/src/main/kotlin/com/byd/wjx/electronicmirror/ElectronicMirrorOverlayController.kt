package com.byd.wjx.electronicmirror

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

internal class ElectronicMirrorOverlayHandle(
    val windowManager: WindowManager,
    val container: FrameLayout,
    val surfaceView: SurfaceView,
    val ready: CompletableDeferred<Surface>,
    val label: TextView,
    val target: ElectronicMirrorDisplayTarget,
) {
    @Volatile var intentionalRemoval: Boolean = false
    @Volatile var surface: Surface? = null
}

/** Creates a non-touchable camera Surface window on either the cluster or centre display. */
internal class ElectronicMirrorOverlayController(
    context: Context,
    private val onUnexpectedSurfaceLoss: (ElectronicMirrorOverlayHandle) -> Unit,
) {
    private val appContext = context.applicationContext

    suspend fun show(scene: ElectronicMirrorScene): ElectronicMirrorOverlayHandle? {
        if (!Settings.canDrawOverlays(appContext)) {
            WjxFileLogger.w(TAG, "overlay permission missing")
            return null
        }
        val handle = withContext(Dispatchers.Main.immediate) { createWindow(scene) } ?: return null
        val surface = withTimeoutOrNull(SURFACE_TIMEOUT_MS) { handle.ready.await() }
        if (surface == null || !surface.isValid) {
            WjxFileLogger.w(TAG, "Surface not ready target=${scene.target}")
            hide(handle)
            return null
        }
        return handle
    }

    suspend fun hide(handle: ElectronicMirrorOverlayHandle?) {
        if (handle == null) return
        withContext(Dispatchers.Main.immediate) {
            handle.intentionalRemoval = true
            runCatching { handle.windowManager.removeView(handle.container) }
                .onFailure { WjxFileLogger.w(TAG, "remove overlay failed: ${it.message}") }
        }
    }

    suspend fun updateLabel(handle: ElectronicMirrorOverlayHandle, scene: ElectronicMirrorScene) {
        withContext(Dispatchers.Main.immediate) {
            handle.label.text = labelFor(scene)
        }
    }

    private fun createWindow(scene: ElectronicMirrorScene): ElectronicMirrorOverlayHandle? {
        val display = resolveDisplay(scene.target) ?: run {
            WjxFileLogger.w(TAG, "display unavailable target=${scene.target}")
            return null
        }
        val point = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(point)
        if (point.x <= 0 || point.y <= 0) return null

        val displayContext = appContext.createDisplayContext(display)
        val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val geometry = scene.geometry.sanitized()
        val windowWidth = (point.x * geometry.widthPct / 100f).roundToInt().coerceIn(160, point.x)
        val windowHeight = (point.y * geometry.heightPct / 100f).roundToInt().coerceIn(100, point.y)
        val windowX = ((point.x - windowWidth) * geometry.offsetXPct / 100f).roundToInt()
        val windowY = ((point.y - windowHeight) * geometry.offsetYPct / 100f).roundToInt()

        val container = FrameLayout(displayContext).apply {
            clipChildren = true
            clipToPadding = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            background = roundedDrawable(Color.BLACK, BORDER_RADIUS_DP, null)
            clipToOutline = true
            foreground = roundedDrawable(Color.TRANSPARENT, BORDER_RADIUS_DP, BORDER_COLOR)
        }
        val surfaceView = SurfaceView(displayContext)
        val cameraLayout = cameraSurfaceLayout(windowWidth, windowHeight)
        container.addView(
            surfaceView,
            FrameLayout.LayoutParams(cameraLayout.width, cameraLayout.height, Gravity.TOP or Gravity.START).apply {
                leftMargin = cameraLayout.x
                topMargin = cameraLayout.y
            },
        )
        surfaceView.holder.setFixedSize(SOURCE_WIDTH, SOURCE_HEIGHT)

        val label = TextView(displayContext).apply {
            text = labelFor(scene)
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 12), dp(displayContext, 5), dp(displayContext, 12), dp(displayContext, 5))
            background = roundedDrawable(0xB3121B22.toInt(), 14f, null)
        }
        container.addView(
            label,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(displayContext, 12)
                topMargin = dp(displayContext, 10)
            },
        )

        val live = TextView(displayContext).apply {
            text = "●  LIVE"
            setTextColor(0xFF63E6BE.toInt())
            textSize = 11f
            setPadding(dp(displayContext, 10), dp(displayContext, 5), dp(displayContext, 10), dp(displayContext, 5))
            background = roundedDrawable(0xB3121B22.toInt(), 14f, null)
        }
        container.addView(
            live,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dp(displayContext, 12)
                topMargin = dp(displayContext, 10)
            },
        )

        val ready = CompletableDeferred<Surface>()
        val handle = ElectronicMirrorOverlayHandle(windowManager, container, surfaceView, ready, label, scene.target)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                handle.surface = holder.surface
                if (!ready.isCompleted) ready.complete(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                handle.surface = null
                if (!handle.intentionalRemoval) onUnexpectedSurfaceLoss(handle)
            }
        })

        val params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowX
            y = windowY
            title = "SuperToyBox Electronic Mirror"
        }

        return runCatching {
            windowManager.addView(container, params)
            WjxFileLogger.i(
                TAG,
                "shown target=${scene.target} display=${display.displayId} ${windowWidth}x$windowHeight+$windowX,$windowY " +
                    "surface=${cameraLayout.width}x${cameraLayout.height}+${cameraLayout.x},${cameraLayout.y}",
            )
            handle
        }.onFailure {
            WjxFileLogger.e(TAG, "add overlay failed: ${it.message}", it)
        }.getOrNull()
    }

    private fun resolveDisplay(target: ElectronicMirrorDisplayTarget): Display? {
        val manager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return when (target) {
            ElectronicMirrorDisplayTarget.CENTER -> manager.getDisplay(Display.DEFAULT_DISPLAY)
            ElectronicMirrorDisplayTarget.CLUSTER -> {
                val candidates = manager.displays.filter {
                    it.name.contains("XDJAScreenProjection", ignoreCase = true)
                }
                candidates.firstOrNull { it.name.endsWith("_1") }
                    ?: candidates.firstOrNull()
                    ?: manager.getDisplay(DEFAULT_CLUSTER_DISPLAY_ID)
            }
        }
    }

    /**
     * L1Mini validated the APA source as 1280x720 and cropped its first 430 columns. We preserve
     * that source crop here, then crop-fill it into the independently adjustable user window.
     */
    private fun cameraSurfaceLayout(windowWidth: Int, windowHeight: Int): CameraLayout {
        val visibleSourceWidth = SOURCE_WIDTH - SOURCE_CROP_LEFT
        val scale = max(
            windowWidth.toFloat() / visibleSourceWidth,
            windowHeight.toFloat() / SOURCE_HEIGHT,
        )
        val width = (SOURCE_WIDTH * scale).roundToInt()
        val height = (SOURCE_HEIGHT * scale).roundToInt()
        val visibleWidth = (visibleSourceWidth * scale).roundToInt()
        val x = ((windowWidth - visibleWidth) / 2f - SOURCE_CROP_LEFT * scale).roundToInt()
        val y = ((windowHeight - height) / 2f).roundToInt()
        return CameraLayout(width, height, x, y)
    }

    private fun labelFor(scene: ElectronicMirrorScene): String = when (scene.reason) {
        ElectronicMirrorReason.REVERSE -> "倒挡 · 后方影像"
        ElectronicMirrorReason.LEFT_TURN -> "左转 · 左后侧影像"
        ElectronicMirrorReason.RIGHT_TURN -> "右转 · 右后侧影像"
        ElectronicMirrorReason.MANUAL_PREVIEW -> "预览 · ${scene.viewpoint.displayName}影像"
    }

    private fun roundedDrawable(color: Int, radiusDp: Float, strokeColor: Int?): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * appContext.resources.displayMetrics.density
            if (strokeColor != null) setStroke(dp(appContext, 2), strokeColor)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private data class CameraLayout(val width: Int, val height: Int, val x: Int, val y: Int)

    companion object {
        private const val TAG = "ElectronicMirrorOverlay"
        private const val DEFAULT_CLUSTER_DISPLAY_ID = 2
        private const val SOURCE_WIDTH = 1280
        private const val SOURCE_HEIGHT = 720
        private const val SOURCE_CROP_LEFT = 430
        private const val SURFACE_TIMEOUT_MS = 3_000L
        private const val BORDER_RADIUS_DP = 18f
        private const val BORDER_COLOR = 0xFF45D6B5.toInt()
        private const val WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }
}
