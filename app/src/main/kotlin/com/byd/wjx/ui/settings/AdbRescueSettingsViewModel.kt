package com.byd.wjx.ui.settings

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.adbrescue.AdbRescueManager
import com.byd.wjx.adbrescue.AdbRescuePreferences
import com.byd.wjx.adbrescue.AdbRescueReport
import com.byd.wjx.adbrescue.AdbRescueRuntimeCoordinator
import com.byd.wjx.adbrescue.AdbRescueScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

data class AdbRescueSettingsUiState(
    val report: AdbRescueReport? = null,
    val guardEnabled: Boolean = false,
    val busy: Boolean = false,
    val rescueRunning: Boolean = false,
    val rescueAttempt: Int = 0,
    val rescueRemainingSeconds: Int = RESCUE_DURATION_SECONDS,
    val diagnosticProgressWritten: Boolean? = null,
    val message: String? = null,
)

@HiltViewModel
class AdbRescueSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: AdbRescueManager,
    private val runtimeCoordinator: AdbRescueRuntimeCoordinator,
    private val preferences: AdbRescuePreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AdbRescueSettingsUiState(guardEnabled = preferences.isGuardEnabled()),
    )
    val uiState: StateFlow<AdbRescueSettingsUiState> = _uiState.asStateFlow()
    private var rescueJob: Job? = null
    internal var elapsedRealtime: () -> Long = SystemClock::elapsedRealtime

    fun refresh() {
        if (_uiState.value.busy || _uiState.value.rescueRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            try {
                val report = withContext(Dispatchers.IO) {
                    manager.inspect("MANUAL_REFRESH", runWriteTest = false)
                }
                _uiState.update { it.copy(report = report, busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(busy = false, message = "读取 ADB 状态失败：${error.message.orEmpty()}")
                }
            }
        }
    }

    fun enableBoth() {
        if (_uiState.value.busy || _uiState.value.rescueRunning) return
        viewModelScope.launch {
            if (!setGuardPreference(true)) return@launch
            _uiState.update { it.copy(busy = true, message = "正在授权并检查两种 ADB 模式") }
            try {
                val outcome = runtimeCoordinator.ensureEnabled("MANUAL_ENABLE_BOTH")
                val message = when {
                    outcome.report.allModesEnabled && outcome.report.writeSecureSettingsGranted ->
                        "两种 ADB 模式与开机自动守护均已开启"
                    outcome.report.allModesEnabled ->
                        "两种 ADB 模式已开启，但 WRITE_SECURE_SETTINGS 尚未授予"
                    !outcome.helperAvailable ->
                        "自动守护已开启，但 Helper 暂不可用，未能完成系统修复"
                    else -> "自动守护已开启，部分模式未生效，请查看写入回读结果"
                }
                _uiState.update {
                    it.copy(report = outcome.report, busy = false, message = message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(busy = false, message = "开启 ADB 失败：${error.message.orEmpty()}")
                }
            }
        }
    }

    fun setGuardEnabled(enabled: Boolean) {
        if (_uiState.value.busy || _uiState.value.rescueRunning) return
        if (enabled) {
            enableBoth()
            return
        }
        if (!setGuardPreference(false)) return
        AdbRescueScheduler.cancelAll(context)
        _uiState.update {
            it.copy(message = "开机自动守护已关闭，当前 ADB 状态保持不变")
        }
    }

    fun disableBoth() {
        if (_uiState.value.busy || _uiState.value.rescueRunning) return
        viewModelScope.launch {
            if (!setGuardPreference(false)) return@launch
            AdbRescueScheduler.cancelAll(context)
            _uiState.update { it.copy(busy = true, message = "正在关闭两种 ADB 模式") }
            try {
                val report = runtimeCoordinator.disableBoth("MANUAL_DISABLE_BOTH")
                _uiState.update {
                    it.copy(
                        report = report,
                        busy = false,
                        message = if (report.allModesDisabled) {
                            "两种 ADB 模式与开机自动守护均已关闭"
                        } else {
                            "自动守护已关闭，但部分 ADB 状态未能关闭，请查看回读结果"
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(busy = false, message = "关闭 ADB 失败：${error.message.orEmpty()}")
                }
            }
        }
    }

    fun startRescue() {
        if (rescueJob?.isActive == true || _uiState.value.busy) return
        rescueJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    rescueRunning = true,
                    rescueAttempt = 0,
                    rescueRemainingSeconds = RESCUE_DURATION_SECONDS,
                    diagnosticProgressWritten = null,
                    message = "三分钟 ADB 救援已启动",
                )
            }
            val deadlineMs = elapsedRealtime() + RESCUE_DURATION_MS
            var attempt = 0
            var lastError: Throwable? = null
            try {
                while (isActive) {
                    val remainingMs = deadlineMs - elapsedRealtime()
                    if (remainingMs <= 0L) break
                    attempt += 1
                    val remainingSeconds = remainingSeconds(deadlineMs, elapsedRealtime())
                    try {
                        val outcome = withTimeout(remainingMs) {
                            runtimeCoordinator.ensureEnabled("RESCUE_TICK_$attempt")
                        }
                        val diagnostics = withContext(Dispatchers.IO) {
                            manager.recordRescueProgress(outcome.report, attempt, remainingSeconds)
                        }
                        lastError = null
                        _uiState.update {
                            it.copy(
                                report = outcome.report,
                                rescueAttempt = attempt,
                                rescueRemainingSeconds = remainingSeconds,
                                diagnosticProgressWritten = diagnostics.allWritten,
                                message = if (outcome.report.allModesEnabled) {
                                    "救援运行中，两种 ADB 模式当前正常"
                                } else {
                                    "救援运行中，正在等待系统服务允许写入"
                                },
                            )
                        }
                    } catch (_: TimeoutCancellationException) {
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        lastError = error
                        manager.recordEvent("RESCUE_TICK_FAILED:$attempt:${error.message.orEmpty()}")
                        _uiState.update {
                            it.copy(
                                rescueAttempt = attempt,
                                rescueRemainingSeconds = remainingSeconds,
                                message = "第 $attempt 次救援失败，将继续重试：${error.message.orEmpty()}",
                            )
                        }
                    }
                    val delayMs = (deadlineMs - elapsedRealtime()).coerceAtMost(1_000L)
                    if (delayMs > 0L) delay(delayMs)
                }
                _uiState.update {
                    it.copy(
                        rescueRunning = false,
                        rescueRemainingSeconds = 0,
                        message = lastError?.let { error ->
                            "三分钟 ADB 救援已结束，最后一次失败：${error.message.orEmpty()}"
                        } ?: "三分钟 ADB 救援已完成",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                rescueJob = null
                if (_uiState.value.rescueRunning) {
                    _uiState.update { it.copy(rescueRunning = false) }
                }
            }
        }
    }

    fun stopRescue(reason: String = "manual") {
        val wasRunning = rescueJob?.isActive == true
        rescueJob?.cancel()
        rescueJob = null
        if (wasRunning) {
            manager.recordEvent("RESCUE_STOPPED:$reason")
            _uiState.update { it.copy(rescueRunning = false, message = "ADB 救援已停止") }
        }
    }

    override fun onCleared() {
        stopRescue("view_model_cleared")
        super.onCleared()
    }

    private fun setGuardPreference(enabled: Boolean): Boolean {
        if (!preferences.setGuardEnabled(enabled)) {
            _uiState.update { it.copy(message = "ADB 自动守护设置保存失败") }
            return false
        }
        _uiState.update { it.copy(guardEnabled = enabled) }
        return true
    }
}

internal fun remainingSeconds(deadlineMs: Long, nowMs: Long): Int =
    ceil(((deadlineMs - nowMs).coerceAtLeast(0L)) / 1_000.0).toInt()

private const val RESCUE_DURATION_SECONDS = 180
private const val RESCUE_DURATION_MS = RESCUE_DURATION_SECONDS * 1_000L
