package com.byd.wjx.domain.insight

enum class LocalInsightTone {
    GOOD,
    INFO,
    WARNING,
    CRITICAL
}

data class LocalInsightMetric(
    val label: String,
    val value: String,
    val detail: String,
    val tone: LocalInsightTone = LocalInsightTone.INFO
)

data class LocalInsightResult(
    val hasEnoughData: Boolean,
    val title: String,
    val summary: String,
    val metrics: List<LocalInsightMetric>,
    val recommendations: List<String>,
    val generatedAt: Long,
) {
    companion object {
        fun initial(now: Long = System.currentTimeMillis()) = LocalInsightResult(
            hasEnoughData = false,
            title = "本地洞察",
            summary = "正在读取本地行程与电池数据",
            metrics = emptyList(),
            recommendations = emptyList(),
            generatedAt = now,
        )
    }
}

data class InsightTripSample(
    val startTs: Long,
    val distanceKm: Double,
    val consumptionKwhPer100Km: Double
)

data class LocalInsightStats(
    val generatedAt: Long,
    val windowDays: Int,
    val recentTripCount: Int,
    val previousTripCount: Int,
    val recentDistanceKm: Double,
    val previousDistanceKm: Double,
    val recentKwh: Double,
    val previousKwh: Double,
    val recentCost: Double,
    val previousCost: Double,
    val recentAvgSpeedKmh: Double? = null,
    val recentAvgConsumption: Double?,
    val previousAvgConsumption: Double?,
    val shortTripCount: Int,
    val coldTripCount: Int,
    val hotTripCount: Int,
    val idleDrainKwh: Double,
    val idleDrainHours: Double,
    val chargeCount: Int,
    val chargeKwh: Double,
    val chargeCost: Double,
    val acChargeKwh: Double,
    val dcChargeKwh: Double,
    val acChargeCount: Int = 0,
    val dcChargeCount: Int = 0,
    val acCostPerKwh: Double? = null,
    val dcCostPerKwh: Double? = null,
    val nightIdleDrainKwh: Double = 0.0,
    val nightIdleDrainSharePercent: Double? = null,
    val latestVoltage12v: Double?,
    val minVoltage12v: Double?,
    val avgVoltage12v: Double?,
    val voltage12vTrendDelta: Double? = null,
    val maxCellDeltaV: Double?,
    val avgExteriorTempC: Double? = null,
    val avgBatteryTempC: Double?,
    val bestTrip: InsightTripSample?,
    val worstTrip: InsightTripSample?,
)
