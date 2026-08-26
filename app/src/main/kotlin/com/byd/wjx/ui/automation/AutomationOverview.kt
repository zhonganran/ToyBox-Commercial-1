package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

internal fun automationTriggerIcon(trigger: TriggerDef?): ImageVector = when (trigger?.kind) {
    "network_available" -> Icons.Outlined.Wifi
    "interval" -> Icons.Outlined.Sync
    "board_vehicle", "service_start" -> Icons.Outlined.Bolt
    else -> when (trigger?.param) {
        "Gear" -> Icons.Outlined.Settings
        "Speed" -> Icons.Outlined.Timer
        "ChargingStatus" -> Icons.Outlined.Bolt
        else -> Icons.Outlined.Link
    }
}

internal fun automationActionIcon(action: ActionDef?): ImageVector = when (action?.kind) {
    "inside_sound", "outside_sound" -> Icons.AutoMirrored.Outlined.VolumeUp
    "notification_silent", "notification_sound" -> Icons.Outlined.Notifications
    "app_launch" -> Icons.Outlined.Apps
    "navigate" -> Icons.Outlined.Navigation
    "auto_door" -> Icons.Outlined.Lock
    "play_music", "play_video" -> Icons.Outlined.PlayArrow
    "sentry_mode" -> Icons.Outlined.Shield
    else -> Icons.Outlined.Bolt
}

internal fun automationTriggerSummary(trigger: TriggerDef?, exitOnly: Boolean = false): String = when {
    trigger == null -> "等待设置触发条件"
    isChargeGunUnplugEvent(trigger, exitOnly) -> "充电枪已拔出"
    trigger.kind == "interval" -> "每 ${trigger.value} 秒"
    trigger.kind == "board_vehicle" -> "上车开门"
    trigger.displayName.isNotBlank() -> trigger.displayName
    else -> "${trigger.chineseName} ${trigger.operator} ${trigger.value}"
}

internal fun isChargeGunUnplugEvent(trigger: TriggerDef?, exitOnly: Boolean): Boolean =
    exitOnly &&
        trigger?.param == "ChargeGunState" &&
        trigger.operator == ">=" &&
        trigger.value == "2"

internal fun automationActionSummary(action: ActionDef?): String =
    action?.displayName?.takeIf { it.isNotBlank() } ?: "等待设置执行动作"

@Composable
internal fun LinkageEmptyState(hasRules: Boolean, onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.88f)),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasRules) "当前筛选下没有规则" else "还没有智能联动规则",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasRules) {
                    "切换筛选条件，或创建一条新的智能联动。"
                } else {
                    "创建规则后，车辆状态变化时可以自动执行动作。"
                },
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = AccentContent,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.automation_create_button),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
