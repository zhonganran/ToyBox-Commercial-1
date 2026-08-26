package com.byd.wjx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.log.WjxFileLogger
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
    private var historySyncJob: Job? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WjxFileLogger.i(TAG, "onCreate")
        configureSystemBars()
        requestNotificationPermissionIfNeeded()
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showSystemBars()
    }

    override fun onResume() {
        super.onResume()
        syncVehicleHistory("activity_resume")
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        showSystemBars()
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            WjxFileLogger.i(TAG, "Notification permission granted=$granted")
        }
    }

    private fun startBackgroundStack() {
        WjxFileLogger.i(TAG, "startBackgroundStack")
        KeepAliveService.start(this, "main_activity")
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
