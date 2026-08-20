package com.byd.wjx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.led.mafu.MafuBleClient
import com.byd.wjx.led.mafu.MafuConnectionPhase
import com.byd.wjx.led.mafu.MafuConnectionState
import com.byd.wjx.led.mafu.MafuDevice
import com.byd.wjx.led.mafu.MafuLinkageRule
import com.byd.wjx.led.mafu.MafuPreferenceSnapshot
import com.byd.wjx.led.mafu.MafuPreferences
import com.byd.wjx.led.mafu.MafuRemoteModel
import com.byd.wjx.led.mafu.MafuRuntimeCoordinator
import com.byd.wjx.led.mafu.MafuRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MafuSettingsUiState(
    val preferences: MafuPreferenceSnapshot = MafuPreferenceSnapshot(),
    val connection: MafuConnectionState = MafuConnectionState(),
    val devices: List<MafuDevice> = emptyList(),
    val runtime: MafuRuntimeState = MafuRuntimeState(),
    val actionMessage: String? = null,
)

@HiltViewModel
class MafuSettingsViewModel @Inject constructor(
    private val preferences: MafuPreferences,
    private val bleClient: MafuBleClient,
    private val runtimeCoordinator: MafuRuntimeCoordinator,
) : ViewModel() {
    private val actionMessage = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(MafuSettingsUiState())
    val uiState: StateFlow<MafuSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.state,
                bleClient.connection,
                bleClient.devices,
                runtimeCoordinator.runtime,
                actionMessage,
            ) { prefs, connection, devices, runtime, message ->
                MafuSettingsUiState(
                    preferences = prefs,
                    connection = connection,
                    devices = devices,
                    runtime = runtime,
                    actionMessage = message,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setEnabled(enabled: Boolean) {
        bleClient.stopScan()
        preferences.setEnabled(enabled)
        bleClient.startAutoConnect()
        showMessage(if (enabled) "马夫 LED 联动已开启" else "马夫 LED 联动已关闭")
    }

    fun scan() {
        showMessage(null)
        val connection = bleClient.connection.value
        if (connection.phase in setOf(
                MafuConnectionPhase.CONNECTING,
                MafuConnectionPhase.DISCOVERING,
                MafuConnectionPhase.SUBSCRIBING,
                MafuConnectionPhase.INITIALIZING,
                MafuConnectionPhase.READY,
                MafuConnectionPhase.RETRYING,
            )
        ) {
            bleClient.disconnect()
        }
        bleClient.startScan()
    }

    fun bind(device: MafuDevice) {
        bleClient.stopScan()
        preferences.setEnabled(true)
        bleClient.connect(device.address, device.name)
        showMessage("正在验证马夫 LED 设备 ${device.name}")
    }

    fun reconnect() {
        bleClient.startAutoConnect()
        showMessage("正在重新连接马夫 LED 设备")
    }

    fun unbind() {
        bleClient.disconnect()
        preferences.unbindDevice()
        showMessage("马夫 LED 设备已解绑")
    }

    fun setBrightness(level: Int) {
        preferences.setBrightness(level)
    }

    fun applyBrightness(level: Int) {
        preferences.setBrightness(level)
        viewModelScope.launch {
            val ok = runtimeCoordinator.applyBrightness(level)
            showMessage(if (ok) "亮度已调至 $level/15" else "设备未连接，亮度将在重连后应用")
        }
    }

    fun setScreenPower(enabled: Boolean) {
        viewModelScope.launch {
            val ok = runtimeCoordinator.setScreenPower(enabled)
            showMessage(
                when {
                    ok && enabled -> "屏幕已开启"
                    ok -> "屏幕已关闭"
                    else -> "设备未连接，无法切换屏幕电源"
                },
            )
        }
    }

    fun setRemoteModel(model: MafuRemoteModel) {
        preferences.setRemoteModel(model)
        showMessage("已切换为${model.title}")
    }

    fun saveRule(rule: MafuLinkageRule): Boolean {
        val saved = preferences.saveRule(rule)
        showMessage(if (saved) "马夫 LED 联动规则已保存" else "同一条件只能创建一条规则")
        return saved
    }

    fun deleteRule(id: String) {
        preferences.deleteRule(id)
        showMessage("马夫 LED 联动规则已删除")
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        preferences.setRuleEnabled(id, enabled)
        showMessage(if (enabled) "规则已启用" else "规则已停用")
    }

    fun moveRule(id: String, offset: Int) {
        preferences.moveRule(id, offset)
    }

    private fun showMessage(message: String?) {
        actionMessage.value = message
    }
}
