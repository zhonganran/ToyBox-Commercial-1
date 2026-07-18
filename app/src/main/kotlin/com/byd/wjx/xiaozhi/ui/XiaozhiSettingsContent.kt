package com.byd.wjx.xiaozhi.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.xiaozhi.domain.XiaozhiProvisioningState

@Composable
fun XiaozhiSettingsContent(
    viewModel: XiaozhiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wakeState by viewModel.wakeState.collectAsStateWithLifecycle()
    val config = state.config
    val context = LocalContext.current
    var websocketUrl by remember { mutableStateOf(config.websocketUrl) }
    var token by remember { mutableStateOf(config.token) }
    var otaUrl by remember { mutableStateOf(config.otaUrl) }
    var wakePhrase by remember { mutableStateOf(wakeState.config.phrase) }
    var wakeMessage by remember { mutableStateOf<String?>(null) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        wakeMessage = if (granted) {
            viewModel.updateWakeWord(enabled = true, phrase = wakePhrase)
        } else {
            "没有麦克风权限，无法后台监听唤醒词"
        }
    }
    LaunchedEffect(config.websocketUrl, config.token, config.otaUrl) {
        websocketUrl = config.websocketUrl
        token = config.token
        otaUrl = config.otaUrl
    }
    LaunchedEffect(wakeState.config.phrase) {
        wakePhrase = wakeState.config.phrase
    }
    LaunchedEffect(Unit) {
        viewModel.reconcileWakeService()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.90f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = AccentGreen)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台语音唤醒", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("直接喊唤醒名字即可；监听音频只在车机本地处理", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = wakeState.config.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                wakeMessage = viewModel.updateWakeWord(enabled, wakePhrase)
                            }
                        },
                    )
                }
                OutlinedTextField(
                    value = wakePhrase,
                    onValueChange = { wakePhrase = it.take(6) },
                    label = { Text("唤醒名字") },
                    placeholder = { Text("默认：小智（可自定义）") },
                    supportingText = { Text("支持 2～6 个中文汉字；检测到完整唤醒词后自动进入连续对话") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = xiaozhiFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { wakeMessage = viewModel.updateWakeWord(wakeState.config.enabled, wakePhrase) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.weight(0.04f))
                        Text("保存唤醒词", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (wakeState.listening) "监听中" else if (wakeState.serviceRunning) "已待命" else "未运行",
                        color = if (wakeState.listening) AccentGreen else AccentOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    wakeMessage ?: wakeState.statusText,
                    color = if (wakeMessage != null || !wakeState.engineAvailable) SocRed else TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Text(
                    if (wakeState.engineAvailable) {
                        "当前引擎：${wakeState.engineLabel}。唤醒词完全在车机本地检测，不上传监听音频；对话或实时喊话使用麦克风时会自动让行，结束后恢复。"
                    } else {
                        "当前引擎：${wakeState.engineLabel}。本地喊名字唤醒不可用，页面内的小智对话仍可使用。"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.90f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("小智官方服务", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "自动申请设备配置。首次使用如果出现激活码，请前往小智官网登录并绑定；本页会自动轮询激活结果。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                OutlinedTextField(
                    value = otaUrl,
                    onValueChange = { otaUrl = it },
                    label = { Text("官方 OTA / 激活地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = xiaozhiFieldColors(),
                )
                Button(
                    onClick = { viewModel.provisionOfficial(otaUrl) },
                    enabled = state.provisioning !is XiaozhiProvisioningState.Checking,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                ) {
                    Icon(Icons.Outlined.CloudSync, contentDescription = null)
                    Spacer(Modifier.weight(0.04f))
                    Text(
                        if (state.provisioning is XiaozhiProvisioningState.Checking) "正在获取配置" else "获取官方配置",
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    onClick = { openXiaozhiWebConsole(context) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Icon(Icons.Outlined.SmartToy, contentDescription = null)
                    Spacer(Modifier.weight(0.04f))
                    Text("网页登录小智官网", fontWeight = FontWeight.Bold)
                }
                ProvisioningResult(state.provisioning, context) { openXiaozhiWebConsole(context) }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.90f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("服务器配置", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "官方配置会自动填入。使用自建服务时，也可以直接填写 WebSocket 地址和 Token。Token 只保存在当前车机。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                OutlinedTextField(
                    value = websocketUrl,
                    onValueChange = { websocketUrl = it },
                    label = { Text("WebSocket 地址") },
                    placeholder = { Text("wss://...（ws:// 仅支持本机）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = xiaozhiFieldColors(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("访问 Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = xiaozhiFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.saveConfiguration(websocketUrl, token, otaUrl) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.weight(0.04f))
                        Text("保存配置", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = viewModel::clearConfiguration, modifier = Modifier.weight(0.72f)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.weight(0.04f))
                        Text("清除")
                    }
                }
                HorizontalDivider(color = CardBorder)
                ConfigIdentityRow("设备 ID", config.deviceId)
                ConfigIdentityRow("客户端 ID", config.clientId)
                Text(
                    if (config.isConfigured) "当前状态：已配置，可进入“小智对话”页面测试" else "当前状态：尚未配置",
                    color = if (config.isConfigured) AccentGreen else AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ProvisioningResult(
    state: XiaozhiProvisioningState,
    context: Context,
    onOpenConsole: () -> Unit,
) {
    when (state) {
        XiaozhiProvisioningState.Idle -> Unit
        XiaozhiProvisioningState.Checking -> Text("正在向小智官方服务申请设备配置……", color = AccentOrange, fontSize = 13.sp)
        is XiaozhiProvisioningState.Ready -> Text(state.message, color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        is XiaozhiProvisioningState.Failed -> Text(state.message, color = SocRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        is XiaozhiProvisioningState.ActivationRequired -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .border(1.dp, AccentOrange.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("设备激活码", color = AccentOrange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(state.code, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Text(state.message, color = TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("小智激活码", state.code))
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("复制激活码")
                    }
                    OutlinedButton(
                        onClick = onOpenConsole,
                    ) {
                        Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("网页绑定")
                    }
                }
            }
        }
    }
}

private fun openXiaozhiWebConsole(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(XIAOZHI_WEB_CONSOLE_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "未找到可打开小智官网的浏览器", Toast.LENGTH_LONG).show()
        }
}

private const val XIAOZHI_WEB_CONSOLE_URL = "https://xiaozhi.me/login?redirect=/console/"

@Composable
private fun ConfigIdentityRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(0.34f))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun xiaozhiFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor = AccentGreen,
    unfocusedLabelColor = TextSecondary,
    cursorColor = AccentGreen,
)
