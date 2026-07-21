package com.byd.wjx.domain.calculator

import kotlin.math.roundToInt

data class HomeRangeEstimate(
    val electricKm: Int?,
    val fuelKm: Int?,
    val combinedKm: Int?,
)

/** Converts remaining energy and live consumption into bounded home-screen estimates. */
object HomeRangeEstimator {
    fun estimate(
        dynamicElectricKm: Double?,
        remainingBatteryKwh: Double?,
        electricConsumptionPer100Km: Double?,
        remainingFuelLiters: Double?,
        fuelConsumptionPer100Km: Double?,
    ): HomeRangeEstimate {
        val electricKm = validRange(dynamicElectricKm, MAX_ELECTRIC_RANGE_KM)
            ?: estimateFromConsumption(
                remainingAmount = remainingBatteryKwh,
                consumptionPer100Km = electricConsumptionPer100Km,
                validConsumptionRange = ELECTRIC_CONSUMPTION_RANGE,
                maxRangeKm = MAX_ELECTRIC_RANGE_KM,
            )
        val fuelKm = estimateFromConsumption(
            remainingAmount = remainingFuelLiters,
            consumptionPer100Km = fuelConsumptionPer100Km,
            validConsumptionRange = FUEL_CONSUMPTION_RANGE,
            maxRangeKm = MAX_FUEL_RANGE_KM,
        )
        return HomeRangeEstimate(
            electricKm = electricKm,
            fuelKm = fuelKm,
            combinedKm = listOfNotNull(electricKm, fuelKm)
                .takeIf { it.isNotEmpty() }
                ?.sum()
                ?.coerceAtMost(MAX_COMBINED_RANGE_KM),
        )
    }

    private fun estimateFromConsumption(
        remainingAmount: Double?,
        consumptionPer100Km: Double?,
        validConsumptionRange: ClosedFloatingPointRange<Double>,
        maxRangeKm: Int,
    ): Int? {
        val remaining = remainingAmount?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val consumption = consumptionPer100Km
            ?.takeIf { it.isFinite() && it in validConsumptionRange }
            ?: return null
        return validRange(remaining / consumption * 100.0, maxRangeKm)
    }

    private fun validRange(value: Double?, maxRangeKm: Int): Int? = value
        ?.takeIf { it.isFinite() && it >= 0.0 && it <= maxRangeKm.toDouble() }
        ?.roundToInt()

    private val ELECTRIC_CONSUMPTION_RANGE = 1.0..100.0
    private val FUEL_CONSUMPTION_RANGE = 0.5..50.0
    private const val MAX_ELECTRIC_RANGE_KM = 1_500
    private const val MAX_FUEL_RANGE_KM = 2_500
    private const val MAX_COMBINED_RANGE_KM = 3_000
}
