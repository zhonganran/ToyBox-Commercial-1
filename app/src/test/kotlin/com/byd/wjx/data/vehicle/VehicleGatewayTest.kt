package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.FidRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleGatewayTest {

    private val chargingBaseSpecs = listOf(
        VehicleReadSpec(5, 1009, FidRegistry.FID_GUN_CONNECT_STATE),
        VehicleReadSpec(5, 1009, FidRegistry.FID_CHARGING_TYPE),
        VehicleReadSpec(5, 1009, FidRegistry.FID_CHARGE_BATTERY_VOLT),
        VehicleReadSpec(5, 1009, FidRegistry.FID_BATTERY_TYPE),
        VehicleReadSpec(7, 1009, FidRegistry.FID_CHARGING_CAPACITY),
        VehicleReadSpec(5, 1009, FidRegistry.FID_CHARGING_BMS_STATE),
    )

    private val chargingElectricalSpecs = listOf(
        VehicleReadSpec(5, 1009, FidRegistry.FID_CHARGE_CURRENT),
        VehicleReadSpec(7, 1009, FidRegistry.FID_CHARGE_POWER),
        VehicleReadSpec(5, 1014, FidRegistry.FID_BATTERY_MAX_CHARGE_POWER),
        VehicleReadSpec(5, 1014, FidRegistry.FID_BATTERY_MAX_DISCHARGE_POWER),
        VehicleReadSpec(5, 1039, FidRegistry.FID_BATTERY_INSULATION),
        VehicleReadSpec(5, 1009, FidRegistry.FID_CHARGE_STOP_SOC),
    )

    @Test
    fun `helper batch is primary and avoids adb reads`() = runTest {
        val helper = mockk<HelperClient>()
        val specs = listOf(
            VehicleReadSpec(5, 1014, 1),
            VehicleReadSpec(7, 1014, 2),
        )
        coEvery { helper.readBatch(specs) } returns listOf(
            VehicleRawRead(0, 77),
            VehicleRawRead(0, java.lang.Float.floatToRawIntBits(88.5f)),
        )

        val result = HelperVehicleGateway(helper).readBatch(specs)

        assertNotNull(result)
        assertEquals(VehicleReadSource.HELPER_BINDER, result?.source)
        assertEquals(2, result?.acceptedCount)
    }

    @Test
    fun `missing helper protocol does not start a slow adb batch`() = runTest {
        val helper = mockk<HelperClient>()
        val intSpec = VehicleReadSpec(5, 1014, 1)
        val floatSpec = VehicleReadSpec(7, 1014, 2)
        val specs = listOf(intSpec, floatSpec)
        coEvery { helper.readBatch(specs) } returns null

        val result = HelperVehicleGateway(helper).readBatch(specs)

        assertEquals(null, result)
    }

    @Test
    fun `helper transport errors do not duplicate the same batch through adb`() = runTest {
        val helper = mockk<HelperClient>()
        val specs = listOf(
            VehicleReadSpec(5, 1014, 1),
            VehicleReadSpec(7, 1014, 2),
        )
        coEvery { helper.readBatch(specs) } returns listOf(
            VehicleRawRead(-1, 0),
            VehicleRawRead(-1, 0),
        )

        val result = HelperVehicleGateway(helper).readBatch(specs)

        assertEquals(VehicleReadSource.HELPER_BINDER, result?.source)
        assertEquals(0, result?.acceptedCount)
    }

    @Test
    fun `charging page request reads electrical diagnostics after active charging is confirmed`() = runTest {
        val helper = mockk<HelperClient>()
        coEvery { helper.readBatch(chargingBaseSpecs) } returns listOf(
            VehicleRawRead(0, 2),
            VehicleRawRead(0, 2),
            VehicleRawRead(0, 420),
            VehicleRawRead(0, 1),
            VehicleRawRead(0, java.lang.Float.floatToRawIntBits(8.25f)),
            VehicleRawRead(0, 1),
        )
        coEvery { helper.readBatch(chargingElectricalSpecs) } returns listOf(
            VehicleRawRead(0, 16),
            VehicleRawRead(0, java.lang.Float.floatToRawIntBits(6.7f)),
            VehicleRawRead(0, 120),
            VehicleRawRead(0, 180),
            VehicleRawRead(0, 850),
            VehicleRawRead(0, 90),
        )

        val reading = HelperVehicleGateway(helper)
            .readChargingSnapshot(includeElectricalTelemetry = true)

        assertEquals(16, reading?.chargeCurrentA)
        assertEquals(6.7f, reading?.chargePowerKw)
        assertEquals(120, reading?.batteryMaxChargePowerKw)
        assertEquals(180, reading?.batteryMaxDischargePowerKw)
        assertEquals(850, reading?.batteryInsulationKohm)
        assertEquals(90, reading?.chargeStopSoc)
        coVerify(exactly = 1) { helper.readBatch(chargingElectricalSpecs) }
    }

    @Test
    fun `background charging snapshot does not add electrical telemetry reads`() = runTest {
        val helper = mockk<HelperClient>()
        coEvery { helper.readBatch(chargingBaseSpecs) } returns listOf(
            VehicleRawRead(0, 2),
            VehicleRawRead(0, 2),
            VehicleRawRead(0, 420),
            VehicleRawRead(0, 1),
            VehicleRawRead(0, java.lang.Float.floatToRawIntBits(8.25f)),
            VehicleRawRead(0, 1),
        )

        val reading = HelperVehicleGateway(helper).readChargingSnapshot()

        assertNull(reading?.chargeCurrentA)
        assertNull(reading?.chargePowerKw)
        coVerify(exactly = 0) { helper.readBatch(chargingElectricalSpecs) }
    }

    @Test
    fun `disconnected charging page request skips current and power fids`() = runTest {
        val helper = mockk<HelperClient>()
        coEvery { helper.readBatch(chargingBaseSpecs) } returns listOf(
            VehicleRawRead(0, 1),
            VehicleRawRead(0, 1),
            VehicleRawRead(0, 0),
            VehicleRawRead(0, 1),
            VehicleRawRead(-1, 0),
            VehicleRawRead(0, 0),
        )

        val reading = HelperVehicleGateway(helper)
            .readChargingSnapshot(includeElectricalTelemetry = true)

        assertNull(reading?.chargeCurrentA)
        assertNull(reading?.chargePowerKw)
        coVerify(exactly = 0) { helper.readBatch(chargingElectricalSpecs) }
    }
}
