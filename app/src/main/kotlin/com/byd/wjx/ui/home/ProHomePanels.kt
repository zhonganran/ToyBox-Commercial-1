package com.byd.wjx.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.domain.insight.LocalInsightMetric
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.GlassStroke
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun HomeMetricRibbon(
    state: DashboardUiState,
    onOpenDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val metrics = listOf(
        RibbonMetric("电续航", state.elecDrivingRangeKm?.toString() ?: "--", "km", Icons.Outlined.EvStation, SceneMint, rangeProgress(state.elecDrivingRangeKm, 320)),
        RibbonMetric("油续航", state.fuelDrivingRangeKm?.toString() ?: "--", "km", Icons.Outlined.LocalGasStation, SceneBlue, rangeProgress(state.fuelDrivingRangeKm, 800)),
        RibbonMetric("总续航", state.combinedRangeKm?.toString() ?: "--", "km", Icons.Outlined.Route, SceneViolet, rangeProgress(state.combinedRangeKm, 1200)),
        RibbonMetric(
            label = "油量",
            value = state.fuelLevel?.toString() ?: "--",
            unit = "%",
            icon = Icons.Outlined.WaterDrop,
            color = SceneAmber,
            progress = ((state.fuelLevel ?: 0) / 100f).coerceIn(0f, 1f),
            extraValue = state.fuelRemainingLiters?.let { "%.1f L".format(it) },
        ),
        RibbonMetric("实时功率", state.power?.let { "%.1f".format(it) } ?: "--", "kW", Icons.Outlined.ElectricBolt, AccentOrange, ((kotlin.math.abs(state.power ?: 0.0) / 80.0).toFloat()).coerceIn(0f, 1f)),
        RibbonMetric("总里程", state.odometer?.let { "%,.0f".format(it) } ?: "--", "km", Icons.Outlined.Speed, ScenePink, 0.72f),
    )
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.86f))
            .border(1.dp, GlassStroke, shape)
            .clickable(onClick = onOpenDashboard)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (compact || maxWidth < 900.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item -> RibbonMetricItem(item, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                metrics.forEachIndexed { index, item ->
                    RibbonMetricItem(item, Modifier.weight(1f))
                    if (index < metrics.lastIndex) {
                        Box(Modifier.width(1.dp).height(42.dp).background(TextPrimary.copy(alpha = 0.08f)))
                    }
                }
            }
        }
    }
}

private data class RibbonMetric(
    val label: String,
    val value: String,
    val unit: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val progress: Float,
    val extraValue: String? = null,
)

