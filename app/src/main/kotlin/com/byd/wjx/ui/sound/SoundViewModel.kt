package com.byd.wjx.ui.sound

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.RealtimeHailingStateContract
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.data.sound.LocalSoundLibraryCoordinator
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SoundViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val localSoundLibraryCoordinator: LocalSoundLibraryCoordinator,
    private val profileDetector: CarProfileDetector = CarProfileDetector(),
) : ViewModel() {
    private val hailingSettings = ExteriorHailingSettings(appContext)
    private val _uiState = MutableStateFlow(SoundUiState(isLoading = true))
    val uiState: StateFlow<SoundUiState> = _uiState.asStateFlow()
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
            val path = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE_PATH).orEmpty()
            val state = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE).orEmpty()
            when (state) {
                WjxOutsidePlayService.STATE_STARTED -> {
                    if (path.isNotBlank()) {
                        _uiState.update { it.copy(playingPath = path) }
                    }
                }
                WjxOutsidePlayService.STATE_STOPPED,
                WjxOutsidePlayService.STATE_COMPLETED,
                WjxOutsidePlayService.STATE_ERROR -> {
                    _uiState.update { current ->
                        if (path.isBlank() || current.playingPath == path) {
                            current.copy(playingPath = null)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }
    private val hailingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = RealtimeHailingStateContract.fromIntent(intent) ?: return
            _uiState.update { it.withRealtimeHailingState(state) }
        }
    }

    init {
        registerPlaybackReceiver()
        registerHailingReceiver()
        refresh()
    }

    private fun registerPlaybackReceiver() {
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                playbackReceiver,
                IntentFilter(WjxOutsidePlayService.ACTION_PLAYBACK_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun registerHailingReceiver() {
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                hailingReceiver,
                IntentFilter(RealtimeHailingStateContract.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun refresh(clearMessage: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, message = if (clearMessage) null else it.message) }
            val sounds = localSoundLibraryCoordinator.listSounds()
            val profile = profileDetector.detect()
            val hailingState = WjxRealtimeHailingService.state()
            _uiState.update {
                it.copy(
                    sounds = sounds,
                    isLoading = false,
                    controllerLabel = profile.seriesLabel,
                    channelLabel = hailingSettings.channelModeLabel(profile),
                    channelMode = hailingSettings.channelMode,
                    playbackVolumePercent = hailingSettings.playbackVolumePercent,
                    realtimeVolumePercent = hailingSettings.realtimeVolumePercent,
                    realtimeMicGainPercent = hailingSettings.realtimeAutoMicSensitivityPercent,
                    realtimeNoiseSuppressionPercent = hailingSettings.realtimeNoiseSuppressionPercent,
                ).withRealtimeHailingSnapshot(hailingState)
            }
        }
    }

    fun play(sound: SoundLibrary.SoundItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val soundPath = sound.file.absolutePath
            val currentPlaying = _uiState.value.playingPath
            if (currentPlaying == soundPath) {
                WjxOutsidePlayService.stop(appContext)
                _uiState.update {
                    it.copy(
                        playingPath = null,
                        message = "已停止：${sound.name}",
                    )
                }
                return@launch
            }
            val profile = profileDetector.detect()
            val channel = hailingSettings.effectiveChannel(profile)
            WjxOutsidePlayService.play(appContext, sound.file, channel)
            _uiState.update {
                it.copy(
                    playingPath = soundPath,
                    controllerLabel = profile.seriesLabel,
                    channelLabel = hailingSettings.channelModeLabel(profile),
                    channelMode = hailingSettings.channelMode,
                    message = "正在准备车外播放：${sound.name} · ${channel.shortLabel}",
                )
            }
        }
    }

    fun toggleRealtimeHailing() {
        viewModelScope.launch(Dispatchers.IO) {
            if (WjxRealtimeHailingService.state().isActive) {
                runCatching {
                    WjxRealtimeHailingService.stop(appContext, notifyIfIdle = true)
                }.onSuccess {
                    val state = WjxRealtimeHailingService.state()
                    _uiState.update {
                        it.copy(
                            realtimeHailingRunning = state.isRunning,
                            realtimeHailingBusy = state.isBusy,
                            message = state.message.ifBlank { "实时喊话已停止" },
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(realtimeHailingBusy = false, message = "实时喊话停止失败，请重试") }
                }
                return@launch
            }
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update { it.copy(message = "请先允许麦克风权限") }
                return@launch
            }
            WjxOutsidePlayService.stop(appContext)
            val profile = profileDetector.detect()
            val channel = hailingSettings.effectiveChannel(profile)
            runCatching { WjxRealtimeHailingService.start(appContext, channel) }
                .onSuccess {
                    val state = WjxRealtimeHailingService.state()
                    _uiState.update {
                        it.copy(
                            controllerLabel = profile.seriesLabel,
                            channelLabel = hailingSettings.channelModeLabel(profile),
                            channelMode = hailingSettings.channelMode,
                            realtimeHailingRunning = state.isRunning,
                            realtimeHailingBusy = state.isBusy,
                            playingPath = null,
                            message = "正在准备实时喊话：${channel.shortLabel}",
                        )
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(realtimeHailingBusy = false, message = "实时喊话启动失败")
                    }
                }
        }
    }

    fun toggleAudioTuningPanel() {
        _uiState.update { it.copy(audioTuningPanelVisible = !it.audioTuningPanelVisible) }
    }

    fun setPlaybackVolumePercent(value: Int) {
        hailingSettings.playbackVolumePercent = value
        notifyPlaybackVolumeChanged()
        _uiState.update { it.copy(playbackVolumePercent = hailingSettings.playbackVolumePercent) }
    }

    fun setRealtimeVolumePercent(value: Int) {
        hailingSettings.realtimeVolumePercent = value
        notifyRealtimeTuningChanged()
        _uiState.update {
            it.copy(
                realtimeVolumePercent = hailingSettings.realtimeVolumePercent,
                realtimeMicGainPercent = hailingSettings.realtimeAutoMicSensitivityPercent,
            )
        }
    }

    fun setRealtimeNoiseSuppressionPercent(value: Int) {
        hailingSettings.realtimeNoiseSuppressionPercent = value
        notifyRealtimeTuningChanged()
        _uiState.update {
            it.copy(
                realtimeNoiseSuppressionPercent = hailingSettings.realtimeNoiseSuppressionPercent,
                realtimeMicGainPercent = hailingSettings.realtimeAutoMicSensitivityPercent,
            )
        }
    }

    fun resetAudioTuning() {
        hailingSettings.resetAudioTuning()
        notifyPlaybackVolumeChanged()
        notifyRealtimeTuningChanged()
        _uiState.update {
            it.copy(
                playbackVolumePercent = hailingSettings.playbackVolumePercent,
                realtimeVolumePercent = hailingSettings.realtimeVolumePercent,
                realtimeMicGainPercent = hailingSettings.realtimeAutoMicSensitivityPercent,
                realtimeNoiseSuppressionPercent = hailingSettings.realtimeNoiseSuppressionPercent,
                audioTuningPanelVisible = true,
                message = "音量和喊话参数已恢复默认",
            )
        }
    }

    private fun notifyRealtimeTuningChanged() {
        if (WjxRealtimeHailingService.state().isRunning) {
            WjxRealtimeHailingService.updateTuning(appContext)
        }
    }

    private fun notifyPlaybackVolumeChanged() {
        WjxOutsidePlayService.updateVolume(appContext)
    }

    fun delete(sound: SoundLibrary.SoundItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val soundPath = sound.file.absolutePath
            if (_uiState.value.playingPath == soundPath) {
                WjxOutsidePlayService.stop(appContext)
            }
            val result = localSoundLibraryCoordinator.deleteSound(soundPath)
            _uiState.update {
                it.copy(
                    sounds = result.sounds,
                    playingPath = if (it.playingPath == soundPath) null else it.playingPath,
                    message = when {
                        !result.deleted -> "删除失败：${sound.name}"
                        result.eventSoundSyncSucceeded == false ->
                            "已删除：${sound.name}，车辆事件音效将在后台稍后同步"
                        else -> "已删除：${sound.name}"
                    },
                )
            }
        }
    }

    fun moveSound(fromPath: String, toPath: String) {
        viewModelScope.launch {
            val sounds = localSoundLibraryCoordinator.moveSound(fromPath, toPath)
            _uiState.update { it.copy(sounds = sounds, message = "已更新音效排序") }
        }
    }

    fun stop() {
        WjxOutsidePlayService.stop(appContext)
        _uiState.update {
            it.copy(
                playingPath = null,
                message = "已停止车外播放",
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        runCatching { appContext.unregisterReceiver(playbackReceiver) }
        runCatching { appContext.unregisterReceiver(hailingReceiver) }
        super.onCleared()
    }
}
