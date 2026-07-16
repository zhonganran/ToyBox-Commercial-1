package com.byd.wjx.data.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.byd.wjx.BuildConfig
import com.byd.wjx.log.WjxFileLogger

enum class AutostartSettingsLaunchResult {
    DILINK_SETTINGS,
    APP_DETAILS_FALLBACK,
    FAILED,
}

/** DiLink's app-start manager is a separate system package, not a standard Android setting. */
object AutostartSettingsLauncher {
    private const val TAG = "AutostartSettings"
    private const val DILINK_PACKAGE = "com.byd.appstartmanagement"
    private const val DILINK_ACTIVITY = "com.byd.appstartmanagement.frame.AppStartManagement"

    fun open(context: Context): AutostartSettingsLaunchResult {
        val appContext = context.applicationContext
        val diLinkIntent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(DILINK_PACKAGE, DILINK_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(diLinkIntent)
            WjxFileLogger.i(TAG, "opened DiLink autostart settings")
            return AutostartSettingsLaunchResult.DILINK_SETTINGS
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "DiLink autostart settings unavailable: ${error.message}")
        }

        val detailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(detailsIntent)
            AutostartSettingsLaunchResult.APP_DETAILS_FALLBACK
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "application details settings unavailable: ${error.message}")
            AutostartSettingsLaunchResult.FAILED
        }
    }
}

/** Shows the guide once per installed app version; "later" never permanently suppresses it. */
object AutostartGuidePreferences {
    private const val FILE = "autostart_guide"
    private const val KEY_CONFIRMED_VERSION = "confirmed_version_code"

    fun shouldShow(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_CONFIRMED_VERSION, -1) != BuildConfig.VERSION_CODE

    fun markConfirmed(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CONFIRMED_VERSION, BuildConfig.VERSION_CODE)
            .apply()
    }
}
