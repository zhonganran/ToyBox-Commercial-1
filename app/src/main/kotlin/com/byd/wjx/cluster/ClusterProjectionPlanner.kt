package com.byd.wjx.cluster

import kotlin.math.roundToInt

/** Complete geometry needed to place a projection surface and size its render buffer. */
internal data class ProjectionSurfacePlan(
    val geo: ClusterGeometry,
    val render: RenderPlan,
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val surfaceX: Int,
    val surfaceY: Int,
)

internal object ClusterProjectionPlanner {
    /** Independent mode renders the selected app directly inside the configured cluster window. */
    fun independent(
        mode: ClusterMode,
        clusterWidth: Int,
        clusterHeight: Int,
        clusterDensityDpi: Int,
        widthPct: Int,
        heightPct: Int,
        offsetXPct: Int,
        offsetYPct: Int,
        scalePct: Int,
    ): ProjectionSurfacePlan? {
        val geo = geometryFor(
            mode,
            clusterWidth,
            clusterHeight,
            widthPct,
            heightPct,
            offsetXPct,
            offsetYPct,
        ) ?: return null
        val render = renderPlanFor(geo, clusterDensityDpi, scalePct)
        return ProjectionSurfacePlan(
            geo = geo,
            render = render,
            surfaceWidth = geo.width,
            surfaceHeight = geo.height,
            surfaceX = geo.xOffset,
            surfaceY = geo.yOffset,
        )
    }

    /**
     * Duplicate mode crop-fills the cluster with the centre display without stretching it. The
     * surface may extend beyond the cluster bounds; the overlay container clips the excess.
     */
    fun duplicate(
        clusterWidth: Int,
        clusterHeight: Int,
        clusterDensityDpi: Int,
        sourceWidth: Int,
        sourceHeight: Int,
    ): ProjectionSurfacePlan {
        val geo = ClusterGeometry(clusterWidth, clusterHeight, 0, 0)
        val sourceAspect = if (sourceWidth > 0 && sourceHeight > 0) {
            sourceWidth.toFloat() / sourceHeight.toFloat()
        } else {
            clusterWidth.toFloat() / clusterHeight.toFloat()
        }
        val clusterAspect = clusterWidth.toFloat() / clusterHeight.toFloat()
        val surfaceWidth: Int
        val surfaceHeight: Int
        if (sourceAspect > clusterAspect) {
            surfaceHeight = clusterHeight
            surfaceWidth = (clusterHeight * sourceAspect).roundToInt().coerceAtLeast(clusterWidth)
        } else {
            surfaceWidth = clusterWidth
            surfaceHeight = (clusterWidth / sourceAspect).roundToInt().coerceAtLeast(clusterHeight)
        }
        val surfaceX = (clusterWidth - surfaceWidth) / 2
        val surfaceY = (clusterHeight - surfaceHeight) / 2
        return ProjectionSurfacePlan(
            geo = geo,
            render = RenderPlan(surfaceWidth, surfaceHeight, clusterDensityDpi),
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            surfaceX = surfaceX,
            surfaceY = surfaceY,
        )
    }
}
