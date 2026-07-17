package com.byd.wjx.electronicmirror

enum class ElectronicMirrorDisplayTarget {
    CLUSTER,
    CENTER,
}

enum class ElectronicMirrorViewpoint(val helperValue: Int, val displayName: String) {
    REAR(2000, "后方"),
    LEFT(2002, "左后侧"),
    RIGHT(2003, "右后侧"),
}

enum class ElectronicMirrorReason {
    MANUAL_PREVIEW,
    REVERSE,
    LEFT_TURN,
    RIGHT_TURN,
}

data class ElectronicMirrorGeometry(
    val widthPct: Int,
    val heightPct: Int,
    val offsetXPct: Int,
    val offsetYPct: Int,
) {
    fun sanitized(): ElectronicMirrorGeometry = copy(
        widthPct = widthPct.coerceIn(MIN_SIZE_PCT, MAX_SIZE_PCT),
        heightPct = heightPct.coerceIn(MIN_SIZE_PCT, MAX_SIZE_PCT),
        offsetXPct = offsetXPct.coerceIn(MIN_OFFSET_PCT, MAX_OFFSET_PCT),
        offsetYPct = offsetYPct.coerceIn(MIN_OFFSET_PCT, MAX_OFFSET_PCT),
    )

    companion object {
        const val MIN_SIZE_PCT = 24
        const val MAX_SIZE_PCT = 100
        const val MIN_OFFSET_PCT = 0
        const val MAX_OFFSET_PCT = 100

        val DEFAULT_CLUSTER = ElectronicMirrorGeometry(
            widthPct = 66,
            heightPct = 92,
            offsetXPct = 50,
            offsetYPct = 50,
        )
        val DEFAULT_CENTER = ElectronicMirrorGeometry(
            widthPct = 44,
            heightPct = 56,
            offsetXPct = 96,
            offsetYPct = 50,
        )
    }
}

data class ElectronicMirrorConfig(
    val enabled: Boolean = false,
    val displayTarget: ElectronicMirrorDisplayTarget = ElectronicMirrorDisplayTarget.CLUSTER,
    val leftTurnEnabled: Boolean = true,
    val rightTurnEnabled: Boolean = true,
    val reverseEnabled: Boolean = false,
    val fps: Int = DEFAULT_FPS,
    val clusterGeometry: ElectronicMirrorGeometry = ElectronicMirrorGeometry.DEFAULT_CLUSTER,
    val centerGeometry: ElectronicMirrorGeometry = ElectronicMirrorGeometry.DEFAULT_CENTER,
) {
    fun geometryFor(target: ElectronicMirrorDisplayTarget = displayTarget): ElectronicMirrorGeometry =
        when (target) {
            ElectronicMirrorDisplayTarget.CLUSTER -> clusterGeometry
            ElectronicMirrorDisplayTarget.CENTER -> centerGeometry
        }

    fun sanitized(): ElectronicMirrorConfig = copy(
        fps = fps.coerceIn(MIN_FPS, MAX_FPS),
        clusterGeometry = clusterGeometry.sanitized(),
        centerGeometry = centerGeometry.sanitized(),
    )

    companion object {
        const val DEFAULT_FPS = 30
        const val MIN_FPS = 15
        const val MAX_FPS = 30
    }
}

data class ElectronicMirrorSignals(
    val turnSignalFlash: Int?,
    val gear: Int?,
    val originalCameraActive: Boolean,
    val autoParkingActive: Boolean = false,
)

data class ElectronicMirrorScene(
    val viewpoint: ElectronicMirrorViewpoint,
    val target: ElectronicMirrorDisplayTarget,
    val geometry: ElectronicMirrorGeometry,
    val reason: ElectronicMirrorReason,
    val fps: Int,
)

data class ElectronicMirrorRuntimeStatus(
    val active: Boolean = false,
    val message: String = "待机",
    val viewpoint: ElectronicMirrorViewpoint? = null,
    val target: ElectronicMirrorDisplayTarget? = null,
)

/** Lightweight cross-cutting guard used by legacy object-based cluster projection controls. */
object ElectronicMirrorActivity {
    @Volatile
    var isActive: Boolean = false
        internal set
}
