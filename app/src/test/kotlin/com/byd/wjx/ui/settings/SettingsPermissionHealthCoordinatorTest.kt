package com.byd.wjx.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.data.system.SettingsPermissionHealthCoordinator
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsPermissionHealthCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `automatic repair retries every shell-owned authorization`() = runTest {
        val adb = mockk<AdbOnDeviceClient>(relaxed = true)
        val bootstrap = mockk<HelperBootstrap>()
        val helper = mockk<HelperClient>(relaxed = true)
        coEvery { adb.connect() } returns Result.success(Unit)
        coEvery { bootstrap.ensureCurrent() } returns true
        coEvery { adb.isConnected() } returns true
        coEvery { helper.isAlive() } returns true
        val coordinator = SettingsPermissionHealthCoordinator(context, adb, bootstrap, helper)

        val result = coordinator.repairAndInspect()

        assertTrue(result.items.any { it.id == "adb" && it.ready })
        assertTrue(result.items.any { it.id == "helper" && it.ready })
        assertTrue(result.items.any { item ->
            item.id == "runtime" && !item.ready && item.openAppPermissionSettings
        })
        coVerify(exactly = 1) { adb.grantUsageStatsAppop(context.packageName) }
        coVerify(exactly = 1) { adb.grantMusicNotificationListenerAccess() }
        coVerify(exactly = 1) { helper.grantOverlayPermission() }
        coVerify(exactly = 1) { helper.enableAccessibilityService() }
    }
}
