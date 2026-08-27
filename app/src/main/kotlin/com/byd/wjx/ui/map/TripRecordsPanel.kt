package com.byd.wjx.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
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
import com.byd.wjx.ui.theme.AccentOrange
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
    onSelectDashcamFolder: () -> Unit,
    onRefreshDashcamVideos: () -> Unit,
    onClearDashcamFolder: () -> Unit,
    onOpenDashcamVideos: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.68f)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "panel_header") {
                TripPanelHeader(state, onSelectTrip)
            }
            item(key = "trip_summary") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TripSummary(state)
                }
            }
            item(key = "dashcam_folder") {
                DashcamFolderRow(
                    state = state,
                    onSelectFolder = onSelectDashcamFolder,
                    onRefresh = onRefreshDashcamVideos,
                    onClear = onClearDashcamFolder,
                )
            }
            item(key = "list_header") {
                TripListHeader()
            }
            if (state.trips.isEmpty()) {
                item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("所选时间段暂无行程记录", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(state.trips.take(120), key = TripEntity::id) { trip ->
                    val hasRoute = state.tripRoutes.any { it.trip.id == trip.id }
                    val videoCount = state.dashcamVideosByTrip[trip.id]?.size ?: 0
                    TripRecordRow(
                        trip = trip,
                        hasRoute = hasRoute,
                        videoCount = videoCount,
                        selected = state.selectedTripId == trip.id,
                        onClick = { if (hasRoute) onSelectTrip(trip.id) },
                        onOpenVideos = { onOpenDashcamVideos(trip.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashcamFolderRow(
    state: MapUiState,
    onSelectFolder: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBorder.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = AccentBlue)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.dashcamFolderName ?: "尚未添加行车记录仪文件夹",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    state.dashcamScanning -> "正在扫描录像文件"
                    state.dashcamError != null -> state.dashcamError
                    state.dashcamFolderName != null -> "已识别 ${state.dashcamVideoCount} 段录像"
                    else -> "手动选择保存录像的文件夹"
                },
                color = if (state.dashcamError != null) AccentOrange else TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.dashcamFolderName == null) {
            Button(
                onClick = onSelectFolder,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Text("添加文件夹")
            }
        } else {
            IconButton(onClick = onSelectFolder, enabled = !state.dashcamScanning) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "更换录像文件夹", tint = AccentBlue)
            }
            IconButton(onClick = onRefresh, enabled = !state.dashcamScanning) {
                Icon(Icons.Outlined.Refresh, contentDescription = "重新扫描录像", tint = AccentGreen)
            }
            IconButton(onClick = onClear, enabled = !state.dashcamScanning) {
                Icon(Icons.Outlined.Delete, contentDescription = "清除录像文件夹", tint = AccentOrange)
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
        TripHeaderText("时间 / 时长", 1.25f)
        TripHeaderText("里程", 0.65f)
        TripHeaderText("用电", 0.65f)
        TripHeaderText("用油", 0.60f)
        TripHeaderText("电费 / 油费", 0.92f)
        TripHeaderText("录像", 0.72f)
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
    videoCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenVideos: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = hasRoute, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1.25f)) {
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
        TripValue(trip.distanceKm?.let { "%.1f km".format(Locale.CHINA, it) }, 0.65f)
        TripValue(trip.kwhConsumed?.let { "%.1f kWh".format(Locale.CHINA, it) }, 0.65f)
        TripValue(trip.fuelConsumedL?.let { "%.1f L".format(Locale.CHINA, it) }, 0.60f)
        Column(modifier = Modifier.weight(0.92f)) {
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
        if (videoCount > 0) {
            TextButton(
                onClick = onOpenVideos,
                modifier = Modifier.weight(0.72f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = AccentBlue,
                )
                Text("${videoCount}段", color = AccentBlue, fontSize = 10.sp, maxLines = 1)
            }
        } else {
            TripValue(null, 0.72f)
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
