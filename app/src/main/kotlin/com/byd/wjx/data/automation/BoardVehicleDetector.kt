package com.byd.wjx.data.automation

import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.data.remote.DiParsData

/**
 * Detects a real boarding door-open edge without confusing the normal door-open used to exit
 * after a drive. Once driving or a fastened driver belt has been observed, the detector only
 * rearms after that exit door has opened and closed again while the vehicle is not driving.
 */
internal class BoardVehicleDetector {
    private var initialized = false
    private var previousDoorOpen = false
    private var armed = false
    private var droveSinceLastBoard = false
    private var exitDoorOpened = false

    fun onSample(data: DiParsData): Boolean {
        val doorOpen = data.doorFL == 1
        val driving = BydSignalSemantics.isDriveReady(data.powerState) ||
            data.gear == 4 || (data.speed ?: 0) > 0 || data.seatbeltFL == 1

        if (!initialized) {
            initialized = true
            previousDoorOpen = doorOpen
            armed = !driving && !doorOpen
            return !driving && doorOpen
        }

        if (driving) {
            droveSinceLastBoard = true
            armed = false
            exitDoorOpened = false
        } else if (droveSinceLastBoard) {
            if (doorOpen) exitDoorOpened = true
            if (exitDoorOpened && previousDoorOpen && !doorOpen) {
                droveSinceLastBoard = false
                exitDoorOpened = false
                armed = true
            }
        }

        val boarded = armed && !previousDoorOpen && doorOpen
        if (boarded) armed = false
        previousDoorOpen = doorOpen
        return boarded
    }
}
