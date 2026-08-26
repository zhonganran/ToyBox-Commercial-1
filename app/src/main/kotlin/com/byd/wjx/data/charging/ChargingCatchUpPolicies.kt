package com.byd.wjx.data.charging

import com.byd.wjx.data.autoservice.ChargingReading

internal const val CATCH_UP_MIN_DELTA_KWH = 0.5
internal const val CATCH_UP_HEURISTIC_HOURS_FALLBACK = 1.0
internal const val CATCH_UP_MIN_ELAPSED_HOURS = 0.05
internal const val CATCH_UP_MAX_ELAPSED_HOURS = 16.0
internal const val CATCH_UP_SOC_SANITY_RATIO_LOW = 0.70
internal const val CATCH_UP_SOC_SANITY_RATIO_HIGH = 1.30
internal const val CATCH_UP_MIN_SOC_DELTA_FOR_SNAPSHOT = 5
internal const val CATCH_UP_MIN_SOC_DELTA_FOR_CHARGE = 2
internal const val CATCH_UP_SOC_DELTA_TRUSTED_CHARGE = 3
internal const val CATCH_UP_ODOMETER_MOVED_EPSILON_KM = 1.0f
internal const val CATCH_UP_MAX_CONSECUTIVE_GUN_GLITCHES = 3
internal const val CATCH_UP_SOH_SANITY_MIN = 50.0

internal data class CatchUpEnergyResolution(
    val kwh: Double,
    val effectiveCapacityKwh: Double,
    val source: String,
    val bmsCandidateKwh: Double?,
    val bmsToSocRatio: Double?,
)

internal fun shouldRejectCatchUpCharge(
    socDelta: Int,
    odometerMoved: Boolean,
    chargePending: Boolean,
): Boolean = socDelta < CATCH_UP_MIN_SOC_DELTA_FOR_CHARGE ||
    (
        socDelta < CATCH_UP_SOC_DELTA_TRUSTED_CHARGE &&
            odometerMoved &&
            !chargePending
        )

internal fun chargingDeviceHasDynamicData(charging: ChargingReading?): Boolean =
    charging != null && (
        charging.chargingType != null ||
            charging.bmsState != null ||
            charging.chargingCapacityKwh != null ||
            charging.chargeBatteryVoltV != null
        )

internal fun resolveCatchUpEnergy(
    currentCapacityKwh: Double?,
    previousCapacityKwh: Double?,
    socDelta: Int,
    nominalCapacityKwh: Double,
    sohPercent: Double?,
): CatchUpEnergyResolution {
    val sohFactor = sohPercent
        ?.takeIf { it in CATCH_UP_SOH_SANITY_MIN..100.0 }
        ?.div(100.0)
        ?: 1.0
    val effectiveCapacity = nominalCapacityKwh * sohFactor
    val socEstimate = socDelta / 100.0 * effectiveCapacity
    val capacityDelta = if (currentCapacityKwh != null && previousCapacityKwh != null) {
        currentCapacityKwh - previousCapacityKwh
    } else {
        null
    }
    val candidate = when {
        capacityDelta != null && capacityDelta in CATCH_UP_MIN_DELTA_KWH..200.0 ->
            capacityDelta to "autoservice_cap_delta"
        currentCapacityKwh != null && currentCapacityKwh in CATCH_UP_MIN_DELTA_KWH..200.0 ->
            currentCapacityKwh to "autoservice_cap_session"
        else -> null
    }
    if (candidate == null) {
        return CatchUpEnergyResolution(
            kwh = socEstimate,
            effectiveCapacityKwh = effectiveCapacity,
            source = "autoservice_soc_estimate",
            bmsCandidateKwh = null,
            bmsToSocRatio = null,
        )
    }
    val ratio = if (socEstimate > 0.0) candidate.first / socEstimate else Double.NaN
    val trustworthy = ratio.isFinite() &&
        ratio in CATCH_UP_SOC_SANITY_RATIO_LOW..CATCH_UP_SOC_SANITY_RATIO_HIGH
    return CatchUpEnergyResolution(
        kwh = if (trustworthy) candidate.first else socEstimate,
        effectiveCapacityKwh = effectiveCapacity,
        source = if (trustworthy) candidate.second else "autoservice_soc_fallback",
        bmsCandidateKwh = candidate.first,
        bmsToSocRatio = ratio,
    )
}

internal fun catchUpElapsedHours(now: Long, previousTimestamp: Long): Double {
    val rawHours = (now - previousTimestamp) / 3_600_000.0
    return if (previousTimestamp <= 0L || rawHours <= 0.0) {
        CATCH_UP_HEURISTIC_HOURS_FALLBACK
    } else {
        rawHours.coerceIn(CATCH_UP_MIN_ELAPSED_HOURS, CATCH_UP_MAX_ELAPSED_HOURS)
    }
}
