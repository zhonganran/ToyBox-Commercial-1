package com.byd.wjx.ui.settings

import android.content.Context
import android.os.Build
import com.byd.wjx.R
import com.byd.wjx.data.charging.CatchUpJournal
import com.byd.wjx.data.charging.ChargingStateStore
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SettingsLogRecordingState(
    val isRecording: Boolean = false,
    val status: String? = null,
)

/** Owns the logcat process, file pipe, timeout, and diagnostic header lifecycle. */
class SettingsLogRecorder @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val chargingStateStore: ChargingStateStore,
    private val catchUpJournal: CatchUpJournal,
) {
    private val recorderJob = SupervisorJob()
    private val scope = CoroutineScope(recorderJob + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val _state = MutableStateFlow(SettingsLogRecordingState())

    val state: StateFlow<SettingsLogRecordingState> = _state.asStateFlow()

    @Volatile
    private var logProcess: Process? = null
    private var pipeJob: Job? = null
    private var autoStopJob: Job? = null

    fun start() {
        scope.launch {
            lifecycleMutex.withLock {
                if (logProcess != null) return@withLock
                try {
                    if (!WjxDirectories.ensureBaseDirs()) {
                        _state.value = SettingsLogRecordingState(
                            status = appContext.getString(R.string.settings_log_error_no_fs_access),
                        )
                        return@withLock
                    }

                    val target = WjxDirectories.appLog
                    appendDiagnosticHeader()
                    WjxFileLogger.i(TAG, "logcat recording start target=${WjxFileLogger.logFilePath}")
                    Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()

                    val process = Runtime.getRuntime().exec(
                        arrayOf("logcat", "-v", "time", "--pid=${android.os.Process.myPid()}"),
                    )
                    logProcess = process
                    pipeJob = scope.launch { pipeLogcat(process, target) }
                    autoStopJob = scope.launch {
                        delay(LOG_MAX_DURATION_MS)
                        WjxFileLogger.appendExternalLine("--- 日志已自动停止：达到 2 小时时长上限 ---")
                        stopInternal()
                    }
                    _state.value = SettingsLogRecordingState(
                        isRecording = true,
                        status = appContext.getString(
                            R.string.settings_log_recording_started,
                            WjxFileLogger.logFilePath,
                        ),
                    )
                } catch (error: Exception) {
                    cleanupProcessLocked()
                    WjxFileLogger.e(TAG, "startLogRecording failed", error)
                    _state.value = SettingsLogRecordingState(
                        status = appContext.getString(
                            R.string.settings_error_with_message,
                            error.message ?: "?",
                        ),
                    )
                }
            }
        }
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    fun close() {
        logProcess?.destroy()
        logProcess = null
        WjxFileLogger.flush()
        scope.cancel()
    }

    private suspend fun stopInternal() {
        lifecycleMutex.withLock {
            val wasRecording = logProcess != null || _state.value.isRecording
            cleanupProcessLocked()
            if (!wasRecording) return@withLock

            try {
                val file = WjxDirectories.appLog
                WjxFileLogger.flush()
                val sizeKb = file.length() / 1024
                WjxFileLogger.i(TAG, "logcat recording stop target=${file.absolutePath} sizeKb=$sizeKb")
                _state.value = SettingsLogRecordingState(
                    status = appContext.getString(
                        R.string.settings_log_saved,
                        file.absolutePath,
                        sizeKb,
                    ),
                )
            } catch (error: Exception) {
                WjxFileLogger.e(TAG, "stopLogRecording failed", error)
                _state.value = SettingsLogRecordingState(
                    status = appContext.getString(
                        R.string.settings_error_with_message,
                        error.message ?: "?",
                    ),
                )
            }
        }
    }

    private fun cleanupProcessLocked() {
        autoStopJob?.cancel()
        autoStopJob = null
        logProcess?.destroy()
        logProcess = null
        pipeJob?.cancel()
        pipeJob = null
    }

    private suspend fun pipeLogcat(process: Process, target: File) = withContext(Dispatchers.IO) {
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (isActive && logProcess === process) {
                    val line = reader.readLine() ?: break
                    if (target.length() >= LOG_MAX_SIZE_BYTES) {
                        WjxFileLogger.appendExternalLine(
                            "--- 日志已停止：文件大小达到 $LOG_MAX_SIZE_MB MB 上限 ---",
                        )
                        scope.launch { stopInternal() }
                        break
                    }
                    WjxFileLogger.appendExternalLine(line)
                }
            }
        } catch (error: Exception) {
            if (logProcess === process) {
                WjxFileLogger.w(TAG, "logcat pipe stopped: ${error.message}")
                scope.launch { stopInternal() }
            }
        } finally {
            if (logProcess === process) {
                scope.launch { stopInternal() }
            }
        }
    }

    /** Adds the app, device, settings, charging recovery, and vehicle-source context. */
    private suspend fun appendDiagnosticHeader() = withContext(Dispatchers.IO) {
        val header = buildString {
            appendLine("=== 超级玩具箱诊断信息 ===")
            try {
                val packageName = appContext.packageName
                val packageInfo = appContext.packageManager.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName ?: "?"
                val versionCode = packageInfo.longVersionCode.toString()
                appendLine("时间：${formatTimestamp(System.currentTimeMillis())}")
                appendLine("应用：$packageName $versionName（版本号=$versionCode）")
                appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("系统：${Build.VERSION.RELEASE}（接口级别 ${Build.VERSION.SDK_INT}）")
                appendLine("系统指纹：${Build.FINGERPRINT}")
                appendLine("系统语言：${Locale.getDefault().toLanguageTag()}")
            } catch (error: Exception) {
                appendLine("（获取应用和设备信息失败：${error.message}）")
            }

            appendLine("--- 设置 ---")
            try {
                val capacityRaw = settingsRepository.getString(
                    SettingsRepository.KEY_BATTERY_CAPACITY,
                    "",
                )
                val fuelTankCapacityRaw = settingsRepository.getString(
                    SettingsRepository.KEY_FUEL_TANK_CAPACITY,
                    "",
                )
                appendLine("数据来源：比亚迪能耗数据库")
                appendLine(
                    "电池容量：原始=\"$capacityRaw\"，解析=${settingsRepository.getBatteryCapacity()}",
                )
                appendLine(
                    "油箱容量：原始=\"$fuelTankCapacityRaw\"，解析=${settingsRepository.getFuelTankCapacity()}",
                )
                appendLine("界面主题：${settingsRepository.getThemeMode()}")
            } catch (error: Exception) {
                appendLine("（获取设置失败：${error.message}）")
            }

            appendLine("--- 充电补录 ---")
            try {
                val anchor = chargingStateStore.load()
                val anchorTimestamp = if (anchor.ts > 0L) formatTimestamp(anchor.ts) else "未设置"
                appendLine(
                    "锚点：电量=${anchor.socPercent}，里程=${anchor.mileageKm}，" +
                        "时间=$anchorTimestamp，待补录=${yesNo(chargingStateStore.loadChargePending())}",
                )
                appendLine("补录日志：")
                val journal = catchUpJournal.read()
                if (journal.isBlank()) {
                    appendLine("  （空）")
                } else {
                    journal.lineSequence().forEach { appendLine("  $it") }
                }
            } catch (error: Exception) {
                appendLine("（获取充电补录状态失败：${error.message}）")
            }

            appendLine("--- 车辆数据来源 ---")
            try {
                val energyDirectory = File("/storage/emulated/0/energydata")
                appendLine(
                    "能耗目录：存在=${yesNo(energyDirectory.exists())}，" +
                        "是目录=${yesNo(energyDirectory.isDirectory)}",
                )
                if (energyDirectory.isDirectory) {
                    val files = energyDirectory.listFiles()
                    if (files == null) {
                        appendLine("  读取文件列表：无结果（可能无权限）")
                    } else {
                        files.forEach {
                            appendLine("  ${it.name}（${it.length()} 字节，修改时间=${it.lastModified()}）")
                        }
                    }
                }
            } catch (error: Exception) {
                appendLine("（获取车辆数据来源失败：${error.message}）")
            }
            appendLine("===============================")
            appendLine()
        }

        WjxFileLogger.appendSection("诊断信息", header)
    }

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))

    private fun yesNo(value: Boolean): String = if (value) "是" else "否"

    private companion object {
        const val TAG = "SettingsLogRecorder"
        const val LOG_MAX_DURATION_MS = 2 * 60 * 60 * 1000L
        const val LOG_MAX_SIZE_BYTES = WjxFileLogger.MAX_LOG_BYTES
        const val LOG_MAX_SIZE_MB = LOG_MAX_SIZE_BYTES / 1024 / 1024
    }
}