@Composable
private fun RibbonMetricItem(metric: RibbonMetric, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Column {
                Text(metric.label, color = TextPrimary.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(metric.value, color = TextPrimary, fontSize = 23.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Text(metric.unit, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                    metric.extraValue?.let {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "· $it",
                            color = TextPrimary.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(TextPrimary.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(metric.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(metric.color))
        }
    }
}

@Composable
internal fun BatteryHealthPanel(
    state: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE20E9C8E), Color(0xE21F7E77)),
        title = "电池健康",
        icon = Icons.Outlined.HealthAndSafety,
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(0.92f)) {
                Text("SoH", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.currentSoh?.roundToInt()?.toString() ?: "--", color = Color.White, fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light)
                    Text("%", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(batteryHint(state), color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 2)
            }
            Column(modifier = Modifier.weight(1.28f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                WhiteMetric("电池均温", tempText(state.avgBatTemp))
                WhiteMetric("最高/最低", maxMinTempText(state))
                WhiteMetric("电芯压差", formatCellVoltageDelta(state.cellVoltageDelta))
                WhiteMetric("12V 电池", state.voltage12v?.let { "%.1f V".format(it) } ?: "--")
                WhiteMetric("充电枪", chargeGunText(state))
            }
        }
    }
}

@Composable
internal fun VehicleEnvironmentPanel(
    state: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xDF168FBD), Color(0xDF245AA8)),
        title = "车身环境",
        icon = Icons.Outlined.DirectionsCar,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.AcUnit, "空调", acMainText(state), state.acStatus == 1, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.Air, "风量", fanText(state), (state.fanLevel ?: 0) > 0, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.DirectionsCar, "内循环", circText(state.acCirc), state.acCirc == 1, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.LightMode, "灯光", lightMainText(state), lightsActive(state), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.Lock, "车锁", lockSummary(state), lockSummary(state) == "已锁", Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.DirectionsCar, "车门", doorSummary(state), doorSummary(state) == "全关", Modifier.weight(1f))
                EnvironmentItem(
                    Icons.Outlined.EvStation,
                    "充电口",
                    openCloseText(state.chargingCapCover),
                    BydSignalSemantics.isCoverOpen(state.chargingCapCover),
                    Modifier.weight(1f),
                )
                EnvironmentItem(
                    Icons.Outlined.LocalGasStation,
                    "油箱盖",
                    openCloseText(state.fuelCap),
                    BydSignalSemantics.isCoverOpen(state.fuelCap),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun TripRoutePanel(
    state: DashboardUiState,
    onOpenTripMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE3366BD5), Color(0xE34A57C8)),
        title = "行程与轨迹",
        icon = Icons.Outlined.Route,
        onClick = onOpenTripMap,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TripValue("本次", state.tripDistanceKm?.let { "%.1f km".format(it) } ?: "--", Modifier.weight(1f))
                TripValue("时长", tripDurationText(state.sessionStartedAt), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TripValue("用电", state.currentTripElecKwh?.let { "%.1f kWh".format(it) } ?: "--", Modifier.weight(1f))
                TripValue("电耗", state.elecConPer100km?.let { "%.1f kWh/100km".format(it) } ?: "--", Modifier.weight(1f))
            }
            TripHistoryBars(
                distances = state.recentTrips.mapNotNull { it.distanceKm?.takeIf { distance -> distance > 0.0 } },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(
                text = if (state.recentTrips.isEmpty()) "暂无行程记录" else "查看完整行程轨迹",
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ChargeRecordPanel(
    charge: ChargeEntity?,
    currencySymbol: String,
    onOpenCharges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE29354E8), Color(0xE35B54D9)),
        title = "充电记录",
        icon = Icons.Outlined.ElectricBolt,
        onClick = onOpenCharges,
    ) {
        if (charge == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.EvStation, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text("暂无充电记录", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("完成一次充电后会自动显示", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatChargeDate(charge.startTs), color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    HeroPill(chargeTypeText(charge), Color.White.copy(alpha = 0.20f))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(charge.socStart?.let { "$it%" } ?: "--", color = Color.White, fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  →  ", color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 3.dp))
                    Text(charge.socEnd?.let { "$it%" } ?: if (charge.endTs == null) "进行中" else "--", color = Color.White, fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                ChargeSocProgress(charge, Modifier.fillMaxWidth().height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TripValue("补能", chargeEnergyText(charge), Modifier.weight(1f))
                    TripValue("时长", chargeDurationText(charge), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TripValue("费用", charge.cost?.let { "%.2f %s".format(it, currencySymbol) } ?: "--", Modifier.weight(1f))
                    TripValue("平均功率", charge.avgPowerKw?.let { "%.1f kW".format(it) } ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun LocalInsightPanel(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val insight = state.localInsight
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.90f))
            .border(1.dp, GlassStroke, shape)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        val compact = maxWidth < 900.dp
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Insights, contentDescription = null, tint = SceneBlue, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.localInsightLoading) "本地洞察" else "本地洞察 · ${insight.title}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.localInsightLoading) "分析中" else if (insight.hasEnoughData) "本地分析完成" else "数据积累中",
                    color = if (insight.hasEnoughData) AccentGreen else AccentBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background((if (insight.hasEnoughData) AccentGreen else AccentBlue).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                text = if (state.localInsightLoading) "正在分析近期行程、充电和电池数据" else insight.summary.ifBlank { "本地数据积累中" },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.localInsightLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("所有分析均在车机本地完成，不上传车辆数据", color = TextSecondary, fontSize = 11.sp)
                }
            } else if (compact) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insight.metrics.take(6).chunked(3).forEach { rowMetrics ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowMetrics.forEach { metric -> LocalInsightMetricItem(metric, Modifier.weight(1f)) }
                            repeat(3 - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    insight.recommendations.take(2).forEach { InsightRecommendation(it) }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        insight.metrics.take(6).forEach { metric ->
                            LocalInsightMetricItem(metric, Modifier.weight(1f))
                        }
                    }
                    if (insight.recommendations.isNotEmpty()) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(TextPrimary.copy(alpha = 0.10f)))
                        Column(
                            modifier = Modifier.width(310.dp).padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            insight.recommendations.take(2).forEach { InsightRecommendation(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDarkPanel(
    modifier: Modifier,
    colors: List<Color>,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val interactiveModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(
        modifier = interactiveModifier
            .clip(shape)
            .background(Brush.linearGradient(colors), shape)
            .border(1.dp, Color.White.copy(alpha = 0.48f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun WhiteMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EnvironmentItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Icon(icon, contentDescription = null, tint = if (active) Color.White else Color.White.copy(alpha = 0.62f), modifier = Modifier.size(26.dp))
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TripValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TripHistoryBars(
    distances: List<Double>,
    modifier: Modifier = Modifier,
) {
    if (distances.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            Text("近期暂无可用行程数据", color = Color.White.copy(alpha = 0.66f), fontSize = 10.sp)
        }
        return
    }
    val values = distances.takeLast(7)
    val maximum = max(values.maxOrNull() ?: 0.0, 0.1)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        values.forEach { value ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((value / maximum).toFloat().coerceIn(0.12f, 1f))
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(Color.White.copy(alpha = 0.58f)),
            )
        }
    }
}

@Composable
private fun ChargeSocProgress(charge: ChargeEntity, modifier: Modifier = Modifier) {
    val start = charge.socStart?.coerceIn(0, 100)
    val end = charge.socEnd?.coerceIn(0, 100)
    val progress = when {
        end != null -> end / 100f
        start != null -> start / 100f
        else -> 0f
    }
    Box(modifier = modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.82f)),
            )
        }
    }
}

@Composable
private fun LocalInsightMetricItem(
    metric: LocalInsightMetric,
    modifier: Modifier = Modifier,
) {
    val color = insightToneColor(metric.tone)
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(metric.label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(metric.value, color = TextPrimary, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(metric.detail, color = TextSecondary, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InsightRecommendation(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AccentGreen.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = TextPrimary, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
