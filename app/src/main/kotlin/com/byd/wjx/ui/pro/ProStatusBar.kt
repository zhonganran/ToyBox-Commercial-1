package com.byd.wjx.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.GlassWhite
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun ProTopStatusBar(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    var clock by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = formatClock()
            delay(30_000L)
        }
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(40.dp)
            .background(GlassWhite.copy(alpha = 0.72f))
            .border(0.5.dp, CardBorder.copy(alpha = 0.72f), RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp),
    ) {
        val compact = maxWidth < 980.dp
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Icon(
                Icons.Outlined.ViewInAr,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "超级玩具箱",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            StatusDivider()
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (state.vehicleDataConnected) Color(0xFF15CB55) else Color(0xFFFF6B6B)),
            )
            Text(
                if (state.vehicleDataConnected) "车辆在线" else "车辆离线",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
            Text(
                "${state.exteriorTemp?.let { "$it°C" } ?: "--°C"}  外",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            StatusDivider()
            Icon(Icons.Outlined.Air, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
            Text(
                "${state.insideTemp?.let { "$it°C" } ?: "--°C"}  内",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            StatusDivider()
            Text(clock, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!compact) {
                Text(
                    freshnessText(state.lastVehicleDataAt),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatusDivider() {
    Box(Modifier.width(1.dp).height(16.dp).background(CardBorder.copy(alpha = 0.72f)))
}

internal fun freshnessText(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
    val age = timestamp?.let { (now - it).coerceAtLeast(0L) } ?: return "等待更新"
    return when {
        age < 10_000L -> "刚刚更新"
        age < 60_000L -> "${age / 1_000L} 秒前"
        age < 3_600_000L -> "${age / 60_000L} 分钟前"
        else -> "数据待刷新"
    }
}

private fun formatClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
