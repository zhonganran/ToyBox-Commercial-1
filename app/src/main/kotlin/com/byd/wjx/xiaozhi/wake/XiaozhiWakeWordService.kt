package com.byd.wjx.xiaozhi.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioRecord
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
import com.byd.wjx.xiaozhi.audio.XiaozhiMicrophoneFactory
import com.byd.wjx.xiaozhi.domain.XiaozhiPhase
import com.byd.wjx.xiaozhi.domain.XiaozhiSessionManager
import com.byd.wjx.xiaozhi.service.XiaozhiOverlayPermissionCoordinator
import com.byd.wjx.xiaozhi.service.XiaozhiStatusOverlayController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Low-power, fully local wake-word service.
 *
 * It owns the microphone only while Xiaozhi and realtime hailing are idle. A
 * keyword match always closes AudioRecord before the cloud conversation starts,
 * so the two capture pipelines never compete for the BYD cabin microphone.
 */
@AndroidEntryPoint
class XiaozhiWakeWordService : LifecycleService() {
    @Inject lateinit var preferences: XiaozhiWakePreferences
    @Inject lateinit var controller: XiaozhiWakeController
    @Inject lateinit var sessionManager: XiaozhiSessionManager
    @Inject lateinit var audioUsage: AudioUsageCoordinator
    @Inject lateinit var feedbackPlayer: XiaozhiFeedbackPlayer
    @Inject lateinit var microphoneFactory: XiaozhiMicrophoneFactory
    @Inject lateinit var wakeEngine: SherpaOnnxWakeWordEngine
    @Inject lateinit var statusOverlay: XiaozhiStatusOverlayController
    @Inject lateinit var overlayPermission: XiaozhiOverlayPermissionCoordinator

    private val recorderLock = Any()
    private var activeRecorder: AudioRecord? = null
    private var restartJob: Job? = null
    private var listeningJob: Job? = null
    private var wakeJob: Job? = null
    private var listeningGeneration = 0L
    private var failureCount = 0

