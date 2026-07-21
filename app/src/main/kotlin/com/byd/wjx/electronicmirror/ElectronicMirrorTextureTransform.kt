package com.byd.wjx.electronicmirror

import android.graphics.Matrix
import kotlin.math.max

/**
 * The production-validated crop model used by the SurfacePreview path.
 *
 * BYD's pano_h producer delivers one horizontal strip whose tiles are rear, left, right and
 * front. TextureView scales the complete strip, translates the selected tile into the window and
 * clips everything outside it. The producer frame is already rectified, so no fisheye shader or
 * frame copy belongs in this path.
 */
internal data class ElectronicMirrorTextureTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float,
)

internal fun electronicMirrorTextureTransform(
    scene: ElectronicMirrorScene,
    viewWidth: Int,
    viewHeight: Int,
): ElectronicMirrorTextureTransform? {
    if (viewWidth <= 0 || viewHeight <= 0) return null

    val profile = scene.surfaceProfile.sanitized()
    val tileCount = profile.tileCount.coerceAtLeast(1)
    val tileIndex = scene.tileIndex.coerceIn(0, tileCount - 1)
    val tileAspect = (profile.bufferWidth / tileCount.toFloat()) / profile.bufferHeight.toFloat()
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    if (!tileAspect.isFinite() || tileAspect <= 0f || !viewAspect.isFinite() || viewAspect <= 0f) {
        return null
    }

    val aspectScaleX = max(tileAspect / viewAspect, 1f)
    val aspectScaleY = max(viewAspect / tileAspect, 1f)
    val viewScale = scene.projection.viewScale.coerceIn(MIN_VIEW_SCALE, MAX_VIEW_SCALE)
    val cropScaleX = max(aspectScaleX * viewScale, 1f)
    val cropScaleY = max(aspectScaleY * viewScale, 1f)
    val horizontal = scene.projection.horizontal.coerceIn(MIN_HORIZONTAL, MAX_HORIZONTAL)
    val vertical = (scene.projection.verticalCenterRatio - 0.5f)
        .coerceIn(MIN_VERTICAL_FROM_CENTER, MAX_VERTICAL_FROM_CENTER)

    return ElectronicMirrorTextureTransform(
        scaleX = tileCount * cropScaleX,
        scaleY = cropScaleY,
        translateX = -viewWidth * (
            horizontal * cropScaleX +
                (cropScaleX - 1f) / 2f +
                tileIndex * cropScaleX
            ),
        translateY = -viewHeight * (
            vertical * cropScaleY +
                (cropScaleY - 1f) / 2f
            ),
    )
}

internal fun electronicMirrorTextureMatrix(
    scene: ElectronicMirrorScene,
    viewWidth: Int,
    viewHeight: Int,
): Matrix = Matrix().apply {
    electronicMirrorTextureTransform(scene, viewWidth, viewHeight)?.let { transform ->
        setScale(transform.scaleX, transform.scaleY)
        postTranslate(transform.translateX, transform.translateY)
    }
}

private const val MIN_HORIZONTAL = -0.45f
private const val MAX_HORIZONTAL = 0.45f
private const val MIN_VERTICAL_FROM_CENTER = -0.3f
private const val MAX_VERTICAL_FROM_CENTER = 0.3f
private const val MIN_VIEW_SCALE = 0.75f
private const val MAX_VIEW_SCALE = 2.0f
