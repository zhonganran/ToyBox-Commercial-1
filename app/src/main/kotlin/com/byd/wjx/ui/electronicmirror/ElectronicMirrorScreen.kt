package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorDisplayTarget
import com.byd.wjx.electronicmirror.ElectronicMirrorGeometry
import com.byd.wjx.electronicmirror.ElectronicMirrorRuntimeStatus
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
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
fun ElectronicMirrorScreen(
    onBack: () -> Unit,
    viewModel: ElectronicMirrorViewModel = hiltViewModel(),
) {
    ElectronicMirrorSettingsContent(
        embedded = false,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun ElectronicMirrorSettingsContent(
    embedded: Boolean,
    onBack: () -> Unit = {},
    viewModel: ElectronicMirrorViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val savedRevision by viewModel.savedRevision.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose(viewModel::stopPreview)
    }

    Column(
        modifier = if (embedded) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 22.dp)
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (embedded) {
            MirrorEmbeddedStatus(active = active, status = status)
        } else {
            MirrorHeader(onBack = onBack, active = active, status = status)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MirrorControlCard(
                config = config,
                onUpdate = viewModel::update,
                modifier = Modifier.weight(0.9f),
            )
            MirrorLayoutPreview(
                target = config.displayTarget,
                geometry = config.geometryFor(),
                modifier = Modifier.weight(1.1f),
            )
        }

        GeometryEditor(
            geometry = config.geometryFor(),
            target = config.displayTarget,
            onUpdate = viewModel::updateSelectedGeometry,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = viewModel::save,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color(0xFF071A16)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (savedRevision > 0) "保存布局" else "保存设置", fontWeight = FontWeight.Bold)
            }
            PreviewButton("预览左后侧", ElectronicMirrorViewpoint.LEFT, viewModel::preview)
            PreviewButton("预览右后侧", ElectronicMirrorViewpoint.RIGHT, viewModel::preview)
            PreviewButton("预览正后方", ElectronicMirrorViewpoint.REAR, viewModel::preview)
            Button(
                onClick = viewModel::stopPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardSurface.copy(alpha = 0.82f),
                    contentColor = TextPrimary,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("停止预览", fontWeight = FontWeight.Bold)
            }
        }

        Text(
            "画面优先级：原车自动泊车/全景影像 ＞ 倒挡 ＞ 转向灯 ＞ 仪表投屏和歌词。退出转向后会自动恢复此前内容。",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun MirrorEmbeddedStatus(
    active: Boolean,
    status: ElectronicMirrorRuntimeStatus,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) AccentGreen.copy(alpha = 0.5f) else CardBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(9.dp).background(if (active) AccentGreen else TextMuted, CircleShape))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (active) "电子后视镜运行中" else "电子后视镜待机",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(status.message, color = TextSecondary, fontSize = 12.sp)
            }
            Text(
                "30 FPS · Surface 直出",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MirrorHeader(
    onBack: () -> Unit,
    active: Boolean,
    status: ElectronicMirrorRuntimeStatus,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CardSurface.copy(alpha = 0.72f)),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = TextPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("电子后视镜", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("转向灯触发 APA 侧后方画面，仪表盘与中控屏可分别调整布局", color = TextSecondary, fontSize = 14.sp)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) AccentGreen.copy(alpha = 0.18f) else CardSurface.copy(alpha = 0.72f))
                .border(1.dp, if (active) AccentGreen.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (active) AccentGreen else TextMuted, CircleShape))
            Text(status.message, color = if (active) AccentGreen else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MirrorControlCard(
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
                title = "启用电子后视镜",
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
private fun MirrorLayoutPreview(
    target: ElectronicMirrorDisplayTarget,
    geometry: ElectronicMirrorGeometry,
    modifier: Modifier = Modifier,
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
                Text("30 FPS · Surface 直出", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val canvasHeight = if (target == ElectronicMirrorDisplayTarget.CLUSTER) maxWidth * 0.375f else maxWidth * 0.5625f
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
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF153B47), Color(0xFF315B5A), Color(0xFF17333D)),
                                ),
                            )
                            .border(2.dp, AccentGreen, RoundedCornerShape(12.dp)),
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.align(Alignment.Center).size(30.dp),
                        )
                        Text(
                            "电子后视镜窗口",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeometryEditor(
    geometry: ElectronicMirrorGeometry,
    target: ElectronicMirrorDisplayTarget,
    onUpdate: ((ElectronicMirrorGeometry) -> ElectronicMirrorGeometry) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${if (target == ElectronicMirrorDisplayTarget.CLUSTER) "仪表盘" else "中控屏"}窗口调整",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GeometrySlider("左右位置", geometry.offsetXPct, 0..100) { value ->
                        onUpdate { it.copy(offsetXPct = value) }
                    }
                    GeometrySlider("上下位置", geometry.offsetYPct, 0..100) { value ->
                        onUpdate { it.copy(offsetYPct = value) }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GeometrySlider("窗口宽度", geometry.widthPct, 24..100) { value ->
                        onUpdate { it.copy(widthPct = value) }
                    }
                    GeometrySlider("窗口高度", geometry.heightPct, 24..100) { value ->
                        onUpdate { it.copy(heightPct = value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeometrySlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$value%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun PreviewButton(
    label: String,
    viewpoint: ElectronicMirrorViewpoint,
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
) {
    Button(
        onClick = { onPreview(viewpoint) },
        colors = ButtonDefaults.buttonColors(
            containerColor = SceneBlue.copy(alpha = 0.18f),
            contentColor = TextPrimary,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(52.dp),
    ) {
        Icon(Icons.Outlined.Visibility, contentDescription = null)
        Spacer(Modifier.width(7.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}
