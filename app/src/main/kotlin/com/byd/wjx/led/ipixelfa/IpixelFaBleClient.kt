package com.byd.wjx.led.ipixelfa

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
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
class IpixelFaBleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: IpixelFaPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val foundDevices = ConcurrentHashMap<String, IpixelFaDevice>()

    private val _connection = MutableStateFlow(IpixelFaConnectionState())
    val connection: StateFlow<IpixelFaConnectionState> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<IpixelFaDevice>>(emptyList())
    val devices: StateFlow<List<IpixelFaDevice>> = _devices.asStateFlow()

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var manualDisconnect = false
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun hasRuntimePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun startAutoConnect() {
        val prefs = preferences.state.value
        if (!prefs.enabled) {
            manualDisconnect = true
            reconnectJob?.cancel()
            reconnectJob = null
            stopScan()
            closeGatt()
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.DISABLED,
                message = "标准 FA 协议未开启",
            )
            return
        }
        manualDisconnect = false
        if (_connection.value.phase in setOf(
                IpixelFaConnectionPhase.CONNECTING,
                IpixelFaConnectionPhase.DISCOVERING,
            )
        ) return
        if (prefs.deviceAddress.isBlank()) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.IDLE,
                message = "请先绑定标准 FA 设备",
            )
            return
        }
        connect(prefs.deviceAddress, prefs.deviceName)
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRuntimePermission()) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.ERROR,
                message = "缺少蓝牙权限",
            )
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.ERROR,
                message = "蓝牙未开启",
            )
            return
        }
        stopScan()
        foundDevices.clear()
        _devices.value = emptyList()
        _connection.value = IpixelFaConnectionState(
            phase = IpixelFaConnectionPhase.SCANNING,
            message = "正在查找标准 FA 设备",
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { scanner.startScan(null, settings, scanCallback) }
            .onFailure {
                _connection.value = IpixelFaConnectionState(
                    phase = IpixelFaConnectionPhase.ERROR,
                    message = "扫描失败：${it.message}",
                )
                WjxFileLogger.w(TAG, "FA BLE scan start failed", it)
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
        if (_connection.value.phase == IpixelFaConnectionPhase.SCANNING) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.IDLE,
                message = if (_devices.value.isEmpty()) "未发现标准 FA 设备" else "请选择标准 FA 设备",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String = "") {
        manualDisconnect = false
        val current = _connection.value
        if (
            current.address.equals(address, ignoreCase = true) &&
            current.phase in setOf(
                IpixelFaConnectionPhase.CONNECTING,
                IpixelFaConnectionPhase.DISCOVERING,
                IpixelFaConnectionPhase.READY,
            )
        ) return
        reconnectJob?.cancel()
        reconnectJob = null
        connectInternal(address, name)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connectInternal(address: String, name: String) {
        if (!hasRuntimePermission()) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "缺少蓝牙权限",
            )
            return
        }
        if (adapter?.isEnabled != true) {
            val disconnected = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "蓝牙未开启",
            )
            _connection.value = disconnected
            scheduleReconnect(disconnected)
            return
        }
        manualDisconnect = false
        stopScan()
        closeGatt()

        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "标准 FA 设备地址无效",
            )
            return
        }
        val connecting = IpixelFaConnectionState(
            phase = IpixelFaConnectionPhase.CONNECTING,
            address = address,
            name = name,
            message = "正在连接 ${name.ifBlank { address }}",
        )
        _connection.value = connecting
        WjxFileLogger.i(TAG, "connect address=$address name=$name")
        val gatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            WjxFileLogger.w(TAG, "connectGatt failed", it)
            null
        }
        if (gatt == null) {
            _connection.value = connecting.copy(
                phase = IpixelFaConnectionPhase.ERROR,
                message = "标准 FA 设备连接启动失败",
            )
            return
        }
        bluetoothGatt = gatt
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopScan()
        closeGatt()
        _connection.value = IpixelFaConnectionState(
            phase = IpixelFaConnectionPhase.IDLE,
            message = "已断开",
        )
    }

    suspend fun play(preset: IpixelFaPreset): Boolean = write(
        packet = IpixelFaProtocol.playPreset(preset),
        description = "preset=${preset.presetId}",
    )

    suspend fun setBrightness(percent: Int): Boolean = write(
        packet = IpixelFaProtocol.brightness(percent),
        description = "brightness=${percent.coerceIn(0, 100)}",
    )

    @SuppressLint("MissingPermission")
    private suspend fun write(packet: ByteArray, description: String): Boolean = writeMutex.withLock {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic
        if (gatt == null || characteristic == null || !_connection.value.ready) {
            WjxFileLogger.w(TAG, "write skipped not ready description=$description")
            return@withLock false
        }
        val writeWithResponse = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        characteristic.writeType = if (writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val ack = if (writeWithResponse) CompletableDeferred<Int>() else null
        pendingWriteAck = ack
        val accepted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, packet, characteristic.writeType) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = packet
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }.getOrDefault(false)
        if (!accepted) {
            if (pendingWriteAck === ack) pendingWriteAck = null
            recoverFromWriteFailure(description)
            return@withLock false
        }
        WjxFileLogger.i(
            TAG,
            "write queued $description type=${if (writeWithResponse) "DEFAULT" else "NO_RESPONSE"} " +
                "bytes=${packet.toHex()}",
        )

        val succeeded = if (ack == null) {
            true
        } else {
            val status = withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { ack.await() }
            if (pendingWriteAck === ack) pendingWriteAck = null
            status == BluetoothGatt.GATT_SUCCESS
        }
        if (!succeeded) {
            recoverFromWriteFailure(description)
            return@withLock false
        }
        delay(IpixelFaProtocol.WRITE_GAP_MS)
        true
    }

    private fun recoverFromWriteFailure(description: String) {
        val disconnected = _connection.value
        WjxFileLogger.w(TAG, "resetting FA GATT after write failure description=$description")
        closeGatt()
        if (scheduleReconnect(disconnected)) return
        _connection.value = disconnected.copy(
            phase = IpixelFaConnectionPhase.ERROR,
            message = "标准 FA 设备未返回有效应答",
            retryInSeconds = null,
        )
    }

    private fun markReady(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt) return
        val current = _connection.value
        val enabled = preferences.state.value.enabled
        preferences.bindDevice(
            address = current.address,
            name = current.name.ifBlank { "标准 FA 设备" },
            enable = enabled,
        )
        if (!enabled) {
            manualDisconnect = true
            closeGatt()
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.DISABLED,
                message = "标准 FA 协议未开启",
            )
            return
        }
        _connection.value = current.copy(
            phase = IpixelFaConnectionPhase.READY,
            message = "标准 FA 协议已连接",
            retryInSeconds = null,
        )
        reconnectJob?.cancel()
        reconnectJob = null
        WjxFileLogger.i(TAG, "FA BLE ready address=${current.address}")
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun closeGatt() {
        val old = bluetoothGatt
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        bluetoothGatt = null
        writeCharacteristic = null
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    private fun scheduleReconnect(disconnected: IpixelFaConnectionState): Boolean {
        val prefs = preferences.state.value
        if (
            manualDisconnect ||
            !prefs.enabled ||
            prefs.deviceAddress.isBlank() ||
            !prefs.deviceAddress.equals(disconnected.address, ignoreCase = true)
        ) return false
        if (reconnectJob?.isActive == true) return true

        val job = scope.launch {
            var attempt = 0
            while (isActive) {
                val currentPrefs = preferences.state.value
                if (
                    manualDisconnect ||
                    !currentPrefs.enabled ||
                    currentPrefs.deviceAddress.isBlank()
                ) return@launch
                val delayMs = RECONNECT_DELAYS_MS[attempt.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
                _connection.value = IpixelFaConnectionState(
                    phase = IpixelFaConnectionPhase.RETRYING,
                    address = currentPrefs.deviceAddress,
                    name = currentPrefs.deviceName,
                    message = "标准 FA 连接已断开，${delayMs / 1_000}秒后重连",
                    retryInSeconds = (delayMs / 1_000).toInt(),
                )
                delay(delayMs)
                if (_connection.value.ready) return@launch
                val latest = preferences.state.value
                if (
                    manualDisconnect ||
                    !latest.enabled ||
                    !latest.deviceAddress.equals(currentPrefs.deviceAddress, ignoreCase = true)
                ) return@launch
                connectInternal(latest.deviceAddress, latest.deviceName)
                delay(CONNECT_SETTLE_MS)
                if (_connection.value.ready) return@launch
                attempt++
            }
        }
        reconnectJob = job
        job.invokeOnCompletion {
            if (reconnectJob === job) reconnectJob = null
        }
        return true
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = runCatching { result.device.name }.getOrNull()
                .orEmpty()
                .ifBlank { result.scanRecord?.deviceName.orEmpty() }
            val services = result.scanRecord?.serviceUuids.orEmpty().mapTo(linkedSetOf()) { it.uuid }
            val advertisesFa = IpixelFaProtocol.serviceUuid in services
            val nameLooksLikeIpixel = name.contains("IPIXEL", ignoreCase = true) ||
                name.contains("PIXEL", ignoreCase = true) ||
                name.contains("LED", ignoreCase = true)
            if (!advertisesFa && !(services.isEmpty() && nameLooksLikeIpixel)) return

            val item = IpixelFaDevice(
                address = result.device.address,
                name = name.ifBlank { "标准 FA 设备" },
                rssi = result.rssi,
            )
            foundDevices[item.address] = item
            _devices.value = foundDevices.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _connection.value = IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.ERROR,
                message = "扫描失败：$errorCode",
            )
            WjxFileLogger.w(TAG, "FA BLE scan failed code=$errorCode")
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
                _connection.value = current.copy(
                    phase = IpixelFaConnectionPhase.DISCOVERING,
                    message = "正在验证标准 FA 服务",
                )
                if (!gatt.discoverServices()) {
                    closeGatt()
                    val failed = current.copy(
                        phase = IpixelFaConnectionPhase.ERROR,
                        message = "标准 FA 服务读取启动失败",
                    )
                    _connection.value = failed
                    scheduleReconnect(failed)
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val disconnected = _connection.value
                closeGatt()
                if (!scheduleReconnect(disconnected)) {
                    _connection.value = disconnected.copy(
                        phase = IpixelFaConnectionPhase.ERROR,
                        message = "标准 FA 连接失败，请重新选择设备",
                        retryInSeconds = null,
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val failed = _connection.value.copy(
                    phase = IpixelFaConnectionPhase.ERROR,
                    message = "标准 FA 服务读取失败",
                )
                closeGatt()
                _connection.value = failed
                scheduleReconnect(failed)
                return
            }
            val service = gatt.getService(IpixelFaProtocol.serviceUuid)
            val characteristicUuids = service?.characteristics
                .orEmpty()
                .mapTo(linkedSetOf()) { it.uuid }
            if (!IpixelFaProtocol.acceptsGattProfile(service?.uuid, characteristicUuids)) {
                _connection.value = _connection.value.copy(
                    phase = IpixelFaConnectionPhase.ERROR,
                    message = "该设备不是标准 FA 协议设备",
                )
                closeGatt()
                return
            }
            val write = checkNotNull(service?.getCharacteristic(IpixelFaProtocol.writeUuid))
            val supportsWrite = write.properties and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (!supportsWrite) {
                _connection.value = _connection.value.copy(
                    phase = IpixelFaConnectionPhase.ERROR,
                    message = "标准 FA 写入通道不可用",
                )
                closeGatt()
                return
            }
            writeCharacteristic = write
            markReady(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== bluetoothGatt || characteristic.uuid != IpixelFaProtocol.writeUuid) return
            pendingWriteAck?.complete(status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                WjxFileLogger.i(TAG, "write callback success")
            } else {
                WjxFileLogger.w(TAG, "write callback failed status=$status")
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "IpixelFaBleClient"
        private const val SCAN_WINDOW_MS = 10_000L
        private const val WRITE_ACK_TIMEOUT_MS = 1_500L
        private const val CONNECT_SETTLE_MS = 5_000L
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }
}
