package com.byd.wjx.xiaozhi.wake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class XiaozhiWakeController @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: XiaozhiWakePreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(XiaozhiWakeRuntimeState(config = preferences.config.value))
    val state: StateFlow<XiaozhiWakeRuntimeState> = _state.asStateFlow()
    private val retryLock = Any()
    private var retryJob: Job? = null
    private var retryAttempt = 0

    init {
        applicationScope.launch {
            preferences.config.collect { config ->
                _state.update { current ->
                    current.copy(
                        config = config,
                        statusText = if (!config.enabled) "语音唤醒未开启" else current.statusText,
                    )
                }
            }
        }
    }

    fun update(enabled: Boolean, phrase: String): String? {
        validateWakePhrase(phrase)?.let { return it }
        preferences.save(enabled, phrase)
        reconcile("settings_update")
        return null
    }

    fun setEnabled(enabled: Boolean): String? = update(enabled, preferences.config.value.phrase)

    fun reconcile(reason: String) {
        cancelRetry(resetAttempts = true)
        attemptStart(reason)
    }

    private fun attemptStart(reason: String) {
        val config = preferences.config.value
        val permissionGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val defaultRecognizerAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }
            .getOrDefault(false)
        val explicitRecognizer = if (defaultRecognizerAvailable) {
            null
        } else {
            XiaozhiRecognitionServiceResolver.resolve(appContext)
        }
        val recognitionAvailable = defaultRecognizerAvailable || explicitRecognizer != null
        val engineLabel = explicitRecognizer?.engineLabel
            ?: if (defaultRecognizerAvailable) "系统语音识别" else "未检测到语音识别引擎"
        _state.update {
            it.copy(
                recognitionAvailable = recognitionAvailable,
                engineLabel = engineLabel,
            )
        }
        if (!config.enabled) {
            cancelRetry(resetAttempts = true)
            appContext.stopService(Intent(appContext, XiaozhiWakeWordService::class.java))
            reportService(false, false, "语音唤醒未开启")
            return
        }
        if (!permissionGranted) {
            cancelRetry(resetAttempts = false)
            reportService(false, false, "需要麦克风权限才能开启语音唤醒")
            return
        }
        if (!recognitionAvailable) {
            cancelRetry(resetAttempts = true)
            appContext.stopService(Intent(appContext, XiaozhiWakeWordService::class.java))
            reportService(
                running = false,
                listening = false,
                status = "车机没有可用的语音识别服务；按键和页面对话不受影响",
                engineLabel = engineLabel,
                available = false,
            )
            WjxFileLogger.w(TAG, "wake disabled: no RecognitionService reason=$reason")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, XiaozhiWakeWordService::class.java).setAction(XiaozhiWakeWordService.ACTION_RECONCILE),
            )
        }.onFailure {
            reportService(false, false, "语音唤醒服务启动失败：${it.message.orEmpty()}")
            WjxFileLogger.w(TAG, "start failed reason=$reason message=${it.message}", it)
            scheduleRetry("start_failed")
        }.onSuccess {
            WjxFileLogger.i(
                TAG,
                "start requested reason=$reason phrase=${config.phrase} engine=$engineLabel",
            )
            scheduleVerification()
        }
    }

    fun reportService(
        running: Boolean,
        listening: Boolean,
        status: String,
        engineLabel: String = _state.value.engineLabel,
        available: Boolean = _state.value.recognitionAvailable,
    ) {
        if (running) cancelRetry(resetAttempts = true)
        _state.update {
            it.copy(
                serviceRunning = running,
                listening = listening,
                recognitionAvailable = available,
                engineLabel = engineLabel,
                statusText = status,
            )
        }
    }

    fun reportRecognition(text: String) {
        _state.update { it.copy(lastRecognizedText = text) }
    }

    fun reportWake(text: String) {
        _state.update {
            it.copy(
                listening = false,
                statusText = "已听到“${it.config.phrase}”，正在开始对话",
                lastRecognizedText = text,
                lastWakeAtMs = System.currentTimeMillis(),
            )
        }
    }

    private fun scheduleVerification() {
        synchronized(retryLock) {
            retryJob?.cancel()
            retryJob = applicationScope.launch {
                delay(START_VERIFICATION_MS)
                synchronized(retryLock) { retryJob = null }
                if (preferences.config.value.enabled && !_state.value.serviceRunning) {
                    scheduleRetry("start_not_confirmed")
                }
            }
        }
    }

    private fun scheduleRetry(reason: String) {
        val delayMs: Long
        synchronized(retryLock) {
            if (retryJob?.isActive == true || !preferences.config.value.enabled) return
            retryAttempt = (retryAttempt + 1).coerceAtMost(MAX_RETRY_ATTEMPTS)
            delayMs = exponentialBackoffDelay(retryAttempt, BASE_RETRY_MS, MAX_RETRY_MS)
            retryJob = applicationScope.launch {
                delay(delayMs)
                synchronized(retryLock) { retryJob = null }
                attemptStart("retry:$reason")
            }
        }
        WjxFileLogger.i(TAG, "retry scheduled reason=$reason delay=${delayMs}ms attempt=$retryAttempt")
    }

    private fun cancelRetry(resetAttempts: Boolean) {
        synchronized(retryLock) {
            retryJob?.cancel()
            retryJob = null
            if (resetAttempts) retryAttempt = 0
        }
    }

    companion object {
        private const val TAG = "XiaozhiWake"
        private const val START_VERIFICATION_MS = 4_000L
        private const val BASE_RETRY_MS = 1_000L
        private const val MAX_RETRY_MS = 60_000L
        private const val MAX_RETRY_ATTEMPTS = 7
    }
}
