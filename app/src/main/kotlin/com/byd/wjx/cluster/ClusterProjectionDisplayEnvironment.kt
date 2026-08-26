package com.byd.wjx.cluster

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.byd.wjx.log.WjxFileLogger

/** Resolves physical displays and turns saved preferences into a projection surface plan. */
internal class ClusterProjectionDisplayEnvironment {
    private var clusterWidth: Int = DEFAULT_CLUSTER_WIDTH
    private var clusterHeight: Int = DEFAULT_CLUSTER_HEIGHT
    private var clusterDensityDpi: Int = DEFAULT_CLUSTER_DENSITY_DPI

    fun resolveClusterDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val projectionDisplays = displayManager.displays.filter {
            it.name.contains(CLUSTER_DISPLAY_NAME, ignoreCase = true)
        }
        val match = projectionDisplays.firstOrNull { it.name.endsWith("_1") }
            ?: projectionDisplays.firstOrNull()
            ?: displayManager.getDisplay(DEFAULT_CLUSTER_DISPLAY_ID)
        if (match != null) updateClusterMetrics(match)
        return match
    }

    fun buildSurfacePlan(
        context: Context,
        layout: ProjectionLayout,
        mode: ClusterMode = ClusterMode.FULLSCREEN,
    ): ProjectionSurfacePlan? {
        if (mode == ClusterMode.OFF) return null
        return if (layout == ProjectionLayout.DUPLICATE) {
            duplicateFullScreenPlan(context)
        } else {
            val preferences = ClusterProjectionPreferences(context).snapshot()
            ClusterProjectionPlanner.independent(
                mode = mode,
                clusterWidth = clusterWidth,
                clusterHeight = clusterHeight,
                clusterDensityDpi = clusterDensityDpi,
                widthPct = preferences.widthPct,
                heightPct = preferences.heightPct,
                offsetXPct = preferences.offsetXPct,
                offsetYPct = preferences.offsetYPct,
                scalePct = preferences.scalePct,
            )
        }
    }

    fun targetPackage(context: Context): String =
        ClusterProjectionPreferences(context).snapshot().targetPackage

    private fun updateClusterMetrics(display: Display) {
        val size = realSize(display)
        if (size.first > 0 && size.second > 0) {
            clusterWidth = size.first
            clusterHeight = size.second
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getMetrics(metrics)
        if (metrics.densityDpi > 0) clusterDensityDpi = metrics.densityDpi
        WjxFileLogger.i(
            TAG,
            "cluster display id=${display.displayId} ${clusterWidth}x$clusterHeight dpi=$clusterDensityDpi",
        )
    }

    private fun duplicateFullScreenPlan(context: Context): ProjectionSurfacePlan {
        val (sourceWidth, sourceHeight) = resolveCenterDisplaySize(context)
        val plan = ClusterProjectionPlanner.duplicate(
            clusterWidth = clusterWidth,
            clusterHeight = clusterHeight,
            clusterDensityDpi = clusterDensityDpi,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
        WjxFileLogger.i(
            TAG,
            "duplicate fullscreen crop source=${sourceWidth}x$sourceHeight " +
                "cluster=${clusterWidth}x$clusterHeight " +
                "buffer=${plan.render.bufferWidth}x${plan.render.bufferHeight} " +
                "surface=${plan.surfaceWidth}x${plan.surfaceHeight}+${plan.surfaceX},${plan.surfaceY}",
        )
        return plan
    }

    private fun resolveCenterDisplaySize(context: Context): Pair<Int, Int> {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return clusterWidth to clusterHeight
        val (width, height) = realSize(display)
        return if (width > 0 && height > 0) width to height else clusterWidth to clusterHeight
    }

    private fun realSize(display: Display): Pair<Int, Int> {
        val point = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(point)
        return point.x to point.y
    }

    private companion object {
        const val TAG = "ClusterProjection"
        const val CLUSTER_DISPLAY_NAME = "XDJAScreenProjection"
        const val DEFAULT_CLUSTER_DISPLAY_ID = 2
        const val DEFAULT_CLUSTER_WIDTH = 1280
        const val DEFAULT_CLUSTER_HEIGHT = 480
        const val DEFAULT_CLUSTER_DENSITY_DPI = 320
    }
}
