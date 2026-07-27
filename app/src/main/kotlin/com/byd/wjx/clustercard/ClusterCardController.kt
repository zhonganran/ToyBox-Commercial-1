package com.byd.wjx.clustercard

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Singleton
class ClusterCardController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var displayContext: Context? = null
    private var windowManager: WindowManager? = null
    private var cardView: ClusterCardView? = null
    private var params: WindowManager.LayoutParams? = null
    private var displayId = -1
    private var clusterWidth = 1280
    private var clusterHeight = 480
    private var lastMissingDisplayLogAt = 0L

    suspend fun render(
        config: ClusterCardConfig,
        baseData: DiParsData?,
        telemetryData: DiParsData?,
    ) = withContext(Dispatchers.Main.immediate) {
        val metrics = ClusterCardMetricCatalog.selected(config)
        if (!config.enabled || metrics.isEmpty() || !Settings.canDrawOverlays(appContext)) {
            hideInternal()
            return@withContext
        }

        ensureDisplayStillValid()
        if (cardView == null && !show(config)) return@withContext
        val view = cardView ?: return@withContext
        val lp = params ?: return@withContext
        val newWidth = windowWidth(config)
        val newHeight = windowHeight(config)
        var changed = false
        if (lp.width != newWidth || lp.height != newHeight) {
            lp.width = newWidth
            lp.height = newHeight
            changed = true
        }
        if (lp.alpha != config.alpha) {
            lp.alpha = config.alpha
            changed = true
        }
        val oldX = lp.x
        val oldY = lp.y
        positionByPercent(lp, config)
        view.bind(metrics, baseData, telemetryData, config.scale)
        if (changed || lp.x != oldX || lp.y != oldY) {
            runCatching { windowManager?.updateViewLayout(view, lp) }
                .onFailure {
                    WjxFileLogger.w(TAG, "update failed; rebuilding: ${it.message}")
                    hideInternal()
                }
        }
    }

    fun stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideInternal()
        } else {
            mainHandler.post(::hideInternal)
        }
    }

    private fun show(config: ClusterCardConfig): Boolean {
        val display = resolveClusterDisplay() ?: run {
            logMissingDisplay()
            return false
        }
        val ctx = appContext.createDisplayContext(display)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = ClusterCardView(ctx)
        val lp = WindowManager.LayoutParams(
            windowWidth(config, ctx),
            windowHeight(config, ctx),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            CLUSTER_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            alpha = config.alpha
            positionByPercent(this, config)
        }
        return runCatching {
            wm.addView(view, lp)
            displayContext = ctx
            windowManager = wm
            cardView = view
            params = lp
            displayId = display.displayId
            WjxFileLogger.i(
                TAG,
                "shown display=${display.displayId} size=${lp.width}x${lp.height} metrics=${config.metricIds.joinToString()}",
            )
            true
        }.getOrElse {
            WjxFileLogger.w(TAG, "show failed: ${it.message}", it)
            displayContext = null
            windowManager = null
            cardView = null
            params = null
            displayId = -1
            false
        }
    }

    private fun ensureDisplayStillValid() {
        if (cardView == null || displayId < 0) return
        val display = resolveClusterDisplay()
        if (display == null || !display.isValid || display.displayId != displayId) {
            WjxFileLogger.w(TAG, "cluster display changed old=$displayId new=${display?.displayId}; rebuilding")
            hideInternal()
        }
    }

    private fun hideInternal() {
        val view = cardView
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
                .onFailure { WjxFileLogger.w(TAG, "remove failed: ${it.message}") }
        }
        cardView = null
        params = null
        windowManager = null
        displayContext = null
        displayId = -1
    }

    private fun positionByPercent(lp: WindowManager.LayoutParams, config: ClusterCardConfig) {
        val maxX = (clusterWidth - lp.width).coerceAtLeast(0)
        val maxY = (clusterHeight - lp.height).coerceAtLeast(0)
        lp.x = (maxX * config.xPct / 100f).roundToInt().coerceIn(0, maxX)
        lp.y = (maxY * config.yPct / 100f).roundToInt().coerceIn(0, maxY)
    }

    private fun windowWidth(config: ClusterCardConfig, context: Context? = displayContext): Int {
        val density = context?.resources?.displayMetrics?.density ?: appContext.resources.displayMetrics.density
        return (ClusterCardLayout.widthDp(config) * density).roundToInt().coerceIn(180, clusterWidth)
    }

    private fun windowHeight(config: ClusterCardConfig, context: Context? = displayContext): Int {
        val density = context?.resources?.displayMetrics?.density ?: appContext.resources.displayMetrics.density
        return (ClusterCardLayout.heightDp(config) * density).roundToInt().coerceIn(64, clusterHeight)
    }

    private fun resolveClusterDisplay(): Display? {
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val projectionDisplays = displayManager.displays.filter {
            it.name.contains(CLUSTER_DISPLAY_NAME, ignoreCase = true)
        }
        val display = projectionDisplays.firstOrNull { it.name.endsWith("_1") }
            ?: projectionDisplays.firstOrNull()
            ?: displayManager.getDisplay(DEFAULT_CLUSTER_DISPLAY_ID)
        if (display != null) {
            val size = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            if (size.x > 0 && size.y > 0) {
                clusterWidth = size.x
                clusterHeight = size.y
            }
        }
        return display
    }

    private fun logMissingDisplay() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMissingDisplayLogAt >= MISSING_DISPLAY_LOG_INTERVAL_MS) {
            lastMissingDisplayLogAt = now
            WjxFileLogger.w(TAG, "cluster display not found; waiting for projection display")
        }
    }

    private companion object {
        const val TAG = "ClusterCard"
        const val CLUSTER_DISPLAY_NAME = "XDJAScreenProjection"
        const val DEFAULT_CLUSTER_DISPLAY_ID = 2
        const val MISSING_DISPLAY_LOG_INTERVAL_MS = 30_000L
        const val CLUSTER_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
