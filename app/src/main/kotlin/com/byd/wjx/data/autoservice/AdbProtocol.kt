package com.byd.wjx.data.autoservice

/** Narrow protocol seam used by the on-device ADB coordinator and its tests. */
internal interface AdbProtocol {
    fun connect(): Boolean
    fun exec(cmd: String): String?
    fun isConnected(): Boolean
    fun disconnect()
}
