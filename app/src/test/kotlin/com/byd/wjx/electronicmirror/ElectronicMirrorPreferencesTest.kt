package com.byd.wjx.electronicmirror

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ElectronicMirrorPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: ElectronicMirrorPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ElectronicMirrorPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        preferences = ElectronicMirrorPreferences(context)
    }

    @Test
    fun `safe defaults enable turn views but not camera automation`() {
        val config = preferences.snapshot()

        assertFalse(config.enabled)
        assertTrue(config.leftTurnEnabled)
        assertTrue(config.rightTurnEnabled)
        assertFalse(config.reverseEnabled)
        assertEquals(ElectronicMirrorDisplayTarget.CLUSTER, config.displayTarget)
        assertEquals(ElectronicMirrorGeometry.DEFAULT_CLUSTER, config.clusterGeometry)
        assertEquals(ElectronicMirrorGeometry.DEFAULT_CENTER, config.centerGeometry)
        assertEquals(ElectronicMirrorCalibration.DEFAULT, config.leftCalibration)
        assertEquals(ElectronicMirrorCalibration.DEFAULT, config.rightCalibration)
        assertEquals(ElectronicMirrorCalibration.DEFAULT, config.rearCalibration)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_A, config.leftSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_B, config.rightSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, config.rearSource)
    }

    @Test
    fun `custom image order is persisted`() {
        val changed = ElectronicMirrorConfig()
            .withSource(ElectronicMirrorViewpoint.LEFT, ElectronicMirrorCameraSource.SOURCE_C)
            .withSource(ElectronicMirrorViewpoint.RIGHT, ElectronicMirrorCameraSource.SOURCE_A)

        preferences.save(changed)

        val restored = preferences.snapshot()
        assertEquals(changed.leftSource, restored.leftSource)
        assertEquals(changed.rightSource, restored.rightSource)
        assertEquals(changed.rearSource, restored.rearSource)
    }

    @Test
    fun `left right and rear keep independent calibration`() {
        val left = ElectronicMirrorCalibration(
            topLeft = ElectronicMirrorCalibrationPoint(100, 120),
            topRight = ElectronicMirrorCalibrationPoint(800, 80),
            bottomRight = ElectronicMirrorCalibrationPoint(800, 900),
            bottomLeft = ElectronicMirrorCalibrationPoint(100, 950),
            yawDegrees = -4,
        ).sanitized()
        val right = ElectronicMirrorCalibration(
            topLeft = ElectronicMirrorCalibrationPoint(180, 100),
            topRight = ElectronicMirrorCalibrationPoint(940, 140),
            bottomRight = ElectronicMirrorCalibrationPoint(940, 880),
            bottomLeft = ElectronicMirrorCalibrationPoint(180, 920),
            rollDegrees = 3,
        ).sanitized()
        val rear = ElectronicMirrorCalibration(
            topLeft = ElectronicMirrorCalibrationPoint(120, 160),
            topRight = ElectronicMirrorCalibrationPoint(880, 160),
            bottomRight = ElectronicMirrorCalibrationPoint(880, 840),
            bottomLeft = ElectronicMirrorCalibrationPoint(120, 840),
            pitchDegrees = 5,
        ).sanitized()

        preferences.save(
            ElectronicMirrorConfig(
                leftCalibration = left,
                rightCalibration = right,
                rearCalibration = rear,
            ),
        )

        val restored = preferences.snapshot()
        assertEquals(left, restored.leftCalibration)
        assertEquals(right, restored.rightCalibration)
        assertEquals(rear, restored.rearCalibration)
    }

    @Test
    fun `cluster and center keep independent geometry`() {
        val cluster = ElectronicMirrorGeometry(82, 91, 11, 22)
        val center = ElectronicMirrorGeometry(47, 58, 77, 66)
        preferences.save(
            ElectronicMirrorConfig(
                enabled = true,
                displayTarget = ElectronicMirrorDisplayTarget.CENTER,
                reverseEnabled = true,
                clusterGeometry = cluster,
                centerGeometry = center,
            ),
        )

        val restored = preferences.snapshot()
        assertTrue(restored.enabled)
        assertTrue(restored.reverseEnabled)
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, restored.displayTarget)
        assertEquals(cluster, restored.clusterGeometry)
        assertEquals(center, restored.centerGeometry)
    }

    @Test
    fun `invalid geometry and fps are clamped on save`() {
        preferences.save(
            ElectronicMirrorConfig(
                fps = 100,
                clusterGeometry = ElectronicMirrorGeometry(1, 200, -1, 101),
            ),
        )

        val restored = preferences.snapshot()
        assertEquals(30, restored.fps)
        assertEquals(ElectronicMirrorGeometry(24, 100, 0, 100), restored.clusterGeometry)
    }
}
