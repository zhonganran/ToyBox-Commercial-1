package com.byd.wjx.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.*

private val PrimaryColor = AccentGreen

@Composable
internal fun AppSection(state: SettingsUiState, viewModel: SettingsViewModel) {
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

    SectionHeader(text = stringResource(R.string.settings_appearance_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.settings_theme_title),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.settings_theme_description),
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip(
                    stringResource(R.string.settings_theme_light),
                    state.themeMode == "light",
                ) { viewModel.setThemeMode("light") }
                UnitChip(
                    stringResource(R.string.settings_theme_dark),
                    state.themeMode == "dark",
                ) { viewModel.setThemeMode("dark") }
                UnitChip(
                    stringResource(R.string.settings_theme_system),
                    state.themeMode == "system",
                ) { viewModel.setThemeMode("system") }
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.68f))

            Text(
                "背景壁纸",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "切换后会同步应用到首页、个性音效、桌面组件、智能联动和设置等全部页面。",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(AppWallpaper.entries, key = { it.storedValue }) { wallpaper ->
                    WallpaperOption(
                        wallpaper = wallpaper,
                        selected = state.appWallpaper == wallpaper.storedValue,
                        onClick = { viewModel.setAppWallpaper(wallpaper.storedValue) },
                    )
                }
            }
        }
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
                "超级玩具箱 V${state.appVersion}",
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
private fun WallpaperOption(
    wallpaper: AppWallpaper,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AccentGreen else CardBorder,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            Image(
                painter = painterResource(wallpaper.thumbnailRes),
                contentDescription = wallpaper.displayName,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "当前壁纸",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                        .padding(2.dp),
                )
            }
        }
        Text(
            wallpaper.displayName,
            color = if (selected) AccentGreen else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            wallpaper.description,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}
