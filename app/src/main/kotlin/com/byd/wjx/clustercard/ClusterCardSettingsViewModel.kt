package com.byd.wjx.clustercard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClusterCardSettingsUiState(
    val config: ClusterCardConfig = ClusterCardConfig(),
    val overlayPermissionGranted: Boolean = false,
    val permissionBusy: Boolean = false,
)

@HiltViewModel
class ClusterCardSettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ClusterCardRepository,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val uiStates = ClusterCardSlot.entries.associateWith { slot ->
        MutableStateFlow(
            ClusterCardSettingsUiState(
                config = repository.current(slot),
                overlayPermissionGranted = Settings.canDrawOverlays(appContext),
            ),
        )
    }
    private val readOnlyUiStates = uiStates.mapValues { (_, flow) -> flow.asStateFlow() }
    val uiState: StateFlow<ClusterCardSettingsUiState> = uiState(ClusterCardSlot.PRIMARY)

    init {
        ClusterCardSlot.entries.forEach { slot ->
            viewModelScope.launch {
                repository.config(slot).collect { config ->
                    uiStateFlow(slot).update { it.copy(config = config) }
                }
            }
        }
    }

    fun uiState(slot: ClusterCardSlot): StateFlow<ClusterCardSettingsUiState> =
        checkNotNull(readOnlyUiStates[slot])

    fun refresh(slot: ClusterCardSlot? = null) {
        repository.refresh(slot)
        refreshOverlayPermission()
    }

    fun refreshOverlayPermission() {
        val granted = Settings.canDrawOverlays(appContext)
        updateAllStates { it.copy(overlayPermissionGranted = granted) }
        repository.notifyOverlayPermissionChanged()
    }

    fun setEnabled(slot: ClusterCardSlot, value: Boolean) = repository.setEnabled(slot, value)

    fun addMetric(slot: ClusterCardSlot, id: String) {
        val current = repository.current(slot).metricIds
        if (id in current || current.size >= CLUSTER_CARD_MAX_METRICS) return
        repository.setMetricIds(slot, current + id)
    }

    fun removeMetric(slot: ClusterCardSlot, id: String) = repository.setMetricIds(
        slot,
        repository.current(slot).metricIds.filterNot { it == id },
    )

    fun moveMetric(slot: ClusterCardSlot, id: String, offset: Int) {
        repository.setMetricIds(slot, repository.current(slot).metricIds.moved(id, offset))
    }

    fun setScale(slot: ClusterCardSlot, value: Float) = repository.setScale(slot, value)

    fun setWidthScale(slot: ClusterCardSlot, value: Float) = repository.setWidthScale(slot, value)

    fun setXPct(slot: ClusterCardSlot, value: Float) = repository.setXPct(slot, value)

    fun setYPct(slot: ClusterCardSlot, value: Float) = repository.setYPct(slot, value)

    fun setFontFamily(slot: ClusterCardSlot, value: ClusterCardFont) = repository.setFontFamily(slot, value)

    fun setTextColor(slot: ClusterCardSlot, value: ClusterCardTextColor) = repository.setTextColor(slot, value)

    fun setFontScale(slot: ClusterCardSlot, value: Float) = repository.setFontScale(slot, value)

    fun setHeightScale(slot: ClusterCardSlot, value: Float) = repository.setHeightScale(slot, value)

    fun resetPosition(slot: ClusterCardSlot) = repository.resetPosition(slot)

    fun requestOverlayPermission() {
        if (uiStates.values.any { it.value.permissionBusy }) return
        viewModelScope.launch {
            updateAllStates { it.copy(permissionBusy = true) }
            runCatching {
                helperBootstrap.ensureCurrent() && helperClient.grantOverlayPermission()
            }
            delay(600L)
            val granted = Settings.canDrawOverlays(appContext)
            if (!granted) openSystemOverlaySettings()
            updateAllStates {
                it.copy(
                    overlayPermissionGranted = granted || Settings.canDrawOverlays(appContext),
                    permissionBusy = false,
                )
            }
            repository.notifyOverlayPermissionChanged()
        }
    }

    private fun uiStateFlow(slot: ClusterCardSlot): MutableStateFlow<ClusterCardSettingsUiState> =
        checkNotNull(uiStates[slot])

    private fun updateAllStates(transform: (ClusterCardSettingsUiState) -> ClusterCardSettingsUiState) {
        uiStates.values.forEach { state -> state.update(transform) }
    }

    private fun openSystemOverlaySettings() {
        runCatching {
            appContext.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${appContext.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun List<String>.moved(value: String, offset: Int): List<String> {
    val from = indexOf(value)
    if (from < 0) return this
    val to = (from + offset).coerceIn(indices)
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
