package com.byd.wjx.led.a950

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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class A950BleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: A950Preferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val foundDevices = ConcurrentHashMap<String, A950Device>()

    private val _connection = MutableStateFlow(A950ConnectionState())
    val connection: StateFlow<A950ConnectionState> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<A950Device>>(emptyList())
    val devices: StateFlow<List<A950Device>> = _devices.asStateFlow()

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var longWriteCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var pendingWriteUuid: UUID? = null
    @Volatile private var manualDisconnect = false
    @Volatile private var protocolSetupStarted = false
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var protocolInitJob: Job? = null
    private var keepaliveJob: Job? = null
    private var mtuFallbackJob: Job? = null

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
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.DISABLED,
                message = "A950 协议未开启",
            )
            return
        }
        manualDisconnect = false
        if (_connection.value.phase in setOf(
                A950ConnectionPhase.CONNECTING,
                A950ConnectionPhase.DISCOVERING,
                A950ConnectionPhase.INITIALIZING,
            )
        ) return
        if (_connection.value.ready && _connection.value.address.equals(
                prefs.deviceAddress,
                ignoreCase = true,
            )
        ) return
        if (prefs.deviceAddress.isBlank()) {
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.IDLE,
                message = "请先绑定 A950 设备",
            )
            return
        }
        connect(prefs.deviceAddress, prefs.deviceName)
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRuntimePermission()) {
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
                message = "缺少蓝牙权限",
            )
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
                message = "蓝牙未开启",
            )
            return
        }
        stopScan()
        foundDevices.clear()
        _devices.value = emptyList()
        _connection.value = A950ConnectionState(
            phase = A950ConnectionPhase.SCANNING,
            message = "正在查找可连接的蓝牙设备",
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val started = runCatching { scanner.startScan(null, settings, scanCallback) }
        if (started.isFailure) {
            val error = started.exceptionOrNull()
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
                message = "扫描失败：${error?.message.orEmpty()}",
            )
            if (error != null) {
                WjxFileLogger.w(TAG, "A950 BLE scan start failed", error)
            } else {
                WjxFileLogger.w(TAG, "A950 BLE scan start failed")
            }
            return
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
        if (_connection.value.phase == A950ConnectionPhase.SCANNING) {
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.IDLE,
                message = if (_devices.value.isEmpty()) {
                    "未发现可连接设备，请确认屏幕已进入配对状态"
                } else {
                    "请选择设备并验证 A950 协议"
                },
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
                A950ConnectionPhase.CONNECTING,
                A950ConnectionPhase.DISCOVERING,
                A950ConnectionPhase.INITIALIZING,
                A950ConnectionPhase.READY,
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
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "缺少蓝牙权限",
            )
            return
        }
        if (adapter?.isEnabled != true) {
            val disconnected = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
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
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "A950 设备地址无效",
            )
            return
        }
        val connecting = A950ConnectionState(
            phase = A950ConnectionPhase.CONNECTING,
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
            val failed = connecting.copy(
                phase = A950ConnectionPhase.ERROR,
                message = "A950 设备连接启动失败",
            )
            _connection.value = failed
            scheduleReconnect(failed)
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
        _connection.value = A950ConnectionState(
            phase = A950ConnectionPhase.IDLE,
            message = "已断开",
        )
    }

    suspend fun play(preset: A950Preset): Boolean = writeSequence(
        packets = listOf(
            A950Protocol.enableRemoteControlPacket to "remote control enabled",
            A950Protocol.playPreset(preset) to
                "slot=${preset.slotNumber} code=${preset.commandCode}",
        ),
    )

    suspend fun setBrightness(percent: Int): Boolean = write(
        packet = A950Protocol.brightness(percent),
        description = "brightness=${percent.coerceIn(0, 100)}",
    )

    suspend fun uploadGraffiti(payload: ByteArray): Boolean = writeMutex.withLock {
        if (payload.isEmpty() || !_connection.value.ready) return@withLock false
        val chunkSize = A950Protocol.graffitiChunkDataSize(negotiatedMtu)
        if (!writeLongPacketLocked(A950Protocol.graffitiSessionHeader(payload), "graffiti header")) {
            return@withLock false
        }
        var sequence = 0
        var offset = 0
        while (offset < payload.size) {
            val end = (offset + chunkSize).coerceAtMost(payload.size)
            val packet = A950Protocol.graffitiChunk(
                sequence = sequence,
                data = payload.copyOfRange(offset, end),
            )
            if (!writeLongPacketLocked(packet, "graffiti chunk=$sequence")) {
                return@withLock false
            }
            offset = end
            sequence++
        }
        if (!writeLongPacketLocked(A950Protocol.graffitiUploadEndPacket, "graffiti end")) {
            return@withLock false
        }
        delay(GRAFFITI_RENDER_DELAY_MS)
        writeLocked(
            packet = A950Protocol.showGraffitiPacket,
            description = "show graffiti",
            requireReady = true,
            preferWriteWithResponse = true,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun write(
        packet: ByteArray,
        description: String,
        requireReady: Boolean = true,
    ): Boolean = writeMutex.withLock {
        writeLocked(packet, description, requireReady)
    }

    private suspend fun writeSequence(
        packets: List<Pair<ByteArray, String>>,
        requireReady: Boolean = true,
    ): Boolean = writeMutex.withLock {
        packets.all { (packet, description) ->
            writeLocked(packet, description, requireReady)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeLocked(
        packet: ByteArray,
        description: String,
        requireReady: Boolean,
        preferWriteWithResponse: Boolean = false,
    ): Boolean = writeCharacteristicLocked(
        characteristic = writeCharacteristic,
        packet = packet,
        description = description,
        requireReady = requireReady,
        preferWriteWithResponse = preferWriteWithResponse,
        gapMs = A950Protocol.WRITE_GAP_MS,
        recoverOnFailure = true,
    )

    private suspend fun writeLongPacketLocked(packet: ByteArray, description: String): Boolean {
        repeat(LONG_WRITE_RETRIES) { attempt ->
            val sent = writeCharacteristicLocked(
                characteristic = longWriteCharacteristic,
                packet = packet,
                description = description,
                requireReady = true,
                preferWriteWithResponse = true,
                gapMs = A950Protocol.LONG_WRITE_GAP_MS,
                recoverOnFailure = false,
            )
            if (sent) return true
            if (!_connection.value.ready) return false
            delay(LONG_WRITE_RETRY_DELAY_MS)
            WjxFileLogger.w(TAG, "retry $description attempt=${attempt + 2}")
        }
        if (_connection.value.ready) recoverFromWriteFailure("$description retries exhausted")
        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristicLocked(
        characteristic: BluetoothGattCharacteristic?,
        packet: ByteArray,
        description: String,
        requireReady: Boolean,
        preferWriteWithResponse: Boolean,
        gapMs: Long,
        recoverOnFailure: Boolean,
    ): Boolean {
        val gatt = bluetoothGatt
        if (gatt == null || characteristic == null || (requireReady && !_connection.value.ready)) {
            WjxFileLogger.w(TAG, "write skipped not ready description=$description")
            return false
        }
        val supportsWriteWithResponse = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val supportsWriteWithoutResponse = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val writeType = if (preferWriteWithResponse && supportsWriteWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else if (supportsWriteWithoutResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else if (supportsWriteWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            WjxFileLogger.w(TAG, "write skipped unsupported characteristic description=$description")
            return false
        }
        characteristic.writeType = writeType
        val ack = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
            CompletableDeferred<Int>()
        } else {
            null
        }
        pendingWriteAck = ack
        pendingWriteUuid = ack?.let { characteristic.uuid }
        val accepted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, packet, writeType) ==
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
            if (pendingWriteUuid == characteristic.uuid) pendingWriteUuid = null
            if (recoverOnFailure) recoverFromWriteFailure(description)
            return false
        }
        WjxFileLogger.i(
            TAG,
            "write queued $description type=${if (ack == null) "NO_RESPONSE" else "DEFAULT"} " +
                "bytes=${packet.toHex()}",
        )

        if (ack != null) {
            val status = try {
                withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { ack.await() }
            } finally {
                if (pendingWriteAck === ack) pendingWriteAck = null
                if (pendingWriteUuid == characteristic.uuid) pendingWriteUuid = null
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (recoverOnFailure) {
                    recoverFromWriteFailure("$description callback status=$status")
                }
                return false
            }
        }
        delay(gapMs)
        return true
    }

    private fun recoverFromWriteFailure(description: String) {
        val disconnected = _connection.value
        WjxFileLogger.w(TAG, "resetting A950 GATT after write failure description=$description")
        closeGatt()
        if (scheduleReconnect(disconnected)) return
        _connection.value = disconnected.copy(
            phase = A950ConnectionPhase.ERROR,
            message = "A950 设备写入失败",
            retryInSeconds = null,
        )
    }

    private fun startProtocolInitialization(gatt: BluetoothGatt) {
        protocolInitJob?.cancel()
        val job = scope.launch {
            A950Protocol.initializationSteps.forEach { step ->
                delay(step.delayBeforeMs)
                if (gatt !== bluetoothGatt) return@launch
                if (!write(step.packet, "A950 ${step.label}", requireReady = false)) return@launch
            }
            delay(A950Protocol.READY_DELAY_MS)
            markReady(gatt)
        }
        protocolInitJob = job
        job.invokeOnCompletion {
            if (protocolInitJob === job) protocolInitJob = null
        }
    }

    @Synchronized
    private fun completeProtocolSetup(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || protocolSetupStarted) return
        protocolSetupStarted = true
        mtuFallbackJob?.cancel()
        mtuFallbackJob = null
        _connection.value = _connection.value.copy(
            phase = A950ConnectionPhase.INITIALIZING,
            message = "正在初始化 A950 协议",
        )
        startProtocolInitialization(gatt)
    }

    private fun markReady(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt) return
        val current = _connection.value
        val enabled = preferences.state.value.enabled
        preferences.bindDevice(
            address = current.address,
            name = current.name.ifBlank { "A950 设备" },
            enable = enabled,
        )
        if (!enabled) {
            manualDisconnect = true
            closeGatt()
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.DISABLED,
                message = "A950 协议未开启",
            )
            return
        }
        _connection.value = current.copy(
            phase = A950ConnectionPhase.READY,
            message = "A950 协议已连接",
            retryInSeconds = null,
        )
        reconnectJob?.cancel()
        reconnectJob = null
        startKeepalive(gatt)
        WjxFileLogger.i(TAG, "A950 BLE ready address=${current.address} mtu=$negotiatedMtu")
    }

    private fun startKeepalive(gatt: BluetoothGatt) {
        keepaliveJob?.cancel()
        val job = scope.launch {
            while (isActive && gatt === bluetoothGatt && _connection.value.ready) {
                delay(A950Protocol.KEEPALIVE_INTERVAL_MS)
                if (!isActive || gatt !== bluetoothGatt || !_connection.value.ready) return@launch
                if (!write(A950Protocol.keepalivePacket, "A950 keepalive")) return@launch
            }
        }
        keepaliveJob = job
        job.invokeOnCompletion {
            if (keepaliveJob === job) keepaliveJob = null
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun closeGatt() {
        val old = bluetoothGatt
        protocolInitJob?.cancel()
        protocolInitJob = null
        keepaliveJob?.cancel()
        keepaliveJob = null
        mtuFallbackJob?.cancel()
        mtuFallbackJob = null
        bluetoothGatt = null
        writeCharacteristic = null
        longWriteCharacteristic = null
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        pendingWriteUuid = null
        protocolSetupStarted = false
        negotiatedMtu = DEFAULT_MTU
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    private fun scheduleReconnect(disconnected: A950ConnectionState): Boolean {
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
                _connection.value = A950ConnectionState(
                    phase = A950ConnectionPhase.RETRYING,
                    address = currentPrefs.deviceAddress,
                    name = currentPrefs.deviceName,
                    message = "A950 连接已断开，${delayMs / 1_000}秒后重连",
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
            handleScanResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            scanStopJob?.cancel()
            scanStopJob = null
            _connection.value = A950ConnectionState(
                phase = A950ConnectionPhase.ERROR,
                message = "扫描失败：$errorCode",
            )
            WjxFileLogger.w(TAG, "A950 BLE scan failed code=$errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !result.isConnectable) return

        val name = runCatching { result.device.name }.getOrNull()
            .orEmpty()
            .ifBlank { result.scanRecord?.deviceName.orEmpty() }
        val services = result.scanRecord?.serviceUuids.orEmpty().mapTo(linkedSetOf()) { it.uuid }
        val advertisesA950 = A950Protocol.serviceUuid in services
        val item = A950Device(
            address = result.device.address,
            name = name.ifBlank { if (advertisesA950) "A950 设备" else "未知蓝牙设备" },
            rssi = result.rssi,
        )
        val previous = foundDevices.put(item.address, item)
        _devices.value = foundDevices.values.sortedByDescending { it.rssi }
        if (previous == null) {
            WjxFileLogger.i(
                TAG,
                "scan candidate address=${item.address} name=${item.name} " +
                    "advertisesA950=$advertisesA950 services=${services.joinToString()}",
            )
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
                    phase = A950ConnectionPhase.DISCOVERING,
                    message = "正在验证 A950 服务",
                )
                if (!gatt.discoverServices()) {
                    closeGatt()
                    val failed = current.copy(
                        phase = A950ConnectionPhase.ERROR,
                        message = "A950 服务读取启动失败",
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
                        phase = A950ConnectionPhase.ERROR,
                        message = "A950 连接失败，请重新选择设备",
                        retryInSeconds = null,
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val failed = _connection.value.copy(
                    phase = A950ConnectionPhase.ERROR,
                    message = "A950 服务读取失败",
                )
                closeGatt()
                _connection.value = failed
                scheduleReconnect(failed)
                return
            }
            val service = gatt.getService(A950Protocol.serviceUuid)
            val characteristicUuids = service?.characteristics
                .orEmpty()
                .mapTo(linkedSetOf()) { it.uuid }
            if (!A950Protocol.acceptsGattProfile(service?.uuid, characteristicUuids)) {
                _connection.value = _connection.value.copy(
                    phase = A950ConnectionPhase.ERROR,
                    message = "该设备不是 A950 协议设备",
                )
                closeGatt()
                return
            }
            val write = checkNotNull(service?.getCharacteristic(A950Protocol.writeUuid))
            val longWrite = checkNotNull(service.getCharacteristic(A950Protocol.longWriteUuid))
            val supportsWriteWithResponse = write.properties and
                BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val supportsWriteWithoutResponse = write.properties and
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            if (!supportsWriteWithResponse && !supportsWriteWithoutResponse) {
                _connection.value = _connection.value.copy(
                    phase = A950ConnectionPhase.ERROR,
                    message = "A950 写入通道不可用",
                )
                closeGatt()
                return
            }
            val longSupportsWriteWithResponse = longWrite.properties and
                BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val longSupportsWriteWithoutResponse = longWrite.properties and
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            if (!longSupportsWriteWithResponse && !longSupportsWriteWithoutResponse) {
                _connection.value = _connection.value.copy(
                    phase = A950ConnectionPhase.ERROR,
                    message = "A950 涂鸦通道不可用",
                )
                closeGatt()
                return
            }
            writeCharacteristic = write
            longWriteCharacteristic = longWrite
            runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
            val mtuRequested = runCatching {
                gatt.requestMtu(A950Protocol.REQUESTED_MTU)
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

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== bluetoothGatt) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            WjxFileLogger.i(TAG, "A950 MTU status=$status mtu=$negotiatedMtu")
            completeProtocolSetup(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== bluetoothGatt) return
            if (
                characteristic.uuid != A950Protocol.writeUuid &&
                characteristic.uuid != A950Protocol.longWriteUuid
            ) return
            val ack = pendingWriteAck
            if (ack != null && pendingWriteUuid == characteristic.uuid) {
                ack.complete(status)
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS) return
            WjxFileLogger.w(TAG, "write callback failed status=$status")
            scope.launch {
                writeMutex.withLock {
                    if (gatt === bluetoothGatt) recoverFromWriteFailure("callback status=$status")
                }
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "A950BleClient"
        private const val DEFAULT_MTU = 23
        private const val SCAN_WINDOW_MS = 15_000L
        private const val CONNECT_SETTLE_MS = 10_000L
        private const val MTU_CALLBACK_TIMEOUT_MS = 2_500L
        private const val WRITE_ACK_TIMEOUT_MS = 2_500L
        private const val LONG_WRITE_RETRIES = 3
        private const val LONG_WRITE_RETRY_DELAY_MS = 35L
        private const val GRAFFITI_RENDER_DELAY_MS = 120L
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }
}
