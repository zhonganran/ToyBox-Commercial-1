package com.byd.wjx.led.ipixelfa

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal class IpixelFaGattTransport(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected(gatt: BluetoothGatt)
        fun onDisconnected()
        fun onReady(gatt: BluetoothGatt)
        fun onFailure(message: String, reconnect: Boolean)
    }

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null

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
        val writeWithResponse = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val writeType = if (writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        characteristic.writeType = writeType
        val acknowledgement = if (writeWithResponse) CompletableDeferred<Int>() else null
        pendingWriteAck = acknowledgement
        val accepted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, packet, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = packet
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }.getOrDefault(false)
        if (!accepted) {
            clearIfPending(acknowledgement)
            return false
        }
        WjxFileLogger.i(
            TAG,
            "write queued $description type=${if (writeWithResponse) "DEFAULT" else "NO_RESPONSE"} " +
                "bytes=${packet.toHex()}",
        )

        val succeeded = if (acknowledgement == null) {
            true
        } else {
            val status = withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { acknowledgement.await() }
            clearIfPending(acknowledgement)
            status == BluetoothGatt.GATT_SUCCESS
        }
        if (!succeeded) return false
        delay(IpixelFaProtocol.WRITE_GAP_MS)
        return true
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun close() {
        val old = bluetoothGatt
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        bluetoothGatt = null
        writeCharacteristic = null
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
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
                listener.onConnected(gatt)
                if (!gatt.discoverServices()) {
                    listener.onFailure("标准 FA 服务读取启动失败", reconnect = true)
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                listener.onDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrent(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onFailure("标准 FA 服务读取失败", reconnect = true)
                return
            }
            val service = gatt.getService(IpixelFaProtocol.serviceUuid)
            val characteristicUuids = service?.characteristics
                .orEmpty()
                .mapTo(linkedSetOf()) { it.uuid }
            if (!IpixelFaProtocol.acceptsGattProfile(service?.uuid, characteristicUuids)) {
                listener.onFailure("该设备不是标准 FA 协议设备", reconnect = false)
                return
            }
            val write = service?.getCharacteristic(IpixelFaProtocol.writeUuid)
            if (write == null) {
                listener.onFailure("该设备不是标准 FA 协议设备", reconnect = false)
                return
            }
            val supportsWrite = write.properties and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (!supportsWrite) {
                listener.onFailure("标准 FA 写入通道不可用", reconnect = false)
                return
            }
            writeCharacteristic = write
            listener.onReady(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (!isCurrent(gatt) || characteristic.uuid != IpixelFaProtocol.writeUuid) return
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
        private const val WRITE_ACK_TIMEOUT_MS = 1_500L
    }
}
