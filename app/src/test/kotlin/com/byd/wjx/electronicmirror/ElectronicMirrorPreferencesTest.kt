package com.byd.wjx.electronicmirror

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.vehicle.VehicleIdentityResolver
import com.byd.wjx.vehicle.VehicleMirrorProfile
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
        setVehicleIdentityProperties()
    }

    @Test
    fun `verified remote profile applies until a local manual choice overrides it`() {
        val profile = testRemoteProfile(cameraId = 3)
        assertTrue(preferences.installRemoteProfile(profile))

        val automatic = preferences.snapshot()
        assertEquals(3, automatic.cameraIdOverride)
        assertEquals("verified-test", automatic.remoteProfileId)
        assertFalse(automatic.manualProfileOverride)

        preferences.save(
            automatic.copy(
                cameraIdOverride = 1,
                manualProfileOverride = true,
            ),
        )
        val manual = preferences.snapshot()
        assertEquals(1, manual.cameraIdOverride)
        assertTrue(manual.manualProfileOverride)

        preferences.restoreAutomaticProfile()
        val restored = preferences.snapshot()
        assertEquals(3, restored.cameraIdOverride)
        assertFalse(restored.manualProfileOverride)
    }

    @Test
    fun `remote profile with another compatibility key is ignored`() {
        preferences.installRemoteProfile(testRemoteProfile(cameraId = 2).copy(compatibilityKey = "0".repeat(64)))

        val config = preferences.snapshot()

        assertEquals(ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID, config.cameraIdOverride)
        assertEquals("", config.remoteProfileId)
    }

    @Test
    fun `clearing a disabled remote profile returns automatic mode to local defaults`() {
        preferences.installRemoteProfile(testRemoteProfile(cameraId = 2))
        assertEquals(2, preferences.snapshot().cameraIdOverride)

        assertTrue(preferences.clearRemoteProfile())

        val config = preferences.snapshot()
        assertEquals(ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID, config.cameraIdOverride)
        assertEquals("", config.remoteProfileId)
    }

    private fun testRemoteProfile(cameraId: Int): VehicleMirrorProfile = VehicleMirrorProfile(
        profileId = "verified-test",
        version = 2,
        compatibilityKey = VehicleIdentityResolver.currentCompatibilityKey(),
        cameraId = cameraId,
        surfaceIndex = 0,
        leftSource = ElectronicMirrorCameraSource.SOURCE_A,
        rightSource = ElectronicMirrorCameraSource.SOURCE_B,
        rearSource = ElectronicMirrorCameraSource.SOURCE_C,
        updatedAt = "2026-07-23T12:00:00Z",
    )

    private fun setVehicleIdentityProperties() {
        val properties = mapOf(
            "persist.sys.byd.default_name" to "宋 PLUS DM-i",
            "persist.sys.byd.bluetooth_name" to "",
            "persist.sys.model_variant.model" to "SA3HEV",
            "persist.sys.vehicle_40d_code" to "BYD-S3",
            "ro.build.car.series" to "SONG_PLUS",
            "ro.vehicle.type" to "PHEV",
            "apps.setting.product.outswver" to "57.1.3.2501010.1",
            "vendor.vehicle.config.cam_sort" to "rear,left,right,front",
        )
        val type = Class.forName("android.os.SystemProperties")
        val setter = type.getDeclaredMethod("set", String::class.java, String::class.java)
        properties.forEach { (name, value) -> setter.invoke(null, name, value) }
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
        assertEquals(ElectronicMirrorConfig.MAX_FPS, config.fps)
        assertEquals(ElectronicMirrorSurfaceProfile.MAX_QUALITY_WIDTH, config.qualityWidth)
        assertEquals(ElectronicMirrorDisplayTarget.CLUSTER, config.displayTarget)
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
        assertEquals(ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID, config.cameraIdOverride)
        assertEquals(ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX, config.panoramaSurfaceIndex)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_A, config.leftSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_B, config.rightSource)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C, config.rearSource)
    }

    @Test
    fun `legacy quality preferences are ignored and cleared on save`() {
        val raw = context.getSharedPreferences(
            ElectronicMirrorPreferences.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        raw.edit()
            .putInt("fps", 12)
            .putInt("quality_width", 480)
            .commit()

        val restored = preferences.snapshot()
        assertEquals(ElectronicMirrorConfig.MAX_FPS, restored.fps)
        assertEquals(ElectronicMirrorSurfaceProfile.MAX_QUALITY_WIDTH, restored.qualityWidth)

        preferences.save(restored)
        assertFalse(raw.contains("fps"))
        assertFalse(raw.contains("quality_width"))
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
                cameraIdOverride = 3,
                panoramaSurfaceIndex = 3,
            ),
        )

        val restored = preferences.snapshot()
        assertTrue(restored.streamingRearEnabled)
        assertFalse(restored.streamingRearVisible)
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, restored.streamingRearDisplayTarget)
        assertEquals(cluster, restored.streamingRearClusterGeometry)
        assertEquals(center, restored.streamingRearCenterGeometry)
        assertEquals(3, restored.cameraIdOverride)
        assertEquals(3, restored.panoramaSurfaceIndex)
        assertTrue(preferences.isAnyEnabled())
    }

    @Test
    fun `invalid geometry fps and quality are clamped on save`() {
        preferences.save(
            ElectronicMirrorConfig(
                fps = 100,
                qualityWidth = 4_000,
                clusterGeometry = ElectronicMirrorGeometry(1, 200, -1, 101),
                cameraIdOverride = -8,
                panoramaSurfaceIndex = 99,
            ),
        )

        val restored = preferences.snapshot()
        assertEquals(30, restored.fps)
        assertEquals(ElectronicMirrorSurfaceProfile.MAX_QUALITY_WIDTH, restored.qualityWidth)
        assertEquals(ElectronicMirrorGeometry(16, 80, 0, 100), restored.clusterGeometry)
        assertEquals(ElectronicMirrorSurfaceProfile.AUTO_CAMERA_ID, restored.cameraIdOverride)
        assertEquals(ElectronicMirrorSurfaceProfile.MAX_SURFACE_INDEX, restored.panoramaSurfaceIndex)
    }
}
