package com.byd.wjx

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
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
    LOCATION_SERVICES,
    BLUETOOTH,
    OVERLAY,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY,
}

internal object AppSpecialPermissionPolicy {
    fun isGranted(context: Context, permission: FirstLaunchSpecialPermission): Boolean = when (permission) {
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
            permission !in attemptedSpecialPermissions && !specialPermissionGranted(permission)
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
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
