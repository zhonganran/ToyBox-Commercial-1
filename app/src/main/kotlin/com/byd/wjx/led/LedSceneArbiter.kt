package com.byd.wjx.led

import com.byd.wjx.data.nativestack.BydSignalSemantics

/**
 * Converts noisy vehicle samples into one stable LED scene.
 *
 * Turn signals blink at the vehicle level, so their scene is held briefly after the last ON
 * sample. Persistent higher-priority scenes switch directly without flashing DEFAULT between them.
 */
class LedSceneArbiter(
    private val turnHoldMs: Long = 1_200L,
    private val oneShotDurationMs: Long = 4_000L,
    private val keepDistanceBrakeOnPercent: Int = 12,
    private val keepDistanceBrakeOffPercent: Int = 5,
    private val keepDistanceReleaseHoldMs: Long = 700L,
) {
    private var lastLeftAt = Long.MIN_VALUE
    private var lastRightAt = Long.MIN_VALUE
    private var lastHazardAt = Long.MIN_VALUE
    private var temporaryStopUntil = Long.MIN_VALUE
    private var exitVehicleUntil = Long.MIN_VALUE
    private var previousGear: Int? = null
    private var previousPowerState: Int? = null
    private var previousDriverDoor: Int? = null
    private var keepDistanceBraking = false
    private var keepDistanceUntil = Long.MIN_VALUE

    fun evaluate(signals: LedVehicleSignals, now: Long): LedScene {
        val leftOn = signals.turnSignalLeft == 1
        val rightOn = signals.turnSignalRight == 1
        val hazardOn = signals.hazardLights == 1 || (leftOn && rightOn)
        if (hazardOn) {
            lastHazardAt = now
        } else if (leftOn) {
            lastLeftAt = now
            lastRightAt = Long.MIN_VALUE
        } else if (rightOn) {
            lastRightAt = now
            lastLeftAt = Long.MIN_VALUE
        }

        val enteredPark = previousGear != null && previousGear != 1 && signals.gear == 1
        if (enteredPark && BydSignalSemantics.isPowered(signals.powerState)) {
            temporaryStopUntil = now + oneShotDurationMs
        }

        val driverDoorOpened = previousDriverDoor != null && previousDriverDoor != 1 && signals.driverDoor == 1
        val poweredDown = BydSignalSemantics.isPowered(previousPowerState) &&
            signals.powerState == BydSignalSemantics.POWER_OFF
        if ((driverDoorOpened && signals.gear == 1) || poweredDown) {
            exitVehicleUntil = now + oneShotDurationMs
        }

        updateKeepDistanceState(signals, now)

        previousGear = signals.gear ?: previousGear
        previousPowerState = signals.powerState ?: previousPowerState
        previousDriverDoor = signals.driverDoor ?: previousDriverDoor

        val candidates = buildList {
            if (isRecent(lastHazardAt, now, turnHoldMs)) add(LedScene.HAZARD)
            val reversing = signals.lightReversing?.let { it == 1 } ?: (signals.gear == 2)
            if (reversing) add(LedScene.REVERSE_PARKING)
            if (isRecent(lastLeftAt, now, turnHoldMs)) add(LedScene.LEFT_TURN)
            if (isRecent(lastRightAt, now, turnHoldMs)) add(LedScene.RIGHT_TURN)
            if (isAutopilotActive(signals)) add(LedScene.AUTOPILOT)
            if (isKeepDistanceActive(signals, now)) add(LedScene.KEEP_DISTANCE)
            if (now < exitVehicleUntil) add(LedScene.EXIT_VEHICLE)
            if (now < temporaryStopUntil) add(LedScene.TEMPORARY_STOP)
        }
        return candidates.maxByOrNull { it.priority } ?: LedScene.DEFAULT
    }

    fun reset() {
        lastLeftAt = Long.MIN_VALUE
        lastRightAt = Long.MIN_VALUE
        lastHazardAt = Long.MIN_VALUE
        temporaryStopUntil = Long.MIN_VALUE
        exitVehicleUntil = Long.MIN_VALUE
        previousGear = null
        previousPowerState = null
        previousDriverDoor = null
        keepDistanceBraking = false
        keepDistanceUntil = Long.MIN_VALUE
    }

    private fun isAutopilotActive(signals: LedVehicleSignals): Boolean =
        isLccNcaActive(signals.adasLccNcaStatus) ||
            isTjaActive(signals.adasTjaState)

    private fun updateKeepDistanceState(signals: LedVehicleSignals, now: Long) {
        if ((signals.speed ?: 0) <= 0) {
            keepDistanceBraking = false
            keepDistanceUntil = Long.MIN_VALUE
            return
        }

        signals.lightBrake?.let { light ->
            if (light == 1) {
                keepDistanceBraking = true
                keepDistanceUntil = Long.MIN_VALUE
            } else if (keepDistanceBraking) {
                keepDistanceBraking = false
                keepDistanceUntil = now + keepDistanceReleaseHoldMs
            }
            return
        }

        when {
            (signals.brakePedalDepth ?: -1) >= keepDistanceBrakeOnPercent -> {
                keepDistanceBraking = true
                keepDistanceUntil = Long.MIN_VALUE
            }
            keepDistanceBraking && (signals.brakePedalDepth ?: -1) > keepDistanceBrakeOffPercent -> Unit
            keepDistanceBraking -> {
                keepDistanceBraking = false
                keepDistanceUntil = now + keepDistanceReleaseHoldMs
            }
        }
    }

    private fun isKeepDistanceActive(signals: LedVehicleSignals, now: Long): Boolean =
        (signals.speed ?: 0) > 0 && (keepDistanceBraking || now < keepDistanceUntil)

    // BYD TJA/ICA: 1 is passive, 2/3/5 are active, and 4 is a fault state.
    private fun isTjaActive(value: Int?): Boolean = value == 2 || value == 3 || value == 5

    // The combined LCC/NCA status uses 1 as standby on the supported CAN-FD platforms.
    // Restrict activation to working states so a configured-but-idle system cannot seize the LED.
    private fun isLccNcaActive(value: Int?): Boolean = value == 2 || value == 3

    private fun isRecent(timestamp: Long, now: Long, windowMs: Long): Boolean =
        timestamp != Long.MIN_VALUE && now >= timestamp && now - timestamp <= windowMs
}
