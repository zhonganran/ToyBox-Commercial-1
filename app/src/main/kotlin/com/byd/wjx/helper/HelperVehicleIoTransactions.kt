package com.byd.wjx.helper

import android.os.Parcel

internal fun HelperDaemonRuntime.handleVehicleIoTransaction(
    code: Int,
    data: Parcel,
    reply: Parcel?,
): Boolean = when (code) {
    HelperBinderProtocol.TX_PING -> runCatching {
        reply?.writeInt(0)
        reply?.writeInt(HelperBinderProtocol.CURRENT_VERSION)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }

    HelperBinderProtocol.TX_READ -> runCatching {
        val tx = data.readInt()
        val dev = data.readInt()
        val fid = data.readInt()
        val result = autoserviceTransact(
            autoservice,
            autoInterface,
            tx,
            dev,
            fid,
            0,
            writeValue = false,
        )
        reply?.writeInt(result.first)
        reply?.writeInt(result.second)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }

    HelperBinderProtocol.TX_READ_BATCH -> runCatching {
        val count = data.readInt()
        require(count in 0..HelperBinderProtocol.MAX_BATCH_READS) {
            "invalid batch size=$count"
        }
        reply?.writeInt(0)
        reply?.writeInt(count)
        withAutoserviceTransactionLock {
            repeat(count) {
                val tx = data.readInt()
                val dev = data.readInt()
                val fid = data.readInt()
                val result = autoserviceTransact(
                    autoservice,
                    autoInterface,
                    tx,
                    dev,
                    fid,
                    0,
                    writeValue = false,
                )
                reply?.writeInt(result.first)
                reply?.writeInt(result.second)
            }
        }
        true
    }.getOrElse {
        helperLog("VehicleBatch", "read failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }

    HelperBinderProtocol.TX_WRITE -> runCatching {
        val dev = data.readInt()
        val fid = data.readInt()
        val value = data.readInt()
        val result = autoserviceTransact(
            autoservice,
            autoInterface,
            6,
            dev,
            fid,
            value,
            writeValue = true,
        )
        reply?.writeInt(result.first)
        reply?.writeInt(result.second)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }

    else -> error("vehicle I/O transaction routed incorrectly: $code")
}
