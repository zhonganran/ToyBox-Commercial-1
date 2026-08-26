package com.byd.wjx.led

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal enum class LedSignalTier {
    MOTION,
    BRAKE,
    BODY,
    ADAS,
}

internal class LedVehicleSignalAccumulator {
    @Volatile private var coreSignals = LedVehicleSignals()
    @Volatile private var motionSample = TimedLedSignals()
    @Volatile private var brakeSample = TimedLedSignals()
    @Volatile private var bodySample = TimedLedSignals()
    @Volatile private var adasSample = TimedLedSignals()
    private var lastLogKey: LedSignalLogKey? = null

    fun updateCore(data: DiParsData) {
        coreSignals = LedVehicleSignals(
            speed = data.speed,
            gear = data.gear,
            powerState = data.powerState,
        )
    }

    fun update(tier: LedSignalTier, data: DiParsData, sampledAt: Long) {
        val sample = TimedLedSignals(data.toLedSignals(), sampledAt)
        when (tier) {
            LedSignalTier.MOTION -> motionSample = sample
            LedSignalTier.BRAKE -> brakeSample = sample
            LedSignalTier.BODY -> bodySample = sample
            LedSignalTier.ADAS -> adasSample = sample
        }
    }

    fun snapshot(now: Long): LedVehicleSignals = coreSignals
        .mergeMotion(freshSignals(now, motionSample, MOTION_FRESH_MS))
        .mergeBrake(freshSignals(now, brakeSample, BRAKE_FRESH_MS))
        .mergeBody(freshSignals(now, bodySample, BODY_FRESH_MS))
        .mergeAdas(freshSignals(now, adasSample, ADAS_FRESH_MS))

    fun reset() {
        coreSignals = LedVehicleSignals()
        motionSample = TimedLedSignals()
        brakeSample = TimedLedSignals()
        bodySample = TimedLedSignals()
        adasSample = TimedLedSignals()
        lastLogKey = null
    }

    fun logChanges(tag: String, signals: LedVehicleSignals) {
        val key = LedSignalLogKey.from(signals)
        if (key == lastLogKey) return
        lastLogKey = key
        WjxFileLogger.i(
            tag,
            "signals turn=${signals.turnSignalFlash} gear=${signals.gear} speed=${signals.speed} " +
                "brake=${signals.brakePedalDepth} accel=${signals.accelPedalDepth} " +
                "brakeLight=${signals.lightBrake} reverseLight=${signals.lightReversing} " +
                "doors=${signals.doorFL}/${signals.doorFR}/${signals.doorRL}/${signals.doorRR} " +
                "trunk=${signals.trunk} apa=${signals.adasApaParkingStatus} " +
                "acc=${signals.adasAccMode} accCruise=${signals.accCruiseState} " +
                "lccNca=${signals.adasLccNcaStatus} mnoa=${signals.adasMnoaStatus}",
        )
    }

    private fun DiParsData.toLedSignals(): LedVehicleSignals = LedVehicleSignals(
        speed = speed,
        turnSignalFlash = turnSignalFlash,
        gear = gear,
        powerState = powerState,
        doorFL = doorFL,
        doorFR = doorFR,
        doorRL = doorRL,
        doorRR = doorRR,
        trunk = trunk,
        adasApaParkingStatus = adasApaParkingStatus,
        adasAccMode = adasAccMode,
        adasLccNcaStatus = adasLccNcaStatus,
        accCruiseState = accCruiseState,
        adasMnoaStatus = adasMnoaStatus,
        brakePedalDepth = brakePedalDepth,
        lightBrake = lightBrake,
        lightReversing = lightReversing,
        accelPedalDepth = accelPedalDepth,
    )

    private fun LedVehicleSignals.mergeMotion(motion: LedVehicleSignals): LedVehicleSignals = copy(
        turnSignalFlash = motion.turnSignalFlash ?: turnSignalFlash,
        gear = motion.gear ?: gear,
    )

    private fun LedVehicleSignals.mergeBrake(brake: LedVehicleSignals): LedVehicleSignals = copy(
        brakePedalDepth = brake.brakePedalDepth ?: brakePedalDepth,
        lightBrake = brake.lightBrake ?: lightBrake,
        lightReversing = brake.lightReversing ?: lightReversing,
        accelPedalDepth = brake.accelPedalDepth ?: accelPedalDepth,
    )

