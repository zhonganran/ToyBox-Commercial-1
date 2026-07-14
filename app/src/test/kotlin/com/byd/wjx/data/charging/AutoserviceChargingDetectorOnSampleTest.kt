package com.byd.wjx.data.charging

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.repository.BatteryHealthRepository
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.FakeVehicleGateway
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoserviceChargingDetectorOnSampleTest {
    @Test fun `onSample caches the latest DiParsData`() {
        val detector = AutoserviceChargingDetector(
            gateway = FakeVehicleGateway(),
            chargeRepo = mockk(relaxed = true),
            batteryHealthRepo = mockk(relaxed = true),
            stateStore = mockk(relaxed = true),
            classifier = mockk(relaxed = true),
            settings = mockk(relaxed = true),
            parsReader = mockk<ParsReader>(relaxed = true),
            journal = mockk(relaxed = true),
        )

        val sample = diParsData(soc = 85)
        detector.onSample(sample)
        // private field — assert via reflection
        val field = AutoserviceChargingDetector::class.java.getDeclaredField("lastSample")
        field.isAccessible = true
        assertEquals(sample, field.get(detector))
    }
}
