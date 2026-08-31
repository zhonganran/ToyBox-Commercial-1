package com.byd.wjx.camera.blindspot

import com.byd.wjx.data.vehicle.VehicleReadSpec
import com.byd.wjx.vehicle.CarProfileDetector

/** Shared BYD vehicle signal mapping kept outside the runtime controller. */
data class BlindSpotVehicleProfile(
    val id: String,
    val controllerSeries: Int,
    val coreReads: List<VehicleReadSpec>,
    val bsdReads: List<VehicleReadSpec>,
) {
    val readsWithBsd: List<VehicleReadSpec> get() = coreReads + bsdReads
}

class BlindSpotVehicleProfileResolver(
    private val detector: CarProfileDetector = CarProfileDetector(),
) {
    fun resolve(): BlindSpotVehicleProfile {
        val vehicle = detector.detect()
        return COMMON_PROFILE.copy(
            id = "byd-common-${vehicle.seriesLabel}",
            controllerSeries = vehicle.series,
        )
    }

    companion object {
        val COMMON_PROFILE = BlindSpotVehicleProfile(
            id = "byd-common-probe",
            controllerSeries = 0,
            coreReads = listOf(
                VehicleReadSpec(5, 1004, 950009900),
                VehicleReadSpec(7, 1013, -1807745016),
                VehicleReadSpec(5, 1011, 555745336),
                VehicleReadSpec(7, 1001, 300941320),
            ),
            bsdReads = listOf(
                VehicleReadSpec(5, 1038, 1098907664),
                VehicleReadSpec(5, 1038, 1098907666),
            ),
        )

    }
}
