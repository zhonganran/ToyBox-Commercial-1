package com.byd.wjx.electronicmirror

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ElectronicMirrorPreferencesTest {
    @Test fun `pip size keeps sixteen by nine aspect`() {
        val size = ElectronicMirrorPreferences.pipSize(1920, 36)
        assertEquals(691, size.width)
        assertEquals(388, size.height)
    }

    @Test fun `default pip sits at left edge and vertical center`() {
        val rect = ElectronicMirrorPreferences.defaultPipRect(1920, 1080, 36)
        assertEquals(0, rect.left)
        assertEquals((1080 - rect.height()) / 2, rect.top)
    }

    @Test fun `pip width is clamped to the supported range`() {
        assertEquals(200, ElectronicMirrorPreferences.pipSize(1000, 1).width)
        assertEquals(500, ElectronicMirrorPreferences.pipSize(1000, 99).width)
    }

    @Test fun `cluster bounds split into matching left and right regions`() {
        val (left, right) = ElectronicMirrorPreferences.clusterSideRects(
            android.graphics.Rect(100, 20, 1300, 500),
        )
        assertEquals(android.graphics.Rect(100, 20, 700, 500), left)
        assertEquals(android.graphics.Rect(700, 20, 1300, 500), right)
    }

    @Test fun `display target defaults to cluster and parses center`() {
        assertEquals(ElectronicMirrorDisplayTarget.CLUSTER, ElectronicMirrorDisplayTarget.fromStored(null))
        assertEquals(ElectronicMirrorDisplayTarget.CLUSTER, ElectronicMirrorDisplayTarget.fromStored("bad"))
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, ElectronicMirrorDisplayTarget.fromStored("center"))
    }

    @Test fun `center screen window stays inside the left half`() {
        assertEquals(269, ElectronicMirrorPreferences.centerLeftMaxX(1920, 691))
        assertEquals(0, ElectronicMirrorPreferences.centerLeftMaxX(1920, 1200))
    }

    @Test fun `legacy sixty percent width is read back as fifty percent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(ElectronicMirrorPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(ElectronicMirrorPreferences.KEY_PIP_WIDTH_PCT, 60)
            .commit()

        assertEquals(50, ElectronicMirrorPreferences(context).pipWidthPct)
    }
}
