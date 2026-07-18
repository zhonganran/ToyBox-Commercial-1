package com.byd.wjx.desktopcard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DesktopQuickCardSettingsUiState(
    val config: DesktopQuickCardConfig = DesktopQuickCardConfig(),
    val sounds: List<SoundLibrary.SoundItem> = emptyList(),
    val overlayPermissionGranted: Boolean = false,
    val permissionBusy: Boolean = false,
)

@HiltViewModel
class DesktopQuickCardSettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: DesktopQuickCardRepository,
    private val soundLibrary: SoundLibrary,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(
        DesktopQuickCardSettingsUiState(
            config = repository.config.value,
            sounds = soundLibrary.listSounds(),
            overlayPermissionGranted = Settings.canDrawOverlays(appContext),
        )
    )
    val uiState: StateFlow<DesktopQuickCardSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.config.collect { config -> _uiState.update { it.copy(config = config) } }
        }
    }

    fun refresh() {
        repository.refresh()
        _uiState.update {
            it.copy(
                sounds = soundLibrary.listSounds(),
                overlayPermissionGranted = Settings.canDrawOverlays(appContext),
            )
        }
    }

    fun refreshOverlayPermission() {
        _uiState.update { it.copy(overlayPermissionGranted = Settings.canDrawOverlays(appContext)) }
    }

    fun setEnabled(value: Boolean) = repository.setEnabled(value)
    fun setAllowOverOtherApps(value: Boolean) = repository.setAllowOverOtherApps(value)
    fun setScale(value: Float) = repository.setScale(value)
    fun setSectionGapDp(value: Float) = repository.setSectionGapDp(value)
    fun setDividerEnabled(value: Boolean) = repository.setDividerEnabled(value)
    fun resetPosition() = repository.resetPosition()

    fun addCommand(command: String) {
        val current = repository.config.value.functionCommands
        if (current.size >= DESKTOP_QUICK_CARD_MAX_ITEMS || command in current) return
        repository.setFunctionCommands(current + command)
    }

    fun removeCommand(command: String) = repository.setFunctionCommands(
        repository.config.value.functionCommands.filterNot { it == command }
    )

    fun moveCommand(command: String, offset: Int) {
        repository.setFunctionCommands(repository.config.value.functionCommands.moved(command, offset))
    }

    fun addSound(path: String) {
        val current = repository.config.value.soundPaths
        if (current.size >= DESKTOP_QUICK_CARD_MAX_ITEMS || path in current) return
        repository.setSoundPaths(current + path)
    }

    fun removeSound(path: String) = repository.setSoundPaths(
        repository.config.value.soundPaths.filterNot { it == path }
    )

    fun moveSound(path: String, offset: Int) {
        repository.setSoundPaths(repository.config.value.soundPaths.moved(path, offset))
    }

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
        }
    }

    private fun openSystemOverlaySettings() {
        runCatching {
            appContext.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${appContext.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
