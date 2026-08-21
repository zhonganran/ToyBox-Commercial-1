package com.byd.wjx.led.mafu

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
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MafuBleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MafuPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val foundDevices = ConcurrentHashMap<String, MafuDevice>()
    private val sequence = AtomicInteger(1)
    private val notificationLock = Any()

    private val _connection = MutableStateFlow(MafuConnectionState())
    val connection: StateFlow<MafuConnectionState> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<MafuDevice>>(emptyList())
    val devices: StateFlow<List<MafuDevice>> = _devices.asStateFlow()

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingProtocolAck: PendingProtocolAck? = null
    @Volatile private var pendingGattWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var manualDisconnect = false
    @Volatile private var transportSetupStarted = false
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    private var notificationBuffer = byteArrayOf()
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var protocolInitJob: Job? = null
    private var notificationSetupJob: Job? = null
    @Volatile private var connectedAtElapsedRealtime = 0L

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
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.DISABLED,
                message = "马夫 LED 协议未开启",
            )
            return
        }
        manualDisconnect = false
        if (_connection.value.phase in ACTIVE_CONNECTION_PHASES) return
        if (_connection.value.ready && _connection.value.address.equals(
                prefs.deviceAddress,
                ignoreCase = true,
            )
        ) return
        if (prefs.deviceAddress.isBlank()) {
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.IDLE,
                message = "请先绑定马夫 LED 设备",
            )
            return
        }
        connect(prefs.deviceAddress, prefs.deviceName)
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRuntimePermission()) {
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                message = "缺少蓝牙权限",
            )
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                message = "蓝牙未开启",
            )
            return
        }
        stopScan()
        foundDevices.clear()
        _devices.value = emptyList()
        _connection.value = MafuConnectionState(
            phase = MafuConnectionPhase.SCANNING,
            message = "正在查找 YS、TL 设备",
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val started = runCatching { scanner.startScan(null, settings, scanCallback) }
        if (started.isFailure) {
            val error = started.exceptionOrNull()
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                message = "扫描失败：${error?.message.orEmpty()}",
            )
            if (error != null) {
                WjxFileLogger.w(TAG, "Mafu BLE scan start failed", error)
            } else {
                WjxFileLogger.w(TAG, "Mafu BLE scan start failed")
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
        if (_connection.value.phase == MafuConnectionPhase.SCANNING) {
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.IDLE,
                message = if (_devices.value.isEmpty()) "未发现 YS、TL 设备" else "请选择马夫 LED 设备",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String = "") {
        manualDisconnect = false
        val current = _connection.value
        if (current.address.equals(address, ignoreCase = true) && current.phase in ACTIVE_CONNECTION_PHASES) {
            return
        }
        reconnectJob?.cancel()
        reconnectJob = null
        connectInternal(address, name)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connectInternal(address: String, name: String) {
        if (!hasRuntimePermission()) {
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "缺少蓝牙权限",
            )
            return
        }
        if (adapter?.isEnabled != true) {
            val failed = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "蓝牙未开启",
            )
            _connection.value = failed
            scheduleReconnect(failed)
            return
        }
        manualDisconnect = false
        stopScan()
        closeGatt()

        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                address = address,
                name = name,
                message = "马夫 LED 设备地址无效",
            )
            return
        }
        val connecting = MafuConnectionState(
            phase = MafuConnectionPhase.CONNECTING,
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
                phase = MafuConnectionPhase.ERROR,
                message = "马夫 LED 连接启动失败",
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
        _connection.value = MafuConnectionState(
            phase = MafuConnectionPhase.IDLE,
            message = "已断开",
        )
    }

    suspend fun play(key: MafuRemoteKey, model: MafuRemoteModel): Boolean = sendCommand(
        buildPacket = { MafuProtocol.playRemoteKey(model, key, it) },
        description = "remoteKey=${key.keyNumber} program=${model.programId(key)}",
    )

    suspend fun playProgramList(): Boolean = sendCommand(
        buildPacket = MafuProtocol::playProgramList,
        description = "programList=1..60 model=loop",
    )

    suspend fun setBrightness(level: Int): Boolean = sendCommand(
        buildPacket = { MafuProtocol.brightness(level, it) },
        description = "brightness=${level.coerceIn(1, 15)}",
    )

    suspend fun setScreenPower(enabled: Boolean): Boolean = sendCommand(
        buildPacket = { MafuProtocol.screenPower(enabled, it) },
        description = "screenPower=${if (enabled) "on" else "off"}",
    )

    suspend fun queryRemote(model: MafuRemoteModel): Boolean = sendCommand(
        buildPacket = { MafuProtocol.queryRemote(model, it) },
        description = "query remote model=${model.modelCode.toString(16)}",
    )

    suspend fun setRemoteBinding(model: MafuRemoteModel, bindState: Int): Boolean = sendCommand(
        buildPacket = { MafuProtocol.setRemoteBinding(model, bindState, it) },
        description = "remote binding model=${model.modelCode.toString(16)} state=$bindState",
    )

    private suspend fun sendCommand(
        buildPacket: (Int) -> ByteArray,
        description: String,
        requireReady: Boolean = true,
    ): Boolean = writeMutex.withLock {
        val sequence = nextSequence()
        val packet = buildPacket(sequence)
        repeat(COMMAND_ATTEMPTS) { attempt ->
            val ack = CompletableDeferred<Boolean>()
            pendingProtocolAck = PendingProtocolAck(sequence, ack)
            val queued = writePacket(packet, description, requireReady)
            if (queued) {
                val accepted = withTimeoutOrNull(MafuProtocol.ACK_TIMEOUT_MS) { ack.await() } == true
                if (pendingProtocolAck?.deferred === ack) pendingProtocolAck = null
                if (accepted) {
                    WjxFileLogger.i(TAG, "command acknowledged $description sno=$sequence")
                    return@withLock true
                }
            } else if (pendingProtocolAck?.deferred === ack) {
                pendingProtocolAck = null
            }
            WjxFileLogger.w(
                TAG,
                "command retry ${attempt + 1}/$COMMAND_ATTEMPTS $description sno=$sequence",
            )
        }
        false
    }

    @SuppressLint("MissingPermission")
    private suspend fun writePacket(
        packet: ByteArray,
        description: String,
        requireReady: Boolean,
    ): Boolean {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic
        if (gatt == null || characteristic == null || (requireReady && !_connection.value.ready)) {
            WjxFileLogger.w(TAG, "write skipped not ready description=$description")
            return false
        }
        val supportsNoResponse = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val writeType = if (supportsNoResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        characteristic.writeType = writeType

        MafuProtocol.splitForWrite(packet).forEachIndexed { index, chunk ->
            val gattAck = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                CompletableDeferred<Int>()
            } else {
                null
            }
            pendingGattWriteAck = gattAck
            val accepted = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(characteristic, chunk, writeType) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = chunk
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            }.getOrDefault(false)
            if (!accepted) {
                if (pendingGattWriteAck === gattAck) pendingGattWriteAck = null
                WjxFileLogger.w(TAG, "BLE write rejected $description chunk=$index")
                return false
            }
            if (gattAck != null) {
                val status = withTimeoutOrNull(GATT_WRITE_TIMEOUT_MS) { gattAck.await() }
                if (pendingGattWriteAck === gattAck) pendingGattWriteAck = null
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    WjxFileLogger.w(TAG, "BLE write callback failed $description status=$status")
                    return false
                }
            }
            delay(MafuProtocol.WRITE_GAP_MS)
        }
        WjxFileLogger.i(TAG, "write queued $description bytes=${packet.toHex()}")
        return true
    }

    private fun startProtocolInitialization(gatt: BluetoothGatt) {
        protocolInitJob?.cancel()
        val job = scope.launch {
            delay(MafuProtocol.NOTIFY_SETTLE_BEFORE_INIT_MS)
            if (gatt !== bluetoothGatt) return@launch
            _connection.value = _connection.value.copy(
                phase = MafuConnectionPhase.INITIALIZING,
                message = "正在初始化马夫 LED 协议",
            )
            val deviceInfoReady = sendCommand(
                buildPacket = MafuProtocol::queryDeviceInfoAll,
                description = "query device info",
                requireReady = false,
            )
            val timeReady = deviceInfoReady && sendCommand(
                buildPacket = { MafuProtocol.syncTime(it) },
                description = "sync device time",
                requireReady = false,
            )
            if (timeReady && gatt === bluetoothGatt) {
                markReady(gatt)
            } else if (gatt === bluetoothGatt) {
                failConnection("马夫 LED 初始化未收到应答")
            }
        }
        protocolInitJob = job
        job.invokeOnCompletion {
            if (protocolInitJob === job) protocolInitJob = null
        }
    }

    @Synchronized
    private fun scheduleNotificationSetup(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || transportSetupStarted) return
        transportSetupStarted = true
        notificationSetupJob?.cancel()
        val elapsedSinceConnection = SystemClock.elapsedRealtime() - connectedAtElapsedRealtime
        val remainingSettle = (
            MafuProtocol.CONNECTION_SETTLE_BEFORE_NOTIFY_MS - elapsedSinceConnection
            ).coerceAtLeast(0L)
        notificationSetupJob = scope.launch {
            delay(remainingSettle)
            if (gatt !== bluetoothGatt) return@launch
            enableNotifications(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val characteristic = notifyCharacteristic ?: return failConnection("马夫 LED 通知通道不可用")
        _connection.value = _connection.value.copy(
            phase = MafuConnectionPhase.SUBSCRIBING,
            message = "正在开启马夫 LED 通知",
        )
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            failConnection("马夫 LED 通知开启失败")
            return
        }
        val descriptor = characteristic.getDescriptor(MafuProtocol.cccdUuid)
        if (descriptor == null) {
            failConnection("马夫 LED 通知描述符缺失")
            return
        }
        val accepted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }.getOrDefault(false)
        if (!accepted) failConnection("马夫 LED 通知订阅失败")
    }

    private fun markReady(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt) return
        val current = _connection.value
        val enabled = preferences.state.value.enabled
        preferences.bindDevice(
            address = current.address,
            name = current.name.ifBlank { "马夫 LED 设备" },
            enable = enabled,
        )
        if (!enabled) {
            manualDisconnect = true
            closeGatt()
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.DISABLED,
                message = "马夫 LED 协议未开启",
            )
            return
        }
        _connection.value = current.copy(
            phase = MafuConnectionPhase.READY,
            message = "马夫 LED 协议已连接",
            retryInSeconds = null,
        )
        reconnectJob?.cancel()
        reconnectJob = null
        WjxFileLogger.i(TAG, "Mafu BLE ready address=${current.address} mtu=$negotiatedMtu")
    }

    private fun failConnection(message: String) {
        val failed = _connection.value.copy(
            phase = MafuConnectionPhase.ERROR,
            message = message,
            retryInSeconds = null,
        )
        closeGatt()
        _connection.value = failed
        WjxFileLogger.w(TAG, "$message; GATT released, waiting for manual retry")
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun closeGatt() {
        val old = bluetoothGatt
        protocolInitJob?.cancel()
        protocolInitJob = null
        notificationSetupJob?.cancel()
        notificationSetupJob = null
        pendingProtocolAck?.deferred?.complete(false)
        pendingProtocolAck = null
        pendingGattWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingGattWriteAck = null
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        transportSetupStarted = false
        connectedAtElapsedRealtime = 0L
        negotiatedMtu = DEFAULT_MTU
        synchronized(notificationLock) { notificationBuffer = byteArrayOf() }
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    private fun scheduleReconnect(disconnected: MafuConnectionState): Boolean {
        val prefs = preferences.state.value
        if (
            manualDisconnect ||
            !prefs.enabled ||
            prefs.deviceAddress.isBlank() ||
            !prefs.deviceAddress.equals(disconnected.address, ignoreCase = true)
        ) return false
        if (reconnectJob?.isActive == true) return true

        val job = scope.launch {
            repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
                val currentPrefs = preferences.state.value
                if (
                    !isActive ||
                    manualDisconnect ||
                    !currentPrefs.enabled ||
                    currentPrefs.deviceAddress.isBlank()
                ) return@launch
                _connection.value = MafuConnectionState(
                    phase = MafuConnectionPhase.RETRYING,
                    address = currentPrefs.deviceAddress,
                    name = currentPrefs.deviceName,
                    message = "马夫 LED 已断开，5秒后重连 (${attempt + 1}/$MAX_RECONNECT_ATTEMPTS)",
                    retryInSeconds = 5,
                )
                delay(RECONNECT_DELAY_MS)
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
            }
            val latest = preferences.state.value
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                address = latest.deviceAddress,
                name = latest.deviceName,
                message = "马夫 LED 重连失败，请手动重试",
            )
        }
        reconnectJob = job
        job.invokeOnCompletion {
            if (reconnectJob === job) reconnectJob = null
        }
        return true
    }

    private fun handleNotification(value: ByteArray) {
        val packets = synchronized(notificationLock) {
            notificationBuffer += value
            val complete = mutableListOf<ByteArray>()
            while (notificationBuffer.isNotEmpty()) {
                val magicOffset = notificationBuffer.indexOfMagic()
                if (magicOffset < 0) {
                    notificationBuffer = if (notificationBuffer.lastOrNull() == 0xAA.toByte()) {
                        byteArrayOf(0xAA.toByte())
                    } else {
                        byteArrayOf()
                    }
                    break
                }
                if (magicOffset > 0) notificationBuffer = notificationBuffer.copyOfRange(
                    magicOffset,
                    notificationBuffer.size,
                )
                if (notificationBuffer.size < PROTOCOL_HEADER_SIZE) break
                val packetSize = MafuProtocol.packetSize(notificationBuffer)
                if (packetSize == null) {
                    notificationBuffer = notificationBuffer.copyOfRange(1, notificationBuffer.size)
                    continue
                }
                if (notificationBuffer.size < packetSize) break
                complete += notificationBuffer.copyOfRange(0, packetSize)
                notificationBuffer = notificationBuffer.copyOfRange(packetSize, notificationBuffer.size)
            }
            complete
        }
        packets.forEach { packet ->
            val response = MafuProtocol.parseAcknowledgement(packet)
            if (response == null) {
                WjxFileLogger.w(TAG, "ignored invalid notification bytes=${packet.toHex()}")
                return@forEach
            }
            val pending = pendingProtocolAck
            if (pending != null && pending.sequence == response.sequence) {
                pending.deferred.complete(response.accepted)
            }
            WjxFileLogger.i(
                TAG,
                "notification sno=${response.sequence} type=${response.responseType} accepted=${response.accepted}",
            )
        }
    }

    private fun nextSequence(): Int {
        while (true) {
            val current = sequence.get()
            val next = if (current >= 0xFFFF) 1 else current + 1
            if (sequence.compareAndSet(current, next)) return current
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = runCatching { result.device.name }.getOrNull()
                .orEmpty()
                .ifBlank { result.scanRecord?.deviceName.orEmpty() }
            if (!MafuProtocol.acceptsDeviceName(name)) return
            val item = MafuDevice(
                address = result.device.address,
                name = name,
                rssi = result.rssi,
            )
            foundDevices[item.address] = item
            _devices.value = foundDevices.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            scanStopJob?.cancel()
            scanStopJob = null
            _connection.value = MafuConnectionState(
                phase = MafuConnectionPhase.ERROR,
                message = "扫描失败：$errorCode",
            )
            WjxFileLogger.w(TAG, "Mafu BLE scan failed code=$errorCode")
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
                connectedAtElapsedRealtime = SystemClock.elapsedRealtime()
                val current = _connection.value
                _connection.value = current.copy(
                    phase = MafuConnectionPhase.DISCOVERING,
                    message = "正在验证马夫 LED 服务",
                )
                if (!gatt.discoverServices()) failConnection("马夫 LED 服务读取启动失败")
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val disconnected = _connection.value
                closeGatt()
                if (!scheduleReconnect(disconnected)) {
                    _connection.value = disconnected.copy(
                        phase = MafuConnectionPhase.ERROR,
                        message = "马夫 LED 连接失败，请重新选择设备",
                        retryInSeconds = null,
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("马夫 LED 服务读取失败")
                return
            }
            val service = gatt.getService(MafuProtocol.serviceUuid)
            val characteristicUuids = service?.characteristics
                .orEmpty()
                .mapTo(linkedSetOf()) { it.uuid }
            if (!MafuProtocol.acceptsGattProfile(service?.uuid, characteristicUuids)) {
                failConnection("该设备不是马夫 LED 协议设备")
                return
            }
            val write = checkNotNull(service?.getCharacteristic(MafuProtocol.writeUuid))
            val notify = checkNotNull(service?.getCharacteristic(MafuProtocol.notifyUuid))
            val supportsWrite = write.properties and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (!supportsWrite) {
                failConnection("马夫 LED 写入通道不可用")
                return
            }
            writeCharacteristic = write
            notifyCharacteristic = notify
            runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
            scheduleNotificationSetup(gatt)
            val mtuRequested = runCatching { gatt.requestMtu(MafuProtocol.REQUESTED_MTU) }
                .getOrDefault(false)
            if (!mtuRequested) {
                WjxFileLogger.w(TAG, "Mafu MTU request rejected; continuing with mtu=$negotiatedMtu")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== bluetoothGatt) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            WjxFileLogger.i(TAG, "Mafu MTU status=$status mtu=$negotiatedMtu")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== bluetoothGatt || descriptor.uuid != MafuProtocol.cccdUuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                startProtocolInitialization(gatt)
            } else {
                failConnection("马夫 LED 通知订阅失败：$status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== bluetoothGatt || characteristic.uuid != MafuProtocol.writeUuid) return
            pendingGattWriteAck?.complete(status)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (gatt !== bluetoothGatt || characteristic.uuid != MafuProtocol.notifyUuid) return
            @Suppress("DEPRECATION")
            handleNotification(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== bluetoothGatt || characteristic.uuid != MafuProtocol.notifyUuid) return
            handleNotification(value)
        }
    }

    private fun ByteArray.indexOfMagic(): Int {
        for (index in 0 until lastIndex) {
            if (this[index] == 0xAA.toByte() && this[index + 1] == 0x55.toByte()) return index
        }
        return -1
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private data class PendingProtocolAck(
        val sequence: Int,
        val deferred: CompletableDeferred<Boolean>,
    )

    private companion object {
        private const val TAG = "MafuBleClient"
        private const val DEFAULT_MTU = 23
        private const val SCAN_WINDOW_MS = 10_000L
        private const val CONNECT_SETTLE_MS = 12_000L
        private const val GATT_WRITE_TIMEOUT_MS = 2_500L
        private const val PROTOCOL_HEADER_SIZE = 10
        private const val COMMAND_ATTEMPTS = 3
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 5_000L
        private val ACTIVE_CONNECTION_PHASES = setOf(
            MafuConnectionPhase.CONNECTING,
            MafuConnectionPhase.DISCOVERING,
            MafuConnectionPhase.SUBSCRIBING,
            MafuConnectionPhase.INITIALIZING,
            MafuConnectionPhase.READY,
        )
    }
}
