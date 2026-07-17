package com.byd.wjx.xiaozhi.protocol

import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.xiaozhi.domain.XiaozhiConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

interface XiaozhiWebSocketListener {
    fun onReady(hello: XiaozhiServerEvent.Hello)
    fun onEvent(event: XiaozhiServerEvent)
    fun onAudio(packet: ByteArray)
    fun onClosed(reason: String)
    fun onFailure(message: String)
}

@Singleton
class XiaozhiWebSocketClient @Inject constructor(
    okHttpClient: OkHttpClient,
) {
    private val webSocketClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var sessionId = ""
    @Volatile private var listener: XiaozhiWebSocketListener? = null

    fun connect(config: XiaozhiConfig, listener: XiaozhiWebSocketListener) {
        disconnect("重新连接")
        this.listener = listener
        ready = false
        sessionId = ""
        val requestBuilder = Request.Builder()
            .url(config.websocketUrl)
            .header("Protocol-Version", XiaozhiMessageCodec.PROTOCOL_VERSION.toString())
            .header("Device-Id", config.deviceId)
            .header("Client-Id", config.clientId)
        if (config.token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.token}")
        }
        WjxFileLogger.i(TAG, "connect url=${config.websocketUrl} device=${config.deviceId} token=${config.token.isNotBlank()}")
        socket = webSocketClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (webSocket !== socket) return
                    if (!webSocket.send(XiaozhiMessageCodec.hello())) {
                        listener.onFailure("小智握手消息发送失败")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (webSocket !== socket) return
                    val event = runCatching { XiaozhiMessageCodec.parse(text) }
                        .getOrElse {
                            WjxFileLogger.w(TAG, "json parse failed: ${it.message}")
                            return
                        }
                    if (event is XiaozhiServerEvent.Hello) {
                        ready = true
                        sessionId = event.sessionId
                        listener.onReady(event)
                    } else {
                        listener.onEvent(event)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (webSocket === socket && ready) listener.onAudio(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    val replyCode = normalizeXiaozhiCloseCode(code)
                    WjxFileLogger.i(
                        TAG,
                        "remote closing code=$code replyCode=$replyCode reason=$reason",
                    )
                    webSocket.close(replyCode, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (webSocket !== socket) return
                    socket = null
                    ready = false
                    listener.onClosed(reason.ifBlank { "连接已关闭（$code）" })
                }

                override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                    if (webSocket !== socket) return
                    socket = null
                    ready = false
                    val suffix = response?.code?.let { "（HTTP $it）" }.orEmpty()
                    WjxFileLogger.w(TAG, "websocket failed code=${response?.code}: ${error.message}")
                    listener.onFailure("小智服务器连接失败$suffix：${error.message ?: "未知错误"}")
                }
            },
        )
    }

    fun startAutomaticListening(): Boolean =
        sendText(XiaozhiMessageCodec.startAutomaticListening(sessionId))

    fun abort(): Boolean = sendText(XiaozhiMessageCodec.abort(sessionId))

    fun sendAudio(packet: ByteArray): Boolean = ready && socket?.send(ByteString.of(*packet)) == true

    fun isReady(): Boolean = ready && socket != null

    fun disconnect(reason: String = "主动断开") {
        val oldSocket = socket
        socket = null
        ready = false
        sessionId = ""
        oldSocket?.close(1000, reason)
    }

    private fun sendText(text: String): Boolean = ready && socket?.send(text) == true

    companion object {
        private const val TAG = "XiaozhiWebSocket"
    }
}

/**
 * OkHttp reports a peer close frame without a status code as 1005. Codes 1004, 1005, 1006 and
 * 1015 are protocol-only sentinel values and must never be sent in a close frame. Reply with a
 * normal close code so a valid server shutdown reaches onClosed instead of onFailure.
 */
internal fun normalizeXiaozhiCloseCode(code: Int): Int = when {
    code !in 1000..4999 -> 1000
    code in 1004..1006 -> 1000
    code == 1015 -> 1000
    else -> code
}
