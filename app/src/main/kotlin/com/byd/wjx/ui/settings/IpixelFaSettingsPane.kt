package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.led.ipixelfa.IpixelFaConnectionPhase
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun IpixelFaSettingsPane(viewModel: IpixelFaSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = state.preferences
    val connected = state.connection.ready &&
        state.connection.address.equals(preferences.deviceAddress, ignoreCase = true)
    var brightness by remember(preferences.brightness) {
        mutableFloatStateOf(preferences.brightness.toFloat())
    }
    val startScan = rememberBluetoothScanAction(viewModel::scan)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(AccentBlue.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (connected) Icons.Outlined.BluetoothConnected else Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            tint = if (connected) AccentGreen else AccentBlue,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "标准 FA 协议",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = faConnectionMessage(
                                phase = state.connection.phase,
                                message = state.connection.message,
                                address = state.connection.address,
                                enabled = preferences.enabled,
                                deviceAddress = preferences.deviceAddress,
                                connected = connected,
                            ),
                            color = if (connected) AccentGreen else TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = preferences.enabled,
                        onCheckedChange = viewModel::setEnabled,
                        colors = bydSwitchColors(),
                    )
                }

                if (preferences.deviceAddress.isNotBlank()) {
                    LedBoundDeviceRow(
                        name = preferences.deviceName.ifBlank { "标准 FA 设备" },
                        address = preferences.deviceAddress,
                        protocolLabel = "标准 FA 协议",
                        connected = connected,
                        onReconnect = viewModel::reconnect,
                        onUnbind = viewModel::unbind,
                    )
                }

                Button(
                    onClick = startScan,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text(
                        if (state.connection.phase == IpixelFaConnectionPhase.SCANNING) {
                            "重新识别"
                        } else {
                            "识别 FA 设备"
                        },
                    )
                }

                state.actionMessage?.let {
                    Text(it, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("屏幕亮度", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${brightness.toInt()}%",
                        color = AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        viewModel.setBrightness(it.toInt())
                    },
                    onValueChangeFinished = { viewModel.applyBrightness(brightness.toInt()) },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = CardBorder,
                    ),
                )
            }
        }

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
                        "发现的标准 FA 设备",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    state.devices.forEach { device ->
                        LedDiscoveredDeviceRow(
                            name = device.name,
                            address = device.address,
                            rssi = device.rssi,
                            protocolLabel = "标准 FA 协议",
                            onClick = { viewModel.bind(device) },
                        )
                    }
                }
            }
        }

        LedLinkageRulesSection(
            rules = preferences.rules,
            currentPreset = state.runtime.currentPreset,
            onSave = viewModel::saveRule,
            onDelete = viewModel::deleteRule,
            onEnabledChange = viewModel::setRuleEnabled,
            onMove = viewModel::moveRule,
        )
    }
}

internal fun faConnectionMessage(
    phase: IpixelFaConnectionPhase,
    message: String,
    address: String,
    enabled: Boolean,
    deviceAddress: String,
    connected: Boolean,
): String = when {
    phase == IpixelFaConnectionPhase.SCANNING -> message
    connected -> message
    !enabled -> "联动未开启"
    phase in setOf(
        IpixelFaConnectionPhase.CONNECTING,
        IpixelFaConnectionPhase.DISCOVERING,
        IpixelFaConnectionPhase.RETRYING,
        IpixelFaConnectionPhase.ERROR,
    ) -> message
    deviceAddress.isBlank() -> "请识别并绑定标准 FA 设备"
    address.equals(deviceAddress, ignoreCase = true) -> message
    else -> "等待连接标准 FA 设备"
}
