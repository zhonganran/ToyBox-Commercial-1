package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectronicMirrorSceneArbiterTest {
    private val enabled = ElectronicMirrorConfig(enabled = true, reverseEnabled = true)

    @Test
    fun `original vehicle camera always owns the screen first`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 2, gear = 2, originalCameraActive = true),
            manualPreview = ElectronicMirrorViewpoint.RIGHT,
        )

        assertNull(scene)
    }

    @Test
    fun `auto parking fid blocks mirror before foreground monitor catches up`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(
                turnSignalFlash = 4,
                gear = 4,
                originalCameraActive = false,
                autoParkingActive = true,
            ),
        )

        assertNull(scene)
    }

    @Test
    fun `reverse is above turn signal within electronic mirror`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 2, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorViewpoint.REAR, scene?.viewpoint)
        assertEquals(ElectronicMirrorReason.REVERSE, scene?.reason)
    }

    @Test
    fun `stable left and right enums select their matching side views`() {
        val left = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 2, gear = 4, originalCameraActive = false),
        )
        val right = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 4, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorViewpoint.LEFT, left?.viewpoint)
        assertEquals(ElectronicMirrorViewpoint.RIGHT, right?.viewpoint)
    }

    @Test
    fun `hazard and off do not borrow independent lamp fallback data`() {
        assertNull(
            ElectronicMirrorSceneArbiter.resolve(
                enabled,
                ElectronicMirrorSignals(turnSignalFlash = 6, gear = 4, originalCameraActive = false),
            ),
        )
        assertNull(
            ElectronicMirrorSceneArbiter.resolve(
                enabled,
                ElectronicMirrorSignals(turnSignalFlash = 1, gear = 4, originalCameraActive = false),
            ),
        )
    }

    @Test
    fun `manual preview works while automation master is disabled`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            ElectronicMirrorConfig(enabled = false),
            ElectronicMirrorSignals(turnSignalFlash = null, gear = null, originalCameraActive = false),
            manualPreview = ElectronicMirrorViewpoint.LEFT,
        )

        assertEquals(ElectronicMirrorReason.MANUAL_PREVIEW, scene?.reason)
        assertEquals(ElectronicMirrorViewpoint.LEFT, scene?.viewpoint)
    }

    @Test
    fun `scene carries calibration for its selected viewpoint`() {
        val rightCalibration = ElectronicMirrorCalibration(yawDegrees = 7)
        val scene = ElectronicMirrorSceneArbiter.resolve(
            ElectronicMirrorConfig(
                enabled = true,
                rightCalibration = rightCalibration,
            ),
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 4, originalCameraActive = false),
        )

        assertEquals(rightCalibration, scene?.calibration)
    }

    @Test
    fun `scene carries mapped camera source for logical viewpoint`() {
        val config = ElectronicMirrorConfig(enabled = true)
            .withSource(ElectronicMirrorViewpoint.LEFT, ElectronicMirrorCameraSource.SOURCE_C)
        val scene = ElectronicMirrorSceneArbiter.resolve(
            config,
            ElectronicMirrorSignals(turnSignalFlash = 2, gear = 4, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorViewpoint.LEFT, scene?.viewpoint)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, scene?.source)
    }
}
