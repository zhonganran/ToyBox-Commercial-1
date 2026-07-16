package com.byd.wjx.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.wjx.R
import com.byd.wjx.data.system.AutostartSettingsLaunchResult

@Composable
internal fun AutostartGuideDialog(
    launchResult: AutostartSettingsLaunchResult?,
    onOpenSettings: () -> Unit,
    onConfirmed: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                text = stringResource(R.string.autostart_guide_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.autostart_guide_message))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (launchResult == null) {
                                R.string.autostart_guide_open_settings
                            } else {
                                R.string.autostart_guide_open_settings_again
                            },
                        ),
                    )
                }
                when (launchResult) {
                    AutostartSettingsLaunchResult.DILINK_SETTINGS ->
                        Text(stringResource(R.string.autostart_guide_opened))
                    AutostartSettingsLaunchResult.APP_DETAILS_FALLBACK ->
                        Text(stringResource(R.string.autostart_guide_fallback))
                    AutostartSettingsLaunchResult.FAILED ->
                        Text(stringResource(R.string.autostart_guide_failed))
                    null -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmed) {
                Text(stringResource(R.string.autostart_guide_confirmed))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.autostart_guide_later))
            }
        },
    )
}
