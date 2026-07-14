package com.byd.wjx.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.domain.insight.LocalInsightResult
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.dashboard.DashboardViewModel
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ProHomeScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenSound: () -> Unit,
    onOpenDesktop: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCharges: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenTripMap: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var imageVersion by remember { mutableIntStateOf(0) }
    val customVehicleImage = remember(appContext, imageVersion) { loadVehicleImageBitmap(appContext) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (saveVehicleImage(appContext, uri)) {
            imageVersion += 1
            Toast.makeText(appContext, "车辆图片已更新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(appContext, "车辆图片保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    fun deleteCustomVehicleImage() {
        if (deleteVehicleImage(appContext)) {
            imageVersion += 1
            Toast.makeText(appContext, "车辆图片已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(appContext, "暂无自定义车辆图片", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1080.dp
        val compact = maxWidth < 760.dp
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = if (compact) 14.dp else 46.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (wide) {
                HomeHero(
                    state = state,
                    image = customVehicleImage,
                    onPickImage = { imagePicker.launch("image/*") },
                    onDeleteImage = ::deleteCustomVehicleImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp),
                )
                HomeMetricRibbon(
                    state = state,
                    onOpenCharges = onOpenCharges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(238.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BatteryHealthPanel(
                        state = state,
                        modifier = Modifier
                            .weight(0.98f)
                            .fillMaxHeight(),
                    )
                    VehicleEnvironmentPanel(
                        state = state,
                        modifier = Modifier
                            .weight(1.02f)
                            .fillMaxHeight(),
                    )
                    JourneyAssistPanel(
                        state = state,
                        onOpenDashboard = onOpenDashboard,
                        onOpenTripMap = onOpenTripMap,
                        modifier = Modifier
                            .weight(1.45f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                HomeHero(
                    state = state,
                    image = customVehicleImage,
                    onPickImage = { imagePicker.launch("image/*") },
                    onDeleteImage = ::deleteCustomVehicleImage,
                    stacked = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 620.dp else 390.dp),
                )
                HomeMetricRibbon(
                    state = state,
                    onOpenCharges = onOpenCharges,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 220.dp else 150.dp),
                )
                BatteryHealthPanel(state, Modifier.fillMaxWidth().height(240.dp))
                VehicleEnvironmentPanel(state, Modifier.fillMaxWidth().height(240.dp))
                JourneyAssistPanel(
                    state = state,
                    onOpenDashboard = onOpenDashboard,
                    onOpenTripMap = onOpenTripMap,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun HomeHero(
    state: DashboardUiState,
    image: ImageBitmap?,
    onPickImage: () -> Unit,
    onDeleteImage: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().height(225.dp)) {
                SocGauge(state, Modifier.weight(1f).fillMaxHeight())
                SpeedGauge(state, Modifier.weight(1f).fillMaxHeight())
            }
            VehicleStage(
                state = state,
                image = image,
                onPickImage = onPickImage,
                onDeleteImage = onDeleteImage,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            SocGauge(state, Modifier.width(310.dp).fillMaxHeight())
            VehicleStage(
                state = state,
                image = image,
                onPickImage = onPickImage,
                onDeleteImage = onDeleteImage,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SpeedGauge(state, Modifier.width(310.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun SocGauge(state: DashboardUiState, modifier: Modifier = Modifier) {
    val value = state.soc?.coerceIn(0, 100)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(252.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.58f),
                startAngle = 132f,
                sweepAngle = 276f,
                useCenter = false,
                style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(SceneCyan, SceneMint, SceneCyan)),
                startAngle = 132f,
                sweepAngle = 276f * ((value ?: 0) / 100f),
                useCenter = false,
                style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
            )
            repeat(28) { index ->
                val angle = Math.toRadians((132f + index * (276f / 27f)).toDouble())
                val center = Offset(size.width / 2f, size.height / 2f)
                val outer = size.minDimension * 0.43f
                val inner = outer - 5.dp.toPx()
                val start = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * inner,
                    center.y + kotlin.math.sin(angle).toFloat() * inner,
                )
                val end = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * outer,
                    center.y + kotlin.math.sin(angle).toFloat() * outer,
                )
                drawLine(Color.White.copy(alpha = 0.82f), start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOC", color = TextPrimary.copy(alpha = 0.80f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value?.toString() ?: "--",
                    color = TextPrimary,
                    fontSize = 68.sp,
                    lineHeight = 70.sp,
                    fontWeight = FontWeight.Light,
                )
                Text("%", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 9.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill(text = workModeText(state.workMode), color = SceneMint)
                HeroPill(text = driveModeText(state.driveMode), color = SceneSkyColor)
            }
        }
        Icon(
            Icons.Outlined.BatteryChargingFull,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(28.dp),
        )
    }
}

private val SceneSkyColor = Color(0xFF4D9FE4)

@Composable
private fun SpeedGauge(state: DashboardUiState, modifier: Modifier = Modifier) {
    val speed = state.speed?.coerceAtLeast(0)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(252.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.62f),
                startAngle = 312f,
                sweepAngle = 276f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
            val progress = ((speed ?: 0) / 160f).coerceIn(0f, 1f)
            drawArc(
                brush = Brush.sweepGradient(listOf(SceneBlue, SceneViolet, ScenePink)),
                startAngle = 312f,
                sweepAngle = 276f * progress,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
            repeat(26) { index ->
                val angle = Math.toRadians((312f + index * (276f / 25f)).toDouble())
                val center = Offset(size.width / 2f, size.height / 2f)
                val outer = size.minDimension * 0.43f
                val inner = outer - 5.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.82f),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(speed?.toString() ?: "--", color = TextPrimary, fontSize = 68.sp, lineHeight = 70.sp, fontWeight = FontWeight.Light)
            Text("km/h", color = TextPrimary.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(gearText(state.gear), color = TextPrimary, fontSize = 62.sp, lineHeight = 66.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun VehicleStage(
    state: DashboardUiState,
    image: ImageBitmap?,
    onPickImage: () -> Unit,
    onDeleteImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.70f), SceneCyan.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.64f),
                    radius = size.width * 0.42f,
                ),
                topLeft = Offset(size.width * 0.16f, size.height * 0.52f),
                size = Size(size.width * 0.68f, size.height * 0.30f),
            )
        }

        TireBadge("左前", state.tirePressFL, Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 70.dp))
        TireBadge("右前", state.tirePressFR, Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 70.dp))
        TireBadge("左后", state.tirePressRL, Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 64.dp))
        TireBadge("右后", state.tirePressRR, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 64.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .fillMaxHeight(0.72f)
                .pointerInput(image) {
                    detectTapGestures(
                        onTap = { onPickImage() },
                        onLongPress = { onDeleteImage() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "自定义车辆图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.56f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = "更换车辆图片", tint = TextPrimary, modifier = Modifier.size(21.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.32f))
                        .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                        .clickable(onClick = onPickImage)
                        .padding(horizontal = 48.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(34.dp))
                    Text("上传车辆图片", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("点击上传 · 长按删除", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowIcon(Icons.Outlined.ElectricBolt, SceneMint, state.powerState != null)
            FlowLine(SceneMint)
            FlowIcon(Icons.Outlined.Battery5Bar, SceneBlue, state.soc != null)
            FlowLine(SceneBlue)
            FlowIcon(Icons.Outlined.Air, SceneSkyColor, state.acStatus == 1)
        }
    }
}

@Composable
private fun TireBadge(label: String, value: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x6B315E7A))
            .border(1.dp, Color.White.copy(alpha = 0.56f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.TireRepair, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text("$label ${tireValue(value)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HeroPill(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
}

@Composable
private fun FlowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, active: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.70f))
            .border(1.dp, color.copy(alpha = if (active) 0.86f else 0.30f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = if (active) 1f else 0.45f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FlowLine(color: Color) {
    Canvas(modifier = Modifier.width(68.dp).height(12.dp)) {
        drawLine(color.copy(alpha = 0.36f), Offset.Zero.copy(y = size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx(), StrokeCap.Round)
        repeat(4) { index ->
            drawCircle(color.copy(alpha = 0.80f - index * 0.12f), radius = 2.4.dp.toPx(), center = Offset(size.width * ((index + 1) / 5f), size.height / 2f))
        }
    }
}

@Composable
private fun HomeMetricRibbon(
    state: DashboardUiState,
    onOpenCharges: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val metrics = listOf(
        RibbonMetric("电续航", state.elecDrivingRangeKm?.toString() ?: "--", "km", Icons.Outlined.EvStation, SceneMint, rangeProgress(state.elecDrivingRangeKm, 320)),
        RibbonMetric("油续航", state.fuelDrivingRangeKm?.toString() ?: "--", "km", Icons.Outlined.LocalGasStation, SceneBlue, rangeProgress(state.fuelDrivingRangeKm, 800)),
        RibbonMetric("总续航", state.combinedRangeKm?.toString() ?: "--", "km", Icons.Outlined.Route, SceneViolet, rangeProgress(state.combinedRangeKm, 1200)),
        RibbonMetric("油量", state.fuelLevel?.toString() ?: "--", "%", Icons.Outlined.WaterDrop, SceneAmber, ((state.fuelLevel ?: 0) / 100f).coerceIn(0f, 1f)),
        RibbonMetric("实时功率", state.power?.let { "%.1f".format(it) } ?: "--", "kW", Icons.Outlined.ElectricBolt, AccentOrange, ((kotlin.math.abs(state.power ?: 0.0) / 80.0).toFloat()).coerceIn(0f, 1f)),
        RibbonMetric("总里程", state.odometer?.let { "%,.0f".format(it) } ?: "--", "km", Icons.Outlined.Speed, ScenePink, 0.72f),
    )
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.76f))
            .border(1.dp, Color.White.copy(alpha = 0.90f), shape)
            .clickable(onClick = onOpenCharges)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (compact || maxWidth < 900.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item -> RibbonMetricItem(item, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                metrics.forEachIndexed { index, item ->
                    RibbonMetricItem(item, Modifier.weight(1f))
                    if (index < metrics.lastIndex) {
                        Box(Modifier.width(1.dp).height(42.dp).background(TextPrimary.copy(alpha = 0.08f)))
                    }
                }
            }
        }
    }
}

private data class RibbonMetric(
    val label: String,
    val value: String,
    val unit: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val progress: Float,
)

@Composable
private fun RibbonMetricItem(metric: RibbonMetric, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Column {
                Text(metric.label, color = TextPrimary.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(metric.value, color = TextPrimary, fontSize = 23.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Text(metric.unit, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(TextPrimary.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(metric.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(metric.color))
        }
    }
}

@Composable
private fun BatteryHealthPanel(state: DashboardUiState, modifier: Modifier = Modifier) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE20E9C8E), Color(0xE21F7E77)),
        title = "电池健康",
        icon = Icons.Outlined.HealthAndSafety,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(0.95f)) {
                Text("SoH", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.currentSoh?.roundToInt()?.toString() ?: "--", color = Color.White, fontSize = 54.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light)
                    Text("%", color = Color.White, fontSize = 23.sp, modifier = Modifier.padding(bottom = 7.dp))
                }
                Text(batteryHint(state), color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp)
            }
            Column(modifier = Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                WhiteMetric("电池均温", tempText(state.avgBatTemp))
                WhiteMetric("最高/最低", maxMinTempText(state))
                WhiteMetric("电芯压差", state.cellVoltageDelta?.let { "%.0f mV".format(it * 1000) } ?: "--")
                WhiteMetric("12V 电池", state.voltage12v?.let { "%.1f V".format(it) } ?: "--")
                WhiteMetric("充电枪", chargeGunText(state))
            }
        }
    }
}

@Composable
private fun VehicleEnvironmentPanel(state: DashboardUiState, modifier: Modifier = Modifier) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xDF168FBD), Color(0xDF245AA8)),
        title = "车身环境",
        icon = Icons.Outlined.DirectionsCar,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.AcUnit, "空调", acMainText(state), state.acStatus == 1)
                EnvironmentItem(Icons.Outlined.Air, "风量", fanText(state), (state.fanLevel ?: 0) > 0)
                EnvironmentItem(Icons.Outlined.DirectionsCar, "内循环", circText(state.acCirc), state.acCirc == 1)
                EnvironmentItem(Icons.Outlined.LightMode, "灯光", lightMainText(state), lightsActive(state))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.Lock, "车锁", lockSummary(state), lockSummary(state) == "已锁")
                EnvironmentItem(Icons.Outlined.DirectionsCar, "车门", doorSummary(state), doorSummary(state) == "全关")
                EnvironmentItem(Icons.Outlined.EvStation, "充电口", openCloseText(state.chargingCapCover), state.chargingCapCover == 1)
                EnvironmentItem(Icons.Outlined.LocalGasStation, "油箱盖", openCloseText(state.fuelCap), state.fuelCap == 1)
            }
        }
    }
}

