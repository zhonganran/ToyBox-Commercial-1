package com.byd.wjx.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary

@Composable
internal fun SocGauge(state: DashboardUiState, modifier: Modifier = Modifier) {
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
                drawLine(
                    Color.White.copy(alpha = 0.82f),
                    Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * inner,
                        center.y + kotlin.math.sin(angle).toFloat() * inner,
                    ),
                    Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * outer,
                        center.y + kotlin.math.sin(angle).toFloat() * outer,
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
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
internal fun SpeedGauge(state: DashboardUiState, modifier: Modifier = Modifier) {
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
