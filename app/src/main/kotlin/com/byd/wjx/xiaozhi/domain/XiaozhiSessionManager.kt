package com.byd.wjx.xiaozhi.domain

import android.content.Context
import com.byd.wjx.audio.AudioOwner
import com.byd.wjx.audio.AudioUsageCoordinator
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.xiaozhi.audio.XiaozhiAudioEngine
import com.byd.wjx.xiaozhi.audio.XiaozhiFeedbackPlayer
import com.byd.wjx.xiaozhi.data.XiaozhiPreferences
import com.byd.wjx.xiaozhi.protocol.XiaozhiProvisioningClient
import com.byd.wjx.xiaozhi.protocol.XiaozhiServerEvent
import com.byd.wjx.xiaozhi.protocol.XiaozhiWebSocketClient
import com.byd.wjx.xiaozhi.protocol.XiaozhiWebSocketListener
import com.byd.wjx.xiaozhi.service.XiaozhiVoiceService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class XiaozhiSessionManager @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: XiaozhiPreferences,
    private val provisioningClient: XiaozhiProvisioningClient,
    private val webSocketClient: XiaozhiWebSocketClient,
    private val audioEngine: XiaozhiAudioEngine,
    private val feedbackPlayer: XiaozhiFeedbackPlayer,
    private val audioUsage: AudioUsageCoordinator,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : XiaozhiWebSocketListener {
    private val appContext = context.applicationContext
    private val messageIds = AtomicLong(System.currentTimeMillis())
    private val _state = MutableStateFlow(initialState(preferences.config.value))
    val state: StateFlow<XiaozhiUiState> = _state.asStateFlow()

    private var handshakeTimeoutJob: Job? = null
    private var startPreparationJob: Job? = null
    private var autoListenTimeoutJob: Job? = null
    private var autoRestartJob: Job? = null
    private var responseTimeoutJob: Job? = null
    private var speakingTimeoutJob: Job? = null
    private var idleDisconnectJob: Job? = null
    private var provisioningJob: Job? = null
    private var startRequested = false
    private var continuousConversation = false
    private var serverFrameDurationMs = 60
    private val commandQueue = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        applicationScope.launch {
            for (command in commandQueue) {
                runCatching(command).onFailure { error ->
                    WjxFileLogger.e(TAG, "session command failed: ${error.message}", error)
                    runCatching { failSession(error.message ?: "小智会话内部异常") }
                }
            }
        }
        applicationScope.launch {
            preferences.config.collect { config ->
                dispatch {
                    _state.update { current ->
                        val inactive = current.phase in setOf(
                            XiaozhiPhase.NotConfigured,
                            XiaozhiPhase.Idle,
                            XiaozhiPhase.Error,
                        )
                        current.copy(
                            config = config,
                            phase = if (inactive) {
                                if (config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured
                            } else current.phase,
                            statusText = if (inactive) {
                                if (config.isConfigured) "小智已配置，可以开始对话" else "尚未配置小智服务"
                            } else current.statusText,
                            error = if (config.isConfigured) null else current.error,
                        )
                    }
                }
            }
        }
    }

    fun toggleAutomaticConversation() = dispatch(::toggleAutomaticConversationNow)

    private fun toggleAutomaticConversationNow() {
        when (_state.value.phase) {
            XiaozhiPhase.NotConfigured -> {
                setError("请先在设置中获取官方配置，或填写 WebSocket 地址")
                XiaozhiVoiceService.stop(appContext)
            }
            XiaozhiPhase.Idle, XiaozhiPhase.Error -> startAutomaticConversationNow()
            XiaozhiPhase.Connecting -> cancelConversation("已取消连接")
            XiaozhiPhase.Listening, XiaozhiPhase.Thinking, XiaozhiPhase.Speaking ->
                abortConversationNow()
        }
    }

    fun startAutomaticConversation() = dispatch(::startAutomaticConversationNow)

    fun startWakeConversation() = dispatch(::startAutomaticConversationNow)

    private fun startAutomaticConversationNow() {
        if (_state.value.phase in ACTIVE_PHASES) {
            WjxFileLogger.i(TAG, "duplicate start ignored phase=${_state.value.phase}")
            return
        }
        val config = preferences.config.value
        if (!config.isConfigured) {
            setError("尚未配置小智服务器")
            XiaozhiVoiceService.stop(appContext)
            return
        }
        if (!XiaozhiVoiceService.ensureRunning(appContext)) {
            failSession("无法启动小智语音服务")
            return
        }
        audioUsage.setActive(AudioOwner.XiaozhiConversation, true)
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        idleDisconnectJob?.cancel()
        continuousConversation = true
        startRequested = true
        _state.update {
            it.copy(
                phase = XiaozhiPhase.Connecting,
                statusText = "正在准备自动聆听",
                error = null,
            )
        }
        stopExteriorAudio()
        startPreparationJob?.cancel()
        startPreparationJob = applicationScope.launch {
            val released = audioUsage.awaitInactive(
                owners = START_BLOCKING_OWNERS,
                timeoutMs = AUDIO_RESOURCE_RELEASE_TIMEOUT_MS,
            )
            if (released) delay(AUDIO_RESOURCE_SETTLE_MS)
            dispatch {
                if (!released && startRequested) {
                    failSession("车外音频或麦克风未能及时释放")
                } else {
                    continueStartConversation()
                }
            }
        }
    }

    private fun continueStartConversation() {
        startPreparationJob = null
        if (!startRequested || _state.value.phase != XiaozhiPhase.Connecting) return
        val config = preferences.config.value
        if (!config.isConfigured) {
            failSession("小智服务器配置已失效")
            return
        }
        audioEngine.acquireFocus(::handleAudioFocusLost)
            .onFailure {
                failSession(it.message ?: "无法取得音频焦点")
                return
            }
        _state.update {
            it.copy(
                phase = XiaozhiPhase.Connecting,
                statusText = if (webSocketClient.isReady()) "正在准备麦克风" else "正在连接小智服务器",
                error = null,
            )
        }
        if (webSocketClient.isReady()) {
            beginListening()
            return
        }
        webSocketClient.connect(config, this)
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = applicationScope.launch {
            delay(HANDSHAKE_TIMEOUT_MS)
            dispatch {
                if (_state.value.phase == XiaozhiPhase.Connecting) {
                    failSession("小智服务器握手超时")
                }
            }
        }
    }

    fun abortConversation() = dispatch(::abortConversationNow)

    fun preemptForRealtimeHailing() = dispatch {
        if (_state.value.phase in ACTIVE_PHASES || audioUsage.isActive(AudioOwner.XiaozhiConversation)) {
            abortConversationNow("实时喊话已接管麦克风")
        }
    }

    private fun abortConversationNow(status: String? = null) {
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        startPreparationJob?.cancel()
        handshakeTimeoutJob?.cancel()
        audioEngine.stopRecording()
        audioEngine.stopPlayback()
        webSocketClient.abort()
        audioEngine.abandonFocus()
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        _state.update {
            it.copy(
                phase = if (it.config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
                statusText = status ?: if (it.config.isConfigured) "对话已停止" else "尚未配置小智服务",
                error = null,
            )
        }
        scheduleIdleDisconnect()
        XiaozhiVoiceService.stop(appContext)
    }

    fun disconnect() = dispatch(::disconnectNow)

    private fun disconnectNow() {
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        idleDisconnectJob?.cancel()
        startPreparationJob?.cancel()
        handshakeTimeoutJob?.cancel()
        audioEngine.stopAll()
        webSocketClient.disconnect()
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        _state.update {
            it.copy(
                connected = false,
                phase = if (it.config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
                statusText = if (it.config.isConfigured) "已断开，下次对话会重新连接" else "尚未配置小智服务",
                error = null,
            )
        }
        XiaozhiVoiceService.stop(appContext)
    }

    fun saveManualConfig(websocketUrl: String, token: String, otaUrl: String) = dispatch {
        val normalizedUrl = websocketUrl.trim()
        val secureWebSocket = normalizedUrl.startsWith("wss://")
        val localWebSocket = normalizedUrl.startsWith("ws://localhost") ||
            normalizedUrl.startsWith("ws://127.0.0.1")
        if (normalizedUrl.isNotBlank() && !secureWebSocket && !localWebSocket) {
            _state.update {
                it.copy(
                    provisioning = XiaozhiProvisioningState.Failed(
                        "请使用 wss:// 加密地址；未加密 ws:// 仅允许 localhost 或 127.0.0.1",
                    ),
                )
            }
            return@dispatch
        }
        disconnectNow()
        preferences.saveServer(normalizedUrl, token, otaUrl)
        _state.update {
            it.copy(
                provisioning = if (normalizedUrl.isBlank()) {
                    XiaozhiProvisioningState.Idle
                } else {
                    XiaozhiProvisioningState.Ready("小智服务器配置已保存")
                },
            )
        }
    }

    fun clearConfiguration() = dispatch {
        provisioningJob?.cancel()
        disconnectNow()
        preferences.clearServer()
        _state.update {
            it.copy(
                provisioning = XiaozhiProvisioningState.Idle,
                phase = XiaozhiPhase.NotConfigured,
                statusText = "尚未配置小智服务",
            )
        }
    }

    fun provisionOfficial(otaUrl: String) = dispatch {
        provisioningJob?.cancel()
        preferences.saveOtaUrl(otaUrl)
        provisioningJob = applicationScope.launch {
            _state.update { it.copy(provisioning = XiaozhiProvisioningState.Checking) }
            repeat(PROVISION_ATTEMPTS) { attempt ->
                val result = provisioningClient.fetch(preferences.config.value)
                val response = result.getOrElse { error ->
                    _state.update {
                        it.copy(
                            provisioning = XiaozhiProvisioningState.Failed(
                                error.message ?: "官方配置请求失败",
                            ),
                        )
                    }
                    return@launch
                }
                if (!response.activationCode.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            provisioning = XiaozhiProvisioningState.ActivationRequired(
                                code = response.activationCode,
                                message = response.activationMessage ?: "请在小智控制台绑定该设备",
                            ),
                        )
                    }
                    if (attempt < PROVISION_ATTEMPTS - 1) delay(PROVISION_POLL_MS)
                    return@repeat
                }
                if (!response.websocketUrl.isNullOrBlank()) {
                    preferences.saveServer(
                        websocketUrl = response.websocketUrl,
                        token = response.token.orEmpty(),
                        otaUrl = preferences.config.value.otaUrl,
                    )
                    _state.update {
                        it.copy(provisioning = XiaozhiProvisioningState.Ready())
                    }
                    return@launch
                }
                _state.update {
                    it.copy(provisioning = XiaozhiProvisioningState.Failed("官方响应中没有 WebSocket 配置"))
                }
                return@launch
            }
            val current = _state.value.provisioning
            if (current is XiaozhiProvisioningState.ActivationRequired) {
                _state.update {
                    it.copy(
                        provisioning = XiaozhiProvisioningState.ActivationRequired(
                            code = current.code,
                            message = "等待激活超时，请完成绑定后点击“获取官方配置”刷新",
                        ),
                    )
                }
            }
        }
    }

    fun clearMessages() = dispatch {
        _state.update { it.copy(messages = emptyList()) }
    }

    fun onVoiceHostStopped() = dispatch {
        if (_state.value.phase !in setOf(XiaozhiPhase.Connecting, XiaozhiPhase.Listening, XiaozhiPhase.Thinking, XiaozhiPhase.Speaking)) {
            return@dispatch
        }
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        startPreparationJob?.cancel()
        handshakeTimeoutJob?.cancel()
        audioEngine.stopAll()
        webSocketClient.disconnect("小智语音服务已停止")
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        _state.update {
            it.copy(
                connected = false,
                phase = if (it.config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
                statusText = "小智语音服务已停止",
                error = null,
            )
        }
    }

    override fun onReady(hello: XiaozhiServerEvent.Hello) {
        dispatch { handleReady(hello) }
    }

    private fun handleReady(hello: XiaozhiServerEvent.Hello) {
        handshakeTimeoutJob?.cancel()
        serverFrameDurationMs = hello.frameDurationMs.coerceIn(10, 120)
        _state.update {
            it.copy(
                connected = true,
                serverSampleRate = hello.sampleRate,
                statusText = "小智服务器已连接",
                error = null,
            )
        }
        if (startRequested) beginListening()
    }

    override fun onEvent(event: XiaozhiServerEvent) {
        dispatch { handleEvent(event) }
    }

    private fun handleEvent(event: XiaozhiServerEvent) {
        when (event) {
            is XiaozhiServerEvent.Stt -> if (event.text.isNotBlank()) {
                autoListenTimeoutJob?.cancel()
                // Auto mode uses the server-side VAD to finish an utterance. Once STT arrives,
                // release the recorder immediately so it cannot capture the reply or continue
                // uploading cabin audio while the model is thinking.
                startRequested = false
                audioEngine.stopRecording()
                addMessage(XiaozhiMessageRole.User, event.text)
                _state.update { it.copy(phase = XiaozhiPhase.Thinking, statusText = "小智正在思考") }
                scheduleResponseTimeout()
            }
            is XiaozhiServerEvent.Tts -> handleTts(event)
            is XiaozhiServerEvent.Alert -> {
                val text = listOfNotNull(event.status, event.message).joinToString("：")
                if (text.isNotBlank()) addMessage(XiaozhiMessageRole.System, text)
            }
            is XiaozhiServerEvent.Llm -> Unit
            is XiaozhiServerEvent.Mcp -> Unit // MCP vehicle tools are intentionally disabled in phase one.
            is XiaozhiServerEvent.Unknown -> WjxFileLogger.i(TAG, "ignored event type=${event.type}")
            is XiaozhiServerEvent.Hello -> Unit
        }
    }

    override fun onAudio(packet: ByteArray) {
        audioEngine.enqueuePlayback(packet)
    }

    override fun onClosed(reason: String) {
        dispatch { handleClosed(reason) }
    }

    private fun handleClosed(reason: String) {
        handshakeTimeoutJob?.cancel()
        startPreparationJob?.cancel()
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        idleDisconnectJob?.cancel()
        val wasActive = _state.value.phase !in setOf(XiaozhiPhase.Idle, XiaozhiPhase.NotConfigured)
        audioEngine.stopAll()
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        _state.update {
            it.copy(
                connected = false,
                phase = if (wasActive) XiaozhiPhase.Error else if (it.config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
                statusText = if (wasActive) "小智连接已断开" else "连接已关闭",
                error = if (wasActive) reason else null,
            )
        }
        if (wasActive) feedbackPlayer.playFailureTone()
        XiaozhiVoiceService.stop(appContext)
    }

    override fun onFailure(message: String) {
        dispatch { failSession(message) }
    }

    private fun beginListening() {
        if (!startRequested || !webSocketClient.isReady()) return
        val sampleRate = _state.value.serverSampleRate
        audioEngine.configurePlayback(sampleRate, serverFrameDurationMs)
            .onFailure {
                failSession(it.message ?: "车内语音播放器初始化失败")
                return
            }
        if (!webSocketClient.startAutomaticListening()) {
            failSession("开始对话消息发送失败")
            return
        }
        audioEngine.startRecording(
            onPacket = { packet ->
                if (!webSocketClient.sendAudio(packet)) {
                    WjxFileLogger.w(TAG, "audio packet dropped: websocket not ready")
                }
            },
            onError = { message -> dispatch { failSession(message) } },
        ).onFailure {
            failSession(it.message ?: "麦克风启动失败")
            return
        }
        _state.update {
            it.copy(
                phase = XiaozhiPhase.Listening,
                statusText = "正在自动聆听，说完后会自动识别",
                error = null,
            )
        }
        scheduleAutoListenTimeout()
    }

    private fun handleTts(event: XiaozhiServerEvent.Tts) {
        when (event.state) {
            "start" -> {
                startRequested = false
                autoListenTimeoutJob?.cancel()
                cancelResponseTimeout()
                scheduleSpeakingTimeout()
                audioEngine.stopRecording()
                _state.update { it.copy(phase = XiaozhiPhase.Speaking, statusText = "小智正在回答") }
            }
            "sentence_start" -> event.text?.takeIf { it.isNotBlank() }?.let {
                cancelResponseTimeout()
                scheduleSpeakingTimeout()
                if (_state.value.phase != XiaozhiPhase.Speaking) {
                    startRequested = false
                    autoListenTimeoutJob?.cancel()
                    audioEngine.stopRecording()
                    _state.update { state ->
                        state.copy(phase = XiaozhiPhase.Speaking, statusText = "小智正在回答")
                    }
                }
                addMessage(XiaozhiMessageRole.Assistant, it)
            }
            "stop" -> {
                cancelResponseTimeout()
                cancelSpeakingTimeout()
                audioEngine.finishPlayback {
                    dispatch {
                        if (continuousConversation && webSocketClient.isReady()) {
                            _state.update {
                                it.copy(
                                    phase = XiaozhiPhase.Connecting,
                                    statusText = "回答完成，继续说话即可",
                                    error = null,
                                )
                            }
                            autoRestartJob?.cancel()
                            autoRestartJob = applicationScope.launch {
                                delay(AUTO_RESTART_AFTER_TTS_MS)
                                dispatch {
                                    if (continuousConversation && _state.value.phase == XiaozhiPhase.Connecting) {
                                        startRequested = true
                                        beginListening()
                                    }
                                }
                            }
                        } else {
                            finishConversation("回答完成，可以继续对话")
                        }
                    }
                }
            }
        }
    }

    private fun addMessage(role: XiaozhiMessageRole, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        _state.update { current ->
            val next = current.messages + XiaozhiChatMessage(
                id = messageIds.incrementAndGet(),
                role = role,
                text = normalized,
            )
            current.copy(messages = next.takeLast(MAX_MESSAGES))
        }
    }

    private fun scheduleAutoListenTimeout() {
        autoListenTimeoutJob?.cancel()
        autoListenTimeoutJob = applicationScope.launch {
            delay(AUTO_LISTEN_TIMEOUT_MS)
            dispatch {
                if (_state.value.phase == XiaozhiPhase.Listening) {
                    WjxFileLogger.i(TAG, "auto conversation idle timeout")
                    continuousConversation = false
                    startRequested = false
                    audioEngine.stopRecording()
                    webSocketClient.abort()
                    finishConversation("一段时间没有听到说话，已恢复等待唤醒")
                }
            }
        }
    }

    private fun finishConversation(status: String) {
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        audioEngine.stopRecording()
        audioEngine.abandonFocus()
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        _state.update {
            it.copy(
                phase = if (it.config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
                statusText = status,
                error = null,
            )
        }
        scheduleIdleDisconnect()
        XiaozhiVoiceService.stop(appContext)
    }

    private fun cancelAutoJobs() {
        autoListenTimeoutJob?.cancel()
        autoListenTimeoutJob = null
        autoRestartJob?.cancel()
        autoRestartJob = null
    }

    private fun scheduleResponseTimeout() {
        responseTimeoutJob?.cancel()
        responseTimeoutJob = applicationScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            dispatch {
                if (_state.value.phase == XiaozhiPhase.Thinking) {
                    failSession("小智服务器长时间没有返回回答")
                }
            }
        }
    }

    private fun cancelResponseTimeout() {
        responseTimeoutJob?.cancel()
        responseTimeoutJob = null
    }

    private fun scheduleSpeakingTimeout() {
        speakingTimeoutJob?.cancel()
        speakingTimeoutJob = applicationScope.launch {
            delay(SPEAKING_TIMEOUT_MS)
            dispatch {
                if (_state.value.phase == XiaozhiPhase.Speaking) {
                    failSession("小智回答播放长时间未结束")
                }
            }
        }
    }

    private fun cancelSpeakingTimeout() {
        speakingTimeoutJob?.cancel()
        speakingTimeoutJob = null
    }

    private fun scheduleIdleDisconnect() {
        idleDisconnectJob?.cancel()
        if (!webSocketClient.isReady()) return
        idleDisconnectJob = applicationScope.launch {
            delay(IDLE_CONNECTION_TIMEOUT_MS)
            dispatch {
                if (_state.value.phase in setOf(XiaozhiPhase.Idle, XiaozhiPhase.Error) && webSocketClient.isReady()) {
                    webSocketClient.disconnect("空闲连接自动释放")
                    _state.update { it.copy(connected = false) }
                }
            }
        }
    }

    private fun failSession(message: String) {
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        idleDisconnectJob?.cancel()
        startPreparationJob?.cancel()
        handshakeTimeoutJob?.cancel()
        audioEngine.stopAll()
        webSocketClient.disconnect("会话失败")
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        setError(message)
        feedbackPlayer.playFailureTone()
        XiaozhiVoiceService.stop(appContext)
    }

    private fun cancelConversation(status: String) {
        startRequested = false
        continuousConversation = false
        cancelAutoJobs()
        cancelResponseTimeout()
        cancelSpeakingTimeout()
        idleDisconnectJob?.cancel()
        startPreparationJob?.cancel()
        handshakeTimeoutJob?.cancel()
        audioEngine.stopAll()
        webSocketClient.disconnect(status)
        audioUsage.setActive(AudioOwner.XiaozhiConversation, false)
        _state.update {
            it.copy(
                connected = false,
                phase = if (it.config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
                statusText = status,
                error = null,
            )
        }
        XiaozhiVoiceService.stop(appContext)
    }

    private fun handleAudioFocusLost() {
        dispatch {
            if (_state.value.phase in setOf(XiaozhiPhase.Listening, XiaozhiPhase.Thinking, XiaozhiPhase.Speaking)) {
                abortConversationNow()
                _state.update { it.copy(statusText = "音频被其他功能占用，对话已停止") }
            }
        }
    }

    private fun stopExteriorAudio() {
        WjxOutsidePlayService.stop(appContext)
        WjxRealtimeHailingService.stop(appContext)
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                connected = false,
                phase = XiaozhiPhase.Error,
                statusText = message,
                error = message,
            )
        }
    }

    private fun dispatch(command: () -> Unit) {
        if (!commandQueue.trySend(command).isSuccess) {
            WjxFileLogger.w(TAG, "session command dropped: queue unavailable")
        }
    }

    companion object {
        private const val TAG = "XiaozhiSession"
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
        private const val AUDIO_RESOURCE_RELEASE_TIMEOUT_MS = 2_400L
        private const val AUDIO_RESOURCE_SETTLE_MS = 320L
        private const val PROVISION_POLL_MS = 3_000L
        private const val PROVISION_ATTEMPTS = 30
        private const val MAX_MESSAGES = 60
        private const val AUTO_LISTEN_TIMEOUT_MS = 15_000L
        private const val AUTO_RESTART_AFTER_TTS_MS = 420L
        private const val RESPONSE_TIMEOUT_MS = 45_000L
        private const val SPEAKING_TIMEOUT_MS = 90_000L
        private const val IDLE_CONNECTION_TIMEOUT_MS = 120_000L
        private val START_BLOCKING_OWNERS = setOf(
            AudioOwner.WakeWordListener,
            AudioOwner.RealtimeHailing,
            AudioOwner.ExteriorPlayback,
        )
        private val ACTIVE_PHASES = setOf(
            XiaozhiPhase.Connecting,
            XiaozhiPhase.Listening,
            XiaozhiPhase.Thinking,
            XiaozhiPhase.Speaking,
        )

        private fun initialState(config: XiaozhiConfig): XiaozhiUiState = XiaozhiUiState(
            config = config,
            phase = if (config.isConfigured) XiaozhiPhase.Idle else XiaozhiPhase.NotConfigured,
            statusText = if (config.isConfigured) "小智已配置，可以开始对话" else "尚未配置小智服务",
        )
    }
}
