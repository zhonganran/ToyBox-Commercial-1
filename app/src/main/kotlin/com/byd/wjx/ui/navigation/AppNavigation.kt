package com.byd.wjx.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.byd.wjx.R
import androidx.compose.ui.platform.LocalContext
import com.byd.wjx.data.legal.DisclaimerPreferences
import com.byd.wjx.data.system.AutostartGuidePreferences
import com.byd.wjx.data.system.AutostartSettingsLaunchResult
import com.byd.wjx.data.system.AutostartSettingsLauncher
import com.byd.wjx.service.UpdateChecker
import com.byd.wjx.ui.automation.AutomationScreen
import com.byd.wjx.ui.charges.ChargesScreen
import com.byd.wjx.ui.dashboard.DashboardScreen
import com.byd.wjx.ui.places.PlacesScreen
import com.byd.wjx.ui.lyrics.LyricDisplayScreen
import com.byd.wjx.ui.home.ProHomeScreen
import com.byd.wjx.ui.legal.DisclaimerScreen
import com.byd.wjx.ui.map.MapScreen
import com.byd.wjx.ui.pro.ProAppScaffold
import com.byd.wjx.ui.pro.ProNavItem
import com.byd.wjx.ui.settings.SettingsScreen
import com.byd.wjx.ui.settings.UpdateDialog
import com.byd.wjx.ui.settings.UpdateState
import com.byd.wjx.ui.sound.SoundScreen
import com.byd.wjx.ui.theme.*
import com.byd.wjx.xiaozhi.ui.XiaozhiScreen

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "首页", Icons.Outlined.Home),
    Sound("sound", "个性音效", Icons.AutoMirrored.Outlined.VolumeUp),
    Lyrics("lyrics", "歌词显示", Icons.Outlined.Subtitles),
    Automation("automation", "智能联动", Icons.Outlined.Bolt),
    Xiaozhi("xiaozhi", "小智", Icons.Outlined.RecordVoiceOver),
    Settings("settings", "设置", Icons.Outlined.Settings)
}

