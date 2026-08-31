package com.byd.wjx.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.ui.help.UsageGuideScreen
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    adbRescueViewModel: AdbRescueSettingsViewModel = hiltViewModel(),
    initialCategory: String? = null,
    onOpenBlindSpot: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adbRescueState by adbRescueViewModel.uiState.collectAsStateWithLifecycle()
    val categories = SettingsCategory.entries
    var showUsageGuide by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(adbRescueViewModel) {
        adbRescueViewModel.refresh()
    }

    if (showUsageGuide) {
        BackHandler { showUsageGuide = false }
        UsageGuideScreen(onBack = { showUsageGuide = false })
        return
    }

    if (state.energy.showRecalcConfirm) {
        val energy = state.energy
        val tariffLabel = when (energy.tripCostTariff) {
            "home" -> energy.homeTariff
            "dc" -> energy.dcTariff
            else -> energy.tripCostTariff
        }
        AlertDialog(
            onDismissRequest = viewModel::hideRecalcConfirm,
            title = { Text(stringResource(R.string.settings_recalc_dialog_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.settings_recalc_dialog_text, tariffLabel, state.currencySymbol),
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRecalc) {
                    Text(stringResource(R.string.settings_recalc_confirm_button), color = AccentOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideRecalcConfirm) {
                    Text(stringResource(R.string.settings_cancel_button), color = TextSecondary)
                }
            },
            containerColor = CardSurface,
        )
    }

    if (state.maintenance.showUpdateDialog) {
        UpdateDialog(
            currentVersion = state.maintenance.appVersion,
            state = state.maintenance.updateDialogState,
            onCheck = {
                when (state.maintenance.updateDialogState) {
                    is UpdateState.Available -> viewModel.downloadUpdate()
                    is UpdateState.InstallPermissionRequired -> viewModel.continuePendingInstall()
                    else -> viewModel.checkForUpdate()
                }
            },
            onDismiss = viewModel::hideUpdateDialog,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 62.dp, vertical = 22.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "设置",
                    color = TextPrimary,
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "授权、车辆、控制和数据管理",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            OutlinedButton(
                onClick = { showUsageGuide = true },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.68f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text("使用说明", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "V${state.maintenance.appVersion}",
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
            var selectedCategory by rememberSaveable(initialCategory) {
                mutableStateOf(
                    categories.firstOrNull { it.name == initialCategory }
                        ?: SettingsCategory.Auth,
                )
            }
            val activeCategory = selectedCategory.takeIf { it in categories }
                ?: SettingsCategory.Auth
            LaunchedEffect(initialCategory) {
                val requested = categories.firstOrNull { it.name == initialCategory }
                if (requested != null) {
                    selectedCategory = requested
                } else if (selectedCategory !in categories) {
                    selectedCategory = SettingsCategory.Auth
                }
            }
            if (compact) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(categories, key = { it.name }) { category ->
                            SettingsCategoryHeader(
                                category = category,
                                state = state,
                                adbRescueState = adbRescueState,
                                selected = activeCategory == category,
                                onClick = {
                                    if (category == SettingsCategory.BlindSpot) {
                                        onOpenBlindSpot()
                                    } else {
                                        selectedCategory = category
                                    }
                                },
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
                            category = activeCategory,
                            state = state,
                            viewModel = viewModel,
                            adbRescueState = adbRescueState,
                            adbRescueViewModel = adbRescueViewModel,
                            onOpenBlindSpot = onOpenBlindSpot,
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
                        categories.forEach { category ->
                            SettingsCategoryHeader(
                                category = category,
                                state = state,
                                adbRescueState = adbRescueState,
                                selected = activeCategory == category,
                                onClick = {
                                    if (category == SettingsCategory.BlindSpot) {
                                        onOpenBlindSpot()
                                    } else {
                                        selectedCategory = category
                                    }
                                },
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
                            category = activeCategory,
                            state = state,
                            viewModel = viewModel,
                            adbRescueState = adbRescueState,
                            adbRescueViewModel = adbRescueViewModel,
                            onOpenBlindSpot = onOpenBlindSpot,
                        )
                    }
                }
            }
        }
    }
}
