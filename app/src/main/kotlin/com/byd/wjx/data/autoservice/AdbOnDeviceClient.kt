package com.byd.wjx.data.autoservice

/**
 * Narrow on-device ADB capability used only for helper bootstrap and approved diagnostics.
 * Production vehicle reads and writes continue through the versioned helper Binder.
 */
interface AdbOnDeviceClient {
    suspend fun connect(): Result<Unit>
    suspend fun isConnected(): Boolean
    suspend fun getprop(name: String): String? = null
    suspend fun getSecureSetting(name: String): String? = null
    suspend fun readMediaSessionDump(): String? = null
    suspend fun grantMusicNotificationListenerAccess(): Boolean = false
    suspend fun grantUsageStatsAppop(packageName: String): Boolean
    suspend fun spawnHelper(): Boolean
    suspend fun readHelperLog(): String?
    suspend fun helperHeartbeat(): Boolean
    suspend fun shutdown()
}
