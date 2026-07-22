package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Test

class ElectronicMirrorWindowLayoutTest {
    private fun scene(
        viewpoint: ElectronicMirrorViewpoint,
        reason: ElectronicMirrorReason,
        geometry: ElectronicMirrorGeometry,
    ) = ElectronicMirrorScene(
        viewpoint = viewpoint,
        source = when (viewpoint) {
            ElectronicMirrorViewpoint.LEFT -> ElectronicMirrorCameraSource.SOURCE_A
            ElectronicMirrorViewpoint.RIGHT -> ElectronicMirrorCameraSource.SOURCE_B
            ElectronicMirrorViewpoint.REAR -> ElectronicMirrorCameraSource.SOURCE_C
        },
        target = ElectronicMirrorDisplayTarget.CENTER,
        geometry = geometry,
        reason = reason,
        fps = 20,
        projection = ElectronicMirrorProjectionSpec.defaultFor(viewpoint),
        surfaceProfile = ElectronicMirrorSurfaceProfile.panorama(0),
    )

    @Test
    fun `side window uses validated 30 percent width and 16 by 9`() {
        val layout = electronicMirrorWindowLayout(
            scene(
                ElectronicMirrorViewpoint.LEFT,
                ElectronicMirrorReason.LEFT_TURN,
                ElectronicMirrorGeometry.DEFAULT_CENTER,
            ),
            1920,
            1080,
        )

        assertEquals(576, layout.width)
        assertEquals(324, layout.height)
    }

    @Test
    fun `left and right default geometry anchor to opposite edges`() {
        val config = ElectronicMirrorConfig(enabled = true)
        val leftScene = requireNotNull(
            ElectronicMirrorSceneArbiter.resolve(
                config,
                ElectronicMirrorSignals(2, 4, originalCameraActive = false),
            ),
        )
        val rightScene = requireNotNull(
            ElectronicMirrorSceneArbiter.resolve(
                config,
                ElectronicMirrorSignals(4, 4, originalCameraActive = false),
            ),
        )

        val left = electronicMirrorWindowLayout(leftScene, 1920, 1080)
        val right = electronicMirrorWindowLayout(rightScene, 1920, 1080)
        assertEquals(0, left.x)
        assertEquals(1920 - right.width, right.x)
        assertEquals(left.y, right.y)
    }

    @Test
    fun `streaming rear keeps its configured wide dimensions`() {
        val layout = electronicMirrorWindowLayout(
            scene(
                ElectronicMirrorViewpoint.REAR,
                ElectronicMirrorReason.STREAMING_REAR,
                ElectronicMirrorGeometry.DEFAULT_STREAMING_CENTER,
            ),
            1920,
            1080,
        )

        assertEquals(883, layout.width)
        assertEquals(410, layout.height)
    }

    @Test
    fun `drag position converts pixels into available-area percent`() {
        assertEquals(0, positionPercent(0, 1_000))
        assertEquals(50, positionPercent(500, 1_000))
        assertEquals(100, positionPercent(1_000, 1_000))
        assertEquals(0, positionPercent(50, 0))
    }

    @Test
    fun `left and right drag persist as symmetric side inset`() {
        val leftScene = scene(
            ElectronicMirrorViewpoint.LEFT,
            ElectronicMirrorReason.LEFT_TURN,
            ElectronicMirrorGeometry.DEFAULT_CENTER,
        )
        val rightScene = scene(
            ElectronicMirrorViewpoint.RIGHT,
            ElectronicMirrorReason.RIGHT_TURN,
            ElectronicMirrorGeometry.DEFAULT_CENTER,
        )

        val fromLeft = ElectronicMirrorConfig().withDraggedWindowPosition(leftScene, 18, 42)
        val fromRight = ElectronicMirrorConfig().withDraggedWindowPosition(rightScene, 82, 42)

        assertEquals(ElectronicMirrorGeometry(30, 80, 18, 42), fromLeft.centerGeometry)
        assertEquals(fromLeft.centerGeometry, fromRight.centerGeometry)
    }

    @Test
    fun `streaming rear drag keeps its own target position`() {
        val streamingScene = scene(
            ElectronicMirrorViewpoint.REAR,
            ElectronicMirrorReason.STREAMING_REAR,
            ElectronicMirrorGeometry.DEFAULT_STREAMING_CENTER,
        )

        val updated = ElectronicMirrorConfig().withDraggedWindowPosition(streamingScene, 67, 23)

        assertEquals(
            ElectronicMirrorGeometry(46, 38, 67, 23),
            updated.streamingRearCenterGeometry,
        )
        assertEquals(ElectronicMirrorGeometry.DEFAULT_CENTER, updated.centerGeometry)
    }
}
