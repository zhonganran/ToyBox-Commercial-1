package com.byd.wjx.domain.insight

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LocalInsightEngine @Inject constructor() {

    fun analyze(stats: LocalInsightStats): LocalInsightResult {
        if (stats.recentTripCount < MIN_TRIPS) {
            return insufficient(stats)
        }

        val consumptionChange = stats.consumptionChangePercent()
        val healthTone = stats.healthTone()
        val title = when {
            healthTone == LocalInsightTone.CRITICAL -> "本周优先关注电池健康"
            healthTone == LocalInsightTone.WARNING -> "本周电池状态需要留意"
            consumptionChange != null && consumptionChange >= 8.0 -> "本周电耗偏高"
            consumptionChange != null && consumptionChange <= -8.0 -> "本周能耗更省"
            stats.shortTripRatio() >= 0.5 -> "本周短途偏多"
            else -> "本周车辆状态稳定"
        }

        return LocalInsightResult(
            hasEnoughData = true,
            title = title,
            summary = buildSummary(stats, consumptionChange),
            metrics = buildMetrics(stats, consumptionChange),
            recommendations = buildRecommendations(stats, consumptionChange),
            generatedAt = stats.generatedAt,
        )
    }

    private fun insufficient(stats: LocalInsightStats): LocalInsightResult {
        val voltageTone = when {
            (stats.minVoltage12v ?: 13.0) < 11.8 -> LocalInsightTone.CRITICAL
            (stats.minVoltage12v ?: 13.0) < 12.2 -> LocalInsightTone.WARNING
            stats.latestVoltage12v != null -> LocalInsightTone.GOOD
            else -> LocalInsightTone.INFO
        }
        val cellTone = when {
            (stats.maxCellDeltaV ?: 0.0) >= 0.10 -> LocalInsightTone.CRITICAL
            (stats.maxCellDeltaV ?: 0.0) >= 0.06 -> LocalInsightTone.WARNING
            stats.maxCellDeltaV != null -> LocalInsightTone.GOOD
            else -> LocalInsightTone.INFO
        }
        val metrics = listOf(
            LocalInsightMetric(
                label = "有效行程",
                value = "${stats.recentTripCount}/$MIN_TRIPS",
                detail = "最近 ${stats.windowDays} 天至少需要 $MIN_TRIPS 次有效行程",
                tone = LocalInsightTone.WARNING,
            ),
            LocalInsightMetric(
                label = "累计里程",
                value = "%.1f km".formatCn(stats.recentDistanceKm),
                detail = "当前只做本地统计，不上传任何数据",
                tone = LocalInsightTone.INFO,
            ),
            LocalInsightMetric(
                label = "充电记录",
                value = "${stats.chargeCount} 次",
                detail = "%.1f kWh".formatCn(stats.chargeKwh),
                tone = LocalInsightTone.INFO,
            ),
            LocalInsightMetric(
                label = "12V",
                value = stats.latestVoltage12v?.let { "%.1f V".formatCn(it) } ?: "--",
                detail = stats.minVoltage12v?.let { "最低 %.1f V".formatCn(it) } ?: "暂无 12V 记录",
                tone = voltageTone,
            ),
            LocalInsightMetric(
                label = "电芯压差",
                value = stats.maxCellDeltaV?.let { "%.0f mV".formatCn(it * 1000.0) } ?: "--",
                detail = stats.avgBatteryTempC?.let { "电池均温 %.0f°C".formatCn(it) } ?: "暂无电池温度",
                tone = cellTone,
            ),
        )
        return LocalInsightResult(
            hasEnoughData = false,
            title = "数据还不够",
            summary = "最近 ${stats.windowDays} 天只有 ${stats.recentTripCount} 次有效行程，继续开几次后会自动生成本地洞察。",
            metrics = metrics,
            recommendations = listOf(
                "本地洞察只读取车机里的行程、充电和电池记录，不会走云端分析。",
                "行程记录达到 $MIN_TRIPS 次后，会开始对比最近 ${stats.windowDays} 天和前 ${stats.windowDays} 天的变化。",
            ),
            generatedAt = stats.generatedAt,
        )
    }

    private fun buildSummary(stats: LocalInsightStats, consumptionChange: Double?): String {
        val avg = stats.recentAvgConsumption
        val avgText = avg?.let { "%.1f kWh/100km".formatCn(it) } ?: "暂无有效电耗"
        val changeText = when {
            consumptionChange == null -> "暂无上一周期可比基准"
            abs(consumptionChange) < 5.0 -> "和上一周期基本持平"
            consumptionChange > 0.0 -> "比上一周期高 %.0f%%".formatCn(consumptionChange)
            else -> "比上一周期低 %.0f%%".formatCn(abs(consumptionChange))
        }
        val distanceText = "%.1f km".formatCn(stats.recentDistanceKm)
        val speedText = stats.recentAvgSpeedKmh?.let { "，均速 %.0f km/h".formatCn(it) }.orEmpty()
        return "最近 ${stats.windowDays} 天行驶 $distanceText，平均电耗 $avgText，$changeText$speedText。"
    }

    private fun buildMetrics(stats: LocalInsightStats, consumptionChange: Double?): List<LocalInsightMetric> {
        val consumptionTone = when {
            consumptionChange == null -> LocalInsightTone.INFO
            consumptionChange >= 10.0 -> LocalInsightTone.WARNING
            consumptionChange <= -8.0 -> LocalInsightTone.GOOD
            else -> LocalInsightTone.INFO
        }
        val idleRate = if (stats.idleDrainHours > 0.0) stats.idleDrainKwh / stats.idleDrainHours else 0.0
        val voltageTone = when {
            (stats.minVoltage12v ?: 13.0) < 11.8 -> LocalInsightTone.CRITICAL
            (stats.minVoltage12v ?: 13.0) < 12.2 -> LocalInsightTone.WARNING
            else -> LocalInsightTone.GOOD
        }
        val cellTone = when {
            (stats.maxCellDeltaV ?: 0.0) >= 0.10 -> LocalInsightTone.CRITICAL
            (stats.maxCellDeltaV ?: 0.0) >= 0.06 -> LocalInsightTone.WARNING
            stats.maxCellDeltaV != null -> LocalInsightTone.GOOD
            else -> LocalInsightTone.INFO
        }

        return listOf(
            LocalInsightMetric(
                label = "平均电耗",
                value = stats.recentAvgConsumption?.let { "%.1f".formatCn(it) } ?: "--",
                detail = consumptionChange?.let { "较前 ${stats.windowDays} 天 %+.0f%%".formatCn(it) } ?: "等待上一周期基准",
                tone = consumptionTone,
            ),
            LocalInsightMetric(
                label = "行驶里程",
                value = "%.1f km".formatCn(stats.recentDistanceKm),
                detail = buildString {
                    append("${stats.recentTripCount} 次行程，短途 ${stats.shortTripCount} 次")
                    stats.recentAvgSpeedKmh?.let { append("，均速 %.0f".formatCn(it)) }
                },
                tone = LocalInsightTone.INFO,
            ),
            LocalInsightMetric(
                label = "驻车耗电",
                value = "%.1f kWh".formatCn(stats.idleDrainKwh),
                detail = if (idleRate > 0.0) {
                    val night = if (stats.nightIdleDrainKwh >= 0.1) {
                        "，夜间 %.1f kWh".formatCn(stats.nightIdleDrainKwh)
                    } else ""
                    "%.2f kWh/h，共 %.1f h%s".formatCn(idleRate, stats.idleDrainHours, night)
                } else {
                    "暂无明显驻车耗电"
                },
                tone = if (stats.idleDrainKwh >= 3.0 || idleRate >= 0.7) LocalInsightTone.WARNING else LocalInsightTone.GOOD,
            ),
            LocalInsightMetric(
                label = "充电补能",
                value = "%.1f kWh".formatCn(stats.chargeKwh),
                detail = "${stats.chargeCount} 次，慢充 %.1f / 快充 %.1f kWh，费用 %.1f 元"
                    .formatCn(stats.acChargeKwh, stats.dcChargeKwh, stats.chargeCost),
                tone = LocalInsightTone.INFO,
            ),
            LocalInsightMetric(
                label = "12V",
                value = stats.latestVoltage12v?.let { "%.1f V".formatCn(it) } ?: "--",
                detail = buildString {
                    append(stats.minVoltage12v?.let { "最低 %.1f V".formatCn(it) } ?: "暂无 12V 记录")
                    stats.voltage12vTrendDelta?.let { append("，趋势 %+.2fV".formatCn(it)) }
                },
                tone = voltageTone,
            ),
            LocalInsightMetric(
                label = "电芯压差",
                value = stats.maxCellDeltaV?.let { "%.0f mV".formatCn(it * 1000.0) } ?: "--",
                detail = stats.avgBatteryTempC?.let { "电池均温 %.0f°C".formatCn(it) } ?: "暂无电池温度",
                tone = cellTone,
            ),
        )
    }

    private fun buildRecommendations(stats: LocalInsightStats, consumptionChange: Double?): List<String> {
        val recs = mutableListOf<String>()
        when {
            consumptionChange != null && consumptionChange >= 10.0 ->
                recs += "电耗明显上升，建议检查胎压、载重、空调使用和高速占比。"
            consumptionChange != null && consumptionChange <= -8.0 ->
                recs += "本周电耗下降，当前驾驶节奏和温控策略可以继续保持。"
            consumptionChange == null ->
                recs += "上一周期数据不足，本周先建立自己的本地基准。"
        }

        if (stats.shortTripRatio() >= 0.5 && stats.shortTripCount >= 3) {
            recs += "短途占比偏高，冷车和频繁启停会拉高电耗，短距离用车可以适当合并行程。"
        }
        val idleRate = if (stats.idleDrainHours > 0.0) stats.idleDrainKwh / stats.idleDrainHours else 0.0
        if (stats.idleDrainKwh >= 3.0 || idleRate >= 0.7) {
            recs += "驻车耗电偏高，长时间停车时留意空调、哨兵、外放和屏幕常亮。"
        }
        if ((stats.minVoltage12v ?: 13.0) < 12.2) {
            recs += "12V 电压出现偏低记录，若频繁低于 12.2V，建议重点观察小电瓶状态。"
        }
        if ((stats.maxCellDeltaV ?: 0.0) >= 0.06) {
            recs += "电芯压差偏大，后续充满或深放后再观察一次，连续偏高再处理。"
        }
        if ((stats.avgBatteryTempC ?: 25.0) >= 42.0) {
            recs += "电池温度偏高，快充或高负载后可以给车辆留一点散热时间。"
        } else if ((stats.avgBatteryTempC ?: 25.0) <= 8.0) {
            recs += "低温用车会增加电耗，出发前预热和温和加速会更稳。"
        }
        if (stats.dcRatio() >= 0.7 && stats.chargeKwh >= 20.0) {
            recs += "直流快充占比较高，日常有条件时可穿插慢充，减少电池热负担。"
        }
        val acCost = stats.acCostPerKwh
        val dcCost = stats.dcCostPerKwh
        if (acCost != null && dcCost != null && stats.acChargeKwh >= 3.0 && stats.dcChargeKwh >= 3.0 && dcCost > acCost * 1.15) {
            recs += "本周快充单价约 %.2f 元/kWh，慢充约 %.2f 元/kWh，非急用时慢充更划算。".formatCn(dcCost, acCost)
        }
        if (stats.dcChargeCount >= 2 && stats.dcChargeKwh >= stats.acChargeKwh * 1.5 && stats.dcChargeKwh >= 15.0) {
            recs += "快充次数和电量占比都偏高，连续快充后留意电池温度和补能成本。"
        }
        if (stats.nightIdleDrainKwh >= 0.3) {
            val share = stats.nightIdleDrainSharePercent?.toInt() ?: 0
            recs += "夜间驻车耗电 %.1f kWh，占驻车耗电约 %d%%，可检查哨兵、外放、屏幕和空调保活。"
                .formatCn(stats.nightIdleDrainKwh, share)
        }
        if ((stats.voltage12vTrendDelta ?: 0.0) <= -0.10 && (stats.minVoltage12v ?: 13.0) < 12.6) {
            recs += "12V 最近呈下降趋势，建议连续几天观察，避免长期低压影响启动和模块唤醒。"
        }
        if ((stats.avgExteriorTempC ?: 25.0) <= 5.0 && (consumptionChange ?: 0.0) > 3.0) {
            recs += "最近外温偏低，能耗上升可能和电池/座舱预热有关，短途尤其明显。"
        }
        if ((stats.avgExteriorTempC ?: 25.0) >= 30.0 && (stats.recentAvgConsumption ?: 0.0) >= 22.0) {
            recs += "最近外温较高且电耗偏高，可检查空调温度、风量和停车暴晒后的预冷时间。"
        }
        if ((stats.recentAvgSpeedKmh ?: 0.0) >= 75.0 && (stats.recentAvgConsumption ?: 0.0) >= 17.0 && stats.recentDistanceKm >= 80.0) {
            recs += "均速较高时风阻会明显拉高电耗，高速巡航适当降速通常更省。"
        }
        val best = stats.bestTrip
        val worst = stats.worstTrip
        if (best != null && worst != null && worst.consumptionKwhPer100Km - best.consumptionKwhPer100Km >= 5.0) {
            recs += "最好和最高电耗行程差距较大，可对比路况、车速和空调设置找出主要原因。"
        }
        if (recs.isEmpty()) {
            recs += "本周没有明显异常，继续保持当前用车习惯即可。"
        }
        return recs.take(MAX_RECOMMENDATIONS)
    }

    private fun LocalInsightStats.consumptionChangePercent(): Double? {
        val current = recentAvgConsumption ?: return null
        val previous = previousAvgConsumption ?: return null
        if (previousTripCount < 3 || previous <= 0.0) return null
        return (current - previous) / previous * 100.0
    }

    private fun LocalInsightStats.shortTripRatio(): Double =
        if (recentTripCount <= 0) 0.0 else shortTripCount.toDouble() / recentTripCount

    private fun LocalInsightStats.dcRatio(): Double =
        if (chargeKwh <= 0.0) 0.0 else dcChargeKwh / chargeKwh

    private fun LocalInsightStats.healthTone(): LocalInsightTone = when {
        (minVoltage12v ?: 13.0) < 11.8 || (maxCellDeltaV ?: 0.0) >= 0.10 -> LocalInsightTone.CRITICAL
        (minVoltage12v ?: 13.0) < 12.2 || (maxCellDeltaV ?: 0.0) >= 0.06 -> LocalInsightTone.WARNING
        else -> LocalInsightTone.GOOD
    }

    private fun String.formatCn(vararg args: Any): String =
        String.format(Locale.CHINA, this, *args)

    private companion object {
        const val MIN_TRIPS = 5
        const val MAX_RECOMMENDATIONS = 5
    }
}
