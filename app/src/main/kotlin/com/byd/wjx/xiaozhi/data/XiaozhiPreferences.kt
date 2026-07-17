package com.byd.wjx.xiaozhi.data

import android.content.Context
import com.byd.wjx.xiaozhi.domain.XiaozhiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class XiaozhiPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<XiaozhiConfig> = _config.asStateFlow()

    fun saveServer(websocketUrl: String, token: String, otaUrl: String = _config.value.otaUrl) {
        val updated = _config.value.copy(
            websocketUrl = websocketUrl.trim(),
            token = token.trim().removePrefix("Bearer ").trim(),
            otaUrl = otaUrl.trim().ifBlank { XiaozhiConfig.DEFAULT_OTA_URL },
        )
        prefs.edit()
            .putString(KEY_WEBSOCKET_URL, updated.websocketUrl)
            .putString(KEY_TOKEN, updated.token)
            .putString(KEY_OTA_URL, updated.otaUrl)
            .apply()
        _config.value = updated
    }

    fun saveOtaUrl(otaUrl: String) {
        saveServer(_config.value.websocketUrl, _config.value.token, otaUrl)
    }

    fun clearServer() {
        val updated = _config.value.copy(websocketUrl = "", token = "")
        prefs.edit()
            .remove(KEY_WEBSOCKET_URL)
            .remove(KEY_TOKEN)
            .apply()
        _config.value = updated
    }

    private fun loadConfig(): XiaozhiConfig {
        val clientId = prefs.getString(KEY_CLIENT_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_CLIENT_ID, it).apply()
            }
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: stableDeviceId(clientId).also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        return XiaozhiConfig(
            websocketUrl = prefs.getString(KEY_WEBSOCKET_URL, "").orEmpty(),
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
            deviceId = deviceId,
            clientId = clientId,
            otaUrl = prefs.getString(KEY_OTA_URL, XiaozhiConfig.DEFAULT_OTA_URL)
                .orEmpty()
                .ifBlank { XiaozhiConfig.DEFAULT_OTA_URL },
        )
    }

    private fun stableDeviceId(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val mac = digest.copyOfRange(0, 6)
        // Locally administered unicast address: stable on this installation without exposing a real MAC.
        mac[0] = (((mac[0].toInt() and 0xff) or 0x02) and 0xfe).toByte()
        return mac.joinToString(":") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    companion object {
        const val PREFS_NAME = "wjx_xiaozhi"
        private const val KEY_WEBSOCKET_URL = "websocket_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_OTA_URL = "ota_url"
    }
}
