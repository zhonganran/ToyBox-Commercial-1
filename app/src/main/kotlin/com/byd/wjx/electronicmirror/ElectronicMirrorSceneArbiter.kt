package com.byd.wjx.electronicmirror

/**
 * Pure resolver for the electronic-mirror pictures.
 *
 * Original vehicle camera/auto-parking is always allowed to own the screen first. Within this
 * feature, reverse is above the turn-signal views, matching the vehicle's safety hierarchy. Side
 * views and the streaming rear mirror share the same panorama producer and may coexist.
 */
object ElectronicMirrorSceneArbiter {
    const val GEAR_REVERSE = 2
    const val TURN_LEFT = 2
    const val TURN_RIGHT = 4

    fun resolve(
        config: ElectronicMirrorConfig,
        signals: ElectronicMirrorSignals,
        manualPreview: ElectronicMirrorViewpoint? = null,
    ): ElectronicMirrorScene? = resolveAll(config, signals, manualPreview).firstOrNull()

    fun resolveAll(
        config: ElectronicMirrorConfig,
        signals: ElectronicMirrorSignals,
        manualPreview: ElectronicMirrorViewpoint? = null,
    ): List<ElectronicMirrorScene> {
        if (signals.originalCameraActive || signals.autoParkingActive) return emptyList()

        if (manualPreview != null) {
            return listOf(sceneFor(config, manualPreview, ElectronicMirrorReason.MANUAL_PREVIEW))
        }
        if (config.enabled && config.reverseEnabled && signals.gear == GEAR_REVERSE) {
            return listOf(
                sceneFor(config, ElectronicMirrorViewpoint.REAR, ElectronicMirrorReason.REVERSE),
            )
        }

        val turnSignalRequestsSideView = config.enabled && when (signals.turnSignalFlash) {
            TURN_LEFT -> config.leftTurnEnabled
            TURN_RIGHT -> config.rightTurnEnabled
            TURN_HAZARD -> config.leftTurnEnabled || config.rightTurnEnabled
            else -> false
        }
        val streamingActive = config.streamingRearEnabled &&
            (config.streamingRearVisible || turnSignalRequestsSideView)
        val choices = buildList {
            when (signals.turnSignalFlash) {
                TURN_LEFT -> if (config.enabled && config.leftTurnEnabled) {
                    add(ElectronicMirrorViewpoint.LEFT to ElectronicMirrorReason.LEFT_TURN)
                }
                TURN_RIGHT -> if (config.enabled && config.rightTurnEnabled) {
                    add(ElectronicMirrorViewpoint.RIGHT to ElectronicMirrorReason.RIGHT_TURN)
                }
                TURN_HAZARD -> {
                    if (config.enabled && config.leftTurnEnabled) {
                        add(ElectronicMirrorViewpoint.LEFT to ElectronicMirrorReason.LEFT_TURN)
                    }
                    if (config.enabled && config.rightTurnEnabled) {
                        add(ElectronicMirrorViewpoint.RIGHT to ElectronicMirrorReason.RIGHT_TURN)
                    }
                }
            }
            if (streamingActive) {
                add(ElectronicMirrorViewpoint.REAR to ElectronicMirrorReason.STREAMING_REAR)
            }
        }
        return choices.map { (viewpoint, reason) -> sceneFor(config, viewpoint, reason) }
    }

    private fun sceneFor(
        config: ElectronicMirrorConfig,
        viewpoint: ElectronicMirrorViewpoint,
        reason: ElectronicMirrorReason,
    ): ElectronicMirrorScene {
        val isStreamingRear = reason == ElectronicMirrorReason.STREAMING_REAR
        val target = if (isStreamingRear) {
            config.streamingRearDisplayTarget
        } else {
            config.displayTarget
        }
        val source = config.sourceFor(viewpoint)
        // Surface 2/3/4 are independent fisheye side/rear feeds on the tested vehicle. They show
        // the door or a round fisheye image and cannot become a rectified mirror by tuning
        // crop values. Every view therefore stays on the same rectified four-tile pano_h strip.
        val surfaceProfile = ElectronicMirrorSurfaceProfile.panorama(
            surfaceIndex = config.panoramaSurfaceIndex,
            qualityWidth = config.qualityWidth,
        )
        val geometry = if (isStreamingRear) {
            config.streamingRearGeometryFor(target).sanitizedStreaming()
        } else {
            val base = config.geometryFor(target).sanitizedSide()
            val sideInset = base.offsetXPct
            when (viewpoint) {
                ElectronicMirrorViewpoint.LEFT -> base.copy(offsetXPct = sideInset)
                ElectronicMirrorViewpoint.RIGHT -> base.copy(offsetXPct = 100 - sideInset)
                ElectronicMirrorViewpoint.REAR -> base.copy(offsetXPct = 50)
            }
        }

        return ElectronicMirrorScene(
            viewpoint = viewpoint,
            source = source,
            target = target,
            geometry = geometry,
            reason = reason,
            fps = config.fps,
            projection = config.projectionFor(viewpoint),
            surfaceProfile = surfaceProfile,
            tileIndex = source.stripTileIndex,
            mirrorHorizontally = viewpoint == ElectronicMirrorViewpoint.REAR,
        )
    }

    private const val TURN_HAZARD = 6
}
