package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorTextureTransformTest {
    private fun scene(
        viewpoint: ElectronicMirrorViewpoint,
        source: ElectronicMirrorCameraSource,
        projection: ElectronicMirrorProjectionSpec,
    ) = ElectronicMirrorScene(
        viewpoint = viewpoint,
        source = source,
        target = ElectronicMirrorDisplayTarget.CENTER,
        geometry = ElectronicMirrorGeometry.DEFAULT_CENTER,
        reason = ElectronicMirrorReason.MANUAL_PREVIEW,
        fps = 20,
        projection = projection,
        surfaceProfile = ElectronicMirrorSurfaceProfile.panorama(0),
        tileIndex = source.stripTileIndex,
        mirrorHorizontally = viewpoint == ElectronicMirrorViewpoint.REAR,
    )

    @Test
    fun `left and right use different tiles without changing scale`() {
        val left = assertNotNullTransform(
            scene(
                ElectronicMirrorViewpoint.LEFT,
                ElectronicMirrorCameraSource.SOURCE_A,
                ElectronicMirrorProjectionSpec.DEFAULT_LEFT,
            ),
        )
        val right = assertNotNullTransform(
            scene(
                ElectronicMirrorViewpoint.RIGHT,
                ElectronicMirrorCameraSource.SOURCE_B,
                ElectronicMirrorProjectionSpec.DEFAULT_RIGHT,
            ),
        )

        assertEquals(left.scaleX, right.scaleX, 0.001f)
        assertEquals(left.scaleY, right.scaleY, 0.001f)
        assertTrue(right.translateX < left.translateX)
    }

    @Test
    fun `raising picture selects a higher source region`() {
        val base = ElectronicMirrorProjectionSpec.DEFAULT_REAR
        val low = assertNotNullTransform(
            scene(ElectronicMirrorViewpoint.REAR, ElectronicMirrorCameraSource.SOURCE_C, base.copy(heightOffsetPermille = -100)),
        )
        val high = assertNotNullTransform(
            scene(ElectronicMirrorViewpoint.REAR, ElectronicMirrorCameraSource.SOURCE_C, base.copy(heightOffsetPermille = 120)),
        )

        assertTrue(high.translateY > low.translateY)
    }

    @Test
    fun `larger view scale crops further instead of changing camera source`() {
        val base = ElectronicMirrorProjectionSpec.DEFAULT_LEFT
        val wide = assertNotNullTransform(
            scene(ElectronicMirrorViewpoint.LEFT, ElectronicMirrorCameraSource.SOURCE_A, base.copy(viewScalePermille = 850)),
        )
        val zoomed = assertNotNullTransform(
            scene(ElectronicMirrorViewpoint.LEFT, ElectronicMirrorCameraSource.SOURCE_A, base.copy(viewScalePermille = 1450)),
        )

        assertTrue(zoomed.scaleX > wide.scaleX)
        assertTrue(zoomed.scaleY > wide.scaleY)
    }

    @Test
    fun `invalid output size returns no transform`() {
        val transform = electronicMirrorTextureTransform(
            scene(
                ElectronicMirrorViewpoint.REAR,
                ElectronicMirrorCameraSource.SOURCE_C,
                ElectronicMirrorProjectionSpec.DEFAULT_REAR,
            ),
            0,
            324,
        )

        assertEquals(null, transform)
    }

    private fun assertNotNullTransform(scene: ElectronicMirrorScene): ElectronicMirrorTextureTransform {
        val transform = electronicMirrorTextureTransform(scene, 576, 324)
        assertNotNull(transform)
        return checkNotNull(transform)
    }
}
