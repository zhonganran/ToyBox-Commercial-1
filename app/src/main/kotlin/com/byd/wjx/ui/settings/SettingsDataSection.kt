package com.byd.wjx.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentPurple
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SettingsDataSection(
    preferences: SettingsAppPreferencesUiState,
    configStatus: String?,
    onTripGpsRecordingChange: (Boolean) -> Unit,
    onExportConfig: () -> Unit,
    onRestoreConfig: (Uri) -> Unit,
) {
    val onTripGpsToggle = rememberTripGpsToggleAction(onTripGpsRecordingChange)
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onRestoreConfig)
    }
    var showExportConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    if (showExportConfirm) {
        ExportConfigConfirmDialog(
            onDismiss = { showExportConfirm = false },
            onConfirm = {
                showExportConfirm = false
                onExportConfig()
            },
        )
    }

    if (showRestoreConfirm) {
        RestoreConfigConfirmDialog(
            onDismiss = { showRestoreConfirm = false },
            onConfirm = {
                showRestoreConfirm = false
                restoreLauncher.launch(arrayOf("application/zip"))
            },
        )
    }

    SectionHeader(text = stringResource(R.string.settings_app_data_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    checked = preferences.tripGpsRecordingEnabled,
                    onCheckedChange = onTripGpsToggle,
                    colors = bydSwitchColors(),
                )
            }

            HorizontalDivider(color = CardBorder)
            val errorPrefix = stringResource(R.string.settings_error_prefix)
            Button(
                onClick = { showExportConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = AccentContent,
                ),
            ) {
                Text(
                    stringResource(R.string.settings_config_export_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Button(
                onClick = { showRestoreConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    contentColor = AccentContent,
                ),
            ) {
                Text(
                    stringResource(R.string.settings_config_restore_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            configStatus?.let { status ->
                Text(
                    status,
                    color = if (status.startsWith(errorPrefix)) SocRed else AccentGreen,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
