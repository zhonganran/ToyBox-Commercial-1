package com.byd.wjx

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

@SuppressLint("InlinedApi", "BatteryLife")
internal fun openAppSpecialPermissionSettings(
    context: Context,
    permission: FirstLaunchSpecialPermission,
    onFailure: (Throwable) -> Unit = {},
): Boolean {
    val intent = when (permission) {
        FirstLaunchSpecialPermission.ALL_FILES_ACCESS -> Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        FirstLaunchSpecialPermission.LOCATION_SERVICES ->
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        FirstLaunchSpecialPermission.BLUETOOTH ->
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        FirstLaunchSpecialPermission.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        FirstLaunchSpecialPermission.NOTIFICATION_LISTENER ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        FirstLaunchSpecialPermission.ACCESSIBILITY ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        FirstLaunchSpecialPermission.USAGE_STATS ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        FirstLaunchSpecialPermission.BATTERY_OPTIMIZATION -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
    }
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse { error ->
        onFailure(error)
        if (permission != FirstLaunchSpecialPermission.ALL_FILES_ACCESS) {
            false
        } else {
            runCatching {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            }.onFailure(onFailure).getOrDefault(false)
        }
    }
}

internal fun openAppPermissionSettings(
    context: Context,
    onFailure: (Throwable) -> Unit = {},
): Boolean {
    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    if (context !is Activity) appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(appDetails)
        true
    }.getOrElse { error ->
        onFailure(error)
        runCatching {
            val fallback = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            true
        }.onFailure(onFailure).getOrDefault(false)
    }
}
