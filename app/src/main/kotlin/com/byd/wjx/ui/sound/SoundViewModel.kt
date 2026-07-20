package com.byd.wjx.ui.sound

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.audio.AutoParkingSoundSettings
import com.byd.wjx.audio.ExteriorChannelMode
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.data.sound.RemoteSound
import com.byd.wjx.data.sound.RemoteSoundCategory
import com.byd.wjx.data.sound.SoundDownloadRecord
import com.byd.wjx.data.sound.SoundDownloadStore
import com.byd.wjx.data.sound.SoundLibraryRemoteRepository
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SoundUiState(
    val sounds: List<SoundLibrary.SoundItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val playingPath: String? = null,
    val previewingRemoteId: Long? = null,
    val controllerLabel: String = "未知",
    val channelLabel: String = "自动识别中",
    val channelMode: ExteriorChannelMode = ExteriorChannelMode.AUTO,
    val selectedTab: SoundTab = SoundTab.Local,
    val remoteCategories: List<RemoteSoundCategory> = emptyList(),
    val remoteSounds: List<RemoteSound> = emptyList(),
    val remoteQuery: String = "",
    val remoteCategory: String? = null,
    val remotePage: Int = 1,
    val remoteTotal: Int = 0,
    val remoteHasMore: Boolean = false,
    val remoteLoading: Boolean = false,
    val remoteError: String? = null,
    val downloadingIds: Set<Long> = emptySet(),
    val previewLoadingIds: Set<Long> = emptySet(),
    val downloads: List<SoundDownloadRecord> = emptyList(),
    val realtimeHailingRunning: Boolean = false,
    val audioTuningPanelVisible: Boolean = false,
    val playbackVolumePercent: Int = ExteriorHailingSettings.DEFAULT_PLAYBACK_VOLUME_PERCENT,
    val realtimeVolumePercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_VOLUME_PERCENT,
    val realtimeMicGainPercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_MIC_GAIN_PERCENT,
    val realtimeNoiseSuppressionPercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_NOISE_SUPPRESSION_PERCENT,
)

enum class SoundTab(val title: String) {
    Local("我的音效"),
    Online("在线音效库"),
}

