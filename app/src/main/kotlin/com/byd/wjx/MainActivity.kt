package com.byd.wjx

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.system.SettingsPermissionHealthCoordinator
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.security.EndpointPrivacy
import com.byd.wjx.service.KeepAliveService
import com.byd.wjx.service.UpdateChecker
import com.byd.wjx.storage.WjxDirectories
import com.byd.wjx.ui.components.ConsumptionThresholds
import com.byd.wjx.ui.components.LocalConsumptionThresholds
import com.byd.wjx.ui.navigation.AppNavigation
import com.byd.wjx.ui.theme.SuperToyBoxTheme
import com.byd.wjx.ui.theme.AppWallpaper
import com.byd.wjx.ui.theme.LocalAppWallpaper
import com.byd.wjx.ui.theme.LocalSuperToyBoxDarkTheme
import com.byd.wjx.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var updateChecker: UpdateChecker
    @Inject lateinit var historyImporter: HistoryImporter
    @Inject lateinit var permissionHealthCoordinator: SettingsPermissionHealthCoordinator
    private var historySyncJob: Job? = null
    private var pendingInstallJob: Job? = null
    private lateinit var permissionCoordinator: FirstLaunchPermissionCoordinator
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionCoordinator.onRuntimePermissionsResult(result)
    }
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionCoordinator.onBackgroundLocationResult(granted)
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WjxFileLogger.i(TAG, "onCreate")
        permissionCoordinator = FirstLaunchPermissionCoordinator(
            context = this,
            launchRuntimePermissions = runtimePermissionLauncher::launch,
            launchBackgroundLocation = backgroundLocationLauncher::launch,
            launchSpecialPermissionSettings = ::openSpecialPermissionSettings,
            log = { message -> WjxFileLogger.i(TAG, message) },
            onCompleted = ::runInitialPermissionRepair,
        )
        configureSystemBars()
        ensureWjxDirectory()
        startBackgroundStack()

        setContent {
            val storedThemeMode by produceState(initialValue = SettingsRepository.DEFAULT_THEME_MODE) {
                settingsRepository.observeThemeMode().collect { value = it }
            }
            val storedAppWallpaper by produceState(initialValue = SettingsRepository.DEFAULT_APP_WALLPAPER) {
                settingsRepository.observeAppWallpaper().collect { value = it }
            }
            val thresholds by produceState(initialValue = ConsumptionThresholds.Default) {
                settingsRepository.observeConsumptionThresholds().collect { (good, bad) ->
                    value = ConsumptionThresholds(good = good, bad = bad)
                }
            }

            SuperToyBoxTheme(themeMode = ThemeMode.fromStored(storedThemeMode)) {
                val darkTheme = LocalSuperToyBoxDarkTheme.current
                SideEffect {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                CompositionLocalProvider(
                    LocalConsumptionThresholds provides thresholds,
                    LocalAppWallpaper provides AppWallpaper.fromStored(storedAppWallpaper),
                ) {
                    AppNavigation(updateChecker = updateChecker)
                }
            }
        }
        window.decorView.post(permissionCoordinator::start)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showSystemBars()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionCoordinator.isInitialized) {
            permissionCoordinator.onActivityResumed()
        }
        resumePendingUpdateInstall()
        syncVehicleHistory("activity_resume")
    }

    private fun resumePendingUpdateInstall() {
        pendingInstallJob?.cancel()
        pendingInstallJob = lifecycleScope.launch {
            delay(300L)
            runCatching { updateChecker.resumePendingInstall(this@MainActivity) }
                .onSuccess { resumed ->
                    if (resumed) WjxFileLogger.i(TAG, "pending update install resumed")
                }
                .onFailure { error ->
                    WjxFileLogger.e(TAG, "pending update install failed", error)
                    Toast.makeText(
                        this@MainActivity,
                        "更新安装失败：${EndpointPrivacy.userMessage(error, "安装包无效")}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private fun openSpecialPermissionSettings(permission: FirstLaunchSpecialPermission): Boolean =
        openAppSpecialPermissionSettings(this, permission) { error ->
            WjxFileLogger.w(
                TAG,
                "Unable to open ${permission.name} settings: ${error.message}",
                error,
            )
        }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        showSystemBars()
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun startBackgroundStack() {
        WjxFileLogger.i(TAG, "startBackgroundStack")
        KeepAliveService.start(this, "main_activity")
    }

    /** After the visible permission flow, let the already-authorized ADB/Helper repair system-only grants. */
    private fun runInitialPermissionRepair() {
        lifecycleScope.launch {
            delay(250L)
            val snapshot = runCatching { permissionHealthCoordinator.repairAndInspect() }
                .onFailure { WjxFileLogger.w(TAG, "initial permission repair failed: ${it.message}", it) }
                .getOrNull()
            WjxFileLogger.i(
                TAG,
                "initial permission repair completed unresolved=${snapshot?.unresolvedRequiredCount ?: -1}",
            )
        }
    }

    private fun syncVehicleHistory(reason: String) {
        historySyncJob?.cancel()
        historySyncJob = lifecycleScope.launch {
            delay(500L)
            val result = historyImporter.runSync()
            WjxFileLogger.i(
                TAG,
                "Vehicle history sync reason=$reason count=${result.count} " +
                    "detail=${result.details ?: result.error.orEmpty()}",
            )
        }
    }

    private fun ensureWjxDirectory() {
        val ready = WjxDirectories.ensureBaseDirs()
        if (ready) {
            WjxFileLogger.i(TAG, "WJX directory ready: ${WjxDirectories.baseDir.absolutePath} log=${WjxDirectories.appLog.absolutePath}")
        } else {
            WjxFileLogger.w(TAG, "WJX directory not created: ${WjxDirectories.baseDir.absolutePath}")
        }
    }
}
