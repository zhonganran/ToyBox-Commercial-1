package com.byd.wjx.camera.blindspot

import com.byd.wjx.vehicle.CarProfileDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotVehicleProfileTest {
    @Test
    fun `known controller series resolves an explicit verified profile`() {
        val profile = BlindSpotVehicleProfileResolver(
            detector = CarProfileDetector(propertyReader = { property ->
                if (property == "apps.setting.product.outswver") "34.2.1" else ""
            }),
        ).resolve()

        assertEquals(34, profile.controllerSeries)
        assertEquals("controller-34", profile.id)
        assertTrue(profile.verified)
    }

    @Test
    fun `unknown controller series remains clearly marked unverified`() {
        val profile = BlindSpotVehicleProfileResolver(
            detector = CarProfileDetector(propertyReader = { "unknown" }),
        ).resolve()

        assertEquals(0, profile.controllerSeries)
        assertFalse(profile.verified)
        assertTrue(profile.id.endsWith("-unverified"))
    }
}
