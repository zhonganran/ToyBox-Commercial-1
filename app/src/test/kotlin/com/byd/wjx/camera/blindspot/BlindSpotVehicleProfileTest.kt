package com.byd.wjx.camera.blindspot

import com.byd.wjx.vehicle.CarProfileDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotVehicleProfileTest {
    @Test
    fun `known controller series uses the common vehicle reads`() {
        val profile = BlindSpotVehicleProfileResolver(
            detector = CarProfileDetector(propertyReader = { property ->
                if (property == "apps.setting.product.outswver") "34.2.1" else ""
            }),
        ).resolve()

        assertEquals(34, profile.controllerSeries)
        assertTrue(profile.id.startsWith("byd-common-"))
        assertEquals(BlindSpotVehicleProfileResolver.COMMON_PROFILE.coreReads, profile.coreReads)
        assertEquals(BlindSpotVehicleProfileResolver.COMMON_PROFILE.bsdReads, profile.bsdReads)
    }

    @Test
    fun `unknown controller series also uses the common vehicle reads`() {
        val profile = BlindSpotVehicleProfileResolver(
            detector = CarProfileDetector(propertyReader = { "unknown" }),
        ).resolve()

        assertEquals(0, profile.controllerSeries)
        assertTrue(profile.id.startsWith("byd-common-"))
        assertEquals(BlindSpotVehicleProfileResolver.COMMON_PROFILE.coreReads, profile.coreReads)
        assertEquals(BlindSpotVehicleProfileResolver.COMMON_PROFILE.bsdReads, profile.bsdReads)
    }
}
