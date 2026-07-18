package com.byd.wjx.desktopcard

import android.content.Context
import com.byd.wjx.data.foreground.ForegroundApp
import com.byd.wjx.data.foreground.ForegroundAppMonitor
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.theme.ThemeMode
import com.byd.wjx.util.HomePackageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class DesktopQuickCardRuntime @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: DesktopQuickCardRepository,
    private val foregroundMonitor: ForegroundAppMonitor,
    private val controller: DesktopQuickCardController,
    private val settingsRepository: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val homePackages = HomePackageResolver.resolve(appContext)
    private var renderJob: Job? = null

    fun start(scope: CoroutineScope, cameraActive: StateFlow<Boolean>) {
        if (renderJob?.isActive == true) return
        controller.start(scope)
        repository.refresh()
        foregroundMonitor.start(scope)
        renderJob = scope.launch {
            combine(
                repository.config,
                foregroundMonitor.foreground,
                cameraActive,
                settingsRepository.observeThemeMode(),
            ) { config, foreground, camera, theme ->
                val decision = DesktopQuickCardVisibility.evaluate(
                    config = config,
                    foreground = foreground,
                    homePackages = homePackages,
                    ownPackage = appContext.packageName,
                    cameraActive = camera,
                )
                RuntimeRender(config, decision, ThemeMode.fromStored(theme), foreground)
            }.collect { render ->
                WjxFileLogger.i(
                    TAG,
                    "render show=${render.decision.show} reason=${render.decision.reason} " +
                        "pkg=${render.foreground.packageName} cls=${render.foreground.className}",
                )
                controller.render(render.config, render.decision.show, render.themeMode)
            }
        }
        WjxFileLogger.i(TAG, "started homePackages=${homePackages.joinToString()}")
    }

    fun stop() {
        renderJob?.cancel()
        renderJob = null
        controller.stop()
        WjxFileLogger.i(TAG, "stopped")
    }

    private data class RuntimeRender(
        val config: DesktopQuickCardConfig,
        val decision: DesktopQuickCardVisibilityDecision,
        val themeMode: ThemeMode,
        val foreground: ForegroundApp,
    )

    private companion object {
        const val TAG = "DesktopCardRuntime"
    }
}
