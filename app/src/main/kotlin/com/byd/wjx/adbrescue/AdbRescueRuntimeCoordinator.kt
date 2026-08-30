package com.byd.wjx.adbrescue

import android.content.Context
import android.os.Process
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AdbRescueRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: AdbRescueManager,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    private val operationMutex = Mutex()

    suspend fun ensureEnabled(trigger: String): AdbRescueRepairOutcome = operationMutex.withLock {
        ensureEnabledLocked(trigger)
    }

    suspend fun disableBoth(trigger: String): AdbRescueReport = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val helperAvailable = try {
                ensureHelper()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                WjxFileLogger.w(TAG, "disable helper preparation failed: ${error.message}", error)
                false
            }
            var report = manager.disableBoth("$trigger:direct")
            var helperAttempted = false
            var helperSucceeded = false
            if (!report.allModesDisabled && helperAvailable) {
                helperAttempted = true
                helperSucceeded =
                    helperClient.execShellResult(AdbRescueShellCommands.DISABLE_BOTH_MODES)?.ok == true
                report = manager.inspect("$trigger:helper_readback", runWriteTest = false)
            }
            WjxFileLogger.i(
                TAG,
                "disable trigger=$trigger allDisabled=${report.allModesDisabled} helper=$helperAvailable " +
                    "fallback=$helperAttempted/$helperSucceeded",
            )
            report
        }
    }

    private suspend fun ensureEnabledLocked(trigger: String): AdbRescueRepairOutcome = withContext(Dispatchers.IO) {
        val initial = manager.inspect("$trigger:inspect", runWriteTest = false)
        if (initial.allModesEnabled && initial.writeSecureSettingsGranted) {
            return@withContext AdbRescueRepairOutcome(
                report = initial,
                helperAvailable = false,
                permissionGrantAttempted = false,
                permissionGrantSucceeded = true,
                helperModeRepairAttempted = false,
                helperModeRepairSucceeded = false,
            )
        }
        var helperAvailable = false
        var permissionGrantAttempted = false
        var permissionGrantSucceeded = initial.writeSecureSettingsGranted

        if (!initial.writeSecureSettingsGranted) {
            helperAvailable = ensureHelper()
            if (helperAvailable) {
                permissionGrantAttempted = true
                val command = AdbRescueShellCommands.grantWriteSecureSettings(
                    context.packageName,
                    Process.myUid() / PER_USER_RANGE,
                )
                permissionGrantSucceeded = helperClient.execShellResult(command)?.ok == true &&
                    manager.hasWriteSecureSettingsPermission()
            }
        }

        var report = manager.enableBoth("$trigger:direct", runWriteTest = false)
        var helperModeRepairAttempted = false
        var helperModeRepairSucceeded = false
        if (!report.allModesEnabled) {
            if (!helperAvailable) helperAvailable = ensureHelper()
            if (helperAvailable) {
                helperModeRepairAttempted = true
                helperModeRepairSucceeded =
                    helperClient.execShellResult(AdbRescueShellCommands.ENABLE_BOTH_MODES)?.ok == true
                report = manager.inspect("$trigger:helper_readback", runWriteTest = false)
            }
        }
        WjxFileLogger.i(
            TAG,
            "ensure trigger=$trigger allEnabled=${report.allModesEnabled} permission=" +
                "${report.writeSecureSettingsGranted} helper=$helperAvailable grant=" +
                "$permissionGrantAttempted/$permissionGrantSucceeded fallback=" +
                "$helperModeRepairAttempted/$helperModeRepairSucceeded",
        )
        AdbRescueRepairOutcome(
            report = report,
            helperAvailable = helperAvailable,
            permissionGrantAttempted = permissionGrantAttempted,
            permissionGrantSucceeded = permissionGrantSucceeded,
            helperModeRepairAttempted = helperModeRepairAttempted,
            helperModeRepairSucceeded = helperModeRepairSucceeded,
        )
    }

    private suspend fun ensureHelper(): Boolean =
        helperClient.isAlive() || helperBootstrap.ensureCurrent()

    private companion object {
        const val TAG = "AdbRescueRuntime"
        const val PER_USER_RANGE = 100_000
    }
}
