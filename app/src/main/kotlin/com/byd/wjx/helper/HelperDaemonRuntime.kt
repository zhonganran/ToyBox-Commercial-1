package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder

internal class HelperDaemonRuntime(
    val autoservice: IBinder,
    val autoInterface: String,
    val systemContext: Context?,
    val installIdentity: String,
    val virtualDisplays: HelperVirtualDisplayRegistry = HelperVirtualDisplayRegistry(),
    val lockSoundRuntime: WjxLockSoundRuntime =
        WjxLockSoundRuntime(autoservice, autoInterface, systemContext),
) {
    fun start() {
        lockSoundRuntime.start()
    }
}
