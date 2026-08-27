package com.byd.wjx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.led.LedBrightnessSchedule
import com.byd.wjx.led.ipixelfa.IpixelFaBleClient
import com.byd.wjx.led.ipixelfa.IpixelFaConnectionPhase
import com.byd.wjx.led.ipixelfa.IpixelFaConnectionState
import com.byd.wjx.led.ipixelfa.IpixelFaDevice
import com.byd.wjx.led.ipixelfa.IpixelFaLinkageRule
import com.byd.wjx.led.ipixelfa.IpixelFaPreferenceSnapshot
import com.byd.wjx.led.ipixelfa.IpixelFaPreferences
import com.byd.wjx.led.ipixelfa.IpixelFaRuntimeCoordinator
import com.byd.wjx.led.ipixelfa.IpixelFaRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IpixelFaSettingsUiState(
    val preferences: IpixelFaPreferenceSnapshot = IpixelFaPreferenceSnapshot(),
    val connection: IpixelFaConnectionState = IpixelFaConnectionState(),
    val devices: List<IpixelFaDevice> = emptyList(),
    val runtime: IpixelFaRuntimeState = IpixelFaRuntimeState(),
    val actionMessage: String? = null,
)

@HiltViewModel
class IpixelFaSettingsViewModel @Inject constructor(
    private val preferences: IpixelFaPreferences,
    private val bleClient: IpixelFaBleClient,
    private val runtimeCoordinator: IpixelFaRuntimeCoordinator,
    private val activationManager: ActivationManager,
) : ViewModel() {
    private val actionMessage = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(IpixelFaSettingsUiState())
    val uiState: StateFlow<IpixelFaSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.state,
                bleClient.connection,
                bleClient.devices,
                runtimeCoordinator.runtime,
                actionMessage,
            ) { prefs, connection, devices, runtime, message ->
                IpixelFaSettingsUiState(
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
        showMessage(if (enabled) "标准 FA 联动已开启" else "标准 FA 联动已关闭")
    }

    fun scan() {
        if (!requireLedLinkageAccess()) return
        showMessage(null)
        viewModelScope.launch {
            val connection = bleClient.connection.value
            if (connection.phase in setOf(
                    IpixelFaConnectionPhase.CONNECTING,
                    IpixelFaConnectionPhase.DISCOVERING,
                    IpixelFaConnectionPhase.READY,
                    IpixelFaConnectionPhase.RETRYING,
                )
            ) {
                bleClient.disconnect()
            }
            bleClient.startScan()
        }
    }

    fun stopScan() = bleClient.stopScan()

    fun bind(device: IpixelFaDevice) {
        if (!requireLedLinkageAccess()) return
        bleClient.stopScan()
        preferences.setEnabled(true)
        bleClient.connect(device.address, device.name)
        showMessage("正在验证标准 FA 设备 ${device.name}")
    }

    fun reconnect() {
        if (!requireLedLinkageAccess()) return
        bleClient.startAutoConnect()
        showMessage("正在重新连接标准 FA 设备")
    }

    fun unbind() {
        viewModelScope.launch {
            val snapshot = preferences.state.value
            val connection = bleClient.connection.value
            if (connection.address.equals(snapshot.deviceAddress, ignoreCase = true)) {
                bleClient.disconnect()
            }
            preferences.unbindDevice()
            showMessage("标准 FA 设备已解绑")
        }
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

    fun setBrightnessSchedule(schedule: LedBrightnessSchedule) {
        if (!requireLedLinkageAccess()) return
        preferences.setBrightnessSchedule(schedule)
        viewModelScope.launch {
            val ok = runtimeCoordinator.syncBrightness()
            showMessage(
                when {
                    ok && schedule.enabled -> "日夜亮度联动已应用"
                    ok -> "日夜亮度联动已关闭，已恢复手动亮度"
                    else -> "亮度设置已保存，将在设备重连后应用"
                },
            )
        }
    }

    fun saveRule(rule: IpixelFaLinkageRule): Boolean {
        if (!requireLedLinkageAccess()) return false
        val saved = preferences.saveRule(rule)
        showMessage(if (saved) "联动规则已保存" else "同一条件只能创建一条规则")
        return saved
    }

    fun deleteRule(id: String) {
        preferences.deleteRule(id)
        showMessage("联动规则已删除")
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
