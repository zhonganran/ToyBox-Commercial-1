package com.byd.wjx.cluster

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
class ClusterProjectionPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: ClusterProjectionPreferences

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ClusterProjectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        preferences = ClusterProjectionPreferences(context)
    }

    @Test fun `defaults are owned by one projection snapshot`() {
        val snapshot = preferences.snapshot()

        assertFalse(snapshot.enabled)
        assertEquals(DEFAULT_TRIGGER_KEYCODE, snapshot.triggerKeyCode)
        assertEquals(MAX_PROJECTION_PCT, snapshot.widthPct)
        assertEquals(MAX_PROJECTION_PCT, snapshot.heightPct)
        assertEquals(CENTER_OFFSET_PCT, snapshot.offsetXPct)
        assertEquals(CENTER_OFFSET_PCT, snapshot.offsetYPct)
        assertEquals(DEFAULT_SCALE_PCT, snapshot.scalePct)
        assertEquals(ProjectionLayout.INDEPENDENT, snapshot.layout)
        assertEquals(NAVI_PACKAGE, snapshot.targetPackage)
    }

    @Test fun `grouped writes are returned as one consistent snapshot`() {
        preferences.setEnabled(true)
        preferences.setTriggerKeyCode(99)
        preferences.setLayout(ProjectionLayout.DUPLICATE)
        preferences.setTarget("example.navigation", "Example")
        preferences.setGeometry(80, 70, 40, 60, 90)
        preferences.saveLastVirtualDisplayId(12)

        val snapshot = preferences.snapshot()
        assertTrue(snapshot.enabled)
        assertEquals(99, snapshot.triggerKeyCode)
        assertEquals(ProjectionLayout.DUPLICATE, snapshot.layout)
        assertEquals("example.navigation", snapshot.targetPackage)
        assertEquals("Example", snapshot.targetLabel)
        assertEquals(80, snapshot.widthPct)
        assertEquals(70, snapshot.heightPct)
        assertEquals(40, snapshot.offsetXPct)
        assertEquals(60, snapshot.offsetYPct)
        assertEquals(90, snapshot.scalePct)
        assertEquals(12, snapshot.lastVirtualDisplayId)

        preferences.clearLastVirtualDisplayId()
        assertEquals(ClusterProjectionPreferences.NO_VIRTUAL_DISPLAY, preferences.snapshot().lastVirtualDisplayId)
    }
}
