package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorCalibrationTest {
    @Test
    fun `default calibration is a complete identity rectangle`() {
        val calibration = ElectronicMirrorCalibration.DEFAULT.sanitized()

        assertEquals(
            listOf(
                ElectronicMirrorCalibrationPoint(0, 0),
                ElectronicMirrorCalibrationPoint(1000, 0),
                ElectronicMirrorCalibrationPoint(1000, 1000),
                ElectronicMirrorCalibrationPoint(0, 1000),
            ),
            calibration.points,
        )
        assertEquals(0, calibration.yawDegrees)
        assertEquals(0, calibration.rollDegrees)
        assertEquals(0, calibration.pitchDegrees)
    }

    @Test
    fun `invalid points and angles are constrained to a non-folding quadrilateral`() {
        val safe = ElectronicMirrorCalibration(
            topLeft = ElectronicMirrorCalibrationPoint(-100, 990),
            topRight = ElectronicMirrorCalibrationPoint(10, 950),
            bottomRight = ElectronicMirrorCalibrationPoint(20, -10),
            bottomLeft = ElectronicMirrorCalibrationPoint(1500, 100),
            yawDegrees = 80,
            rollDegrees = -80,
            pitchDegrees = 30,
        ).sanitized()

        assertEquals(safe.topLeft.xPermille, safe.bottomLeft.xPermille)
        assertEquals(safe.topRight.xPermille, safe.bottomRight.xPermille)
        assertTrue(safe.topRight.xPermille - safe.topLeft.xPermille >= ElectronicMirrorCalibration.MIN_POINT_GAP)
        assertTrue(safe.bottomLeft.yPermille - safe.topLeft.yPermille >= ElectronicMirrorCalibration.MIN_POINT_GAP)
        assertTrue(safe.bottomRight.yPermille - safe.topRight.yPermille >= ElectronicMirrorCalibration.MIN_POINT_GAP)
        assertEquals(15, safe.yawDegrees)
        assertEquals(-15, safe.rollDegrees)
        assertEquals(15, safe.pitchDegrees)
    }

    @Test
    fun `dragging one corner keeps the matching side x linked`() {
        val changed = ElectronicMirrorCalibration.DEFAULT.withPoint(
            index = 0,
            point = ElectronicMirrorCalibrationPoint(240, 180),
        )

        assertEquals(240, changed.topLeft.xPermille)
        assertEquals(240, changed.bottomLeft.xPermille)
        assertEquals(180, changed.topLeft.yPermille)
        assertEquals(1000, changed.bottomLeft.yPermille)
    }
}
