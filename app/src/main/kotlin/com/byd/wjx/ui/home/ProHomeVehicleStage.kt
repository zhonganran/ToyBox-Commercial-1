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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun VehicleStage(
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
