package com.byd.wjx.xiaozhi.protocol

import org.json.JSONObject

sealed interface XiaozhiServerEvent {
    data class Hello(
        val sessionId: String,
        val sampleRate: Int,
        val channels: Int,
        val frameDurationMs: Int,
    ) : XiaozhiServerEvent

    data class Stt(val text: String) : XiaozhiServerEvent
    data class Tts(val state: String, val text: String?) : XiaozhiServerEvent
    data class Llm(val emotion: String?) : XiaozhiServerEvent
    data class Alert(val status: String?, val message: String?) : XiaozhiServerEvent
    data class Mcp(val payload: String) : XiaozhiServerEvent
    data class Unknown(val type: String, val raw: String) : XiaozhiServerEvent
}

object XiaozhiMessageCodec {
    const val PROTOCOL_VERSION = 1
    const val INPUT_SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val FRAME_DURATION_MS = 60

    fun hello(): String = JSONObject()
        .put("type", "hello")
        .put("version", PROTOCOL_VERSION)
        .put("features", JSONObject().put("mcp", false))
        .put("transport", "websocket")
        .put(
            "audio_params",
            JSONObject()
                .put("format", "opus")
                .put("sample_rate", INPUT_SAMPLE_RATE)
                .put("channels", CHANNELS)
                .put("frame_duration", FRAME_DURATION_MS),
        )
        .toString()

    fun startAutomaticListening(sessionId: String): String = sessionMessage(sessionId, "listen")
        .put("state", "start")
        .put("mode", "auto")
        .toString()

    fun abort(sessionId: String): String = sessionMessage(sessionId, "abort").toString()

    fun parse(text: String): XiaozhiServerEvent {
        val json = JSONObject(text)
        val type = json.optString("type")
        return when (type) {
            "hello" -> {
                val params = json.optJSONObject("audio_params")
                XiaozhiServerEvent.Hello(
                    sessionId = json.optString("session_id"),
                    sampleRate = params?.optInt("sample_rate", 24_000) ?: 24_000,
                    channels = params?.optInt("channels", 1) ?: 1,
                    frameDurationMs = params?.optInt("frame_duration", FRAME_DURATION_MS)
                        ?: FRAME_DURATION_MS,
                )
            }
            "stt" -> XiaozhiServerEvent.Stt(json.optString("text"))
            "tts" -> XiaozhiServerEvent.Tts(
                state = json.optString("state"),
                text = json.optString("text").takeIf { it.isNotBlank() },
            )
            "llm" -> XiaozhiServerEvent.Llm(json.optString("emotion").takeIf { it.isNotBlank() })
            "alert" -> XiaozhiServerEvent.Alert(
                status = json.optString("status").takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() },
            )
            "mcp" -> XiaozhiServerEvent.Mcp(json.opt("payload")?.toString().orEmpty())
            else -> XiaozhiServerEvent.Unknown(type = type, raw = text)
        }
    }

    private fun sessionMessage(sessionId: String, type: String): JSONObject = JSONObject()
        .apply {
            if (sessionId.isNotBlank()) put("session_id", sessionId)
            put("type", type)
        }
}
