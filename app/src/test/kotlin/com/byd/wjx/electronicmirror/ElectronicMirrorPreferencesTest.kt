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
        assertFalse(config.streamingRearEnabled)
        assertTrue(config.streamingRearVisible)
        assertEquals(20, config.fps)
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, config.displayTarget)
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, config.streamingRearDisplayTarget)
        assertEquals(ElectronicMirrorGeometry.DEFAULT_CLUSTER, config.clusterGeometry)
        assertEquals(ElectronicMirrorGeometry.DEFAULT_CENTER, config.centerGeometry)
        assertEquals(
            ElectronicMirrorGeometry.DEFAULT_STREAMING_CLUSTER,
            config.streamingRearClusterGeometry,
        )
        assertEquals(
            ElectronicMirrorGeometry.DEFAULT_STREAMING_CENTER,
            config.streamingRearCenterGeometry,
        )
        assertEquals(ElectronicMirrorProjectionSpec.DEFAULT_LEFT, config.leftProjection)
        assertEquals(ElectronicMirrorProjectionSpec.DEFAULT_RIGHT, config.rightProjection)
        assertEquals(ElectronicMirrorProjectionSpec.DEFAULT_REAR, config.rearProjection)
        assertEquals(ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX, config.panoramaSurfaceIndex)
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
    fun `left right and rear keep independent projection`() {
        val left = ElectronicMirrorProjectionSpec(
            horizontalPermille = -120,
            heightOffsetPermille = 40,
            viewScalePermille = 1100,
        )
        val right = ElectronicMirrorProjectionSpec(
            horizontalPermille = 150,
            heightOffsetPermille = -30,
            viewScalePermille = 1200,
        )
        val rear = ElectronicMirrorProjectionSpec(
            horizontalPermille = 20,
            heightOffsetPermille = 90,
            viewScalePermille = 1450,
        )

        preferences.save(
            ElectronicMirrorConfig(
                leftProjection = left,
                rightProjection = right,
                rearProjection = rear,
            ),
        )

        val restored = preferences.snapshot()
        assertEquals(left, restored.leftProjection)
        assertEquals(right, restored.rightProjection)
        assertEquals(rear, restored.rearProjection)
    }

    @Test
    fun `cluster and center keep independent geometry`() {
        val cluster = ElectronicMirrorGeometry(42, 72, 11, 22)
        val center = ElectronicMirrorGeometry(47, 58, 37, 66)
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
    fun `streaming rear state and both target geometries are persisted`() {
        val cluster = ElectronicMirrorGeometry(90, 44, 48, 3)
        val center = ElectronicMirrorGeometry(62, 32, 51, 7)
        preferences.save(
            ElectronicMirrorConfig(
                streamingRearEnabled = true,
                streamingRearVisible = false,
                streamingRearDisplayTarget = ElectronicMirrorDisplayTarget.CENTER,
                streamingRearClusterGeometry = cluster,
                streamingRearCenterGeometry = center,
                panoramaSurfaceIndex = 3,
            ),
        )

        val restored = preferences.snapshot()
        assertTrue(restored.streamingRearEnabled)
        assertFalse(restored.streamingRearVisible)
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, restored.streamingRearDisplayTarget)
        assertEquals(cluster, restored.streamingRearClusterGeometry)
        assertEquals(center, restored.streamingRearCenterGeometry)
        assertEquals(3, restored.panoramaSurfaceIndex)
        assertTrue(preferences.isAnyEnabled())
    }

    @Test
    fun `invalid geometry and fps are clamped on save`() {
        preferences.save(
            ElectronicMirrorConfig(
                fps = 100,
                clusterGeometry = ElectronicMirrorGeometry(1, 200, -1, 101),
                panoramaSurfaceIndex = 99,
            ),
        )

        val restored = preferences.snapshot()
        assertEquals(30, restored.fps)
        assertEquals(ElectronicMirrorGeometry(16, 80, 0, 100), restored.clusterGeometry)
        assertEquals(ElectronicMirrorSurfaceProfile.MAX_SURFACE_INDEX, restored.panoramaSurfaceIndex)
    }
}
