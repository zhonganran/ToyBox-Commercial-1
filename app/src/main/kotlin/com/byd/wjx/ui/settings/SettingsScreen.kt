package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.cluster.ClusterMode
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.CENTER_OFFSET_PCT
import com.byd.wjx.cluster.MAX_OFFSET_PCT
import com.byd.wjx.cluster.MAX_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_OFFSET_PCT
import com.byd.wjx.cluster.MIN_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_SCALE_PCT
import com.byd.wjx.cluster.MAX_SCALE_PCT
import com.byd.wjx.cluster.DEFAULT_SCALE_PCT
import com.byd.wjx.cluster.NAVI_PACKAGE
import com.byd.wjx.cluster.ProjectionLayout
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutBinding
import com.byd.wjx.cluster.KeyShortcutPressType
import com.byd.wjx.cluster.KeyShortcutSettings
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContactSupport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.byd.wjx.cluster.DEFAULT_TRIGGER_KEYCODE
import com.byd.wjx.cluster.SteeringWheelKeyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import com.byd.wjx.R
import com.byd.wjx.audio.ExteriorChannelMode
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.*
import java.io.File
import java.net.URL

private val PrimaryColor = AccentGreen
private const val PERMANENT_AUTH_PRICE = "15.9 元"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPlaces: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsContext = LocalContext.current

    // Recalculate confirmation dialog
    if (state.showRecalcConfirm) {
        val tariffLabel = when (state.tripCostTariff) {
            "home" -> state.homeTariff
            "dc" -> state.dcTariff
            else -> state.tripCostTariff
        }
        AlertDialog(
            onDismissRequest = { viewModel.hideRecalcConfirm() },
            title = { Text(stringResource(R.string.settings_recalc_dialog_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.settings_recalc_dialog_text, tariffLabel, state.currencySymbol),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRecalc() }) {
                    Text(stringResource(R.string.settings_recalc_confirm_button), color = AccentOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRecalcConfirm() }) {
                    Text(stringResource(R.string.settings_cancel_button), color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }

    // Update dialog
    if (state.showUpdateDialog) {
        UpdateDialog(
            currentVersion = state.appVersion,
            state = state.updateDialogState,
            onCheck = {
                when (state.updateDialogState) {
                    is UpdateState.Available -> viewModel.downloadUpdate()
                    else -> viewModel.checkForUpdate()
                }
            },
            onDismiss = { viewModel.hideUpdateDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 62.dp, vertical = 22.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("设置", color = TextPrimary, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                Text("授权、车辆、控制和数据管理", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                "v${state.appVersion}",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.52f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val compact = maxWidth < 900.dp
            var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.Auth) }
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(SettingsCategory.values().toList(), key = { it.name }) { category ->
                            SettingsCategoryHeader(
                                category = category,
                                state = state,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                modifier = Modifier.width(268.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SettingsCategoryContent(
                            category = selectedCategory,
                            state = state,
                            viewModel = viewModel,
                            onNavigateToPlaces = onNavigateToPlaces,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.68f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsCategory.values().forEach { category ->
                            SettingsCategoryHeader(
                                category = category,
                                state = state,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.82f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SettingsCategoryContent(
                            category = selectedCategory,
                            state = state,
                            viewModel = viewModel,
                            onNavigateToPlaces = onNavigateToPlaces,
                        )
                    }
                }
            }
        }
    }
}

private enum class SettingsCategory {
    Auth,
    Service,
    Vehicle,
    Control,
    Led,
    EventSound,
    Data,
}

@Composable
private fun SettingsCategoryHeader(
    category: SettingsCategory,
    state: SettingsUiState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (category) {
        SettingsCategory.Auth -> category.title
        SettingsCategory.Service -> category.title
        SettingsCategory.Vehicle -> category.title
        SettingsCategory.Control -> category.title
        SettingsCategory.Led -> category.title
        SettingsCategory.EventSound -> category.title
        SettingsCategory.Data -> category.title
    }
    val subtitle = when (category) {
        SettingsCategory.Auth -> category.subtitle
        SettingsCategory.Service -> category.subtitle
        SettingsCategory.Vehicle -> category.subtitle
        SettingsCategory.Control -> category.subtitle
        SettingsCategory.Led -> category.subtitle
        SettingsCategory.EventSound -> category.subtitle
        SettingsCategory.Data -> category.subtitle
    }
    val status = category.status(state)
    val accent = category.accent(state)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color.White.copy(alpha = 0.84f) else Color.White.copy(alpha = 0.48f),
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) accent.copy(alpha = 0.76f) else Color.White.copy(alpha = 0.70f),
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
                    fontSize = 8.sp,
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
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                status,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accent.copy(alpha = if (selected) 0.18f else 0.10f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigateToPlaces: () -> Unit,
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
            SettingsCategory.Service -> {
                ServiceAuthorizationSection(state, viewModel)
            }
            SettingsCategory.Vehicle -> {
                BatterySection(state, viewModel)
                PlacesSection(onNavigateToPlaces)
            }
            SettingsCategory.Control -> {
                DisplaySection(state, viewModel)
            }
            SettingsCategory.Led -> {
                LedLinkSettingsSection()
            }
            SettingsCategory.EventSound -> {
                VehicleEventSoundSection(state, viewModel)
            }
            SettingsCategory.Data -> {
                AppSection(state, viewModel)
            }
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
    }

private val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.Auth -> Icons.Outlined.Lock
        SettingsCategory.Service -> Icons.Outlined.VerifiedUser
        SettingsCategory.Vehicle -> Icons.Outlined.Place
        SettingsCategory.Control -> Icons.Outlined.Key
        SettingsCategory.Led -> Icons.Outlined.BluetoothConnected
        SettingsCategory.EventSound -> Icons.Outlined.MusicNote
        SettingsCategory.Data -> Icons.Outlined.CloudUpload
    }

private val SettingsCategory.title: String
    get() = when (this) {
        SettingsCategory.Auth -> "授权激活"
        SettingsCategory.Service -> "服务与授权"
        SettingsCategory.Vehicle -> "车辆费用与地点"
        SettingsCategory.Control -> "按键 / 仪表 / 车外通道"
        SettingsCategory.Led -> "LED联动"
        SettingsCategory.EventSound -> "车辆事件音效"
        SettingsCategory.Data -> "数据与更新"
    }

private val SettingsCategory.subtitle: String
    get() = when (this) {
        SettingsCategory.Auth -> "激活码、车架号、试用状态和授权能力"
        SettingsCategory.Service -> "永久授权 $PERMANENT_AUTH_PRICE、联系客服、售后支持"
        SettingsCategory.Vehicle -> "电池容量、充电价格、能耗区间和常用地点"
        SettingsCategory.Control -> "车外音效通道、方向盘快捷键、仪表盘投射"
        SettingsCategory.Led -> "蓝牙屏幕、车辆事件联动和内置画面测试"
        SettingsCategory.EventSound -> "锁车、解锁、哨兵、自动泊车的后台车外音效"
        SettingsCategory.Data -> "行程轨迹、备份恢复、更新和关于"
    }

private fun SettingsCategory.status(state: SettingsUiState): String =
    when (this) {
        SettingsCategory.Auth -> state.activation.statusLabel
        SettingsCategory.Service -> if (state.activation.activated) "已授权" else PERMANENT_AUTH_PRICE
        SettingsCategory.Vehicle -> "费用 / 能耗"
        SettingsCategory.Control -> if (state.activation.activated || state.activation.trialActive) "高级可用" else "部分需激活"
        SettingsCategory.Led -> "设备联动"
        SettingsCategory.EventSound -> {
            val count = LockSoundEvent.entries.count { state.lockSound.enabled(it) } +
                if (state.autoParkingSoundEnabled) 1 else 0
            if (count > 0) "${count} 项开启" else "未开启"
        }
        SettingsCategory.Data -> if (state.autoCheckUpdates) "自动更新" else "数据管理"
    }

private fun SettingsCategory.accent(state: SettingsUiState): Color =
    when (this) {
        SettingsCategory.Auth -> if (state.activation.activated) AccentGreen else if (state.activation.trialActive) AccentOrange else SocRed
        SettingsCategory.Service -> AccentOrange
        SettingsCategory.Vehicle -> AccentBlue
        SettingsCategory.Control -> AccentGreen
        SettingsCategory.Led -> AccentBlue
        SettingsCategory.EventSound -> AccentOrange
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
                Text(category.subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
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
private fun AuthorizationRailCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activation = state.activation
    val statusColor = when {
        activation.activated -> AccentGreen
        activation.trialActive -> AccentOrange
        else -> SocRed
    }
    val statusDetail = when {
        activation.activated -> "感谢您对「超级玩具箱」的信任与支持"
        activation.trialActive -> "剩余 ${activation.remainingTrialDays} 天试用，永久授权 $PERMANENT_AUTH_PRICE"
        else -> "试用已结束，永久授权 $PERMANENT_AUTH_PRICE"
    }
    fun copyText(label: String, value: String, toast: String) {
        if (value.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.70f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.86f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFBDF2F0), Color(0xFFC9E8FF), Color(0xFFDDF4FF)),
                        ),
                    )
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(150.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .background(AccentGreen.copy(alpha = 0.14f), RoundedCornerShape(46.dp))
                                .border(6.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(46.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(52.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (activation.activated) "永久授权" else if (activation.trialActive) "试用授权" else "等待激活",
                                    color = TextPrimary,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    if (activation.deviceCode.isBlank()) "设备未识别" else "设备已识别",
                                    color = if (activation.deviceCode.isBlank()) SocRed else AccentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background((if (activation.deviceCode.isBlank()) SocRed else AccentGreen).copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                                Text(
                                    activation.statusLabel,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            Text(statusDetail, color = TextPrimary.copy(alpha = 0.76f), fontSize = 13.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        AuthorizationValue(
                            label = "已绑定车辆（VIN）",
                            value = activation.deviceCode.ifBlank { "未读取到车架号" },
                            error = activation.deviceCode.isBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        AuthorizationValue(
                            label = "已绑定激活码",
                            value = activation.boundActivationCode.ifBlank {
                                if (activation.activated) "已绑定云端授权" else "尚未绑定"
                            },
                            error = !activation.activated,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { copyText("超级玩具箱授权信息", activation.boundActivationCode.ifBlank { activation.deviceCode }, "授权信息已复制") },
                            enabled = activation.deviceCode.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.72f), contentColor = TextPrimary),
                            modifier = Modifier.height(56.dp),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("复制", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.refreshActivation(refreshDeviceCode = true) },
                            enabled = !state.activationLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.72f), contentColor = TextPrimary),
                            modifier = Modifier.height(56.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (state.activationLoading) "读取中" else "刷新", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!activation.activated) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActivationMetricPill(
                            label = "试用状态",
                            value = if (activation.trialActive) "剩余 ${activation.remainingTrialDays} 天" else "已结束",
                            color = if (activation.trialActive) AccentOrange else SocRed,
                            modifier = Modifier.weight(1f),
                        )
                        ActivationMetricPill(
                            label = "车辆绑定",
                            value = if (activation.deviceCode.isBlank()) "待读取" else "已识别",
                            color = if (activation.deviceCode.isBlank()) SocRed else AccentBlue,
                            modifier = Modifier.weight(1f),
                        )
                        ActivationMetricPill(
                            label = "永久授权",
                            value = PERMANENT_AUTH_PRICE,
                            color = AccentGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = state.activationCodeInput,
                        onValueChange = { viewModel.updateActivationCode(it) },
                        label = { Text("输入激活码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = AccentGreen,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = AccentGreen,
                        ),
                    )
                    Button(
                        onClick = { viewModel.activate() },
                        enabled = !state.activationLoading && state.activationCodeInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                    ) {
                        Text("立即激活", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.activationMessage?.let {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        it,
                        color = if (it.contains("成功")) AccentGreen else SocRed,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorizationValue(
    label: String,
    value: String,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TextPrimary.copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(
            value,
            color = if (error) SocRed else TextPrimary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivationMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun SettingsCapabilityCard(state: SettingsUiState) {
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
            CapabilityRow("基础功能", "音效播放、桌面组件、常用设置", "可用", AccentGreen)
            CapabilityRow(
                "高级功能",
                "解锁/锁车/哨兵音效、自动泊车音效、按键快捷操作、仪表投射",
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

@Composable
private fun ServiceAuthorizationSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activation = state.activation
    var activeQrTarget by remember { mutableStateOf<ServiceQrTarget?>(null) }
    val wechatTarget = serviceQrTarget(state, "wechat", "微信客服", "点击查看二维码", "待配置微信二维码", AccentGreen)
    val wechatGroupTarget = serviceQrTarget(state, "wechat_group", "微信交流群", "点击查看二维码", "待配置微信交流群二维码", AccentPurple)
    val paymentTarget = serviceQrTarget(
        state,
        "payment",
        "付款二维码",
        "永久授权 $PERMANENT_AUTH_PRICE / 车，确认后付款",
        "待配置收款二维码",
        AccentOrange,
    )

    activeQrTarget?.let { target ->
        ServiceQrDialog(target = target, onDismiss = { activeQrTarget = null })
    }

    SectionHeader(text = "服务与授权")
    ServicePanel(
        title = "官方客服",
        subtitle = "购买授权、功能咨询、车型适配和售后支持",
        accent = AccentBlue,
    ) {
        Text(
            "如需购买授权、功能咨询、车型适配或售后支持，请通过以下方式联系我。购买前请先说明车型、系统版本和需要使用的功能，避免因车型差异导致部分功能不可用。",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.serviceQrMessage ?: if (state.serviceQrLoading) "正在读取后台二维码" else "二维码从后台动态读取",
                color = if (state.serviceQrMessage?.contains("失败") == true) SocRed else TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.refreshServiceQrCodes() },
                enabled = !state.serviceQrLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = TextPrimary),
            ) {
                Text(if (state.serviceQrLoading) "读取中" else "刷新", fontSize = 12.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServiceQrEntryCard(
                target = wechatTarget,
                modifier = Modifier.weight(1f),
                onClick = { activeQrTarget = it },
            )
            ServiceQrEntryCard(
                target = wechatGroupTarget,
                modifier = Modifier.weight(1f),
                onClick = { activeQrTarget = it },
            )
        }
    }

    ServicePanel(
        title = "购买永久授权 · $PERMANENT_AUTH_PRICE",
        subtitle = if (activation.activated) "当前车辆已开通永久授权" else "$PERMANENT_AUTH_PRICE / 车，一车一授权",
        accent = if (activation.activated) AccentGreen else AccentOrange,
    ) {
        Text(
            "超级玩具箱永久授权价格为 $PERMANENT_AUTH_PRICE / 车，采用一车一授权方式并绑定当前车辆设备信息。付款前请先联系客服确认车型与功能支持情况，付款后请发送付款截图和软件内显示的车架号，核验后为你开通授权。",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        ServiceInfoLine(
            label = "授权价格",
            value = "$PERMANENT_AUTH_PRICE / 车",
            valueColor = AccentOrange,
        )
        ServiceInfoLine(
            label = "当前状态",
            value = activation.statusLabel,
            valueColor = if (activation.activated) AccentGreen else if (activation.trialActive) AccentOrange else SocRed,
        )
        ServiceInfoLine(
            label = "绑定车架号",
            value = activation.deviceCode.ifBlank { "未读取到车架号" },
            valueColor = if (activation.deviceCode.isBlank()) SocRed else TextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (activation.deviceCode.isBlank()) {
                        Toast.makeText(context, "暂未读取到车架号", Toast.LENGTH_SHORT).show()
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("超级玩具箱车架号", activation.deviceCode))
                        Toast.makeText(context, "车架号已复制", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = activation.deviceCode.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
            ) {
                Text("复制车架号", fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = { activeQrTarget = paymentTarget },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = AccentContent),
            ) {
                Text("查看付款二维码", fontSize = 12.sp, maxLines = 1)
            }
        }
    }

    ServicePanel(
        title = "付款与售后规则",
        subtitle = "永久授权 $PERMANENT_AUTH_PRICE，确认后付款",
        accent = AccentPurple,
    ) {
        ServiceBullet("永久授权价格为 $PERMANENT_AUTH_PRICE / 车，一车一授权。")
        ServiceBullet("请先联系客服确认车型、系统版本和需要使用的功能。")
        ServiceBullet("付款完成后，请发送付款截图和软件内显示的车架号。")
        ServiceBullet("授权开通后，更新或重新安装会按车架号自动恢复。")
        ServiceBullet("付款即表示你已阅读并同意软件免责声明及授权规则。")
    }
}

private data class ServiceQrTarget(
    val key: String,
    val title: String,
    val subtitle: String,
    val placeholder: String,
    val imageUrl: String,
    val expiresAt: String,
    val description: String,
    val accent: Color,
)

private fun serviceQrTarget(
    state: SettingsUiState,
    key: String,
    fallbackTitle: String,
    fallbackSubtitle: String,
    fallbackPlaceholder: String,
    accent: Color,
): ServiceQrTarget {
    val remote = state.serviceQrCodes[key]
    return ServiceQrTarget(
        key = key,
        title = remote?.title?.ifBlank { fallbackTitle } ?: fallbackTitle,
        subtitle = remote?.subtitle?.ifBlank { fallbackSubtitle } ?: fallbackSubtitle,
        placeholder = if (remote?.imageUrl.isNullOrBlank()) fallbackPlaceholder else "正在加载二维码",
        imageUrl = remote?.imageUrl.orEmpty(),
        expiresAt = remote?.expiresAt.orEmpty(),
        description = remote?.description.orEmpty(),
        accent = accent,
    )
}

@Composable
private fun ServiceQrEntryCard(
    target: ServiceQrTarget,
    modifier: Modifier = Modifier,
    onClick: (ServiceQrTarget) -> Unit,
) {
    Column(
        modifier = modifier
            .background(target.accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable { onClick(target) }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(target.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(
            "点击查看二维码",
            color = target.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            maxLines = 1,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.74f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ServiceQrDialog(target: ServiceQrTarget, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(target.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(target.subtitle, color = TextSecondary, fontSize = 12.sp)
                    }
                    Text(
                        "X",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(CardSurface, RoundedCornerShape(999.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .size(230.dp)
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RemoteQrImage(target)
                }
                Text(
                    serviceQrDialogNote(target),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun RemoteQrImage(target: ServiceQrTarget) {
    val context = LocalContext.current
    var imageBytes by remember(target.key, target.imageUrl) { mutableStateOf<ByteArray?>(null) }
    var loadFailed by remember(target.key, target.imageUrl) { mutableStateOf(false) }

    LaunchedEffect(target.key, target.imageUrl) {
        loadFailed = false
        val cacheFile = serviceQrCacheFile(context, target.key)
        val cached = if (target.imageUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { if (cacheFile.exists()) cacheFile.readBytes() else null }.getOrNull()
            }
        } else {
            null
        }
        if (cached != null) {
            imageBytes = cached
        }
        if (target.imageUrl.isBlank()) {
            loadFailed = cached == null
            return@LaunchedEffect
        }
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                URL(target.imageUrl).openStream().use { input ->
                    input.readBytes()
                }.also { bytes ->
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeBytes(bytes)
                }
            }.getOrNull()
        }
        if (remote != null) {
            imageBytes = remote
        } else if (cached == null) {
            loadFailed = true
        }
    }

    val imageBitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = target.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        QrPlaceholderPattern()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (loadFailed && target.imageUrl.isNotBlank()) "二维码加载失败" else target.placeholder,
            color = Color(0xFF424750),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun serviceQrCacheFile(context: Context, key: String): File =
    File(context.cacheDir, "service_qrcodes/$key.img")

private fun serviceQrDialogNote(target: ServiceQrTarget): String {
    val remoteDescription = target.description.ifBlank {
        if (target.title.contains("付款")) {
            "请先联系客服确认车型、系统版本和功能支持情况后再付款。付款后发送截图和车架号用于开通授权。"
        } else {
            "二维码由后台动态更新，过期后无需更新 APK。"
        }
    }
    val description = if (target.key == "payment") {
        "永久授权价格：$PERMANENT_AUTH_PRICE / 车。\n$remoteDescription"
    } else {
        remoteDescription
    }
    return if (target.expiresAt.isNotBlank()) {
        "$description\n有效期：${target.expiresAt}"
    } else {
        description
    }
}

@Composable
private fun QrPlaceholderPattern() {
    Column(
        modifier = Modifier
            .size(132.dp)
            .background(Color(0xFFF7F8FA), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(9) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(9) { col ->
                    val finder = (row < 3 && col < 3) || (row < 3 && col > 5) || (row > 5 && col < 3)
                    val filled = finder || ((row * 7 + col * 5) % 4 == 0)
                    Spacer(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (filled) Color(0xFF111318) else Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServicePanel(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.96f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    title.take(1),
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            HorizontalDivider(color = CardBorder.copy(alpha = 0.72f))
            content()
        }
    }
}

@Composable
private fun ServiceInfoLine(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServiceBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BatterySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SectionHeader(text = stringResource(R.string.settings_battery_section_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsTextField(
                label = stringResource(R.string.settings_battery_capacity_label),
                value = state.batteryCapacity,
                onValueChange = { viewModel.saveBatteryCapacity(it) },
                keyboardType = KeyboardType.Decimal
            )
            SettingsTextField(
                label = stringResource(R.string.settings_tariff_home_label, state.currencySymbol),
                value = state.homeTariff,
                onValueChange = { viewModel.updateHomeTariff(it) },
                keyboardType = KeyboardType.Decimal
            )
            SettingsTextField(
                label = stringResource(R.string.settings_tariff_dc_label, state.currencySymbol),
                value = state.dcTariff,
                onValueChange = { viewModel.updateDcTariff(it) },
                keyboardType = KeyboardType.Decimal
            )
            Text(stringResource(R.string.settings_tariff_trip_label), color = TextSecondary, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip("慢充", state.tripCostTariff == "home") { viewModel.saveTripCostTariff("home") }
                UnitChip("快充", state.tripCostTariff == "dc") { viewModel.saveTripCostTariff("dc") }
                UnitChip(stringResource(R.string.settings_tariff_trip_custom_chip), state.tripCostTariff != "home" && state.tripCostTariff != "dc") {
                    viewModel.saveTripCostTariff(state.homeTariff)
                }
            }
            if (state.tripCostTariff != "home" && state.tripCostTariff != "dc") {
                SettingsTextField(
                    label = stringResource(R.string.settings_tariff_custom_label, state.currencySymbol),
                    value = state.tripCostTariff,
                    onValueChange = { viewModel.saveTripCostTariff(it) },
                    keyboardType = KeyboardType.Decimal
                )
            }

            // Save tariffs button
            Button(
                onClick = { viewModel.saveTariffs() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent)
            ) {
                Text(stringResource(R.string.settings_save_tariffs_button), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            state.tariffSaveStatus?.let {
                Text(it, color = AccentGreen, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.settings_tariff_future_note),
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
            )

            HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))

            // Recalculate all trips button
            Button(
                onClick = { viewModel.showRecalcConfirm() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f))
            ) {
                Text(stringResource(R.string.settings_recalc_all_button), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            state.recalcStatus?.let {
                Text(it, color = AccentGreen, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.settings_recalc_note),
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }

    SectionHeader(text = stringResource(R.string.settings_consumption_section_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsTextField(
                label = stringResource(R.string.settings_consumption_good_label),
                value = state.consumptionGood,
                onValueChange = { viewModel.saveConsumptionGood(it) },
                keyboardType = KeyboardType.Decimal
            )
            SettingsTextField(
                label = stringResource(R.string.settings_consumption_bad_label),
                value = state.consumptionBad,
                onValueChange = { viewModel.saveConsumptionBad(it) },
                keyboardType = KeyboardType.Decimal
            )
        }
    }
}

@Composable
private fun PlacesSection(onNavigateToPlaces: () -> Unit) {
    SectionHeader(text = stringResource(R.string.settings_places_section_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToPlaces() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Place,
                contentDescription = null,
                tint = AccentGreen,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_places_entry_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.settings_places_entry_description), color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val keyShortcutAllowed = state.activation.activated || state.activation.trialActive
    val prefs = remember {
        context.getSharedPreferences(ClusterProjectionManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val exteriorSettings = remember(context) { ExteriorHailingSettings(context) }
    val keyShortcutSettings = remember(context) { KeyShortcutSettings(context) }
    val soundLibrary = remember(context) { SoundLibrary(context.applicationContext) }
    val carProfile = state.carProfile
    var exteriorChannelMode by remember { mutableStateOf(exteriorSettings.channelMode) }
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ClusterEntryPoint::class.java)
    }
    var keyShortcutBindings by remember { mutableStateOf(keyShortcutSettings.load()) }
    var keyShortcutEditor by remember { mutableStateOf<KeyShortcutEditorState?>(null) }
    var keyShortcutLearning by remember { mutableStateOf(false) }
    var localSounds by remember { mutableStateOf(soundLibrary.listSounds()) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "麦克风权限已允许" else "未允许麦克风权限，实时喊话无法启动", Toast.LENGTH_SHORT).show()
    }
    var enabled by remember {
        mutableStateOf(prefs.getBoolean(ClusterProjectionManager.KEY_MIRROR_ENABLED, false))
    }
    var widthPct by remember {
        mutableStateOf(prefs.getInt(ClusterProjectionManager.KEY_WIDTH_PCT, MAX_PROJECTION_PCT))
    }
    var heightPct by remember {
        mutableStateOf(prefs.getInt(ClusterProjectionManager.KEY_HEIGHT_PCT, MAX_PROJECTION_PCT))
    }
    var offsetXPct by remember {
        mutableStateOf(prefs.getInt(ClusterProjectionManager.KEY_OFFSET_X_PCT, CENTER_OFFSET_PCT))
    }
    var offsetYPct by remember {
        mutableStateOf(prefs.getInt(ClusterProjectionManager.KEY_OFFSET_Y_PCT, CENTER_OFFSET_PCT))
    }
    var scalePct by remember {
        mutableStateOf(prefs.getInt(ClusterProjectionManager.KEY_SCALE_PCT, DEFAULT_SCALE_PCT))
    }
    var targetPkg by remember {
        mutableStateOf(prefs.getString(ClusterProjectionManager.KEY_TARGET_PACKAGE, NAVI_PACKAGE) ?: NAVI_PACKAGE)
    }
    var targetLabel by remember {
        mutableStateOf(
            prefs.getString(ClusterProjectionManager.KEY_TARGET_LABEL, null)
                ?: resolveAppLabel(context, targetPkg)
        )
    }
    var pickingApp by remember { mutableStateOf(false) }
    var learning by remember { mutableStateOf(false) }
    var triggerKey by remember {
        mutableStateOf(prefs.getInt(ClusterProjectionManager.KEY_TRIGGER_KEYCODE, DEFAULT_TRIGGER_KEYCODE))
    }
    var projectionLayout by remember {
        mutableStateOf(ClusterProjectionManager.readProjectionLayout(context))
    }

    SectionHeader(text = "车外音效")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = AccentGreen,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("车外通道", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    carProfile?.let(exteriorSettings::channelModeLabel) ?: "自动识别中",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip("自动", exteriorChannelMode == ExteriorChannelMode.AUTO) {
                    exteriorChannelMode = ExteriorChannelMode.AUTO
                    exteriorSettings.channelMode = ExteriorChannelMode.AUTO
                    viewModel.syncLockSoundNow("exterior_channel_auto")
                }
                UnitChip("A", exteriorChannelMode == ExteriorChannelMode.CHANNEL_0) {
                    exteriorChannelMode = ExteriorChannelMode.CHANNEL_0
                    exteriorSettings.channelMode = ExteriorChannelMode.CHANNEL_0
                    viewModel.syncLockSoundNow("exterior_channel_a")
                }
                UnitChip("B", exteriorChannelMode == ExteriorChannelMode.CHANNEL_1) {
                    exteriorChannelMode = ExteriorChannelMode.CHANNEL_1
                    exteriorSettings.channelMode = ExteriorChannelMode.CHANNEL_1
                    viewModel.syncLockSoundNow("exterior_channel_b")
                }
            }
        }
    }

    KeyShortcutSection(
        bindings = keyShortcutBindings,
        activationAllowed = keyShortcutAllowed,
        onAdd = {
            if (!keyShortcutAllowed) {
                Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                return@KeyShortcutSection
            }
            localSounds = soundLibrary.listSounds()
            keyShortcutEditor = KeyShortcutEditorState()
        },
        onEdit = {
            localSounds = soundLibrary.listSounds()
            keyShortcutEditor = it.toEditorState()
        },
        onEnabledChanged = { binding, checked ->
            if (checked && !keyShortcutAllowed) {
                keyShortcutBindings = keyShortcutSettings.upsert(binding.copy(enabled = false))
                Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                return@KeyShortcutSection
            }
            keyShortcutBindings = keyShortcutSettings.upsert(binding.copy(enabled = checked))
            SteeringWheelKeyService.resetShortcutRuntimeState()
            ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
        },
        onDelete = { binding ->
            keyShortcutBindings = keyShortcutSettings.delete(binding.id)
            SteeringWheelKeyService.resetShortcutRuntimeState()
        },
    )

    keyShortcutEditor?.let { editor ->
        KeyShortcutEditorDialog(
            editor = editor,
            sounds = localSounds,
            onEditorChanged = { keyShortcutEditor = it },
            onLearnKey = {
                ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
                keyShortcutLearning = true
            },
            onDismiss = { keyShortcutEditor = null },
            onSave = { next ->
                val binding = next.toBinding()
                if (binding.enabled && !keyShortcutAllowed) {
                    Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                    return@KeyShortcutEditorDialog
                }
                keyShortcutBindings = keyShortcutSettings.upsert(binding)
                SteeringWheelKeyService.resetShortcutRuntimeState()
                keyShortcutEditor = null
                if (
                    binding.actionType == KeyShortcutActionType.RealtimeHailing &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
                val message = if (enabled && binding.keyCode == triggerKey) {
                    "已保存；该按键也用于仪表投射，快捷操作会优先执行"
                } else {
                    "按键快捷操作已保存"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (keyShortcutLearning) {
        LearnButtonDialog(
            onSave = { code ->
                keyShortcutEditor = keyShortcutEditor?.copy(keyCode = code)
                keyShortcutLearning = false
            },
            onDismiss = { keyShortcutLearning = false },
            preferLongPress = keyShortcutEditor?.pressType == KeyShortcutPressType.Long,
        )
    }

    SectionHeader(text = stringResource(R.string.settings_display_mirror_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = AccentGreen,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_display_mirror_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.settings_display_mirror_desc),
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange@{ requested ->
                    val allowed = state.activation.activated || state.activation.trialActive
                    if (requested && !allowed) {
                        enabled = false
                        prefs.edit().putBoolean(ClusterProjectionManager.KEY_MIRROR_ENABLED, false).apply()
                        Toast.makeText(context, "按键映射到仪表盘需要激活后使用", Toast.LENGTH_SHORT).show()
                        return@onChange
                    }
                    enabled = requested
                    prefs.edit().putBoolean(ClusterProjectionManager.KEY_MIRROR_ENABLED, requested).apply()
                    if (!requested) {
                        ClusterProjectionManager.setMode(
                            context,
                            ClusterMode.OFF,
                            entryPoint.helperClient(),
                            entryPoint.helperBootstrap(),
                        )
                        ClusterProjectionManager.stopDuplicateProjectionFallback()
                    }
                    // Turning the switch on self-enables our a11y key filter via the daemon, so star
                    // control works on a clean install with no ADB (DiLink has no a11y settings UI).
                    if (requested) {
                        ClusterProjectionManager.enableStarControl(
                            entryPoint.helperClient(), entryPoint.helperBootstrap())
                        if (projectionLayout == ProjectionLayout.DUPLICATE) {
                            ClusterProjectionManager.prepareDuplicateProjection(context)
                        }
                    }
                },
                colors = bydSwitchColors(),
            )
        }
    }

    // Trigger-button row — only meaningful while the feature (and thus the a11y service) is on, so
    // learning is gated on `enabled`. Tapping opens the learn dialog.
    if (enabled) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable {
                    ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
                    learning = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = AccentGreen,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_display_button_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(steeringButtonLabel(triggerKey), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                Text(stringResource(R.string.settings_display_button_change), color = AccentGreen, fontSize = 13.sp)
            }
        }
    }

    if (learning) {
        LearnButtonDialog(
            onSave = { code ->
                triggerKey = code
                ClusterProjectionManager.setTriggerKeyCode(context, code)
                learning = false
            },
            onDismiss = { learning = false },
        )
    }

    SectionHeader(text = stringResource(R.string.settings_display_projection_layout_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip(
                    stringResource(R.string.settings_display_projection_layout_independent),
                    projectionLayout == ProjectionLayout.INDEPENDENT,
                ) {
                    projectionLayout = ProjectionLayout.INDEPENDENT
                    prefs.edit()
                        .putString(ClusterProjectionManager.KEY_PROJECTION_LAYOUT, ProjectionLayout.INDEPENDENT.name)
                        .apply()
                    ClusterProjectionManager.stopDuplicateProjectionFallback()
                    ClusterProjectionManager.reproject(
                        context, entryPoint.helperClient(), entryPoint.helperBootstrap())
                }
                UnitChip(
                    stringResource(R.string.settings_display_projection_layout_duplicate),
                    projectionLayout == ProjectionLayout.DUPLICATE,
                ) {
                    projectionLayout = ProjectionLayout.DUPLICATE
                    prefs.edit()
                        .putString(ClusterProjectionManager.KEY_PROJECTION_LAYOUT, ProjectionLayout.DUPLICATE.name)
                        .apply()
                    if (enabled) {
                        ClusterProjectionManager.prepareDuplicateProjection(context)
                    }
                    ClusterProjectionManager.reproject(
                        context, entryPoint.helperClient(), entryPoint.helperBootstrap())
                }
            }
            Text(
                text = if (projectionLayout == ProjectionLayout.DUPLICATE) {
                    stringResource(R.string.settings_display_projection_layout_duplicate_desc)
                } else {
                    stringResource(R.string.settings_display_projection_layout_independent_desc)
                },
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }

    // App picker only applies to independent mode. Duplicate mode mirrors whatever is already on
    // the centre screen, so showing the picker there would imply a target-app choice that is ignored.
    if (projectionLayout == ProjectionLayout.INDEPENDENT) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { pickingApp = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = AccentGreen,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_display_app_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(targetLabel, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }

    if (pickingApp && projectionLayout == ProjectionLayout.INDEPENDENT) {
        AppLaunchPickerDialog(
            currentPackage = targetPkg,
            onDismiss = { pickingApp = false },
            onSelect = { newPkg, newLabel ->
                targetPkg = newPkg
                targetLabel = newLabel
                prefs.edit()
                    .putString(ClusterProjectionManager.KEY_TARGET_PACKAGE, newPkg)
                    .putString(ClusterProjectionManager.KEY_TARGET_LABEL, newLabel)
                    .apply()
                pickingApp = false
            },
        )
    }

    if (projectionLayout == ProjectionLayout.INDEPENDENT) {
        SectionHeader(text = stringResource(R.string.settings_display_size_header))
        // Persist the new size and re-apply it live. reproject() is a no-op unless we are actively
        // projecting, so a tweak while OFF just lands in prefs and shows on the next star press.
        val applyGeometry: () -> Unit = {
            prefs.edit()
                .putInt(ClusterProjectionManager.KEY_WIDTH_PCT, widthPct)
                .putInt(ClusterProjectionManager.KEY_HEIGHT_PCT, heightPct)
                .putInt(ClusterProjectionManager.KEY_OFFSET_X_PCT, offsetXPct)
                .putInt(ClusterProjectionManager.KEY_OFFSET_Y_PCT, offsetYPct)
                .putInt(ClusterProjectionManager.KEY_SCALE_PCT, scalePct)
                .apply()
            ClusterProjectionManager.reproject(
                context, entryPoint.helperClient(), entryPoint.helperBootstrap())
        }
        // Defaults (100/100 size, centered, scale 100) reproduce the plain fullscreen projection, so
        // cars without a native mini zone (e.g. Leopard 3) need no tuning. The offset sliders only
        // matter once the window is smaller than the panel; on Sea Lion 07 they let the user move the
        // window into the native mini-cluster zone (#48).
        ClusterSizeSlider(
            stringResource(R.string.settings_display_size_width), widthPct, enabled,
            { widthPct = it }, applyGeometry,
            description = stringResource(R.string.settings_display_size_width_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_size_height), heightPct, enabled,
            { heightPct = it }, applyGeometry,
            description = stringResource(R.string.settings_display_size_height_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_offset_x), offsetXPct, enabled,
            { offsetXPct = it }, applyGeometry, MIN_OFFSET_PCT, MAX_OFFSET_PCT,
            description = stringResource(R.string.settings_display_offset_x_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_offset_y), offsetYPct, enabled,
            { offsetYPct = it }, applyGeometry, MIN_OFFSET_PCT, MAX_OFFSET_PCT,
            description = stringResource(R.string.settings_display_offset_y_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_scale), scalePct, enabled,
            { scalePct = it }, applyGeometry, MIN_SCALE_PCT, MAX_SCALE_PCT,
            description = stringResource(R.string.settings_display_scale_desc),
        )
    } else {
        Text(
            text = stringResource(R.string.settings_display_duplicate_fullscreen_note),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private data class KeyShortcutEditorState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val keyCode: Int = 0,
    val pressType: KeyShortcutPressType = KeyShortcutPressType.Short,
    val actionType: KeyShortcutActionType = KeyShortcutActionType.PlaySound,
    val soundPath: String = "",
    val soundName: String = "",
    val automationButtonId: Int = 1,
    val enabled: Boolean = true,
)

private fun KeyShortcutBinding.toEditorState(): KeyShortcutEditorState =
    KeyShortcutEditorState(
        id = id,
        keyCode = keyCode,
        pressType = pressType,
        actionType = actionType,
        soundPath = soundPath,
        soundName = soundName,
        automationButtonId = automationButtonId,
        enabled = enabled,
    )

private fun KeyShortcutEditorState.toBinding(): KeyShortcutBinding =
    KeyShortcutBinding(
        id = id,
        keyCode = keyCode,
        pressType = pressType,
        actionType = actionType,
        soundPath = soundPath,
        soundName = soundName,
        automationButtonId = automationButtonId,
        enabled = enabled,
    )

@Composable
private fun KeyShortcutSection(
    bindings: List<KeyShortcutBinding>,
    activationAllowed: Boolean,
    onAdd: () -> Unit,
    onEdit: (KeyShortcutBinding) -> Unit,
    onEnabledChanged: (KeyShortcutBinding, Boolean) -> Unit,
    onDelete: (KeyShortcutBinding) -> Unit,
) {
    SectionHeader(text = "按键快捷操作")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("方向盘快捷键", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (activationAllowed) "${bindings.size} 条规则" else "试用已结束，激活后可用",
                        color = if (activationAllowed) TextSecondary else SocRed,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("添加", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (bindings.isEmpty()) {
                Text("暂无快捷操作", color = TextSecondary, fontSize = 12.sp)
            } else {
                bindings.forEach { binding ->
                    KeyShortcutRow(
                        binding = binding,
                        onClick = { onEdit(binding) },
                        onEnabledChanged = { onEnabledChanged(binding, it) },
                        onDelete = { onDelete(binding) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyShortcutRow(
    binding: KeyShortcutBinding,
    onClick: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val keyLabel = steeringButtonLabel(binding.keyCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = when (binding.actionType) {
                    KeyShortcutActionType.PlaySound -> "按键播放音效"
                    KeyShortcutActionType.RealtimeHailing -> "按键实时喊话"
                    KeyShortcutActionType.Automation -> "按键智能联动"
                },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(keyLabel)
                    append(" · ")
                    append(binding.pressType.label)
                    if (binding.actionType == KeyShortcutActionType.PlaySound) {
                        append(" · ")
                        append(binding.soundName.ifBlank { "未选择音效" })
                    } else if (binding.actionType == KeyShortcutActionType.Automation) {
                        append(" · 联动按钮 ${binding.automationButtonId}")
                    }
                },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = binding.enabled,
            onCheckedChange = onEnabledChanged,
            colors = bydSwitchColors(),
        )
        TextButton(onClick = onDelete) {
            Text("删除", color = Color(0xFFE85D5D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun KeyShortcutEditorDialog(
    editor: KeyShortcutEditorState,
    sounds: List<SoundLibrary.SoundItem>,
    onEditorChanged: (KeyShortcutEditorState) -> Unit,
    onLearnKey: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (KeyShortcutEditorState) -> Unit,
) {
    var error by remember(editor.id) { mutableStateOf<String?>(null) }
    val selectedKey = if (editor.keyCode > 0) steeringButtonLabel(editor.keyCode) else "未识别按键"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按键快捷操作") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("动作", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyShortcutChoiceChip(
                        label = "播放音效",
                        selected = editor.actionType == KeyShortcutActionType.PlaySound,
                        onClick = { onEditorChanged(editor.copy(actionType = KeyShortcutActionType.PlaySound)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "实时喊话",
                        selected = editor.actionType == KeyShortcutActionType.RealtimeHailing,
                        onClick = { onEditorChanged(editor.copy(actionType = KeyShortcutActionType.RealtimeHailing)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "智能联动",
                        selected = editor.actionType == KeyShortcutActionType.Automation,
                        onClick = { onEditorChanged(editor.copy(actionType = KeyShortcutActionType.Automation)) },
                    )
                }

                Text("触发", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyShortcutChoiceChip(
                        label = "按一下",
                        selected = editor.pressType == KeyShortcutPressType.Short,
                        onClick = { onEditorChanged(editor.copy(pressType = KeyShortcutPressType.Short)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "按两下",
                        selected = editor.pressType == KeyShortcutPressType.Double,
                        onClick = { onEditorChanged(editor.copy(pressType = KeyShortcutPressType.Double)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "长按",
                        selected = editor.pressType == KeyShortcutPressType.Long,
                        onClick = { onEditorChanged(editor.copy(pressType = KeyShortcutPressType.Long)) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("按键", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(selectedKey, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = onLearnKey,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("识别", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                if (editor.actionType == KeyShortcutActionType.PlaySound) {
                    Text("音效", color = TextSecondary, fontSize = 12.sp)
                    if (sounds.isEmpty()) {
                        Text("本地暂无音效", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sounds, key = { it.file.absolutePath }) { sound ->
                                KeyShortcutChoiceChip(
                                    label = sound.name,
                                    selected = editor.soundPath == sound.file.absolutePath,
                                    onClick = {
                                        onEditorChanged(
                                            editor.copy(
                                                soundPath = sound.file.absolutePath,
                                                soundName = sound.name,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (editor.actionType == KeyShortcutActionType.Automation) {
                    Text("联动按钮", color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { slot ->
                            KeyShortcutChoiceChip(
                                label = slot.toString(),
                                selected = editor.automationButtonId == slot,
                                onClick = { onEditorChanged(editor.copy(automationButtonId = slot)) },
                            )
                        }
                    }
                }

                error?.let {
                    Text(it, color = Color(0xFFE85D5D), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        editor.keyCode <= 0 -> "请先识别按键"
                        editor.actionType == KeyShortcutActionType.PlaySound && editor.soundPath.isBlank() -> "请选择一个音效"
                        else -> null
                    }
                    if (error == null) onSave(editor)
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun KeyShortcutChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryColor,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}

/** App label for the cluster picker row; falls back to the package name when not installed/resolvable. */
private fun resolveAppLabel(context: Context, pkg: String): String =
    try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

/** Human label for a steering-wheel keycode: known name, else "button (code N)". */
@Composable
private fun steeringButtonLabel(keyCode: Int): String {
    val res = com.byd.wjx.cluster.knownButtonNameRes(keyCode)
    return if (res != 0) stringResource(res)
    else stringResource(R.string.steering_button_unknown, keyCode)
}

private sealed interface LearnUiState {
    data object Waiting : LearnUiState
    data class Rejected(val keyCode: Int) : LearnUiState
    data class Captured(val keyCode: Int) : LearnUiState
    data object TimedOut : LearnUiState
}

/**
 * Learn-the-button dialog. Puts SteeringWheelKeyService into learn mode while open and collects the
 * captured key from its StateFlow (same process). States: Waiting → (Rejected loops) → Captured
 * (confirm) / TimedOut. learnMode is always cleared on dispose.
 */
@Composable
private fun LearnButtonDialog(
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    preferLongPress: Boolean = false,
) {
    var state by remember { mutableStateOf<LearnUiState>(LearnUiState.Waiting) }

    // Clear learn mode AND the captured value on close, so a later reopen can't latch a stale capture.
    DisposableEffect(Unit) {
        onDispose {
            SteeringWheelKeyService.learnMode = false
            SteeringWheelKeyService.learnPreferLongPress = false
            SteeringWheelKeyService.capturedKey.value = null
        }
    }

    // Arm capture, then react ONLY to fresh emissions. The StateFlow replays its current value to a
    // new collector, so we reset it to null first and filterNotNull() drops both that reset and any
    // stale value left from a previous session — reopening therefore always starts at Waiting.
    LaunchedEffect(Unit) {
        SteeringWheelKeyService.capturedKey.value = null
        SteeringWheelKeyService.learnPreferLongPress = preferLongPress
        SteeringWheelKeyService.learnMode = true
        SteeringWheelKeyService.capturedKey
            .filterNotNull()
            .collect { r ->
                state = if (r.assignable) {
                    SteeringWheelKeyService.learnMode = false
                    LearnUiState.Captured(r.keyCode)
                } else {
                    LearnUiState.Rejected(r.keyCode)
                }
            }
    }

    // Timeout while still waiting/rejected (no assignable capture yet).
    LaunchedEffect(state) {
        if (state is LearnUiState.Waiting || state is LearnUiState.Rejected) {
            delay(10_000)
            if (state is LearnUiState.Waiting || state is LearnUiState.Rejected) {
                SteeringWheelKeyService.learnMode = false
                state = LearnUiState.TimedOut
            }
        }
    }

    // "Again": re-arm. The collector above keeps running (keyed on Unit), so just reset + Waiting.
    val restart = {
        SteeringWheelKeyService.capturedKey.value = null
        SteeringWheelKeyService.learnPreferLongPress = preferLongPress
        SteeringWheelKeyService.learnMode = true
        state = LearnUiState.Waiting
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learn_button_dialog_title)) },
        text = {
            when (val s = state) {
                is LearnUiState.Waiting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentGreen)
                    Text(stringResource(R.string.learn_button_waiting))
                }
                is LearnUiState.Rejected -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.learn_button_rejected))
                    Text(steeringButtonLabel(s.keyCode), color = TextSecondary, fontSize = 12.sp)
                }
                is LearnUiState.Captured -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.learn_button_captured))
                    Text(
                        "${steeringButtonLabel(s.keyCode)} (${s.keyCode})",
                        color = TextPrimary, fontWeight = FontWeight.Medium,
                    )
                }
                is LearnUiState.TimedOut -> Text(stringResource(R.string.learn_button_timeout))
            }
        },
        confirmButton = {
            when (val s = state) {
                is LearnUiState.Captured -> TextButton(onClick = { onSave(s.keyCode) }) {
                    Text(stringResource(R.string.learn_button_save))
                }
                is LearnUiState.TimedOut -> TextButton(onClick = restart) {
                    Text(stringResource(R.string.learn_button_again))
                }
                else -> {}
            }
        },
        dismissButton = {
            when (state) {
                is LearnUiState.Captured -> TextButton(onClick = restart) {
                    Text(stringResource(R.string.learn_button_again))
                }
                else -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.learn_button_cancel))
                }
            }
        },
    )
}

@Composable
private fun ClusterSizeSlider(
    label: String,
    pct: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit,
    min: Int = MIN_PROJECTION_PCT,
    max: Int = MAX_PROJECTION_PCT,
    description: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
            Text(
                "$pct%",
                color = if (enabled) AccentGreen else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (description != null) {
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Slider(
            value = pct.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / 2 - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
            ),
        )
    }
}

@Composable
private fun VehicleEventSoundSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importVehicleSound(uri)
    }
    LaunchedEffect(Unit) {
        viewModel.reloadLockSoundChoices()
    }
    SectionHeader(text = "车辆事件音效")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { importLauncher.launch(arrayOf("audio/*")) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = AccentContent),
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入车辆音效", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            LockSoundEventRow(
                event = LockSoundEvent.Lock,
                subtitle = "车辆锁止并伴随闪灯确认后播放",
                state = state,
                onEnabledChanged = { viewModel.setLockSoundEnabled(LockSoundEvent.Lock, it) },
                onSoundSelected = { viewModel.setLockSoundPath(LockSoundEvent.Lock, it.path) },
                onSoundDeleted = viewModel::deleteVehicleSound,
            )
            LockSoundEventRow(
                event = LockSoundEvent.Unlock,
                subtitle = "车辆解锁并伴随闪灯确认后播放",
                state = state,
                onEnabledChanged = { viewModel.setLockSoundEnabled(LockSoundEvent.Unlock, it) },
                onSoundSelected = { viewModel.setLockSoundPath(LockSoundEvent.Unlock, it.path) },
                onSoundDeleted = viewModel::deleteVehicleSound,
            )
            LockSoundEventRow(
                event = LockSoundEvent.Sentry,
                subtitle = "哨兵报警上报后播放",
                state = state,
                onEnabledChanged = { viewModel.setLockSoundEnabled(LockSoundEvent.Sentry, it) },
                onSoundSelected = { viewModel.setLockSoundPath(LockSoundEvent.Sentry, it.path) },
                onSoundDeleted = viewModel::deleteVehicleSound,
            )
            AutoParkingSoundEventRow(
                state = state,
                onEnabledChanged = viewModel::setAutoParkingSoundEnabled,
                onSoundSelected = { viewModel.setAutoParkingSoundPath(it.path) },
                onSoundDeleted = viewModel::deleteVehicleSound,
            )
            state.lockSoundStatus?.let {
                Text(
                    text = if (state.lockSoundSyncing) "同步中…" else it,
                    color = if (it.contains("不可用") || it.contains("暂无") || it.contains("不存在")) SocRed else AccentGreen,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun AutoParkingSoundEventRow(
    state: SettingsUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onSoundSelected: (LockSoundChoice) -> Unit,
    onSoundDeleted: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val selectedPath = state.autoParkingSoundPath
    val selected = state.lockSoundChoices.firstOrNull { it.path == selectedPath }
    val premiumAllowed = state.activation.activated || state.activation.trialActive

    if (picking) {
        LockSoundPickerDialog(
            title = "自动泊车音效",
            choices = state.lockSoundChoices,
            selectedPath = selectedPath,
            onDismiss = { picking = false },
            onSelect = {
                picking = false
                onSoundSelected(it)
            },
            onDelete = onSoundDeleted,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .clickable(enabled = premiumAllowed) { picking = true }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("自动泊车音效", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("泊车执行中循环车外播放，泊车结束后停止", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            Text(
                if (!premiumAllowed) "需激活后使用"
                else selected?.let { "已选择：${it.name}" } ?: if (selectedPath.isBlank()) "未选择音效" else "文件不存在：${File(selectedPath).name}",
                color = if (!premiumAllowed) SocRed else if (selected != null) AccentBlue else TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { picking = true }, enabled = premiumAllowed) {
            Text("选择", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Switch(
            checked = state.autoParkingSoundEnabled,
            onCheckedChange = onEnabledChanged,
            enabled = premiumAllowed,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun LockSoundEventRow(
    event: LockSoundEvent,
    subtitle: String,
    state: SettingsUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onSoundSelected: (LockSoundChoice) -> Unit,
    onSoundDeleted: (String) -> Unit,
) {
    var picking by remember(event) { mutableStateOf(false) }
    val selectedPath = state.lockSound.path(event)
    val selected = state.lockSoundChoices.firstOrNull { it.path == selectedPath }
    val enabled = state.lockSound.enabled(event)
    val premiumAllowed = state.activation.activated || state.activation.trialActive

    if (picking) {
        LockSoundPickerDialog(
            title = event.title,
            choices = state.lockSoundChoices,
            selectedPath = selectedPath,
            onDismiss = { picking = false },
            onSelect = {
                picking = false
                onSoundSelected(it)
            },
            onDelete = onSoundDeleted,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .clickable(enabled = premiumAllowed) { picking = true }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(event.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            Text(
                if (!premiumAllowed) "需激活后使用"
                else selected?.let { "已选择：${it.name}" } ?: if (selectedPath.isBlank()) "未选择音效" else "文件不存在：${File(selectedPath).name}",
                color = if (!premiumAllowed) SocRed else if (selected != null) AccentBlue else TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { picking = true }, enabled = premiumAllowed) {
            Text("选择", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            enabled = premiumAllowed,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun LockSoundPickerDialog(
    title: String,
    choices: List<LockSoundChoice>,
    selectedPath: String,
    onDismiss: () -> Unit,
    onSelect: (LockSoundChoice) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LockSoundChoice?>(null) }

    pendingDelete?.let { choice ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除车辆音效", color = TextPrimary) },
            text = {
                Text(
                    "确定永久删除“${choice.name}”吗？如果解锁、锁车、哨兵或自动泊车正在使用它，对应功能会自动关闭。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(choice.path)
                    },
                ) {
                    Text("删除", color = SocRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardSurfaceElevated,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            if (choices.isEmpty()) {
                Text(
                    "车辆音效为空。请从在线音效库下载到“车辆音效”，或在车辆事件音效页面直接导入。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(choices, key = { it.path }) { choice ->
                        val selected = choice.path == selectedPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) AccentGreen.copy(alpha = 0.14f) else CardSurface,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onSelect(choice) }
                                .padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(choice.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(choice.sizeLabel, color = TextMuted, fontSize = 11.sp)
                            }
                            if (selected) {
                                Text("已选", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { pendingDelete = choice }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除车辆音效",
                                    tint = SocRed,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        },
        containerColor = CardSurfaceElevated,
    )
}

@Composable
private fun AppSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    // SAF picker for restore — must be declared at composable top level
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreConfig(uri)
    }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    // Confirm dialog for destructive restore operation
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = {
                Text(
                    stringResource(R.string.settings_config_restore_confirm_title),
                    color = TextPrimary,
                )
            },
            text = {
                Text(
                    stringResource(R.string.settings_config_restore_confirm_body),
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restoreLauncher.launch(arrayOf("application/zip"))
                }) {
                    Text(
                        stringResource(R.string.settings_config_restore_confirm_ok),
                        color = SocRed,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(
                        stringResource(R.string.settings_config_restore_confirm_cancel),
                        color = TextSecondary,
                    )
                }
            },
            containerColor = CardSurfaceElevated,
        )
    }

    SectionHeader(text = stringResource(R.string.settings_app_data_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_record_trip_gps_label),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_record_trip_gps_description),
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = state.tripGpsRecordingEnabled,
                    onCheckedChange = { viewModel.setTripGpsRecordingEnabled(it) },
                    colors = bydSwitchColors(),
                )
            }

            HorizontalDivider(color = CardBorder)
            val errorPrefix = stringResource(R.string.settings_error_prefix)

            // Export full config backup (DB + prefs) as a zip to Downloads
            Button(
                onClick = { viewModel.exportConfig() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = AccentContent)
            ) {
                Text(
                    stringResource(R.string.settings_config_export_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Restore config backup: confirm dialog then SAF zip picker
            Button(
                onClick = { showRestoreConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = AccentContent)
            ) {
                Text(
                    stringResource(R.string.settings_config_restore_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (state.configStatus != null) {
                Text(
                    state.configStatus!!,
                    color = if (state.configStatus!!.startsWith(errorPrefix)) SocRed else PrimaryColor,
                    fontSize = 12.sp,
                )
            }
        }
    }

    SectionHeader(text = stringResource(R.string.settings_about_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        val context = LocalContext.current
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "超级玩具箱 ${state.appVersion}",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(stringResource(R.string.settings_copyright), color = TextSecondary, fontSize = 14.sp)
            if (state.lastBootInfo != null) {
                Text(
                    stringResource(R.string.settings_autostart_recorded, state.lastBootInfo!!),
                    color = AccentGreen,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    stringResource(R.string.settings_autostart_not_recorded),
                    color = SocRed,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "超级玩具箱独立版",
                color = AccentBlue,
                fontSize = 14.sp,
                textDecoration = TextDecoration.None,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.settings_update_check_toggle_label), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_update_check_toggle_description),
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.autoCheckUpdates,
                    onCheckedChange = { viewModel.setAutoCheckUpdates(it) },
                    colors = bydSwitchColors(),
                )
            }

            Button(
                onClick = { viewModel.showUpdateDialog() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = AccentContent)
            ) {
                Text(stringResource(R.string.settings_update_check_button), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = AccentGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = PrimaryColor,
            unfocusedLabelColor = TextSecondary,
            cursorColor = PrimaryColor
        )
    )
}

@Composable
private fun UnitChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryColor,
            selectedLabelColor = Color.White,
            containerColor = CardSurfaceElevated,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected
        )
    )
}
