package com.byd.wjx.adbrescue

import android.content.Intent

internal object AdbRescueTriggerPolicy {
    private val immediateActions = setOf(
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_USER_UNLOCKED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        "com.byd.action.ACC_ON",
        "com.byd.action.IGN_ON",
    )

    fun shouldRun(action: String): Boolean = action in immediateActions

    fun shouldScheduleRetries(action: String): Boolean = action in immediateActions

    fun shouldRunWhileUserLocked(action: String): Boolean =
        action == Intent.ACTION_LOCKED_BOOT_COMPLETED
}
