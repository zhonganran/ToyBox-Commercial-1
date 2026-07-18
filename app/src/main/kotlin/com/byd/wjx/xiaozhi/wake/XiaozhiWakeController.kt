package com.byd.wjx.xiaozhi.wake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    private val wakeEngine: SherpaOnnxWakeWordEngine,
    private val phraseCompiler: XiaozhiWakePhraseCompiler,
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
        val compiled = phraseCompiler.compile(phrase.trim()).getOrElse { error ->
            return error.message ?: "唤醒词无法用于本地检测"
        }
        preferences.save(enabled, compiled.displayPhrase)
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
        val engineError = wakeEngine.availabilityError()
        val engineAvailable = engineError == null
        val engineLabel = XiaozhiWakeWordService.ENGINE_LABEL
        _state.update {
            it.copy(
                engineAvailable = engineAvailable,
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
        phraseCompiler.validationError(config.phrase)?.let { phraseError ->
            cancelRetry(resetAttempts = true)
            appContext.stopService(Intent(appContext, XiaozhiWakeWordService::class.java))
            reportService(false, false, phraseError, engineLabel = engineLabel, available = false)
            return
        }
        if (!engineAvailable) {
            cancelRetry(resetAttempts = true)
            appContext.stopService(Intent(appContext, XiaozhiWakeWordService::class.java))
            reportService(
                running = false,
                listening = false,
                status = engineError ?: "本地唤醒引擎不可用",
                engineLabel = engineLabel,
                available = false,
            )
            WjxFileLogger.w(TAG, "wake disabled: local engine unavailable reason=$reason error=$engineError")
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
        available: Boolean = _state.value.engineAvailable,
    ) {
        if (running) cancelRetry(resetAttempts = true)
        _state.update {
            it.copy(
                serviceRunning = running,
                listening = listening,
                engineAvailable = available,
                engineLabel = engineLabel,
                statusText = status,
            )
        }
    }

    fun reportListening(listening: Boolean) {
        _state.update { it.copy(listening = listening) }
    }

    fun reportWake(text: String) {
        _state.update {
            it.copy(
                listening = false,
                statusText = "已在本地检测到“${it.config.phrase}”，正在开始对话",
                lastDetectedPhrase = text,
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
