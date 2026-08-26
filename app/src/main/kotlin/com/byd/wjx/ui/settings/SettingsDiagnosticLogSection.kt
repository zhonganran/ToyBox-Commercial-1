package com.byd.wjx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SettingsDiagnosticLogSection(
    maintenance: SettingsMaintenanceUiState,
    onUploadLogs: () -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_diagnostic_log_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.settings_diagnostic_log_title),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.settings_diagnostic_log_description),
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Button(
                onClick = onUploadLogs,
                enabled = !maintenance.isUploadingLogs,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = AccentContent,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    when {
                        maintenance.isUploadingLogs -> stringResource(R.string.settings_log_uploading_button)
                        maintenance.logUploadStatus?.startsWith("日志上传成功") == true ->
                            stringResource(R.string.settings_log_upload_complete_button)
                        else -> stringResource(R.string.settings_log_upload_button)
                    },
                    modifier = Modifier.padding(start = 7.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            maintenance.logUploadStatus?.let { status ->
                Text(
                    status,
                    color = if (status.startsWith("日志上传失败")) SocRed else AccentGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
