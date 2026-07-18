package com.byd.wjx.electronicmirror

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.wjx.log.WjxFileLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

internal class ElectronicMirrorOverlayHandle(
    val windowManager: WindowManager,
    val container: FrameLayout,
    val textureView: TextureView,
    val ready: CompletableDeferred<Surface>,
    val firstFrame: CompletableDeferred<Unit>,
    val label: TextView,
    val target: ElectronicMirrorDisplayTarget,
    val layoutParams: WindowManager.LayoutParams,
    initialCalibration: ElectronicMirrorCalibration,
) {
    @Volatile var intentionalRemoval: Boolean = false
    @Volatile var surface: Surface? = null
    val frameCount = AtomicLong(0L)
    @Volatile var lastFrameAtMs: Long = 0L
    @Volatile var calibration: ElectronicMirrorCalibration = initialCalibration
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
            handle.surface?.release()
            handle.surface = null
        }
    }

    suspend fun reveal(handle: ElectronicMirrorOverlayHandle) {
        withContext(Dispatchers.Main.immediate) {
            if (!handle.intentionalRemoval && handle.surface?.isValid == true) {
                // Do not animate this alpha. A direct reveal after a real producer frame avoids a
                // black/transparent flash on the instrument cluster compositor.
                handle.layoutParams.alpha = 1f
                handle.windowManager.updateViewLayout(handle.container, handle.layoutParams)
            }
        }
    }

    suspend fun updateLabel(handle: ElectronicMirrorOverlayHandle, scene: ElectronicMirrorScene) {
        withContext(Dispatchers.Main.immediate) {
            handle.label.text = labelFor(scene)
        }
    }

    suspend fun updateCalibration(
        handle: ElectronicMirrorOverlayHandle,
        calibration: ElectronicMirrorCalibration,
    ) {
        withContext(Dispatchers.Main.immediate) {
            handle.calibration = calibration.sanitized()
            applyCalibration(handle)
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
        val textureView = TextureView(displayContext).apply {
            isOpaque = true
        }
        container.addView(
            textureView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

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

        val params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WINDOW_FLAGS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowX
            y = windowY
            title = "SuperToyBox Electronic Mirror"
            // The overlay must never expose the cluster/centre display below it. Keep a solid
            // black surface while the camera producer starts, then TextureView replaces it with
            // the first real frame without changing window opacity.
            alpha = 1f
        }

        val ready = CompletableDeferred<Surface>()
        val firstFrame = CompletableDeferred<Unit>()
        val handle = ElectronicMirrorOverlayHandle(
            windowManager,
            container,
            textureView,
            ready,
            firstFrame,
            label,
            scene.target,
            params,
            scene.calibration.sanitized(),
        )
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                applyCalibration(handle, width, height)
                val surface = Surface(texture)
                handle.surface = surface
                if (!ready.isCompleted) ready.complete(surface)
                WjxFileLogger.i(TAG, "Surface available target=${scene.target} size=${width}x$height")
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                applyCalibration(handle, width, height)
                WjxFileLogger.i(TAG, "Surface resized target=${scene.target} size=${width}x$height")
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                handle.frameCount.incrementAndGet()
                handle.lastFrameAtMs = SystemClock.elapsedRealtime()
                if (!firstFrame.isCompleted) {
                    firstFrame.complete(Unit)
                    WjxFileLogger.i(TAG, "first frame target=${scene.target}")
                }
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                handle.surface?.release()
                handle.surface = null
                if (!handle.intentionalRemoval) onUnexpectedSurfaceLoss(handle)
                return true
            }
        }

        return runCatching {
            windowManager.addView(container, params)
            WjxFileLogger.i(
                TAG,
                "prepared opaque target=${scene.target} display=${display.displayId} " +
                    "${windowWidth}x$windowHeight+$windowX,$windowY",
            )
            handle
        }.onFailure {
            WjxFileLogger.e(TAG, "add overlay failed: ${it.message}", it)
        }.getOrNull()
    }

    private fun applyCalibration(
        handle: ElectronicMirrorOverlayHandle,
        width: Int = handle.textureView.width,
        height: Int = handle.textureView.height,
    ) {
        if (width <= 0 || height <= 0 || handle.intentionalRemoval) return
        handle.textureView.setTransform(
            electronicMirrorTextureMatrix(handle.calibration, width, height),
        )
    }

    private fun resolveDisplay(target: ElectronicMirrorDisplayTarget): Display? {
        val manager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return when (target) {
            ElectronicMirrorDisplayTarget.CENTER -> manager.getDisplay(Display.DEFAULT_DISPLAY)
            ElectronicMirrorDisplayTarget.CLUSTER -> {
                val candidates = manager.displays
                    .filter { it.displayId != Display.DEFAULT_DISPLAY }
                    .sortedByDescending { electronicMirrorDisplayRank(it.name) }
                candidates.firstOrNull { electronicMirrorDisplayRank(it.name) > 0 }
                    ?: manager.getDisplay(DEFAULT_CLUSTER_DISPLAY_ID)
            }
        }
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

    companion object {
        private const val TAG = "ElectronicMirrorOverlay"
        private const val DEFAULT_CLUSTER_DISPLAY_ID = 2
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

/** Rank known BYD cluster projection displays without binding the feature to one ROM name. */
internal fun electronicMirrorDisplayRank(name: String): Int {
    val normalized = name.trim().lowercase()
    return when {
        "xdjascreenprojection" in normalized && normalized.endsWith("_1") -> 40
        "xdjascreenprojection" in normalized -> 30
        "叠加视图 #1" in normalized -> 25
        "fission_" in normalized -> 20
        else -> 0
    }
}
