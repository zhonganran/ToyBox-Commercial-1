package com.byd.wjx.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleInfoModelsTest {
    @Test
    fun `VIN year code S resolves to the current generation 2025`() {
        assertEquals(2025, VinYearParser.parse("LC0FD4C42S7184345"))
    }

    @Test
    fun `invalid VIN does not create a year`() {
        assertNull(VinYearParser.parse("not-a-vin"))
        assertNull(VinYearParser.parse("LC0FD4C42I7184345"))
    }

    @Test
    fun `display label uses only the reported name and VIN year`() {
        assertEquals("2025款 宋 PLUS DM-i", composeDisplayLabel(2025, "宋 PLUS DM-i"))
        assertEquals("宋 PLUS DM-i", composeDisplayLabel(null, "宋 PLUS DM-i"))
        assertNull(composeDisplayLabel(2025, ""))
    }

    @Test
    fun `custom bluetooth name is never presented as the vehicle model`() {
        val properties = mapOf(
            "persist.sys.byd.default_name" to "",
            "persist.sys.byd.bluetooth_name" to "车主自定义名称",
        )

        assertEquals("", VehicleIdentityResolver.readDisplayName { properties[it].orEmpty() })
    }

    @Test
    fun `identity reader keeps real system properties without camera fingerprint`() {
        val properties = mapOf(
            "persist.sys.byd.default_name" to "宋 PLUS DM-i",
            "persist.sys.model_variant.model" to "SA3HEV",
            "ro.build.car.series" to "SONG_PLUS",
            "ro.vehicle.type" to "PHEV",
            "apps.setting.product.outswver" to "57.1.3.2501010.1",
        )

        val identity = VehicleIdentityResolver.read { properties[it].orEmpty() }

        assertEquals("宋 PLUS DM-i", identity.displayName)
        assertEquals("SA3HEV", identity.modelVariant)
        assertEquals(57, identity.controllerSeries)
        assertEquals("57.1.3.2501010.1", identity.controllerVersion)
    }

    @Test
    fun `temporary empty properties keep the last known identity for the same VIN`() {
        val previous = VehicleInfo(
            vin = "LC0FD4C42S7184345",
            vinYear = 2025,
            displayName = "宋 PLUS DM-i",
            modelVariant = "SA3HEV",
            carSeries = "SONG_PLUS",
            vehicleType = "PHEV",
            controllerVersion = "57.1.3.2501010.1",
            controllerSeries = 57,
        )

        val retained = retainKnownVehicleInfo(
            previous,
            previous.copy(
                displayName = "",
                modelVariant = "",
                carSeries = "",
                vehicleType = "",
                controllerVersion = "",
                controllerSeries = 0,
            ),
        )

        assertEquals(previous, retained)
    }

    @Test
    fun `a different VIN never inherits the previous vehicle identity`() {
        val previous = VehicleInfo(
            vin = "LC0FD4C42S7184345",
            vinYear = 2025,
            displayName = "宋 PLUS DM-i",
            modelVariant = "SA3HEV",
            carSeries = "SONG_PLUS",
            vehicleType = "PHEV",
            controllerVersion = "57.1.3.2501010.1",
            controllerSeries = 57,
        )
        val current = VehicleInfo(
            vin = "LC0FD4C42T7184345",
            vinYear = 2026,
            displayName = "",
            modelVariant = "",
            carSeries = "",
            vehicleType = "",
            controllerVersion = "",
            controllerSeries = 0,
        )

        assertEquals(current, retainKnownVehicleInfo(previous, current))
    }
}
