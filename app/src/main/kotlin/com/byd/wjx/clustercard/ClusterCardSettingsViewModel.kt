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
    private val _uiState = MutableStateFlow(
        ClusterCardSettingsUiState(
            config = repository.config.value,
            overlayPermissionGranted = Settings.canDrawOverlays(appContext),
        ),
    )
    val uiState: StateFlow<ClusterCardSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.config.collect { config -> _uiState.update { it.copy(config = config) } }
        }
    }

    fun refresh() {
        repository.refresh()
        refreshOverlayPermission()
    }

    fun refreshOverlayPermission() {
        _uiState.update { it.copy(overlayPermissionGranted = Settings.canDrawOverlays(appContext)) }
        repository.notifyOverlayPermissionChanged()
    }

    fun setEnabled(value: Boolean) = repository.setEnabled(value)

    fun addMetric(id: String) {
        val current = repository.config.value.metricIds
        if (id in current || current.size >= CLUSTER_CARD_MAX_METRICS) return
        repository.setMetricIds(current + id)
    }

    fun removeMetric(id: String) = repository.setMetricIds(
        repository.config.value.metricIds.filterNot { it == id },
    )

    fun moveMetric(id: String, offset: Int) {
        repository.setMetricIds(repository.config.value.metricIds.moved(id, offset))
    }

    fun setScale(value: Float) = repository.setScale(value)

    fun setWidthScale(value: Float) = repository.setWidthScale(value)

    fun setXPct(value: Float) = repository.setXPct(value)

    fun setYPct(value: Float) = repository.setYPct(value)

    fun resetPosition() = repository.resetPosition()

    fun requestOverlayPermission() {
        if (_uiState.value.permissionBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(permissionBusy = true) }
            runCatching {
                helperBootstrap.ensureCurrent() && helperClient.grantOverlayPermission()
            }
            delay(600L)
            val granted = Settings.canDrawOverlays(appContext)
            if (!granted) openSystemOverlaySettings()
            _uiState.update {
                it.copy(
                    overlayPermissionGranted = granted || Settings.canDrawOverlays(appContext),
                    permissionBusy = false,
                )
            }
            repository.notifyOverlayPermissionChanged()
        }
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
