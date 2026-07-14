package com.byd.wjx.data.charging

import android.util.Log
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.log.WjxFileLogger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns charging catch-up lifecycle, gun-edge detection and charging-power evidence.
 * TrackingService only feeds full vehicle samples and supplies its lifecycle scope.
 */
class ChargingCatchUpCoordinator @Inject constructor(
    private val detector: AutoserviceChargingDetector,
    private val journal: CatchUpJournal,
) {
    private val gunEdgeDetector = GunStateEdgeDetector()
    private val powerLock = Any()
    private val gunEdgeInFlight = AtomicBoolean(false)
    private val catchUpRetryInFlight = AtomicBoolean(false)

    @Volatile private var scope: CoroutineScope? = null
    private var observedChargingPowerKwAbs = 0.0
    private var lastCatchUpRetryAtMs = 0L

    @Volatile private var catchUpResolved = false
    @Volatile private var socRearmUsed = false

    fun start(scope: CoroutineScope) {
        this.scope = scope
        gunEdgeDetector.reset()
        synchronized(powerLock) { observedChargingPowerKwAbs = 0.0 }
        catchUpResolved = false
        socRearmUsed = false
        lastCatchUpRetryAtMs = System.currentTimeMillis()
        scope.launch { runStartupCatchUp() }
    }

    fun stop() {
        scope = null
    }

    /** Called once for every full vehicle snapshot. */
    suspend fun onSample(data: DiParsData) {
        detector.onSample(data)

        val socAboveAnchor = detector.recordParkedAnchor(data)
        if (socAboveAnchor && catchUpResolved && !socRearmUsed) {
            socRearmUsed = true
            catchUpResolved = false
            Log.i(TAG, "Catch-up re-arm: live SOC above anchor with gun out")
            WjxFileLogger.i(TAG, "Catch-up re-arm: live SOC above anchor with gun out soc=${data.soc}")
            scope?.launch {
                runCatching { journal.append("REARM soc=${data.soc} above anchor") }
                retryUnresolvedCatchUp()
            }
        }

        val chargingPower = data.power ?: 0.0
        if (chargingPower < 0.0) {
            val magnitude = -chargingPower
            synchronized(powerLock) {
                if (magnitude > observedChargingPowerKwAbs) {
                    observedChargingPowerKwAbs = magnitude
                }
            }
        }

        observeGunState(data.chargeGunState)
        scheduleCatchUpRetryIfDue(System.currentTimeMillis())
    }

    private suspend fun runStartupCatchUp() {
        try {
            var attempt = 0
            while (true) {
                val result = detector.runCatchUp()
                WjxFileLogger.i(TAG, "Autoservice catch-up: ${result.outcome} (attempt ${attempt + 1})")
                catchUpResolved = result.outcome.isResolved()
                val retryable = result.outcome == CatchUpOutcome.SENTINEL ||
                    result.outcome == CatchUpOutcome.AUTOSERVICE_UNAVAILABLE
                if (!retryable || attempt >= AUTOSERVICE_CATCHUP_MAX_RETRIES) break
                attempt++
                delay(AUTOSERVICE_CATCHUP_RETRY_DELAY_MS)
            }
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "Autoservice catch-up failed: ${e.message}", e)
        }
    }

    private fun observeGunState(gun: Int?) {
        if (!gunEdgeDetector.onSample(gun)) return
        val activeScope = scope ?: return
        if (!gunEdgeInFlight.compareAndSet(false, true)) return
        activeScope.launch {
            try {
                val powerForClassification = synchronized(powerLock) {
                    val value = observedChargingPowerKwAbs.takeIf { it > 0.0 }
                    observedChargingPowerKwAbs = 0.0
                    value
                }
                try {
                    val result = detector.runCatchUp(observedKwAbs = powerForClassification)
                    catchUpResolved = result.outcome.isResolved()
                    Log.i(TAG, "Live end-of-charging gun edge: ${result.outcome}")
                    WjxFileLogger.i(
                        TAG,
                        "Live end-of-charging gun edge outcome=${result.outcome} powerKw=$powerForClassification",
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Live end-of-charging failed: ${e.message}")
                    WjxFileLogger.w(TAG, "Live end-of-charging failed: ${e.message}", e)
                }
            } finally {
                gunEdgeInFlight.set(false)
            }
        }
    }

    private fun scheduleCatchUpRetryIfDue(nowMs: Long) {
        if (catchUpResolved || nowMs - lastCatchUpRetryAtMs < CATCHUP_RETRY_INTERVAL_MS) return
        val activeScope = scope ?: return
        lastCatchUpRetryAtMs = nowMs
        activeScope.launch { retryUnresolvedCatchUp() }
    }

    private suspend fun retryUnresolvedCatchUp() {
        if (!catchUpRetryInFlight.compareAndSet(false, true)) return
        try {
            val result = detector.runCatchUp()
            catchUpResolved = result.outcome.isResolved()
            Log.i(TAG, "Catch-up tick retry: ${result.outcome}")
            WjxFileLogger.i(TAG, "Catch-up tick retry outcome=${result.outcome}")
        } catch (e: Exception) {
            Log.w(TAG, "Catch-up tick retry failed: ${e.message}")
            WjxFileLogger.w(TAG, "Catch-up tick retry failed: ${e.message}", e)
        } finally {
            catchUpRetryInFlight.set(false)
        }
    }

    private fun CatchUpOutcome.isResolved(): Boolean =
        this == CatchUpOutcome.SESSION_CREATED ||
            this == CatchUpOutcome.NO_DELTA ||
            this == CatchUpOutcome.BASELINE_INITIALIZED

    private companion object {
        const val TAG = "ChargingCatchUp"
        const val AUTOSERVICE_CATCHUP_MAX_RETRIES = 4
        const val AUTOSERVICE_CATCHUP_RETRY_DELAY_MS = 3_000L
        const val CATCHUP_RETRY_INTERVAL_MS = 30_000L
    }
}
