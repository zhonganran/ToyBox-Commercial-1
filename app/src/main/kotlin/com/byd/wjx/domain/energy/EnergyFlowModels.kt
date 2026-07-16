package com.byd.wjx.domain.energy

import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.data.remote.DiParsData

enum class EnergyFlowCategory {
    EV_4WD_REGEN,
    EV_FWD_REGEN,
    EV_RWD_REGEN,
    HEV_REGEN,
    ASSIST_REGEN,
    OTHER_REGEN,
    ENGINE_GENERATION,
    NONE,
}

data class EnergyFlowTotals(
    val ev4wdRegenKwh: Double = 0.0,
    val evFwdRegenKwh: Double = 0.0,
    val evRwdRegenKwh: Double = 0.0,
    val hevRegenKwh: Double = 0.0,
    val assistRegenKwh: Double = 0.0,
    val otherRegenKwh: Double = 0.0,
    val engineGenerationKwh: Double = 0.0,
) {
    val totalRegenKwh: Double
        get() = ev4wdRegenKwh + evFwdRegenKwh + evRwdRegenKwh +
            hevRegenKwh + assistRegenKwh + otherRegenKwh

    val totalRecoveredKwh: Double
        get() = totalRegenKwh + engineGenerationKwh

    fun add(category: EnergyFlowCategory, kwh: Double): EnergyFlowTotals {
        if (!kwh.isFinite() || kwh <= 0.0) return this
        return when (category) {
            EnergyFlowCategory.EV_4WD_REGEN -> copy(ev4wdRegenKwh = ev4wdRegenKwh + kwh)
            EnergyFlowCategory.EV_FWD_REGEN -> copy(evFwdRegenKwh = evFwdRegenKwh + kwh)
            EnergyFlowCategory.EV_RWD_REGEN -> copy(evRwdRegenKwh = evRwdRegenKwh + kwh)
            EnergyFlowCategory.HEV_REGEN -> copy(hevRegenKwh = hevRegenKwh + kwh)
            EnergyFlowCategory.ASSIST_REGEN -> copy(assistRegenKwh = assistRegenKwh + kwh)
            EnergyFlowCategory.OTHER_REGEN -> copy(otherRegenKwh = otherRegenKwh + kwh)
            EnergyFlowCategory.ENGINE_GENERATION ->
                copy(engineGenerationKwh = engineGenerationKwh + kwh)
            EnergyFlowCategory.NONE -> this
        }
    }
}

data class EnergyFlowStats(
    val currentCategory: EnergyFlowCategory = EnergyFlowCategory.NONE,
    val rawEnergyState: Int? = null,
    val sessionStartedAt: Long? = null,
    val session: EnergyFlowTotals = EnergyFlowTotals(),
    val lifetime: EnergyFlowTotals = EnergyFlowTotals(),
)

/**
 * Integrates negative live battery power into the BYD energy-flow bucket reported by energyState.
 * Charging is excluded, and long gaps are rejected so a process sleep cannot create fake energy.
 */
class EnergyFlowAccumulator(
    initialLifetime: EnergyFlowTotals = EnergyFlowTotals(),
    initialSession: EnergyFlowTotals = EnergyFlowTotals(),
    initialSessionStartedAt: Long? = null,
) {
    private data class TimedSample(val atMs: Long, val data: DiParsData)

    private var previous: TimedSample? = null
    private var current = EnergyFlowStats(
        sessionStartedAt = initialSessionStartedAt,
        session = initialSession,
        lifetime = initialLifetime,
    )

    val stats: EnergyFlowStats get() = current

    fun onSample(nowMs: Long, data: DiParsData, sessionStartedAt: Long?): EnergyFlowStats {
        val currentCategory = classify(data)

        // No active ignition session means no recovery integration. Keep the completed session
        // totals visible until the next real session starts.
        if (sessionStartedAt == null) {
            previous = null
            current = current.copy(
                currentCategory = EnergyFlowCategory.NONE,
                rawEnergyState = data.energyState,
            )
            return current
        }

        if (sessionStartedAt != current.sessionStartedAt) {
            current = current.copy(
                currentCategory = currentCategory,
                rawEnergyState = data.energyState,
                sessionStartedAt = sessionStartedAt,
                session = EnergyFlowTotals(),
            )
            previous = TimedSample(nowMs, data)
            return current
        }

        val prior = previous
        previous = TimedSample(nowMs, data)
        current = current.copy(
            currentCategory = currentCategory,
            rawEnergyState = data.energyState,
        )
        if (prior == null) return current

        val elapsedMs = nowMs - prior.atMs
        if (elapsedMs <= 0L || elapsedMs > MAX_INTEGRATION_GAP_MS) return current

        val priorCategory = classify(prior.data)
        val priorPower = recoveredPowerKw(prior.data, priorCategory)
        val currentPower = recoveredPowerKw(data, currentCategory)
        val averagePowerKw = when {
            priorPower != null && currentPower != null -> (priorPower + currentPower) / 2.0
            priorPower != null -> priorPower
            currentPower != null -> currentPower
            else -> return current
        }
        val bucket = if (currentPower != null) currentCategory else priorCategory
        val deltaKwh = averagePowerKw * (elapsedMs / 1_000.0) / 3_600.0
        current = current.copy(
            session = current.session.add(bucket, deltaKwh),
            lifetime = current.lifetime.add(bucket, deltaKwh),
        )
        return current
    }

    companion object {
        const val MAX_INTEGRATION_GAP_MS = 5_000L
        private const val MIN_RECOVERY_POWER_KW = -0.3

        fun classify(data: DiParsData): EnergyFlowCategory {
            if (recoveredPowerKw(data, EnergyFlowCategory.OTHER_REGEN) == null) {
                return EnergyFlowCategory.NONE
            }
            return when (data.energyState) {
                4 -> EnergyFlowCategory.EV_4WD_REGEN
                5 -> EnergyFlowCategory.EV_FWD_REGEN
                6 -> EnergyFlowCategory.EV_RWD_REGEN
                14, 15, 16 -> EnergyFlowCategory.HEV_REGEN
                27, 28, 29 -> EnergyFlowCategory.ASSIST_REGEN
                11, 12, 13, 18, 20, 21, 22 -> EnergyFlowCategory.ENGINE_GENERATION
                null, 0 -> EnergyFlowCategory.NONE
                else -> EnergyFlowCategory.OTHER_REGEN
            }
        }

        private fun recoveredPowerKw(
            data: DiParsData,
            category: EnergyFlowCategory,
        ): Double? {
            if (category == EnergyFlowCategory.NONE) return null
            if (ChargeGunState.isConnected(data.chargeGunState)) return null
            if (BydSignalSemantics.isCharging(data.chargingStatus)) return null
            return data.power
                ?.takeIf { it.isFinite() && it < MIN_RECOVERY_POWER_KW }
                ?.let { -it }
        }
    }
}
