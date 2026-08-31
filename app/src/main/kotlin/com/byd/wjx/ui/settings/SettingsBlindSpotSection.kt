package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.camera.blindspot.BlindSpotDisplayTarget
import com.byd.wjx.camera.blindspot.BlindSpotPreferences
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun BlindSpotSettingsEntry(
    activationAllowed: Boolean,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = remember(context) { BlindSpotPreferences(context).snapshot() }
    val status = when {
        !activationAllowed -> "需要激活"
        snapshot.enabled -> "已开启"
        else -> "未开启"
    }
    val destination = when {
        snapshot.left.target == BlindSpotDisplayTarget.CLUSTER &&
            snapshot.right.target == BlindSpotDisplayTarget.CLUSTER -> "仪表显示"
        snapshot.left.target == BlindSpotDisplayTarget.CENTER &&
            snapshot.right.target == BlindSpotDisplayTarget.CENTER -> "中控显示"
        else -> "左右独立显示"
    }

    SectionHeader(text = "电子后视镜")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = if (activationAllowed) AccentGreen else TextMuted,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "电子后视镜",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "全屏预览、画面框选、鱼眼矫正和转向触发设置",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                Text("$status · $destination", color = AccentGreen, fontSize = 12.sp)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "进入电子后视镜设置",
                tint = TextSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
