package com.byd.wjx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.led.LedBleClient
import com.byd.wjx.led.LedConnectionState
import com.byd.wjx.led.LedDevice
import com.byd.wjx.led.LedLinkageRule
import com.byd.wjx.led.LedPreferenceSnapshot
import com.byd.wjx.led.LedPreferences
import com.byd.wjx.led.LedRuntimeCoordinator
import com.byd.wjx.led.LedRuntimeState
import com.byd.wjx.led.LedScene
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedSettingsUiState(
    val preferences: LedPreferenceSnapshot = LedPreferenceSnapshot(),
    val connection: LedConnectionState = LedConnectionState(),
    val devices: List<LedDevice> = emptyList(),
    val runtime: LedRuntimeState = LedRuntimeState(),
    val actionMessage: String? = null,
)

@HiltViewModel
class LedSettingsViewModel @Inject constructor(
    private val preferences: LedPreferences,
    private val bleClient: LedBleClient,
    private val runtimeCoordinator: LedRuntimeCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LedSettingsUiState())
    val uiState: StateFlow<LedSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.state,
                bleClient.connection,
                bleClient.devices,
                runtimeCoordinator.runtime,
            ) { prefs, connection, devices, runtime ->
                LedSettingsUiState(
                    preferences = prefs,
                    connection = connection,
                    devices = devices,
                    runtime = runtime,
                    actionMessage = _uiState.value.actionMessage,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            preferences.setEnabled(true)
            showMessage("LED联动已开启")
        } else {
            preferences.setEnabled(false)
            showMessage("LED联动已关闭")
        }
    }

    fun scan() {
        bleClient.startScan()
        showMessage(null)
    }

    fun stopScan() = bleClient.stopScan()

    fun bind(device: LedDevice) {
        preferences.setDevice(device.address, device.name)
        preferences.setEnabled(true)
        showMessage("正在绑定 ${device.name}")
    }

    fun reconnect() {
        bleClient.startAutoConnect()
        showMessage("正在重新连接")
    }

    fun unbind() {
        viewModelScope.launch {
            if (bleClient.connection.value.ready) runtimeCoordinator.testScene(LedScene.DEFAULT)
            bleClient.disconnect()
            preferences.setEnabled(false)
            preferences.clearDevice()
            showMessage("LED设备已解绑")
        }
    }

    fun setBrightness(percent: Int) {
        preferences.setBrightness(percent)
    }

    fun applyBrightness(percent: Int) {
        preferences.setBrightness(percent)
        viewModelScope.launch {
            val ok = runtimeCoordinator.applyBrightness(percent)
            showMessage(if (ok) "亮度已调至 $percent%" else "LED未连接，亮度将在重连后应用")
        }
    }

    fun testScene(scene: LedScene) {
        viewModelScope.launch {
            val ok = runtimeCoordinator.testScene(scene)
            showMessage(if (ok) "正在测试：${scene.title}" else "请先连接LED设备")
        }
    }

    fun saveRule(rule: LedLinkageRule): Boolean {
        val saved = preferences.saveRule(rule)
        showMessage(if (saved) "联动规则已保存" else "同一条件只能创建一条规则")
        return saved
    }

    fun deleteRule(id: String) {
        preferences.deleteRule(id)
        showMessage("联动规则已删除")
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        preferences.setRuleEnabled(id, enabled)
        showMessage(if (enabled) "规则已启用" else "规则已停用")
    }

    fun moveRule(id: String, offset: Int) {
        preferences.moveRule(id, offset)
    }

    private fun showMessage(message: String?) {
        _uiState.update { it.copy(actionMessage = message) }
    }
}
