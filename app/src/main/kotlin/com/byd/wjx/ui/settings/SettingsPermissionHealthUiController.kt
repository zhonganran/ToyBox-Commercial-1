package com.byd.wjx.ui.settings

import com.byd.wjx.data.system.SettingsPermissionHealthCoordinator
import com.byd.wjx.data.system.SettingsPermissionHealthSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns permission-health refresh and automatic repair UI state. */
internal class SettingsPermissionHealthUiController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val coordinator: SettingsPermissionHealthCoordinator,
) {
    fun refresh() = runCheck(coordinator::inspect)

    fun repair() = runCheck(coordinator::repairAndInspect)

    private fun runCheck(check: suspend () -> SettingsPermissionHealthSnapshot) {
        if (state.value.maintenance.isPermissionHealthLoading) return
        scope.launch {
            updateState { it.copy(isPermissionHealthLoading = true) }
            val snapshot = runCatching { check() }.getOrElse { error ->
                state.value.maintenance.permissionHealth.copy(
                    message = "权限检查失败：${error.message ?: "未知错误"}",
                )
            }
            updateState {
                it.copy(
                    permissionHealth = snapshot,
                    isPermissionHealthLoading = false,
                )
            }
        }
    }

    private fun updateState(transform: (SettingsMaintenanceUiState) -> SettingsMaintenanceUiState) {
        state.update { it.copy(maintenance = transform(it.maintenance)) }
    }
}
