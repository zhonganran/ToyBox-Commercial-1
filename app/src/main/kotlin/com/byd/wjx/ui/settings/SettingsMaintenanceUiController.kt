package com.byd.wjx.ui.settings

import android.content.Context
import android.net.Uri
import com.byd.wjx.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns settings maintenance UI state and the cancellable update download lifecycle. */
internal class SettingsMaintenanceUiController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val coordinator: SettingsMaintenanceCoordinator,
) {
    private var downloadJob: Job? = null

    fun loadDiagnostics() {
        scope.launch {
            val diagnostics = coordinator.diagnostics()
            updateState { it.copy(lastBootInfo = diagnostics.lastBootInfo) }
        }
    }

    fun uploadLogs() {
        if (state.value.maintenance.isUploadingLogs) return
        scope.launch {
            updateState { it.copy(isUploadingLogs = true, logUploadStatus = "正在上传诊断日志…") }
            val result = coordinator.uploadLogs()
            updateState { it.copy(isUploadingLogs = false, logUploadStatus = result.message) }
        }
    }

    fun showUpdateDialog() {
        updateState { it.copy(showUpdateDialog = true, updateDialogState = UpdateState.Idle) }
    }

    fun hideUpdateDialog() {
        downloadJob?.cancel()
        downloadJob = null
        coordinator.clearAvailableUpdate()
        updateState { it.copy(showUpdateDialog = false, updateDialogState = UpdateState.Idle) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        coordinator.setAutoCheckEnabled(enabled)
        updateState { it.copy(autoCheckUpdates = enabled) }
    }

    fun checkForUpdate() {
        scope.launch {
            updateState { it.copy(updateDialogState = UpdateState.Checking) }
            val update = coordinator.checkForUpdate()
            updateState { it.copy(updateDialogState = update) }
        }
    }

    fun exportConfig(destination: Uri) {
        scope.launch {
            updateState { it.copy(configStatus = context.getString(R.string.settings_export_in_progress)) }
            val result = coordinator.exportConfig(destination)
            updateState { it.copy(configStatus = result.message) }
        }
    }

    fun restoreConfig(uri: Uri) {
        scope.launch {
            updateState { it.copy(configStatus = context.getString(R.string.settings_export_in_progress)) }
            val result = coordinator.restoreConfig(uri)
            updateState { it.copy(configStatus = result.message) }
        }
    }

    fun downloadUpdate() {
        if (downloadJob?.isActive == true || !coordinator.hasAvailableUpdate()) return
        downloadJob = scope.launch {
            try {
                coordinator.downloadUpdates().collect { update ->
                    if (isActive) updateState { it.copy(updateDialogState = update) }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                downloadJob = null
            }
        }
    }

    private fun updateState(transform: (SettingsMaintenanceUiState) -> SettingsMaintenanceUiState) {
        state.update { it.copy(maintenance = transform(it.maintenance)) }
    }
}