@HiltViewModel
class SoundViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val soundLibrary: SoundLibrary,
    private val remoteRepository: SoundLibraryRemoteRepository,
    private val downloadStore: SoundDownloadStore,
    private val lockSoundConfigSync: LockSoundConfigSync,
    private val autoParkingSettings: AutoParkingSoundSettings,
    private val profileDetector: CarProfileDetector = CarProfileDetector(),
) : ViewModel() {
    private val hailingSettings = ExteriorHailingSettings(appContext)
    private val lockSoundSettings = LockSoundSettings(appContext)
    private val _uiState = MutableStateFlow(SoundUiState(isLoading = true))
    val uiState: StateFlow<SoundUiState> = _uiState.asStateFlow()
    private var remoteJob: Job? = null
    private var remoteRequestSeq = 0
    private var previewPlayer: MediaPlayer? = null
    private var previewSeq = 0
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
            val path = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE_PATH).orEmpty()
            val state = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE).orEmpty()
            when (state) {
                WjxOutsidePlayService.STATE_STARTED -> {
                    if (path.isNotBlank()) {
                        _uiState.update { it.copy(playingPath = path, previewingRemoteId = null) }
                    }
                }
                WjxOutsidePlayService.STATE_STOPPED,
                WjxOutsidePlayService.STATE_COMPLETED,
                WjxOutsidePlayService.STATE_ERROR -> {
                    _uiState.update { current ->
                        if (path.isBlank() || current.playingPath == path) {
                            current.copy(playingPath = null, previewingRemoteId = null)
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
            if (intent?.action != WjxRealtimeHailingService.ACTION_HAILING_STATE) return
            val state = intent.getStringExtra(WjxRealtimeHailingService.EXTRA_STATE).orEmpty()
            val message = intent.getStringExtra(WjxRealtimeHailingService.EXTRA_MESSAGE).orEmpty()
            when (state) {
                WjxRealtimeHailingService.STATE_STARTED -> {
                    _uiState.update {
                        it.copy(
                            realtimeHailingRunning = true,
                            playingPath = null,
                            previewingRemoteId = null,
                            message = message.ifBlank { "实时喊话已开启" },
                        )
                    }
                }
                WjxRealtimeHailingService.STATE_STOPPED,
                WjxRealtimeHailingService.STATE_ERROR -> {
                    _uiState.update {
                        it.copy(
                            realtimeHailingRunning = false,
                            message = message.ifBlank {
                                if (state == WjxRealtimeHailingService.STATE_ERROR) "实时喊话启动失败" else "实时喊话已停止"
                            },
                        )
                    }
                }
            }
        }
    }

    init {
        registerPlaybackReceiver()
        registerHailingReceiver()
        _uiState.update { it.copy(downloads = downloadStore.loadRecords()) }
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
                IntentFilter(WjxRealtimeHailingService.ACTION_HAILING_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun refresh(clearMessage: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, message = if (clearMessage) null else it.message) }
            val sounds = soundLibrary.listSounds()
            val profile = profileDetector.detect()
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
                )
            }
        }
    }

    fun importSound(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val displayName = appContext.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "导入音效" }
                val extension = displayName.substringAfterLast('.', "").lowercase()
                require(extension in SoundLibrary.SUPPORTED_EXT) {
                    "仅支持 MP3、WAV、AAC、M4A、OGG、FLAC"
                }
                val baseName = displayName.substringBeforeLast('.', displayName)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: error("无法读取所选音效")
                val saved = input.use { soundLibrary.saveSound(baseName, extension, it) }
                _uiState.update {
                    it.copy(
                        sounds = soundLibrary.listSounds(),
                        message = "已导入：${saved.name}",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(message = "导入失败：${error.message ?: "文件不可用"}")
                }
            }
        }
    }

    fun selectTab(tab: SoundTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                audioTuningPanelVisible = if (tab == SoundTab.Local) it.audioTuningPanelVisible else false,
            )
        }
        if (tab == SoundTab.Online && _uiState.value.remoteSounds.isEmpty()) {
            refreshOnline()
        }
    }

    fun updateRemoteQuery(query: String) {
        _uiState.update { it.copy(remoteQuery = query) }
    }

    fun searchOnline() {
        _uiState.update { it.copy(remoteCategory = null) }
        refreshOnline(resetPage = true)
    }

    fun selectRemoteCategory(category: String?) {
        _uiState.update { it.copy(remoteCategory = category, remoteQuery = "") }
        refreshOnline(resetPage = true)
    }

    fun loadMoreOnline() {
        val state = _uiState.value
        if (state.remoteLoading || !state.remoteHasMore) return
        refreshOnline(resetPage = false)
    }

    fun refreshOnline(resetPage: Boolean = true) {
        remoteJob?.cancel()
        val requestSeq = ++remoteRequestSeq
        val query = _uiState.value.remoteQuery.trim()
        val category = _uiState.value.remoteCategory.takeIf { query.isBlank() }
        remoteJob = viewModelScope.launch(Dispatchers.IO) {
            val startState = _uiState.value
            val nextPage = if (resetPage) 1 else startState.remotePage + 1
            _uiState.update { it.copy(remoteLoading = true, remoteError = null) }
            runCatching {
                val categories = remoteRepository.loadCategories()
                val page = remoteRepository.searchSounds(
                    query = query,
                    category = category,
                    page = nextPage,
                )
                categories to page
            }.onSuccess { (categories, page) ->
                if (requestSeq != remoteRequestSeq) return@onSuccess
                _uiState.update { current ->
                    current.copy(
                        remoteCategories = categories,
                        remoteSounds = if (resetPage) page.items else current.remoteSounds + page.items,
                        remotePage = page.page,
                        remoteTotal = page.total,
                        remoteHasMore = page.hasMore,
                        remoteLoading = false,
                        remoteError = null,
                    )
                }
            }.onFailure { error ->
                if (requestSeq != remoteRequestSeq) return@onFailure
                _uiState.update {
                    it.copy(
                        remoteLoading = false,
                        remoteError = error.message ?: "在线音效库连接失败",
                    )
                }
            }
        }
    }

    fun preview(sound: RemoteSound) {
        if (sound.id in _uiState.value.previewLoadingIds) return
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.previewingRemoteId == sound.id) {
                previewSeq++
                stopInCarPreview()
                _uiState.update {
                    it.copy(
                        previewingRemoteId = null,
                        playingPath = null,
                        message = "已停止试听：${sound.name}",
                    )
                }
                return@launch
            }
            val requestSeq = ++previewSeq
            _uiState.update {
                it.copy(
                    previewLoadingIds = it.previewLoadingIds + sound.id,
                    message = "正在加载试听：${sound.name}",
                )
            }
            runCatching {
                val file = remoteRepository.previewToCache(sound, appContext.cacheDir.resolve("sound-preview"))
                if (requestSeq != previewSeq) {
                    _uiState.update { it.copy(previewLoadingIds = it.previewLoadingIds - sound.id) }
                    return@launch
                }
                WjxOutsidePlayService.stop(appContext)
                stopInCarPreview()
                _uiState.update {
                    it.copy(
                        previewLoadingIds = it.previewLoadingIds - sound.id,
                        previewingRemoteId = sound.id,
                        playingPath = file.absolutePath,
                        message = "正在试听：${sound.name}",
                    )
                }
                val player = MediaPlayer()
                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    player.setDataSource(file.absolutePath)
                    player.setOnCompletionListener { completedPlayer ->
                        releaseInCarPreview(completedPlayer)
                        if (requestSeq == previewSeq) {
                            _uiState.update { current ->
                                current.copy(previewingRemoteId = null, playingPath = null)
                            }
                        }
                    }
                    player.setOnErrorListener { errorPlayer, _, _ ->
                        releaseInCarPreview(errorPlayer)
                        if (requestSeq == previewSeq) {
                            _uiState.update { current ->
                                current.copy(
                                    previewingRemoteId = null,
                                    playingPath = null,
                                    message = "试听失败：${sound.name}",
                                )
                            }
                        }
                        true
                    }
                    player.prepare()
                    previewPlayer = player
                    player.start()
                } catch (error: Exception) {
                    releaseInCarPreview(player)
                    throw error
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    val next = current.copy(previewLoadingIds = current.previewLoadingIds - sound.id)
                    if (requestSeq == previewSeq) {
                        next.copy(
                            previewingRemoteId = null,
                            playingPath = null,
                            message = error.message ?: "试听失败：${sound.name}",
                        )
                    } else {
                        next
                    }
                }
            }
        }
    }

    fun download(sound: RemoteSound) {
        if (sound.id in _uiState.value.downloadingIds) return
        val existing = downloadStore.findExistingRecord(sound.id)
        if (existing != null) {
            _uiState.update { it.copy(message = "音效已存在：${existing.fileName}") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    downloadingIds = it.downloadingIds + sound.id,
                    message = "正在下载：${sound.name}",
                )
            }
            runCatching {
                remoteRepository.download(sound)
            }.onSuccess { result ->
                val record = SoundDownloadRecord(
                    soundId = sound.id,
                    name = sound.name,
                    fileName = result.fileName,
                    sizeLabel = SoundLibrary.formatSize(result.sizeBytes),
                    finishedAt = System.currentTimeMillis(),
                )
                downloadStore.add(record)
                _uiState.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - sound.id,
                        downloads = downloadStore.loadRecords(),
                        message = "已下载到 /sdcard/WJX：${result.fileName}",
                    )
                }
                refresh(clearMessage = false)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - sound.id,
                        message = error.message ?: "下载失败：${sound.name}",
                    )
                }
            }
        }
    }

    fun play(sound: SoundLibrary.SoundItem) {
        viewModelScope.launch(Dispatchers.IO) {
            previewSeq++
            stopInCarPreview()
            val soundPath = sound.file.absolutePath
            val currentPlaying = _uiState.value.playingPath
            if (currentPlaying == soundPath) {
                WjxOutsidePlayService.stop(appContext)
                _uiState.update {
                    it.copy(
                        playingPath = null,
                        previewingRemoteId = null,
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
                    previewingRemoteId = null,
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
            previewSeq++
            stopInCarPreview()
            if (_uiState.value.realtimeHailingRunning) {
                WjxRealtimeHailingService.stop(appContext, notifyIfIdle = true)
                _uiState.update { it.copy(message = "正在停止实时喊话") }
                return@launch
            }
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update { it.copy(message = "请先允许麦克风权限") }
                return@launch
            }
            WjxOutsidePlayService.stop(appContext)
            val profile = profileDetector.detect()
            val channel = hailingSettings.effectiveChannel(profile)
            WjxRealtimeHailingService.start(appContext, channel)
            _uiState.update {
                it.copy(
                    controllerLabel = profile.seriesLabel,
                    channelLabel = hailingSettings.channelModeLabel(profile),
                    channelMode = hailingSettings.channelMode,
                    playingPath = null,
                    previewingRemoteId = null,
                    message = "正在准备实时喊话：${channel.shortLabel}",
                )
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
        if (_uiState.value.realtimeHailingRunning) {
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
            val deleted = soundLibrary.deleteSound(soundPath)
            if (deleted) {
                downloadStore.removeByFileName(sound.file.name)
                autoParkingSettings.clearIfPath(soundPath)
                lockSoundSettings.clearIfPath(soundPath)
            }
            _uiState.update {
                it.copy(
                    sounds = soundLibrary.listSounds(),
                    downloads = downloadStore.loadRecords(),
                    playingPath = if (it.playingPath == soundPath) null else it.playingPath,
                    message = if (deleted) "已删除：${sound.name}" else "删除失败：${sound.name}",
                )
            }
            if (deleted) {
                lockSoundConfigSync.sync("sound_delete")
            }
        }
    }

    fun moveSound(fromPath: String, toPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            soundLibrary.moveSound(fromPath, toPath)
            _uiState.update { it.copy(sounds = soundLibrary.listSounds(), message = "已更新音效排序") }
        }
    }

    fun stop() {
        previewSeq++
        stopInCarPreview()
        WjxOutsidePlayService.stop(appContext)
        _uiState.update { it.copy(playingPath = null, previewingRemoteId = null, message = "已停止车外播放") }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun stopInCarPreview() {
        val player = previewPlayer ?: return
        releaseInCarPreview(player)
    }

    private fun releaseInCarPreview(player: MediaPlayer) {
        if (previewPlayer === player) previewPlayer = null
        runCatching {
            if (player.isPlaying) player.stop()
        }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    override fun onCleared() {
        stopInCarPreview()
        runCatching { appContext.unregisterReceiver(playbackReceiver) }
        runCatching { appContext.unregisterReceiver(hailingReceiver) }
        super.onCleared()
    }
}
