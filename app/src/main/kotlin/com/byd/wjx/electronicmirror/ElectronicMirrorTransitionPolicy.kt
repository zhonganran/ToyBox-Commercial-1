package com.byd.wjx.electronicmirror

/** Minimal camera operation needed when the resolved electronic-mirror scene changes. */
enum class ElectronicMirrorTransition {
    NONE,
    UPDATE_TRANSFORM,
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

        val sameWindow = current.target == desired.target &&
            current.geometry == desired.geometry &&
            current.fps == desired.fps
        if (!sameWindow) return ElectronicMirrorTransition.REBUILD_WINDOW

        return if (current.source == desired.source) {
            ElectronicMirrorTransition.UPDATE_TRANSFORM
        } else {
            ElectronicMirrorTransition.SWITCH_SOURCE
        }
    }
}
