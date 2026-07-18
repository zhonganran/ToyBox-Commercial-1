package com.byd.wjx.electronicmirror

enum class ElectronicMirrorDisplayTarget {
    CLUSTER,
    CENTER,
}

enum class ElectronicMirrorViewpoint(val displayName: String) {
    REAR("后方"),
    LEFT("左后侧"),
    RIGHT("右后侧"),
}

/** The three validated APA camera inputs available to the electronic mirror. */
enum class ElectronicMirrorCameraSource(
    val helperValue: Int,
    val previewChannel: Int,
    val displayName: String,
) {
    SOURCE_A(2002, 2, "来源 A"),
    SOURCE_B(2003, 3, "来源 B"),
    SOURCE_C(2000, 4, "来源 C"),
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

data class ElectronicMirrorCalibrationPoint(
    val xPermille: Int,
    val yPermille: Int,
) {
    fun sanitized(): ElectronicMirrorCalibrationPoint = copy(
        xPermille = xPermille.coerceIn(0, ElectronicMirrorCalibration.COORDINATE_MAX),
        yPermille = yPermille.coerceIn(0, ElectronicMirrorCalibration.COORDINATE_MAX),
    )
}

/**
 * Source-area calibration for one panoramic-camera viewpoint.
 *
 * Points are stored in permille so the same calibration can be used on the cluster and centre
 * displays. Their order is top-left, top-right, bottom-right, bottom-left. The two points on one
 * side share their X coordinate, matching L1Mini's editor and preventing a dragged quadrilateral
 * from folding over itself.
 */
data class ElectronicMirrorCalibration(
    val topLeft: ElectronicMirrorCalibrationPoint = ElectronicMirrorCalibrationPoint(0, 0),
    val topRight: ElectronicMirrorCalibrationPoint = ElectronicMirrorCalibrationPoint(1000, 0),
    val bottomRight: ElectronicMirrorCalibrationPoint = ElectronicMirrorCalibrationPoint(1000, 1000),
    val bottomLeft: ElectronicMirrorCalibrationPoint = ElectronicMirrorCalibrationPoint(0, 1000),
    val yawDegrees: Int = 0,
    val rollDegrees: Int = 0,
    val pitchDegrees: Int = 0,
) {
    val points: List<ElectronicMirrorCalibrationPoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withPoint(index: Int, point: ElectronicMirrorCalibrationPoint): ElectronicMirrorCalibration {
        val safe = point.sanitized()
        return when (index) {
            0 -> copy(
                topLeft = safe,
                bottomLeft = bottomLeft.copy(xPermille = safe.xPermille),
            )
            1 -> copy(
                topRight = safe,
                bottomRight = bottomRight.copy(xPermille = safe.xPermille),
            )
            2 -> copy(
                bottomRight = safe,
                topRight = topRight.copy(xPermille = safe.xPermille),
            )
            3 -> copy(
                bottomLeft = safe,
                topLeft = topLeft.copy(xPermille = safe.xPermille),
            )
            else -> this
        }.sanitized()
    }

    fun sanitized(): ElectronicMirrorCalibration {
        val rawTopLeft = topLeft.sanitized()
        val rawTopRight = topRight.sanitized()
        val rawBottomRight = bottomRight.sanitized()
        val rawBottomLeft = bottomLeft.sanitized()

        val leftX = ((rawTopLeft.xPermille + rawBottomLeft.xPermille) / 2)
            .coerceIn(0, COORDINATE_MAX - MIN_POINT_GAP)
        val rightX = ((rawTopRight.xPermille + rawBottomRight.xPermille) / 2)
            .coerceIn(leftX + MIN_POINT_GAP, COORDINATE_MAX)
        val leftTopY = rawTopLeft.yPermille.coerceIn(0, COORDINATE_MAX - MIN_POINT_GAP)
        val leftBottomY = rawBottomLeft.yPermille.coerceIn(
            leftTopY + MIN_POINT_GAP,
            COORDINATE_MAX,
        )
        val rightTopY = rawTopRight.yPermille.coerceIn(0, COORDINATE_MAX - MIN_POINT_GAP)
        val rightBottomY = rawBottomRight.yPermille.coerceIn(
            rightTopY + MIN_POINT_GAP,
            COORDINATE_MAX,
        )

        return copy(
            topLeft = ElectronicMirrorCalibrationPoint(leftX, leftTopY),
            topRight = ElectronicMirrorCalibrationPoint(rightX, rightTopY),
            bottomRight = ElectronicMirrorCalibrationPoint(rightX, rightBottomY),
            bottomLeft = ElectronicMirrorCalibrationPoint(leftX, leftBottomY),
            yawDegrees = yawDegrees.coerceIn(MIN_ANGLE_DEGREES, MAX_ANGLE_DEGREES),
            rollDegrees = rollDegrees.coerceIn(MIN_ANGLE_DEGREES, MAX_ANGLE_DEGREES),
            pitchDegrees = pitchDegrees.coerceIn(MIN_ANGLE_DEGREES, MAX_ANGLE_DEGREES),
        )
    }

    companion object {
        const val COORDINATE_MAX = 1000
        const val MIN_POINT_GAP = 80
        const val MIN_ANGLE_DEGREES = -15
        const val MAX_ANGLE_DEGREES = 15

        val DEFAULT = ElectronicMirrorCalibration()
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
    val leftCalibration: ElectronicMirrorCalibration = ElectronicMirrorCalibration.DEFAULT,
    val rightCalibration: ElectronicMirrorCalibration = ElectronicMirrorCalibration.DEFAULT,
    val rearCalibration: ElectronicMirrorCalibration = ElectronicMirrorCalibration.DEFAULT,
    val leftSource: ElectronicMirrorCameraSource = ElectronicMirrorCameraSource.SOURCE_A,
    val rightSource: ElectronicMirrorCameraSource = ElectronicMirrorCameraSource.SOURCE_B,
    val rearSource: ElectronicMirrorCameraSource = ElectronicMirrorCameraSource.SOURCE_C,
) {
    fun geometryFor(target: ElectronicMirrorDisplayTarget = displayTarget): ElectronicMirrorGeometry =
        when (target) {
            ElectronicMirrorDisplayTarget.CLUSTER -> clusterGeometry
            ElectronicMirrorDisplayTarget.CENTER -> centerGeometry
        }

    fun calibrationFor(viewpoint: ElectronicMirrorViewpoint): ElectronicMirrorCalibration =
        when (viewpoint) {
            ElectronicMirrorViewpoint.LEFT -> leftCalibration
            ElectronicMirrorViewpoint.RIGHT -> rightCalibration
            ElectronicMirrorViewpoint.REAR -> rearCalibration
        }

    fun withCalibration(
        viewpoint: ElectronicMirrorViewpoint,
        calibration: ElectronicMirrorCalibration,
    ): ElectronicMirrorConfig = when (viewpoint) {
        ElectronicMirrorViewpoint.LEFT -> copy(leftCalibration = calibration.sanitized())
        ElectronicMirrorViewpoint.RIGHT -> copy(rightCalibration = calibration.sanitized())
        ElectronicMirrorViewpoint.REAR -> copy(rearCalibration = calibration.sanitized())
    }

    fun sourceFor(viewpoint: ElectronicMirrorViewpoint): ElectronicMirrorCameraSource =
        when (viewpoint) {
            ElectronicMirrorViewpoint.LEFT -> leftSource
            ElectronicMirrorViewpoint.RIGHT -> rightSource
            ElectronicMirrorViewpoint.REAR -> rearSource
        }

    /** Assigns one source per logical view; selecting an occupied source swaps both assignments. */
    fun withSource(
        viewpoint: ElectronicMirrorViewpoint,
        source: ElectronicMirrorCameraSource,
    ): ElectronicMirrorConfig {
        val safe = sanitized()
        val previousSource = safe.sourceFor(viewpoint)
        if (previousSource == source) return safe
        val occupiedViewpoint = ElectronicMirrorViewpoint.entries.firstOrNull { candidate ->
            candidate != viewpoint && safe.sourceFor(candidate) == source
        }
        var updated = safe.setSource(viewpoint, source)
        if (occupiedViewpoint != null) {
            updated = updated.setSource(occupiedViewpoint, previousSource)
        }
        return updated
    }

    fun resetSources(): ElectronicMirrorConfig = copy(
        leftSource = ElectronicMirrorCameraSource.SOURCE_A,
        rightSource = ElectronicMirrorCameraSource.SOURCE_B,
        rearSource = ElectronicMirrorCameraSource.SOURCE_C,
    )

    private fun setSource(
        viewpoint: ElectronicMirrorViewpoint,
        source: ElectronicMirrorCameraSource,
    ): ElectronicMirrorConfig = when (viewpoint) {
        ElectronicMirrorViewpoint.LEFT -> copy(leftSource = source)
        ElectronicMirrorViewpoint.RIGHT -> copy(rightSource = source)
        ElectronicMirrorViewpoint.REAR -> copy(rearSource = source)
    }

    fun sanitized(): ElectronicMirrorConfig {
        val sourcesAreUnique = setOf(leftSource, rightSource, rearSource).size ==
            ElectronicMirrorCameraSource.entries.size
        return copy(
            fps = fps.coerceIn(MIN_FPS, MAX_FPS),
            clusterGeometry = clusterGeometry.sanitized(),
            centerGeometry = centerGeometry.sanitized(),
            leftCalibration = leftCalibration.sanitized(),
            rightCalibration = rightCalibration.sanitized(),
            rearCalibration = rearCalibration.sanitized(),
            leftSource = if (sourcesAreUnique) leftSource else ElectronicMirrorCameraSource.SOURCE_A,
            rightSource = if (sourcesAreUnique) rightSource else ElectronicMirrorCameraSource.SOURCE_B,
            rearSource = if (sourcesAreUnique) rearSource else ElectronicMirrorCameraSource.SOURCE_C,
        )
    }

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
    val source: ElectronicMirrorCameraSource,
    val target: ElectronicMirrorDisplayTarget,
    val geometry: ElectronicMirrorGeometry,
    val reason: ElectronicMirrorReason,
    val fps: Int,
    val calibration: ElectronicMirrorCalibration,
)

data class ElectronicMirrorRuntimeStatus(
    val active: Boolean = false,
    val message: String = "待机",
    val viewpoint: ElectronicMirrorViewpoint? = null,
    val target: ElectronicMirrorDisplayTarget? = null,
)

/** Prevents cluster projection from competing with an active electronic-mirror surface. */
object ElectronicMirrorRuntimeGuard {
    @Volatile
    var isActive: Boolean = false
        internal set
}
