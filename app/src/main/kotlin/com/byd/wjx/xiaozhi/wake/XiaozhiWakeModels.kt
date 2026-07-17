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
    val recognitionAvailable: Boolean = true,
    val engineLabel: String = "系统语音识别",
    val statusText: String = "正在准备语音唤醒",
    val lastRecognizedText: String = "",
    val lastWakeAtMs: Long? = null,
)

internal fun normalizeWakeText(value: String): String = value
    .lowercase()
    .filter { character -> character.isLetterOrDigit() }

internal fun validateWakePhrase(value: String): String? {
    val normalized = normalizeWakeText(value)
    return when {
        normalized.length < 2 -> "唤醒词至少需要 2 个字或字符"
        normalized.length > 16 -> "唤醒词不能超过 16 个字或字符"
        else -> null
    }
}

internal fun matchesWakePhrase(recognized: String, phrase: String): Boolean {
    val heard = normalizeWakeText(recognized)
    val target = normalizeWakeText(phrase)
    if (heard.isBlank() || target.isBlank()) return false
    if (target.length <= SHORT_WAKE_PHRASE_LENGTH) {
        return heard == target || heard == target + target
    }
    return heard == target || heard.contains(target)
}

internal fun selectWakeMatch(
    candidates: List<String>,
    confidenceScores: FloatArray?,
    phrase: String,
): String? = candidates.withIndex().firstOrNull { (index, candidate) ->
    val confidence = confidenceScores?.getOrNull(index) ?: UNKNOWN_CONFIDENCE
    matchesWakePhrase(candidate, phrase) &&
        (confidence == UNKNOWN_CONFIDENCE || confidence >= MIN_WAKE_CONFIDENCE)
}?.value

internal fun exponentialBackoffDelay(
    attempt: Int,
    baseMs: Long,
    maxMs: Long,
): Long {
    val shift = (attempt.coerceAtLeast(1) - 1).coerceAtMost(20)
    return (baseMs * (1L shl shift)).coerceAtMost(maxMs)
}

private const val SHORT_WAKE_PHRASE_LENGTH = 2
private const val UNKNOWN_CONFIDENCE = -1f
private const val MIN_WAKE_CONFIDENCE = 0.45f
