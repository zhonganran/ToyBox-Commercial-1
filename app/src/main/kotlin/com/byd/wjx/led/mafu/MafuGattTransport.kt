package com.byd.wjx.led.mafu

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class MafuGattTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected(gatt: BluetoothGatt)
        fun onDisconnected()
        fun onSubscribing()
        fun onNotificationsEnabled(gatt: BluetoothGatt)
        fun onFailure(message: String)
        fun onNotification(value: ByteArray)
    }

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    @Volatile private var connectedAtElapsedRealtime = 0L
    private var notificationSetupStarted = false
    private var notificationSetupJob: Job? = null

    val currentMtu: Int get() = negotiatedMtu

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            ?: return false
        bluetoothGatt = gatt
        return true
    }

    fun isCurrent(gatt: BluetoothGatt): Boolean = gatt === bluetoothGatt

    @SuppressLint("MissingPermission")
    suspend fun writePacket(packet: ByteArray, description: String): Boolean {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic
        if (gatt == null || characteristic == null) {
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
            val acknowledgement = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                CompletableDeferred<Int>()
            } else {
                null
            }
            pendingWriteAck = acknowledgement
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
                clearIfPending(acknowledgement)
                WjxFileLogger.w(TAG, "BLE write rejected $description chunk=$index")
                return false
            }
            if (acknowledgement != null) {
                val status = withTimeoutOrNull(GATT_WRITE_TIMEOUT_MS) { acknowledgement.await() }
                clearIfPending(acknowledgement)
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

    @SuppressLint("MissingPermission")
    @Synchronized
    fun close() {
        val old = bluetoothGatt
        notificationSetupJob?.cancel()
        notificationSetupJob = null
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        notificationSetupStarted = false
        connectedAtElapsedRealtime = 0L
        negotiatedMtu = DEFAULT_MTU
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }

    @Synchronized
    private fun scheduleNotificationSetup(gatt: BluetoothGatt) {
        if (!isCurrent(gatt) || notificationSetupStarted) return
        notificationSetupStarted = true
        notificationSetupJob?.cancel()
        val elapsed = SystemClock.elapsedRealtime() - connectedAtElapsedRealtime
        val remaining = (MafuProtocol.CONNECTION_SETTLE_BEFORE_NOTIFY_MS - elapsed).coerceAtLeast(0L)
        notificationSetupJob = scope.launch {
            delay(remaining)
            if (isCurrent(gatt)) enableNotifications(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val characteristic = notifyCharacteristic ?: return listener.onFailure("马夫 LED 通知通道不可用")
        listener.onSubscribing()
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            listener.onFailure("马夫 LED 通知开启失败")
            return
        }
        val descriptor = characteristic.getDescriptor(MafuProtocol.cccdUuid)
            ?: return listener.onFailure("马夫 LED 通知描述符缺失")
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
        if (!accepted) listener.onFailure("马夫 LED 通知订阅失败")
    }

    private fun clearIfPending(acknowledgement: CompletableDeferred<Int>?) {
        if (pendingWriteAck === acknowledgement) pendingWriteAck = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrent(gatt)) {
                runCatching { gatt.close() }
                return
            }
            WjxFileLogger.i(TAG, "connection state status=$status newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connectedAtElapsedRealtime = SystemClock.elapsedRealtime()
                listener.onConnected(gatt)
                if (!gatt.discoverServices()) listener.onFailure("马夫 LED 服务读取启动失败")
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                listener.onDisconnected()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrent(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onFailure("马夫 LED 服务读取失败")
                return
            }
            val service = gatt.getService(MafuProtocol.serviceUuid)
            val characteristicUuids = service?.characteristics
                .orEmpty()
                .mapTo(linkedSetOf()) { it.uuid }
            if (!MafuProtocol.acceptsGattProfile(service?.uuid, characteristicUuids)) {
                listener.onFailure("该设备不是马夫 LED 协议设备")
                return
            }
            val write = service?.getCharacteristic(MafuProtocol.writeUuid)
            val notify = service?.getCharacteristic(MafuProtocol.notifyUuid)
            if (write == null || notify == null) {
                listener.onFailure("该设备不是马夫 LED 协议设备")
                return
            }
            val supportsWrite = write.properties and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (!supportsWrite) {
                listener.onFailure("马夫 LED 写入通道不可用")
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
            if (!isCurrent(gatt)) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            WjxFileLogger.i(TAG, "Mafu MTU status=$status mtu=$negotiatedMtu")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!isCurrent(gatt) || descriptor.uuid != MafuProtocol.cccdUuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onNotificationsEnabled(gatt)
            } else {
                listener.onFailure("马夫 LED 通知订阅失败：$status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (!isCurrent(gatt) || characteristic.uuid != MafuProtocol.writeUuid) return
            pendingWriteAck?.complete(status)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (!isCurrent(gatt) || characteristic.uuid != MafuProtocol.notifyUuid) return
            @Suppress("DEPRECATION")
            listener.onNotification(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (!isCurrent(gatt) || characteristic.uuid != MafuProtocol.notifyUuid) return
            listener.onNotification(value)
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02X".format(it) }

    private companion object {
        private const val TAG = "MafuBleClient"
        private const val DEFAULT_MTU = 23
        private const val GATT_WRITE_TIMEOUT_MS = 2_500L
    }
}
