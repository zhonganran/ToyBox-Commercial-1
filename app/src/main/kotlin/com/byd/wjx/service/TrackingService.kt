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
import com.byd.wjx.domain.calculator.DrivingMetricsCoordinator
import com.byd.wjx.domain.tracker.DriveSessionCoordinator
import com.byd.wjx.domain.tracker.TripTracker
import com.byd.wjx.desktopcard.DesktopQuickCardRuntime
import com.byd.wjx.electronicmirror.ElectronicMirrorCoordinator
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.service.runtime.CameraRuntimeCoordinator
import com.byd.wjx.service.runtime.SteeringWheelRuntimeCoordinator
import com.byd.wjx.service.runtime.TrackingLocationCoordinator
import com.byd.wjx.service.runtime.TrackingNotificationController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var tripTracker: TripTracker
    @Inject lateinit var historyImporter: com.byd.wjx.data.local.HistoryImporter
    @Inject lateinit var automationEngine: AutomationEngine
    @Inject lateinit var networkAvailableMonitor: com.byd.wjx.data.automation.NetworkAvailableMonitor
    @Inject lateinit var sharedAdaptiveLoop: com.byd.wjx.data.loop.SharedAdaptiveLoop
    @Inject lateinit var tripRecorder: com.byd.wjx.data.trips.TripRecorder
    @Inject lateinit var helperBootstrap: com.byd.wjx.data.vehicle.HelperBootstrap
    @Inject lateinit var lyricRuntime: com.byd.wjx.lyrics.LyricRuntime
    @Inject lateinit var trackingStateStore: TrackingStateStore
    @Inject lateinit var vehicleStateRepository: VehicleStateRepository
    @Inject lateinit var driveSessionCoordinator: DriveSessionCoordinator
    @Inject lateinit var drivingMetricsCoordinator: DrivingMetricsCoordinator
    @Inject lateinit var chargingCatchUpCoordinator: ChargingCatchUpCoordinator
    @Inject lateinit var energyFlowStatsStore: com.byd.wjx.data.energy.EnergyFlowStatsStore
    @Inject lateinit var locationCoordinator: TrackingLocationCoordinator
    @Inject lateinit var cameraRuntimeCoordinator: CameraRuntimeCoordinator
    @Inject lateinit var steeringWheelRuntimeCoordinator: SteeringWheelRuntimeCoordinator
    @Inject lateinit var notificationController: TrackingNotificationController
    @Inject lateinit var ledRuntimeCoordinator: com.byd.wjx.led.LedRuntimeCoordinator
    @Inject lateinit var electronicMirrorCoordinator: ElectronicMirrorCoordinator
    @Inject lateinit var desktopQuickCardRuntime: DesktopQuickCardRuntime

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val helperReadiness = CompletableDeferred<Boolean>()
    private var pollingJob: Job? = null
    private var tripTrackerOwner: Long = 0L

    companion object {
        private const val TAG = "TrackingService"
        private const val HELPER_READINESS_WAIT_MS = 9_000L

        fun start(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
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

        // v2.4.8: clear odometer buffers poisoned by the startup-race that
        // shipped in v2.4.5–v2.4.7 (DiPars returned Mileage:0 on first poll
        // and the zero row stuck around, blocking every later real reading
        // as a "jump > 100 km"). Safe no-op once buffer is healthy.
        serviceScope.launch {
            val cleared = drivingMetricsCoordinator.cleanupCorruptStartupRows()
            if (cleared > 0) {
                Log.i(TAG, "Cleared $cleared corrupt odometer-buffer row(s) (legacy startup-race)")
                WjxFileLogger.i(TAG, "Cleared $cleared corrupt odometer-buffer row(s)")
            }
        }

        // Bootstrap and protocol-check the native helper before the first vehicle snapshot. The
        // polling coroutine waits on helperReadiness with a timeout, then lets normal backoff
        // retry the Binder-only path until the helper becomes ready.
        serviceScope.launch {
            try {
                val ok = helperBootstrap.ensureVehicleReadsReady()
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
        steeringWheelRuntimeCoordinator.start(serviceScope)

        // Start the network monitor BEFORE polling so the first evaluate() tick
        // already has access to the latest VALIDATED edge state.
        networkAvailableMonitor.start()
        startPolling()
        cameraRuntimeCoordinator.start(serviceScope)
        desktopQuickCardRuntime.start(serviceScope, cameraRuntimeCoordinator.active)
        electronicMirrorCoordinator.start(cameraRuntimeCoordinator.active)
        lyricRuntime.start(cameraRuntimeCoordinator.active, electronicMirrorCoordinator.active)
        ledRuntimeCoordinator.start(serviceScope)
        publishRunning(true)
        ChainLog.append(this, "TrackingService fully started")
        WjxFileLogger.i(TAG, "fully started")

        // v2.0: event-based sync on service start
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
        return START_STICKY
    }

    override fun onDestroy() {
        WjxFileLogger.w(TAG, "onDestroy: stopping TrackingService")
        ChainLog.append(this, "TrackingService onDestroy")
        pollingJob?.cancel()
        pollingJob = null
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

        lyricRuntime.stop()
        electronicMirrorCoordinator.stop()
        desktopQuickCardRuntime.stop()
        ledRuntimeCoordinator.stop()
        cameraRuntimeCoordinator.stop()
        steeringWheelRuntimeCoordinator.stop()
        networkAvailableMonitor.stop()
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
                    runCatching { ledRuntimeCoordinator.onVehicleData(data) }
                        .onFailure { WjxFileLogger.e(TAG, "LED linkage tick failed: ${it.message}", it) }
                }
            }

            // Camera ownership is serialized inside ElectronicMirrorCoordinator. A dedicated
            // collector prevents a slow first camera open from overlapping later turn-signal ticks.
            launch {
                sharedAdaptiveLoop.actionFlow.collect { data ->
                    runCatching { electronicMirrorCoordinator.onVehicleData(data) }
                        .onFailure {
                            WjxFileLogger.e(TAG, "Electronic mirror tick failed: ${it.message}", it)
                        }
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

}
