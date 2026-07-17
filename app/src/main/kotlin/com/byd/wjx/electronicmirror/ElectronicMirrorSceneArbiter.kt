package com.byd.wjx.electronicmirror

/**
 * Pure priority resolver for the electronic-mirror picture.
 *
 * Original vehicle camera/auto-parking is always allowed to own the screen first. Within this
 * feature, reverse is above the turn-signal views, matching the vehicle's safety hierarchy.
 */
object ElectronicMirrorSceneArbiter {
    const val GEAR_REVERSE = 2
    const val TURN_LEFT = 2
    const val TURN_RIGHT = 4

    fun resolve(
        config: ElectronicMirrorConfig,
        signals: ElectronicMirrorSignals,
        manualPreview: ElectronicMirrorViewpoint? = null,
    ): ElectronicMirrorScene? {
        if (signals.originalCameraActive || signals.autoParkingActive) return null

        val choice = when {
            manualPreview != null -> manualPreview to ElectronicMirrorReason.MANUAL_PREVIEW
            !config.enabled -> return null
            config.reverseEnabled && signals.gear == GEAR_REVERSE ->
                ElectronicMirrorViewpoint.REAR to ElectronicMirrorReason.REVERSE
            config.leftTurnEnabled && signals.turnSignalFlash == TURN_LEFT ->
                ElectronicMirrorViewpoint.LEFT to ElectronicMirrorReason.LEFT_TURN
            config.rightTurnEnabled && signals.turnSignalFlash == TURN_RIGHT ->
                ElectronicMirrorViewpoint.RIGHT to ElectronicMirrorReason.RIGHT_TURN
            else -> return null
        }

        return ElectronicMirrorScene(
            viewpoint = choice.first,
            target = config.displayTarget,
            geometry = config.geometryFor(),
            reason = choice.second,
            fps = config.fps,
        )
    }
}
