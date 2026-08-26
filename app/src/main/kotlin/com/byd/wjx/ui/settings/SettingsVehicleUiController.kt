package com.byd.wjx.ui.settings

import android.net.Uri
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.data.vehicle.HeadlightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns settings UI state for vehicle identity, headlight readback, and event sounds. */
internal class SettingsVehicleUiController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val eventSoundCoordinator: VehicleEventSoundCoordinator,
    private val vehicleControlCoordinator: SettingsVehicleControlCoordinator,
) {
    fun load() {
        scope.launch {
            val carProfile = vehicleControlCoordinator.loadProfile()
            updateVehicleControl { it.copy(carProfile = carProfile) }
        }
        reloadEventSoundChoices()
    }

    fun reloadEventSoundChoices() {
        scope.launch { refreshEventSoundState(status = null) }
    }

    fun setEventSoundEnabled(event: LockSoundEvent, enabled: Boolean) {
        scope.launch {
            applyEventSoundChange(eventSoundCoordinator.setEnabled(event, enabled))
        }
    }

    fun setEventSoundPath(event: LockSoundEvent, path: String) {
        scope.launch {
            applyEventSoundChange(eventSoundCoordinator.setPath(event, path))
        }
    }

    fun importEventSound(uri: Uri) {
        scope.launch { applyEventSoundChange(eventSoundCoordinator.importSound(uri)) }
    }

    fun deleteEventSound(path: String) {
        scope.launch { applyEventSoundChange(eventSoundCoordinator.deleteSound(path)) }
    }

    fun syncEventSoundNow(reason: String) {
        scope.launch {
            refreshEventSoundState(status = null)
            syncEventSoundConfig(reason)
        }
    }

    fun refreshHeadlightControl() {
        if (state.value.vehicleControl.headlightLoading) return
        updateVehicleControl {
            it.copy(headlightLoading = true, headlightMessage = "正在检测车辆灯光服务")
        }
        scope.launch {
            val snapshot = vehicleControlCoordinator.refreshHeadlight()
            updateVehicleControl { it.withSnapshot(snapshot) }
        }
    }

    fun setHeadlightMode(mode: HeadlightMode) {
        if (state.value.vehicleControl.headlightLoading) return
        updateVehicleControl {
            it.copy(headlightLoading = true, headlightMessage = "正在切换为${mode.label}")
        }
        scope.launch {
            val result = vehicleControlCoordinator.setHeadlightMode(mode)
            updateVehicleControl { it.withSnapshot(result.snapshot) }
        }
    }

    internal suspend fun refreshEventSoundState(status: String?) {
        applyEventSoundState(eventSoundCoordinator.snapshot(status))
    }

    internal suspend fun syncEventSoundConfig(reason: String) {
        applyEventSoundState(eventSoundCoordinator.snapshot(), syncing = true)
        applyEventSoundState(eventSoundCoordinator.sync(reason))
    }

    private suspend fun applyEventSoundChange(change: VehicleEventSoundChange) {
        applyEventSoundState(change.state)
        change.syncReason?.let { syncEventSoundConfig(it) }
    }

    private fun applyEventSoundState(
        eventSound: VehicleEventSoundState,
        syncing: Boolean = false,
    ) {
        state.update {
            it.copy(
                vehicleEventSound = it.vehicleEventSound.merge(eventSound, syncing),
            )
        }
    }

    private fun updateVehicleControl(
        transform: (SettingsVehicleControlUiState) -> SettingsVehicleControlUiState,
    ) {
        state.update { it.copy(vehicleControl = transform(it.vehicleControl)) }
    }
}
