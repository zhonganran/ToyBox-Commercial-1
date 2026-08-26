package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder

internal class HelperDaemonRuntime(
    val autoservice: IBinder,
    val autoInterface: String,
    val systemContext: Context?,
    val virtualDisplays: HelperVirtualDisplayRegistry = HelperVirtualDisplayRegistry(),
    val lockSoundRuntime: WjxLockSoundRuntime =
        WjxLockSoundRuntime(autoservice, autoInterface, systemContext),
    val headlightClient: BydHeadlightBinderClient = BydHeadlightBinderClient(systemContext),
) {
    fun start() {
        lockSoundRuntime.start()
    }
}
