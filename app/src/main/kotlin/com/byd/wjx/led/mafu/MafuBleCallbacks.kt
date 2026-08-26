package com.byd.wjx.led.mafu

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult

internal class MafuDeviceScanCallback(
    private val onDevice: (MafuDevice) -> Unit,
    private val onFailure: (Int) -> Unit,
) : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val name = runCatching { result.device.name }.getOrNull()
            .orEmpty()
            .ifBlank { result.scanRecord?.deviceName.orEmpty() }
        if (!MafuProtocol.acceptsDeviceName(name)) return
        onDevice(
            MafuDevice(
                address = result.device.address,
                name = name,
                rssi = result.rssi,
            ),
        )
    }

    override fun onScanFailed(errorCode: Int) = onFailure(errorCode)
}
