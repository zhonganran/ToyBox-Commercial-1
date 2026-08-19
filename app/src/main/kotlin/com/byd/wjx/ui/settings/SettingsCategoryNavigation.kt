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
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    Creative,
    Data,
}

@Composable
internal fun SettingsCategoryHeader(
    category: SettingsCategory,
    state: SettingsUiState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = category.status(state)
    val accent = category.accent(state)

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
    onNavigateToPlaces: () -> Unit,
    onNavigateToCreativeWorkshop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (category != SettingsCategory.Auth) {
            SettingsCategoryHero(category, state)
        }
        when (category) {
            SettingsCategory.Auth -> {
                AuthorizationRailCard(state = state, viewModel = viewModel)
                SettingsCapabilityCard(state)
            }
            SettingsCategory.Service -> ServiceAuthorizationSection(state, viewModel)
            SettingsCategory.Vehicle -> {
                BatterySection(state, viewModel)
                PlacesSection(onNavigateToPlaces)
            }
            SettingsCategory.Control -> DisplaySection(state, viewModel)
            SettingsCategory.Led -> LedLinkSettingsSection()
            SettingsCategory.EventSound -> VehicleEventSoundSection(state, viewModel)
            SettingsCategory.Creative -> CreativeWorkshopSettingsSection(onNavigateToCreativeWorkshop)
            SettingsCategory.Data -> AppSection(state, viewModel)
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
        SettingsCategory.Creative -> "07"
        SettingsCategory.Data -> "08"
    }

private val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.Auth -> Icons.Outlined.Lock
        SettingsCategory.Service -> Icons.Outlined.VerifiedUser
        SettingsCategory.Vehicle -> Icons.Outlined.Place
        SettingsCategory.Control -> Icons.Outlined.Key
        SettingsCategory.Led -> Icons.Outlined.BluetoothConnected
        SettingsCategory.EventSound -> Icons.Outlined.MusicNote
        SettingsCategory.Creative -> Icons.Outlined.Palette
        SettingsCategory.Data -> Icons.Outlined.CloudUpload
    }

private val SettingsCategory.title: String
    get() = when (this) {
        SettingsCategory.Auth -> "授权激活"
        SettingsCategory.Service -> "服务与授权"
        SettingsCategory.Vehicle -> "车辆费用与地点"
        SettingsCategory.Control -> "按键 / 显示 / 车外通道"
        SettingsCategory.Led -> "LED联动"
        SettingsCategory.EventSound -> "车辆事件音效"
        SettingsCategory.Creative -> "创意工坊"
        SettingsCategory.Data -> "数据与更新"
    }

private val SettingsCategory.subtitle: String
    get() = when (this) {
        SettingsCategory.Auth -> "激活码、车架号、试用状态和授权能力"
        SettingsCategory.Service -> "永久授权 $PERMANENT_AUTH_PRICE、联系客服、售后支持"
        SettingsCategory.Vehicle -> "电池容量、充电价格、能耗区间和常用地点"
        SettingsCategory.Control -> "车外音效通道、方向盘快捷键、仪表盘投射"
        SettingsCategory.Led -> "蓝牙屏幕、车辆事件联动和内置画面测试"
        SettingsCategory.EventSound -> "锁车、解锁和哨兵的后台车外音效"
        SettingsCategory.Creative -> "在线 3D 车型、车漆、彩绘和车窗膜"
        SettingsCategory.Data -> "界面主题、行程轨迹、备份恢复、更新和关于"
    }

private fun SettingsCategory.status(state: SettingsUiState): String =
    when (this) {
        SettingsCategory.Auth -> state.activation.statusLabel
        SettingsCategory.Service -> if (state.activation.activated) "已授权" else PERMANENT_AUTH_PRICE
        SettingsCategory.Vehicle -> "费用 / 能耗"
        SettingsCategory.Control -> if (state.activation.activated || state.activation.trialActive) "高级可用" else "部分需激活"
        SettingsCategory.Led -> "设备联动"
        SettingsCategory.EventSound -> {
            val count = LockSoundEvent.entries.count { state.lockSound.enabled(it) }
            if (count > 0) "${count} 项开启" else "未开启"
        }
        SettingsCategory.Creative -> "在线资源"
        SettingsCategory.Data -> when (state.themeMode) {
            "light" -> "白天主题"
            "dark" -> "黑暗主题"
            else -> "自适应主题"
        }
    }

private fun SettingsCategory.accent(state: SettingsUiState): Color =
    when (this) {
        SettingsCategory.Auth -> if (state.activation.activated) AccentGreen else if (state.activation.trialActive) AccentOrange else SocRed
        SettingsCategory.Service -> AccentOrange
        SettingsCategory.Vehicle -> AccentBlue
        SettingsCategory.Control -> AccentGreen
        SettingsCategory.Led -> AccentBlue
        SettingsCategory.EventSound -> AccentOrange
        SettingsCategory.Creative -> AccentPurple
        SettingsCategory.Data -> AccentPurple
    }

@Composable
private fun SettingsCategoryHero(category: SettingsCategory, state: SettingsUiState) {
    val accent = category.accent(state)
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
                category.status(state),
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

@Composable
private fun CreativeWorkshopSettingsSection(onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.90f)),
        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(AccentPurple.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("我的车辆设计", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("车型和彩绘按需从服务器加载", color = TextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = Color.White),
            ) {
                Text("进入创意工坊", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
