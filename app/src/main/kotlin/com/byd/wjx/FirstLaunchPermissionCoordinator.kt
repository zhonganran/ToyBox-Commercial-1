package com.byd.wjx

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

internal const val FIRST_LAUNCH_PERMISSION_PREFS = "first_launch_permissions"

internal object AppRuntimePermissionPolicy {
    @Suppress("InlinedApi")
    fun initialPermissions(sdkInt: Int): List<String> = buildList {
        if (sdkInt <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (sdkInt >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun needsBackgroundLocation(sdkInt: Int, fineLocationGranted: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.Q && fineLocationGranted
}

internal enum class FirstLaunchSpecialPermission {
    ALL_FILES_ACCESS,
    LOCATION_SERVICES,
    BLUETOOTH,
    OVERLAY,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY,
    USAGE_STATS,
    BATTERY_OPTIMIZATION,
}

internal object AppSpecialPermissionPolicy {
    fun isApplicable(permission: FirstLaunchSpecialPermission, sdkInt: Int): Boolean = when (permission) {
        FirstLaunchSpecialPermission.ALL_FILES_ACCESS -> sdkInt >= Build.VERSION_CODES.R
        FirstLaunchSpecialPermission.USAGE_STATS -> sdkInt >= Build.VERSION_CODES.LOLLIPOP
        FirstLaunchSpecialPermission.BATTERY_OPTIMIZATION -> sdkInt >= Build.VERSION_CODES.M
        else -> true
    }

    fun isGranted(context: Context, permission: FirstLaunchSpecialPermission): Boolean = when (permission) {
        FirstLaunchSpecialPermission.ALL_FILES_ACCESS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        FirstLaunchSpecialPermission.LOCATION_SERVICES -> {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            manager == null || LocationManagerCompat.isLocationEnabled(manager)
        }
        FirstLaunchSpecialPermission.BLUETOOTH -> runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.isEnabled ?: true
        }.getOrDefault(true)
        FirstLaunchSpecialPermission.OVERLAY -> Settings.canDrawOverlays(context)
        FirstLaunchSpecialPermission.NOTIFICATION_LISTENER -> enabledComponents(
            context,
            ENABLED_NOTIFICATION_LISTENERS,
        ).any { it.packageName == context.packageName }
        FirstLaunchSpecialPermission.ACCESSIBILITY -> {
            val enabled = enabledComponents(
                context,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            REQUIRED_ACCESSIBILITY_SERVICES.all(enabled::contains)
        }
        FirstLaunchSpecialPermission.USAGE_STATS -> runCatching {
            val manager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return@runCatching false
            manager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        FirstLaunchSpecialPermission.BATTERY_OPTIMIZATION -> runCatching {
            val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return@runCatching false
            manager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    private fun enabledComponents(context: Context, setting: String): Set<ComponentName> =
        Settings.Secure.getString(context.contentResolver, setting)
            .orEmpty()
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .toSet()

    private val REQUIRED_ACCESSIBILITY_SERVICES = setOf(
        ComponentName("com.byd.wjx", "com.byd.wjx.cluster.SteeringWheelKeyService"),
        ComponentName("com.byd.wjx", "com.byd.wjx.service.KeepAliveAccessibilityService"),
    )
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
}

internal class FirstLaunchPermissionCoordinator(
    private val context: Context,
    private val launchRuntimePermissions: (Array<String>) -> Unit,
    private val launchBackgroundLocation: (String) -> Unit,
    private val launchSpecialPermissionSettings: (FirstLaunchSpecialPermission) -> Boolean,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val permissionGranted: (String) -> Boolean = { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
    private val specialPermissionGranted: (FirstLaunchSpecialPermission) -> Boolean = { permission ->
        AppSpecialPermissionPolicy.isGranted(context, permission)
    },
    private val log: (String) -> Unit = {},
    private val onCompleted: () -> Unit = {},
) {
    private val preferences = context.getSharedPreferences(
        FIRST_LAUNCH_PERMISSION_PREFS,
        Context.MODE_PRIVATE,
    )
    private var requestInFlight = false
    private var pendingSpecialPermission: FirstLaunchSpecialPermission? = null
    private val attemptedSpecialPermissions = mutableSetOf<FirstLaunchSpecialPermission>()

    fun start() {
        if (requestInFlight) return

        val missing = AppRuntimePermissionPolicy.initialPermissions(sdkInt)
            .filterNot(permissionGranted)
        if (missing.isNotEmpty()) {
            requestInFlight = true
            log("requesting initial runtime permissions count=${missing.size}")
            launchRuntimePermissions(missing.toTypedArray())
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }

    fun onRuntimePermissionsResult(result: Map<String, Boolean>) {
        requestInFlight = false
        val grantedCount = result.values.count { it }
        log("initial runtime permissions granted=$grantedCount/${result.size}")
        requestBackgroundLocationIfNeeded()
    }

    fun onBackgroundLocationResult(granted: Boolean) {
        requestInFlight = false
        log("background location permission granted=$granted")
        continueAfterRuntimePermissions()
    }

    fun onActivityResumed() {
        val returnedFrom = pendingSpecialPermission ?: return
        pendingSpecialPermission = null
        requestInFlight = false
        log("returned from ${returnedFrom.name.lowercase()} settings granted=${specialPermissionGranted(returnedFrom)}")
        requestNextSpecialPermission()
    }

    private fun requestBackgroundLocationIfNeeded() {
        val fineLocationGranted = permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val backgroundLocationGranted = permissionGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (AppRuntimePermissionPolicy.needsBackgroundLocation(sdkInt, fineLocationGranted) &&
            !backgroundLocationGranted
        ) {
            requestInFlight = true
            log("requesting background location permission")
            launchBackgroundLocation(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            continueAfterRuntimePermissions()
        }
    }

    private fun continueAfterRuntimePermissions() {
        if (preferences.getBoolean(KEY_COMPLETED, false)) {
            log("permission health check completed")
        } else {
            requestNextSpecialPermission()
        }
    }

    private fun requestNextSpecialPermission() {
        val next = FirstLaunchSpecialPermission.entries.firstOrNull { permission ->
            AppSpecialPermissionPolicy.isApplicable(permission, sdkInt) &&
                permission !in attemptedSpecialPermissions &&
                !specialPermissionGranted(permission)
        }
        if (next == null) {
            markCompleted()
            return
        }

        attemptedSpecialPermissions += next
        pendingSpecialPermission = next
        requestInFlight = true
        log("opening ${next.name.lowercase()} settings")
        if (!launchSpecialPermissionSettings(next)) {
            pendingSpecialPermission = null
            requestInFlight = false
            log("unable to open ${next.name.lowercase()} settings")
            requestNextSpecialPermission()
        }
    }

    private fun markCompleted() {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
        log("first-launch permission flow completed")
        onCompleted()
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
