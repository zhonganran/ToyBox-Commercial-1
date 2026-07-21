package com.byd.wjx.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.dashboard.DashboardViewModel
import com.byd.wjx.ui.dashboard.EnergyFlowDetailRows
import com.byd.wjx.ui.dashboard.SystemMetricsDetailRows
import com.byd.wjx.ui.dashboard.energyFlowCategoryLabel
import com.byd.wjx.ui.dashboard.formatEnergyFlowKwh
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun EnergyRecoveryScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.sessionEnergyFlow
    val metrics = listOf(
        OperationalMetric(
            label = "本次回收",
            value = "${formatEnergyFlowKwh(session.totalRegenKwh)} kWh",
            supporting = energyFlowCategoryLabel(state.energyFlowCategory),
            color = AccentGreen,
            icon = Icons.Outlined.ElectricBolt,
        ),
        OperationalMetric(
            label = "本次发动机发电",
            value = "${formatEnergyFlowKwh(session.engineGenerationKwh)} kWh",
            supporting = state.energyState?.let { "能量状态 $it" } ?: "能量状态暂无数据",
            color = SceneAmber,
            icon = Icons.Outlined.ElectricBolt,
        ),
        OperationalMetric(
            label = "累计回收",
            value = "${formatEnergyFlowKwh(state.lifetimeEnergyFlow.totalRegenKwh)} kWh",
            supporting = "应用记录期内",
            color = AccentBlue,
            icon = Icons.Outlined.ElectricBolt,
        ),
    )

    OperationalPage(
        eyebrow = "ENERGY RECOVERY",
        title = "能量回收",
        subtitle = "单独展示本次行程与累计能量回收数据",
        statusText = "能量数据",
        statusActive = state.vehicleDataConnected,
        metrics = metrics,
        onBack = onBack,
    ) {
        OperationalSectionTitle("本次行程", Icons.Outlined.ElectricBolt, AccentGreen)
        OperationalDetailCard(borderColor = AccentGreen) {
            EnergyFlowDetailRows("回收明细", session)
        }
        OperationalSectionTitle("累计统计", Icons.Outlined.ElectricBolt, AccentBlue)
        OperationalDetailCard(borderColor = AccentBlue) {
            EnergyFlowDetailRows("历史累计", state.lifetimeEnergyFlow)
        }
    }
}

@Composable
fun VehicleSystemScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setSystemMetricsActive(true)
        onDispose { viewModel.setSystemMetricsActive(false) }
    }
    val snapshot = state.systemMetrics
    val metrics = listOf(
        OperationalMetric(
            label = "CPU 总占用",
            value = snapshot?.cpuTotalUsagePercent?.let { "$it%" }
                ?: if (state.systemMetricsSampleCount < 2) "采样中" else "--",
            supporting = snapshot?.let { "${it.cpuCoreCount} 核心" } ?: "等待系统数据",
            color = AccentBlue,
            icon = Icons.Outlined.Memory,
        ),
        OperationalMetric(
            label = "内存占用",
            value = snapshot?.memory?.let { "${it.usedPercent}%" } ?: "--",
            supporting = snapshot?.memory?.let { "可用 ${it.availableMb} MB" } ?: "系统节点暂无数据",
            color = AccentGreen,
            icon = Icons.Outlined.Memory,
        ),
        OperationalMetric(
            label = "存储空间",
            value = snapshot?.storageVolumes?.firstOrNull()?.let { "${it.usedPercent}%" } ?: "--",
            supporting = snapshot?.storageVolumes?.firstOrNull()?.let { "可用 %.1f GB".format(it.freeGb) }
                ?: "未读取到分区信息",
            color = SceneAmber,
            icon = Icons.Outlined.Memory,
        ),
        OperationalMetric(
            label = "GPU",
            value = snapshot?.gpuReadings?.firstOrNull()?.value ?: "--",
            supporting = snapshot?.gpuReadings?.firstOrNull()?.label ?: "当前车机未提供 GPU 节点",
            color = AccentBlue,
            icon = Icons.Outlined.Memory,
        ),
    )

    OperationalPage(
        eyebrow = "VEHICLE SYSTEM",
        title = "车机系统监控",
        subtitle = "CPU、内存、存储、GPU、温度与当前应用进程",
        statusText = "系统监控",
        statusActive = snapshot != null,
        metrics = metrics,
        onBack = onBack,
    ) {
        OperationalSectionTitle("完整系统信息", Icons.Outlined.Memory, AccentBlue)
        OperationalDetailCard(borderColor = AccentBlue) {
            SystemMetricsDetailRows(
                snapshot = snapshot,
                loading = state.systemMetricsLoading,
                sampleReady = state.systemMetricsSampleCount >= 2,
            )
        }
    }
}

@Composable
private fun OperationalPage(
    eyebrow: String,
    title: String,
    subtitle: String,
    statusText: String,
    statusActive: Boolean,
    metrics: List<OperationalMetric>,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 30.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeDetailHeader(
                eyebrow = eyebrow,
                title = title,
                subtitle = subtitle,
                connected = statusActive,
                statusText = statusText,
                onBack = onBack,
            )
            OperationalMetricGrid(metrics, columns = if (wide) metrics.size else 2)
            content()
            Spacer(Modifier.height(2.dp))
        }
    }
}

private data class OperationalMetric(
    val label: String,
    val value: String,
    val supporting: String,
    val color: Color,
    val icon: ImageVector,
)

@Composable
private fun OperationalMetricGrid(metrics: List<OperationalMetric>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        metrics.chunked(columns).forEach { rowMetrics ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowMetrics.forEach { metric -> OperationalMetricCard(metric, Modifier.weight(1f)) }
                repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OperationalMetricCard(metric: OperationalMetric, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, metric.color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(metric.icon, contentDescription = null, tint = metric.color)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(metric.label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                Text(
                    metric.value,
                    color = metric.color,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(metric.supporting, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OperationalSectionTitle(title: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color)
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OperationalDetailCard(
    borderColor: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.48f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.height(1.dp))
            content()
            Spacer(Modifier.height(1.dp))
        }
    }
}
