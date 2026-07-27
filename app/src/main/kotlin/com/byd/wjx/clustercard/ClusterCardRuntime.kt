package com.byd.wjx.clustercard

import com.byd.wjx.data.nativestack.OnDemandVehicleTelemetryReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class ClusterCardRuntime @Inject constructor(
    private val repository: ClusterCardRepository,
    private val controller: ClusterCardController,
    private val vehicleStateRepository: VehicleStateRepository,
    private val telemetryReader: OnDemandVehicleTelemetryReader,
) {
    private val telemetryData = MutableStateFlow<DiParsData?>(null)
    private var renderJob: Job? = null
    private var telemetryJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (renderJob?.isActive == true) return
        repository.refresh()
        startTelemetry(scope)
        val renderInputs = combine(
            repository.config,
            vehicleStateRepository.state,
            telemetryData,
            repository.runtimeRevision,
        ) { config, vehicleState, telemetry, _ ->
            RenderInput(config, vehicleState, telemetry)
        }
        renderJob = scope.launch {
            renderInputs.combine(heartbeat()) { input, _ -> input }
                .collect { input ->
                    controller.render(
                        config = input.config,
                        baseData = input.vehicleState.data.takeIf { input.vehicleState.connected },
                        telemetryData = input.telemetry.takeIf { input.vehicleState.connected },
                    )
                }
        }
        WjxFileLogger.i(TAG, "started")
    }

    fun stop() {
        renderJob?.cancel()
        telemetryJob?.cancel()
        renderJob = null
        telemetryJob = null
        telemetryData.value = null
        controller.stop()
        WjxFileLogger.i(TAG, "stopped")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startTelemetry(scope: CoroutineScope) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            combine(repository.config, vehicleStateRepository.connected) { config, connected ->
                TelemetryRequest(
                    fields = ClusterCardMetricCatalog.telemetryFields(config),
                    active = config.enabled && config.metricIds.isNotEmpty() && connected,
                )
            }
                .distinctUntilChanged()
                .flatMapLatest { request ->
                    if (!request.active || request.fields.isEmpty()) {
                        flowOf(null)
                    } else {
                        telemetryFlow(request.fields)
                    }
                }
                .collect { telemetryData.value = it }
        }
    }

    private fun telemetryFlow(fields: Set<String>) = flow {
        val interval = if (fields.any(RPM_FIELDS::contains)) RPM_INTERVAL_MS else TEMPERATURE_INTERVAL_MS
        while (currentCoroutineContext().isActive) {
            emit(
                runCatching { telemetryReader.readPowertrainTelemetry(fields) }
                    .onFailure { WjxFileLogger.w(TAG, "telemetry read failed: ${it.message}") }
                    .getOrNull(),
            )
            delay(interval)
        }
    }

    private fun heartbeat() = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(DISPLAY_RETRY_INTERVAL_MS)
        }
    }

    private data class RenderInput(
        val config: ClusterCardConfig,
        val vehicleState: VehicleState,
        val telemetry: DiParsData?,
    )

    private data class TelemetryRequest(
        val fields: Set<String>,
        val active: Boolean,
    )

    private companion object {
        const val TAG = "ClusterCardRuntime"
        const val RPM_INTERVAL_MS = 1_000L
        const val TEMPERATURE_INTERVAL_MS = 2_000L
        const val DISPLAY_RETRY_INTERVAL_MS = 2_000L
        val RPM_FIELDS = setOf("engineRpm", "frontMotorSpeed", "rearMotorSpeed")
    }
}
