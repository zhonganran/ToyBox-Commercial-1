package com.byd.wjx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.FirstLaunchSpecialPermission
import com.byd.wjx.data.system.SettingsPermissionHealthItem
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SettingsPermissionHealthSection(
    maintenance: SettingsMaintenanceUiState,
    onRefresh: () -> Unit,
    onRepair: () -> Unit,
    onOpenSpecialPermission: (FirstLaunchSpecialPermission) -> Boolean,
    onOpenAppPermissionSettings: () -> Boolean,
) {
    val snapshot = maintenance.permissionHealth
    SectionHeader(text = "权限与运行环境")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val summaryColor = if (snapshot.unresolvedRequiredCount == 0) AccentGreen else SocRed
            Text(
                if (snapshot.items.isEmpty()) {
                    "正在读取权限状态"
                } else if (snapshot.unresolvedRequiredCount == 0) {
                    "核心权限与后台服务正常"
                } else {
                    "还有 ${snapshot.unresolvedRequiredCount} 项需要处理"
                },
                color = summaryColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            snapshot.items.forEach { item ->
                PermissionHealthRow(
                    item = item,
                    onOpenSpecialPermission = onOpenSpecialPermission,
                    onOpenAppPermissionSettings = onOpenAppPermissionSettings,
                )
            }

            snapshot.message?.let { message ->
                Text(
                    message,
                    color = if (snapshot.unresolvedRequiredCount == 0) AccentGreen else AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !maintenance.isPermissionHealthLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (maintenance.isPermissionHealthLoading) "检查中" else "重新检查",
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                Button(
                    onClick = onRepair,
                    enabled = !maintenance.isPermissionHealthLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = AccentContent,
                    ),
                ) {
                    Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("自动修复", modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionHealthRow(
    item: SettingsPermissionHealthItem,
    onOpenSpecialPermission: (FirstLaunchSpecialPermission) -> Boolean,
    onOpenAppPermissionSettings: () -> Boolean,
) {
    val color = when {
        item.ready -> AccentGreen
        item.optional -> AccentOrange
        else -> SocRed
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(item.detail, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (!item.ready && (item.specialPermission != null || item.openAppPermissionSettings)) {
            IconButton(
                onClick = {
                    item.specialPermission?.let(onOpenSpecialPermission)
                        ?: onOpenAppPermissionSettings()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "打开${item.title}授权设置",
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            when {
                item.ready -> "正常"
                item.optional -> "按需"
                else -> "待处理"
            },
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
