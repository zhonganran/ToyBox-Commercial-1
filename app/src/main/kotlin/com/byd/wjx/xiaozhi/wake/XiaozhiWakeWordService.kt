package com.byd.wjx.xiaozhi.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.byd.wjx.MainActivity
import com.byd.wjx.R
import com.byd.wjx.audio.AudioOwner
import com.byd.wjx.audio.AudioUsageCoordinator
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.xiaozhi.audio.XiaozhiFeedbackPlayer
import com.byd.wjx.xiaozhi.domain.XiaozhiPhase
import com.byd.wjx.xiaozhi.domain.XiaozhiSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class XiaozhiWakeWordService : LifecycleService(), RecognitionListener {
    @Inject lateinit var preferences: XiaozhiWakePreferences
    @Inject lateinit var controller: XiaozhiWakeController
    @Inject lateinit var sessionManager: XiaozhiSessionManager
    @Inject lateinit var audioUsage: AudioUsageCoordinator
    @Inject lateinit var feedbackPlayer: XiaozhiFeedbackPlayer

    private var recognizer: SpeechRecognizer? = null
    private var recognizerActive = false
    private var usingOnDeviceRecognizer = false
    private var allowOnDeviceRecognizer = true
    private var recognitionEngineLabel = "系统语音识别"
    private var restartJob: Job? = null
    private var wakeJob: Job? = null
    private var wakeTriggered = false
    private var recognizerFailureCount = 0
    private var gate = WakeListeningGate()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("正在准备语音唤醒"))
        controller.reportService(true, false, "正在准备语音唤醒")
        lifecycleScope.launch {
            combine(
                preferences.config,
                sessionManager.state,
                audioUsage.activeOwners,
            ) { config, session, audioOwners ->
                WakeListeningGate(
                    enabled = config.enabled,
                    phrase = config.phrase,
                    serverConfigured = session.config.isConfigured,
                    conversationIdle = session.phase in setOf(XiaozhiPhase.Idle, XiaozhiPhase.Error),
                    microphoneBusy = audioOwners.any { it != AudioOwner.WakeWordListener },
                )
            }
                .distinctUntilChanged()
                .collect { next ->
                    gate = next
                    reconcileListening()
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_DISABLE -> {
                controller.setEnabled(false)
                stopSelf()
            }
            ACTION_RECONCILE, null -> Unit // StateFlow collector performs the actual reconciliation.
        }
        return START_STICKY
    }

    override fun onDestroy() {
        restartJob?.cancel()
        wakeJob?.cancel()
        pauseRecognition(destroy = true)
        controller.reportService(false, false, if (preferences.config.value.enabled) "语音唤醒服务已停止" else "语音唤醒未开启")
        super.onDestroy()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        if (!recognizerActive) return
        recognizerFailureCount = 0
        updateStatus("等待唤醒词“${gate.phrase}”", listening = true)
    }

    override fun onBeginningOfSpeech() {
        if (recognizerActive) updateStatus("正在识别唤醒词", listening = true)
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        if (recognizerActive) updateStatus("正在确认唤醒词", listening = false)
    }

    override fun onError(error: Int) {
        markRecognitionInactive()
        if (usingOnDeviceRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        ) {
            WjxFileLogger.w(TAG, "on-device recognition unavailable error=$error; falling back to system recognizer")
            allowOnDeviceRecognizer = false
            pauseRecognition(destroy = true)
            scheduleRestart(300L, "正在切换系统语音识别")
            return
        }
        val delayMs = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_200L
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 2_500L
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                updateStatus("麦克风权限不可用", listening = false)
                return
            }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
            else -> nextRecognizerRetryDelay()
        }
        if (error !in setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            WjxFileLogger.w(TAG, "recognition error=$error restart=${delayMs}ms")
        }
        scheduleRestart(delayMs, if (delayMs > 1_000L) "语音识别暂时不可用，正在重试" else "等待唤醒词“${gate.phrase}”")
    }

    override fun onResults(results: Bundle?) {
        markRecognitionInactive()
        if (!handleResults(results)) scheduleRestart(250L)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        handleResults(partialResults)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun reconcileListening() {
        when {
            !gate.enabled -> stopSelf()
            !gate.serverConfigured -> {
                pauseRecognition()
                updateStatus("请先完成小智服务配置", listening = false)
            }
            !gate.conversationIdle -> {
                pauseRecognition()
                updateStatus("小智正在对话，唤醒监听已暂停", listening = false)
            }
            gate.microphoneBusy -> {
                pauseRecognition()
                updateStatus("其他麦克风功能正在使用，唤醒监听已让行", listening = false)
            }
            wakeTriggered -> Unit
            else -> scheduleRestart(RESTART_AFTER_RELEASE_MS)
        }
    }

    private fun scheduleRestart(delayMs: Long, status: String? = null) {
        restartJob?.cancel()
        if (!canListen()) return
        status?.let { updateStatus(it, listening = false) }
        restartJob = lifecycleScope.launch {
            if (delayMs > 0L) delay(delayMs)
            startListening()
        }
    }

    private fun startListening() {
        restartJob = null
        if (!canListen() || recognizerActive) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("需要麦克风权限才能开启语音唤醒", listening = false)
            return
        }
        val activeRecognizer = recognizer ?: createRecognizer() ?: run {
            updateStatus("车机语音识别服务初始化失败", listening = false, available = false)
            scheduleRestart(nextRecognizerRetryDelay(), "语音识别引擎暂时不可用，正在自动恢复")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 450L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        runCatching {
            recognizerActive = true
            activeRecognizer.startListening(intent)
            audioUsage.setActive(AudioOwner.WakeWordListener, true)
            updateStatus("正在启动唤醒监听", listening = false)
        }.onFailure {
            markRecognitionInactive()
            WjxFileLogger.w(TAG, "startListening failed: ${it.message}", it)
            scheduleRestart(nextRecognizerRetryDelay(), "语音识别启动失败，正在重试")
        }
    }

    private fun createRecognizer(): SpeechRecognizer? = runCatching {
        val useOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            allowOnDeviceRecognizer &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)
        usingOnDeviceRecognizer = useOnDevice
        val defaultRecognizerAvailable = !useOnDevice &&
            runCatching { SpeechRecognizer.isRecognitionAvailable(this) }.getOrDefault(false)
        val explicitRecognizer = if (useOnDevice || defaultRecognizerAvailable) {
            null
        } else {
            XiaozhiRecognitionServiceResolver.resolve(this)
        }
        val next = when {
            useOnDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            defaultRecognizerAvailable -> SpeechRecognizer.createSpeechRecognizer(this)
            explicitRecognizer != null -> SpeechRecognizer.createSpeechRecognizer(this, explicitRecognizer.component)
            else -> error("车机没有可绑定的语音识别服务")
        }
        recognitionEngineLabel = when {
            useOnDevice -> "系统离线语音识别"
            explicitRecognizer != null -> explicitRecognizer.engineLabel
            else -> "系统语音识别"
        }
        next.setRecognitionListener(this)
        recognizer = next
        controller.reportService(
            running = true,
            listening = false,
            status = "正在准备语音唤醒",
            engineLabel = recognitionEngineLabel,
            available = true,
        )
        next
    }.onFailure {
        WjxFileLogger.w(TAG, "create recognizer failed: ${it.message}", it)
    }.getOrNull()

    private fun handleResults(bundle: Bundle?): Boolean {
        if (wakeTriggered || !canListen()) return false
        val candidates = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        candidates.firstOrNull()?.takeIf { it.isNotBlank() }?.let(controller::reportRecognition)
        val matched = selectWakeMatch(
            candidates = candidates,
            confidenceScores = bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
            phrase = gate.phrase,
        ) ?: return false
        triggerWake(matched)
        return true
    }

    private fun triggerWake(recognizedText: String) {
        if (wakeTriggered) return
        wakeTriggered = true
        restartJob?.cancel()
        pauseRecognition()
        controller.reportWake(recognizedText)
        updateNotification("已唤醒，正在开始对话")
        feedbackPlayer.playWakeTone()
        wakeJob?.cancel()
        wakeJob = lifecycleScope.launch {
            sessionManager.startWakeConversation()
            delay(WAKE_GUARD_MS)
            wakeTriggered = false
            reconcileListening()
        }
        WjxFileLogger.i(TAG, "wake matched phrase=${gate.phrase} recognized=$recognizedText")
    }

    private fun pauseRecognition(destroy: Boolean = false) {
        restartJob?.cancel()
        restartJob = null
        val active = recognizer
        markRecognitionInactive()
        runCatching { active?.cancel() }
        if (destroy) {
            runCatching { active?.destroy() }
            recognizer = null
            usingOnDeviceRecognizer = false
        }
    }

    private fun canListen(): Boolean = gate.enabled &&
        gate.serverConfigured &&
        gate.conversationIdle &&
        !gate.microphoneBusy &&
        !wakeTriggered

    private fun updateStatus(status: String, listening: Boolean, available: Boolean = true) {
        controller.reportService(
            running = true,
            listening = listening,
            status = status,
            engineLabel = recognitionEngineLabel,
            available = available,
        )
        updateNotification(status)
    }

    private fun markRecognitionInactive() {
        recognizerActive = false
        audioUsage.setActive(AudioOwner.WakeWordListener, false)
    }

    private fun nextRecognizerRetryDelay(): Long {
        recognizerFailureCount = (recognizerFailureCount + 1).coerceAtMost(MAX_RECOGNIZER_FAILURES)
        return exponentialBackoffDelay(
            attempt = recognizerFailureCount,
            baseMs = BASE_RECOGNIZER_RETRY_MS,
            maxMs = MAX_RECOGNIZER_RETRY_MS,
        )
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "小智语音唤醒",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "后台等待自定义唤醒词"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disablePendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, XiaozhiWakeWordService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("超级玩具箱 · 小智唤醒")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "关闭唤醒", disablePendingIntent)
            .build()
    }

    private data class WakeListeningGate(
        val enabled: Boolean = false,
        val phrase: String = XiaozhiWakeConfig.DEFAULT_WAKE_PHRASE,
        val serverConfigured: Boolean = false,
        val conversationIdle: Boolean = false,
        val microphoneBusy: Boolean = false,
    )

    companion object {
        const val ACTION_RECONCILE = "com.byd.wjx.xiaozhi.wake.RECONCILE"
        private const val ACTION_DISABLE = "com.byd.wjx.xiaozhi.wake.DISABLE"
        private const val CHANNEL_ID = "wjx_xiaozhi_wake"
        private const val NOTIFICATION_ID = 1111
        private const val TAG = "XiaozhiWake"
        private const val RESTART_AFTER_RELEASE_MS = 650L
        private const val WAKE_GUARD_MS = 900L
        private const val BASE_RECOGNIZER_RETRY_MS = 700L
        private const val MAX_RECOGNIZER_RETRY_MS = 30_000L
        private const val MAX_RECOGNIZER_FAILURES = 6
    }
}
