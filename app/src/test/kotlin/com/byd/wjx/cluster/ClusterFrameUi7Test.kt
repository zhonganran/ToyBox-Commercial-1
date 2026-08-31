package com.byd.wjx.cluster

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.vehicle.HelperClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClusterFrameUi7Test {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ClusterFrameUi7.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `frame remains held until projection and camera both release it`() = runTest {
        val prefs = context.getSharedPreferences(ClusterFrameUi7.PREFS_NAME, Context.MODE_PRIVATE)
        val helper = mockk<HelperClient>()
        coEvery { helper.read(ClusterFrameUi7.DEV_INSTRUMENT, ClusterFrameUi7.FID_CENTER) } returnsMany
            listOf(7L, ClusterFrameUi7.CENTER_MAP.toLong())
        coEvery { helper.read(ClusterFrameUi7.DEV_INSTRUMENT, ClusterFrameUi7.FID_LEFT) } returns 0L
        coEvery { helper.read(ClusterFrameUi7.DEV_INSTRUMENT, ClusterFrameUi7.FID_MENU_STATUS) } returns 1L
        coEvery { helper.writeStatus(any(), any(), any()) } returns 1
        val frame = ClusterFrameUi7(prefs, platformized = { true })

        assertTrue(frame.apply(helper, ClusterFrameUi7.Owner.CAMERA))
        assertTrue(frame.apply(helper, ClusterFrameUi7.Owner.PROJECTION))
        frame.restore(helper, ClusterFrameUi7.Owner.CAMERA)
        coVerify(exactly = 0) {
            helper.writeStatus(
                ClusterFrameUi7.DEV_INSTRUMENT,
                ClusterFrameUi7.FID_CENTER,
                7,
            )
        }

        frame.restore(helper, ClusterFrameUi7.Owner.PROJECTION)

        coVerify(exactly = 1) {
            helper.writeStatus(
                ClusterFrameUi7.DEV_INSTRUMENT,
                ClusterFrameUi7.FID_CENTER,
                7,
            )
        }
        assertFalse(frame.hasStaleFrame())
    }

    @Test
    fun `service start restores persisted stock frame`() = runTest {
        val prefs = context.getSharedPreferences(ClusterFrameUi7.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(ClusterFrameUi7.KEY_SAVED_CENTER, 7)
            .putInt(ClusterFrameUi7.KEY_SAVED_LEFT, 0)
            .putInt(ClusterFrameUi7.KEY_SAVED_MENU, 1)
            .commit()
        val helper = mockk<HelperClient>()
        coEvery { helper.read(ClusterFrameUi7.DEV_INSTRUMENT, ClusterFrameUi7.FID_CENTER) } returns
            ClusterFrameUi7.CENTER_MAP.toLong()
        coEvery { helper.writeStatus(any(), any(), any()) } returns 1
        val frame = ClusterFrameUi7(prefs, platformized = { true })

        frame.recoverStale(helper)

        coVerify {
            helper.writeStatus(
                ClusterFrameUi7.DEV_INSTRUMENT,
                ClusterFrameUi7.FID_CENTER,
                7,
            )
        }
        assertFalse(frame.hasStaleFrame())
    }
}
