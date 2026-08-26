package com.byd.wjx.led.mafu

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MafuBleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MafuPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foundDevices = ConcurrentHashMap<String, MafuDevice>()
    private val notificationDecoder = MafuNotificationFrameDecoder()

    private val _connection = MutableStateFlow(MafuConnectionState())
    val connection: StateFlow<MafuConnectionState> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<MafuDevice>>(emptyList())
    val devices: StateFlow<List<MafuDevice>> = _devices.asStateFlow()

    @Volatile private var manualDisconnect = false
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var protocolInitJob: Job? = null

    private val transport = MafuGattTransport(
        context = context,
        scope = scope,
        listener = object : MafuGattTransport.Listener {
            override fun onConnected(gatt: BluetoothGatt) {
                _connection.value = _connection.value.copy(
                    phase = MafuConnectionPhase.DISCOVERING,
                    message = "正在验证马夫 LED 服务",
                )
            }

            override fun onDisconnected() = handleTransportDisconnected()

            override fun onSubscribing() {
                _connection.value = _connection.value.copy(
                    phase = MafuConnectionPhase.SUBSCRIBING,
                    message = "正在开启马夫 LED 通知",
                )
            }

            override fun onNotificationsEnabled(gatt: BluetoothGatt) = startProtocolInitialization(gatt)

            override fun onFailure(message: String) = failConnection(message)

            override fun onNotification(value: ByteArray) = handleNotification(value)
        },
    )
    private val commandSession = MafuCommandSession(writePacket = ::writePacket)
    private val scanCallback = MafuDeviceScanCallback(
        onDevice = ::recordDiscoveredDevice,
        onFailure = ::handleScanFailure,
    )

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
        val started = runCatching { transport.connect(device) }.getOrElse {
            WjxFileLogger.w(TAG, "connectGatt failed", it)
            false
        }
        if (!started) {
            val failed = connecting.copy(
                phase = MafuConnectionPhase.ERROR,
                message = "马夫 LED 连接启动失败",
            )
            _connection.value = failed
            scheduleReconnect(failed)
            return
        }
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

    suspend fun play(key: MafuRemoteKey, model: MafuRemoteModel): Boolean = commandSession.send(
        buildPacket = { MafuProtocol.playRemoteKey(model, key, it) },
        description = "remoteKey=${key.keyNumber} program=${model.programId(key)}",
    )

    suspend fun playProgramList(): Boolean = commandSession.send(
        buildPacket = MafuProtocol::playProgramList,
        description = "programList=1..60 model=loop",
    )

    suspend fun setBrightness(level: Int): Boolean = commandSession.send(
        buildPacket = { MafuProtocol.brightness(level, it) },
        description = "brightness=${level.coerceIn(1, 15)}",
    )

    suspend fun setScreenPower(enabled: Boolean): Boolean = commandSession.send(
        buildPacket = { MafuProtocol.screenPower(enabled, it) },
        description = "screenPower=${if (enabled) "on" else "off"}",
    )

    suspend fun queryRemote(model: MafuRemoteModel): Boolean = commandSession.send(
        buildPacket = { MafuProtocol.queryRemote(model, it) },
        description = "query remote model=${model.modelCode.toString(16)}",
    )

    suspend fun setRemoteBinding(model: MafuRemoteModel, bindState: Int): Boolean = commandSession.send(
        buildPacket = { MafuProtocol.setRemoteBinding(model, bindState, it) },
        description = "remote binding model=${model.modelCode.toString(16)} state=$bindState",
    )

    private suspend fun writePacket(
        packet: ByteArray,
        description: String,
        requireReady: Boolean,
    ): Boolean {
        if (requireReady && !_connection.value.ready) {
            WjxFileLogger.w(TAG, "write skipped not ready description=$description")
            return false
        }
        return transport.writePacket(packet, description)
    }

    private fun startProtocolInitialization(gatt: BluetoothGatt) {
        protocolInitJob?.cancel()
        val job = scope.launch {
            delay(MafuProtocol.NOTIFY_SETTLE_BEFORE_INIT_MS)
            if (!transport.isCurrent(gatt)) return@launch
            _connection.value = _connection.value.copy(
                phase = MafuConnectionPhase.INITIALIZING,
                message = "正在初始化马夫 LED 协议",
            )
            val deviceInfoReady = commandSession.send(
                buildPacket = MafuProtocol::queryDeviceInfoAll,
                description = "query device info",
                requireReady = false,
            )
            val timeReady = deviceInfoReady && commandSession.send(
                buildPacket = { MafuProtocol.syncTime(it) },
                description = "sync device time",
                requireReady = false,
            )
            if (timeReady && transport.isCurrent(gatt)) {
                markReady(gatt)
            } else if (transport.isCurrent(gatt)) {
                failConnection("马夫 LED 初始化未收到应答")
            }
        }
        protocolInitJob = job
        job.invokeOnCompletion {
            if (protocolInitJob === job) protocolInitJob = null
        }
    }

    private fun markReady(gatt: BluetoothGatt) {
        if (!transport.isCurrent(gatt)) return
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
        WjxFileLogger.i(TAG, "Mafu BLE ready address=${current.address} mtu=${transport.currentMtu}")
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

    @Synchronized
    private fun closeGatt() {
        protocolInitJob?.cancel()
        protocolInitJob = null
        commandSession.reset()
        notificationDecoder.reset()
        transport.close()
    }

    private fun handleTransportDisconnected() {
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

    private fun scheduleReconnect(disconnected: MafuConnectionState): Boolean {
        val prefs = preferences.state.value
        if (!MafuReconnectPolicy.canSchedule(manualDisconnect, prefs, disconnected.address)) return false
        if (reconnectJob?.isActive == true) return true

        val job = scope.launch {
            repeat(MafuReconnectPolicy.MAX_ATTEMPTS) { attempt ->
                val currentPrefs = preferences.state.value
                if (!isActive || !MafuReconnectPolicy.canAttempt(manualDisconnect, currentPrefs)) return@launch
                _connection.value = MafuConnectionState(
                    phase = MafuConnectionPhase.RETRYING,
                    address = currentPrefs.deviceAddress,
                    name = currentPrefs.deviceName,
                    message = "马夫 LED 已断开，5秒后重连 (${attempt + 1}/${MafuReconnectPolicy.MAX_ATTEMPTS})",
                    retryInSeconds = 5,
                )
                delay(MafuReconnectPolicy.RETRY_DELAY_MS)
                if (_connection.value.ready) return@launch
                val latest = preferences.state.value
                if (manualDisconnect || !MafuReconnectPolicy.stillTargets(latest, currentPrefs.deviceAddress)) {
                    return@launch
                }
                connectInternal(latest.deviceAddress, latest.deviceName)
                delay(MafuReconnectPolicy.CONNECT_SETTLE_MS)
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
        notificationDecoder.append(value).forEach { packet ->
            val response = MafuProtocol.parseAcknowledgement(packet)
            if (response == null) {
                WjxFileLogger.w(TAG, "ignored invalid notification bytes=${packet.toHex()}")
                return@forEach
            }
            commandSession.accept(response)
            WjxFileLogger.i(
                TAG,
                "notification sno=${response.sequence} type=${response.responseType} accepted=${response.accepted}",
            )
        }
    }

    private fun recordDiscoveredDevice(device: MafuDevice) {
        foundDevices[device.address] = device
        _devices.value = foundDevices.values.sortedByDescending { it.rssi }
    }

    private fun handleScanFailure(errorCode: Int) {
        scanStopJob?.cancel()
        scanStopJob = null
        _connection.value = MafuConnectionState(
            phase = MafuConnectionPhase.ERROR,
            message = "扫描失败：$errorCode",
        )
        WjxFileLogger.w(TAG, "Mafu BLE scan failed code=$errorCode")
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "MafuBleClient"
        private const val SCAN_WINDOW_MS = 10_000L
        private val ACTIVE_CONNECTION_PHASES = setOf(
            MafuConnectionPhase.CONNECTING,
            MafuConnectionPhase.DISCOVERING,
            MafuConnectionPhase.SUBSCRIBING,
            MafuConnectionPhase.INITIALIZING,
            MafuConnectionPhase.READY,
        )
    }
}
