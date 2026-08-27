package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.led.mafu.MafuConnectionPhase
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary

@Composable
internal fun MafuSettingsPane(viewModel: MafuSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = state.preferences
    val connected = state.connection.ready &&
        state.connection.address.equals(preferences.deviceAddress, ignoreCase = true)
    val startScan = rememberBluetoothScanAction(viewModel::scan)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MafuConnectionCard(
            state = state,
            connected = connected,
            onEnabledChange = viewModel::setEnabled,
            onReconnect = viewModel::reconnect,
            onUnbind = viewModel::unbind,
            onScan = startScan,
        )
        MafuScreenPowerCard(
            enabled = state.runtime.screenPowerEnabled,
            connected = connected,
            onEnabledChange = viewModel::setScreenPower,
        )
        MafuBrightnessCard(
            brightness = preferences.brightness,
            enabled = !preferences.brightnessSchedule.enabled,
            onBrightnessChange = viewModel::setBrightness,
            onBrightnessChangeFinished = viewModel::applyBrightness,
        )
        LedBrightnessScheduleCard(
            schedule = preferences.brightnessSchedule,
            minBrightness = 1,
            maxBrightness = 15,
            brightnessText = { "$it / 15" },
            onScheduleChange = viewModel::setBrightnessSchedule,
        )
        MafuRemoteModelCard(
            model = preferences.remoteModel,
            onModelChange = viewModel::setRemoteModel,
        )

        if (state.devices.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "发现的马夫 LED 设备",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    state.devices.forEach { device ->
                        LedDiscoveredDeviceRow(
                            name = device.name,
                            address = device.address,
                            rssi = device.rssi,
                            protocolLabel = "马夫 LED",
                            onClick = { viewModel.bind(device) },
                        )
                    }
                }
            }
        }

        MafuLinkageRulesSection(
            rules = preferences.rules,
            remoteModel = preferences.remoteModel,
            currentRemoteKey = state.runtime.currentRemoteKey,
            onSave = viewModel::saveRule,
            onDelete = viewModel::deleteRule,
            onEnabledChange = viewModel::setRuleEnabled,
            onMove = viewModel::moveRule,
        )
    }
}

internal fun mafuConnectionMessage(
    phase: MafuConnectionPhase,
    message: String,
    enabled: Boolean,
    deviceAddress: String,
    connected: Boolean,
): String = when {
    phase == MafuConnectionPhase.SCANNING -> message
    connected -> message
    !enabled -> "联动未开启"
    phase in setOf(
        MafuConnectionPhase.CONNECTING,
        MafuConnectionPhase.DISCOVERING,
        MafuConnectionPhase.SUBSCRIBING,
        MafuConnectionPhase.INITIALIZING,
        MafuConnectionPhase.RETRYING,
        MafuConnectionPhase.ERROR,
    ) -> message
    deviceAddress.isBlank() -> "请识别并绑定 YS、TL 设备"
    else -> "等待连接马夫 LED 设备"
}
