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
    @Volatile private var activeDeviceKind = LedDeviceKind.UNKNOWN
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    @Volatile private var protocolSetupStarted = false
    @Volatile private var protocolInitializationStarted = false
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var shutdownJob: Job? = null
    private var protocolInitJob: Job? = null
    private var keepaliveJob: Job? = null
    private var mtuFallbackJob: Job? = null
    private var notifyFallbackJob: Job? = null

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
        connect(prefs.deviceAddress, prefs.deviceName, prefs.deviceKind)
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
    fun connect(
        address: String,
        name: String = "",
        kind: LedDeviceKind = LedDeviceKind.UNKNOWN,
    ) {
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
        connectInternal(address, name, kind)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connectInternal(
        address: String,
        name: String = "",
        kind: LedDeviceKind = LedDeviceKind.UNKNOWN,
    ) {
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
            deviceKind = kind,
            message = "正在连接 ${name.ifBlank { address }}",
        )
        WjxFileLogger.i(TAG, "connect address=$address name=$name expectedKind=$kind")
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
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

    suspend fun play(scene: LedScene): Boolean {
        val kind = activeDeviceKind
        val packet = when (kind) {
            LedDeviceKind.IPIXEL_FA -> LedProtocol.playScene(scene)
            LedDeviceKind.A950 -> A950LedProtocol.playScene(scene)
            LedDeviceKind.UNKNOWN -> return false
        }
        val presetId = when (kind) {
            LedDeviceKind.IPIXEL_FA -> LedProtocol.presetId(scene)
            LedDeviceKind.A950 -> A950LedProtocol.presetId(scene)
            LedDeviceKind.UNKNOWN -> return false
        }
        return write(packet, "scene=${scene.name} kind=$kind preset=$presetId")
    }

    suspend fun setBrightness(percent: Int): Boolean {
        val kind = activeDeviceKind
        val packet = when (kind) {
            LedDeviceKind.IPIXEL_FA -> LedProtocol.brightness(percent)
            LedDeviceKind.A950 -> A950LedProtocol.brightness(percent)
            LedDeviceKind.UNKNOWN -> return false
        }
        return write(packet, "brightness=${percent.coerceIn(0, 100)} kind=$kind")
    }

    @SuppressLint("MissingPermission")
    private suspend fun write(
        packet: ByteArray,
        description: String,
        requireReady: Boolean = true,
    ): Boolean = writeMutex.withLock {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic
        if (gatt == null || characteristic == null || (requireReady && !_connection.value.ready)) {
            WjxFileLogger.w(TAG, "write skipped not ready description=$description")
            return@withLock false
        }
        val writeWithResponse = activeDeviceKind != LedDeviceKind.A950 &&
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
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
        if (succeeded) {
            delay(if (activeDeviceKind == LedDeviceKind.A950) A950_WRITE_GAP_MS else WRITE_GAP_MS)
        }
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
        val kind = activeDeviceKind
        _connection.value = current.copy(
            phase = LedConnectionPhase.READY,
            deviceKind = kind,
            message = "${kind.title}已连接",
            retryInSeconds = null,
        )
        if (kind != LedDeviceKind.UNKNOWN && preferences.state.value.deviceKind != kind) {
            preferences.setDeviceKind(kind)
        }
        reconnectJob?.cancel()
        reconnectJob = null
        if (kind == LedDeviceKind.A950) startA950Keepalive(gatt)
        WjxFileLogger.i(TAG, "BLE ready address=${current.address} kind=$kind mtu=$negotiatedMtu")
    }

    private fun startA950Initialization(gatt: BluetoothGatt) {
        protocolInitJob?.cancel()
        val job = scope.launch {
            delay(A950_INITIAL_DELAY_MS)
            repeat(A950_PREPARE_COUNT) { index ->
                if (gatt !== bluetoothGatt || activeDeviceKind != LedDeviceKind.A950) return@launch
                if (!write(
                        A950LedProtocol.prepareUploadPacket,
                        "A950 prepare ${index + 1}/$A950_PREPARE_COUNT",
                        requireReady = false,
                    )
                ) return@launch
                delay(A950_PREPARE_GAP_MS)
            }
            delay(A950_INIT_DELAY_MS)
            if (!write(
                    A950LedProtocol.initializePacket,
                    "A950 initialize",
                    requireReady = false,
                )
            ) return@launch
            delay(A950_READY_DELAY_MS)
            markReady(gatt)
        }
        protocolInitJob = job
        job.invokeOnCompletion {
            if (protocolInitJob === job) protocolInitJob = null
        }
    }

    private fun startA950Keepalive(gatt: BluetoothGatt) {
        keepaliveJob?.cancel()
        val job = scope.launch {
            while (
                isActive &&
                gatt === bluetoothGatt &&
                activeDeviceKind == LedDeviceKind.A950 &&
                _connection.value.ready
            ) {
                delay(A950LedProtocol.KEEPALIVE_INTERVAL_MS)
                if (!isActive || gatt !== bluetoothGatt || !_connection.value.ready) return@launch
                if (!write(A950LedProtocol.keepalivePacket, "A950 keepalive")) return@launch
            }
        }
        keepaliveJob = job
        job.invokeOnCompletion {
            if (keepaliveJob === job) keepaliveJob = null
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun completeProtocolSetup(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || protocolSetupStarted) return
        protocolSetupStarted = true
        mtuFallbackJob?.cancel()
        mtuFallbackJob = null

        val notify = notifyCharacteristic
        if (notify == null) {
            completeProtocolInitialization(gatt)
            return
        }
        val notificationEnabled = runCatching {
            gatt.setCharacteristicNotification(notify, true)
        }.getOrDefault(false)
        val descriptor = notify.getDescriptor(LedProtocol.CCCD_UUID)
        if (!notificationEnabled || descriptor == null) {
            completeProtocolInitialization(gatt)
            return
        }
        val descriptorAccepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!descriptorAccepted) {
            completeProtocolInitialization(gatt)
        } else {
            notifyFallbackJob?.cancel()
            notifyFallbackJob = scope.launch {
                delay(NOTIFY_CALLBACK_TIMEOUT_MS)
                WjxFileLogger.w(TAG, "notify descriptor callback timeout; continuing initialization")
                completeProtocolInitialization(gatt)
            }
        }
    }

    @Synchronized
    private fun completeProtocolInitialization(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || protocolInitializationStarted) return
        protocolInitializationStarted = true
        notifyFallbackJob?.cancel()
        notifyFallbackJob = null
        when (activeDeviceKind) {
            LedDeviceKind.IPIXEL_FA -> markReady(gatt)
            LedDeviceKind.A950 -> startA950Initialization(gatt)
            LedDeviceKind.UNKNOWN -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val old = bluetoothGatt
        protocolInitJob?.cancel()
        protocolInitJob = null
        keepaliveJob?.cancel()
        keepaliveJob = null
        mtuFallbackJob?.cancel()
        mtuFallbackJob = null
        notifyFallbackJob?.cancel()
        notifyFallbackJob = null
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        activeDeviceKind = LedDeviceKind.UNKNOWN
        negotiatedMtu = DEFAULT_MTU
        protocolSetupStarted = false
        protocolInitializationStarted = false
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
                    deviceKind = prefs.deviceKind,
                    message = "LED连接已断开，${delayMs / 1_000}秒后重连",
                    retryInSeconds = (delayMs / 1_000).toInt(),
                )
                delay(delayMs)
                if (_connection.value.ready) return@launch
                connectInternal(prefs.deviceAddress, prefs.deviceName, prefs.deviceKind)
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
            val services = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            val kind = when {
                LedProtocol.SERVICE_UUID in services -> LedDeviceKind.IPIXEL_FA
                A950LedProtocol.SERVICE_UUID in services -> LedDeviceKind.A950
                else -> LedDeviceKind.UNKNOWN
            }
            val nameLooksLikeLed = name.contains("LED", ignoreCase = true) ||
                name.contains("PIXEL", ignoreCase = true) ||
                name.contains("A950", ignoreCase = true)
            val previous = foundDevices[result.device.address]
            val resolvedKind = kind.takeUnless { it == LedDeviceKind.UNKNOWN }
                ?: previous?.kind
                ?: LedDeviceKind.UNKNOWN
            if (kind == LedDeviceKind.UNKNOWN && name.isBlank()) return
            if (
                kind == LedDeviceKind.UNKNOWN &&
                !nameLooksLikeLed &&
                !foundDevices.containsKey(result.device.address)
            ) {
                WjxFileLogger.i(TAG, "scan candidate awaiting service probe name=$name address=${result.device.address}")
            }
            val item = LedDevice(
                address = result.device.address,
                name = name.ifBlank { "LED设备" },
                rssi = result.rssi,
                kind = resolvedKind,
            )
            foundDevices[item.address] = item
            _devices.value = foundDevices.values.sortedWith(
                compareByDescending<LedDevice> { it.kind != LedDeviceKind.UNKNOWN }
                    .thenByDescending { it.rssi },
            )
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
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connection.value = _connection.value.copy(
                    phase = LedConnectionPhase.ERROR,
                    message = "LED服务读取失败，正在重连",
                )
                closeGatt()
                scheduleReconnect()
                return
            }
            val faService = gatt.getService(LedProtocol.SERVICE_UUID)
            val faWrite = faService?.getCharacteristic(LedProtocol.WRITE_UUID)
            val a950Service = gatt.getService(A950LedProtocol.SERVICE_UUID)
            val a950Write = a950Service?.getCharacteristic(A950LedProtocol.WRITE_UUID)
            val preferredKind = _connection.value.deviceKind
            val detectedKind = when {
                preferredKind == LedDeviceKind.A950 && a950Write != null -> LedDeviceKind.A950
                preferredKind == LedDeviceKind.IPIXEL_FA && faWrite != null -> LedDeviceKind.IPIXEL_FA
                faWrite != null -> LedDeviceKind.IPIXEL_FA
                a950Write != null -> LedDeviceKind.A950
                else -> LedDeviceKind.UNKNOWN
            }
            if (detectedKind == LedDeviceKind.UNKNOWN) {
                _connection.value = _connection.value.copy(phase = LedConnectionPhase.ERROR, message = "设备不是受支持的LED屏")
                closeGatt()
                return
            }
            activeDeviceKind = detectedKind
            _connection.value = _connection.value.copy(
                deviceKind = detectedKind,
                message = "已识别${detectedKind.title}，正在初始化",
            )
            WjxFileLogger.i(TAG, "services detected kind=$detectedKind")
            when (detectedKind) {
                LedDeviceKind.IPIXEL_FA -> {
                    writeCharacteristic = faWrite
                    notifyCharacteristic = faService?.getCharacteristic(LedProtocol.NOTIFY_UUID)
                    completeProtocolSetup(gatt)
                }
                LedDeviceKind.A950 -> {
                    writeCharacteristic = a950Write
                    notifyCharacteristic = a950Service?.getCharacteristic(A950LedProtocol.NOTIFY_UUID)
                    runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                    val mtuRequested = runCatching {
                        gatt.requestMtu(A950LedProtocol.REQUESTED_MTU)
                    }.getOrDefault(false)
                    if (mtuRequested) {
                        mtuFallbackJob?.cancel()
                        mtuFallbackJob = scope.launch {
                            delay(MTU_CALLBACK_TIMEOUT_MS)
                            WjxFileLogger.w(TAG, "A950 MTU callback timeout; continuing with mtu=$negotiatedMtu")
                            completeProtocolSetup(gatt)
                        }
                    } else {
                        WjxFileLogger.w(TAG, "A950 MTU request rejected; continuing with mtu=$negotiatedMtu")
                        completeProtocolSetup(gatt)
                    }
                }
                LedDeviceKind.UNKNOWN -> Unit
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== bluetoothGatt || activeDeviceKind != LedDeviceKind.A950) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            WjxFileLogger.i(TAG, "A950 MTU status=$status mtu=$negotiatedMtu")
            completeProtocolSetup(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== bluetoothGatt || descriptor.uuid != LedProtocol.CCCD_UUID) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                WjxFileLogger.w(TAG, "notify descriptor failed status=$status; continuing without ACK notifications")
            }
            completeProtocolInitialization(gatt)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt !== bluetoothGatt || characteristic.uuid != writeCharacteristic?.uuid) return
            pendingWriteAck?.complete(status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                WjxFileLogger.i(TAG, "write callback success uuid=${characteristic.uuid}")
            } else {
                WjxFileLogger.w(TAG, "write callback failed status=$status uuid=${characteristic.uuid}")
            }
        }

        @Deprecated("Deprecated in Android API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt !== bluetoothGatt || characteristic.uuid != notifyCharacteristic?.uuid) return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            WjxFileLogger.i(TAG, "notify bytes=${bytes.toHex()}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== bluetoothGatt || characteristic.uuid != notifyCharacteristic?.uuid) return
            WjxFileLogger.i(TAG, "notify bytes=${value.toHex()}")
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "LedBleClient"
        private const val DEFAULT_MTU = 23
        private const val SCAN_WINDOW_MS = 10_000L
        private const val WRITE_GAP_MS = 70L
        private const val A950_WRITE_GAP_MS = 90L
        private const val WRITE_ACK_TIMEOUT_MS = 1_500L
        private const val CONNECT_SETTLE_MS = 5_000L
        private const val MTU_CALLBACK_TIMEOUT_MS = 2_500L
        private const val NOTIFY_CALLBACK_TIMEOUT_MS = 3_000L
        private const val A950_INITIAL_DELAY_MS = 200L
        private const val A950_PREPARE_COUNT = 3
        private const val A950_PREPARE_GAP_MS = 500L
        private const val A950_INIT_DELAY_MS = 300L
        private const val A950_READY_DELAY_MS = 200L
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }
}
