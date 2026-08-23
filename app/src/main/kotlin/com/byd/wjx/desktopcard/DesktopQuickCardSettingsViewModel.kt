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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DesktopQuickCardSettingsUiState(
    val config: DesktopQuickCardConfig = DesktopQuickCardConfig(),
    val maxFloatingIconSizeDp: Float = DESKTOP_QUICK_CARD_DEFAULT_ICON_DP,
    val sounds: List<SoundLibrary.SoundItem> = emptyList(),
    val overlayPermissionGranted: Boolean = false,
    val permissionBusy: Boolean = false,
    val icons: List<DesktopQuickCardIcon> = DesktopQuickCardIconCatalog.builtIns,
    val iconImportBusy: Boolean = false,
    val iconMessage: String? = null,
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
            maxFloatingIconSizeDp = repository.maxFloatingIconSizeDp,
            sounds = soundLibrary.listSounds(),
            overlayPermissionGranted = Settings.canDrawOverlays(appContext),
            icons = repository.icons.value,
        )
    )
    val uiState: StateFlow<DesktopQuickCardSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.config.collect { config -> _uiState.update { it.copy(config = config) } }
        }
        viewModelScope.launch {
            repository.icons.collect { icons -> _uiState.update { it.copy(icons = icons) } }
        }
    }

    fun refresh() {
        repository.refresh()
        _uiState.update {
            it.copy(
                maxFloatingIconSizeDp = repository.maxFloatingIconSizeDp,
                sounds = soundLibrary.listSounds(),
                overlayPermissionGranted = Settings.canDrawOverlays(appContext),
            )
        }
    }

    fun refreshOverlayPermission() {
        _uiState.update { it.copy(overlayPermissionGranted = Settings.canDrawOverlays(appContext)) }
        repository.notifyOverlayPermissionChanged()
    }

    fun setEnabled(value: Boolean) = repository.setEnabled(value)
    fun setFloatingIconId(value: String) = repository.setFloatingIconId(value)
    fun setFloatingIconSizeDp(value: Float) = repository.setFloatingIconSizeDp(value)

    fun importIcon(uri: Uri) {
        if (_uiState.value.iconImportBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(iconImportBusy = true, iconMessage = null) }
            val result = repository.importCustomIcon(uri)
            _uiState.update {
                it.copy(
                    iconImportBusy = false,
                    iconMessage = result.fold(
                        onSuccess = { imported -> "已添加并使用“${imported.label}”" },
                        onFailure = { error -> "导入失败：${error.message ?: "无法读取图片"}" },
                    ),
                )
            }
        }
    }

    fun deleteIcon(id: String) = repository.deleteCustomIcon(id)

    fun addCommand(command: String) {
        val current = repository.config.value.functionCommands
        if (command in current) return
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
        if (path in current) return
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
            repository.notifyOverlayPermissionChanged()
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
