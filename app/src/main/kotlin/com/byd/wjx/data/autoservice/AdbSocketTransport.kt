package com.byd.wjx.data.autoservice

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/** Owns the loopback ADB socket and raw packet I/O lifecycle. */
internal class AdbSocketTransport(
    private val host: String,
    private val port: Int,
) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    fun open() {
        close()
        val connected = Socket(host, port).apply {
            soTimeout = SOCKET_TIMEOUT_MS
            tcpNoDelay = true
        }
        socket = connected
        input = connected.getInputStream()
        output = connected.getOutputStream()
    }

    fun isConnected(): Boolean = socket?.let { it.isConnected && !it.isClosed } == true

    fun write(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        AdbWire.write(output ?: throw IOException("not connected"), command, arg0, arg1, payload)
    }

    fun read(): AdbWire.Packet = AdbWire.read(input ?: throw IOException("not connected"))

    fun <T> withUserPromptTimeout(block: () -> T): T {
        val activeSocket = socket
        return try {
            activeSocket?.soTimeout = USER_PROMPT_TIMEOUT_MS
            block()
        } finally {
            activeSocket?.soTimeout = SOCKET_TIMEOUT_MS
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 5_000
        const val USER_PROMPT_TIMEOUT_MS = 60_000
    }
}
