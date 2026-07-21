package com.byd.wjx.statusbar

import android.location.Location
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.nativestack.OnDemandVehicleTelemetryReader
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.lyrics.MusicPlaybackSnapshot
import com.byd.wjx.lyrics.MusicPlaybackStore
import com.byd.wjx.ui.theme.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class CustomStatusBarRuntime @Inject constructor(
    private val repository: CustomStatusBarRepository,
    private val controller: CustomStatusBarController,
    private val vehicleStateRepository: VehicleStateRepository,
    private val settingsRepository: SettingsRepository,
    private val onDemandTelemetryReader: OnDemandVehicleTelemetryReader,
    private val systemStatusMonitor: SystemStatusMonitor,
    private val nativeStatusBarCoordinator: NativeStatusBarReplacementCoordinator,
    private val trackingStateRepository: TrackingStateRepository,
    private val weatherRepository: StatusBarWeatherRepository,
) {
    private var renderJob: Job? = null
    private var powertrainJob: Job? = null
    private var nativeControlJob: Job? = null
    private var weatherJob: Job? = null
    private val powertrainTelemetry = MutableStateFlow<DiParsData?>(null)

    fun start(scope: CoroutineScope) {
        if (renderJob?.isActive == true) return
        systemStatusMonitor.start()
        powertrainJob = scope.launch {
            combine(repository.config, vehicleStateRepository.connected) { config, connected ->
                connected && config.needsPowertrainTelemetry()
            }
                .distinctUntilChanged()
                .collectLatest { active ->
                    powertrainTelemetry.value = null
                    if (!active) return@collectLatest
                    while (currentCoroutineContext().isActive) {
                        powertrainTelemetry.value = runCatching {
                            onDemandTelemetryReader.readPowertrainTelemetry()
                        }.getOrNull()
                        delay(POWERTRAIN_REFRESH_MS)
                    }
                }
        }
        weatherJob = scope.launch {
            combine(repository.config, trackingStateRepository.lastLocation) { config, location ->
                location?.takeIf { config.needsWeather() }?.toWeatherRequest()
            }.distinctUntilChanged().collectLatest { request ->
                if (request == null) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    weatherRepository.refresh(request.toLocation())
                    delay(WEATHER_REFRESH_MS)
                }
            }
        }
        renderJob = scope.launch {
            combine(
                repository.config,
                vehicleStateRepository.state,
                MusicPlaybackStore.state,
                settingsRepository.observeThemeMode(),
                clockFlow(),
            ) { config, vehicle, music, theme, nowMs ->
                RenderInputs(
                    config = config,
                    vehicle = vehicle,
                    music = music,
                    themeMode = ThemeMode.fromStored(theme),
                    nowMs = nowMs,
                )
            }.combine(systemStatusMonitor.snapshot) { inputs, systemStatus ->
                inputs.copy(systemStatus = systemStatus)
            }.combine(trackingStateRepository.cameraActive) { inputs, cameraActive ->
                inputs.copy(cameraActive = cameraActive)
            }.combine(weatherRepository.snapshot) { inputs, weather ->
                inputs.copy(weather = weather)
            }.combine(powertrainTelemetry) { inputs, powertrain ->
                CustomStatusBarFormatter.build(
                    config = inputs.config,
                    vehicle = inputs.vehicle,
                    music = inputs.music,
                    nowMs = inputs.nowMs,
                    themeMode = inputs.themeMode,
                    powertrain = powertrain,
                    systemStatus = inputs.systemStatus,
                    weather = inputs.weather,
                    cameraActive = inputs.cameraActive,
                )
            }.combine(repository.runtimeRevision) { state, _ -> state }
                .collect(controller::render)
        }
        nativeControlJob = scope.launch {
            combine(repository.config, controller.replacementReady) { config, replacementReady ->
                NativeStatusBarReplacementPolicy.shouldReplace(config.enabled, replacementReady)
            }.distinctUntilChanged().collectLatest { replace ->
                var firstAttempt = true
                while (currentCoroutineContext().isActive) {
                    nativeStatusBarCoordinator.reconcile(
                        replace = replace,
                        reason = if (firstAttempt) "runtime" else "runtime_reconcile",
                        force = !firstAttempt && replace,
                    )
                    firstAttempt = false
                    delay(NATIVE_STATUS_BAR_RECONCILE_MS)
                }
            }
        }
        WjxFileLogger.i(TAG, "started")
    }

    fun stop() {
        renderJob?.cancel()
        renderJob = null
        powertrainJob?.cancel()
        powertrainJob = null
        nativeControlJob?.cancel()
        nativeControlJob = null
        weatherJob?.cancel()
        weatherJob = null
        powertrainTelemetry.value = null
        systemStatusMonitor.stop()
        controller.stop()
        nativeStatusBarCoordinator.restoreAsync("runtime_stop")
        WjxFileLogger.i(TAG, "stopped")
    }

    private fun clockFlow() = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(CLOCK_REFRESH_MS)
        }
    }

    private companion object {
        const val TAG = "CustomStatusBarRuntime"
        const val CLOCK_REFRESH_MS = 15_000L
        const val POWERTRAIN_REFRESH_MS = 2_000L
        const val NATIVE_STATUS_BAR_RECONCILE_MS = 60_000L
        const val WEATHER_REFRESH_MS = 30 * 60 * 1_000L
    }

    private data class RenderInputs(
        val config: CustomStatusBarConfig,
        val vehicle: VehicleState,
        val music: MusicPlaybackSnapshot,
        val themeMode: ThemeMode,
        val nowMs: Long,
        val systemStatus: SystemStatusSnapshot = SystemStatusSnapshot(),
        val weather: StatusBarWeatherSnapshot = StatusBarWeatherSnapshot(),
        val cameraActive: Boolean = false,
    )

    private data class WeatherRequest(
        val latitudeHundredth: Int,
        val longitudeHundredth: Int,
    ) {
        fun toLocation(): Location = Location("statusbar_weather").apply {
            latitude = latitudeHundredth / 100.0
            longitude = longitudeHundredth / 100.0
        }
    }

    private fun Location.toWeatherRequest(): WeatherRequest = WeatherRequest(
        latitudeHundredth = (latitude * 100.0).roundToInt(),
        longitudeHundredth = (longitude * 100.0).roundToInt(),
    )
}
