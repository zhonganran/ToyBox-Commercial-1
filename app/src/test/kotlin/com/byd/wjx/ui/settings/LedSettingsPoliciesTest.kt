package com.byd.wjx.ui.settings

import android.Manifest
import com.byd.wjx.led.ipixelfa.IpixelFaConnectionPhase
import com.byd.wjx.led.mafu.MafuConnectionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class LedSettingsPoliciesTest {
    @Test
    fun `legacy Android scans use location permission`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            bluetoothScanPermissions(30),
        )
    }

    @Test
    fun `Android 12 scans use Bluetooth permissions`() {
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            bluetoothScanPermissions(31),
        )
    }

    @Test
    fun `FA connection message preserves active transport status`() {
        assertEquals(
            "正在重试",
            faConnectionMessage(
                phase = IpixelFaConnectionPhase.RETRYING,
                message = "正在重试",
                address = "AA:BB",
                enabled = true,
                deviceAddress = "AA:BB",
                connected = false,
            ),
        )
        assertEquals(
            "联动未开启",
            faConnectionMessage(
                phase = IpixelFaConnectionPhase.IDLE,
                message = "未连接",
                address = "",
                enabled = false,
                deviceAddress = "AA:BB",
                connected = false,
            ),
        )
    }

    @Test
    fun `MAFU connection message distinguishes unbound and waiting states`() {
        assertEquals(
            "请识别并绑定 YS、TL 设备",
            mafuConnectionMessage(
                phase = MafuConnectionPhase.IDLE,
                message = "未连接",
                enabled = true,
                deviceAddress = "",
                connected = false,
            ),
        )
        assertEquals(
            "等待连接马夫 LED 设备",
            mafuConnectionMessage(
                phase = MafuConnectionPhase.IDLE,
                message = "未连接",
                enabled = true,
                deviceAddress = "11:22",
                connected = false,
            ),
        )
    }
}
