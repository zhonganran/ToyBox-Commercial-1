package com.byd.wjx.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.ui.dashboard.DashboardUiState
import kotlin.math.max

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
                TripValue("用油", state.currentTripFuelL?.let { "%.1f L".format(it) } ?: "--", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TripValue("电费", state.currentTripElecCost?.let { "%.2f %s".format(it, state.currencySymbol) } ?: "--", Modifier.weight(1f))
                TripValue("油费", state.currentTripFuelCost?.let { "%.2f %s".format(it, state.currencySymbol) } ?: "--", Modifier.weight(1f))
            }
            TripHistoryBars(
                distances = state.recentTrips.mapNotNull {
                    it.distanceKm?.takeIf { distance -> distance > 0.0 }
                },
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
            EmptyChargeRecord()
        } else {
            ChargeRecordContent(charge, currencySymbol)
        }
    }
}

@Composable
private fun EmptyChargeRecord() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.EvStation,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("暂无充电记录", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("完成一次充电后会自动显示", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp)
    }
}

@Composable
private fun ChargeRecordContent(charge: ChargeEntity, currencySymbol: String) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatChargeDate(charge.startTs),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            HeroPill(chargeTypeText(charge), Color.White.copy(alpha = 0.20f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                charge.socStart?.let { "$it%" } ?: "--",
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "  →  ",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Text(
                charge.socEnd?.let { "$it%" } ?: if (charge.endTs == null) "进行中" else "--",
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        ChargeSocProgress(charge, Modifier.fillMaxWidth().height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TripValue("补能", chargeEnergyText(charge), Modifier.weight(1f))
            TripValue("时长", chargeDurationText(charge), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TripValue(
                "费用",
                charge.cost?.let { "%.2f %s".format(it, currencySymbol) } ?: "--",
                Modifier.weight(1f),
            )
            TripValue(
                "平均功率",
                charge.avgPowerKw?.let { "%.1f kW".format(it) } ?: "--",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TripValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TripHistoryBars(distances: List<Double>, modifier: Modifier = Modifier) {
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
                modifier = Modifier.weight(1f)
                    .fillMaxHeight((value / maximum).toFloat().coerceIn(0.12f, 1f))
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(Color.White.copy(alpha = 0.58f)),
            )
        }
    }
}

@Composable
private fun ChargeSocProgress(charge: ChargeEntity, modifier: Modifier = Modifier) {
    val progress = when (val end = charge.socEnd?.coerceIn(0, 100)) {
        null -> charge.socStart?.coerceIn(0, 100)?.div(100f) ?: 0f
        else -> end / 100f
    }
    Box(modifier = modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
        if (progress > 0f) {
            Box(
                modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.82f)),
            )
        }
    }
}
