package com.byd.wjx.ui.sound

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun SoundScreen(
    viewModel: SoundViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onToggleHailing = rememberRealtimeHailingToggleAction(
        isActive = state.realtimeHailingRunning || state.realtimeHailingBusy,
        onToggle = viewModel::toggleRealtimeHailing,
    )
    var pendingDelete by remember { mutableStateOf<SoundLibrary.SoundItem?>(null) }

    pendingDelete?.let { sound ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除音效", color = TextPrimary) },
            text = {
                Text(
                    "确定永久删除“${sound.name}”吗？删除后无法恢复。" +
                        "如果智能联动或按键快捷操作正在使用它，删除后相关动作将无法播放，请重新选择音效。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(sound)
                    },
                ) {
                    Text("删除", color = Color(0xFFCC3348), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardSurfaceElevated,
        )
    }

    if (state.audioTuningPanelVisible) {
        AudioTuningSidePanel(
            playbackVolumePercent = state.playbackVolumePercent,
            realtimeVolumePercent = state.realtimeVolumePercent,
            realtimeNoiseSuppressionPercent = state.realtimeNoiseSuppressionPercent,
            realtimeMicGainPercent = state.realtimeMicGainPercent,
            onDismiss = viewModel::toggleAudioTuningPanel,
            onPlaybackVolumeChanged = viewModel::setPlaybackVolumePercent,
            onRealtimeVolumeChanged = viewModel::setRealtimeVolumePercent,
            onNoiseSuppressionChanged = viewModel::setRealtimeNoiseSuppressionPercent,
            onReset = viewModel::resetAudioTuning,
        )
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2200L)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 18.dp),
    ) {
        SoundSceneHeader(
            hailingRunning = state.realtimeHailingRunning,
            hailingBusy = state.realtimeHailingBusy,
            onToggleHailing = onToggleHailing,
            onOpenTuning = viewModel::toggleAudioTuningPanel,
            onRefresh = viewModel::refresh,
        )
        Spacer(Modifier.height(10.dp))
        state.message?.let { message ->
            MessageBar(message = message)
            Spacer(Modifier.height(8.dp))
        }
        LocalSoundTab(
            state = state,
            onPlay = viewModel::play,
            onDelete = { pendingDelete = it },
            onMove = viewModel::moveSound,
            onRefresh = viewModel::refresh,
        )
    }
}
