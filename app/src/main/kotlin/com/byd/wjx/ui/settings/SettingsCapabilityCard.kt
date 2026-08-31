package com.byd.wjx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.*

@Composable
internal fun SettingsCapabilityCard(state: SettingsUiState) {
    val activation = state.activation
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.88f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("授权能力", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            CapabilityRow("基础功能", "音效播放、歌词显示、常用设置", "可用", AccentGreen)
            CapabilityRow(
                "高级功能",
                "解锁/锁车/哨兵音效、按键快捷操作、仪表投射、电子后视镜、LED 联动",
                if (activation.activated || activation.trialActive) "可用" else "需激活",
                if (activation.activated || activation.trialActive) AccentBlue else SocRed,
            )
            CapabilityRow(
                "云端恢复",
                if (activation.activated) "重装或更新后按车架号恢复授权" else "激活后自动绑定当前车架号",
                if (activation.activated) "已开启" else "待激活",
                if (activation.activated) AccentGreen else TextMuted,
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    subtitle: String,
    status: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Text(
            status,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
