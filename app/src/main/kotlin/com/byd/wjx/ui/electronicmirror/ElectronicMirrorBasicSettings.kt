package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorDisplayTarget
import com.byd.wjx.electronicmirror.ElectronicMirrorGeometry
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun StreamingRearMirrorCard(
    config: ElectronicMirrorConfig,
    onUpdate: ((ElectronicMirrorConfig) -> ElectronicMirrorConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.42f)),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingSwitchRow(
                title = "启用流媒体后视镜",
                subtitle = "常驻后方宽屏；转向时复用同一条四画面母流，不重启摄像头",
                checked = config.streamingRearEnabled,
                onChecked = { value -> onUpdate { it.copy(streamingRearEnabled = value) } },
            )
            SettingSwitchRow(
                title = "当前显示",
                subtitle = "关闭只隐藏流媒体窗口，不会修改位置和画面微调参数",
                checked = config.streamingRearVisible,
                onChecked = { value -> onUpdate { it.copy(streamingRearVisible = value) } },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("流媒体显示位置", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TargetChip(
                        label = "仪表盘",
                        icon = Icons.Outlined.DirectionsCar,
                        selected = config.streamingRearDisplayTarget == ElectronicMirrorDisplayTarget.CLUSTER,
                    ) {
                        onUpdate {
                            it.copy(streamingRearDisplayTarget = ElectronicMirrorDisplayTarget.CLUSTER)
                        }
                    }
                    TargetChip(
                        label = "中控屏",
                        icon = Icons.Outlined.DesktopWindows,
                        selected = config.streamingRearDisplayTarget == ElectronicMirrorDisplayTarget.CENTER,
                    ) {
                        onUpdate {
                            it.copy(streamingRearDisplayTarget = ElectronicMirrorDisplayTarget.CENTER)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Surface 通道（高级）", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "默认使用通道 0 输出四格母流；只有实车无画面时才切换测试",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { surfaceIndex ->
                        FilterChip(
                            selected = config.panoramaSurfaceIndex == surfaceIndex,
                            onClick = {
                                onUpdate { it.copy(panoramaSurfaceIndex = surfaceIndex) }
                            },
                            label = { Text(surfaceIndex.toString(), fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.18f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.Transparent,
                                labelColor = TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = config.panoramaSurfaceIndex == surfaceIndex,
                                borderColor = CardBorder,
                                selectedBorderColor = AccentGreen.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MirrorControlCard(
    config: ElectronicMirrorConfig,
    onUpdate: ((ElectronicMirrorConfig) -> ElectronicMirrorConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingSwitchRow(
                title = "启用转向/倒挡联动",
                subtitle = "打开后按稳定转向状态自动显示，不跟随灯泡闪烁",
                checked = config.enabled,
                onChecked = { enabled -> onUpdate { it.copy(enabled = enabled) } },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("显示位置", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TargetChip(
                        label = "仪表盘",
                        icon = Icons.Outlined.DirectionsCar,
                        selected = config.displayTarget == ElectronicMirrorDisplayTarget.CLUSTER,
                    ) { onUpdate { it.copy(displayTarget = ElectronicMirrorDisplayTarget.CLUSTER) } }
                    TargetChip(
                        label = "中控屏",
                        icon = Icons.Outlined.DesktopWindows,
                        selected = config.displayTarget == ElectronicMirrorDisplayTarget.CENTER,
                    ) { onUpdate { it.copy(displayTarget = ElectronicMirrorDisplayTarget.CENTER) } }
                }
            }

            SettingSwitchRow(
                title = "左转联动",
                subtitle = "左转状态持续时显示左后侧画面",
                checked = config.leftTurnEnabled,
                onChecked = { value -> onUpdate { it.copy(leftTurnEnabled = value) } },
            )
            SettingSwitchRow(
                title = "右转联动",
                subtitle = "右转状态持续时显示右后侧画面",
                checked = config.rightTurnEnabled,
                onChecked = { value -> onUpdate { it.copy(rightTurnEnabled = value) } },
            )
            SettingSwitchRow(
                title = "倒挡后视（可选）",
                subtitle = "仅在原车倒车/全景影像未占用画面时启用",
                checked = config.reverseEnabled,
                onChecked = { value -> onUpdate { it.copy(reverseEnabled = value) } },
            )
        }
    }
}

@Composable
internal fun MirrorLayoutPreview(
    target: ElectronicMirrorDisplayTarget,
    geometry: ElectronicMirrorGeometry,
    modifier: Modifier = Modifier,
    windowLabel: String = "电子后视镜窗口",
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("布局预览", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (target == ElectronicMirrorDisplayTarget.CLUSTER) "1280 × 480 仪表盘比例" else "中控屏比例",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Text("黑底不透明 · 圆角画面", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val canvasHeight = if (target == ElectronicMirrorDisplayTarget.CLUSTER) {
                    maxWidth * 0.375f
                } else {
                    maxWidth * 0.5625f
                }
                val windowWidth = maxWidth * geometry.widthPct / 100f
                val windowHeight = canvasHeight * geometry.heightPct / 100f
                val x = (maxWidth - windowWidth) * geometry.offsetXPct / 100f
                val y = (canvasHeight - windowHeight) * geometry.offsetYPct / 100f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0C1A23), Color(0xFF142D36), Color(0xFF091116)),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                ) {
                    Text(
                        if (target == ElectronicMirrorDisplayTarget.CLUSTER) "仪表盘" else "中控屏",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(windowWidth, windowHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, AccentGreen.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                    ) {
                        Text(
                            windowLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        )
                        Text(
                            "● 实时",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GeometryEditor(
    geometry: ElectronicMirrorGeometry,
    target: ElectronicMirrorDisplayTarget,
    onUpdate: ((ElectronicMirrorGeometry) -> ElectronicMirrorGeometry) -> Unit,
    onCommit: () -> Unit,
    title: String = "${if (target == ElectronicMirrorDisplayTarget.CLUSTER) "仪表盘" else "中控屏"}窗口调整",
    horizontalLabel: String = "左右位置",
    horizontalRange: IntRange = 0..100,
    widthRange: IntRange = 24..100,
    heightRange: IntRange = 24..100,
    heightLabel: String = "窗口高度",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GeometrySlider(horizontalLabel, geometry.offsetXPct, horizontalRange, onCommit) { value ->
                        onUpdate { it.copy(offsetXPct = value) }
                    }
                    GeometrySlider("上下位置", geometry.offsetYPct, 0..100, onCommit) { value ->
                        onUpdate { it.copy(offsetYPct = value) }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GeometrySlider("窗口宽度", geometry.widthPct, widthRange, onCommit) { value ->
                        onUpdate { it.copy(widthPct = value) }
                    }
                    GeometrySlider(heightLabel, geometry.heightPct, heightRange, onCommit) { value ->
                        onUpdate { it.copy(heightPct = value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeometrySlider(
    label: String,
    value: Int,
    range: IntRange,
    onCommit: () -> Unit,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$value%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onCommit,
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked, colors = bydSwitchColors())
    }
}

@Composable
private fun TargetChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SceneBlue.copy(alpha = 0.2f),
            selectedLabelColor = TextPrimary,
            selectedLeadingIconColor = SceneBlue,
            containerColor = Color.Transparent,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CardBorder,
            selectedBorderColor = SceneBlue.copy(alpha = 0.6f),
        ),
    )
}
