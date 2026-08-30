package com.byd.wjx.adbrescue

import android.content.Context
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.HelperShellResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbRescueRuntimeCoordinatorTest {
    @Test
    fun `healthy values skip helper and extra writes`() = runTest {
        val fixture = fixture()
        every { fixture.manager.inspect("healthy:inspect", runWriteTest = false) } returns report(
            permission = true,
            allEnabled = true,
        )

        val outcome = fixture.coordinator.ensureEnabled("healthy")

        assertTrue(outcome.report.allModesEnabled)
        coVerify(exactly = 0) { fixture.helperClient.isAlive() }
        coVerify(exactly = 0) { fixture.helperBootstrap.ensureCurrent() }
        verify(exactly = 0) { fixture.manager.enableBoth(any(), any()) }
    }

    @Test
    fun `missing permission and rejected direct properties use fixed helper repairs`() = runTest {
        val fixture = fixture()
        val initial = report(permission = false, allEnabled = false)
        val direct = report(permission = true, allEnabled = false)
        val repaired = report(permission = true, allEnabled = true)
        every { fixture.manager.inspect("repair:inspect", runWriteTest = false) } returns initial
        every { fixture.manager.hasWriteSecureSettingsPermission() } returns true
        every { fixture.manager.enableBoth("repair:direct", runWriteTest = false) } returns direct
        every { fixture.manager.inspect("repair:helper_readback", runWriteTest = false) } returns repaired
        coEvery { fixture.helperClient.isAlive() } returns true
        coEvery { fixture.helperClient.execShellResult(any()) } returns HelperShellResult(0, 0, "")

        val outcome = fixture.coordinator.ensureEnabled("repair")

        assertTrue(outcome.permissionGrantAttempted)
        assertTrue(outcome.permissionGrantSucceeded)
        assertTrue(outcome.helperModeRepairAttempted)
        assertTrue(outcome.helperModeRepairSucceeded)
        assertTrue(outcome.report.allModesEnabled)
        coVerify(exactly = 1) {
            fixture.helperClient.execShellResult(
                match { it.contains("pm grant --user") && it.contains("WRITE_SECURE_SETTINGS") },
            )
        }
        coVerify(exactly = 1) {
            fixture.helperClient.execShellResult(AdbRescueShellCommands.ENABLE_BOTH_MODES)
        }
    }

    private fun fixture(): Fixture {
        val context = mockk<Context>()
        every { context.packageName } returns "com.byd.wjx"
        val manager = mockk<AdbRescueManager>()
        val helperBootstrap = mockk<HelperBootstrap>()
        val helperClient = mockk<HelperClient>()
        return Fixture(
            coordinator = AdbRescueRuntimeCoordinator(
                context,
                manager,
                helperBootstrap,
                helperClient,
            ),
            manager = manager,
            helperBootstrap = helperBootstrap,
            helperClient = helperClient,
        )
    }

    private fun report(permission: Boolean, allEnabled: Boolean): AdbRescueReport {
        val enabled = value(enabled = allEnabled)
        return AdbRescueReport(
            trigger = "test",
            userId = 10,
            uid = 1_000_001,
            writeSecureSettingsGranted = permission,
            standardAdb = enabled.copy(key = AdbRescueManager.ADB_ENABLED),
            bydWirelessEnabled = enabled.copy(key = AdbRescueManager.BYD_WIRELESS_ENABLED),
            bydWirelessConnection = enabled.copy(key = AdbRescueManager.BYD_WIRELESS_CONNECTION),
            settingsWriteTestSucceeded = permission,
        )
    }

    private fun value(enabled: Boolean) = AdbRescueValueResult(
        key = "key",
        desiredValue = "1",
        before = if (enabled) "1" else "0",
        writeAttempted = false,
        writeAccepted = null,
        after = if (enabled) "1" else "0",
        enabled = enabled,
    )

    private data class Fixture(
        val coordinator: AdbRescueRuntimeCoordinator,
        val manager: AdbRescueManager,
        val helperBootstrap: HelperBootstrap,
        val helperClient: HelperClient,
    )
}
