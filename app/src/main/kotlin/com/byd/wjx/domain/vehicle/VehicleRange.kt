package com.byd.wjx.domain.vehicle

/** Adds only the original vehicle-reported range components that are available. */
fun totalVehicleRangeKm(electricRangeKm: Int?, fuelRangeKm: Int?): Int? =
    listOfNotNull(electricRangeKm, fuelRangeKm)
        .takeIf { it.isNotEmpty() }
        ?.sum()
