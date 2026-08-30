package com.byd.wjx.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.adbrescue.AdbRescueReport
import com.byd.wjx.adbrescue.AdbRescueValueResult
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.ui.components.bydSwitchColors

@Composable
internal fun AdbRescueSettingsSection(
    viewModel: AdbRescueSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    var showDisableConfirmation by remember { mutableStateOf(false) }

    if (showDisableConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisableConfirmation = false },
            title = { Text("关闭两种 ADB 模式", color = TextPrimary) },
            text = {
                Text(
                    "这会关闭标准 Android ADB、BYD 无线 ADB 和开机自动守护。关闭后可能无法再通过网络 ADB 连接车机。",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisableConfirmation = false
                        viewModel.disableBoth()
                    },
                ) {
                    Text("确认关闭", color = SocRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirmation = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardSurface,
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopRescue("screen_closed") }
    }
    DisposableEffect(state.rescueRunning, activity) {
        if (state.rescueRunning) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (state.rescueRunning) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AdbRescueOverviewCard(state.report, state.guardEnabled)

        AdbGuardToggleCard(
            enabled = state.guardEnabled,
            controlsEnabled = !state.busy && !state.rescueRunning,
            onEnabledChange = viewModel::setGuardEnabled,
        )

        Button(
            onClick = viewModel::enableBoth,
            enabled = !state.busy && !state.rescueRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        ) {
            Icon(Icons.Outlined.DeveloperMode, contentDescription = null, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Text("授权、开启并启用自动守护", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = { showDisableConfirmation = true },
            enabled = !state.busy && !state.rescueRunning && state.report?.anyModeEnabled == true,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, SocRed.copy(alpha = 0.72f)),
        ) {
            Icon(Icons.Outlined.StopCircle, contentDescription = null, tint = SocRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("关闭两种 ADB 模式", color = SocRed, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::refresh,
                enabled = !state.busy && !state.rescueRunning,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
                Text("刷新状态", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    if (state.rescueRunning) viewModel.stopRescue() else viewModel.startRescue()
                },
                enabled = !state.busy,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (state.rescueRunning) SocRed.copy(alpha = 0.8f) else AccentOrange.copy(alpha = 0.8f),
                ),
            ) {
                Icon(
                    if (state.rescueRunning) Icons.Outlined.StopCircle else Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = if (state.rescueRunning) SocRed else AccentOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (state.rescueRunning) "停止救援" else "三分钟救援",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (state.rescueRunning) {
            AdbRescueProgressCard(
                attempt = state.rescueAttempt,
                remainingSeconds = state.rescueRemainingSeconds,
                diagnosticsWritten = state.diagnosticProgressWritten,
            )
        }

        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.report?.allModesEnabled == true) AccentGreen else AccentOrange,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }

        AdbRescueDetailsCard(state.report)

        Text(
            "ADB 会开放系统调试入口。该守护仅恢复标准 ADB 与 BYD 无线 ADB 的三个官方状态值，" +
                "不会修改系统 APK、Wi-Fi 国家码或车辆控制组件。",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun AdbRescueOverviewCard(report: AdbRescueReport?, guardEnabled: Boolean) {
    val allEnabled = report?.allModesEnabled == true
    val permission = report?.writeSecureSettingsGranted == true
    val guardHealthy = allEnabled && permission && guardEnabled
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, if (allEnabled) AccentGreen.copy(alpha = 0.6f) else CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = if (allEnabled) AccentGreen else AccentOrange,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ADB 救援状态", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        when {
                            report == null -> "尚未读取 ADB 状态"
                            guardHealthy -> "两种 ADB 模式与自动守护均正常"
                            allEnabled && guardEnabled -> "两种 ADB 模式已开启，但恢复权限缺失"
                            allEnabled -> "两种 ADB 模式已开启，自动守护已关闭"
                            else -> "存在未开启或无法回读的状态值"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                StatusPill(
                    when {
                        report == null -> "待检查"
                        guardHealthy -> "正常"
                        allEnabled && guardEnabled -> "权限缺失"
                        allEnabled -> "未守护"
                        else -> "需修复"
                    },
                    guardHealthy,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewMetric("WRITE_SECURE_SETTINGS", if (permission) "已授权" else "未授权", permission, Modifier.weight(1f))
                OverviewMetric("Android 用户", report?.userId?.toString() ?: "--", report != null, Modifier.weight(1f))
                OverviewMetric("应用 UID", report?.uid?.toString() ?: "--", report != null, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun AdbGuardToggleCard(
    enabled: Boolean,
    controlsEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("开机自动守护", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                if (enabled) {
                    "开机、应用更新和 ACC 上电后自动检查并恢复 ADB"
                } else {
                    "当前不会在后台自动开启 ADB"
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            enabled = controlsEnabled,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    healthy: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = if (healthy) AccentGreen else AccentOrange,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AdbRescueDetailsCard(report: AdbRescueReport?) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("写入回读诊断", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "只有回读值达到目标才会显示为已开启。",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(3.dp))
            ModeStatusRow("标准 Android ADB", report?.standardAdb)
            ModeStatusRow("BYD 无线 ADB 持久开关", report?.bydWirelessEnabled)
            ModeStatusRow("BYD 无线 ADB 连接开关", report?.bydWirelessConnection)
            if (report != null) {
                Text(
                    "权限写入测试：${if (report.settingsWriteTestSucceeded) "通过" else "未通过或未执行"}  ·  " +
                        "触发：${report.trigger}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ModeStatusRow(title: String, result: AdbRescueValueResult?) {
    val healthy = result?.enabled == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated.copy(alpha = 0.76f), RoundedCornerShape(8.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                result?.key ?: "等待读取",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result != null) {
                val writeState = when (result.writeAccepted) {
                    true -> "写入调用已接受"
                    false -> "写入调用未接受"
                    null -> "本次无需写入"
                }
                Text(
                    "${result.before}  →  ${result.after}  ·  $writeState",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        StatusPill(if (healthy) "已开启" else "未开启", healthy)
    }
}

@Composable
private fun StatusPill(text: String, healthy: Boolean) {
    val color = if (healthy) AccentGreen else SocRed
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun AdbRescueProgressCard(
    attempt: Int,
    remainingSeconds: Int,
    diagnosticsWritten: Boolean?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ADB 救援运行中", color = AccentOrange, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("$attempt / 180", color = TextPrimary, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = { (attempt / 180f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = AccentOrange,
            trackColor = CardSurfaceElevated,
        )
        Text(
            "剩余 ${remainingSeconds} 秒  ·  诊断状态写入：${when (diagnosticsWritten) {
                true -> "正常"
                false -> "未授权"
                null -> "等待首次执行"
            }}",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
