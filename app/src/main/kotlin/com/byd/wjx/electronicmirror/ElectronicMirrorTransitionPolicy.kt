package com.byd.wjx.electronicmirror

/** Minimal camera operation needed when the resolved electronic-mirror scene changes. */
enum class ElectronicMirrorTransition {
    NONE,
    UPDATE_PROJECTION,
    UPDATE_WINDOW,
    SWITCH_SOURCE,
    REBUILD_WINDOW,
}

object ElectronicMirrorTransitionPolicy {
    fun resolve(
        current: ElectronicMirrorScene?,
        desired: ElectronicMirrorScene,
    ): ElectronicMirrorTransition {
        if (current == null) return ElectronicMirrorTransition.REBUILD_WINDOW
        if (current == desired) return ElectronicMirrorTransition.NONE

        val sameSurfaceRoute = current.target == desired.target &&
            current.fps == desired.fps &&
            current.surfaceProfile.cameraTag == desired.surfaceProfile.cameraTag &&
            current.surfaceProfile.tileCount == desired.surfaceProfile.tileCount &&
            current.previewBufferWidth == desired.previewBufferWidth &&
            current.previewBufferHeight == desired.previewBufferHeight
        if (!sameSurfaceRoute) return ElectronicMirrorTransition.REBUILD_WINDOW

        if (current.helperSurfaceIndex != desired.helperSurfaceIndex) {
            return ElectronicMirrorTransition.SWITCH_SOURCE
        }

        if (current.geometry != desired.geometry) {
            return ElectronicMirrorTransition.UPDATE_WINDOW
        }

        return ElectronicMirrorTransition.UPDATE_PROJECTION
    }
}
