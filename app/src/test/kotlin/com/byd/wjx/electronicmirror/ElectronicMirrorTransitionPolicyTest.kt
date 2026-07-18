package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Test

class ElectronicMirrorTransitionPolicyTest {
    private val base = ElectronicMirrorScene(
        viewpoint = ElectronicMirrorViewpoint.LEFT,
        source = ElectronicMirrorCameraSource.SOURCE_A,
        target = ElectronicMirrorDisplayTarget.CLUSTER,
        geometry = ElectronicMirrorGeometry.DEFAULT_CLUSTER,
        reason = ElectronicMirrorReason.LEFT_TURN,
        fps = 30,
        calibration = ElectronicMirrorCalibration.DEFAULT,
    )

    @Test
    fun `unchanged scene does nothing`() {
        assertEquals(
            ElectronicMirrorTransition.NONE,
            ElectronicMirrorTransitionPolicy.resolve(base, base),
        )
    }

    @Test
    fun `calibration-only change updates texture transform`() {
        val desired = base.copy(
            calibration = ElectronicMirrorCalibration.DEFAULT.copy(yawDegrees = 5),
        )

        assertEquals(
            ElectronicMirrorTransition.UPDATE_TRANSFORM,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `source-only change switches the existing camera window`() {
        val desired = base.copy(
            viewpoint = ElectronicMirrorViewpoint.RIGHT,
            source = ElectronicMirrorCameraSource.SOURCE_B,
            reason = ElectronicMirrorReason.RIGHT_TURN,
        )

        assertEquals(
            ElectronicMirrorTransition.SWITCH_SOURCE,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }

    @Test
    fun `target or geometry change rebuilds the window`() {
        val desired = base.copy(
            target = ElectronicMirrorDisplayTarget.CENTER,
            geometry = ElectronicMirrorGeometry.DEFAULT_CENTER,
        )

        assertEquals(
            ElectronicMirrorTransition.REBUILD_WINDOW,
            ElectronicMirrorTransitionPolicy.resolve(base, desired),
        )
    }
}
