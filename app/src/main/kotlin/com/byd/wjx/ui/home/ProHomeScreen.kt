package com.byd.wjx.ui.home

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeCap
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
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.domain.insight.LocalInsightMetric
import com.byd.wjx.domain.insight.LocalInsightTone
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.dashboard.DashboardViewModel
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.GlassStroke
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ProHomeScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenCharges: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenTripMap: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setHomeTelemetryActive(true)
        viewModel.setPowertrainTelemetryActive(true)
        onDispose {
            viewModel.setHomeTelemetryActive(false)
            viewModel.setPowertrainTelemetryActive(false)
        }
    }
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
                        .height(322.dp),
                )
                HomeMetricRibbon(
                    state = state,
                    onOpenCharges = onOpenCharges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BatteryHealthPanel(
                        state = state,
                        onClick = onOpenDashboard,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    VehicleEnvironmentPanel(
                        state = state,
                        onClick = onOpenDashboard,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    TripRoutePanel(
                        state = state,
                        onOpenTripMap = onOpenTripMap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    ChargeRecordPanel(
                        charge = state.latestCharge,
                        currencySymbol = state.currencySymbol,
                        onOpenCharges = onOpenCharges,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                LocalInsightPanel(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp),
                )
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
                BatteryHealthPanel(
                    state = state,
                    onClick = onOpenDashboard,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                VehicleEnvironmentPanel(
                    state = state,
                    onClick = onOpenDashboard,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                TripRoutePanel(
                    state = state,
                    onOpenTripMap = onOpenTripMap,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )
                ChargeRecordPanel(
                    charge = state.latestCharge,
                    currencySymbol = state.currencySymbol,
                    onOpenCharges = onOpenCharges,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )
                LocalInsightPanel(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 360.dp else 260.dp),
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
        Canvas(modifier = Modifier.size(280.dp)) {
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
            Text(
                text = state.batteryRemainingKwh?.let { "剩余 %.1f kWh".format(it) } ?: "剩余 -- kWh",
                color = TextPrimary.copy(alpha = 0.66f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
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
        Canvas(modifier = Modifier.size(280.dp)) {
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
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val tireHorizontalInset = when {
            maxWidth >= 1100.dp -> 170.dp
            maxWidth >= 800.dp -> 110.dp
            maxWidth >= 600.dp -> 70.dp
            else -> 16.dp
        }
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

        TireBadge("左前", state.tirePressFL, state.tireTempFL, state.tireStateFL, Modifier.align(Alignment.TopStart).padding(start = tireHorizontalInset, top = 70.dp))
        TireBadge("右前", state.tirePressFR, state.tireTempFR, state.tireStateFR, Modifier.align(Alignment.TopEnd).padding(end = tireHorizontalInset, top = 70.dp))
        TireBadge("左后", state.tirePressRL, state.tireTempRL, state.tireStateRL, Modifier.align(Alignment.BottomStart).padding(start = tireHorizontalInset, bottom = 82.dp))
        TireBadge("右后", state.tirePressRR, state.tireTempRR, state.tireStateRR, Modifier.align(Alignment.BottomEnd).padding(end = tireHorizontalInset, bottom = 82.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.76f)
                .offset(y = (-18).dp)
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
                        .background(CardSurface.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = "更换车辆图片", tint = TextPrimary, modifier = Modifier.size(21.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardSurface.copy(alpha = 0.62f))
                        .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                        .clickable(onClick = onPickImage)
                        .padding(horizontal = 48.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(34.dp))
                    Text("上传车辆图片", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("点击上传 · 长按删除", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        PowertrainSpeedStrip(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .fillMaxWidth(0.80f)
                .height(72.dp),
        )
    }
}

@Composable
private fun TireBadge(
    label: String,
    pressure: Int?,
    temperature: Int?,
    rawState: Int?,
    modifier: Modifier = Modifier,
) {
    val attention = rawState != null && rawState != 0
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (attention) Color(0xCCB83A46) else Color(0x6B315E7A))
            .border(1.dp, Color.White.copy(alpha = 0.56f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.TireRepair, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(
            buildString {
                append("$label ${tireValue(pressure)} · ${tireTempValue(temperature)}")
                if (attention) append(" · 状态 $rawState")
            },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
private fun PowertrainSpeedStrip(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.88f))
            .border(1.dp, GlassStroke, shape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "动力转速",
            color = TextPrimary.copy(alpha = 0.78f),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowertrainSpeedItem(
                icon = Icons.Outlined.Settings,
                label = "发动机",
                value = state.engineRpm,
                color = SceneAmber,
                modifier = Modifier.weight(1f),
            )
            PowertrainDivider()
            PowertrainSpeedItem(
                icon = Icons.Outlined.ElectricCar,
                label = "前电机",
                value = state.frontMotorSpeed,
                color = SceneMint,
                modifier = Modifier.weight(1f),
            )
            PowertrainDivider()
            PowertrainSpeedItem(
                icon = Icons.Outlined.ElectricCar,
                label = "后电机",
                value = state.rearMotorSpeed,
                color = SceneBlue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PowertrainSpeedItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, color = TextSecondary, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Medium)
            Text(
                text = powertrainRpmText(value),
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PowertrainDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(TextPrimary.copy(alpha = 0.12f)))
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
        RibbonMetric(
            label = "油量",
            value = state.fuelLevel?.toString() ?: "--",
            unit = "%",
            icon = Icons.Outlined.WaterDrop,
            color = SceneAmber,
            progress = ((state.fuelLevel ?: 0) / 100f).coerceIn(0f, 1f),
            secondary = state.fuelRemainingLiters?.let { "剩余 %.1f L".format(it) },
        ),
        RibbonMetric("实时功率", state.power?.let { "%.1f".format(it) } ?: "--", "kW", Icons.Outlined.ElectricBolt, AccentOrange, ((kotlin.math.abs(state.power ?: 0.0) / 80.0).toFloat()).coerceIn(0f, 1f)),
        RibbonMetric("总里程", state.odometer?.let { "%,.0f".format(it) } ?: "--", "km", Icons.Outlined.Speed, ScenePink, 0.72f),
    )
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.86f))
            .border(1.dp, GlassStroke, shape)
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
    val secondary: String? = null,
)

@Composable
private fun RibbonMetricItem(metric: RibbonMetric, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Column {
                Text(metric.label, color = TextPrimary.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(metric.value, color = TextPrimary, fontSize = 23.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Text(metric.unit, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                metric.secondary?.let {
                    Text(it, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(TextPrimary.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(metric.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(metric.color))
        }
    }
}

@Composable
private fun BatteryHealthPanel(
    state: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE20E9C8E), Color(0xE21F7E77)),
        title = "电池健康",
        icon = Icons.Outlined.HealthAndSafety,
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(0.92f)) {
                Text("SoH", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.currentSoh?.roundToInt()?.toString() ?: "--", color = Color.White, fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light)
                    Text("%", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(batteryHint(state), color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 2)
            }
            Column(modifier = Modifier.weight(1.28f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                WhiteMetric("电池均温", tempText(state.avgBatTemp))
                WhiteMetric("最高/最低", maxMinTempText(state))
                WhiteMetric("电芯压差", formatCellVoltageDelta(state.cellVoltageDelta))
                WhiteMetric("12V 电池", state.voltage12v?.let { "%.1f V".format(it) } ?: "--")
                WhiteMetric("充电枪", chargeGunText(state))
            }
        }
    }
}

@Composable
private fun VehicleEnvironmentPanel(
    state: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xDF168FBD), Color(0xDF245AA8)),
        title = "车身环境",
        icon = Icons.Outlined.DirectionsCar,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.AcUnit, "空调", acMainText(state), state.acStatus == 1, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.Air, "风量", fanText(state), (state.fanLevel ?: 0) > 0, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.DirectionsCar, "内循环", circText(state.acCirc), state.acCirc == 1, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.LightMode, "灯光", lightMainText(state), lightsActive(state), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.Lock, "车锁", lockSummary(state), lockSummary(state) == "已锁", Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.DirectionsCar, "车门", doorSummary(state), doorSummary(state) == "全关", Modifier.weight(1f))
                EnvironmentItem(
                    Icons.Outlined.EvStation,
                    "充电口",
                    openCloseText(state.chargingCapCover),
                    BydSignalSemantics.isCoverOpen(state.chargingCapCover),
                    Modifier.weight(1f),
                )
                EnvironmentItem(
                    Icons.Outlined.LocalGasStation,
                    "油箱盖",
                    openCloseText(state.fuelCap),
                    BydSignalSemantics.isCoverOpen(state.fuelCap),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TripRoutePanel(
    state: DashboardUiState,
    onOpenTripMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE3366BD5), Color(0xE34A57C8)),
        title = "行程与轨迹",
        icon = Icons.Outlined.Route,
        onClick = onOpenTripMap,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TripValue("本次", state.tripDistanceKm?.let { "%.1f km".format(it) } ?: "--", Modifier.weight(1f))
                TripValue("时长", tripDurationText(state.sessionStartedAt), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TripValue("用电", state.currentTripElecKwh?.let { "%.1f kWh".format(it) } ?: "--", Modifier.weight(1f))
                TripValue("电耗", state.elecConPer100km?.let { "%.1f kWh/100km".format(it) } ?: "--", Modifier.weight(1f))
            }
            TripHistoryBars(
                distances = state.recentTrips.mapNotNull { it.distanceKm?.takeIf { distance -> distance > 0.0 } },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(
                text = if (state.recentTrips.isEmpty()) "暂无行程记录" else "查看完整行程轨迹",
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChargeRecordPanel(
    charge: ChargeEntity?,
    currencySymbol: String,
    onOpenCharges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE29354E8), Color(0xE35B54D9)),
        title = "充电记录",
        icon = Icons.Outlined.ElectricBolt,
        onClick = onOpenCharges,
    ) {
        if (charge == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.EvStation, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text("暂无充电记录", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("完成一次充电后会自动显示", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatChargeDate(charge.startTs), color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    HeroPill(chargeTypeText(charge), Color.White.copy(alpha = 0.20f))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(charge.socStart?.let { "$it%" } ?: "--", color = Color.White, fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  →  ", color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 3.dp))
                    Text(charge.socEnd?.let { "$it%" } ?: if (charge.endTs == null) "进行中" else "--", color = Color.White, fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                ChargeSocProgress(charge, Modifier.fillMaxWidth().height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TripValue("补能", chargeEnergyText(charge), Modifier.weight(1f))
                    TripValue("时长", chargeDurationText(charge), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TripValue("费用", charge.cost?.let { "%.2f %s".format(it, currencySymbol) } ?: "--", Modifier.weight(1f))
                    TripValue("平均功率", charge.avgPowerKw?.let { "%.1f kW".format(it) } ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LocalInsightPanel(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val insight = state.localInsight
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.90f))
            .border(1.dp, GlassStroke, shape)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        val compact = maxWidth < 900.dp
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Insights, contentDescription = null, tint = SceneBlue, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.localInsightLoading) "本地洞察" else "本地洞察 · ${insight.title}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.localInsightLoading) "分析中" else if (insight.hasEnoughData) "本地分析完成" else "数据积累中",
                    color = if (insight.hasEnoughData) AccentGreen else AccentBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background((if (insight.hasEnoughData) AccentGreen else AccentBlue).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                text = if (state.localInsightLoading) "正在分析近期行程、充电和电池数据" else insight.summary.ifBlank { "本地数据积累中" },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.localInsightLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("所有分析均在车机本地完成，不上传车辆数据", color = TextSecondary, fontSize = 11.sp)
                }
            } else if (compact) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insight.metrics.take(6).chunked(3).forEach { rowMetrics ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowMetrics.forEach { metric -> LocalInsightMetricItem(metric, Modifier.weight(1f)) }
                            repeat(3 - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    insight.recommendations.take(2).forEach { InsightRecommendation(it) }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        insight.metrics.take(6).forEach { metric ->
                            LocalInsightMetricItem(metric, Modifier.weight(1f))
                        }
                    }
                    if (insight.recommendations.isNotEmpty()) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(TextPrimary.copy(alpha = 0.10f)))
                        Column(
                            modifier = Modifier.width(310.dp).padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            insight.recommendations.take(2).forEach { InsightRecommendation(it) }
                        }
                    }
                }
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
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val interactiveModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(
        modifier = interactiveModifier
            .clip(shape)
            .background(Brush.linearGradient(colors), shape)
            .border(1.dp, Color.White.copy(alpha = 0.48f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(20.dp))
            }
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
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
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
private fun TripHistoryBars(
    distances: List<Double>,
    modifier: Modifier = Modifier,
) {
    if (distances.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            Text("近期暂无可用行程数据", color = Color.White.copy(alpha = 0.66f), fontSize = 10.sp)
        }
        return
    }
    val values = distances.takeLast(7)
    val maximum = max(values.maxOrNull() ?: 0.0, 0.1)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        values.forEach { value ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((value / maximum).toFloat().coerceIn(0.12f, 1f))
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(Color.White.copy(alpha = 0.58f)),
            )
        }
    }
}

@Composable
private fun ChargeSocProgress(charge: ChargeEntity, modifier: Modifier = Modifier) {
    val start = charge.socStart?.coerceIn(0, 100)
    val end = charge.socEnd?.coerceIn(0, 100)
    val progress = when {
        end != null -> end / 100f
        start != null -> start / 100f
        else -> 0f
    }
    Box(modifier = modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.82f)),
            )
        }
    }
}

@Composable
private fun LocalInsightMetricItem(
    metric: LocalInsightMetric,
    modifier: Modifier = Modifier,
) {
    val color = insightToneColor(metric.tone)
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(metric.label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(metric.value, color = TextPrimary, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(metric.detail, color = TextSecondary, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InsightRecommendation(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AccentGreen.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = TextPrimary, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun rangeProgress(value: Int?, maxValue: Int): Float =
    ((value ?: 0).toFloat() / maxValue).coerceIn(0f, 1f)

private fun tireValue(value: Int?): String =
    value?.takeIf { it in 80..420 }?.let { "$it kPa" } ?: "-- kPa"

private fun tireTempValue(value: Int?): String =
    value?.takeIf { it in -50..150 }?.let { "$it°C" } ?: "--°C"

private fun tempText(value: Int?): String = value?.let { "$it°C" } ?: "--"

private fun maxMinTempText(state: DashboardUiState): String {
    val maxText = state.maxBatTemp?.let { "$it" } ?: "--"
    val minText = state.minBatTemp?.let { "$it" } ?: "--"
    return "$maxText / $minText°C"
}

private fun openCloseText(value: Int?): String = when (value) {
    BydSignalSemantics.COVER_CLOSED -> "关闭"
    BydSignalSemantics.COVER_OPEN -> "打开"
    else -> "--"
}

private fun chargeGunText(state: DashboardUiState): String = when {
    state.isCharging -> "充电中"
    ChargeGunState.isConnected(state.chargeGunState) -> "已插枪"
    ChargeGunState.isDisconnected(state.chargeGunState) -> "未插枪"
    else -> "--"
}

internal fun formatCellVoltageDelta(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { "%.3f V".format(Locale.CHINA, it) } ?: "--"

private fun powertrainRpmText(value: Int?): String =
    value?.let { "%,d rpm".format(Locale.CHINA, it) } ?: "-- rpm"

private fun tripDurationText(startedAt: Long?): String =
    startedAt?.takeIf { it > 0L }?.let { formatDuration(System.currentTimeMillis() - it) } ?: "--"

private fun chargeDurationText(charge: ChargeEntity): String =
    formatDuration((charge.endTs ?: System.currentTimeMillis()) - charge.startTs)

internal fun formatDuration(durationMs: Long): String {
    if (durationMs < 0L) return "--"
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "${hours}小时${minutes}分"
        else -> "${minutes}分"
    }
}

private fun formatChargeDate(timestamp: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

private fun chargeTypeText(charge: ChargeEntity): String = when {
    charge.endTs == null -> "充电中"
    charge.type.equals("AC", ignoreCase = true) -> "交流慢充"
    charge.type.equals("DC", ignoreCase = true) -> "直流快充"
    else -> "充电完成"
}

private fun chargeEnergyText(charge: ChargeEntity): String =
    (charge.kwhCharged ?: charge.kwhChargedSoc)?.let { "%.1f kWh".format(Locale.CHINA, it) } ?: "--"

private fun insightToneColor(tone: LocalInsightTone): Color = when (tone) {
    LocalInsightTone.GOOD -> AccentGreen
    LocalInsightTone.INFO -> AccentBlue
    LocalInsightTone.WARNING -> SceneAmber
    LocalInsightTone.CRITICAL -> Color(0xFFFF5A68)
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
    val sideDoorOrHoodOpen = listOf(
        state.doorFL,
        state.doorFR,
        state.doorRL,
        state.doorRR,
        state.hood,
    ).any { it == 1 }
    return if (sideDoorOrHoodOpen || BydSignalSemantics.isTrunkNotClosed(state.trunk)) {
        "有开启"
    } else {
        "全关"
    }
}

private fun lockSummary(state: DashboardUiState): String {
    val values = listOf(state.lockFL, state.lockFR, state.lockRL, state.lockRR)
    if (values.all { it == null }) return "--"
    return if (values.all(BydSignalSemantics::isDoorLocked)) "已锁" else "未锁"
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
    else -> gear?.toString() ?: "--"
}

private fun driveModeText(mode: Int?): String = when (mode) {
    1 -> "标准模式"
    2 -> "经济模式"
    3 -> "运动模式"
    4 -> "雪地模式"
    5 -> "泥地模式"
    6 -> "沙地模式"
    else -> mode?.let { "模式 $it" } ?: "--"
}

private fun workModeText(mode: Int?): String = when (mode) {
    0 -> "停止"
    1 -> "EV"
    2 -> "强制 EV"
    3 -> "HEV"
    4 -> "燃油"
    5 -> "保持"
    else -> mode?.let { "模式 $it" } ?: "--"
}
