package com.byd.wjx.ui.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneSky
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class LyricPreviewType(val label: String) {
    DesktopLyric("桌面歌词"),
    ClusterLyric("仪表盘歌词"),
}

@Composable
internal fun LyricDisplayHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("歌词显示", color = TextPrimary, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("管理中控桌面与仪表盘歌词", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun LyricPreviewStage(
    selected: LyricPreviewType,
    onSelected: (LyricPreviewType) -> Unit,
    desktopLyricEnabled: Boolean,
    clusterLyricEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabledMap = mapOf(
        LyricPreviewType.DesktopLyric to desktopLyricEnabled,
        LyricPreviewType.ClusterLyric to clusterLyricEnabled,
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LyricPreviewType.entries.forEach { type ->
                val isSelected = type == selected
                LyricPreviewCard(
                    type = type,
                    enabled = enabledMap[type] == true,
                    selected = isSelected,
                    onClick = { onSelected(type) },
                    modifier = Modifier
                        .weight(if (isSelected) 1.15f else 0.85f)
                        .fillMaxHeight(if (isSelected) 1f else 0.88f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LyricPreviewType.entries.forEach { type ->
                Box(
                    modifier = Modifier
                        .width(if (type == selected) 22.dp else 10.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (type == selected) Color.White else Color.White.copy(alpha = 0.46f)),
                )
            }
        }
    }
}

@Composable
private fun LyricPreviewCard(
    type: LyricPreviewType,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var motionPhase by remember(type) { mutableFloatStateOf(if (type == LyricPreviewType.DesktopLyric) 0.26f else 0.44f) }
    LaunchedEffect(selected, type) {
        if (!selected) return@LaunchedEffect
        while (true) {
            delay(50L)
            motionPhase = (motionPhase + 0.0065f) % 1f
        }
    }
    val colors = when (type) {
        LyricPreviewType.DesktopLyric -> listOf(Color(0xFF2375B7), Color(0xFF174779))
        LyricPreviewType.ClusterLyric -> listOf(Color(0xFF123F83), Color(0xFF071F4B))
    }
    val shape = RoundedCornerShape(if (selected) 22.dp else 16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(colors), shape)
            .border(if (selected) 2.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.88f else 0.32f), shape)
            .clickable(onClick = onClick),
    ) {
        LyricCardBackground(type, selected, motionPhase, Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = if (selected) 0.02f else 0.08f),
                        0.72f to Color.Black.copy(alpha = if (selected) 0.12f else 0.20f),
                        1f to Color.Black.copy(alpha = if (selected) 0.28f else 0.34f),
                    ),
                ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(if (selected) 24.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type.label, color = Color.White, fontSize = if (selected) 18.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (enabled) "已开启" else "未开启",
                    color = if (enabled) Color(0xFF65F0C2) else Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LyricPreviewContent(selected, cluster = type == LyricPreviewType.ClusterLyric)
            }
            if (selected) {
                Spacer(Modifier.height(10.dp))
                PreviewPulse(motionPhase)
            }
        }
    }
}

