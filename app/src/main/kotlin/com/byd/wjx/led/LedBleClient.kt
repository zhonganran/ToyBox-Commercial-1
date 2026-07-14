package com.byd.wjx.led

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedBleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LedPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val foundDevices = ConcurrentHashMap<String, LedDevice>()

    private val _connection = MutableStateFlow(LedConnectionState())
    val connection: StateFlow<LedConnectionState> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<LedDevice>>(emptyList())
    val devices: StateFlow<List<LedDevice>> = _devices.asStateFlow()

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var manualDisconnect = false
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var shutdownJob: Job? = null

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun hasRuntimePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun startAutoConnect() {
        shutdownJob?.cancel()
        shutdownJob = null
        manualDisconnect = false
        val prefs = preferences.state.value
        if (!prefs.enabled) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.DISABLED, message = "LED联动未开启")
            return
        }
        if (prefs.deviceAddress.isBlank()) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.IDLE, message = "请先绑定LED设备")
            return
        }
        connect(prefs.deviceAddress, prefs.deviceName)
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRuntimePermission()) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "缺少蓝牙权限")
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "蓝牙未开启")
            return
        }
        stopScan()
        foundDevices.clear()
        _devices.value = emptyList()
        _connection.value = LedConnectionState(phase = LedConnectionPhase.SCANNING, message = "正在扫描LED设备")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { scanner.startScan(null, settings, scanCallback) }
            .onFailure {
                _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "扫描失败：${it.message}")
                WjxFileLogger.w(TAG, "BLE scan start failed", it)
            }
        scanStopJob = scope.launch {
            delay(SCAN_WINDOW_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanStopJob?.cancel()
        scanStopJob = null
        if (hasRuntimePermission()) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
        if (_connection.value.phase == LedConnectionPhase.SCANNING) {
            _connection.value = LedConnectionState(
                phase = LedConnectionPhase.IDLE,
                message = if (_devices.value.isEmpty()) "未发现LED设备" else "请选择LED设备",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String = "") {
        shutdownJob?.cancel()
        shutdownJob = null
        manualDisconnect = false
        val current = _connection.value
        if (current.address == address && current.phase in setOf(
                LedConnectionPhase.CONNECTING,
                LedConnectionPhase.DISCOVERING,
                LedConnectionPhase.READY,
            )
        ) return
        reconnectJob?.cancel()
        reconnectJob = null
        connectInternal(address, name)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connectInternal(address: String, name: String = "") {
        if (!hasRuntimePermission()) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "缺少蓝牙权限")
            return
        }
        if (adapter?.isEnabled != true) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "蓝牙未开启")
            scheduleReconnect()
            return
        }
        manualDisconnect = false
        stopScan()
        closeGatt()

        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "LED设备地址无效")
            return
        }
        _connection.value = LedConnectionState(
            phase = LedConnectionPhase.CONNECTING,
            address = address,
            name = name,
            message = "正在连接 ${name.ifBlank { address }}",
        )
        WjxFileLogger.i(TAG, "connect address=$address name=$name")
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        shutdownJob?.cancel()
        shutdownJob = null
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopScan()
        closeGatt()
        _connection.value = LedConnectionState(phase = LedConnectionPhase.IDLE, message = "已断开")
    }

    fun restoreDefaultAndDisconnect() {
        shutdownJob?.cancel()
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        val job = scope.launch {
            if (_connection.value.ready) play(LedScene.DEFAULT)
            if (isActive) disconnect()
        }
        shutdownJob = job
        job.invokeOnCompletion {
            if (shutdownJob === job) shutdownJob = null
        }
    }

    suspend fun play(scene: LedScene): Boolean = write(
        LedProtocol.playScene(scene),
        "scene=${scene.name} remotePreset=${scene.remotePresetId}",
    )

    suspend fun setBrightness(percent: Int): Boolean =
        write(LedProtocol.brightness(percent), "brightness=${percent.coerceIn(0, 100)}")

    @SuppressLint("MissingPermission")
    private suspend fun write(packet: ByteArray, description: String): Boolean = writeMutex.withLock {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic
        if (gatt == null || characteristic == null || !_connection.value.ready) {
            WjxFileLogger.w(TAG, "write skipped not ready description=$description")
            return@withLock false
        }
        val writeWithResponse = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        characteristic.writeType = if (writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val ack = if (writeWithResponse) CompletableDeferred<Int>() else null
        pendingWriteAck = ack
        @Suppress("DEPRECATION")
        characteristic.value = packet
        val accepted = runCatching { gatt.writeCharacteristic(characteristic) }.getOrDefault(false)
        if (!accepted) {
            if (pendingWriteAck === ack) pendingWriteAck = null
            WjxFileLogger.w(TAG, "write rejected description=$description")
            recoverFromWriteFailure(description)
            return@withLock false
        }
        WjxFileLogger.i(
            TAG,
            "write queued $description type=${if (writeWithResponse) "DEFAULT" else "NO_RESPONSE"} bytes=${packet.toHex()}",
        )

        val succeeded = if (ack != null) {
            val status = withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { ack.await() }
            if (pendingWriteAck === ack) pendingWriteAck = null
            when {
                status == null -> {
                    WjxFileLogger.w(TAG, "write timeout description=$description")
                    recoverFromWriteFailure(description)
                    false
                }
                status != BluetoothGatt.GATT_SUCCESS -> {
                    WjxFileLogger.w(TAG, "write failed status=$status description=$description")
                    recoverFromWriteFailure(description)
                    false
                }
                else -> true
            }
        } else {
            true
        }
        if (succeeded) delay(WRITE_GAP_MS)
        succeeded
    }

    private fun recoverFromWriteFailure(description: String) {
        val current = _connection.value
        WjxFileLogger.w(TAG, "resetting GATT after write failure description=$description")
        closeGatt()
        if (!manualDisconnect && preferences.state.value.enabled) {
            _connection.value = current.copy(
                phase = LedConnectionPhase.RETRYING,
                message = "LED写入失败，正在重新连接",
                retryInSeconds = 1,
            )
            scheduleReconnect()
        }
    }

    private fun markReady(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt) return
        val current = _connection.value
        _connection.value = current.copy(
            phase = LedConnectionPhase.READY,
            message = "LED已连接",
            retryInSeconds = null,
        )
        reconnectJob?.cancel()
        reconnectJob = null
        WjxFileLogger.i(TAG, "BLE ready address=${current.address}")
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val old = bluetoothGatt
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    private fun scheduleReconnect() {
        if (manualDisconnect || !preferences.state.value.enabled) return
        if (reconnectJob?.isActive == true) return
        val job = scope.launch {
            var attempt = 0
            while (isActive) {
                if (!isActive || manualDisconnect || !preferences.state.value.enabled) return@launch
                val prefs = preferences.state.value
                if (prefs.deviceAddress.isBlank()) return@launch
                val delayMs = RECONNECT_DELAYS_MS[attempt.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
                _connection.value = LedConnectionState(
                    phase = LedConnectionPhase.RETRYING,
                    address = prefs.deviceAddress,
                    name = prefs.deviceName,
                    message = "LED连接已断开，${delayMs / 1_000}秒后重连",
                    retryInSeconds = (delayMs / 1_000).toInt(),
                )
                delay(delayMs)
                if (_connection.value.ready) return@launch
                connectInternal(prefs.deviceAddress, prefs.deviceName)
                delay(CONNECT_SETTLE_MS)
                if (_connection.value.ready) return@launch
                attempt++
            }
        }
        reconnectJob = job
        job.invokeOnCompletion {
            if (reconnectJob === job) reconnectJob = null
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = runCatching { result.device.name }.getOrNull().orEmpty()
            val hasService = result.scanRecord?.serviceUuids.orEmpty().any { it.uuid == LedProtocol.SERVICE_UUID }
            if (!hasService && !name.contains("LED", ignoreCase = true) && !name.contains("PIXEL", ignoreCase = true)) return
            val item = LedDevice(
                address = result.device.address,
                name = name.ifBlank { "LED设备" },
                rssi = result.rssi,
            )
            foundDevices[item.address] = item
            _devices.value = foundDevices.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _connection.value = LedConnectionState(phase = LedConnectionPhase.ERROR, message = "扫描失败：$errorCode")
            WjxFileLogger.w(TAG, "BLE scan failed code=$errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== bluetoothGatt) {
                runCatching { gatt.close() }
                return
            }
            WjxFileLogger.i(TAG, "connection state status=$status newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                val current = _connection.value
                _connection.value = current.copy(phase = LedConnectionPhase.DISCOVERING, message = "正在读取LED服务")
                if (!gatt.discoverServices()) {
                    _connection.value = current.copy(phase = LedConnectionPhase.ERROR, message = "LED服务发现启动失败")
                    closeGatt()
                    scheduleReconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt()
                scheduleReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt) return
            val service = gatt.getService(LedProtocol.SERVICE_UUID)
            val write = service?.getCharacteristic(LedProtocol.WRITE_UUID)
            val notify = service?.getCharacteristic(LedProtocol.NOTIFY_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || write == null) {
                _connection.value = _connection.value.copy(phase = LedConnectionPhase.ERROR, message = "设备不是受支持的LED屏")
                closeGatt()
                return
            }
            writeCharacteristic = write
            notifyCharacteristic = notify
            if (notify == null) {
                markReady(gatt)
                return
            }
            val notificationEnabled = runCatching { gatt.setCharacteristicNotification(notify, true) }.getOrDefault(false)
            val descriptor = notify.getDescriptor(LedProtocol.CCCD_UUID)
            if (!notificationEnabled || descriptor == null) {
                markReady(gatt)
                return
            }
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!gatt.writeDescriptor(descriptor)) markReady(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt === bluetoothGatt && descriptor.uuid == LedProtocol.CCCD_UUID) markReady(gatt)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt !== bluetoothGatt || characteristic.uuid != LedProtocol.WRITE_UUID) return
            pendingWriteAck?.complete(status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                WjxFileLogger.i(TAG, "write callback success uuid=${characteristic.uuid}")
            } else {
                WjxFileLogger.w(TAG, "write callback failed status=$status uuid=${characteristic.uuid}")
            }
        }

        @Deprecated("Deprecated in Android API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt !== bluetoothGatt || characteristic.uuid != LedProtocol.NOTIFY_UUID) return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            WjxFileLogger.i(TAG, "notify bytes=${bytes.toHex()}")
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "LedBleClient"
        private const val SCAN_WINDOW_MS = 10_000L
        private const val WRITE_GAP_MS = 70L
        private const val WRITE_ACK_TIMEOUT_MS = 1_500L
        private const val CONNECT_SETTLE_MS = 3_000L
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }
}