@Composable
private fun JourneyAssistPanel(
    state: DashboardUiState,
    onOpenDashboard: () -> Unit,
    onOpenTripMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier.clickable(onClick = onOpenDashboard),
        colors = listOf(Color(0xDD315CB8), Color(0xDD59469C)),
        title = "行程与驾驶辅助",
        icon = Icons.Outlined.Route,
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.weight(1.22f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TripValue("本次", state.tripDistanceKm?.let { "%.1f km".format(it) } ?: "--", Modifier.weight(1f))
                    TripValue("用电", state.currentTripElecKwh?.let { "%.1f kWh".format(it) } ?: "--", Modifier.weight(1f))
                    TripValue("用油", state.currentTripFuelL?.let { "%.1f L".format(it) } ?: "--", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TripValue("电耗", state.elecConPer100km?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
                    TripValue("油耗", state.fuelConPer100km?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
                    TripValue("停车能耗", "%.1f kWh".format(state.idleDrainKwhToday), Modifier.weight(1f))
                }
                MiniTrendChart(modifier = Modifier.fillMaxWidth().weight(1f))
                Text(
                    text = insightLine(state.localInsight, state.localInsightLoading),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenTripMap),
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
            Column(modifier = Modifier.weight(0.76f), verticalArrangement = Arrangement.SpaceEvenly) {
                AssistStatus("ACC", accText(state))
                AssistStatus("LCC", laneText(state.laneKeepState))
                AssistStatus("NCA", ncaText(state))
                AssistStatus("自动泊车", autoParkText(state.adasApaParkingStatus))
            }
        }
    }
}

@Composable
private fun HomeDarkPanel(
    modifier: Modifier,
    colors: List<Color>,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(colors), shape)
            .border(1.dp, Color.White.copy(alpha = 0.48f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun WhiteMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EnvironmentItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    active: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(70.dp)) {
        Icon(icon, contentDescription = null, tint = if (active) Color.White else Color.White.copy(alpha = 0.62f), modifier = Modifier.size(26.dp))
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TripValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTrendChart(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val values = listOf(0.68f, 0.42f, 0.56f, 0.75f, 0.60f, 0.82f, 0.70f, 0.74f)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.lastIndex.coerceAtLeast(1))
            val y = size.height * (1f - value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, SceneAmber, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { index, value ->
            drawCircle(SceneAmber, 2.4.dp.toPx(), Offset(size.width * index / values.lastIndex, size.height * (1f - value)))
        }
    }
}

@Composable
private fun AssistStatus(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (value.contains("开启") || value.contains("巡航") || value.contains("进行")) SceneMint else Color.White.copy(alpha = 0.54f)))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun rangeProgress(value: Int?, maxValue: Int): Float =
    ((value ?: 0).toFloat() / maxValue).coerceIn(0f, 1f)

private fun tireValue(value: Int?): String =
    value?.takeIf { it in 80..420 }?.let { "$it kPa" } ?: "-- kPa"

private fun tempText(value: Int?): String = value?.let { "$it°C" } ?: "--"

private fun maxMinTempText(state: DashboardUiState): String {
    val maxText = state.maxBatTemp?.let { "$it" } ?: "--"
    val minText = state.minBatTemp?.let { "$it" } ?: "--"
    return "$maxText / $minText°C"
}

private fun openCloseText(value: Int?): String = when (value) {
    0 -> "关闭"
    1 -> "打开"
    else -> "--"
}

private fun chargeGunText(state: DashboardUiState): String = when {
    state.isCharging -> "充电中"
    ChargeGunState.isConnected(state.chargeGunState) -> "已插枪"
    ChargeGunState.isDisconnected(state.chargeGunState) -> "未插枪"
    else -> "--"
}

private fun batteryHint(state: DashboardUiState): String = when {
    state.currentSoh == null -> "等待电池健康数据"
    state.currentSoh >= 95f -> "电池状态优秀"
    state.currentSoh >= 88f -> "电池状态良好"
    else -> "建议关注电池状态"
}

private fun doorSummary(state: DashboardUiState): String {
    val values = listOf(state.doorFL, state.doorFR, state.doorRL, state.doorRR, state.trunk, state.hood)
    if (values.all { it == null }) return "--"
    return if (values.any { it == 1 }) "有开启" else "全关"
}

private fun lockSummary(state: DashboardUiState): String {
    val values = listOf(state.lockFL, state.lockFR, state.lockRL, state.lockRR)
    if (values.all { it == null }) return "--"
    return if (values.all { it == 1 }) "已锁" else "未锁"
}

private fun acMainText(state: DashboardUiState): String = when (state.acStatus) {
    1 -> state.acTemp?.let { "$it°C" } ?: "开启"
    0 -> "关闭"
    else -> "--"
}

private fun fanText(state: DashboardUiState): String = state.fanLevel?.let { "${it}挡" } ?: "--"

private fun circText(value: Int?): String = when (value) {
    1 -> "开启"
    0 -> "外循环"
    else -> "--"
}

private fun lightsActive(state: DashboardUiState): Boolean =
    listOf(state.lightLow, state.lightHigh, state.drl, state.hazardLights).any { it == 1 }

private fun lightMainText(state: DashboardUiState): String = when {
    state.lightHigh == 1 -> "远光"
    state.lightLow == 1 -> "近光"
    state.drl == 1 -> "日行灯"
    state.hazardLights == 1 -> "双闪"
    listOf(state.lightLow, state.lightHigh, state.drl, state.hazardLights).all { it == null } -> "--"
    else -> "关闭"
}

private fun gearText(gear: Int?): String = when (gear) {
    1 -> "P"
    2 -> "R"
    3 -> "N"
    4 -> "D"
    else -> "P"
}

private fun driveModeText(mode: Int?): String = when (mode) {
    1 -> "经济模式"
    2 -> "标准模式"
    3 -> "运动模式"
    4 -> "雪地模式"
    else -> "标准模式"
}

private fun workModeText(mode: Int?): String = when (mode) {
    1 -> "EV"
    2 -> "HEV"
    3 -> "发动机"
    else -> "EV"
}

private fun insightLine(insight: LocalInsightResult, loading: Boolean): String = when {
    loading -> "本地洞察：正在分析近期驾驶数据"
    insight.summary.isNotBlank() -> "本地洞察：${insight.summary}"
    else -> "本地洞察：数据积累中"
}

private fun autoParkText(value: Int?): String = when (value) {
    null, 0 -> "关闭"
    1 -> "待机"
    2, 3, 4 -> "进行中"
    else -> "开启"
}

private fun laneText(value: Int?): String = when (value) {
    null, 0 -> "待机"
    1 -> "开启"
    else -> "工作中"
}

private fun accText(state: DashboardUiState): String = when (state.accCruiseState) {
    null, 0 -> "待机"
    1 -> "开启"
    else -> state.accCruisingSpeed?.let { "巡航 $it" } ?: "巡航中"
}

private fun ncaText(state: DashboardUiState): String = when {
    state.adasLccNcaStatus == null && state.adasAccMode == null -> "关闭"
    state.adasLccNcaStatus == 0 && state.adasAccMode == 0 -> "关闭"
    else -> "开启"
}

private fun vehicleImageFile(context: Context): File =
    File(context.filesDir, "ui/vehicle/home_vehicle.png")

private fun loadVehicleImageBitmap(context: Context): ImageBitmap? {
    val file = vehicleImageFile(context)
    if (!file.isFile) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
}

private fun saveVehicleImage(context: Context, uri: Uri): Boolean = runCatching {
    val decoded = decodeUriSampled(context, uri, 1600) ?: return@runCatching false
    val normalized = normalizeVehicleBitmap(decoded, 1600)
    val file = vehicleImageFile(context)
    file.parentFile?.mkdirs()
    file.outputStream().use { output ->
        normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    if (normalized !== decoded) decoded.recycle()
    normalized.recycle()
    true
}.getOrDefault(false)

private fun deleteVehicleImage(context: Context): Boolean = runCatching {
    val file = vehicleImageFile(context)
    file.isFile && file.delete()
}.getOrDefault(false)

private fun decodeUriSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun normalizeVehicleBitmap(source: Bitmap, maxSide: Int): Bitmap {
    val largest = max(source.width, source.height)
    if (largest <= maxSide) return source
    val scale = maxSide.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).roundToInt().coerceAtLeast(1),
        (source.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}
