package com.byd.wjx.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.byd.wjx.ui.desktopcomponents.DesktopComponentsScreen
import com.byd.wjx.ui.home.BatteryHealthScreen
import com.byd.wjx.ui.home.EnergyRecoveryScreen
import com.byd.wjx.ui.home.ProHomeScreen
import com.byd.wjx.ui.home.VehicleSystemScreen
import com.byd.wjx.ui.home.VehicleEnvironmentScreen
import com.byd.wjx.ui.legal.DisclaimerScreen
import com.byd.wjx.ui.map.MapScreen
import com.byd.wjx.ui.pro.ProAppScaffold
import com.byd.wjx.ui.pro.ProNavItem
import com.byd.wjx.ui.settings.SettingsScreen
import com.byd.wjx.ui.settings.UpdateDialog
import com.byd.wjx.ui.settings.UpdateState
import com.byd.wjx.ui.sound.SoundScreen
import com.byd.wjx.ui.theme.*

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

    var destinationRoute by rememberSaveable { mutableStateOf(AppDestination.HOME.route) }
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

    val destination = AppDestination.fromRoute(destinationRoute)
    val navItems = remember {
        Screen.entries.map { ProNavItem(route = it.route, label = it.label, icon = it.icon) }
    }

    BackHandler(enabled = !disclaimerAccepted) {
        appContext.findActivity()?.finish()
    }
    BackHandler(enabled = disclaimerAccepted && destination.isDetail) {
        destinationRoute = destination.section.route
    }
    ProAppScaffold(
            navItems = navItems,
            selectedRoute = destination.section.route,
            animateBackground = destination == AppDestination.HOME,
            onNavigate = { route ->
                val target = AppDestination.rootForRoute(route)
                if (target == AppDestination.SETTINGS) settingsInitialCategory = null
                destinationRoute = target.route
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                screenStateHolder.SaveableStateProvider(destination.route) {
                    when (destination) {
                        AppDestination.TRIP_MAP -> MapScreen(
                            onBack = { destinationRoute = AppDestination.HOME.route },
                        )
                        AppDestination.CHARGES -> ChargesScreen(
                            onNavigateSettings = {
                                settingsInitialCategory = null
                                destinationRoute = AppDestination.SETTINGS.route
                            },
                        )
                        AppDestination.ENERGY_RECOVERY -> EnergyRecoveryScreen(
                            onBack = { destinationRoute = AppDestination.HOME.route },
                        )
                        AppDestination.VEHICLE_SYSTEM -> VehicleSystemScreen(
                            onBack = { destinationRoute = AppDestination.HOME.route },
                        )
                        AppDestination.BATTERY_HEALTH -> BatteryHealthScreen(
                            onBack = { destinationRoute = AppDestination.HOME.route },
                        )
                        AppDestination.VEHICLE_ENVIRONMENT -> VehicleEnvironmentScreen(
                            onBack = { destinationRoute = AppDestination.HOME.route },
                        )
                        AppDestination.HOME -> ProHomeScreen(
                                onOpenCharges = {
                                    destinationRoute = AppDestination.CHARGES.route
                                },
                                onOpenEnergyRecovery = {
                                    destinationRoute = AppDestination.ENERGY_RECOVERY.route
                                },
                                onOpenVehicleSystem = {
                                    destinationRoute = AppDestination.VEHICLE_SYSTEM.route
                                },
                                onOpenBatteryHealth = {
                                    destinationRoute = AppDestination.BATTERY_HEALTH.route
                                },
                                onOpenVehicleEnvironment = {
                                    destinationRoute = AppDestination.VEHICLE_ENVIRONMENT.route
                                },
                                onOpenTripMap = {
                                    destinationRoute = AppDestination.TRIP_MAP.route
                                },
                            )
                        AppDestination.SOUND -> SoundScreen()
                        AppDestination.DESKTOP_COMPONENTS -> DesktopComponentsScreen()
                        AppDestination.AUTOMATION -> AutomationScreen()
                        AppDestination.SETTINGS -> SettingsScreen(
                            initialCategory = settingsInitialCategory,
                        )
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
