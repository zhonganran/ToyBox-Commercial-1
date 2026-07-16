package com.byd.wjx.domain.energy

import com.byd.wjx.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyFlowAccumulatorTest {
    @Test
    fun `known BYD states classify into distinct recovery buckets`() {
        fun category(state: Int) = EnergyFlowAccumulator.classify(
            diParsData(power = -10.0, energyState = state, chargeGunState = 1),
        )

        assertEquals(EnergyFlowCategory.EV_4WD_REGEN, category(4))
        assertEquals(EnergyFlowCategory.EV_FWD_REGEN, category(5))
        assertEquals(EnergyFlowCategory.EV_RWD_REGEN, category(6))
        assertEquals(EnergyFlowCategory.HEV_REGEN, category(15))
        assertEquals(EnergyFlowCategory.ASSIST_REGEN, category(28))
        assertEquals(EnergyFlowCategory.ENGINE_GENERATION, category(21))
        assertEquals(EnergyFlowCategory.OTHER_REGEN, category(99))
    }

    @Test
    fun `charging and non-negative power never count as recovery`() {
        assertEquals(
            EnergyFlowCategory.NONE,
            EnergyFlowAccumulator.classify(
                diParsData(power = -10.0, energyState = 5, chargeGunState = 2),
            ),
        )
        assertEquals(
            EnergyFlowCategory.NONE,
            EnergyFlowAccumulator.classify(
                diParsData(power = -10.0, energyState = 5, chargingStatus = 1),
            ),
        )
        assertEquals(
            EnergyFlowCategory.NONE,
            EnergyFlowAccumulator.classify(
                diParsData(power = 5.0, energyState = 5, chargeGunState = 1),
            ),
        )
    }

    @Test
    fun `one second at 36 kilowatts adds one hundredth kWh`() {
        val accumulator = EnergyFlowAccumulator()
        val sample = diParsData(power = -36.0, energyState = 5, chargeGunState = 1)

        accumulator.onSample(1_000L, sample, sessionStartedAt = 1_000L)
        val stats = accumulator.onSample(2_000L, sample, sessionStartedAt = 1_000L)

        assertEquals(0.01, stats.session.evFwdRegenKwh, 0.000001)
        assertEquals(0.01, stats.lifetime.evFwdRegenKwh, 0.000001)
        assertEquals(0.0, stats.session.engineGenerationKwh, 0.0)
    }

    @Test
    fun `engine generation is separate and long sample gaps are rejected`() {
        val accumulator = EnergyFlowAccumulator()
        val generation = diParsData(power = -18.0, energyState = 11, chargeGunState = 1)

        accumulator.onSample(1_000L, generation, sessionStartedAt = 1_000L)
        val afterGap = accumulator.onSample(7_000L, generation, sessionStartedAt = 1_000L)
        assertEquals(0.0, afterGap.session.engineGenerationKwh, 0.0)

        val integrated = accumulator.onSample(8_000L, generation, sessionStartedAt = 1_000L)
        assertEquals(0.005, integrated.session.engineGenerationKwh, 0.000001)
        assertEquals(0.0, integrated.session.totalRegenKwh, 0.0)
    }

    @Test
    fun `new drive session resets session totals but retains lifetime totals`() {
        val accumulator = EnergyFlowAccumulator()
        val sample = diParsData(power = -36.0, energyState = 4, chargeGunState = 1)
        accumulator.onSample(1_000L, sample, sessionStartedAt = 1_000L)
        accumulator.onSample(2_000L, sample, sessionStartedAt = 1_000L)

        val newSession = accumulator.onSample(10_000L, sample, sessionStartedAt = 10_000L)

        assertEquals(0.0, newSession.session.totalRegenKwh, 0.0)
        assertEquals(0.01, newSession.lifetime.totalRegenKwh, 0.000001)
    }
}
