package com.byd.wjx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var showThirdPartyNotices by remember { mutableStateOf(false) }
    val thirdPartyNotices = remember {
        runCatching {
            context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
        }.getOrElse { "第三方许可文件读取失败：${it.message.orEmpty()}" }
    }

    if (showThirdPartyNotices) {
        AlertDialog(
            onDismissRequest = { showThirdPartyNotices = false },
            title = { Text("第三方开源许可", color = TextPrimary) },
            text = {
                Text(
                    thirdPartyNotices,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showThirdPartyNotices = false }) {
                    Text("关闭", color = AccentBlue)
                }
            },
            containerColor = CardSurfaceElevated,
        )
    }

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

            OutlinedButton(
                onClick = { showThirdPartyNotices = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Text(
                    "查看第三方开源许可",
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
}
