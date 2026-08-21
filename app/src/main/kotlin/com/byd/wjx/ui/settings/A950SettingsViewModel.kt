package com.byd.wjx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.led.a950.A950BleClient
import com.byd.wjx.led.a950.A950ConnectionPhase
import com.byd.wjx.led.a950.A950ConnectionState
import com.byd.wjx.led.a950.A950Device
import com.byd.wjx.led.a950.A950LinkageRule
import com.byd.wjx.led.a950.A950PreferenceSnapshot
import com.byd.wjx.led.a950.A950Preferences
import com.byd.wjx.led.a950.A950RuntimeCoordinator
import com.byd.wjx.led.a950.A950RuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class A950SettingsUiState(
    val preferences: A950PreferenceSnapshot = A950PreferenceSnapshot(),
    val connection: A950ConnectionState = A950ConnectionState(),
    val devices: List<A950Device> = emptyList(),
    val runtime: A950RuntimeState = A950RuntimeState(),
    val actionMessage: String? = null,
)

@HiltViewModel
class A950SettingsViewModel @Inject constructor(
    private val preferences: A950Preferences,
    private val bleClient: A950BleClient,
    private val runtimeCoordinator: A950RuntimeCoordinator,
    private val activationManager: ActivationManager,
) : ViewModel() {
    private val actionMessage = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(A950SettingsUiState())
    val uiState: StateFlow<A950SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.state,
                bleClient.connection,
                bleClient.devices,
                runtimeCoordinator.runtime,
                actionMessage,
            ) { prefs, connection, devices, runtime, message ->
                A950SettingsUiState(
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
        if (enabled && !requireLedLinkageAccess()) return
        bleClient.stopScan()
        preferences.setEnabled(enabled)
        bleClient.startAutoConnect()
        showMessage(if (enabled) "A950 联动已开启" else "A950 联动已关闭")
    }

    fun scan() {
        if (!requireLedLinkageAccess()) return
        showMessage(null)
        val connection = bleClient.connection.value
        if (connection.phase in setOf(
                A950ConnectionPhase.CONNECTING,
                A950ConnectionPhase.DISCOVERING,
                A950ConnectionPhase.INITIALIZING,
                A950ConnectionPhase.READY,
                A950ConnectionPhase.RETRYING,
            )
        ) {
            bleClient.disconnect()
        }
        bleClient.startScan()
    }

    fun stopScan() = bleClient.stopScan()

    fun bind(device: A950Device) {
        if (!requireLedLinkageAccess()) return
        bleClient.stopScan()
        preferences.setEnabled(true)
        bleClient.connect(device.address, device.name)
        showMessage("正在验证 A950 设备 ${device.name}")
    }

    fun reconnect() {
        if (!requireLedLinkageAccess()) return
        bleClient.startAutoConnect()
        showMessage("正在重新连接 A950 设备")
    }

    fun unbind() {
        bleClient.disconnect()
        preferences.unbindDevice()
        showMessage("A950 设备已解绑")
    }

    fun setBrightness(percent: Int) {
        if (!requireLedLinkageAccess()) return
        preferences.setBrightness(percent)
    }

    fun applyBrightness(percent: Int) {
        if (!requireLedLinkageAccess()) return
        preferences.setBrightness(percent)
        viewModelScope.launch {
            val ok = runtimeCoordinator.applyBrightness(percent)
            showMessage(if (ok) "亮度已调至 $percent%" else "设备未连接，亮度将在重连后应用")
        }
    }

    fun saveRule(rule: A950LinkageRule): Boolean {
        if (!requireLedLinkageAccess()) return false
        val saved = preferences.saveRule(rule)
        showMessage(if (saved) "A950 联动规则已保存" else "同一条件只能创建一条规则")
        return saved
    }

    fun deleteRule(id: String) {
        preferences.deleteRule(id)
        showMessage("A950 联动规则已删除")
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        if (enabled && !requireLedLinkageAccess()) return
        preferences.setRuleEnabled(id, enabled)
        showMessage(if (enabled) "规则已启用" else "规则已停用")
    }

    fun moveRule(id: String, offset: Int) {
        if (!requireLedLinkageAccess()) return
        preferences.moveRule(id, offset)
    }

    private fun requireLedLinkageAccess(): Boolean {
        if (activationManager.canUse(PremiumFeature.LED_LINKAGE)) return true
        bleClient.stopScan()
        showMessage(LED_LINKAGE_ACTIVATION_REQUIRED_MESSAGE)
        return false
    }

    private fun showMessage(message: String?) {
        actionMessage.value = message
    }
}
