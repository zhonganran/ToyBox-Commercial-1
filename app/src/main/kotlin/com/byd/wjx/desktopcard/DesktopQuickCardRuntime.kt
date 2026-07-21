package com.byd.wjx.desktopcard

import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.theme.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class DesktopQuickCardRuntime @Inject constructor(
    private val repository: DesktopQuickCardRepository,
    private val controller: DesktopQuickCardController,
    private val settingsRepository: SettingsRepository,
) {
    private var renderJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (renderJob?.isActive == true) return
        controller.start(scope)
        repository.refresh()
        renderJob = scope.launch {
            combine(
                repository.config,
                settingsRepository.observeThemeMode(),
                repository.runtimeRevision,
            ) { config, theme, _ ->
                config to ThemeMode.fromStored(theme)
            }.collect { (config, theme) ->
                val visibility = DesktopQuickCardVisibility.evaluate(config)
                WjxFileLogger.i(TAG, "render show=${visibility.show} reason=${visibility.reason}")
                controller.render(config, theme)
            }
        }
        WjxFileLogger.i(TAG, "started; overlay remains visible across all foreground interfaces")
    }

    fun stop() {
        renderJob?.cancel()
        renderJob = null
        controller.stop()
        WjxFileLogger.i(TAG, "stopped")
    }

    private companion object {
        const val TAG = "DesktopCardRuntime"
    }
}
