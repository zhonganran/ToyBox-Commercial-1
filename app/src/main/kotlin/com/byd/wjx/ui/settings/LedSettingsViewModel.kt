package com.byd.wjx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.led.LedBleClient
import com.byd.wjx.led.LedConnectionState
import com.byd.wjx.led.LedDevice
import com.byd.wjx.led.DuduFff0SceneCatalogState
import com.byd.wjx.led.LedLinkageRule
import com.byd.wjx.led.LedPreferenceSnapshot
import com.byd.wjx.led.LedPreferences
import com.byd.wjx.led.LedProtocolKind
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
    val duduSceneCatalog: DuduFff0SceneCatalogState = DuduFff0SceneCatalogState(),
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
                bleClient.duduSceneCatalog,
                runtimeCoordinator.runtime,
            ) { prefs, connection, devices, duduSceneCatalog, runtime ->
                LedSettingsUiState(
                    preferences = prefs,
                    connection = connection,
                    devices = devices,
                    duduSceneCatalog = duduSceneCatalog,
                    runtime = runtime,
                    actionMessage = _uiState.value.actionMessage,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setEnabled(enabled: Boolean) {
        bleClient.stopScan()
        val protocol = preferences.state.value.selectedProtocol
        if (enabled) {
            preferences.setEnabled(protocol, true)
            showMessage("${protocol.title}联动已开启")
        } else {
            preferences.setEnabled(protocol, false)
            showMessage("${protocol.title}联动已关闭")
        }
    }

    fun selectProtocol(protocol: LedProtocolKind) {
        if (preferences.state.value.selectedProtocol == protocol) return
        bleClient.stopScan()
        preferences.setSelectedProtocol(protocol)
        showMessage("已切换到${protocol.title}")
    }

    fun scan() {
        bleClient.startScan()
        showMessage(null)
    }

    fun stopScan() = bleClient.stopScan()

    fun bind(device: LedDevice) {
        bleClient.stopScan()
        val protocol = device.protocol
        if (protocol != null) {
            preferences.setSelectedProtocol(protocol)
            bleClient.connect(device.address, device.name, protocol)
            showMessage("正在验证${protocol.title}设备 ${device.name}")
        } else {
            bleClient.connect(device.address, device.name)
            showMessage("正在连接并自动识别 ${device.name}")
        }
    }

    fun reconnect() {
        bleClient.startAutoConnect()
        showMessage("正在重新连接")
    }

    fun unbind() {
        viewModelScope.launch {
            val snapshot = preferences.state.value
            val protocol = snapshot.selectedProtocol
            val profile = snapshot.activeProtocol
            val connection = bleClient.connection.value
            val isCurrentConnection = connection.protocol == protocol &&
                connection.address.equals(profile.deviceAddress, ignoreCase = true)
            if (isCurrentConnection) {
                if (connection.ready) runtimeCoordinator.testScene(LedScene.DEFAULT)
                bleClient.disconnect()
            }
            preferences.setEnabled(protocol, false)
            preferences.clearDevice(protocol)
            showMessage("${protocol.title}设备已解绑")
        }
    }

    fun setBrightness(percent: Int) {
        val protocol = preferences.state.value.selectedProtocol
        preferences.setBrightness(protocol, percent)
    }

    fun applyBrightness(percent: Int) {
        val protocol = preferences.state.value.selectedProtocol
        preferences.setBrightness(protocol, percent)
        viewModelScope.launch {
            val ok = runtimeCoordinator.applyBrightness(percent)
            showMessage(if (ok) "亮度已调至 $percent%" else "LED未连接，亮度将在重连后应用")
        }
    }

    fun setDuduSceneSlot(scene: LedScene, slot: Int) {
        val catalog = bleClient.duduSceneCatalog.value
        if (catalog.scanComplete && slot !in catalog.availableSlots) {
            showMessage("${slot.coerceIn(1, 60)} 号场景未安装")
            return
        }
        preferences.setSceneSlot(LedProtocolKind.DUDU_FFF0, scene, slot)
        showMessage("${scene.title}已设为 ${slot.coerceIn(1, 60)} 号场景")
        if (
            bleClient.connection.value.ready &&
            bleClient.connection.value.protocol == LedProtocolKind.DUDU_FFF0 &&
            runtimeCoordinator.runtime.value.currentScene == scene
        ) {
            viewModelScope.launch { bleClient.play(scene) }
        }
    }

    fun refreshDuduSceneSlots() {
        bleClient.refreshDuduSceneSlots()
        showMessage("正在重新读取 FFF0 场景")
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
