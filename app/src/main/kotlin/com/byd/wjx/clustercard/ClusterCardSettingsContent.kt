package com.byd.wjx.clustercard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun ClusterCardSettingsContent(
    slot: ClusterCardSlot = ClusterCardSlot.PRIMARY,
    viewModel: ClusterCardSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState(slot).collectAsStateWithLifecycle()
    var showMetricPicker by remember { mutableStateOf(false) }

    LifecycleResumeEffect(slot) {
        viewModel.refresh(slot)
        onPauseOrDispose { }
    }

    SettingsPanel {
        ToggleRow(
            title = "启用${slot.label}",
            subtitle = if (state.config.metricIds.isEmpty()) {
                "请先添加至少一个指标，空卡片不会显示"
            } else {
                "车辆启动并连接仪表投射屏后自动显示"
            },
            checked = state.config.enabled,
            onCheckedChange = { viewModel.setEnabled(slot, it) },
        )
        HorizontalDivider(color = CardBorder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("悬浮窗权限", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.overlayPermissionGranted) "已允许，可在仪表盘显示" else "尚未允许，仪表卡片不会显示",
                    color = if (state.overlayPermissionGranted) AccentGreen else AccentOrange,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = viewModel::requestOverlayPermission,
                enabled = !state.permissionBusy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(
                    if (state.overlayPermissionGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Add,
                    contentDescription = null,
                )
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(if (state.overlayPermissionGranted) "重新检查" else if (state.permissionBusy) "授权中" else "拉起授权")
            }
        }
    }

    SettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "显示指标（${state.config.metricIds.size}/$CLUSTER_CARD_MAX_METRICS）",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("没有预设内容，按自己的需要添加并排序", color = TextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = { showMetricPicker = true },
                enabled = state.config.metricIds.size < CLUSTER_CARD_MAX_METRICS,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("添加")
            }
        }

        if (state.config.metricIds.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBorder.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = AccentBlue)
                Column {
                    Text("尚未添加仪表指标", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("可添加转速、温度、车速、能耗、胎压等数据", color = TextSecondary, fontSize = 11.sp)
                }
            }
        } else {
            state.config.metricIds.forEachIndexed { index, id ->
                val metric = ClusterCardMetricCatalog.byId[id] ?: return@forEachIndexed
                MetricRow(
                    metric = metric,
                    index = index,
                    count = state.config.metricIds.size,
                    onUp = { viewModel.moveMetric(slot, id, -1) },
                    onDown = { viewModel.moveMetric(slot, id, 1) },
                    onDelete = { viewModel.removeMetric(slot, id) },
                )
            }
        }
    }

    SettingsPanel {
        Text("卡片外观", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "通过下面的水平和垂直位置调节卡片，不提供仪表触摸操作",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        ConfigSlider(
            title = "整体大小",
            value = state.config.scale,
            valueRange = 0.45f..1f,
            displayValue = "${(state.config.scale * 100).roundToInt()}%",
            onValueChangeFinished = { viewModel.setScale(slot, it) },
        )
        ConfigSlider(
            title = "卡片宽度",
            value = state.config.widthScale,
            valueRange = 0.7f..1.4f,
            displayValue = "${(state.config.widthScale * 100).roundToInt()}%",
            onValueChangeFinished = { viewModel.setWidthScale(slot, it) },
        )
        ConfigSlider(
            title = "水平位置",
            value = state.config.xPct,
            valueRange = 0f..100f,
            displayValue = "${state.config.xPct.roundToInt()}%",
            onValueChangeFinished = { viewModel.setXPct(slot, it) },
        )
        ConfigSlider(
            title = "垂直位置",
            value = state.config.yPct,
            valueRange = 0f..100f,
            displayValue = "${state.config.yPct.roundToInt()}%",
            onValueChangeFinished = { viewModel.setYPct(slot, it) },
        )
        TextButton(onClick = { viewModel.resetPosition(slot) }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = AccentBlue)
            Spacer(Modifier.padding(horizontal = 3.dp))
            Text("恢复默认位置", color = AccentBlue)
        }
    }

    if (showMetricPicker) {
        MetricPicker(
            selected = state.config.metricIds.toSet(),
            onSelect = { viewModel.addMetric(slot, it) },
            onDismiss = { showMetricPicker = false },
        )
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = bydSwitchColors())
    }
}

@Composable
private fun MetricRow(
    metric: ClusterCardMetric,
    index: Int,
    count: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBorder.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            color = AccentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(metric.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(metric.category, color = TextSecondary, fontSize = 11.sp)
        }
        IconButton(onClick = onUp, enabled = index > 0) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", tint = if (index > 0) AccentBlue else TextMuted)
        }
        IconButton(onClick = onDown, enabled = index < count - 1) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", tint = if (index < count - 1) AccentBlue else TextMuted)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = AccentOrange)
        }
    }
}

@Composable
private fun ConfigSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChangeFinished: (Float) -> Unit,
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(displayValue, color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
                inactiveTrackColor = CardBorder,
            ),
        )
    }
}

@Composable
private fun MetricPicker(
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf(ClusterCardMetricCatalog.categories.first()) }
    val candidates = ClusterCardMetricCatalog.all.filter {
        it.category == category && it.id !in selected
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加仪表指标", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "已选择 ${selected.size}/$CLUSTER_CARD_MAX_METRICS",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(ClusterCardMetricCatalog.categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.24f),
                            ),
                        )
                    }
                }
                if (candidates.isEmpty()) {
                    Text("这个分类没有可继续添加的指标", color = TextMuted, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(candidates, key = ClusterCardMetric::id) { metric ->
                            TextButton(
                                onClick = { onSelect(metric.id) },
                                enabled = selected.size < CLUSTER_CARD_MAX_METRICS,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(metric.label, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(metric.category, color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = CardSurfaceElevated,
    )
}
