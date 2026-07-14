package com.byd.wjx.domain.tracker

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.service.SessionPersistence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class DriveSessionTransition {
    NONE,
    STARTED,
    ENDED,
}

data class DriveSessionInitialization(
    val sessionStartedAt: Long?,
    val restored: Boolean,
    val discardedStale: Boolean,
)

data class DriveSessionUpdate(
    val sessionStartedAt: Long?,
    val tripDistanceKm: Double?,
    val tripEnergyKwh: Double?,
    val lastTripAverage: Double?,
    val transition: DriveSessionTransition,
)

/** Owns the ignition-on driving session state machine and its persisted anchor. */
@Singleton
class DriveSessionCoordinator @Inject constructor(
    private val persistence: SessionPersistence,
    private val lastSessionRepository: LastSessionRepository,
    private val tripRepository: TripRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private var sessionStartedAt: Long? = null
    private var sessionLastActiveAt: Long = 0L
    private var startMileageKm: Double? = null
    private var startTotalEnergyKwh: Double? = null
    @Volatile private var cachedLastTripAverage: Double? = null

    @Synchronized
    fun initialize(nowMs: Long = System.currentTimeMillis()): DriveSessionInitialization {
        startMileageKm = null
        startTotalEnergyKwh = null
        val restored = persistence.load()
        val stale = restored?.isStale(nowMs, SESSION_IDLE_CLOSE_MS) == true
        if (stale) {
            val staleSession = requireNotNull(restored)
            val idleSeconds = (nowMs - staleSession.lastActiveTs) / 1_000L
            WjxFileLogger.i(
                TAG,
                "discarded stale session startedAt=${staleSession.sessionStartedAt} idleFor=${idleSeconds}s",
            )
            persistence.clear()
            sessionStartedAt = null
            sessionLastActiveAt = 0L
        } else if (restored != null) {
            sessionStartedAt = restored.sessionStartedAt
            sessionLastActiveAt = restored.lastActiveTs
            WjxFileLogger.i(
                TAG,
                "restored session startedAt=${restored.sessionStartedAt} lastActiveAt=${restored.lastActiveTs}",
            )
        } else {
            sessionStartedAt = null
            sessionLastActiveAt = 0L
        }
        refreshLastTripAverage("initialize")
        return DriveSessionInitialization(
            sessionStartedAt = sessionStartedAt,
            restored = restored != null && !stale,
            discardedStale = stale,
        )
    }

    @Synchronized
    fun onSample(
        nowMs: Long,
        data: DiParsData,
        trackerDriving: Boolean,
    ): DriveSessionUpdate {
        val powerOn = (data.powerState ?: 0) >= 1
        val active = powerOn || trackerDriving
        var transition = DriveSessionTransition.NONE

        if (active) {
            sessionLastActiveAt = nowMs
            if (sessionStartedAt == null) {
                sessionStartedAt = nowMs
                startMileageKm = data.mileage
                startTotalEnergyKwh = data.totalElecConsumption
                lastSessionRepository.onSessionStart(data.soc, nowMs)
                transition = DriveSessionTransition.STARTED
                WjxFileLogger.i(
                    TAG,
                    "session start at=$nowMs powerOn=$powerOn trackerDriving=$trackerDriving " +
                        "mileageStart=${data.mileage} energyStart=${data.totalElecConsumption}",
                )
            } else {
                if (startMileageKm == null && data.mileage != null) {
                    startMileageKm = data.mileage
                }
                if (startTotalEnergyKwh == null && data.totalElecConsumption != null) {
                    startTotalEnergyKwh = data.totalElecConsumption
                }
            }
        } else if (sessionStartedAt != null && nowMs - sessionLastActiveAt >= SESSION_IDLE_CLOSE_MS) {
            val idleSeconds = (nowMs - sessionLastActiveAt) / 1_000L
            lastSessionRepository.onSessionEnd(data.soc, nowMs)
            sessionStartedAt = null
            startMileageKm = null
            startTotalEnergyKwh = null
            persistence.clear()
            transition = DriveSessionTransition.ENDED
            WjxFileLogger.i(
                TAG,
                "session end idle=${idleSeconds}s powerOn=$powerOn trackerDriving=$trackerDriving",
            )
            refreshLastTripAverage("session_end")
        }

        val tripDistanceKm = startMileageKm?.let { start ->
            data.mileage?.let { current -> (current - start).takeIf { it >= 0.0 } }
        }
        val tripEnergyKwh = startTotalEnergyKwh?.let { start ->
            data.totalElecConsumption?.let { current -> (current - start).takeIf { it >= 0.0 } }
        }
        sessionStartedAt?.let { persistence.save(it, sessionLastActiveAt) }

        return DriveSessionUpdate(
            sessionStartedAt = sessionStartedAt,
            tripDistanceKm = tripDistanceKm,
            tripEnergyKwh = tripEnergyKwh,
            lastTripAverage = cachedLastTripAverage,
            transition = transition,
        )
    }

    private fun refreshLastTripAverage(reason: String) {
        applicationScope.launch {
            runCatching { tripRepository.getLastTripAvgConsumption() }
                .onSuccess {
                    cachedLastTripAverage = it
                    WjxFileLogger.d(TAG, "last trip average refreshed reason=$reason value=$it")
                }
                .onFailure {
                    WjxFileLogger.w(TAG, "last trip average refresh failed reason=$reason: ${it.message}", it)
                }
        }
    }

    companion object {
        const val SESSION_IDLE_CLOSE_MS = 10_000L
        private const val TAG = "DriveSessionCoordinator"
    }
}
