package com.byd.wjx.led.ipixelfa

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import java.util.UUID

internal object IpixelFaScanPolicy {
    fun accepts(name: String, serviceUuids: Set<UUID>): Boolean {
        if (IpixelFaProtocol.serviceUuid in serviceUuids) return true
        if (serviceUuids.isNotEmpty()) return false
        return name.contains("IPIXEL", ignoreCase = true) ||
            name.contains("PIXEL", ignoreCase = true) ||
            name.contains("LED", ignoreCase = true)
    }

    fun displayName(name: String): String = name.ifBlank { "标准 FA 设备" }
}

internal class IpixelFaDeviceScanCallback(
    private val onDevice: (IpixelFaDevice) -> Unit,
    private val onFailure: (Int) -> Unit,
) : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val name = runCatching { result.device.name }.getOrNull()
            .orEmpty()
            .ifBlank { result.scanRecord?.deviceName.orEmpty() }
        val services = result.scanRecord?.serviceUuids.orEmpty().mapTo(linkedSetOf()) { it.uuid }
        if (!IpixelFaScanPolicy.accepts(name, services)) return
        onDevice(
            IpixelFaDevice(
                address = result.device.address,
                name = IpixelFaScanPolicy.displayName(name),
                rssi = result.rssi,
            ),
        )
    }

    override fun onScanFailed(errorCode: Int) = onFailure(errorCode)
}
