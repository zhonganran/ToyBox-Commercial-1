package com.byd.wjx.domain.tracker

import android.location.Location
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.local.dao.TripPointDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

enum class TripState { IDLE, DRIVING }

/**
 * v2.0: GPS collector only. Does NOT create TripEntity records.
 * GPS points are written with tripId=0 and later attached to energydata trips
 * by HistoryImporter.attachGpsPoints().
 */
@Singleton
class TripTracker @Inject constructor(
    private val tripPointDao: TripPointDao,
    private val recordGpsEnabled: suspend () -> Boolean = { true },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(TripState.IDLE)
    val state: StateFlow<TripState> = _state

    private val _tripStartedAt = MutableStateFlow<Long?>(null)
    /**
     * Epoch millis when the IDLE→DRIVING transition was confirmed (≤[START_DELAY_MS] ms after
     * actual movement started). Null when not driving.
     * Set on IDLE→DRIVING, cleared on DRIVING→IDLE and in [forceEnd].
     */
    val tripStartedAt: StateFlow<Long?> = _tripStartedAt

    private var speedAboveThresholdSince: Long? = null
    private var speedZeroSince: Long? = null
    private val pendingPoints = Collections.synchronizedList(mutableListOf<TripPointEntity>())
    private var lastRecordedLocation: Location? = null
    private var lastRecordedAt: Long = 0L
    private var stationarySince: Long? = null
    private var stationaryLocked = false
    private var lastNoGpsFileLogAt: Long = 0L

    companion object {
        private const val TAG = "TripTracker"
        private const val SPEED_THRESHOLD = 3    // km/h
        private const val START_DELAY_MS = 5_000L   // 5 seconds above threshold
        private const val STOP_DELAY_MS = 180_000L  // 3 minutes at zero
        private const val POINT_BATCH_SIZE = 30
        private const val MIN_POINT_INTERVAL_MS = 1_000L
        private const val STATIONARY_FILTER_AFTER_MS = 30_000L
        private const val MIN_STATIONARY_DISTANCE_M = 20f
        private const val NO_GPS_FILE_LOG_INTERVAL_MS = 30_000L
    }

    suspend fun onData(data: DiParsData, location: Location?) {
        if (!recordGpsEnabled()) {
            stopRecordingForDisabledSetting()
            return
        }

        // DiLink 4 has no readable autoservice speed fid (returns a sentinel →
        // data.speed == null), which would freeze GPS collection in IDLE. Fall
        // back to the GPS fix's own speed (m/s → km/h) so point collection is
        // gated on GPS, not on a device-specific fid. On Leopard 3 data.speed
        // is valid and takes precedence, so behaviour there is unchanged.
        val speed = data.speed
            ?: location?.takeIf { it.hasSpeed() }?.let { (it.speed * 3.6f).toInt() }
            ?: 0
        val now = clock()

        when (_state.value) {
            TripState.IDLE -> {
                if (speed > SPEED_THRESHOLD) {
                    if (speedAboveThresholdSince == null) {
                        speedAboveThresholdSince = now
                    } else if (now - speedAboveThresholdSince!! >= START_DELAY_MS) {
                        _state.value = TripState.DRIVING
                        _tripStartedAt.value = now
                        speedZeroSince = null
                        speedAboveThresholdSince = null
                        Log.d(TAG, "Driving started (GPS collection)")
                        WjxFileLogger.i(TAG, "Driving started; GPS collection begins")
                        // Record first point
                        recordPoint(location, speed, now)
                    }
                } else {
                    speedAboveThresholdSince = null
                }
            }
            TripState.DRIVING -> {
                // Record GPS point
                recordPoint(location, speed, now)

                if (pendingPoints.size >= POINT_BATCH_SIZE) {
                    flushPoints()
                }

                // Check stop condition
                if (speed == 0) {
                    if (speedZeroSince == null) {
                        speedZeroSince = now
                    } else if (now - speedZeroSince!! >= STOP_DELAY_MS) {
                        flushPoints()
                        _state.value = TripState.IDLE
                        _tripStartedAt.value = null
                        speedZeroSince = null
                        speedAboveThresholdSince = null
                        resetPointFilter()
                        Log.d(TAG, "Driving stopped (GPS collection ended)")
                        WjxFileLogger.i(TAG, "Driving stopped; GPS collection ended")
                    }
                } else {
                    speedZeroSince = null
                }
            }
        }
    }

    private suspend fun stopRecordingForDisabledSetting() {
        flushPoints()
        if (_state.value != TripState.IDLE || _tripStartedAt.value != null) {
            Log.i(TAG, "GPS recording disabled: stopping active GPS collection")
            WjxFileLogger.i(TAG, "GPS recording disabled; stopping active GPS collection")
        }
        _state.value = TripState.IDLE
        _tripStartedAt.value = null
        speedZeroSince = null
        speedAboveThresholdSince = null
        resetPointFilter()
    }

    private fun recordPoint(location: Location?, speed: Int, now: Long) {
        if (location == null) {
            Log.w(TAG, "No GPS fix available (location=null), speed=${speed}km/h")
            if (_state.value == TripState.DRIVING && now - lastNoGpsFileLogAt >= NO_GPS_FILE_LOG_INTERVAL_MS) {
                lastNoGpsFileLogAt = now
                WjxFileLogger.w(TAG, "No GPS fix while driving speed=${speed}km/h")
            }
            return
        }

        if (now - lastRecordedAt < MIN_POINT_INTERVAL_MS) return
        lastRecordedLocation?.let { previous ->
            if (previous.time > 0L && previous.time == location.time) return
        }

        val movedMeters = lastRecordedLocation?.let { previous ->
            FloatArray(1).also { result ->
                Location.distanceBetween(
                    previous.latitude,
                    previous.longitude,
                    location.latitude,
                    location.longitude,
                    result
                )
            }[0]
        }

        if (speed <= SPEED_THRESHOLD) {
            if (stationarySince == null) stationarySince = now
            if (!stationaryLocked && now - (stationarySince ?: now) >= STATIONARY_FILTER_AFTER_MS) {
                stationaryLocked = true
            }
            if (movedMeters != null && movedMeters < MIN_STATIONARY_DISTANCE_M) return
        } else {
            stationarySince = null
            stationaryLocked = false
        }

        pendingPoints.add(
            TripPointEntity(
                tripId = 0, // unattached, will be matched later
                timestamp = now,
                lat = location.latitude,
                lon = location.longitude,
                speedKmh = speed.toDouble()
            )
        )
        lastRecordedLocation = Location(location)
        lastRecordedAt = now

        if (pendingPoints.size % 10 == 1) {
            Log.d(TAG, "GPS point #${pendingPoints.size}: lat=${"%.5f".format(location.latitude)} " +
                "lon=${"%.5f".format(location.longitude)} speed=${speed}km/h")
        }
    }

    private fun resetPointFilter() {
        lastRecordedLocation = null
        lastRecordedAt = 0L
        stationarySince = null
        stationaryLocked = false
    }

    /**
     * Force-end on service shutdown. Just flush remaining GPS points.
     */
    suspend fun forceEnd(lastData: DiParsData?, lastLocation: Location?) {
        if (_state.value != TripState.DRIVING) return
        Log.w(TAG, "forceEnd: flushing GPS points on shutdown")
        WjxFileLogger.w(TAG, "forceEnd: flushing GPS points on shutdown")
        flushPoints()
        _state.value = TripState.IDLE
        _tripStartedAt.value = null
        speedZeroSince = null
        speedAboveThresholdSince = null
        resetPointFilter()
    }

    private suspend fun flushPoints() {
        val snapshot: List<TripPointEntity>
        synchronized(pendingPoints) {
            if (pendingPoints.isEmpty()) return
            snapshot = ArrayList(pendingPoints)
            pendingPoints.clear()
        }
        try {
            tripPointDao.insertAll(snapshot)
            Log.i(TAG, "flushPoints: saved ${snapshot.size} GPS points to DB")
            WjxFileLogger.i(TAG, "flushPoints: saved ${snapshot.size} GPS points to DB")
        } catch (e: Exception) {
            Log.e(TAG, "flushPoints FAILED: ${e.message}", e)
            WjxFileLogger.e(TAG, "flushPoints FAILED: ${e.message}", e)
        }
    }
}
