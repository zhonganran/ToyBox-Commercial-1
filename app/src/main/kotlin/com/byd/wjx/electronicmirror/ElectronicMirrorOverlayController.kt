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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class ElectronicMirrorOverlayHandle(
    val windowManager: WindowManager,
    val container: FrameLayout,
    val textureView: ElectronicMirrorOpaqueTextureView,
    val ready: CompletableDeferred<Boolean>,
    val frameHub: ElectronicMirrorFrameHub,
    val label: TextView,
    val target: ElectronicMirrorDisplayTarget,
    val layoutParams: WindowManager.LayoutParams,
    val displayWidth: Int,
    val displayHeight: Int,
    initialScene: ElectronicMirrorScene,
) {
    @Volatile var intentionalRemoval: Boolean = false
    @Volatile var scene: ElectronicMirrorScene = initialScene
}

/** Creates the alpha-normalised camera window on either the cluster or centre display. */
internal class ElectronicMirrorOverlayController(
    context: Context,
    private val onUnexpectedSurfaceLoss: (ElectronicMirrorOverlayHandle) -> Unit,
) {
    private val appContext = context.applicationContext

    suspend fun show(
        scene: ElectronicMirrorScene,
        frameHub: ElectronicMirrorFrameHub,
    ): ElectronicMirrorOverlayHandle? {
        if (!Settings.canDrawOverlays(appContext)) {
            WjxFileLogger.w(TAG, "overlay permission missing")
            return null
        }
        val handle = withContext(Dispatchers.Main.immediate) {
            createWindow(scene, frameHub)
        } ?: return null
        val ready = withTimeoutOrNull(SURFACE_TIMEOUT_MS) { handle.ready.await() } == true
        if (!ready) {
            WjxFileLogger.w(TAG, "output Surface not ready target=${scene.target}")
            hide(handle)
            return null
        }
        return handle
    }

    suspend fun hide(handle: ElectronicMirrorOverlayHandle?) {
        if (handle == null) return
        withContext(Dispatchers.Main.immediate) {
            handle.intentionalRemoval = true
            handle.textureView.releaseRenderer()
            runCatching { handle.windowManager.removeView(handle.container) }
                .onFailure { WjxFileLogger.w(TAG, "remove overlay failed: ${it.message}") }
        }
    }

    suspend fun updatePresentation(
        handle: ElectronicMirrorOverlayHandle,
        scene: ElectronicMirrorScene,
    ) {
        withContext(Dispatchers.Main.immediate) {
            handle.scene = scene
            handle.label.text = labelFor(scene)
            applyTexturePresentation(handle)
        }
    }

    suspend fun updateGeometry(
        handle: ElectronicMirrorOverlayHandle,
        scene: ElectronicMirrorScene,
    ) {
        withContext(Dispatchers.Main.immediate) {
            val layout = electronicMirrorWindowLayout(scene, handle.displayWidth, handle.displayHeight)
            handle.layoutParams.width = layout.width
            handle.layoutParams.height = layout.height
            handle.layoutParams.x = layout.x
            handle.layoutParams.y = layout.y
            handle.scene = scene
            handle.windowManager.updateViewLayout(handle.container, handle.layoutParams)
            handle.textureView.post { applyTexturePresentation(handle) }
        }
    }

    private fun createWindow(
        scene: ElectronicMirrorScene,
        frameHub: ElectronicMirrorFrameHub,
    ): ElectronicMirrorOverlayHandle? {
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
        val layout = electronicMirrorWindowLayout(scene, point.x, point.y)
        val container = FrameLayout(displayContext).apply {
            clipChildren = true
            clipToPadding = true
            alpha = 1f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            // Do not add our own black placeholder or first-frame reveal layer.
            setBackgroundColor(Color.TRANSPARENT)
            clipToOutline = false
            foreground = roundedDrawable(Color.TRANSPARENT, BORDER_RADIUS_DP, BORDER_COLOR)
        }
        val ready = CompletableDeferred<Boolean>()
        var handle: ElectronicMirrorOverlayHandle? = null
        val textureView = ElectronicMirrorOpaqueTextureView(
            context = displayContext,
            frameHub = frameHub,
            onOutputReady = { attached ->
                if (!ready.isCompleted) ready.complete(attached)
                WjxFileLogger.i(
                    TAG,
                    "camera output Surface ready=$attached target=${scene.target} " +
                        "buffer=${scene.previewBufferWidth}x${scene.previewBufferHeight}",
                )
            },
            onOutputDestroyed = {
                handle?.let { activeHandle ->
                    if (!activeHandle.intentionalRemoval) onUnexpectedSurfaceLoss(activeHandle)
                }
            },
            onOutputFailed = { error ->
                WjxFileLogger.e(TAG, "opaque output failed: ${error.message}", error)
                if (!ready.isCompleted) ready.complete(false)
                handle?.takeIf { !it.intentionalRemoval }?.let(onUnexpectedSurfaceLoss)
            },
        )
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
            setPadding(
                dp(displayContext, 12),
                dp(displayContext, 5),
                dp(displayContext, 12),
                dp(displayContext, 5),
            )
            background = roundedDrawable(0xFF121B22.toInt(), 14f, null)
        }
        container.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(displayContext, 12)
                topMargin = dp(displayContext, 10)
            },
        )

        val live = TextView(displayContext).apply {
            text = LIVE_LABEL
            setTextColor(0xFF63E6BE.toInt())
            textSize = 11f
            setPadding(
                dp(displayContext, 10),
                dp(displayContext, 5),
                dp(displayContext, 10),
                dp(displayContext, 5),
            )
            background = roundedDrawable(0xFF121B22.toInt(), 14f, null)
        }
        container.addView(
            live,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dp(displayContext, 12)
                topMargin = dp(displayContext, 10)
            },
        )

        val params = WindowManager.LayoutParams(
            layout.width,
            layout.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WINDOW_FLAGS,
            // Use the validated overlay window. The child TextureView is explicitly opaque and the
            // GPU pass writes alpha=1 for every camera pixel, so the navigation layer cannot blend.
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = layout.x
            y = layout.y
            title = "SuperToyBox Electronic Mirror"
            alpha = 1f
            dimAmount = 0f
        }

        val createdHandle = ElectronicMirrorOverlayHandle(
            windowManager = windowManager,
            container = container,
            textureView = textureView,
            ready = ready,
            frameHub = frameHub,
            label = label,
            target = scene.target,
            layoutParams = params,
            displayWidth = point.x,
            displayHeight = point.y,
            initialScene = scene,
        )
        handle = createdHandle

        return runCatching {
            windowManager.addView(container, params)
            textureView.post { applyTexturePresentation(createdHandle) }
            WjxFileLogger.i(
                TAG,
                "prepared alpha-normalized TextureView target=${scene.target} display=${display.displayId} " +
                    "${layout.width}x${layout.height}+${layout.x},${layout.y} " +
                    "format=TRANSLUCENT frameAlpha=1 alpha=${params.alpha} " +
                    "buffer=${scene.previewBufferWidth}x${scene.previewBufferHeight} " +
                    "tile=${scene.tileIndex}/${scene.surfaceProfile.tileCount}",
            )
            createdHandle
        }.onFailure {
            createdHandle.intentionalRemoval = true
            createdHandle.textureView.releaseRenderer()
            WjxFileLogger.e(TAG, "add overlay failed: ${it.message}", it)
        }.getOrNull()
    }

    private fun applyTexturePresentation(handle: ElectronicMirrorOverlayHandle) {
        val textureView = handle.textureView
        if (textureView.width <= 0 || textureView.height <= 0) {
            textureView.post { applyTexturePresentation(handle) }
            return
        }
        val scene = handle.scene
        textureView.alpha = 1f
        textureView.scaleX = if (scene.mirrorHorizontally) -1f else 1f
        textureView.scaleY = 1f
        textureView.setTransform(
            electronicMirrorTextureMatrix(scene, textureView.width, textureView.height),
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
        ElectronicMirrorReason.STREAMING_REAR -> "流媒体后视"
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
        private const val LIVE_LABEL = "●  LIVE"
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

internal data class ElectronicMirrorWindowLayout(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
)

internal fun electronicMirrorWindowLayout(
    scene: ElectronicMirrorScene,
    displayWidth: Int,
    displayHeight: Int,
): ElectronicMirrorWindowLayout {
    require(displayWidth > 0 && displayHeight > 0) { "invalid display ${displayWidth}x$displayHeight" }
    val geometry = scene.geometry.sanitized()
    var width = (displayWidth * geometry.widthPct / 100f)
        .roundToInt()
        .coerceIn(MIN_WINDOW_WIDTH, displayWidth)
    var height = (displayHeight * geometry.heightPct / 100f)
        .roundToInt()
        .coerceIn(MIN_WINDOW_HEIGHT, displayHeight)

    // Electronic left/right/reverse windows use 16:9. Streaming rear keeps its
    // independently configured wide window dimensions.
    if (scene.reason != ElectronicMirrorReason.STREAMING_REAR) {
        val heightForWidth = (width * 9f / 16f).roundToInt().coerceAtLeast(1)
        if (heightForWidth > height) {
            width = (height * 16f / 9f).roundToInt().coerceAtLeast(1)
        } else {
            height = heightForWidth
        }
    }

    val availableX = (displayWidth - width).coerceAtLeast(0)
    val availableY = (displayHeight - height).coerceAtLeast(0)
    return ElectronicMirrorWindowLayout(
        width = width,
        height = height,
        x = (availableX * geometry.offsetXPct / 100f).roundToInt().coerceIn(0, availableX),
        y = (availableY * geometry.offsetYPct / 100f).roundToInt().coerceIn(0, availableY),
    )
}

private const val MIN_WINDOW_WIDTH = 160
private const val MIN_WINDOW_HEIGHT = 100

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
