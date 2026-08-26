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
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.led.mafu.MafuConnectionPhase
import com.byd.wjx.led.mafu.MafuRemoteModel
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun MafuConnectionCard(
    state: MafuSettingsUiState,
    connected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onReconnect: () -> Unit,
    onUnbind: () -> Unit,
    onScan: () -> Unit,
) {
    val preferences = state.preferences
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
                        "马夫 LED 协议",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = mafuConnectionMessage(
                            phase = state.connection.phase,
                            message = state.connection.message,
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
                    onCheckedChange = onEnabledChange,
                    colors = bydSwitchColors(),
                )
            }

            if (preferences.deviceAddress.isNotBlank()) {
                LedBoundDeviceRow(
                    name = preferences.deviceName.ifBlank { "马夫 LED 设备" },
                    address = preferences.deviceAddress,
                    protocolLabel = "马夫 LED",
                    connected = connected,
                    onReconnect = onReconnect,
                    onUnbind = onUnbind,
                )
            }

            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text(
                    if (state.connection.phase == MafuConnectionPhase.SCANNING) {
                        "重新识别"
                    } else {
                        "识别 YS、TL 设备"
                    },
                )
            }

            state.actionMessage?.let {
                Text(it, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun MafuScreenPowerCard(
    enabled: Boolean,
    connected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(AccentBlue.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = AccentBlue)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("屏幕电源", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("控制 LED 屏幕的开启和关闭", color = TextSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = connected,
                colors = bydSwitchColors(),
            )
        }
    }
}

@Composable
internal fun MafuBrightnessCard(
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
) {
    var sliderValue by remember(brightness) { mutableFloatStateOf(brightness.toFloat()) }
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
                    "${sliderValue.toInt()} / 15",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onBrightnessChange(it.toInt())
                },
                onValueChangeFinished = { onBrightnessChangeFinished(sliderValue.toInt()) },
                valueRange = 1f..15f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = CardBorder,
                ),
            )
        }
    }
}

@Composable
internal fun MafuRemoteModelCard(
    model: MafuRemoteModel,
    onModelChange: (MafuRemoteModel) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("遥控器型号", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MafuRemoteModel.entries.forEach { candidate ->
                    FilterChip(
                        selected = model == candidate,
                        onClick = { onModelChange(candidate) },
                        label = {
                            Text(
                                "${candidate.keyCount}键",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.18f),
                            selectedLabelColor = AccentBlue,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "按键 1-${model.keyCount}  ·  节目 ${model.programRange.first}-${model.programRange.last}",
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Text(
                "型号 0x${model.modelCode.toString(16).uppercase()}",
                color = TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}
