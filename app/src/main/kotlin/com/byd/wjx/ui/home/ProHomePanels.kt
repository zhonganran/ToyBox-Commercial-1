package com.byd.wjx.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.dashboard.energyFlowCategoryLabel
import com.byd.wjx.ui.dashboard.formatEnergyFlowKwh
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
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
internal fun HomeMetricRibbon(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val metrics = homeRibbonMetrics(state)
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier.clip(shape).background(CardSurface.copy(alpha = 0.86f))
            .border(1.dp, GlassStroke, shape)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (compact || maxWidth < 900.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { item -> RibbonMetricItem(item, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                metrics.forEachIndexed { index, item ->
                    RibbonMetricItem(item, Modifier.weight(1f))
                    if (index < metrics.lastIndex) {
                        Box(
                            Modifier.width(1.dp).height(42.dp)
                                .background(TextPrimary.copy(alpha = 0.08f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeOperationsPanel(
    state: DashboardUiState,
    onOpenEnergyRecovery: () -> Unit,
    onOpenVehicleSystem: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val energyCard: @Composable (Modifier) -> Unit = { cardModifier ->
        HomeOperationCard(
            title = "本次能量回收",
            value = "${formatEnergyFlowKwh(state.sessionEnergyFlow.totalRegenKwh)} kWh",
            supporting = energyFlowCategoryLabel(state.energyFlowCategory),
            icon = Icons.Outlined.ElectricBolt,
            color = AccentGreen,
            onClick = onOpenEnergyRecovery,
            modifier = cardModifier,
        )
    }
    val systemCard: @Composable (Modifier) -> Unit = { cardModifier ->
        val metrics = state.systemMetrics
        HomeOperationCard(
            title = "车机系统",
            value = metrics?.cpuTotalUsagePercent?.let { "CPU $it%" }
                ?: if (state.systemMetricsLoading || state.systemMetricsSampleCount < 2) {
                    "CPU 采样中"
                } else {
                    "CPU --"
                },
            supporting = metrics?.memory?.let {
                "内存 ${it.usedPercent}% · ${metrics.cpuCoreCount} 核心"
            } ?: if (state.systemMetricsLoading) "正在读取系统信息" else "系统节点暂不可读",
            icon = Icons.Outlined.Memory,
            color = AccentBlue,
            onClick = onOpenVehicleSystem,
            modifier = cardModifier,
        )
    }
    if (stacked) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            energyCard(Modifier.weight(1f).fillMaxWidth())
            systemCard(Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            energyCard(Modifier.weight(1f).fillMaxHeight())
            systemCard(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun HomeOperationCard(
    title: String,
    value: String,
    supporting: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp)
                    .background(color.copy(alpha = 0.13f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    value,
                    color = color,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    supporting,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private data class RibbonMetric(
    val label: String,
    val value: String,
    val unit: String,
    val icon: ImageVector,
    val color: Color,
    val progress: Float,
    val extraValue: String? = null,
)

private fun homeRibbonMetrics(state: DashboardUiState): List<RibbonMetric> = listOf(
    RibbonMetric("纯电续航", state.electricRangeKm?.toString() ?: "--", "km", Icons.Outlined.EvStation, SceneMint, rangeProgress(state.electricRangeKm, 320)),
    RibbonMetric("燃油续航", state.fuelRangeKm?.toString() ?: "--", "km", Icons.Outlined.LocalGasStation, SceneBlue, rangeProgress(state.fuelRangeKm, 800)),
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
    RibbonMetric(
        "实时功率",
        state.power?.let { "%.1f".format(it) } ?: "--",
        "kW",
        Icons.Outlined.ElectricBolt,
        AccentOrange,
        (abs(state.power ?: 0.0) / 80.0).toFloat().coerceIn(0f, 1f),
    ),
    RibbonMetric(
        "总里程",
        state.odometer?.let { "%,.0f".format(it) } ?: "--",
        "km",
        Icons.Outlined.Speed,
        ScenePink,
        0.72f,
    ),
)

@Composable
private fun RibbonMetricItem(metric: RibbonMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Column {
                Text(
                    metric.label,
                    color = TextPrimary.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        metric.value,
                        color = TextPrimary,
                        fontSize = 23.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        metric.unit,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    metric.extraValue?.let { extraValue ->
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "· $extraValue",
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
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                .background(TextPrimary.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier.fillMaxWidth(metric.progress.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(CircleShape).background(metric.color),
            )
        }
    }
}
