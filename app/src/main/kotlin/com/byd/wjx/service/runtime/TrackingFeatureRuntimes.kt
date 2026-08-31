package com.byd.wjx.service.runtime

import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.camera.blindspot.BlindSpotController
import com.byd.wjx.clustercard.ClusterCardRuntime
import com.byd.wjx.data.automation.NetworkAvailableMonitor
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.desktopcard.DesktopQuickCardRuntime
import com.byd.wjx.led.ipixelfa.IpixelFaRuntimeCoordinator
import com.byd.wjx.led.mafu.MafuRuntimeCoordinator
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.lyrics.LyricRuntime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** Owns feature runtime ordering and keeps TrackingService focused on vehicle-session orchestration. */
@Singleton
class TrackingFeatureRuntimes @Inject constructor(
    private val steeringWheel: SteeringWheelRuntimeCoordinator,
    private val networkMonitor: NetworkAvailableMonitor,
    private val camera: CameraRuntimeCoordinator,
    private val blindSpot: BlindSpotController,
    private val desktopCard: DesktopQuickCardRuntime,
    private val clusterCard: ClusterCardRuntime,
    private val lyrics: LyricRuntime,
    private val ipixelFa: IpixelFaRuntimeCoordinator,
    private val mafu: MafuRuntimeCoordinator,
    private val activationManager: ActivationManager,
) {
    private var prePollingStarted = false
    private var visualRuntimesStarted = false
    private var ledRuntimesStarted = false

    @Synchronized
    fun startBeforePolling(scope: CoroutineScope) {
        if (prePollingStarted) return
        steeringWheel.start(scope)
        networkMonitor.start()
        prePollingStarted = true
    }

    @Synchronized
    fun startAfterPolling(scope: CoroutineScope) {
        if (visualRuntimesStarted) return
        camera.start(scope)
        blindSpot.start(camera.active)
        desktopCard.start(scope)
        clusterCard.start(scope)
        lyrics.start(camera.active)
        visualRuntimesStarted = true
    }

    @Synchronized
    fun refreshLedAccess(scope: CoroutineScope) {
        val allowed = activationManager.canUse(PremiumFeature.LED_LINKAGE)
        if (allowed == ledRuntimesStarted) return
        if (allowed) {
            ipixelFa.start(scope)
            mafu.start(scope)
            ledRuntimesStarted = true
            WjxFileLogger.i(TAG, "LED runtimes started: activation allowed")
        } else {
            stopLedRuntimes()
            WjxFileLogger.i(TAG, "LED runtimes stopped: activation locked")
        }
    }

    @Synchronized
    fun requestLedReconnect(reason: String) {
        if (!ledRuntimesStarted) {
            WjxFileLogger.i(TAG, "LED reconnect skipped: runtimes inactive reason=$reason")
            return
        }
        WjxFileLogger.i(TAG, "LED reconnect requested reason=$reason")
        ipixelFa.requestReconnect(reason)
        mafu.requestReconnect(reason)
    }

    suspend fun onActionData(data: DiParsData) {
        blindSpot.onPollSnapshot(
            data,
            accessAllowed = activationManager.canUse(PremiumFeature.ELECTRONIC_REARVIEW),
        )
        runCatching { ipixelFa.onVehicleData(data) }
            .onFailure { WjxFileLogger.e(TAG, "FA LED linkage tick failed: ${it.message}", it) }
        runCatching { mafu.onVehicleData(data) }
            .onFailure { WjxFileLogger.e(TAG, "Mafu LED linkage tick failed: ${it.message}", it) }
    }

    @Synchronized
    fun stop() {
        lyrics.stop()
        clusterCard.stop()
        desktopCard.stop()
        blindSpot.stop()
        stopLedRuntimes()
        camera.stop()
        steeringWheel.stop()
        networkMonitor.stop()
        visualRuntimesStarted = false
        prePollingStarted = false
    }

    private fun stopLedRuntimes() {
        mafu.stop()
        ipixelFa.stop()
        ledRuntimesStarted = false
    }

    private companion object {
        const val TAG = "TrackingFeatureRuntimes"
    }
}