@Composable
fun AppNavigation(
    updateChecker: UpdateChecker
) {
    val appContext = LocalContext.current
    var disclaimerAccepted by remember {
        mutableStateOf(DisclaimerPreferences.isAccepted(appContext))
    }
    var showAutostartGuide by rememberSaveable {
        mutableStateOf(AutostartGuidePreferences.shouldShow(appContext))
    }
    var autostartLaunchResult by remember {
        mutableStateOf<AutostartSettingsLaunchResult?>(null)
    }

    var selectedRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    var showTripMap by rememberSaveable { mutableStateOf(false) }
    var showCharges by rememberSaveable { mutableStateOf(false) }
    var showDashboard by rememberSaveable { mutableStateOf(false) }
    var settingsInitialCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val screenStateHolder = rememberSaveableStateHolder()


    // Automatic update check at app startup.
    // UpdateChecker throttles real update-backend requests internally.
    val autoCheckContext = LocalContext.current
    val autoCheckScope = rememberCoroutineScope()
    var autoUpdateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var autoUpdateState by remember { mutableStateOf<UpdateState?>(null) }
    var autoUpdateDownloadJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(disclaimerAccepted) {
        if (!disclaimerAccepted) return@LaunchedEffect
        if (!UpdateChecker.isAutoCheckEnabled(autoCheckContext)) return@LaunchedEffect
        try {
            val info = updateChecker.checkForUpdate(autoCheckContext, forceCheck = false)
            if (info != null) {
                autoUpdateInfo = info
                autoUpdateState = UpdateState.Available(version = info.version, notes = info.releaseNotes)
            }
        } catch (_: Exception) {
            // Ignore silently: offline, rate-limited, etc.
        }
    }
    if (disclaimerAccepted && !showAutostartGuide) autoUpdateState?.let { dialogState ->
        val currentVersion = runCatching {
            autoCheckContext.packageManager.getPackageInfo(autoCheckContext.packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
        UpdateDialog(
            currentVersion = currentVersion,
            state = dialogState,
            onCheck = {
                val info = autoUpdateInfo ?: return@UpdateDialog
                autoUpdateState = UpdateState.Downloading(info.version, autoCheckContext.getString(R.string.update_downloading_start))
                autoUpdateDownloadJob = autoCheckScope.launch {
                    try {
                        updateChecker.downloadAndInstall(autoCheckContext, info) { progress ->
                            // Ignore late progress after dismissal so a closed dialog
                            // does not reappear in the Downloading state.
                            if (isActive) {
                                autoUpdateState = UpdateState.Downloading(info.version, progress)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e // Cooperative cancellation from onDismiss, not an error.
                    } catch (e: Exception) {
                        autoUpdateState = UpdateState.Error(e.message ?: "下载失败")
                    }
                }
            },
            onDismiss = {
                autoUpdateDownloadJob?.cancel()
                autoUpdateDownloadJob = null
                autoUpdateState = null
                autoUpdateInfo = null
            }
        )
    }

    if (disclaimerAccepted && showAutostartGuide) {
        AutostartGuideDialog(
            launchResult = autostartLaunchResult,
            onOpenSettings = {
                autostartLaunchResult = AutostartSettingsLauncher.open(appContext)
            },
            onConfirmed = {
                AutostartGuidePreferences.markConfirmed(appContext)
                showAutostartGuide = false
            },
            onLater = {
                showAutostartGuide = false
            },
        )
    }

    val selectedScreen = Screen.entries.firstOrNull { it.route == selectedRoute } ?: Screen.Home
    val navItems = remember {
        Screen.entries.map { ProNavItem(route = it.route, label = it.label, icon = it.icon) }
    }
    val scaffoldRoute = when {
        showPlaces -> Screen.Settings.route
        showTripMap || showCharges || showDashboard -> Screen.Home.route
        else -> selectedRoute
    }

    BackHandler(enabled = !disclaimerAccepted) {
        appContext.findActivity()?.finish()
    }
    BackHandler(enabled = disclaimerAccepted && showPlaces) {
        showPlaces = false
    }
    BackHandler(enabled = disclaimerAccepted && showTripMap) {
        showTripMap = false
    }
    BackHandler(enabled = disclaimerAccepted && showCharges) {
        showCharges = false
    }
    BackHandler(enabled = disclaimerAccepted && showDashboard) {
        showDashboard = false
    }
    ProAppScaffold(
            navItems = navItems,
            selectedRoute = scaffoldRoute,
            animateBackground = selectedRoute == Screen.Home.route &&
                !showPlaces && !showTripMap && !showCharges && !showDashboard,
            onNavigate = { route ->
                if (selectedRoute != route || showPlaces || showTripMap || showCharges || showDashboard) {
                    selectedRoute = route
                    if (route == Screen.Settings.route) settingsInitialCategory = null
                    showPlaces = false
                    showTripMap = false
                    showCharges = false
                    showDashboard = false
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (showPlaces) {
                    screenStateHolder.SaveableStateProvider("places") {
                        PlacesScreen(onBack = { showPlaces = false })
                    }
                } else if (showTripMap) {
                    screenStateHolder.SaveableStateProvider("trip_map") {
                        MapScreen(onBack = { showTripMap = false })
                    }
                } else if (showCharges) {
                    screenStateHolder.SaveableStateProvider("charges") {
                        ChargesScreen(
                            onNavigateSettings = {
                                selectedRoute = Screen.Settings.route
                                showPlaces = false
                                showTripMap = false
                                showCharges = false
                                showDashboard = false
                            },
                        )
                    }
                } else if (showDashboard) {
                    screenStateHolder.SaveableStateProvider("dashboard") {
                        DashboardScreen(
                            onOpenTripMap = {
                                selectedRoute = Screen.Home.route
                                showPlaces = false
                                showTripMap = true
                                showCharges = false
                                showDashboard = false
                            },
                        )
                    }
                } else {
                    screenStateHolder.SaveableStateProvider(selectedScreen.route) {
                        when (selectedScreen) {
                            Screen.Home -> ProHomeScreen(
                                onOpenCharges = {
                                    selectedRoute = Screen.Home.route
                                    showPlaces = false
                                    showTripMap = false
                                    showCharges = true
                                    showDashboard = false
                                },
                                onOpenDashboard = {
                                    selectedRoute = Screen.Home.route
                                    showPlaces = false
                                    showTripMap = false
                                    showCharges = false
                                    showDashboard = true
                                },
                                onOpenTripMap = {
                                    selectedRoute = Screen.Home.route
                                    showPlaces = false
                                    showTripMap = true
                                    showCharges = false
                                    showDashboard = false
                                },
                            )
                            Screen.Sound -> SoundScreen()
                            Screen.Lyrics -> LyricDisplayScreen()
                            Screen.Automation -> AutomationScreen()
                            Screen.Xiaozhi -> XiaozhiScreen(
                                onOpenSettings = {
                                    settingsInitialCategory = "Xiaozhi"
                                    selectedRoute = Screen.Settings.route
                                },
                            )
                            Screen.Settings -> SettingsScreen(
                                initialCategory = settingsInitialCategory,
                                onNavigateToPlaces = {
                                    selectedRoute = Screen.Settings.route
                                    showPlaces = true
                                },
                            )
                        }
                    }
                }
            }
        }
    if (!disclaimerAccepted) {
        DisclaimerScreen(
            onAccept = {
                DisclaimerPreferences.accept(appContext)
                disclaimerAccepted = true
            },
            onDecline = {
                appContext.findActivity()?.finish()
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
