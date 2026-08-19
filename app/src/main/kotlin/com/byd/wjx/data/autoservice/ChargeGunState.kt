package com.byd.wjx.data.autoservice

/** Canonical encoding for autoservice gun-connect fid 876609586. */
object ChargeGunState {
    const val UNKNOWN = 0
    const val NONE = 1
    const val AC = 2
    const val DC = 3
    const val AC_DC = 4
    const val VTOL = 5

    fun isConnected(value: Int?): Boolean = value in AC..VTOL

    fun isDisconnected(value: Int?): Boolean = value == UNKNOWN || value == NONE
}
