package com.byd.wjx.camera.blindspot

import com.byd.wjx.data.vehicle.VehicleReadSpec
import com.byd.wjx.vehicle.CarProfileDetector

/** Vehicle signal mapping kept outside the controller so verified variants can be added safely. */
data class BlindSpotVehicleProfile(
    val id: String,
    val controllerSeries: Int,
    val verified: Boolean,
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
        return VERIFIED_PROFILES[vehicle.series] ?: COMMON_PROFILE.copy(
            id = "controller-${vehicle.seriesLabel}-unverified",
            controllerSeries = vehicle.series,
            verified = false,
        )
    }

    companion object {
        val COMMON_PROFILE = BlindSpotVehicleProfile(
            id = "byd-common-probe",
            controllerSeries = 0,
            verified = false,
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

        private val VERIFIED_PROFILES = listOf(23, 34, 57).associateWith { series ->
            COMMON_PROFILE.copy(
                id = "controller-$series",
                controllerSeries = series,
                verified = true,
            )
        }
    }
}
