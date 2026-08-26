package com.byd.wjx.helper

import android.os.Parcel

internal fun HelperDaemonRuntime.handleSystemTransaction(
    code: Int,
    data: Parcel,
    reply: Parcel?,
): Boolean = when (code) {
    HelperBinderProtocol.TX_READ_VEHICLE_VIN -> runCatching {
        val raw = readVehicleVinFromBodywork(systemContext)
        reply?.writeInt(if (rawHasVin(raw)) 0 else -1)
        reply?.writeString(raw)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeString("error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
        true
    }

    HelperBinderProtocol.TX_SENTRY_SHELL -> runCatching {
        val result = sentryShellRead(data.readString().orEmpty(), data.readString().orEmpty())
        reply?.writeInt(result.code)
        reply?.writeString(result.stdout)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeString("error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
        true
    }

    HelperBinderProtocol.TX_EXEC_SHELL -> runCatching {
        val result = execShellRead(data.readString().orEmpty())
        reply?.writeInt(result.status)
        reply?.writeInt(result.exitCode)
        reply?.writeString(result.output)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeInt(-1)
        reply?.writeString("error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
        true
    }

    else -> error("system transaction routed incorrectly: $code")
}
