package com.byd.wjx.ui.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
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

@Composable
internal fun HomeHero(
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
internal fun HeroPill(text: String, color: Color) {
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
