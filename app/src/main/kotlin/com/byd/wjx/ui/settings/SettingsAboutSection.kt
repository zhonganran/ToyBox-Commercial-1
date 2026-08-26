package com.byd.wjx.ui.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SettingsAboutSection(
    maintenance: SettingsMaintenanceUiState,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_about_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "超级玩具箱 V${maintenance.appVersion}",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(stringResource(R.string.settings_copyright), color = TextSecondary, fontSize = 14.sp)
            Text(
                maintenance.lastBootInfo?.let {
                    stringResource(R.string.settings_autostart_recorded, it)
                } ?: stringResource(R.string.settings_autostart_not_recorded),
                color = if (maintenance.lastBootInfo != null) AccentGreen else SocRed,
                fontSize = 12.sp,
            )
            Text("超级玩具箱独立版", color = AccentBlue, fontSize = 14.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_update_check_toggle_label),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_update_check_toggle_description),
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = maintenance.autoCheckUpdates,
                    onCheckedChange = onAutoCheckUpdatesChange,
                    colors = bydSwitchColors(),
                )
            }

            Button(
                onClick = onCheckForUpdates,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = AccentContent,
                ),
            ) {
                Text(
                    stringResource(R.string.settings_update_check_button),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
