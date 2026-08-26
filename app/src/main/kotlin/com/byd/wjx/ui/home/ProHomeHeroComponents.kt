package com.byd.wjx.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.GlassStroke
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

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
internal fun PowertrainSpeedStrip(
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
    icon: ImageVector,
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
