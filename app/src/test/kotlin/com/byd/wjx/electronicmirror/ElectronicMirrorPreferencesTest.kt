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

    @Test fun `default pip sits at right edge and vertical center`() {
        val rect = ElectronicMirrorPreferences.defaultPipRect(1920, 1080, 36)
        assertEquals(1920 - rect.width(), rect.left)
        assertEquals((1080 - rect.height()) / 2, rect.top)
    }

    @Test fun `pip width is clamped to the supported range`() {
        assertEquals(200, ElectronicMirrorPreferences.pipSize(1000, 1).width)
        assertEquals(600, ElectronicMirrorPreferences.pipSize(1000, 99).width)
    }

    @Test fun `stored width is clamped to sixty percent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(ElectronicMirrorPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(ElectronicMirrorPreferences.KEY_PIP_WIDTH_PCT, 99)
            .commit()

        assertEquals(60, ElectronicMirrorPreferences(context).pipWidthPct)
    }
}
