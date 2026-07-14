package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.helper.HelperBinderProtocol
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperBootstrapTest {

    @Test
    fun `vehicle read readiness keeps current helper when batch protocol responds`() = runTest {
        val helper = mockk<HelperClient>()
        val adb = mockk<AdbOnDeviceClient>(relaxed = true)
        coEvery { helper.isAlive() } returns true
        coEvery { helper.protocolVersion() } returns HelperBinderProtocol.CURRENT_VERSION
        coEvery { helper.readBatch(VehicleGateway.AVAILABILITY_PROBES) } returns probeReply()

        val bootstrap = HelperBootstrap(adb, helper)
        val ready = bootstrap.ensureVehicleReadsReady()
        val cachedReady = bootstrap.ensureCurrent()

        assertTrue(ready)
        assertTrue(cachedReady)
        coVerify(exactly = 0) { adb.spawnHelper() }
        coVerify(exactly = 1) { helper.protocolVersion() }
        coVerify(exactly = 1) { helper.readBatch(VehicleGateway.AVAILABILITY_PROBES) }
    }

    @Test
    fun `vehicle read readiness restarts alive helper with old version`() = runTest {
        val helper = mockk<HelperClient>()
        val adb = mockk<AdbOnDeviceClient>(relaxed = true)
        coEvery { helper.isAlive() } returns true
        coEvery { adb.spawnHelper() } returns true
        coEvery { helper.protocolVersion() } returnsMany listOf(
            0,
            HelperBinderProtocol.CURRENT_VERSION,
        )
        coEvery { helper.readBatch(VehicleGateway.AVAILABILITY_PROBES) } returns probeReply()

        val ready = HelperBootstrap(adb, helper).ensureVehicleReadsReady()

        assertTrue(ready)
        coVerify(exactly = 1) { adb.spawnHelper() }
    }

    @Test
    fun `vehicle read readiness restarts current helper when batch protocol is incomplete`() = runTest {
        val helper = mockk<HelperClient>()
        val adb = mockk<AdbOnDeviceClient>(relaxed = true)
        coEvery { helper.isAlive() } returns true
        coEvery { adb.spawnHelper() } returns true
        coEvery { helper.protocolVersion() } returns HelperBinderProtocol.CURRENT_VERSION
        coEvery { helper.readBatch(VehicleGateway.AVAILABILITY_PROBES) } returnsMany listOf(
            null,
            probeReply(),
        )

        val ready = HelperBootstrap(adb, helper).ensureVehicleReadsReady()

        assertTrue(ready)
        coVerify(exactly = 1) { adb.spawnHelper() }
    }

    private fun probeReply(): List<VehicleRawRead> =
        VehicleGateway.AVAILABILITY_PROBES.map { VehicleRawRead(status = 0, value = 1) }
}
