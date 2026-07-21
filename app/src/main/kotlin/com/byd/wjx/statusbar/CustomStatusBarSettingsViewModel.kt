package com.byd.wjx.statusbar

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

data class CustomStatusBarSettingsUiState(
    val config: CustomStatusBarConfig = CustomStatusBarConfig(),
    val overlayPermissionGranted: Boolean = false,
    val permissionBusy: Boolean = false,
)

@HiltViewModel
class CustomStatusBarSettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: CustomStatusBarRepository,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(
        CustomStatusBarSettingsUiState(
            config = repository.config.value,
            overlayPermissionGranted = Settings.canDrawOverlays(appContext),
        ),
    )
    val uiState: StateFlow<CustomStatusBarSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.config.collect { config -> _uiState.update { it.copy(config = config) } }
        }
    }

    fun refreshOverlayPermission() {
        _uiState.update { it.copy(overlayPermissionGranted = Settings.canDrawOverlays(appContext)) }
        repository.notifyOverlayPermissionChanged()
    }

    fun setEnabled(value: Boolean) = repository.setEnabled(value)
    fun setBackgroundOpacity(value: Float) = repository.setBackgroundOpacity(value)
    fun setHeightDp(value: Float) = repository.setHeightDp(value)
    fun setTextSizeSp(value: Float) = repository.setTextSizeSp(value)
    fun setHorizontalOffsetDp(value: Float) = repository.setHorizontalOffsetDp(value)
    fun setVerticalOffsetDp(value: Float) = repository.setVerticalOffsetDp(value)
    fun setItemSpacingDp(value: Float) = repository.setItemSpacingDp(value)
    fun setFontStyle(value: CustomStatusBarFontStyle) = repository.setFontStyle(value)
    fun setIconStyle(value: CustomStatusBarIconStyle) = repository.setIconStyle(value)
    fun setTextColorArgb(value: Int) = repository.setTextColorArgb(value)
    fun addItem(item: CustomStatusBarItem) = repository.addItem(item)
    fun removeItem(item: CustomStatusBarItem) = repository.removeItem(item)
    fun moveItem(item: CustomStatusBarItem, offset: Int) = repository.moveItem(item, offset)

    fun requestOverlayPermission() {
        if (_uiState.value.permissionBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(permissionBusy = true) }
            runCatching { helperBootstrap.ensureCurrent() && helperClient.grantOverlayPermission() }
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
