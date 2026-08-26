package com.byd.wjx.helper

import android.os.IBinder
import android.os.Parcel

private val autoserviceTransactionLock = Any()

internal fun <T> withAutoserviceTransactionLock(block: () -> T): T =
    synchronized(autoserviceTransactionLock, block)

/**
 * Performs one autoservice Binder transaction. Transaction codes and reply parsing intentionally
 * match the proven TCP-daemon protocol, and every call shares the process-wide serialization gate.
 */
internal fun autoserviceTransact(
    svc: IBinder,
    autoIface: String,
    tx: Int,
    dev: Int,
    fid: Int,
    value: Int,
    writeValue: Boolean,
): Pair<Int, Int> = withAutoserviceTransactionLock {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    try {
        data.writeInterfaceToken(autoIface)
        data.writeInt(dev)
        data.writeInt(fid)
        if (writeValue) data.writeInt(value)
        svc.transact(tx, data, reply, 0)
        val available = reply.dataAvail()
        val status = if (available >= 4) reply.readInt() else -999
        val returnValue = if (available >= 8) reply.readInt() else 0
        status to returnValue
    } finally {
        data.recycle()
        reply.recycle()
    }
}
