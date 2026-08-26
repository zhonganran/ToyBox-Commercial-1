package com.byd.wjx.helper

import android.os.Binder
import android.os.Parcel

internal fun isHelperCallerAllowed(callingUid: Int, expectedUid: Int): Boolean =
    callingUid == expectedUid

internal class HelperBinderService(
    private val expectedUid: Int,
    private val dispatcher: HelperTransactionDispatcher,
) : Binder() {
    init {
        attachInterface(null, HelperBinderProtocol.DESCRIPTOR)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (!isHelperCallerAllowed(Binder.getCallingUid(), expectedUid)) return false
        data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
        return dispatcher.dispatch(code, data, reply) ?: super.onTransact(code, data, reply, flags)
    }
}
