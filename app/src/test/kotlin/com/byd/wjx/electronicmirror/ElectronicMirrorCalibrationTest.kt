package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Test

class ElectronicMirrorProjectionSpecTest {
    @Test
    fun `default projections match production values`() {
        val left = ElectronicMirrorProjectionSpec.DEFAULT_LEFT
        val right = ElectronicMirrorProjectionSpec.DEFAULT_RIGHT
        val rear = ElectronicMirrorProjectionSpec.DEFAULT_REAR

        assertEquals(-0.12f, left.horizontal, 0.001f)
        assertEquals(0.12f, right.horizontal, 0.001f)
        assertEquals(0.1f, left.heightOffset, 0.001f)
        assertEquals(1.35f, left.viewScale, 0.001f)
        assertEquals(0.12f, rear.heightOffset, 0.001f)
        assertEquals(1.5f, rear.viewScale, 0.001f)
    }

    @Test
    fun `projection values are clamped to validated transform limits`() {
        val safe = ElectronicMirrorProjectionSpec(
            horizontalPermille = 900,
            heightOffsetPermille = -900,
            viewScalePermille = 3000,
        ).sanitized()

        assertEquals(ElectronicMirrorProjectionSpec.MAX_HORIZONTAL, safe.horizontalPermille)
        assertEquals(ElectronicMirrorProjectionSpec.MIN_HEIGHT_OFFSET, safe.heightOffsetPermille)
        assertEquals(ElectronicMirrorProjectionSpec.MAX_VIEW_SCALE, safe.viewScalePermille)
    }

    @Test
    fun `left and right projection cannot cross into the opposite direction`() {
        val left = ElectronicMirrorProjectionSpec(horizontalPermille = 300)
            .sanitizedFor(ElectronicMirrorViewpoint.LEFT)
        val right = ElectronicMirrorProjectionSpec(horizontalPermille = -300)
            .sanitizedFor(ElectronicMirrorViewpoint.RIGHT)

        assertEquals(0, left.horizontalPermille)
        assertEquals(0, right.horizontalPermille)
    }

    @Test
    fun `picture height can move further upward for cluster calibration`() {
        val side = ElectronicMirrorProjectionSpec(heightOffsetPermille = 999)
            .sanitizedFor(ElectronicMirrorViewpoint.LEFT)
        val rear = ElectronicMirrorProjectionSpec(heightOffsetPermille = 999)
            .sanitizedFor(ElectronicMirrorViewpoint.REAR)

        assertEquals(ElectronicMirrorProjectionSpec.MAX_SIDE_HEIGHT_OFFSET, side.heightOffsetPermille)
        assertEquals(ElectronicMirrorProjectionSpec.MAX_REAR_HEIGHT_OFFSET, rear.heightOffsetPermille)
        assertEquals(0.36f, side.verticalCenterRatio, 0.001f)
        assertEquals(0.20f, rear.verticalCenterRatio, 0.001f)
    }

    @Test
    fun `real vehicle panorama profile scales 7680 by 1300 source to 3000 by 508 buffer`() {
        val profile = ElectronicMirrorSurfaceProfile.panorama(surfaceIndex = 0)

        assertEquals("pano_h", profile.cameraTag)
        assertEquals(0, profile.surfaceIndex)
        assertEquals(4, profile.tileCount)
        assertEquals(3000, profile.bufferWidth)
        assertEquals(508, profile.bufferHeight)
    }
}
