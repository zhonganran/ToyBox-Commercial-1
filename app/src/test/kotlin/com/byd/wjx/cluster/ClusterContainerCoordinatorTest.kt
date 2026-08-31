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
class ClusterContainerCoordinatorTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ClusterContainerCoordinator.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `last owner alone powers the compositor down`() = runTest {
        val prefs = context.getSharedPreferences(ClusterContainerCoordinator.PREFS_NAME, Context.MODE_PRIVATE)
        val helper = mockk<HelperClient>()
        coEvery { helper.setClusterContainerMode(any()) } returns true
        val coordinator = ClusterContainerCoordinator(prefs)

        assertTrue(coordinator.setOwner(helper, ClusterContainerCoordinator.Owner.CAMERA, true))
        assertTrue(coordinator.setOwner(helper, ClusterContainerCoordinator.Owner.PROJECTION, true))
        assertTrue(coordinator.setOwner(helper, ClusterContainerCoordinator.Owner.CAMERA, false))
        assertTrue(prefs.getBoolean(ClusterContainerCoordinator.KEY_POWERED, false))
        assertTrue(coordinator.setOwner(helper, ClusterContainerCoordinator.Owner.PROJECTION, false))

        coVerify(exactly = 1) { helper.setClusterContainerMode(true) }
        coVerify(exactly = 1) { helper.setClusterContainerMode(false) }
        assertFalse(prefs.contains(ClusterContainerCoordinator.KEY_POWERED))
    }

    @Test
    fun `service start clears a stale powered marker`() = runTest {
        val prefs = context.getSharedPreferences(ClusterContainerCoordinator.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ClusterContainerCoordinator.KEY_POWERED, true).commit()
        val helper = mockk<HelperClient>()
        coEvery { helper.setClusterContainerMode(false) } returns true
        val coordinator = ClusterContainerCoordinator(prefs)

        coordinator.recoverStale(helper)

        coVerify(exactly = 1) { helper.setClusterContainerMode(false) }
        assertFalse(prefs.contains(ClusterContainerCoordinator.KEY_POWERED))
    }
}
