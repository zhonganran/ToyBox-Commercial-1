package com.byd.wjx.xiaozhi.wake

data class XiaozhiWakeConfig(
    val enabled: Boolean = true,
    val phrase: String = DEFAULT_WAKE_PHRASE,
) {
    companion object {
        const val DEFAULT_WAKE_PHRASE = "小智"
    }
}

data class XiaozhiWakeRuntimeState(
    val config: XiaozhiWakeConfig = XiaozhiWakeConfig(),
    val serviceRunning: Boolean = false,
    val listening: Boolean = false,
    val engineAvailable: Boolean = true,
    val engineLabel: String = XiaozhiWakeWordService.ENGINE_LABEL,
    val statusText: String = "正在准备本地唤醒",
    val lastDetectedPhrase: String = "",
    val lastWakeAtMs: Long? = null,
)

internal fun normalizeWakeText(value: String): String = value
    .lowercase()
    .filter { character -> character.isLetterOrDigit() }

internal fun exponentialBackoffDelay(
    attempt: Int,
    baseMs: Long,
    maxMs: Long,
): Long {
    val shift = (attempt.coerceAtLeast(1) - 1).coerceAtMost(20)
    return (baseMs * (1L shl shift)).coerceAtMost(maxMs)
}
