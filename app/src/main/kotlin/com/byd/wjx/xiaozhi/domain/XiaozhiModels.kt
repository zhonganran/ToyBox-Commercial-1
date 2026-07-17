package com.byd.wjx.xiaozhi.domain

data class XiaozhiConfig(
    val websocketUrl: String = "",
    val token: String = "",
    val deviceId: String = "",
    val clientId: String = "",
    val otaUrl: String = DEFAULT_OTA_URL,
) {
    val isConfigured: Boolean
        get() = websocketUrl.startsWith("ws://") || websocketUrl.startsWith("wss://")

    companion object {
        const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
    }
}

enum class XiaozhiPhase {
    NotConfigured,
    Idle,
    Connecting,
    Listening,
    Thinking,
    Speaking,
    Error,
}

enum class XiaozhiMessageRole {
    User,
    Assistant,
    System,
}

data class XiaozhiChatMessage(
    val id: Long,
    val role: XiaozhiMessageRole,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

sealed interface XiaozhiProvisioningState {
    data object Idle : XiaozhiProvisioningState
    data object Checking : XiaozhiProvisioningState
    data class ActivationRequired(
        val code: String,
        val message: String,
    ) : XiaozhiProvisioningState
    data class Ready(val message: String = "官方服务配置已就绪") : XiaozhiProvisioningState
    data class Failed(val message: String) : XiaozhiProvisioningState
}

data class XiaozhiUiState(
    val config: XiaozhiConfig = XiaozhiConfig(),
    val phase: XiaozhiPhase = XiaozhiPhase.NotConfigured,
    val connected: Boolean = false,
    val statusText: String = "尚未配置小智服务",
    val messages: List<XiaozhiChatMessage> = emptyList(),
    val provisioning: XiaozhiProvisioningState = XiaozhiProvisioningState.Idle,
    val serverSampleRate: Int = 24_000,
    val autoListening: Boolean = false,
    val error: String? = null,
)
