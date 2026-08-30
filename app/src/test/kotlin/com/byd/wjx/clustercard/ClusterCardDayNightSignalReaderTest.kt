package com.byd.wjx.clustercard

import com.byd.wjx.data.vehicle.FakeVehicleGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClusterCardDayNightSignalReaderTest {
    @Test
    fun `reader uses original lighting day night status address`() = runTest {
        val gateway = FakeVehicleGateway().apply {
            intValues[
                ClusterCardDayNightSignalReader.LIGHTS_DEVICE to
                    ClusterCardDayNightSignalReader.LIGHT_DAY_NIGHT_MODE_FID
            ] = 2
        }
        val reader = ClusterCardDayNightSignalReader(gateway)

        assertEquals(2, reader.readRawMode())
        assertEquals(listOf(ClusterCardDayNightSignalReader.READ_SPEC), gateway.lastBatchSpecs)
    }

    @Test
    fun `reader returns null when vehicle signal is unavailable`() = runTest {
        val reader = ClusterCardDayNightSignalReader(FakeVehicleGateway(available = false))

        assertNull(reader.readRawMode())
    }
}
