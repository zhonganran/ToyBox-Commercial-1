package com.byd.wjx.ui.desktop

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery6Bar
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.overlay.AutoDoorFloatingService
import com.byd.wjx.overlay.AutoDoorFloatingSettings
import com.byd.wjx.overlay.SoundFloatingService
import com.byd.wjx.overlay.SoundFloatingSettings
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.ui.pro.ProGlassPanel
import com.byd.wjx.ui.pro.ProInfoCard
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.pro.ProSectionTitle
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneSky
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.ui.widget.WidgetController
import com.byd.wjx.ui.widget.WidgetPreferences
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


internal enum class DesktopPreviewType(val label: String, val shortLabel: String) {
    Sound("音效悬浮窗", "音效"),
    DesktopLyric("桌面歌词", "桌面歌词"),
    ClusterLyric("仪表盘歌词", "仪表歌词"),
    Battery("电量小组件", "电量"),
    Door("自动门", "自动门"),
}

@Composable
internal fun DesktopSceneHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("桌面组件", color = TextPrimary, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("把常用能力放在车机桌面", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DesktopPreviewStage(
    selected: DesktopPreviewType,
    onSelected: (DesktopPreviewType) -> Unit,
    soundEnabled: Boolean,
    desktopLyricEnabled: Boolean,
    clusterLyricEnabled: Boolean,
    batteryEnabled: Boolean,
    doorEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val types = DesktopPreviewType.entries
    val selectedIndex = types.indexOf(selected)
    val ordered = remember(selected) {
        List(types.size) { position ->
            types[(selectedIndex + position - 2 + types.size) % types.size]
        }
    }
    val enabledMap = mapOf(
        DesktopPreviewType.Sound to soundEnabled,
        DesktopPreviewType.DesktopLyric to desktopLyricEnabled,
        DesktopPreviewType.ClusterLyric to clusterLyricEnabled,
        DesktopPreviewType.Battery to batteryEnabled,
        DesktopPreviewType.Door to doorEnabled,
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ordered.forEachIndexed { index, type ->
                val isSelected = index == 2
                val heightFraction = when (index) {
                    2 -> 1f
                    1, 3 -> 0.88f
                    else -> 0.76f
                }
                val weight = when (index) {
                    2 -> 1.62f
                    1, 3 -> 1.12f
                    else -> 0.86f
                }
                DesktopPreviewCard(
                    type = type,
                    enabled = enabledMap[type] == true,
                    selected = isSelected,
                    onClick = { onSelected(type) },
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight(heightFraction),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            types.forEach { type ->
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
internal fun DesktopPreviewCard(
    type: DesktopPreviewType,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var motionPhase by remember(type) { mutableFloatStateOf(STATIC_PREVIEW_PHASES[type.ordinal]) }
    LaunchedEffect(selected, type) {
        if (!selected) {
            motionPhase = STATIC_PREVIEW_PHASES[type.ordinal]
            return@LaunchedEffect
        }
        while (true) {
            delay(50L)
            motionPhase = (motionPhase + 0.0065f) % 1f
        }
    }
    val colors = when (type) {
        DesktopPreviewType.Sound -> listOf(Color(0xFF1A5960), Color(0xFF0C3138))
        DesktopPreviewType.DesktopLyric -> listOf(Color(0xFF2375B7), Color(0xFF174779))
        DesktopPreviewType.ClusterLyric -> listOf(Color(0xFF123F83), Color(0xFF071F4B))
        DesktopPreviewType.Battery -> listOf(Color(0xFF56B69D), Color(0xFF2D766A))
        DesktopPreviewType.Door -> listOf(Color(0xFF607489), Color(0xFF33485F))
    }
    val shape = RoundedCornerShape(if (selected) 22.dp else 16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(colors), shape)
            .border(if (selected) 2.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.88f else 0.32f), shape)
            .clickable(onClick = onClick),
    ) {
        DesktopCardBackground(
            type = type,
            selected = selected,
            motionPhase = motionPhase,
            modifier = Modifier.fillMaxSize(),
        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selected) 24.dp else 16.dp),
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
                when (type) {
                    DesktopPreviewType.Sound -> SoundWidgetPreview(selected)
                    DesktopPreviewType.DesktopLyric -> LyricWidgetPreview(selected, cluster = false)
                    DesktopPreviewType.ClusterLyric -> LyricWidgetPreview(selected, cluster = true)
                    DesktopPreviewType.Battery -> BatteryWidgetPreview(selected)
                    DesktopPreviewType.Door -> DoorWidgetPreview(selected)
                }
            }
            if (selected) {
                Spacer(Modifier.height(10.dp))
                SoundPreviewPulse(motionPhase)
            }
        }
    }
}

@Composable
internal fun DesktopCardBackground(
    type: DesktopPreviewType,
    selected: Boolean,
    motionPhase: Float,
    modifier: Modifier = Modifier,
) {
    val strength = if (selected) 1f else 0.48f
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minSide = size.minDimension
            val phaseAngle = motionPhase * (PI * 2.0).toFloat()

            when (type) {
                DesktopPreviewType.Sound -> {
                    val center = Offset(w * 0.50f, h * 0.58f)
                    repeat(5) { index ->
                        val progress = (motionPhase + index * 0.20f) % 1f
                        drawCircle(
                            color = SceneMint.copy(alpha = (1f - progress) * 0.20f * strength),
                            radius = minSide * (0.10f + progress * 0.38f),
                            center = center,
                            style = Stroke(width = (if (selected) 2f else 1.2f).dp.toPx()),
                        )
                    }
                    val wave = Path()
                    val samples = 42
                    repeat(samples + 1) { index ->
                        val x = w * index / samples
                        val y = h * 0.78f + sin(index * 0.58f + phaseAngle) * h * 0.035f * strength
                        if (index == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                    }
                    drawPath(wave, SceneCyan.copy(alpha = 0.46f * strength), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }

                DesktopPreviewType.DesktopLyric -> {
                    repeat(4) { band ->
                        val path = Path()
                        val samples = 36
                        repeat(samples + 1) { index ->
                            val x = w * index / samples
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

                DesktopPreviewType.ClusterLyric -> {
                    val vanishing = Offset(w * 0.50f, h * 0.66f)
                    repeat(7) { index ->
                        val startX = w * index / 6f
                        drawLine(
                            SceneSky.copy(alpha = 0.18f * strength),
                            Offset(startX, h),
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
                    repeat(3) { index ->
                        drawArc(
                            color = SceneSky.copy(alpha = (0.18f - index * 0.04f) * strength),
                            startAngle = 202f,
                            sweepAngle = 136f,
                            useCenter = false,
                            topLeft = Offset(w * (0.20f - index * 0.04f), h * (0.24f - index * 0.04f)),
                            size = androidx.compose.ui.geometry.Size(w * (0.60f + index * 0.08f), h * (0.62f + index * 0.08f)),
                            style = Stroke(1.4f.dp.toPx()),
                        )
                    }
                }

                DesktopPreviewType.Battery -> {
                    val center = Offset(w * 0.50f, h * 0.56f)
                    val radius = minSide * 0.30f
                    repeat(48) { index ->
                        val angle = (index / 48f) * (PI * 2.0).toFloat() - (PI / 2.0).toFloat()
                        val activeDistance = ((index / 48f - motionPhase + 1f) % 1f)
                        val alpha = (0.10f + (1f - activeDistance).coerceIn(0f, 0.18f) * 2.2f) * strength
                        val start = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                        val end = Offset(center.x + cos(angle) * (radius + minSide * 0.035f), center.y + sin(angle) * (radius + minSide * 0.035f))
                        drawLine(SceneMint.copy(alpha = alpha), start, end, 1.5f.dp.toPx(), StrokeCap.Round)
                    }
                    drawCircle(
                        SceneMint.copy(alpha = (0.08f + 0.05f * (sin(phaseAngle) + 1f)) * strength),
                        radius = radius * (1.08f + 0.03f * sin(phaseAngle)),
                        center = center,
                    )
                }

                DesktopPreviewType.Door -> {
                    repeat(6) { index ->
                        val progress = (motionPhase + index * 0.16f) % 1f
                        val x = w * (0.12f + progress * 0.82f)
                        drawLine(
                            if (index % 2 == 0) SceneCyan.copy(alpha = 0.24f * strength) else SceneAmber.copy(alpha = 0.18f * strength),
                            Offset(x - w * 0.13f, h * (0.70f + index * 0.018f)),
                            Offset(x, h * (0.70f + index * 0.018f)),
                            2.dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                    drawLine(
                        SceneCyan.copy(alpha = 0.36f * strength),
                        Offset(w * (0.22f + motionPhase * 0.28f), h * 0.47f),
                        Offset(w * (0.47f + motionPhase * 0.28f), h * 0.47f),
                        2.4f.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        }

    }
}

private val STATIC_PREVIEW_PHASES = listOf(0.08f, 0.26f, 0.44f, 0.62f, 0.80f)

@Composable
internal fun SoundWidgetPreview(selected: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(if (selected) 150.dp else 105.dp)) {
            repeat(4) { index ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f + index * 0.035f),
                    radius = size.minDimension * (0.18f + index * 0.10f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(if (selected) 76.dp else 58.dp)
                .clip(CircleShape)
                .background(SceneMint.copy(alpha = 0.84f))
                .border(2.dp, Color.White.copy(alpha = 0.68f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (selected) 38.dp else 28.dp))
        }
    }
}

@Composable
internal fun LyricWidgetPreview(selected: Boolean, cluster: Boolean) {
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
internal fun BatteryWidgetPreview(selected: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(if (selected) 150.dp else 104.dp)) {
            drawArc(
                Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                SceneMint,
                startAngle = -90f,
                sweepAngle = 281f,
                useCenter = false,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOC", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp)
            Text("78%", color = Color.White, fontSize = if (selected) 34.sp else 25.sp, fontWeight = FontWeight.Light)
            Text("678 km", color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
        }
    }
}

@Composable
internal fun DoorWidgetPreview(selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (selected) 10.dp else 7.dp)) {
        Image(
            painter = painterResource(R.drawable.auto_door_car_icon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(if (selected) 0.78f else 0.88f)
                .height(if (selected) 92.dp else 58.dp),
            contentScale = ContentScale.Fit,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("左门", "右门").forEach { label ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(label, color = Color.White.copy(alpha = 0.80f), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
internal fun SoundPreviewPulse(motionPhase: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
        repeat(13) { index ->
            val pulse = ((sin((index * 0.72f + motionPhase * (PI * 2.0)).toFloat()) + 1f) * 0.5f)
            val h = size.height * (0.28f + pulse * 0.72f)
            val x = size.width * (index + 1) / 14f
            drawLine(Color.White.copy(alpha = 0.70f), Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun DesktopQuickControlPanel(
    selected: DesktopPreviewType,
    soundEnabled: Boolean,
    desktopLyricEnabled: Boolean,
    clusterLyricEnabled: Boolean,
    batteryEnabled: Boolean,
    doorEnabled: Boolean,
    soundAlpha: Float,
    soundScale: Float,
    desktopLyricScale: Float,
    clusterLyricScale: Float,
    clusterLyricAlpha: Float,
    batteryAlpha: Float,
    batteryScale: Float,
    doorAlpha: Float,
    doorScale: Float,
    onToggle: (Boolean) -> Unit,
    onPrimaryValueChange: (Float) -> Unit,
    onSecondaryValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val enabled = when (selected) {
        DesktopPreviewType.Sound -> soundEnabled
        DesktopPreviewType.DesktopLyric -> desktopLyricEnabled
        DesktopPreviewType.ClusterLyric -> clusterLyricEnabled
        DesktopPreviewType.Battery -> batteryEnabled
        DesktopPreviewType.Door -> doorEnabled
    }
    val primaryValue = when (selected) {
        DesktopPreviewType.Sound -> soundScale
        DesktopPreviewType.DesktopLyric -> desktopLyricScale
        DesktopPreviewType.ClusterLyric -> clusterLyricScale
        DesktopPreviewType.Battery -> batteryScale
        DesktopPreviewType.Door -> doorScale
    }
    val primaryRange = when (selected) {
        DesktopPreviewType.Sound -> SoundFloatingSettings.ICON_SCALE_MIN..SoundFloatingSettings.ICON_SCALE_MAX
        DesktopPreviewType.DesktopLyric -> LyricSettings.FONT_MIN..LyricSettings.FONT_MAX
        DesktopPreviewType.ClusterLyric -> LyricSettings.CLUSTER_FONT_MIN..LyricSettings.CLUSTER_FONT_MAX
        DesktopPreviewType.Battery -> WidgetPreferences.SCALE_MIN..WidgetPreferences.SCALE_MAX
        DesktopPreviewType.Door -> AutoDoorFloatingSettings.SCALE_MIN..AutoDoorFloatingSettings.SCALE_MAX
    }
    val secondaryValue = when (selected) {
        DesktopPreviewType.Sound -> soundAlpha
        DesktopPreviewType.DesktopLyric -> 0.85f
        DesktopPreviewType.ClusterLyric -> clusterLyricAlpha
        DesktopPreviewType.Battery -> batteryAlpha
        DesktopPreviewType.Door -> doorAlpha
    }
    val secondaryRange = when (selected) {
        DesktopPreviewType.Sound -> SoundFloatingSettings.ICON_ALPHA_MIN..SoundFloatingSettings.ICON_ALPHA_MAX
        DesktopPreviewType.DesktopLyric -> 0.3f..1f
        DesktopPreviewType.ClusterLyric -> LyricSettings.ALPHA_MIN..LyricSettings.ALPHA_MAX
        DesktopPreviewType.Battery -> 0.3f..1f
        DesktopPreviewType.Door -> AutoDoorFloatingSettings.ALPHA_MIN..AutoDoorFloatingSettings.ALPHA_MAX
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
            label = if (selected == DesktopPreviewType.DesktopLyric || selected == DesktopPreviewType.ClusterLyric) "字体大小" else "组件大小",
            value = primaryValue,
            range = primaryRange,
            enabled = enabled,
            onValueChange = onPrimaryValueChange,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        if (selected == DesktopPreviewType.DesktopLyric) {
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
                value = secondaryValue,
                range = secondaryRange,
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
internal fun QuickSlider(
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
internal fun NudgeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
        ),
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
                        containerColor = if (selected) {
                            androidx.compose.ui.graphics.Color(preset.currentColor)
                        } else {
                            CardSurfaceElevated
                        },
                        contentColor = if (selected) {
                            androidx.compose.ui.graphics.Color.Black
                        } else {
                            TextPrimary
                        },
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
internal fun FloatingIconOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.18f) else CardSurface, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (selected) AccentGreen else CardBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(label, color = if (selected) AccentGreen else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
internal fun WidgetSlider(
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
