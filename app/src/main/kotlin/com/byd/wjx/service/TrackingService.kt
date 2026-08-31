package com.byd.wjx.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.byd.wjx.data.automation.AutomationEngine
import com.byd.wjx.data.charging.ChargingCatchUpCoordinator
import com.byd.wjx.data.repository.TrackingStateStore
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.domain.calculator.DrivingMetricsCoordinator
import com.byd.wjx.domain.tracker.DriveSessionCoordinator
import com.byd.wjx.domain.tracker.TripTracker
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.cluster.ClusterContainerCoordinator
import com.byd.wjx.cluster.ClusterFrameUi7
import com.byd.wjx.service.runtime.TrackingFeatureRuntimes
import com.byd.wjx.service.runtime.TrackingLocationCoordinator
import com.byd.wjx.service.runtime.TrackingNotificationController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var tripTracker: TripTracker
    @Inject lateinit var historyImporter: com.byd.wjx.data.local.HistoryImporter
    @Inject lateinit var automationEngine: AutomationEngine
    @Inject lateinit var sharedAdaptiveLoop: com.byd.wjx.data.loop.SharedAdaptiveLoop
    @Inject lateinit var tripRecorder: com.byd.wjx.data.trips.TripRecorder
    @Inject lateinit var helperBootstrap: com.byd.wjx.data.vehicle.HelperBootstrap
    @Inject lateinit var helperClient: HelperClient
    @Inject lateinit var trackingStateStore: TrackingStateStore
    @Inject lateinit var vehicleStateRepository: VehicleStateRepository
    @Inject lateinit var driveSessionCoordinator: DriveSessionCoordinator
    @Inject lateinit var drivingMetricsCoordinator: DrivingMetricsCoordinator
    @Inject lateinit var chargingCatchUpCoordinator: ChargingCatchUpCoordinator
    @Inject lateinit var energyFlowStatsStore: com.byd.wjx.data.energy.EnergyFlowStatsStore
    @Inject lateinit var locationCoordinator: TrackingLocationCoordinator
    @Inject lateinit var notificationController: TrackingNotificationController
    @Inject lateinit var featureRuntimes: TrackingFeatureRuntimes

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val helperReadiness = CompletableDeferred<Boolean>()
    private var pollingJob: Job? = null
    private var ledRuntimeGateJob: Job? = null
    private var tripTrackerOwner: Long = 0L

    companion object {
        private const val TAG = "TrackingService"
        private const val EXTRA_START_REASON = "start_reason"
        private const val HELPER_READINESS_WAIT_MS = 9_000L
        private const val LED_ACTIVATION_CHECK_INTERVAL_MS = 5_000L

        fun start(context: Context, reason: String = "manual") {
            val intent = Intent(context, TrackingService::class.java)
                .putExtra(EXTRA_START_REASON, reason)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        tripTrackerOwner = tripTracker.claimOwnership()
        WjxFileLogger.i(TAG, "onCreate: starting TrackingService")
        ChainLog.append(this, "TrackingService onCreate")
        notificationController.createChannel()
        startForeground(
            TrackingNotificationController.NOTIFICATION_ID,
            notificationController.startingNotification(),
        )
        ChainLog.append(this, "startForeground OK")
        locationCoordinator.start()

        // Restore or discard the persisted driving-session anchor before polling starts.
        publishTripDistanceKm(null)
        val sessionInitialization = driveSessionCoordinator.initialize()
        publishSessionStartedAt(sessionInitialization.sessionStartedAt)

        // Bootstrap and protocol-check the native helper before the first vehicle snapshot. The
        // polling coroutine waits on helperReadiness with a timeout, then lets normal backoff
        // retry the Binder-only path until the helper becomes ready.
        serviceScope.launch {
            try {
                val ok = helperBootstrap.ensureVehicleReadsReady()
                if (ok) {
                    ClusterFrameUi7.get(this@TrackingService).recoverStale(helperClient)
                    ClusterContainerCoordinator.get(this@TrackingService).recoverStale(helperClient)
                }
                helperReadiness.complete(ok)
                WjxFileLogger.i(TAG, "HelperBootstrap.ensureVehicleReadsReady=$ok")
                ChainLog.append(this@TrackingService, "Helper daemon: ${if (ok) "alive" else "unreachable"}")
            } catch (e: Exception) {
                helperReadiness.complete(false)
                WjxFileLogger.w(TAG, "HelperBootstrap.ensureRunning failed: ${e.message}", e)
                ChainLog.append(this@TrackingService, "Helper bootstrap failed: ${e.message}")
            }
        }

        // Keep steering-wheel star control bound across boot and every wake. The bind can lose the
        // boot-time race, so we verify-and-retry here (in its own coroutine, independent of the
        // helper bootstrap above) and re-run on every SCREEN_ON / USER_PRESENT.
        // Steering-wheel and network monitors must be active before the first vehicle tick.
        featureRuntimes.startBeforePolling(serviceScope)
        startPolling()
        featureRuntimes.startAfterPolling(serviceScope)
        startLedRuntimeGate()
        publishRunning(true)
        ChainLog.append(this, "TrackingService fully started")
        WjxFileLogger.i(TAG, "fully started")

        // Event-based sync on service start.
        serviceScope.launch {
            try {
                val result = historyImporter.runSync()
                WjxFileLogger.i(TAG, "Sync: ${result.details ?: result.error ?: "ok"}")
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "Sync failed: ${e.message}", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_START_REASON) ?: "sticky_restart"
        featureRuntimes.refreshLedAccess(serviceScope)
        featureRuntimes.requestLedReconnect(reason)
        WjxFileLogger.i(TAG, "onStartCommand reason=$reason startId=$startId")
        return START_STICKY
    }

    override fun onDestroy() {
        WjxFileLogger.w(TAG, "onDestroy: stopping TrackingService")
        ChainLog.append(this, "TrackingService onDestroy")
        pollingJob?.cancel()
        pollingJob = null
        ledRuntimeGateJob?.cancel()
        ledRuntimeGateJob = null
        sharedAdaptiveLoop.stop()
        chargingCatchUpCoordinator.stop()
        energyFlowStatsStore.flush()
        drivingMetricsCoordinator.reset()
        // Do not clear the driving-session anchor here. A process kill mid-trip must
        // remain resumable; DriveSessionCoordinator clears it only after a real idle close.

        // Flush an active GPS trip asynchronously.
        // Android gives ~5 seconds after onDestroy before killing process
        val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val trackerOwner = tripTrackerOwner
        shutdownScope.launch {
            try {
                withTimeout(4000L) {
                    tripTracker.forceEnd(trackerOwner)
                }
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "Graceful shutdown: ${e.message}", e)
            }
        }

        featureRuntimes.stop()
        // AutomationEngine is @Singleton and outlives this service instance. Cancelling its
        // internal scope here leaves confirm-action callbacks dead after KeepAlive restarts us.
        serviceScope.cancel()
        locationCoordinator.stop()

        publishRunning(false)

        // KeepAliveService is the single restart owner; its watchdog and alarm handle process loss.
        try {
            KeepAliveService.start(this, "tracking_destroy")
            WjxFileLogger.i(TAG, "Restart delegated to KeepAliveService")
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "Failed to delegate restart: ${e.message}", e)
        }

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        WjxFileLogger.w(TAG, "onTaskRemoved: delegating restart to KeepAliveService")
        ChainLog.append(this, "onTaskRemoved → restart")
        try {
            KeepAliveService.start(this, "tracking_task_removed")
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "Failed to schedule restart on task removed: ${e.message}", e)
        }
    }

    private fun publishRunning(value: Boolean) {
        trackingStateStore.setRunning(value)
    }

    private fun publishRangeKm(value: Double?) {
        trackingStateStore.setRangeKm(value)
    }

    private fun publishSessionStartedAt(value: Long?) {
        trackingStateStore.setSessionStartedAt(value)
    }

    private fun publishTripDistanceKm(value: Double?) {
        trackingStateStore.setTripDistanceKm(value)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        WjxFileLogger.i(TAG, "Starting polling via SharedAdaptiveLoop")
        pollingJob = serviceScope.launch pollingPipeline@{
            val helperReady = withTimeoutOrNull(HELPER_READINESS_WAIT_MS) {
                helperReadiness.await()
            } == true
            if (!helperReady) {
                WjxFileLogger.w(TAG, "helper read startup gate timed out; Binder polling will retry")
            }
            // Everything below belongs to one polling pipeline. If this parent exits, the
            // vehicle loop and all consumers stop together instead of leaving an orphan poller.
            chargingCatchUpCoordinator.start(this@pollingPipeline)
            // Cold-start reconciliation BEFORE subscribing — so we never receive
            // a tick into a stale open trip from a previous session.
            runCatching { tripRecorder.reconcileColdStart() }
                .onFailure { WjxFileLogger.w(TAG, "Cold-start reconciliation failed", it) }

            sharedAdaptiveLoop.start(this@pollingPipeline)

            launch {
                sharedAdaptiveLoop.actionFlow.collect { data ->
                    try {
                        automationEngine.evaluate(data, trackingStateStore.sessionStartedAt.value)
                    } catch (e: Exception) {
                        WjxFileLogger.e(TAG, "Automation fast tick failed: ${e.message}", e)
                    }
                }
            }

            launch {
                sharedAdaptiveLoop.actionFlow.collect { data ->
                    featureRuntimes.onActionData(data)
                }
            }

            sharedAdaptiveLoop.flow.collect { data ->
                try {
                    chargingCatchUpCoordinator.onSample(data)

                    val loc = trackingStateStore.lastLocation.value
                    tripTracker.onData(tripTrackerOwner, data, loc)

                    val nowMs = System.currentTimeMillis()
                    val session = driveSessionCoordinator.onSample(
                        nowMs = nowMs,
                        data = data,
                        trackerDriving = tripTracker.state.value ==
                            com.byd.wjx.domain.tracker.TripState.DRIVING,
                    )
                    energyFlowStatsStore.onSample(nowMs, data, session.sessionStartedAt)
                    publishSessionStartedAt(session.sessionStartedAt)
                    val metrics = drivingMetricsCoordinator.onSample(nowMs, data, session)
                    publishRangeKm(metrics.rangeKm)
                    publishTripDistanceKm(metrics.tripDistanceKm)

                    // Idle drain tracked via energydata zero-km records only (HistoryImporter).
                    // Live power integration removed — motor power ≠ total battery drain.
                    notificationController.update(data)

                    // Native trip recorder (writes only when energydata absent — i.e. Song/Atto/non-Leopard3)
                    runCatching { tripRecorder.consume(data) }
                        .onFailure {
                            Log.w(TAG, "TripRecorder.consume failed", it)
                            WjxFileLogger.w(TAG, "TripRecorder.consume failed: ${it.message}", it)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Downstream consumer threw on tick: ${e.message}", e)
                    WjxFileLogger.e(TAG, "Downstream consumer threw on tick: ${e.message}", e)
                }
            }
        }
    }

    private fun startLedRuntimeGate() {
        ledRuntimeGateJob?.cancel()
        ledRuntimeGateJob = serviceScope.launch {
            while (isActive) {
                featureRuntimes.refreshLedAccess(this@TrackingService.serviceScope)
                delay(LED_ACTIVATION_CHECK_INTERVAL_MS)
            }
        }
    }

}
