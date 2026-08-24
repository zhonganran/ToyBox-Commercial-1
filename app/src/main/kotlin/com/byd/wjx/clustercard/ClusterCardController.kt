package com.byd.wjx.clustercard

import android.content.Context
import android.content.res.Configuration
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
    private val windows = ClusterCardSlot.entries.associateWith { CardWindowState() }
    private var clusterWidth = 1280
    private var clusterHeight = 480
    private var lastMissingDisplayLogAt = 0L

    suspend fun render(
        slot: ClusterCardSlot,
        config: ClusterCardConfig,
        vehicleConnected: Boolean,
        baseData: DiParsData?,
        telemetryData: DiParsData?,
    ) = withContext(Dispatchers.Main.immediate) {
        val window = window(slot)
        val metrics = ClusterCardMetricCatalog.selected(config)
        if (
            !ClusterCardVisibility.shouldShow(
                config = config,
                vehicleConnected = vehicleConnected,
                overlayPermissionGranted = Settings.canDrawOverlays(appContext),
            )
        ) {
            hideInternal(slot)
            return@withContext
        }

        ensureDisplayStillValid(slot)
        if (window.cardView == null && !show(slot, config)) return@withContext
        val view = window.cardView ?: return@withContext
        val lp = window.params ?: return@withContext
        val newWidth = windowWidth(config, window.displayContext)
        val newHeight = windowHeight(config, window.displayContext)
        var changed = false
        if (lp.width != newWidth || lp.height != newHeight) {
            lp.width = newWidth
            lp.height = newHeight
            changed = true
        }
        val oldX = lp.x
        val oldY = lp.y
        positionByPercent(lp, config)
        val nightMode = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        view.bind(metrics, baseData, telemetryData, config.scale, nightMode)
        if (changed || lp.x != oldX || lp.y != oldY) {
            runCatching { window.windowManager?.updateViewLayout(view, lp) }
                .onFailure {
                    WjxFileLogger.w(TAG, "${slot.label} update failed; rebuilding: ${it.message}")
                    hideInternal(slot)
                }
        }
    }

    fun stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideAllInternal()
        } else {
            mainHandler.post(::hideAllInternal)
        }
    }

    private fun show(slot: ClusterCardSlot, config: ClusterCardConfig): Boolean {
        val display = resolveClusterDisplay() ?: run {
            logMissingDisplay()
            return false
        }
        val window = window(slot)
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
            alpha = 1f
            positionByPercent(this, config)
        }
        return runCatching {
            wm.addView(view, lp)
            window.displayContext = ctx
            window.windowManager = wm
            window.cardView = view
            window.params = lp
            window.displayId = display.displayId
            WjxFileLogger.i(
                TAG,
                "${slot.label} shown display=${display.displayId} size=${lp.width}x${lp.height} metrics=${config.metricIds.joinToString()}",
            )
            true
        }.getOrElse {
            WjxFileLogger.w(TAG, "${slot.label} show failed: ${it.message}", it)
            window.clear()
            false
        }
    }

    private fun ensureDisplayStillValid(slot: ClusterCardSlot) {
        val window = window(slot)
        if (window.cardView == null || window.displayId < 0) return
        val display = resolveClusterDisplay()
        if (display == null || !display.isValid || display.displayId != window.displayId) {
            WjxFileLogger.w(
                TAG,
                "${slot.label} display changed old=${window.displayId} new=${display?.displayId}; rebuilding",
            )
            hideInternal(slot)
        }
    }

    private fun hideInternal(slot: ClusterCardSlot) {
        val window = window(slot)
        val view = window.cardView
        if (view != null) {
            runCatching { window.windowManager?.removeView(view) }
                .onFailure { WjxFileLogger.w(TAG, "${slot.label} remove failed: ${it.message}") }
        }
        window.clear()
    }

    private fun hideAllInternal() {
        ClusterCardSlot.entries.forEach(::hideInternal)
    }

    private fun window(slot: ClusterCardSlot): CardWindowState = checkNotNull(windows[slot])

    private fun positionByPercent(lp: WindowManager.LayoutParams, config: ClusterCardConfig) {
        val maxX = (clusterWidth - lp.width).coerceAtLeast(0)
        val maxY = (clusterHeight - lp.height).coerceAtLeast(0)
        lp.x = (maxX * config.xPct / 100f).roundToInt().coerceIn(0, maxX)
        lp.y = (maxY * config.yPct / 100f).roundToInt().coerceIn(0, maxY)
    }

    private fun windowWidth(config: ClusterCardConfig, context: Context?): Int {
        val density = context?.resources?.displayMetrics?.density ?: appContext.resources.displayMetrics.density
        return (ClusterCardLayout.widthDp(config) * density).roundToInt().coerceIn(180, clusterWidth)
    }

    private fun windowHeight(config: ClusterCardConfig, context: Context?): Int {
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

    private data class CardWindowState(
        var displayContext: Context? = null,
        var windowManager: WindowManager? = null,
        var cardView: ClusterCardView? = null,
        var params: WindowManager.LayoutParams? = null,
        var displayId: Int = -1,
    ) {
        fun clear() {
            displayContext = null
            windowManager = null
            cardView = null
            params = null
            displayId = -1
        }
    }

    private companion object {
        const val TAG = "ClusterCard"
        const val CLUSTER_DISPLAY_NAME = "XDJAScreenProjection"
        const val DEFAULT_CLUSTER_DISPLAY_ID = 2
        const val MISSING_DISPLAY_LOG_INTERVAL_MS = 30_000L
        const val CLUSTER_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
