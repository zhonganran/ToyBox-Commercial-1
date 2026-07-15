package com.byd.wjx.service

import android.content.Context
import android.os.UserManager

object BootState {
    fun isUserUnlocked(context: Context): Boolean {
        val userManager = context.getSystemService(UserManager::class.java) ?: return true
        return userManager.isUserUnlocked
    }

    fun deviceProtectedContext(context: Context): Context =
        context.createDeviceProtectedStorageContext()
}
