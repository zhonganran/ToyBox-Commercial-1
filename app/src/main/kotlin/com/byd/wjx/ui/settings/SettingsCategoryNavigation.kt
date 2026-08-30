package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.AccentPurple
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

internal enum class SettingsCategory {
    Auth,
    Service,
    Vehicle,
    Control,
    Led,
    EventSound,
    Data,
    AdbRescue,
}

@Composable
internal fun SettingsCategoryHeader(
    category: SettingsCategory,
    state: SettingsUiState,
    adbRescueState: AdbRescueSettingsUiState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = category.status(state, adbRescueState)
    val accent = category.accent(state, adbRescueState)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurface.copy(alpha = if (selected) 0.94f else 0.68f),
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) accent.copy(alpha = 0.76f) else CardBorder,
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(accent.copy(alpha = if (selected) 0.18f else 0.10f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(category.icon, contentDescription = null, tint = accent, modifier = Modifier.size(25.dp))
                Text(
                    category.indexLabel,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(accent, RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(category.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    category.subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                status,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accent.copy(alpha = if (selected) 0.18f else 0.10f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
internal fun SettingsCategoryContent(
    category: SettingsCategory,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    adbRescueState: AdbRescueSettingsUiState,
    adbRescueViewModel: AdbRescueSettingsViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (category != SettingsCategory.Auth) {
            SettingsCategoryHero(category, state, adbRescueState)
        }
        when (category) {
            SettingsCategory.Auth -> {
                AuthorizationRailCard(state = state, viewModel = viewModel)
                SettingsCapabilityCard(state)
            }
            SettingsCategory.Service -> ServiceAuthorizationSection(state, viewModel)
            SettingsCategory.Vehicle -> {
                BatterySection(state, viewModel)
            }
            SettingsCategory.Control -> DisplaySection(state, viewModel)
            SettingsCategory.Led -> LedLinkSettingsSection(
                activationAllowed = state.activation.activated || state.activation.trialActive,
            )
            SettingsCategory.EventSound -> VehicleEventSoundSection(state, viewModel)
            SettingsCategory.Data -> AppSection(state, viewModel)
            SettingsCategory.AdbRescue -> AdbRescueSettingsSection(adbRescueViewModel)
        }
    }
}

private val SettingsCategory.indexLabel: String
    get() = when (this) {
        SettingsCategory.Auth -> "01"
        SettingsCategory.Service -> "02"
        SettingsCategory.Vehicle -> "03"
        SettingsCategory.Control -> "04"
        SettingsCategory.Led -> "05"
        SettingsCategory.EventSound -> "06"
        SettingsCategory.Data -> "07"
        SettingsCategory.AdbRescue -> "08"
    }

private val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.Auth -> Icons.Outlined.Lock
        SettingsCategory.Service -> Icons.Outlined.VerifiedUser
        SettingsCategory.Vehicle -> Icons.Outlined.BatteryChargingFull
        SettingsCategory.Control -> Icons.Outlined.Key
        SettingsCategory.Led -> Icons.Outlined.BluetoothConnected
        SettingsCategory.EventSound -> Icons.Outlined.MusicNote
        SettingsCategory.Data -> Icons.Outlined.CloudUpload
        SettingsCategory.AdbRescue -> Icons.Outlined.DeveloperMode
    }

private val SettingsCategory.title: String
    get() = when (this) {
        SettingsCategory.Auth -> "授权激活"
        SettingsCategory.Service -> "服务与授权"
        SettingsCategory.Vehicle -> "车辆费用与能耗"
        SettingsCategory.Control -> "按键 / 显示 / 车外通道"
        SettingsCategory.Led -> "LED联动"
        SettingsCategory.EventSound -> "车辆事件音效"
        SettingsCategory.Data -> "数据与更新"
        SettingsCategory.AdbRescue -> "ADB救援"
    }

private val SettingsCategory.subtitle: String
    get() = when (this) {
        SettingsCategory.Auth -> "激活码、车架号、试用状态和授权能力"
        SettingsCategory.Service -> "永久授权 $PERMANENT_AUTH_PRICE、联系客服、售后支持"
        SettingsCategory.Vehicle -> "电池容量、充电价格和能耗区间"
        SettingsCategory.Control -> "车外音效通道、方向盘快捷键、仪表盘投射"
        SettingsCategory.Led -> "独立蓝牙协议、屏幕亮度和车辆事件联动"
        SettingsCategory.EventSound -> "锁车、解锁和哨兵的后台车外音效"
        SettingsCategory.Data -> "界面主题、行程轨迹、备份恢复、更新和关于"
        SettingsCategory.AdbRescue -> "标准 ADB、BYD 无线 ADB、开机恢复与救援重试"
    }

private fun SettingsCategory.status(
    state: SettingsUiState,
    adbRescueState: AdbRescueSettingsUiState,
): String =
    when (this) {
        SettingsCategory.Auth -> state.activation.statusLabel
        SettingsCategory.Service -> if (state.activation.activated) "已授权" else PERMANENT_AUTH_PRICE
        SettingsCategory.Vehicle -> "费用 / 能耗"
        SettingsCategory.Control -> if (state.activation.activated || state.activation.trialActive) "高级可用" else "部分需激活"
        SettingsCategory.Led -> if (state.activation.activated || state.activation.trialActive) {
            "设备联动"
        } else {
            "需激活"
        }
        SettingsCategory.EventSound -> {
            val count = LockSoundEvent.entries.count { state.vehicleEventSound.config.enabled(it) }
            if (count > 0) "${count} 项开启" else "未开启"
        }
        SettingsCategory.Data -> when (state.appPreferences.themeMode) {
            "light" -> "白天主题"
            "dark" -> "黑暗主题"
            else -> "自适应主题"
        }
        SettingsCategory.AdbRescue -> adbRescueCategoryStatus(adbRescueState)
    }

internal fun adbRescueCategoryStatus(state: AdbRescueSettingsUiState): String = when {
    !state.guardEnabled -> "守护关闭"
    state.report == null -> "待检查"
    state.report.allModesEnabled && state.report.writeSecureSettingsGranted -> "守护正常"
    state.report.allModesEnabled -> "权限缺失"
    else -> "需修复"
}

private fun SettingsCategory.accent(
    state: SettingsUiState,
    adbRescueState: AdbRescueSettingsUiState,
): Color =
    when (this) {
        SettingsCategory.Auth -> if (state.activation.activated) AccentGreen else if (state.activation.trialActive) AccentOrange else SocRed
        SettingsCategory.Service -> AccentOrange
        SettingsCategory.Vehicle -> AccentBlue
        SettingsCategory.Control -> AccentGreen
        SettingsCategory.Led -> if (state.activation.activated || state.activation.trialActive) AccentBlue else SocRed
        SettingsCategory.EventSound -> AccentOrange
        SettingsCategory.Data -> AccentPurple
        SettingsCategory.AdbRescue -> when (adbRescueCategoryStatus(adbRescueState)) {
            "守护正常" -> AccentGreen
            "守护关闭", "待检查" -> AccentBlue
            "权限缺失" -> AccentOrange
            else -> SocRed
        }
    }

@Composable
private fun SettingsCategoryHero(
    category: SettingsCategory,
    state: SettingsUiState,
    adbRescueState: AdbRescueSettingsUiState,
) {
    val accent = category.accent(state, adbRescueState)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                category.indexLabel,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(category.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(category.subtitle, color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Text(
                category.status(state, adbRescueState),
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accent.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}