@Composable
private fun LyricCardBackground(
    type: LyricPreviewType,
    selected: Boolean,
    motionPhase: Float,
    modifier: Modifier = Modifier,
) {
    val strength = if (selected) 1f else 0.48f
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val phaseAngle = motionPhase * (PI * 2.0).toFloat()
        when (type) {
            LyricPreviewType.DesktopLyric -> {
                repeat(4) { band ->
                    val path = Path()
                    repeat(37) { index ->
                        val x = w * index / 36f
                        val y = h * (0.58f + band * 0.075f) +
                            sin(index * 0.34f + phaseAngle + band * 0.8f) * h * (0.045f + band * 0.006f) * strength
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path,
                        Color.White.copy(alpha = (0.18f - band * 0.025f) * strength),
                        style = Stroke((2.4f - band * 0.3f).dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                val sweepX = w * motionPhase
                drawLine(
                    SceneCyan.copy(alpha = 0.34f * strength),
                    Offset(sweepX, h * 0.26f),
                    Offset(sweepX, h * 0.88f),
                    strokeWidth = 1.2f.dp.toPx(),
                )
            }
            LyricPreviewType.ClusterLyric -> {
                val vanishing = Offset(w * 0.50f, h * 0.66f)
                repeat(7) { index ->
                    drawLine(
                        SceneSky.copy(alpha = 0.18f * strength),
                        Offset(w * index / 6f, h),
                        vanishing,
                        strokeWidth = 1.2f.dp.toPx(),
                    )
                }
                repeat(5) { index ->
                    val progress = (motionPhase + index * 0.20f) % 1f
                    val y = h * (0.66f + progress * 0.30f)
                    val halfWidth = w * progress * 0.42f
                    drawLine(
                        SceneCyan.copy(alpha = (0.30f - progress * 0.20f) * strength),
                        Offset(w * 0.50f - halfWidth, y),
                        Offset(w * 0.50f + halfWidth, y),
                        strokeWidth = 1.4f.dp.toPx(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricPreviewContent(selected: Boolean, cluster: Boolean) {
    val lines = listOf("穿过人海 别停下来", "去更远的地方", "看更多的风景", "遇见更好的自己", "让故事继续")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (selected) 10.dp else 7.dp)) {
        if (cluster) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = Color.White.copy(alpha = 0.40f), modifier = Modifier.size(if (selected) 54.dp else 36.dp))
        }
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                color = if (index == 2) Color.White else Color.White.copy(alpha = 0.62f),
                fontSize = when {
                    selected && index == 2 -> 20.sp
                    selected -> 15.sp
                    index == 2 -> 14.sp
                    else -> 11.sp
                },
                fontWeight = if (index == 2) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewPulse(motionPhase: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
        repeat(13) { index ->
            val pulse = ((sin((index * 0.72f + motionPhase * (PI * 2.0)).toFloat()) + 1f) * 0.5f)
            val barHeight = size.height * (0.28f + pulse * 0.72f)
            val x = size.width * (index + 1) / 14f
            drawLine(
                Color.White.copy(alpha = 0.70f),
                Offset(x, (size.height - barHeight) / 2f),
                Offset(x, (size.height + barHeight) / 2f),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun LyricQuickControlPanel(
    selected: LyricPreviewType,
    desktopLyricEnabled: Boolean,
    clusterLyricEnabled: Boolean,
    desktopLyricScale: Float,
    clusterLyricScale: Float,
    clusterLyricAlpha: Float,
    onToggle: (Boolean) -> Unit,
    onPrimaryValueChange: (Float) -> Unit,
    onSecondaryValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val enabled = if (selected == LyricPreviewType.DesktopLyric) desktopLyricEnabled else clusterLyricEnabled
    val primaryValue = if (selected == LyricPreviewType.DesktopLyric) desktopLyricScale else clusterLyricScale
    val primaryRange = if (selected == LyricPreviewType.DesktopLyric) {
        LyricSettings.FONT_MIN..LyricSettings.FONT_MAX
    } else {
        LyricSettings.CLUSTER_FONT_MIN..LyricSettings.CLUSTER_FONT_MAX
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.86f))
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 30.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(selected.label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Switch(checked = enabled, onCheckedChange = onToggle, colors = com.byd.wjx.ui.components.bydSwitchColors())
            Text(if (enabled) "已开启" else "已关闭", color = if (enabled) AccentGreen else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        QuickSlider(
            label = "字体大小",
            value = primaryValue,
            range = primaryRange,
            enabled = enabled,
            onValueChange = onPrimaryValueChange,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        if (selected == LyricPreviewType.DesktopLyric) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("歌词颜色", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Color.White, SceneMint, SceneBlue, ScenePink, SceneAmber, SceneViolet).forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(color)
                                .border(if (index == 0) 2.dp else 1.dp, if (index == 0) SceneBlue else Color.White, RoundedCornerShape(9.dp)),
                        )
                    }
                }
                Text("仅桌面显示 · 透明背景", color = TextSecondary, fontSize = 11.sp)
            }
        } else {
            QuickSlider(
                label = "透明度",
                value = clusterLyricAlpha,
                range = LyricSettings.ALPHA_MIN..LyricSettings.ALPHA_MAX,
                enabled = enabled,
                onValueChange = onSecondaryValueChange,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onReset,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SceneViolet.copy(alpha = 0.78f), contentColor = Color.White),
                modifier = Modifier.width(126.dp).height(48.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                Text("重置位置", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Text("实时预览", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun QuickSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = SceneBlue,
                inactiveTrackColor = TextPrimary.copy(alpha = 0.10f),
            ),
        )
    }
}

@Composable
internal fun DetailToggleRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurfaceElevated.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (expanded) AccentGreen else CardSurface,
                contentColor = if (expanded) AccentContent else TextPrimary,
            ),
            modifier = Modifier.height(38.dp),
        ) {
            Text(if (expanded) "收起调节" else "展开调节", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
internal fun ClusterLyricNudgeControls(
    enabled: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("仪表位置微调", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NudgeButton("上", enabled, onUp, Modifier.weight(1f))
            NudgeButton("下", enabled, onDown, Modifier.weight(1f))
            NudgeButton("左", enabled, onLeft, Modifier.weight(1f))
            NudgeButton("右", enabled, onRight, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated, contentColor = TextPrimary),
        modifier = modifier.height(38.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun PresetChipRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (id, label) ->
                val selected = id == selectedId
                Button(
                    onClick = { onSelect(id) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AccentGreen else CardSurfaceElevated,
                        contentColor = if (selected) AccentContent else TextPrimary,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun ColorPresetChipRow(
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("歌词颜色", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LyricStylePresets.colors.forEach { preset ->
                val selected = preset.id == selectedId
                Button(
                    onClick = { onSelect(preset.id) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(preset.currentColor) else CardSurfaceElevated,
                        contentColor = if (selected) Color.Black else TextPrimary,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(preset.label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = com.byd.wjx.ui.components.bydSwitchColors(),
        )
    }
}

@Composable
internal fun LyricSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
                inactiveTrackColor = TextMuted.copy(alpha = 0.24f),
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = TextMuted.copy(alpha = 0.35f),
            ),
        )
    }
}
