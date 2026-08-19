package com.byd.wjx.ui.creative

import com.byd.wjx.vehicle.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreativeVehicleMatcherTest {
    private val vehicles = listOf(
        vehicle("byd_song", "BYD", "宋", listOf("宋", "宋 pro")),
        vehicle("byd_song_plus_dmi", "BYD", "宋 PLUS DM-i", listOf("宋 plus dm-i", "song plus dmi")),
        vehicle("fangchengbao_5", "方程豹", "豹 5", listOf("方程豹5", "豹5", "bao 5")),
    )

    @Test
    fun `specific Song Plus identity wins over generic Song`() {
        val result = CreativeVehicleMatcher.findBestMatch(
            vehicleInfo(displayName = "宋 PLUS DM-i", carSeries = "SONG_PLUS"),
            vehicles,
        )

        assertEquals("byd_song_plus_dmi", result?.id)
    }

    @Test
    fun `normalizes spaces and punctuation for Fangchengbao`() {
        val result = CreativeVehicleMatcher.findBestMatch(
            vehicleInfo(displayName = "方程豹 豹 5"),
            vehicles,
        )

        assertEquals("fangchengbao_5", result?.id)
    }

    @Test
    fun `unknown identity does not silently select a default vehicle`() {
        val result = CreativeVehicleMatcher.findBestMatch(
            vehicleInfo(displayName = "未知车型", carSeries = "UNKNOWN_SERIES"),
            vehicles,
        )

        assertNull(result)
    }

    private fun vehicle(
        id: String,
        brand: String,
        name: String,
        aliases: List<String>,
    ) = CreativeVehicle(
        id = id,
        brand = brand,
        name = name,
        aliases = aliases,
        modelUrl = "https://cdn.example.com/$id.glb",
        size = 1L,
        version = 1,
        sha256 = "hash",
    )

    private fun vehicleInfo(
        displayName: String,
        modelVariant: String = "",
        carSeries: String = "",
        vehicleType: String = "",
    ) = VehicleInfo(
        vin = "",
        vinYear = null,
        displayName = displayName,
        modelVariant = modelVariant,
        carSeries = carSeries,
        vehicleType = vehicleType,
        controllerVersion = "",
        controllerSeries = 0,
    )
}
