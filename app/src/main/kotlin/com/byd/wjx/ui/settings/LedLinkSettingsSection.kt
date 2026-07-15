package com.byd.wjx.ui.settings

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.led.LedConnectionPhase
import com.byd.wjx.led.LedDevice
import com.byd.wjx.led.LedScene
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun LedLinkSettingsSection(
    viewModel: LedSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingScan by remember { mutableStateOf(false) }
    var brightness by remember(state.preferences.brightness) {
        mutableFloatStateOf(state.preferences.brightness.toFloat())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        if (pendingScan && granted) viewModel.scan()
        pendingScan = false
    }

    fun startScanWithPermission() {
        val missing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        } else {
            emptyList()
        }
        if (missing.isEmpty()) {
            viewModel.scan()
        } else {
            pendingScan = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

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
                            if (state.connection.ready) Icons.Outlined.BluetoothConnected else Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            tint = if (state.connection.ready) AccentGreen else AccentBlue,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LED联动", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            state.connection.message,
                            color = if (state.connection.ready) AccentGreen else TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = state.preferences.enabled,
                        onCheckedChange = viewModel::setEnabled,
                        colors = bydSwitchColors(),
                    )
                }

                if (state.preferences.deviceAddress.isNotBlank()) {
                    LedBoundDeviceRow(
                        name = state.preferences.deviceName.ifBlank { "LED设备" },
                        address = state.preferences.deviceAddress,
                        connected = state.connection.ready,
                        onReconnect = viewModel::reconnect,
                        onUnbind = viewModel::unbind,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = ::startScanWithPermission,
                        enabled = state.preferences.deviceAddress.isBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(7.dp))
                        Text(if (state.connection.phase == LedConnectionPhase.SCANNING) "重新扫描" else "扫描设备")
                    }
                    Button(
                        onClick = { viewModel.testScene(LedScene.DEFAULT) },
                        enabled = state.connection.ready,
                        colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = TextPrimary),
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(7.dp))
                        Text("恢复微笑画面")
                    }
                }

                state.actionMessage?.let {
                    Text(it, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        if (state.devices.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("发现的设备", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    state.devices.forEach { device ->
                        LedDeviceRow(device = device, onClick = { viewModel.bind(device) })
                    }
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
                    Text("${brightness.toInt()}%", color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("内置联动画面测试", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "当前：${state.runtime.currentScene.title}",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LedScene.entries.filterNot { it == LedScene.DEFAULT }.chunked(4).forEach { rowScenes ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowScenes.forEach { scene ->
                            Button(
                                onClick = { viewModel.testScene(scene) },
                                enabled = state.connection.ready,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.runtime.currentScene == scene) AccentGreen else CardSurface,
                                    contentColor = if (state.runtime.currentScene == scene) Color.White else TextPrimary,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(scene.title, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        repeat(4 - rowScenes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedBoundDeviceRow(
    name: String,
    address: String,
    connected: Boolean,
    onReconnect: () -> Unit,
    onUnbind: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (connected) AccentGreen else AccentOrange, RoundedCornerShape(4.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(address, color = TextMuted, fontSize = 10.sp)
        }
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = "重新连接",
            tint = AccentBlue,
            modifier = Modifier.size(22.dp).clickable(onClick = onReconnect),
        )
        Icon(
            Icons.Outlined.LinkOff,
            contentDescription = "解绑设备",
            tint = AccentOrange,
            modifier = Modifier.size(22.dp).clickable(onClick = onUnbind),
        )
    }
}

@Composable
private fun LedDeviceRow(device: LedDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(device.address, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${device.rssi} dBm", color = TextSecondary, fontSize = 11.sp)
    }
}