    private fun LedVehicleSignals.mergeBody(body: LedVehicleSignals): LedVehicleSignals = copy(
        doorFL = body.doorFL ?: doorFL,
        doorFR = body.doorFR ?: doorFR,
        doorRL = body.doorRL ?: doorRL,
        doorRR = body.doorRR ?: doorRR,
        trunk = body.trunk ?: trunk,
        adasApaParkingStatus = body.adasApaParkingStatus ?: adasApaParkingStatus,
    )

    private fun LedVehicleSignals.mergeAdas(adas: LedVehicleSignals): LedVehicleSignals = copy(
        adasAccMode = adas.adasAccMode ?: adasAccMode,
        adasLccNcaStatus = adas.adasLccNcaStatus ?: adasLccNcaStatus,
        accCruiseState = adas.accCruiseState ?: accCruiseState,
        adasMnoaStatus = adas.adasMnoaStatus ?: adasMnoaStatus,
    )

    private fun freshSignals(
        now: Long,
        sample: TimedLedSignals,
        maxAgeMs: Long,
    ): LedVehicleSignals =
        if (sample.sampledAt > 0L && now >= sample.sampledAt && now - sample.sampledAt <= maxAgeMs) {
            sample.signals
        } else {
            LedVehicleSignals()
        }

    private companion object {
        const val MOTION_FRESH_MS = 350L
        const val BRAKE_FRESH_MS = 700L
        const val BODY_FRESH_MS = 1_500L
        const val ADAS_FRESH_MS = 2_500L
    }
}

internal suspend fun runLedSignalLoop(
    tag: String,
    deviceLabel: String,
    signalLabel: String,
    tier: LedSignalTier,
    intervalMs: Long,
    shouldPoll: () -> Boolean,
    fetch: suspend () -> VehicleSnapshot?,
    accumulator: LedVehicleSignalAccumulator,
    onSample: suspend (Long) -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        if (!shouldPoll()) {
            delay(INACTIVE_LOOP_DELAY_MS)
            continue
        }
        val snapshot = try {
            fetch()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            WjxFileLogger.w(tag, "$signalLabel $deviceLabel signal read failed: ${error.message}", error)
            null
        }
        if (snapshot != null) {
            val readAt = System.currentTimeMillis()
            accumulator.update(tier, snapshot.data, readAt)
            onSample(readAt)
        }
        val spent = snapshot?.latencyMs ?: 0L
        delay((intervalMs - spent).coerceAtLeast(MIN_LOOP_DELAY_MS))
    }
}

private data class TimedLedSignals(
    val signals: LedVehicleSignals = LedVehicleSignals(),
    val sampledAt: Long = 0L,
)

private data class LedSignalLogKey(
    val turn: Int?,
    val gear: Int?,
    val moving: Boolean,
    val brakeBucket: Int?,
    val accelBucket: Int?,
    val brakeLight: Int?,
    val reverseLight: Int?,
    val doors: List<Int?>,
    val trunk: Int?,
    val apa: Int?,
    val acc: Int?,
    val accCruise: Int?,
    val lccNca: Int?,
    val mnoa: Int?,
) {
    companion object {
        fun from(signals: LedVehicleSignals): LedSignalLogKey = LedSignalLogKey(
            turn = signals.turnSignalFlash,
            gear = signals.gear,
            moving = (signals.speed ?: 0) > 0,
            brakeBucket = signals.brakePedalDepth?.let { (it / 5) * 5 },
            accelBucket = signals.accelPedalDepth?.let { (it / 5) * 5 },
            brakeLight = signals.lightBrake,
            reverseLight = signals.lightReversing,
            doors = listOf(signals.doorFL, signals.doorFR, signals.doorRL, signals.doorRR),
            trunk = signals.trunk,
            apa = signals.adasApaParkingStatus,
            acc = signals.adasAccMode,
            accCruise = signals.accCruiseState,
            lccNca = signals.adasLccNcaStatus,
            mnoa = signals.adasMnoaStatus,
        )
    }
}

private const val MIN_LOOP_DELAY_MS = 20L
private const val INACTIVE_LOOP_DELAY_MS = 500L
