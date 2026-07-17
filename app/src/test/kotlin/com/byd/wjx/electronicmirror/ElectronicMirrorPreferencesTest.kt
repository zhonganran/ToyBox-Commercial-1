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

