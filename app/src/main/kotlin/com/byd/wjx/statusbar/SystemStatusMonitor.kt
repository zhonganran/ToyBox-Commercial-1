package com.byd.wjx.statusbar

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemStatusSnapshot(
    val wifiEnabled: Boolean? = null,
    val wifiConnected: Boolean = false,
    val wifiLevel: Int? = null,
    val bluetoothEnabled: Boolean? = null,
    val bluetoothConnected: Boolean = false,
    val locationEnabled: Boolean? = null,
    val usbConnected: Boolean = false,
    val mobileConnected: Boolean = false,
)

@Singleton
class SystemStatusMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<SystemStatusSnapshot> = _snapshot.asStateFlow()

    private var started = false
    private var networkCallbackRegistered = false
    private var receiverRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        refresh()
        networkCallbackRegistered = runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            connectivityManager != null
        }.onFailure { WjxFileLogger.w(TAG, "network callback unavailable", it) }
            .getOrDefault(false)
        receiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                statusReceiver,
                statusIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED,
            )
            true
        }.onFailure { WjxFileLogger.w(TAG, "status receiver unavailable", it) }
            .getOrDefault(false)
        WjxFileLogger.i(TAG, "started")
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(statusReceiver) }
            receiverRegistered = false
        }
        WjxFileLogger.i(TAG, "stopped")
    }

    private fun refresh() {
        _snapshot.value = readSnapshot()
    }

    @SuppressLint("MissingPermission")
    private fun readSnapshot(): SystemStatusSnapshot {
        val capabilities = runCatching {
            val manager = connectivityManager
            manager?.activeNetwork?.let { network -> manager.getNetworkCapabilities(network) }
        }.getOrNull()
        val wifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val bluetoothAdapter = bluetoothManager?.adapter
        val canReadBluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val bluetoothEnabled = if (bluetoothAdapter == null) null else if (!canReadBluetooth) null else {
            runCatching { bluetoothAdapter.isEnabled }.getOrNull()
        }
        val bluetoothConnected = bluetoothEnabled == true && bluetoothAdapter?.let { adapter ->
            runCatching {
                listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.GATT)
                    .any { adapter.getProfileConnectionState(it) == BluetoothAdapter.STATE_CONNECTED }
            }.getOrDefault(false)
        } == true

        return SystemStatusSnapshot(
            wifiEnabled = wifiManager?.let { manager -> runCatching { manager.isWifiEnabled }.getOrNull() },
            wifiConnected = wifiConnected,
            wifiLevel = readWifiLevel(wifiConnected),
            bluetoothEnabled = bluetoothEnabled,
            bluetoothConnected = bluetoothConnected,
            locationEnabled = locationManager?.let { manager -> runCatching { manager.isLocationEnabled }.getOrNull() },
            usbConnected = runCatching { usbManager?.deviceList?.isNotEmpty() == true }.getOrDefault(false),
            mobileConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readWifiLevel(connected: Boolean): Int? {
        if (!connected) return null
        val rssi = runCatching { wifiManager?.connectionInfo?.rssi }.getOrNull() ?: return null
        if (rssi <= INVALID_RSSI) return null
        return WifiManager.calculateSignalLevel(rssi, WIFI_LEVEL_COUNT).coerceIn(0, WIFI_LEVEL_COUNT - 1)
    }

    private fun statusIntentFilter() = IntentFilter().apply {
        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(LocationManager.MODE_CHANGED_ACTION)
        addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    }

    private companion object {
        const val TAG = "SystemStatusMonitor"
        const val INVALID_RSSI = -127
        const val WIFI_LEVEL_COUNT = 4
    }
}
