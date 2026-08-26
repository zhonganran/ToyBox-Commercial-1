package com.byd.wjx.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun rememberBluetoothScanAction(onScan: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnScan by rememberUpdatedState(onScan)
    var pendingScan by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (pendingScan && result.values.all { it }) currentOnScan()
        pendingScan = false
    }

    return {
        val missing = bluetoothScanPermissions(Build.VERSION.SDK_INT).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            currentOnScan()
        } else {
            pendingScan = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Suppress("InlinedApi")
internal fun bluetoothScanPermissions(sdkInt: Int): List<String> =
    if (sdkInt >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
internal fun LedBoundDeviceRow(
    name: String,
    address: String,
    protocolLabel: String,
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
                .background(if (connected) AccentGreen else AccentOrange, RoundedCornerShape(4.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("$protocolLabel  ·  $address", color = TextMuted, fontSize = 10.sp)
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
internal fun LedDiscoveredDeviceRow(
    name: String,
    address: String,
    rssi: Int,
    protocolLabel: String,
    onClick: () -> Unit,
) {
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
            Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "$protocolLabel  ·  $address",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("$rssi dBm", color = TextSecondary, fontSize = 11.sp)
    }
}
