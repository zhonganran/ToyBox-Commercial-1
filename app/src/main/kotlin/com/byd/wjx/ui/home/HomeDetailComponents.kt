package com.byd.wjx.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.pro.ProStatusPill
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.util.Locale

internal data class HomeDetailMetric(
    val label: String,
    val value: String,
    val color: Color,
    val supporting: String? = null,
)

@Composable
internal fun HomeDetailHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    connected: Boolean,
    statusText: String = "车辆数据",
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = TextPrimary)
        }
        Spacer(Modifier.size(8.dp))
        ProPageHeader(
            eyebrow = eyebrow,
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
            trailing = { ProStatusPill(statusText, connected) },
        )
    }
}

@Composable
internal fun BatteryHealthSummary(
    state: DashboardUiState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.HealthAndSafety, contentDescription = null, tint = color)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("动力电池 SoH", color = TextSecondary, fontSize = 13.sp)
                    Text(batteryHint(state), color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.currentSoh?.let { "%.0f".format(Locale.CHINA, it) } ?: "--",
                    color = color,
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                )
                Text("%", color = color, fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                if (state.isCharging) "当前正在充电" else "当前未充电",
                color = if (state.isCharging) AccentGreen else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun HomeDetailSection(
    title: String,
    icon: ImageVector,
    metrics: List<HomeDetailMetric>,
    columns: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = SceneBlue, modifier = Modifier.size(20.dp))
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        HomeDetailMetricGrid(metrics, columns, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun HomeDetailMetricGrid(
    metrics: List<HomeDetailMetric>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        metrics.chunked(columns).forEach { rowMetrics ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowMetrics.forEach { metric -> HomeDetailMetricCard(metric, Modifier.weight(1f)) }
                repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HomeDetailMetricCard(metric: HomeDetailMetric, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.62f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(metric.label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            Text(
                metric.value,
                color = metric.color,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metric.supporting?.let { Text(it, color = TextMuted, fontSize = 10.sp, maxLines = 1) }
        }
    }
}
