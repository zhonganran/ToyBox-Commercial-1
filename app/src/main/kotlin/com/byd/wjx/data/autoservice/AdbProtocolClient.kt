package com.byd.wjx.data.autoservice

import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import java.security.KeyPair

/**
 * Minimal authenticated ADB client for the DiLink loopback daemon.
 *
 * This class owns only the handshake and serialized shell-stream state machine. Socket lifecycle,
 * packet encoding, and RSA serialization live in dedicated protocol components.
 */
internal class AdbProtocolClient(
    private val keyPair: KeyPair,
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
) : AdbProtocol {
    private val transport = AdbSocketTransport(host, port)
    private var localStreamId = 1

    @Synchronized
    override fun connect(): Boolean {
        if (transport.isConnected()) {
            Log.d(TAG, "Already connected, skipping reconnect")
            return true
        }
        disconnectInternal()
        return try {
            transport.open()
            transport.write(
                AdbWire.A_CNXN,
                AdbWire.A_VERSION_AUTH,
                AdbWire.MAX_PAYLOAD,
                "host::\u0000".toByteArray(Charsets.UTF_8),
            )
            val first = transport.read()
            when {
                first.command == AdbWire.A_CNXN -> {
                    logConnected("no auth required")
                    true
                }
                first.command == AdbWire.A_AUTH -> {
                    if (first.arg0 == AdbWire.AUTH_TOKEN) {
                        authenticate(first.payload)
                    } else {
                        failHandshake("Unexpected AUTH arg0=${first.arg0}")
                    }
                }
                else -> failHandshake(
                    "Unexpected handshake response cmd=0x${first.command.toString(16)}",
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Connect failed: ${error.message}")
            WjxFileLogger.w(TAG, "Connect failed host=$host:$port: ${error.message}")
            disconnectInternal()
            false
        }
    }

    @Synchronized
    override fun exec(cmd: String): String? {
        if (!transport.isConnected()) return null
        return try {
            val localId = localStreamId++
            transport.write(
                AdbWire.A_OPEN,
                localId,
                0,
                "shell:$cmd\u0000".toByteArray(Charsets.UTF_8),
            )
            val remoteId = awaitRemoteStream(localId, cmd) ?: return null
            readShellOutput(localId, remoteId)
        } catch (error: Exception) {
            Log.w(TAG, "exec failed: ${error.message}")
            WjxFileLogger.w(TAG, "exec failed: ${error.message} cmd=${cmd.take(80)}")
            disconnectInternal()
            null
        }
    }

    @Synchronized
    override fun isConnected(): Boolean = transport.isConnected()

    @Synchronized
    override fun disconnect() = disconnectInternal()

    private fun authenticate(token: ByteArray): Boolean {
        val signature = AdbAuth.signToken(token, keyPair)
        Log.d(TAG, "Auth step 1: sending signature (${signature.size}B)")
        transport.write(AdbWire.A_AUTH, AdbWire.AUTH_SIGNATURE, 0, signature)

        var response = transport.read()
        if (response.command == AdbWire.A_CNXN) {
            logConnected("signature auth")
            return true
        }
        if (response.command == AdbWire.A_AUTH && response.arg0 == AdbWire.AUTH_TOKEN) {
            val publicKey = AdbAuth.serializePublicKey(keyPair)
            Log.d(TAG, "Sending RSA public key for device approval (${publicKey.size}B)")
            transport.write(AdbWire.A_AUTH, AdbWire.AUTH_RSAPUBLICKEY, 0, publicKey)
            response = transport.withUserPromptTimeout(transport::read)
            if (response.command == AdbWire.A_CNXN) {
                logConnected("public key auth")
                return true
            }
        }
        return failHandshake("Auth failed lastResponse=0x${response.command.toString(16)}")
    }

    private fun awaitRemoteStream(localId: Int, cmd: String): Int? {
        repeat(MAX_OPEN_PACKETS) {
            val packet = transport.read()
            if (packet.command == AdbWire.A_OKAY && packet.arg1 == localId) return packet.arg0
            handleStalePacket(packet, localId)
        }
        Log.e(TAG, "shell: never got OKAY for localId=$localId")
        WjxFileLogger.e(TAG, "shell never got OKAY localId=$localId cmd=${cmd.take(80)}")
        disconnectInternal()
        return null
    }

    private fun readShellOutput(localId: Int, remoteId: Int): String {
        val output = StringBuilder()
        repeat(MAX_SHELL_PACKETS) {
            val packet = transport.read()
            when {
                packet.command == AdbWire.A_WRTE && packet.arg0 == remoteId && packet.arg1 == localId -> {
                    output.append(String(packet.payload, Charsets.UTF_8))
                    transport.write(AdbWire.A_OKAY, localId, remoteId, AdbWire.EMPTY)
                }
                packet.command == AdbWire.A_CLSE && packet.arg0 == remoteId -> {
                    transport.write(AdbWire.A_CLSE, localId, remoteId, AdbWire.EMPTY)
                    return output.toString().trim()
                }
                else -> handleStalePacket(packet, localId)
            }
        }
        return output.toString().trim()
    }

    private fun handleStalePacket(packet: AdbWire.Packet, currentLocalId: Int) {
        when (packet.command) {
            AdbWire.A_CLSE -> transport.write(AdbWire.A_CLSE, packet.arg1, packet.arg0, AdbWire.EMPTY)
            AdbWire.A_WRTE -> transport.write(AdbWire.A_OKAY, packet.arg1, packet.arg0, AdbWire.EMPTY)
            else -> Log.d(
                TAG,
                "shell: skipping stale 0x${packet.command.toString(16)} " +
                    "(arg0=${packet.arg0} arg1=${packet.arg1}, current=$currentLocalId)",
            )
        }
    }

    private fun logConnected(mode: String) {
        Log.i(TAG, "Connected via $mode")
        WjxFileLogger.i(TAG, "Connected via $mode host=$host:$port")
    }

    private fun failHandshake(message: String): Boolean {
        Log.e(TAG, message)
        WjxFileLogger.e(TAG, message)
        disconnectInternal()
        return false
    }

    private fun disconnectInternal() {
        transport.close()
        localStreamId = 1
    }

    private companion object {
        const val TAG = "AdbProtocolClient"
        const val MAX_OPEN_PACKETS = 20
        const val MAX_SHELL_PACKETS = 500
    }
}
