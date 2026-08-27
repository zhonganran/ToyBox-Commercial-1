package com.byd.wjx.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.byd.wjx.R
import com.byd.wjx.data.backup.BackupDestinationProvider
import com.byd.wjx.data.backup.BackupManager
import com.byd.wjx.data.backup.BackupRestoreException
import com.byd.wjx.data.backup.BackupRestoreNoticeStore
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.security.EndpointPrivacy
import com.byd.wjx.service.BootDiagnosticsStore
import com.byd.wjx.service.UpdateChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/** Owns settings operations that affect the application installation or support workflow. */
@Singleton
class SettingsMaintenanceCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val updateChecker: UpdateChecker,
    private val backupManager: BackupManager,
    private val backupDestinationProvider: BackupDestinationProvider,
    private val logUploader: SettingsLogUploader,
) {
    private val appContext = context.applicationContext
    private var availableUpdate: UpdateChecker.UpdateInfo? = null

    fun appVersion(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    fun isAutoCheckEnabled(): Boolean = UpdateChecker.isAutoCheckEnabled(appContext)

    fun setAutoCheckEnabled(enabled: Boolean) {
        UpdateChecker.setAutoCheckEnabled(appContext, enabled)
    }

    internal fun diagnostics(): SettingsMaintenanceDiagnostics {
        val snapshot = runCatching { BootDiagnosticsStore.read(appContext) }.getOrNull()
        val lastBootInfo = snapshot
            ?.takeIf { it.lastBootTimestamp > 0L }
            ?.let {
                val timestamp = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.US)
                    .format(Date(it.lastBootTimestamp))
                "$timestamp（${formatBootMethod(it.lastBootMethod)}）"
            }
        return SettingsMaintenanceDiagnostics(lastBootInfo = lastBootInfo)
    }

    internal suspend fun checkForUpdate(): UpdateState {
        availableUpdate = null
        return try {
            val update = updateChecker.checkForUpdate(appContext, forceCheck = true)
            if (update == null) {
                UpdateState.UpToDate
            } else {
                availableUpdate = update
                UpdateState.Available(update.version, update.releaseNotes.orEmpty())
            }
        } catch (error: Exception) {
            UpdateState.Error(EndpointPrivacy.userMessage(error, "未知错误"))
        }
    }

    internal fun hasAvailableUpdate(): Boolean = availableUpdate != null

    internal fun clearAvailableUpdate() {
        availableUpdate = null
    }

    internal fun downloadUpdates(): Flow<UpdateState> = channelFlow {
        val update = availableUpdate ?: return@channelFlow
        send(
            UpdateState.Downloading(
                update.version,
                appContext.getString(R.string.update_downloading_start),
            ),
        )
        try {
            val outcome = updateChecker.downloadAndInstall(appContext, update) { progress ->
                trySend(UpdateState.Downloading(update.version, progress))
            }
            if (outcome == UpdateChecker.InstallOutcome.PERMISSION_REQUIRED) {
                send(
                    UpdateState.InstallPermissionRequired(
                        update.version,
                        appContext.getString(R.string.update_install_permission_required),
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            send(UpdateState.Error(EndpointPrivacy.userMessage(error, "下载失败")))
        }
    }

    internal suspend fun continuePendingInstall(): UpdateState {
        val update = availableUpdate ?: return UpdateState.Error("待安装更新已经失效，请重新检查")
        return try {
            if (updateChecker.continuePendingInstall(appContext)) {
                UpdateState.InstallPermissionRequired(
                    update.version,
                    appContext.getString(R.string.update_install_permission_required),
                )
            } else {
                UpdateState.Error("待安装更新已经失效，请重新下载")
            }
        } catch (error: Exception) {
            UpdateState.Error(EndpointPrivacy.userMessage(error, "无法打开安装授权"))
        }
    }

    internal suspend fun uploadLogs(): SettingsMaintenanceResult =
        runCatching { logUploader.upload() }
            .fold(
                onSuccess = { result ->
                    val idLabel = result.id.takeIf { it > 0L }?.let { "，编号 #$it" }.orEmpty()
                    SettingsMaintenanceResult(true, "日志上传成功$idLabel")
                },
                onFailure = { error ->
                    WjxFileLogger.w(TAG, "diagnostic log upload failed", error)
                    SettingsMaintenanceResult(
                        false,
                        "日志上传失败：${EndpointPrivacy.userMessage(error, "网络不可用")}",
                    )
                },
            )

    internal suspend fun exportConfig(): SettingsMaintenanceResult =
        runCatching {
            val destination = withContext(Dispatchers.IO) {
                backupDestinationProvider.create().also(backupManager::export)
            }
            appContext.getString(R.string.settings_config_export_done, destination.absolutePath)
        }.fold(
            onSuccess = { SettingsMaintenanceResult(true, it) },
            onFailure = ::configFailure,
        )

    internal suspend fun restoreConfig(uri: Uri): SettingsMaintenanceResult {
        return try {
            withContext(Dispatchers.IO) { backupManager.restore(uri) }
            BackupRestoreNoticeStore.save(
                appContext,
                "配置恢复完成，WJX 音效会自动重新扫描；请检查系统权限和行车记录仪文件夹授权。",
            )
            restartApp()
            SettingsMaintenanceResult(true, "配置恢复完成，正在重启")
        } catch (error: Exception) {
            if (error is BackupRestoreException && error.restartRequired) {
                BackupRestoreNoticeStore.save(
                    appContext,
                    "配置恢复失败，已自动恢复操作前的数据；请检查系统权限和行车记录仪文件夹授权。",
                )
                restartApp()
            }
            configFailure(error)
        }
    }

    private fun configFailure(error: Throwable) = SettingsMaintenanceResult(
        false,
        appContext.getString(R.string.settings_error_with_message, EndpointPrivacy.userMessage(error, "?")),
    )

    private fun formatBootMethod(method: String): String = when {
        method == "WorkManager" -> "系统调度启动"
        method == "DirectFallback" -> "直接启动"
        method.startsWith("FAILED:") -> "失败：${method.substringAfter("FAILED:").trim()}"
        else -> method
    }

    private fun restartApp() {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        if (intent != null) appContext.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private companion object { const val TAG = "SettingsMaintenance" }
}
