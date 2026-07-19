package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Test

class ElectronicMirrorTransitionPolicyTest {
    private val base = ElectronicMirrorScene(
        viewpoint = ElectronicMirrorViewpoint.LEFT,
        source = ElectronicMirrorCameraSource.SOURCE_A,
        target = ElectronicMirrorDisplayTarget.CENTER,
        geometry = ElectronicMirrorGeometry.DEFAULT_CENTER,
        reason = ElectronicMirrorReason.LEFT_TURN,
        fps = 20,
        projection = ElectronicMirrorProjectionSpec.DEFAULT_LEFT,
        surfaceProfile = ElectronicMirrorSurfaceProfile.panorama(0),
        tileIndex = ElectronicMirrorCameraSource.SOURCE_A.stripTileIndex,
    )

    @Test
    fun `unchanged scene does nothing`() {
        assertEquals(
            ElectronicMirrorTransition.NONE,
            ElectronicMirrorTransitionPolicy.resolve(base, base),
        )
    }

    @Test
    fun `projection-only change updates TextureView crop`() {
        val desired = base.copy(
            projection = base.projection.copy(horizontalPermille = -80),
        )

        assertEquals(
            ElectronicMirrorTransition.UPDATE_PROJECTION,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `panorama tile change keeps the producer Surface`() {
        val desired = base.copy(
            viewpoint = ElectronicMirrorViewpoint.RIGHT,
            source = ElectronicMirrorCameraSource.SOURCE_B,
            reason = ElectronicMirrorReason.RIGHT_TURN,
            projection = ElectronicMirrorProjectionSpec.DEFAULT_RIGHT,
            tileIndex = ElectronicMirrorCameraSource.SOURCE_B.stripTileIndex,
        )

        assertEquals(
            ElectronicMirrorTransition.UPDATE_PROJECTION,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `geometry-only change updates the existing window`() {
        val desired = base.copy(geometry = base.geometry.copy(widthPct = 36))

        assertEquals(
            ElectronicMirrorTransition.UPDATE_WINDOW,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `target change rebuilds the window`() {
        val desired = base.copy(
            target = ElectronicMirrorDisplayTarget.CLUSTER,
            geometry = ElectronicMirrorGeometry.DEFAULT_CLUSTER,
        )

        assertEquals(
            ElectronicMirrorTransition.REBUILD_WINDOW,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `changing panorama Surface output switches the existing producer binding`() {
        val desired = base.copy(surfaceProfile = ElectronicMirrorSurfaceProfile.panorama(1))

        assertEquals(
            ElectronicMirrorTransition.SWITCH_SOURCE,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `changing panorama buffer route rebuilds the Surface`() {
        val desired = base.copy(
            surfaceProfile = base.surfaceProfile.copy(qualityWidth = 960),
        )

        assertEquals(
            ElectronicMirrorTransition.REBUILD_WINDOW,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }
}
