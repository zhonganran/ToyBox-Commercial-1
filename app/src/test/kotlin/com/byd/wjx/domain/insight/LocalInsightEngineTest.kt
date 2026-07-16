package com.byd.wjx.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInsightEngineTest {

    private val engine = LocalInsightEngine()

    @Test
    fun `less than five trips reports insufficient data`() {
        val result = engine.analyze(stats(recentTripCount = 3))

        assertFalse(result.hasEnoughData)
        assertEquals("数据还不够", result.title)
        assertTrue(result.summary.contains("3 次有效行程"))
        assertEquals("0.020 V", result.metrics.first { it.label == "电芯压差" }.value)
    }

    @Test
    fun `higher consumption creates consumption warning`() {
        val result = engine.analyze(
            stats(
                recentTripCount = 8,
                previousTripCount = 8,
                recentAvgConsumption = 20.0,
                previousAvgConsumption = 16.0,
            )
        )

        assertTrue(result.hasEnoughData)
        assertEquals("本周电耗偏高", result.title)
        assertTrue(result.recommendations.any { it.contains("电耗明显上升") })
    }

    @Test
    fun `low 12v is surfaced in recommendations`() {
        val result = engine.analyze(
            stats(
                recentTripCount = 8,
                previousTripCount = 8,
                minVoltage12v = 12.0,
                latestVoltage12v = 12.1,
            )
        )

        assertEquals("本周电池状态需要留意", result.title)
        assertTrue(result.recommendations.any { it.contains("12V") })
    }

    private fun stats(
        recentTripCount: Int = 6,
        previousTripCount: Int = 6,
        recentAvgConsumption: Double? = 16.0,
        previousAvgConsumption: Double? = 16.0,
        latestVoltage12v: Double? = 13.2,
        minVoltage12v: Double? = 13.0,
    ) = LocalInsightStats(
        generatedAt = 1L,
        windowDays = 7,
        recentTripCount = recentTripCount,
        previousTripCount = previousTripCount,
        recentDistanceKm = 120.0,
        previousDistanceKm = 110.0,
        recentKwh = recentAvgConsumption?.let { 120.0 * it / 100.0 } ?: 0.0,
        previousKwh = previousAvgConsumption?.let { 110.0 * it / 100.0 } ?: 0.0,
        recentCost = 20.0,
        previousCost = 18.0,
        recentAvgConsumption = recentAvgConsumption,
        previousAvgConsumption = previousAvgConsumption,
        shortTripCount = 1,
        coldTripCount = 0,
        hotTripCount = 0,
        idleDrainKwh = 0.5,
        idleDrainHours = 1.0,
        chargeCount = 2,
        chargeKwh = 40.0,
        chargeCost = 30.0,
        acChargeKwh = 25.0,
        dcChargeKwh = 15.0,
        latestVoltage12v = latestVoltage12v,
        minVoltage12v = minVoltage12v,
        avgVoltage12v = latestVoltage12v,
        maxCellDeltaV = 0.02,
        avgBatteryTempC = 26.0,
        bestTrip = InsightTripSample(1L, 20.0, 13.0),
        worstTrip = InsightTripSample(2L, 20.0, 18.0),
    )
}
