package com.byd.wjx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BydPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf(POWER_EVENTS::contains) ?: return
        BootStartupCoordinator.handle(context, action, "byd_power")
    }

    private companion object {
        val POWER_EVENTS = setOf(
            "com.byd.action.ACC_ON",
            "com.byd.action.ACC_OFF",
            "com.byd.action.IGN_ON",
            "com.byd.accmode.ACC_MODE_CHANGED",
        )
    }
}
