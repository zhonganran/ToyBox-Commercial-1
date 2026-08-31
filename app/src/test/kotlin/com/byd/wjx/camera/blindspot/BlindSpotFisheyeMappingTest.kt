package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotFisheyeMappingTest {
    @Test
    fun `stock rectilinear mapping matches captured golden points`() {
        val calibration = BlindSpotPreferences.defaultCalibration().copy(
            dewarpFovDegrees = 100,
            dewarpProjection = BlindSpotDewarpProjection.RECTILINEAR,
        )

        val center = mapFisheyeOutputToSource(calibration, 1920, 1300, 0.5, 0.5)
        val topLeft = mapFisheyeOutputToSource(calibration, 1920, 1300, 0.0, 0.0)
        val bottomRight = mapFisheyeOutputToSource(calibration, 1920, 1300, 1.0, 1.0)

        assertEquals(960.0, center[0], EPSILON)
        assertEquals(650.0, center[1], EPSILON)
        assertEquals(556.425604, topLeft[0], EPSILON)
        assertEquals(376.746503, topLeft[1], EPSILON)
        assertEquals(1920.0, topLeft[0] + bottomRight[0], EPSILON)
        assertEquals(1300.0, topLeft[1] + bottomRight[1], EPSILON)
    }

    @Test
    fun `stock cylindrical mapping matches wide angle golden edge`() {
        val calibration = BlindSpotPreferences.defaultCalibration().copy(
            dewarpFovDegrees = 100,
            dewarpProjection = BlindSpotDewarpProjection.CYLINDRICAL,
        )

        val left = mapFisheyeOutputToSource(calibration, 1920, 1300, 0.0, 0.5)
        val right = mapFisheyeOutputToSource(calibration, 1920, 1300, 1.0, 0.5)

        assertEquals(526.971629, left[0], EPSILON)
        assertEquals(650.0, left[1], EPSILON)
        assertEquals(1920.0, left[0] + right[0], EPSILON)
    }

    @Test
    fun `off center optical axis becomes output center`() {
        val calibration = BlindSpotPreferences.defaultCalibration().copy(
            dewarpFovDegrees = 120,
            dewarpCenterXPct = 37,
            dewarpCenterYPct = 64,
        )

        val center = mapFisheyeOutputToSource(calibration, 1920, 1300, 0.5, 0.5)

        assertEquals(1920.0 * 0.37, center[0], 0.0001)
        assertEquals(1300.0 * 0.64, center[1], 0.0001)
    }

    @Test
    fun `gpu mesh is finite indexed and nonempty at production defaults`() {
        val mesh = buildBlindSpotDewarpMesh(
            BlindSpotPreferences.defaultCalibration(),
            1920,
            720,
        )

        assertEquals(97 * 65 * 4, mesh.vertices.size)
        assertTrue(mesh.indices.isNotEmpty())
        assertTrue(mesh.vertices.all(Float::isFinite))
        assertTrue(mesh.indices.all { it.toInt() in 0 until 97 * 65 })
    }

    @Test
    fun `disabled correction uses identity mesh`() {
        val mesh = buildBlindSpotDewarpMesh(
            BlindSpotPreferences.defaultCalibration().copy(dewarpEnabled = false),
            1920,
            720,
        )

        assertEquals(16, mesh.vertices.size)
        assertEquals(6, mesh.indices.size)
        assertEquals(-1f, mesh.vertices[0], 0f)
        assertEquals(1f, mesh.vertices[15], 0f)
    }

    private companion object {
        const val EPSILON = 0.000001
    }
}
