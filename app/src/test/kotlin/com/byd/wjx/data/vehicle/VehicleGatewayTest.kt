package com.byd.wjx.data.vehicle

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VehicleGatewayTest {

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
}
