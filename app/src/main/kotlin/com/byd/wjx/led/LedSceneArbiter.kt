package com.byd.wjx.led

import com.byd.wjx.data.nativestack.BydSignalSemantics

/**
 * Converts noisy vehicle samples into one stable LED scene.
 *
 * The stable turn-switch enum keeps left/right scenes active until the stalk is cancelled.
 * Persistent higher-priority scenes switch directly without flashing DEFAULT between them.
 */
class LedSceneArbiter(
    private val brakeWarningOnPercent: Int = 12,
    private val brakeWarningOffPercent: Int = 5,
    private val brakeWarningReleaseHoldMs: Long = 700L,
) {
    private var temporaryStopActive = false
    private var exitVehicleActive = false
    private var previousGear: Int? = null
    private var previousPowerState: Int? = null
    private var previousDoorFL: Int? = null
    private var previousDoorFR: Int? = null
    private var previousDoorRL: Int? = null
    private var previousDoorRR: Int? = null
    private var brakeWarningActive = false
    private var brakeWarningUntil = Long.MIN_VALUE

    fun evaluate(signals: LedVehicleSignals, now: Long): LedScene {
        // Stable switch enum: 1=off, 2=left, 4=right, 6=hazard. It remains active through
        // the physical lamp's blink-off phase and returns to off immediately when cancelled.
        val stableTurnState = signals.turnSignalFlash
        val leftOn = stableTurnState == TURN_LEFT
        val rightOn = stableTurnState == TURN_RIGHT
        val hazardOn = stableTurnState == TURN_HAZARD

        val enteredPark = previousGear != null && previousGear != 1 && signals.gear == 1
        if (enteredPark && BydSignalSemantics.isPowered(signals.powerState)) {
            temporaryStopActive = true
        }
        val leftPark = signals.gear != null && signals.gear != PARK_GEAR
        val explicitlyPoweredOff = signals.powerState != null &&
            !BydSignalSemantics.isPowered(signals.powerState)
        if (leftPark || explicitlyPoweredOff) temporaryStopActive = false

        val anyDoorOpened = openedEdge(previousDoorFL, signals.doorFL) ||
            openedEdge(previousDoorFR, signals.doorFR) ||
            openedEdge(previousDoorRL, signals.doorRL) ||
            openedEdge(previousDoorRR, signals.doorRR)
        val poweredDown = BydSignalSemantics.isPowered(previousPowerState) &&
            signals.powerState == BydSignalSemantics.POWER_OFF
        val poweredUp = previousPowerState != null &&
            !BydSignalSemantics.isPowered(previousPowerState) &&
            BydSignalSemantics.isPowered(signals.powerState)
        if (leftPark || poweredUp) exitVehicleActive = false
        if ((anyDoorOpened && signals.gear == PARK_GEAR) || poweredDown) exitVehicleActive = true

        updateBrakeWarningState(signals, now)

        previousGear = signals.gear ?: previousGear
        previousPowerState = signals.powerState ?: previousPowerState
        previousDoorFL = signals.doorFL ?: previousDoorFL
        previousDoorFR = signals.doorFR ?: previousDoorFR
        previousDoorRL = signals.doorRL ?: previousDoorRL
        previousDoorRR = signals.doorRR ?: previousDoorRR

        val candidates = buildList {
            if (hazardOn) add(LedScene.HAZARD)
            val autoParking = signals.adasApaParkingStatus == AUTO_PARKING_ACTIVE
            val manualReversing = !autoParking && signals.gear == REVERSE_GEAR
            if (autoParking || manualReversing) add(LedScene.REVERSE_PARKING)
            if (leftOn) add(LedScene.LEFT_TURN)
            if (rightOn) add(LedScene.RIGHT_TURN)
            if (isAutopilotActive(signals)) add(LedScene.AUTOPILOT)
            if (isBrakeWarningActive(signals, now)) add(LedScene.BRAKE_WARNING)
            if (exitVehicleActive) add(LedScene.EXIT_VEHICLE)
            if (temporaryStopActive) add(LedScene.TEMPORARY_STOP)
        }
        return candidates.maxByOrNull { it.priority } ?: LedScene.DEFAULT
    }

    fun reset() {
        temporaryStopActive = false
        exitVehicleActive = false
        previousGear = null
        previousPowerState = null
        previousDoorFL = null
        previousDoorFR = null
        previousDoorRL = null
        previousDoorRR = null
        brakeWarningActive = false
        brakeWarningUntil = Long.MIN_VALUE
    }

    private fun isAutopilotActive(signals: LedVehicleSignals): Boolean =
        isAdasAccModeActive(signals.adasAccMode) ||
            isLccNcaActive(signals.adasLccNcaStatus) ||
            isTjaActive(signals.adasTjaState) ||
            isLaneKeepActive(signals.laneKeepState) ||
            signals.accCruiseState == ACC_CRUISE_ACTIVE ||
            isMnoaActive(signals.adasMnoaStatus)

    private fun updateBrakeWarningState(signals: LedVehicleSignals, now: Long) {
        if ((signals.speed ?: 0) <= 0) {
            brakeWarningActive = false
            brakeWarningUntil = Long.MIN_VALUE
            return
        }

        signals.lightBrake?.let { light ->
            if (light == 1) {
                brakeWarningActive = true
                brakeWarningUntil = Long.MIN_VALUE
            } else if (brakeWarningActive) {
                brakeWarningActive = false
                brakeWarningUntil = now + brakeWarningReleaseHoldMs
            }
            return
        }

        when {
            (signals.brakePedalDepth ?: -1) >= brakeWarningOnPercent -> {
                brakeWarningActive = true
                brakeWarningUntil = Long.MIN_VALUE
            }
            brakeWarningActive && (signals.brakePedalDepth ?: -1) > brakeWarningOffPercent -> Unit
            brakeWarningActive -> {
                brakeWarningActive = false
                brakeWarningUntil = now + brakeWarningReleaseHoldMs
            }
        }
    }

    private fun isBrakeWarningActive(signals: LedVehicleSignals, now: Long): Boolean =
        (signals.speed ?: 0) > 0 && (brakeWarningActive || now < brakeWarningUntil)

    // BYD TJA/ICA: 1 is passive, 2/3/5 are active, and 4 is a fault state.
    private fun isTjaActive(value: Int?): Boolean = value == 2 || value == 3 || value == 5

    // ADAS_ACC_MODE: 0/1/2 standby, 3/4/6/7 active, 5 driver override.
    private fun isAdasAccModeActive(value: Int?): Boolean =
        value == 3 || value == 4 || value == 6 || value == 7

    // Shared ADAS display enum: 0 off, 1 standby, 2 active, 3 override, 4 fault.
    private fun isLccNcaActive(value: Int?): Boolean = value == ADAS_DISPLAY_ACTIVE

    private fun isLaneKeepActive(value: Int?): Boolean = value == 2 || value == 3

    private fun isMnoaActive(value: Int?): Boolean = value == ADAS_DISPLAY_ACTIVE

    private fun openedEdge(previous: Int?, current: Int?): Boolean =
        previous != null && previous != DOOR_OPEN && current == DOOR_OPEN

    private companion object {
        const val TURN_LEFT = 2
        const val TURN_RIGHT = 4
        const val TURN_HAZARD = 6
        const val PARK_GEAR = 1
        const val REVERSE_GEAR = 2
        const val DOOR_OPEN = 1
        const val ACC_CRUISE_ACTIVE = 2
        const val ADAS_DISPLAY_ACTIVE = 2
        const val AUTO_PARKING_ACTIVE = 4
    }
}
