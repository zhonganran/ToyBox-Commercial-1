package com.byd.wjx.domain.calculator

import android.util.Log
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.domain.tracker.DriveSessionUpdate
import javax.inject.Inject
import javax.inject.Singleton

data class DrivingMetricsResult(
    val rangeKm: Double?,
    val tripDistanceKm: Double?,
    val displayConsumption: Double?,
    val trend: Trend,
)

/** Owns the ordered calculation pipeline for each full vehicle sample. */
@Singleton
class DrivingMetricsCoordinator @Inject constructor(
    private val odometerBuffer: OdometerConsumptionBuffer,
    private val liveTripBuffer: LiveTripBuffer,
    private val socInterpolator: SocInterpolator,
    private val rangeCalculator: RangeCalculator,
    private val consumptionAggregator: ConsumptionAggregator,
) {
    private var lastSummaryLogAt: Long = 0L

    suspend fun onSample(
        nowMs: Long,
        data: DiParsData,
        session: DriveSessionUpdate,
    ): DrivingMetricsResult {
        val sessionId = session.sessionStartedAt
        odometerBuffer.onSample(
            mileage = data.mileage,
            totalElec = data.totalElecConsumption,
            socPercent = data.soc,
            sessionId = sessionId,
        )
        liveTripBuffer.onSample(
            mileage = data.mileage,
            totalElec = data.totalElecConsumption,
            sessionId = sessionId,
        )
        socInterpolator.onSample(
            soc = data.soc,
            totalElecKwh = data.totalElecConsumption,
            sessionId = sessionId,
        )

        val recentAverage = odometerBuffer.recentAvgConsumption()
        val shortAverage = odometerBuffer.shortAvgConsumption()
        val displayValue = BigNumberCalculator.computeDisplay(
            tripKm = session.tripDistanceKm,
            tripKwh = session.tripEnergyKwh,
            lastTripAvg = session.lastTripAverage,
            recentAvg25km = recentAverage,
            sessionActive = sessionId != null,
        )
        consumptionAggregator.onSample(
            now = nowMs,
            displayValue = displayValue,
            recentAvg = recentAverage,
            shortAvg = shortAverage,
        )
        val rangeKm = rangeCalculator.estimate(
            soc = data.soc,
            totalElecKwh = data.totalElecConsumption,
        )
        maybeLogSummary(nowMs, data, sessionId)
        val consumption = consumptionAggregator.state.value
        return DrivingMetricsResult(
            rangeKm = rangeKm,
            tripDistanceKm = session.tripDistanceKm,
            displayConsumption = consumption.displayValue,
            trend = consumption.trend,
        )
    }

    fun reset() {
        consumptionAggregator.reset()
    }

    private suspend fun maybeLogSummary(
        nowMs: Long,
        data: DiParsData,
        sessionId: Long?,
    ) {
        if (nowMs - lastSummaryLogAt < SUMMARY_LOG_INTERVAL_MS) return
        lastSummaryLogAt = nowMs
        val status = odometerBuffer.status()
        val state = consumptionAggregator.state.value
        val carry = socInterpolator.carryOver(data.totalElecConsumption, data.soc)
        Log.i(
            TAG,
            "session id=$sessionId bufferRows=${status.rowCount} " +
                "newestKm=${status.newestMileageKm?.let { "%.1f".format(it) } ?: "-"} " +
                "recentAvg=${"%.2f".format(status.recentAvg)} " +
                "shortAvg=${status.shortAvg?.let { "%.2f".format(it) } ?: "-"} " +
                "display=${state.displayValue?.let { "%.1f".format(it) } ?: "-"} " +
                "trend=${state.trend} socCarry=${"%.3f".format(carry)} " +
                "powerState=${data.powerState}",
        )

        val liveAverage = liveTripBuffer.avgOverLastKm(RangeAvgSource.LIVE_WINDOW_KM)
        Log.i(
            TAG,
            "range sessionKm=${"%.1f".format(liveTripBuffer.sessionKm())} " +
                "liveAvg=${liveAverage?.let { "%.1f".format(it) } ?: "-"} " +
                "samples=${liveTripBuffer.sampleCount()}",
        )
    }

    private companion object {
        const val TAG = "DrivingMetricsCoordinator"
        const val SUMMARY_LOG_INTERVAL_MS = 60_000L
    }
}
