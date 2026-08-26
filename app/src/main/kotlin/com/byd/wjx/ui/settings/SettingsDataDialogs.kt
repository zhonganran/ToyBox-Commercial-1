package com.byd.wjx.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.byd.wjx.R
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun ExportConfigConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_config_export_confirm_title), color = TextPrimary) },
        text = { Text(stringResource(R.string.settings_config_export_confirm_body), color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_config_export_confirm_ok), color = AccentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_config_export_confirm_cancel), color = TextSecondary)
            }
        },
        containerColor = CardSurfaceElevated,
    )
}

@Composable
internal fun RestoreConfigConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_config_restore_confirm_title), color = TextPrimary) },
        text = { Text(stringResource(R.string.settings_config_restore_confirm_body), color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_config_restore_confirm_ok), color = SocRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_config_restore_confirm_cancel), color = TextSecondary)
            }
        },
        containerColor = CardSurfaceElevated,
    )
}
