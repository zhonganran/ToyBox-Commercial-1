package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.ui.theme.*


private val PrimaryColor = AccentGreen

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPlaces: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                    .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(999.dp))
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
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    subtitle,
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
            val count = LockSoundEvent.entries.count { state.lockSound.enabled(it) } +
                if (state.autoParkingSoundEnabled) 1 else 0
            if (count > 0) "${count} 项开启" else "未开启"
        }
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
                label = stringResource(R.string.settings_fuel_tank_capacity_label),
                value = state.fuelTankCapacity,
                onValueChange = { viewModel.saveFuelTankCapacity(it) },
                keyboardType = KeyboardType.Decimal
            )
            Text(
                stringResource(R.string.settings_fuel_tank_capacity_note),
                color = TextSecondary,
                fontSize = 12.sp,
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
internal fun SectionHeader(text: String) {
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
internal fun UnitChip(
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
