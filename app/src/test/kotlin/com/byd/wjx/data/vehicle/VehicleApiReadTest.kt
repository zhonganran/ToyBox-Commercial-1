package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleApiReadTest {

    private val parsReader: ParsReader = mockk()
    private val gateway: VehicleGateway = mockk()
    private val allowlist = WriteAllowlist.EMPTY
    private val writeLogDao: VehicleWriteLogDao = mockk(relaxed = true)
    private val api: VehicleApi = VehicleApiImpl(parsReader, gateway, allowlist, writeLogDao)

    @Test fun `readSnapshot delegates to ParsReader fetch`() = runTest {
        val expected = diParsData(soc = 73, speed = 0)
        coEvery { parsReader.fetch() } returns expected
        assertEquals(expected, api.readSnapshot())
    }

    @Test fun `readSnapshot returns null when reader returns null`() = runTest {
        coEvery { parsReader.fetch() } returns null
        assertNull(api.readSnapshot())
    }

    @Test fun `readBatterySnapshot delegates to VehicleGateway`() = runTest {
        val br = BatteryReading(
            sohPercent = 100f, socPercent = 73.5f,
            lifetimeKwh = 1234f, lifetimeMileageKm = 5678f,
            voltage12v = 12.7f, readAtMs = 1L
        )
        coEvery { gateway.readBatterySnapshot() } returns br
        assertEquals(br, api.readBatterySnapshot())
    }

}