    @Volatile private var destroyed = false
    @Volatile private var gate = WakeListeningGate()
    @Volatile private var activePhrase = ""
    @Volatile private var wakeTriggered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("正在加载本地唤醒模型"))
        controller.reportService(
            running = true,
            listening = false,
            status = "正在加载本地唤醒模型",
            engineLabel = ENGINE_LABEL,
        )
        statusOverlay.updateWake(
            running = true,
            status = "正在加载本地唤醒模型",
        )
        lifecycleScope.launch {
            overlayPermission.ensureGranted()
            statusOverlay.refresh()
        }
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
            ACTION_RECONCILE, null -> Unit
        }
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        restartJob?.cancel()
        wakeJob?.cancel()
        stopListening()
        wakeEngine.release()
        statusOverlay.clearWake()
        controller.reportService(
            running = false,
            listening = false,
            status = if (preferences.config.value.enabled) "本地唤醒服务已停止" else "本地唤醒未开启",
            engineLabel = ENGINE_LABEL,
        )
        super.onDestroy()
    }

    private fun reconcileListening() {
        when {
            destroyed -> Unit
            !gate.enabled -> stopSelf()
            !gate.serverConfigured -> pauseListening("请先完成小智服务配置")
            !gate.conversationIdle -> pauseListening("小智正在对话，本地唤醒监听已暂停")
            gate.microphoneBusy -> pauseListening("其他麦克风功能正在使用，唤醒监听已让行")
            wakeTriggered -> Unit
            listeningJob?.isActive == true && activePhrase == gate.phrase -> Unit
            else -> {
                stopListening()
                scheduleStart(RESTART_AFTER_RELEASE_MS)
            }
        }
    }

    private fun pauseListening(status: String) {
        stopListening()
        updateStatus(status, listening = false)
    }

    private fun scheduleStart(delayMs: Long, status: String? = null) {
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
        if (!canListen() || listeningJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("需要麦克风权限才能开启本地唤醒", listening = false)
            return
        }

        val phrase = gate.phrase
        val generation = ++listeningGeneration
        activePhrase = phrase
        updateStatus("正在加载本地唤醒模型", listening = false)
        listeningJob = lifecycleScope.launch(Dispatchers.IO) {
            runListeningSession(generation, phrase)
        }
    }

    private suspend fun runListeningSession(generation: Long, phrase: String) {
        var recorder: AudioRecord? = null
        var detectedPhrase: String? = null
        var failure: Throwable? = null
        var cancelled = false
        var listeningStarted = false
        try {
            val compiled = wakeEngine.prepare(phrase).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (!canListenFor(phrase)) throw CancellationException("wake listening gate closed")

            recorder = microphoneFactory.create(
                sampleRate = SherpaOnnxWakeWordEngine.SAMPLE_RATE,
                frameSamples = SherpaOnnxWakeWordEngine.FRAME_SAMPLES,
                purpose = "local_wake_word",
            ) ?: error("无法打开车内降噪麦克风")

            val registered = synchronized(recorderLock) {
                if (!canListenFor(phrase)) {
                    false
                } else {
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        "车内麦克风未能开始录音"
                    }
                    activeRecorder = recorder
                    audioUsage.setActive(AudioOwner.WakeWordListener, true)
                    true
                }
            }
            if (!registered) throw CancellationException("wake listening gate closed")
            listeningStarted = true

            withContext(Dispatchers.Main.immediate) {
                if (generation == listeningGeneration && canListenFor(phrase)) {
                    failureCount = 0
                    updateStatus("本地监听中，等待“${compiled.displayPhrase}”", listening = true)
                }
            }

            val samples = ShortArray(SherpaOnnxWakeWordEngine.FRAME_SAMPLES)
            while (currentCoroutineContext().isActive && canListenFor(phrase)) {
                val count = recorder.read(
                    samples,
                    0,
                    samples.size,
                    AudioRecord.READ_BLOCKING,
                )
                when {
                    count > 0 -> {
                        val detected = wakeEngine.accept(samples, count)
                        if (detected != null) {
                            detectedPhrase = detected
                            break
                        }
                    }
                    count == 0 -> delay(5L)
                    else -> {
                        currentCoroutineContext().ensureActive()
                        error("麦克风读取失败：$count")
                    }
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        } catch (error: Throwable) {
            failure = error
            WjxFileLogger.w(TAG, "local wake listener failed: ${error.message}", error)
        } finally {
            releaseRegisteredRecorder(recorder)
            wakeEngine.stopStream()
        }

        withContext(NonCancellable + Dispatchers.Main.immediate) {
            onListeningFinished(
                generation = generation,
                phrase = phrase,
                detectedPhrase = detectedPhrase,
                failure = failure,
                cancelled = cancelled,
                listeningStarted = listeningStarted,
            )
        }
    }

    private fun onListeningFinished(
        generation: Long,
        phrase: String,
        detectedPhrase: String?,
        failure: Throwable?,
        cancelled: Boolean,
        listeningStarted: Boolean,
    ) {
        if (generation != listeningGeneration) return
        listeningJob = null
        activePhrase = ""
        if (destroyed) return

        when {
            detectedPhrase != null && canListenFor(phrase) -> triggerWake(detectedPhrase)
            failure != null && canListenFor(phrase) -> {
                val retryDelay = nextRetryDelay()
                val stage = if (listeningStarted) "本地监听中断" else "本地唤醒启动失败"
                scheduleStart(retryDelay, "$stage，正在自动恢复：${failure.message.orEmpty()}")
            }
            !cancelled -> reconcileListening()
        }
    }

    private fun triggerWake(detectedPhrase: String) {
        if (wakeTriggered || !canListen()) return
        wakeTriggered = true
        restartJob?.cancel()
        controller.reportWake(detectedPhrase)
        statusOverlay.updateWake(
            running = true,
            status = "已唤醒，正在打开自动对话",
        )
        updateNotification("已在本地检测到唤醒词，正在开始对话")
        feedbackPlayer.playWakeTone()
        wakeJob?.cancel()
        wakeJob = lifecycleScope.launch {
            sessionManager.startWakeConversation()
            delay(WAKE_GUARD_MS)
            wakeTriggered = false
            reconcileListening()
        }
        WjxFileLogger.i(TAG, "local wake matched phrase=${gate.phrase}")
    }

    private fun stopListening() {
        restartJob?.cancel()
        restartJob = null
        listeningJob?.cancel()
        stopActiveRecorder()
        wakeEngine.stopStream()
        controller.reportListening(false)
    }

    private fun stopActiveRecorder() {
        val recorder = synchronized(recorderLock) {
            activeRecorder.also { activeRecorder = null }
        } ?: return
        releaseRecorder(recorder)
        audioUsage.setActive(AudioOwner.WakeWordListener, false)
    }

    private fun releaseRegisteredRecorder(recorder: AudioRecord?) {
        if (recorder == null) return
        val ownsRecorder = synchronized(recorderLock) {
            if (activeRecorder === recorder) {
                activeRecorder = null
                true
            } else {
                false
            }
        }
        if (ownsRecorder) {
            releaseRecorder(recorder)
            audioUsage.setActive(AudioOwner.WakeWordListener, false)
        } else if (recorder.state == AudioRecord.STATE_INITIALIZED) {
            releaseRecorder(recorder)
        }
    }

    private fun releaseRecorder(recorder: AudioRecord) {
        runCatching {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        }
        runCatching { recorder.release() }
    }

    private fun canListenFor(phrase: String): Boolean = canListen() && gate.phrase == phrase

    private fun canListen(): Boolean = !destroyed &&
        gate.enabled &&
        gate.serverConfigured &&
        gate.conversationIdle &&
        !gate.microphoneBusy &&
        !wakeTriggered

    private fun updateStatus(status: String, listening: Boolean) {
        controller.reportService(
            running = true,
            listening = listening,
            status = status,
            engineLabel = ENGINE_LABEL,
            available = true,
        )
        statusOverlay.updateWake(
            running = true,
            status = status,
        )
        updateNotification(status)
    }

    private fun nextRetryDelay(): Long {
        failureCount = (failureCount + 1).coerceAtMost(MAX_FAILURES)
        return exponentialBackoffDelay(
            attempt = failureCount,
            baseMs = BASE_RETRY_MS,
            maxMs = MAX_RETRY_MS,
        )
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "小智本地唤醒",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "在车机本地等待自定义中文唤醒词"
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
            .setContentTitle("超级玩具箱 · 小智本地唤醒")
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
        const val ENGINE_LABEL = "Sherpa-ONNX 本地唤醒"
        private const val ACTION_DISABLE = "com.byd.wjx.xiaozhi.wake.DISABLE"
        private const val CHANNEL_ID = "wjx_xiaozhi_wake"
        private const val NOTIFICATION_ID = 1111
        private const val TAG = "XiaozhiWake"
        private const val RESTART_AFTER_RELEASE_MS = 350L
        private const val WAKE_GUARD_MS = 900L
        private const val BASE_RETRY_MS = 1_000L
        private const val MAX_RETRY_MS = 30_000L
        private const val MAX_FAILURES = 6
    }
}
