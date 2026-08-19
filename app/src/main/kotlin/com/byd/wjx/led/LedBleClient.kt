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

    private val _duduSceneCatalog = MutableStateFlow(DuduFff0SceneCatalogState())
    val duduSceneCatalog: StateFlow<DuduFff0SceneCatalogState> = _duduSceneCatalog.asStateFlow()

    private val duduNotificationDecoder = DuduFff0NotificationDecoder()

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var pendingDuduDeviceAck: PendingDuduDeviceAck? = null
    @Volatile private var pendingDuduSceneQuery: PendingDuduSceneQuery? = null
    @Volatile private var manualDisconnect = false
    @Volatile private var activeProtocolSpec: LedGattProtocol? = null
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    @Volatile private var protocolSetupStarted = false
    @Volatile private var protocolInitializationStarted = false
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var shutdownJob: Job? = null
    private var protocolInitJob: Job? = null
    private var keepaliveJob: Job? = null
    private var sceneRefreshJob: Job? = null
    private var duduSceneScanJob: Job? = null
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
        val protocol = prefs.selectedProtocol
        val profile = prefs.activeProtocol
        if (!profile.enabled) {
            _connection.value = LedConnectionState(
                phase = LedConnectionPhase.DISABLED,
                protocol = protocol,
                message = "${protocol.title}未开启",
            )
            return
        }
        if (profile.deviceAddress.isBlank()) {
            _connection.value = LedConnectionState(
                phase = LedConnectionPhase.IDLE,
                protocol = protocol,
                message = "请先绑定${protocol.title}设备",
            )
            return
        }
        connect(profile.deviceAddress, profile.deviceName, protocol)
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
        _connection.value = LedConnectionState(
            phase = LedConnectionPhase.SCANNING,
            message = "正在自动识别LED设备",
        )
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
                protocol = preferences.state.value.selectedProtocol,
                message = if (_devices.value.isEmpty()) "未发现LED设备" else "请选择LED设备",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        address: String,
        name: String = "",
        protocol: LedProtocolKind? = null,
    ) {
        shutdownJob?.cancel()
        shutdownJob = null
        manualDisconnect = false
        val current = _connection.value
        if (
            current.address.equals(address, ignoreCase = true) &&
            (protocol == null || current.protocol == protocol) &&
            current.phase in setOf(
                LedConnectionPhase.CONNECTING,
                LedConnectionPhase.DISCOVERING,
                LedConnectionPhase.READY,
            )
        ) return
        reconnectJob?.cancel()
        reconnectJob = null
        connectInternal(address, name, protocol)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connectInternal(
        address: String,
        name: String = "",
        protocol: LedProtocolKind?,
    ) {
        if (!hasRuntimePermission()) {
            _connection.value = LedConnectionState(
                phase = LedConnectionPhase.ERROR,
                address = address,
                name = name,
                protocol = protocol,
                message = "缺少蓝牙权限",
            )
            return
        }
        if (adapter?.isEnabled != true) {
            _connection.value = LedConnectionState(
                phase = LedConnectionPhase.ERROR,
                address = address,
                name = name,
                protocol = protocol,
                message = "蓝牙未开启",
            )
            scheduleReconnect()
            return
        }
        manualDisconnect = false
        stopScan()
        closeGatt()

        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _connection.value = LedConnectionState(
                phase = LedConnectionPhase.ERROR,
                address = address,
                name = name,
                protocol = protocol,
                message = "LED设备地址无效",
            )
            return
        }
        _connection.value = LedConnectionState(
            phase = LedConnectionPhase.CONNECTING,
            address = address,
            name = name,
            protocol = protocol,
            message = "正在连接 ${name.ifBlank { address }}",
        )
        WjxFileLogger.i(TAG, "connect address=$address name=$name expectedProtocol=$protocol")
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
        val protocol = activeProtocolSpec ?: return false
        val presetId = if (protocol.kind == LedProtocolKind.DUDU_FFF0) {
            preferences.state.value.duduFff0.sceneSlots[scene]
                ?: DuduFff0LedProtocol.DEFAULT_SCENE_SLOT
        } else {
            protocol.presetId(scene)
        }
        if (
            protocol.kind == LedProtocolKind.DUDU_FFF0 &&
            _duduSceneCatalog.value.scanComplete &&
            presetId !in _duduSceneCatalog.value.availableSlots
        ) {
            WjxFileLogger.w(TAG, "scene skipped unavailable FFF0 slot=$presetId scene=${scene.name}")
            return false
        }
        val packet = protocol.playScene(scene, presetId)
        val description = "scene=${scene.name} protocol=${protocol.kind} preset=$presetId"
        val sent = write(packet, description)
        if (sent) {
            startSceneRefresh(protocol, packet, description)
        }
        return sent
    }

    suspend fun setBrightness(percent: Int): Boolean {
        val protocol = activeProtocolSpec ?: return false
        return write(
            protocol.brightness(percent),
            "brightness=${percent.coerceIn(0, 100)} protocol=${protocol.kind}",
        )
    }

    fun refreshDuduSceneSlots() {
        val gatt = bluetoothGatt ?: return
        if (activeProtocolSpec?.kind != LedProtocolKind.DUDU_FFF0 || !_connection.value.ready) return
        startDuduSceneScan(gatt)
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
        val protocol = activeProtocolSpec ?: return@withLock false
        val deviceAckCommand = if (protocol.kind == LedProtocolKind.DUDU_FFF0) {
            DuduFff0LedProtocol.deviceAcknowledgementCommand(packet)
        } else {
            null
        }
        val deviceAck = deviceAckCommand?.let { command ->
            PendingDuduDeviceAck(command, CompletableDeferred()).also { pendingDuduDeviceAck = it }
        }
        val writeWithResponse = !protocol.forceWriteWithoutResponse &&
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        characteristic.writeType = if (writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val chunks = protocol.splitForWrite(packet)
        chunks.forEachIndexed { index, chunk ->
            val ack = if (writeWithResponse) CompletableDeferred<Int>() else null
            pendingWriteAck = ack
            val accepted = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(characteristic, chunk, characteristic.writeType) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = chunk
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            }.getOrDefault(false)
            if (!accepted) {
                if (pendingWriteAck === ack) pendingWriteAck = null
                clearPendingDuduDeviceAck(deviceAck)
                WjxFileLogger.w(TAG, "write rejected description=$description chunk=${index + 1}/${chunks.size}")
                recoverFromWriteFailure(description)
                return@withLock false
            }
            WjxFileLogger.i(
                TAG,
                "write queued $description chunk=${index + 1}/${chunks.size} " +
                    "type=${if (writeWithResponse) "DEFAULT" else "NO_RESPONSE"} bytes=${chunk.toHex()}",
            )

            val chunkSucceeded = if (ack != null) {
                val status = withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { ack.await() }
                if (pendingWriteAck === ack) pendingWriteAck = null
                when {
                    status == null -> {
                        WjxFileLogger.w(TAG, "write timeout description=$description chunk=${index + 1}/${chunks.size}")
                        recoverFromWriteFailure(description)
                        false
                    }
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        WjxFileLogger.w(
                            TAG,
                            "write failed status=$status description=$description chunk=${index + 1}/${chunks.size}",
                        )
                        recoverFromWriteFailure(description)
                        false
                    }
                    else -> true
                }
            } else {
                true
            }
            if (!chunkSucceeded) {
                clearPendingDuduDeviceAck(deviceAck)
                return@withLock false
            }
            delay(protocol.writeGapMs)
        }
        if (deviceAck == null) return@withLock true

        val acknowledged = withTimeoutOrNull(DEVICE_ACK_TIMEOUT_MS) { deviceAck.response.await() } == true
        clearPendingDuduDeviceAck(deviceAck)
        if (!acknowledged) {
            WjxFileLogger.w(TAG, "device ACK timeout command=${deviceAck.command} description=$description")
            recoverFromWriteFailure("$description device ACK")
            return@withLock false
        }
        WjxFileLogger.i(TAG, "device ACK success command=${deviceAck.command} description=$description")
        true
    }

    private fun clearPendingDuduDeviceAck(expected: PendingDuduDeviceAck?) {
        if (expected != null && pendingDuduDeviceAck === expected) pendingDuduDeviceAck = null
    }

    private fun recoverFromWriteFailure(description: String) {
        val current = _connection.value
        WjxFileLogger.w(TAG, "resetting GATT after write failure description=$description")
        closeGatt()
        val snapshot = preferences.state.value
        val protocol = current.protocol
        val profile = protocol?.let(snapshot::protocol)
        val canReconnect = !manualDisconnect &&
            protocol != null &&
            snapshot.selectedProtocol == protocol &&
            profile?.enabled == true &&
            profile.deviceAddress.equals(current.address, ignoreCase = true)
        if (canReconnect) {
            _connection.value = current.copy(
                phase = LedConnectionPhase.RETRYING,
                message = "LED写入失败，正在重新连接",
                retryInSeconds = 1,
            )
            scheduleReconnect()
        } else {
            _connection.value = current.copy(
                phase = LedConnectionPhase.ERROR,
                message = "LED设备未返回有效应答",
                retryInSeconds = null,
            )
        }
    }

    private fun markReady(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt) return
        val current = _connection.value
        val protocol = activeProtocolSpec ?: return
        _connection.value = current.copy(
            phase = LedConnectionPhase.READY,
            protocol = protocol.kind,
            message = "${protocol.kind.title}已连接",
            retryInSeconds = null,
        )
        reconnectJob?.cancel()
        reconnectJob = null
        preferences.bindDetectedDevice(
            protocol = protocol.kind,
            address = current.address,
            name = current.name.ifBlank { "LED设备" },
        )
        foundDevices[current.address]?.let { detected ->
            foundDevices[current.address] = detected.copy(protocol = protocol.kind)
            _devices.value = foundDevices.values.sortedByDescending { it.rssi }
        }
        startProtocolKeepalive(gatt, protocol)
        if (protocol.kind == LedProtocolKind.DUDU_FFF0) startDuduSceneScan(gatt)
        WjxFileLogger.i(TAG, "BLE ready address=${current.address} protocol=${protocol.kind} mtu=$negotiatedMtu")
    }

    private fun startProtocolInitialization(gatt: BluetoothGatt, protocol: LedGattProtocol) {
        protocolInitJob?.cancel()
        val job = scope.launch {
            protocol.initializationSteps.forEach { step ->
                delay(step.delayBeforeMs)
                if (gatt !== bluetoothGatt || activeProtocolSpec !== protocol) return@launch
                if (!write(
                        step.packet,
                        "${protocol.kind} ${step.label}",
                        requireReady = false,
                    )
                ) return@launch
            }
            delay(protocol.readyDelayMs)
            markReady(gatt)
        }
        protocolInitJob = job
        job.invokeOnCompletion {
            if (protocolInitJob === job) protocolInitJob = null
        }
    }

    private fun startProtocolKeepalive(gatt: BluetoothGatt, protocol: LedGattProtocol) {
        val packet = protocol.keepalivePacket ?: return
        val intervalMs = protocol.keepaliveIntervalMs ?: return
        keepaliveJob?.cancel()
        val job = scope.launch {
            while (
                isActive &&
                gatt === bluetoothGatt &&
                activeProtocolSpec === protocol &&
                _connection.value.ready
            ) {
                delay(intervalMs)
                if (!isActive || gatt !== bluetoothGatt || !_connection.value.ready) return@launch
                if (!write(packet, "${protocol.kind} keepalive")) return@launch
            }
        }
        keepaliveJob = job
        job.invokeOnCompletion {
            if (keepaliveJob === job) keepaliveJob = null
        }
    }

    private fun startSceneRefresh(
        protocol: LedGattProtocol,
        packet: ByteArray,
        description: String,
    ) {
        sceneRefreshJob?.cancel()
        val intervalMs = protocol.sceneRefreshIntervalMs ?: return
        val gatt = bluetoothGatt ?: return
        val job = scope.launch {
            while (
                isActive &&
                gatt === bluetoothGatt &&
                activeProtocolSpec === protocol &&
                _connection.value.ready
            ) {
                delay(intervalMs)
                if (!isActive || gatt !== bluetoothGatt || !_connection.value.ready) return@launch
                if (!write(packet, "$description refresh")) return@launch
            }
        }
        sceneRefreshJob = job
        job.invokeOnCompletion {
            if (sceneRefreshJob === job) sceneRefreshJob = null
        }
    }

    private fun startDuduSceneScan(gatt: BluetoothGatt) {
        duduSceneScanJob?.cancel()
        pendingDuduSceneQuery?.response?.cancel()
        pendingDuduSceneQuery = null
        _duduSceneCatalog.value = DuduFff0SceneCatalogState(scanning = true)

        val job = scope.launch {
            val remaining = DuduFff0LedProtocol.SCENE_SLOT_RANGE.toMutableSet()
            val available = linkedSetOf<Int>()
            var attempts = 0

            while (
                isActive &&
                remaining.isNotEmpty() &&
                attempts < MAX_SCENE_QUERY_ATTEMPTS &&
                gatt === bluetoothGatt &&
                activeProtocolSpec?.kind == LedProtocolKind.DUDU_FFF0 &&
                _connection.value.ready
            ) {
                attempts++
                val requestedSlots = remaining.take(MAX_SCENE_QUERY_SLOTS).toSet()
                val pending = PendingDuduSceneQuery(
                    requestedSlots = requestedSlots,
                    response = CompletableDeferred(),
                )
                pendingDuduSceneQuery = pending
                val sent = write(
                    DuduFff0LedProtocol.querySceneSlots(requestedSlots.toList()),
                    "DUDU_FFF0 query slots=${requestedSlots.joinToString()}",
                )
                if (!sent) {
                    if (pendingDuduSceneQuery === pending) pendingDuduSceneQuery = null
                    if (gatt === bluetoothGatt) {
                        _duduSceneCatalog.value = _duduSceneCatalog.value.copy(
                            scanning = false,
                            error = "场景读取失败",
                        )
                    }
                    return@launch
                }

                val response = withTimeoutOrNull(SCENE_QUERY_TIMEOUT_MS) { pending.response.await() }
                if (pendingDuduSceneQuery === pending) pendingDuduSceneQuery = null
                val inspected = response.orEmpty().filterKeys { it in requestedSlots }
                if (inspected.isEmpty()) continue

                remaining.removeAll(inspected.keys)
                available += inspected.filterValues { it }.keys
                _duduSceneCatalog.value = DuduFff0SceneCatalogState(
                    scanning = true,
                    scannedSlots = DuduFff0LedProtocol.SCENE_SLOT_RANGE.count() - remaining.size,
                    availableSlots = available.toSet(),
                )
            }

            if (gatt !== bluetoothGatt || activeProtocolSpec?.kind != LedProtocolKind.DUDU_FFF0) return@launch
            val completed = remaining.isEmpty()
            _duduSceneCatalog.value = DuduFff0SceneCatalogState(
                scanning = false,
                scannedSlots = DuduFff0LedProtocol.SCENE_SLOT_RANGE.count() - remaining.size,
                availableSlots = available.toSet(),
                scanComplete = completed,
                error = if (completed) null else "场景读取不完整，请重新读取",
            )
        }
        duduSceneScanJob = job
        job.invokeOnCompletion {
            if (duduSceneScanJob === job) duduSceneScanJob = null
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
            if (activeProtocolSpec?.requiresNotifications == true) {
                failProtocolSetup(gatt, "FFF0通知通道不可用")
                return
            }
            completeProtocolInitialization(gatt)
            return
        }
        val notificationEnabled = runCatching {
            gatt.setCharacteristicNotification(notify, true)
        }.getOrDefault(false)
        val protocol = activeProtocolSpec ?: return
        val descriptor = notify.getDescriptor(protocol.cccdUuid)
        if (!notificationEnabled || descriptor == null) {
            if (protocol.requiresNotifications) {
                failProtocolSetup(gatt, "FFF0通知订阅失败")
                return
            }
            completeProtocolInitialization(gatt)
            return
        }
        val notificationValue = when {
            notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> null
        }
        if (notificationValue == null) {
            if (protocol.requiresNotifications) {
                failProtocolSetup(gatt, "FFF0特征不支持通知")
            } else {
                completeProtocolInitialization(gatt)
            }
            return
        }
        val descriptorAccepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, notificationValue) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = notificationValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!descriptorAccepted) {
            if (protocol.requiresNotifications) {
                failProtocolSetup(gatt, "FFF0通知描述符写入失败")
            } else {
                completeProtocolInitialization(gatt)
            }
        } else {
            notifyFallbackJob?.cancel()
            notifyFallbackJob = scope.launch {
                delay(NOTIFY_CALLBACK_TIMEOUT_MS)
                if (protocol.requiresNotifications) {
                    failProtocolSetup(gatt, "FFF0通知订阅超时")
                } else {
                    WjxFileLogger.w(TAG, "notify descriptor callback timeout; continuing initialization")
                    completeProtocolInitialization(gatt)
                }
            }
        }
    }

    @Synchronized
    private fun failProtocolSetup(gatt: BluetoothGatt, message: String) {
        if (gatt !== bluetoothGatt) return
        WjxFileLogger.w(TAG, message)
        _connection.value = _connection.value.copy(
            phase = LedConnectionPhase.ERROR,
            message = message,
        )
        closeGatt()
    }

    @Synchronized
    private fun completeProtocolInitialization(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || protocolInitializationStarted) return
        protocolInitializationStarted = true
        notifyFallbackJob?.cancel()
        notifyFallbackJob = null
        val protocol = activeProtocolSpec ?: return
        if (protocol.initializationSteps.isEmpty()) {
            markReady(gatt)
        } else {
            startProtocolInitialization(gatt, protocol)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val old = bluetoothGatt
        protocolInitJob?.cancel()
        protocolInitJob = null
        keepaliveJob?.cancel()
        keepaliveJob = null
        sceneRefreshJob?.cancel()
        sceneRefreshJob = null
        duduSceneScanJob?.cancel()
        duduSceneScanJob = null
        mtuFallbackJob?.cancel()
        mtuFallbackJob = null
        notifyFallbackJob?.cancel()
        notifyFallbackJob = null
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        pendingDuduDeviceAck?.response?.complete(false)
        pendingDuduDeviceAck = null
        pendingDuduSceneQuery?.response?.cancel()
        pendingDuduSceneQuery = null
        duduNotificationDecoder.reset()
        _duduSceneCatalog.value = DuduFff0SceneCatalogState()
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        activeProtocolSpec = null
        negotiatedMtu = DEFAULT_MTU
        protocolSetupStarted = false
        protocolInitializationStarted = false
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    private fun scheduleReconnect() {
        val disconnected = _connection.value
        val protocol = disconnected.protocol ?: return
        val initialPreferences = preferences.state.value
        val initialProfile = initialPreferences.protocol(protocol)
        if (
            manualDisconnect ||
            initialPreferences.selectedProtocol != protocol ||
            !initialProfile.enabled ||
            !initialProfile.deviceAddress.equals(disconnected.address, ignoreCase = true)
        ) return
        if (reconnectJob?.isActive == true) return
        val job = scope.launch {
            var attempt = 0
            while (isActive) {
                val prefs = preferences.state.value
                val profile = prefs.protocol(protocol)
                if (
                    !isActive ||
                    manualDisconnect ||
                    prefs.selectedProtocol != protocol ||
                    !profile.enabled ||
                    profile.deviceAddress.isBlank()
                ) return@launch
                val delayMs = RECONNECT_DELAYS_MS[attempt.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
                _connection.value = LedConnectionState(
                    phase = LedConnectionPhase.RETRYING,
                    address = profile.deviceAddress,
                    name = profile.deviceName,
                    protocol = protocol,
                    message = "${protocol.title}连接已断开，${delayMs / 1_000}秒后重连",
                    retryInSeconds = (delayMs / 1_000).toInt(),
                )
                delay(delayMs)
                if (_connection.value.ready) return@launch
                val latest = preferences.state.value
                val latestProfile = latest.protocol(protocol)
                if (
                    latest.selectedProtocol != protocol ||
                    !latestProfile.enabled ||
                    !latestProfile.deviceAddress.equals(profile.deviceAddress, ignoreCase = true)
                ) return@launch
                connectInternal(profile.deviceAddress, profile.deviceName, protocol)
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
            val name = runCatching { result.device.name }.getOrNull()
                .orEmpty()
                .ifBlank { result.scanRecord?.deviceName.orEmpty() }
            val services = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            val advertisedProtocols = LedProtocolKind.entries
                .filterTo(linkedSetOf()) { LedProtocols.forKind(it).serviceUuid in services }
            val advertisedProtocol = detectLedProtocol(expected = null, available = advertisedProtocols)
            val nameLooksLikeLed = name.contains("LED", ignoreCase = true) ||
                name.contains("PIXEL", ignoreCase = true) ||
                name.contains("A950", ignoreCase = true) ||
                name.contains("DUDU", ignoreCase = true) ||
                name.contains("HAPPY", ignoreCase = true) ||
                name.contains("马夫", ignoreCase = true)
            val previous = foundDevices[result.device.address]
            val resolvedProtocol = advertisedProtocol ?: previous?.protocol
            if (resolvedProtocol == null && advertisedProtocols.isEmpty() && !nameLooksLikeLed) return
            val item = LedDevice(
                address = result.device.address,
                name = name.ifBlank { "LED设备" },
                rssi = result.rssi,
                protocol = resolvedProtocol,
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
                val disconnected = _connection.value
                closeGatt()
                scheduleReconnect()
                if (reconnectJob?.isActive != true) {
                    _connection.value = disconnected.copy(
                        phase = LedConnectionPhase.ERROR,
                        message = "LED连接失败，请重新选择设备",
                        retryInSeconds = null,
                    )
                }
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
            val expectedProtocol = _connection.value.protocol
            val availableProtocols = LedProtocolKind.entries.mapNotNull { kind ->
                val protocol = LedProtocols.forKind(kind)
                val service = gatt.getService(protocol.serviceUuid) ?: return@mapNotNull null
                val write = service.getCharacteristic(protocol.writeUuid) ?: return@mapNotNull null
                val notify = service.getCharacteristic(protocol.notifyUuid)
                if (protocol.requiresNotifications) {
                    val supportsNotification = notify != null &&
                        notify.getDescriptor(protocol.cccdUuid) != null &&
                        notify.properties and (
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                                BluetoothGattCharacteristic.PROPERTY_INDICATE
                            ) != 0
                    if (!supportsNotification) return@mapNotNull null
                }
                kind to Triple(protocol, write, notify)
            }.toMap()
            val detectedKind = detectLedProtocol(expectedProtocol, availableProtocols.keys)
            if (detectedKind == null) {
                val message = if (availableProtocols.isEmpty()) {
                    "设备未发现支持的LED协议"
                } else {
                    "设备协议无法唯一识别，请重新扫描"
                }
                _connection.value = _connection.value.copy(
                    phase = LedConnectionPhase.ERROR,
                    message = message,
                )
                closeGatt()
                return
            }
            val (protocol, write, notify) = checkNotNull(availableProtocols[detectedKind])
            activeProtocolSpec = protocol
            _connection.value = _connection.value.copy(
                protocol = detectedKind,
                message = "已识别${detectedKind.title}，正在初始化",
            )
            WjxFileLogger.i(
                TAG,
                "services detected expected=$expectedProtocol actual=$detectedKind available=${availableProtocols.keys}",
            )
            writeCharacteristic = write
            notifyCharacteristic = notify
            val requestedMtu = protocol.requestedMtu
            if (requestedMtu == null) {
                completeProtocolSetup(gatt)
                return
            }
            runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
            val mtuRequested = runCatching { gatt.requestMtu(requestedMtu) }.getOrDefault(false)
            if (mtuRequested) {
                mtuFallbackJob?.cancel()
                mtuFallbackJob = scope.launch {
                    delay(MTU_CALLBACK_TIMEOUT_MS)
                    WjxFileLogger.w(TAG, "$detectedKind MTU callback timeout; continuing with mtu=$negotiatedMtu")
                    completeProtocolSetup(gatt)
                }
            } else {
                WjxFileLogger.w(TAG, "$detectedKind MTU request rejected; continuing with mtu=$negotiatedMtu")
                completeProtocolSetup(gatt)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== bluetoothGatt || activeProtocolSpec?.requestedMtu == null) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            WjxFileLogger.i(TAG, "${activeProtocolSpec?.kind} MTU status=$status mtu=$negotiatedMtu")
            completeProtocolSetup(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== bluetoothGatt || descriptor.uuid != activeProtocolSpec?.cccdUuid) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (activeProtocolSpec?.requiresNotifications == true) {
                    failProtocolSetup(gatt, "FFF0通知订阅失败：$status")
                    return
                }
                WjxFileLogger.w(TAG, "notify descriptor failed status=$status; continuing without notifications")
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
            handleNotification(bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== bluetoothGatt || characteristic.uuid != notifyCharacteristic?.uuid) return
            handleNotification(value)
        }
    }

    private fun handleNotification(bytes: ByteArray) {
        WjxFileLogger.i(TAG, "notify bytes=${bytes.toHex()}")
        if (activeProtocolSpec?.kind != LedProtocolKind.DUDU_FFF0) return
        duduNotificationDecoder.append(bytes).forEach { response ->
            WjxFileLogger.i(
                TAG,
                "FFF0 response command=${response.command} payload=${response.payload.toHex()}",
            )
            val deviceAck = pendingDuduDeviceAck
            if (
                response.isSuccessAcknowledgement &&
                deviceAck != null &&
                deviceAck.command == response.command
            ) {
                deviceAck.response.complete(true)
            }

            val sceneSlots = response.sceneSlots() ?: return@forEach
            val sceneQuery = pendingDuduSceneQuery ?: return@forEach
            if (sceneSlots.keys.any { it in sceneQuery.requestedSlots }) {
                sceneQuery.response.complete(sceneSlots)
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "LedBleClient"
        private const val DEFAULT_MTU = 23
        private const val SCAN_WINDOW_MS = 10_000L
        private const val WRITE_ACK_TIMEOUT_MS = 1_500L
        private const val DEVICE_ACK_TIMEOUT_MS = 2_000L
        private const val SCENE_QUERY_TIMEOUT_MS = 2_000L
        private const val CONNECT_SETTLE_MS = 5_000L
        private const val MTU_CALLBACK_TIMEOUT_MS = 2_500L
        private const val NOTIFY_CALLBACK_TIMEOUT_MS = 3_000L
        private const val MAX_SCENE_QUERY_SLOTS = 8
        private const val MAX_SCENE_QUERY_ATTEMPTS = 10
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }

    private data class PendingDuduDeviceAck(
        val command: Int,
        val response: CompletableDeferred<Boolean>,
    )

    private data class PendingDuduSceneQuery(
        val requestedSlots: Set<Int>,
        val response: CompletableDeferred<Map<Int, Boolean>>,
    )
}
