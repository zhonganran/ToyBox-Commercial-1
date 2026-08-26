package com.byd.wjx.ui.sound

import com.byd.wjx.audio.RealtimeHailingPhase
import com.byd.wjx.audio.RealtimeHailingState

internal fun SoundUiState.withRealtimeHailingState(
    state: RealtimeHailingState,
): SoundUiState = when (state.phase) {
    RealtimeHailingPhase.Starting -> copy(
        realtimeHailingRunning = false,
        realtimeHailingBusy = true,
        playingPath = null,
        message = state.message.ifBlank { "正在准备实时喊话" },
    )
    RealtimeHailingPhase.Running -> copy(
        realtimeHailingRunning = true,
        realtimeHailingBusy = false,
        playingPath = null,
        message = state.message.ifBlank { "实时喊话已开启" },
    )
    RealtimeHailingPhase.Stopping -> copy(
        realtimeHailingBusy = true,
        message = state.message.ifBlank { "正在停止实时喊话" },
    )
    RealtimeHailingPhase.Idle -> copy(
        realtimeHailingRunning = false,
        realtimeHailingBusy = false,
        message = state.message.ifBlank { "实时喊话已停止" },
    )
    RealtimeHailingPhase.Error -> copy(
        realtimeHailingRunning = false,
        realtimeHailingBusy = false,
        message = state.message.ifBlank { "实时喊话启动失败" },
    )
}

internal fun SoundUiState.withRealtimeHailingSnapshot(
    state: RealtimeHailingState,
): SoundUiState = copy(
    realtimeHailingRunning = state.isRunning,
    realtimeHailingBusy = state.isBusy,
)
