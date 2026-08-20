package com.byd.wjx.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.led.ipixelfa.IpixelFaConnectionPhase
import com.byd.wjx.led.ipixelfa.IpixelFaDevice
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
    faViewModel: IpixelFaSettingsViewModel = hiltViewModel(),
    a950ViewModel: A950SettingsViewModel = hiltViewModel(),
    mafuViewModel: MafuSettingsViewModel = hiltViewModel(),
) {
    val navigationViewModel: LedLinkSettingsViewModel = hiltViewModel()
    val selectedTab by navigationViewModel.selectedTab.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = CardSurfaceElevated,
            contentColor = AccentBlue,
            divider = {},
        ) {
            LedProtocolSettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { navigationViewModel.selectTab(tab) },
                    text = {
                        Text(
                            tab.title,
                            fontWeight = if (selectedTab == tab) FontWeight.ExtraBold else FontWeight.Medium,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            LedProtocolSettingsTab.FA -> IpixelFaSettingsPane(faViewModel)
            LedProtocolSettingsTab.A950 -> A950SettingsPane(a950ViewModel)
            LedProtocolSettingsTab.MAFU -> MafuSettingsPane(mafuViewModel)
        }
    }
}

@Composable
private fun IpixelFaSettingsPane(
    viewModel: IpixelFaSettingsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = state.preferences
    val connected = state.connection.ready &&
        state.connection.address.equals(preferences.deviceAddress, ignoreCase = true)
    var pendingScan by remember { mutableStateOf(false) }
    var brightness by remember(preferences.brightness) {
        mutableFloatStateOf(preferences.brightness.toFloat())
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
                .filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION).filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
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
                            text = when {
                                state.connection.phase == IpixelFaConnectionPhase.SCANNING ->
                                    state.connection.message
                                connected -> state.connection.message
                                !preferences.enabled -> "联动未开启"
                                state.connection.phase in setOf(
                                    IpixelFaConnectionPhase.CONNECTING,
                                    IpixelFaConnectionPhase.DISCOVERING,
                                    IpixelFaConnectionPhase.RETRYING,
                                    IpixelFaConnectionPhase.ERROR,
                                ) -> state.connection.message
                                preferences.deviceAddress.isBlank() -> "请识别并绑定标准 FA 设备"
                                state.connection.address.equals(
                                    preferences.deviceAddress,
                                    ignoreCase = true,
                                ) -> state.connection.message
                                else -> "等待连接标准 FA 设备"
                            },
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
                    FaBoundDeviceRow(
                        name = preferences.deviceName.ifBlank { "标准 FA 设备" },
                        address = preferences.deviceAddress,
                        connected = connected,
                        onReconnect = viewModel::reconnect,
                        onUnbind = viewModel::unbind,
                    )
                }

                Button(
                    onClick = ::startScanWithPermission,
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
                        }
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
                    Text("发现的标准 FA 设备", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    state.devices.forEach { device ->
                        FaDeviceRow(device = device, onClick = { viewModel.bind(device) })
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

internal enum class LedProtocolSettingsTab(val title: String) {
    FA("FA 标准"),
    A950("A950"),
    MAFU("马夫 LED"),
}

@Composable
private fun FaBoundDeviceRow(
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
        Box(
            Modifier
                .size(8.dp)
                .background(if (connected) AccentGreen else AccentOrange, RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("标准 FA 协议  ·  $address", color = TextMuted, fontSize = 10.sp)
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
private fun FaDeviceRow(device: IpixelFaDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Bluetooth,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "标准 FA 协议  ·  ${device.address}",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("${device.rssi} dBm", color = TextSecondary, fontSize = 11.sp)
    }
}
