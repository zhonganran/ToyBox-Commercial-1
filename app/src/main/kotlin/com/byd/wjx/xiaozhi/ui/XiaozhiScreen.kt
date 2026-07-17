package com.byd.wjx.xiaozhi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VoiceChat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.xiaozhi.domain.XiaozhiChatMessage
import com.byd.wjx.xiaozhi.domain.XiaozhiMessageRole
import com.byd.wjx.xiaozhi.domain.XiaozhiPhase
import com.byd.wjx.xiaozhi.domain.XiaozhiUiState

@Composable
fun XiaozhiScreen(
    viewModel: XiaozhiViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wakeState by viewModel.wakeState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startConversationAfterMicrophonePermission()
    }
    val startOrToggle = {
        if (!state.config.isConfigured) {
            onOpenSettings()
        } else if (
            state.phase in setOf(XiaozhiPhase.Idle, XiaozhiPhase.Error) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.toggleAutomaticConversation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 54.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProPageHeader(
            eyebrow = "XIAOZHI VOICE",
            title = "小智对话",
            subtitle = if (wakeState.config.enabled) {
                "说“${wakeState.config.phrase}”唤醒 · 云端理解 · 车内语音回答"
            } else {
                "按一下自动对话 · 云端理解 · 车内语音回答"
            },
            trailing = {
                StatusPill(state)
            },
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 900.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConversationPanel(state, wakeState.config.enabled, wakeState.config.phrase, Modifier.weight(1f))
                    ControlPanel(
                        state,
                        wakeState.config.enabled,
                        wakeState.config.phrase,
                        startOrToggle,
                        viewModel,
                        onOpenSettings,
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ConversationPanel(
                        state,
                        wakeState.config.enabled,
                        wakeState.config.phrase,
                        Modifier.weight(1.65f).fillMaxHeight(),
                    )
                    ControlPanel(
                        state = state,
                        wakeEnabled = wakeState.config.enabled,
                        wakePhrase = wakeState.config.phrase,
                        onMainAction = startOrToggle,
                        viewModel = viewModel,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationPanel(
    state: XiaozhiUiState,
    wakeEnabled: Boolean,
    wakePhrase: String,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.90f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VoiceChat, contentDescription = null, tint = SceneMint, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(9.dp))
                Text("对话记录", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("仅保留本次运行记录", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, tint = SceneMint, modifier = Modifier.size(54.dp))
                        Text("按一下开始，直接和小智说话", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (wakeEnabled) "也可以直接说“$wakePhrase”开始对话" else "可以在设置中开启后台语音唤醒",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = XiaozhiChatMessage::id) { message ->
                        MessageBubble(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: XiaozhiChatMessage) {
    val user = message.role == XiaozhiMessageRole.User
    val system = message.role == XiaozhiMessageRole.System
    val accent = when {
        user -> AccentBlue
        system -> AccentOrange
        else -> SceneMint
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                when (message.role) {
                    XiaozhiMessageRole.User -> "我"
                    XiaozhiMessageRole.Assistant -> "小智"
                    XiaozhiMessageRole.System -> "系统"
                },
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(message.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun ControlPanel(
    state: XiaozhiUiState,
    wakeEnabled: Boolean,
    wakePhrase: String,
    onMainAction: () -> Unit,
    viewModel: XiaozhiViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val accent = phaseColor(state.phase)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(accent.copy(alpha = 0.13f), CircleShape)
                    .border(2.dp, accent.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.phase in setOf(XiaozhiPhase.Listening, XiaozhiPhase.Speaking, XiaozhiPhase.Thinking)) {
                        Icons.Outlined.StopCircle
                    } else {
                        Icons.Outlined.Mic
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = state.statusText,
                color = TextPrimary,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = phaseHint(state, wakeEnabled, wakePhrase),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onMainAction,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            ) {
                Icon(
                    if (state.phase in setOf(XiaozhiPhase.Listening, XiaozhiPhase.Thinking, XiaozhiPhase.Speaking, XiaozhiPhase.Connecting)) {
                        Icons.Outlined.StopCircle
                    } else Icons.Outlined.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(mainButtonLabel(state), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("小智设置")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::clearMessages, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("清空")
                }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = state.connected,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("断开")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: XiaozhiUiState) {
    val color = if (state.connected) AccentGreen else if (state.config.isConfigured) AccentOrange else SocRed
    Row(
        modifier = Modifier
            .background(CardSurfaceElevated.copy(alpha = 0.86f), RoundedCornerShape(999.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            when {
                state.connected -> "服务器在线"
                state.config.isConfigured -> "等待连接"
                else -> "尚未配置"
            },
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun mainButtonLabel(state: XiaozhiUiState): String = when (state.phase) {
    XiaozhiPhase.Connecting -> "取消连接"
    XiaozhiPhase.Listening -> "停止对话"
    XiaozhiPhase.Thinking, XiaozhiPhase.Speaking -> "停止回答"
    XiaozhiPhase.NotConfigured -> "前往设置"
    else -> "开始自动对话"
}

private fun phaseHint(state: XiaozhiUiState, wakeEnabled: Boolean, wakePhrase: String): String = when (state.phase) {
    XiaozhiPhase.NotConfigured -> "先获取小智官方配置，或填写自己的服务地址"
    XiaozhiPhase.Listening -> "说完自动识别；回答后可继续说，15秒无语音自动关闭"
    XiaozhiPhase.Thinking -> "语音已经发送，可以随时点击停止"
    XiaozhiPhase.Speaking -> "回答通过车内语音通道播放"
    XiaozhiPhase.Error -> state.error ?: "请检查网络和服务器配置"
    else -> if (wakeEnabled) "后台等待“$wakePhrase”，唤醒后自动开始对话" else "空闲时不占用麦克风和 Opus 编解码"
}

private fun phaseColor(phase: XiaozhiPhase): Color = when (phase) {
    XiaozhiPhase.Listening -> AccentGreen
    XiaozhiPhase.Thinking, XiaozhiPhase.Connecting -> AccentOrange
    XiaozhiPhase.Speaking -> AccentBlue
    XiaozhiPhase.Error, XiaozhiPhase.NotConfigured -> SocRed
    XiaozhiPhase.Idle -> SceneMint
}
