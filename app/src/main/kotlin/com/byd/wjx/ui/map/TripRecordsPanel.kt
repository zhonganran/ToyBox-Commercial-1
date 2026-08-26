package com.byd.wjx.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.ui.components.consumptionColor
import com.byd.wjx.ui.components.formatTime
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.util.Locale

@Composable
internal fun TripRecordsPanel(
    state: MapUiState,
    onSelectTrip: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.68f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TripPanelHeader(state, onSelectTrip)
            TripSummary(state)
            TripListHeader()
            if (state.trips.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("所选时间段暂无行程记录", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(state.trips.take(120), key = TripEntity::id) { trip ->
                        val hasRoute = state.tripRoutes.any { it.trip.id == trip.id }
                        TripRecordRow(
                            trip = trip,
                            hasRoute = hasRoute,
                            selected = state.selectedTripId == trip.id,
                            onClick = { if (hasRoute) onSelectTrip(trip.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripPanelHeader(state: MapUiState, onSelectTrip: (Long?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.period.panelTitle(),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${state.trips.size} 次行程 · ${state.tripRoutes.size} 条可用轨迹",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
        if (state.selectedTripId != null) {
            Text(
                "显示全部",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectTrip(null) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TripSummary(state: MapUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TripSummaryMetric(
            "行驶时长",
            formatTripDuration(state.totalDurationMs),
            AccentBlue,
            Modifier.weight(1f),
        )
        TripSummaryMetric(
            "总里程",
            "%.1f km".format(Locale.CHINA, state.totalKm),
            AccentGreen,
            Modifier.weight(1f),
        )
        TripSummaryMetric(
            "总用电",
            "%.1f kWh".format(Locale.CHINA, state.totalKwh),
            SceneAmber,
            Modifier.weight(1f),
        )
        TripSummaryMetric(
            "总用油",
            "%.1f L".format(Locale.CHINA, state.totalFuelL),
            AccentBlue,
            Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TripSummaryMetric(
            "总电费",
            "%.2f 元".format(Locale.CHINA, state.totalElectricCost),
            AccentGreen,
            Modifier.weight(1f),
        )
        TripSummaryMetric(
            "总油费",
            "%.2f 元".format(Locale.CHINA, state.totalFuelCost),
            SceneAmber,
            Modifier.weight(1f),
        )
        TripSummaryMetric(
            "平均电耗",
            state.avgConsumption.positiveFormat("%.1f/100km"),
            if (state.avgConsumption > 0) consumptionColor(state.avgConsumption) else TextMuted,
            Modifier.weight(1f),
        )
        TripSummaryMetric(
            "平均油耗",
            state.avgFuelConsumption.positiveFormat("%.1f L/100km"),
            AccentBlue,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun TripSummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.height(62.dp).clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
        Text(
            value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TripListHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp)) {
        TripHeaderText("时间 / 时长", 1.35f)
        TripHeaderText("里程", 0.72f)
        TripHeaderText("用电", 0.72f)
        TripHeaderText("用油", 0.68f)
        TripHeaderText("电费 / 油费", 1.05f)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TripHeaderText(
    text: String,
    weight: Float,
) {
    Text(text, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun TripRecordRow(
    trip: TripEntity,
    hasRoute: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = hasRoute, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1.35f)) {
            Text(
                formatTime(trip.startTs),
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                (trip.endTs?.let { formatTripDuration(it - trip.startTs) } ?: "进行中") +
                    if (hasRoute) " · 有轨迹" else " · 无轨迹",
                color = if (hasRoute) AccentGreen else TextMuted,
                fontSize = 10.sp,
            )
        }
        TripValue(trip.distanceKm?.let { "%.1f km".format(Locale.CHINA, it) }, 0.72f)
        TripValue(trip.kwhConsumed?.let { "%.1f kWh".format(Locale.CHINA, it) }, 0.72f)
        TripValue(trip.fuelConsumedL?.let { "%.1f L".format(Locale.CHINA, it) }, 0.68f)
        Column(modifier = Modifier.weight(1.05f)) {
            Text(
                trip.cost?.let { "电 %.2f 元".format(Locale.CHINA, it) } ?: "电 --",
                color = AccentGreen,
                fontSize = 10.sp,
            )
            Text(
                trip.fuelCost?.let { "油 %.2f 元".format(Locale.CHINA, it) } ?: "油 --",
                color = SceneAmber,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TripValue(value: String?, weight: Float) {
    Text(
        value ?: "--",
        color = TextPrimary,
        fontSize = 11.sp,
        modifier = Modifier.weight(weight),
    )
}

private fun MapPeriod.panelTitle(): String = when (this) {
    MapPeriod.DAY -> "今日行程"
    MapPeriod.WEEK -> "近 7 天行程"
    MapPeriod.MONTH -> "近 30 天行程"
    MapPeriod.YEAR -> "近一年行程"
    MapPeriod.ALL -> "全部行程"
}

private fun Double.positiveFormat(pattern: String): String =
    if (this > 0) pattern.format(Locale.CHINA, this) else "--"
