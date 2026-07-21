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

/** Logical tiles inside BYD's rectified horizontal panorama strip. */
enum class ElectronicMirrorCameraSource(
    val stripTileIndex: Int,
    val displayName: String,
) {
    // The validated strip order is rear, left, right, front. Keep A/B/C labels so the
    // image-order editor can correct a vehicle whose configured order differs.
    SOURCE_A(1, "来源 A"),
    SOURCE_B(2, "来源 B"),
    SOURCE_C(0, "来源 C"),
}

enum class ElectronicMirrorReason {
    MANUAL_PREVIEW,
    REVERSE,
    LEFT_TURN,
    RIGHT_TURN,
    STREAMING_REAR,
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

    fun sanitizedSide(): ElectronicMirrorGeometry = sanitized().copy(
        widthPct = widthPct.coerceIn(MIN_SIDE_WIDTH_PCT, MAX_SIDE_WIDTH_PCT),
        heightPct = heightPct.coerceIn(MIN_SIDE_HEIGHT_PCT, MAX_SIDE_HEIGHT_PCT),
        offsetXPct = offsetXPct.coerceIn(MIN_SIDE_INSET_PCT, MAX_SIDE_INSET_PCT),
    )

    fun sanitizedStreaming(): ElectronicMirrorGeometry = sanitized().copy(
        widthPct = widthPct.coerceIn(MIN_STREAMING_WIDTH_PCT, MAX_STREAMING_WIDTH_PCT),
        heightPct = heightPct.coerceIn(MIN_STREAMING_HEIGHT_PCT, MAX_STREAMING_HEIGHT_PCT),
    )

    companion object {
        const val MIN_SIZE_PCT = 10
        const val MAX_SIZE_PCT = 100
        const val MIN_OFFSET_PCT = 0
        const val MAX_OFFSET_PCT = 100
        const val MIN_SIDE_WIDTH_PCT = 16
        const val MAX_SIDE_WIDTH_PCT = 48
        const val MIN_SIDE_HEIGHT_PCT = 24
        const val MAX_SIDE_HEIGHT_PCT = 80
        const val MIN_SIDE_INSET_PCT = 0
        const val MAX_SIDE_INSET_PCT = 50
        const val MIN_STREAMING_WIDTH_PCT = 28
        const val MAX_STREAMING_WIDTH_PCT = 100
        const val MIN_STREAMING_HEIGHT_PCT = 10
        const val MAX_STREAMING_HEIGHT_PCT = 52

        val DEFAULT_CLUSTER = ElectronicMirrorGeometry(
            widthPct = 30,
            heightPct = 80,
            offsetXPct = 0,
            offsetYPct = 60,
        )
        val DEFAULT_CENTER = ElectronicMirrorGeometry(
            widthPct = 30,
            heightPct = 80,
            offsetXPct = 0,
            offsetYPct = 60,
        )
        val DEFAULT_STREAMING_CLUSTER = ElectronicMirrorGeometry(
            widthPct = 46,
            heightPct = 38,
            offsetXPct = 50,
            offsetYPct = 8,
        )
        val DEFAULT_STREAMING_CENTER = ElectronicMirrorGeometry(
            widthPct = 46,
            heightPct = 38,
            offsetXPct = 50,
            offsetYPct = 8,
        )
    }
}

/** Crop controls for one rectified panorama tile. */
data class ElectronicMirrorProjectionSpec(
    val horizontalPermille: Int = 0,
    val heightOffsetPermille: Int = 0,
    val viewScalePermille: Int = 1000,
) {
    val horizontal: Float
        get() = horizontalPermille / 1000f

    val heightOffset: Float
        get() = heightOffsetPermille / 1000f

    val verticalCenterRatio: Float
        get() = (DEFAULT_VERTICAL_CENTER_RATIO - heightOffset)
            .coerceIn(MIN_VERTICAL_CENTER_RATIO, MAX_VERTICAL_CENTER_RATIO)

    val viewScale: Float
        get() = viewScalePermille / 1000f

    fun sanitized(): ElectronicMirrorProjectionSpec = copy(
        horizontalPermille = horizontalPermille.coerceIn(MIN_HORIZONTAL, MAX_HORIZONTAL),
        heightOffsetPermille = heightOffsetPermille.coerceIn(MIN_HEIGHT_OFFSET, MAX_HEIGHT_OFFSET),
        viewScalePermille = viewScalePermille.coerceIn(MIN_VIEW_SCALE, MAX_VIEW_SCALE),
    )

    fun sanitizedFor(viewpoint: ElectronicMirrorViewpoint): ElectronicMirrorProjectionSpec {
        val safe = sanitized()
        return when (viewpoint) {
            ElectronicMirrorViewpoint.LEFT -> safe.copy(
                horizontalPermille = safe.horizontalPermille.coerceIn(
                    MIN_SIDE_HORIZONTAL,
                    0,
                ),
                heightOffsetPermille = safe.heightOffsetPermille.coerceIn(
                    MIN_SIDE_HEIGHT_OFFSET,
                    MAX_SIDE_HEIGHT_OFFSET,
                ),
                viewScalePermille = safe.viewScalePermille.coerceIn(
                    MIN_SIDE_VIEW_SCALE,
                    MAX_SIDE_VIEW_SCALE,
                ),
            )
            ElectronicMirrorViewpoint.RIGHT -> safe.copy(
                horizontalPermille = safe.horizontalPermille.coerceIn(
                    0,
                    MAX_SIDE_HORIZONTAL,
                ),
                heightOffsetPermille = safe.heightOffsetPermille.coerceIn(
                    MIN_SIDE_HEIGHT_OFFSET,
                    MAX_SIDE_HEIGHT_OFFSET,
                ),
                viewScalePermille = safe.viewScalePermille.coerceIn(
                    MIN_SIDE_VIEW_SCALE,
                    MAX_SIDE_VIEW_SCALE,
                ),
            )
            ElectronicMirrorViewpoint.REAR -> safe.copy(
                horizontalPermille = safe.horizontalPermille.coerceIn(
                    MIN_REAR_HORIZONTAL,
                    MAX_REAR_HORIZONTAL,
                ),
                heightOffsetPermille = safe.heightOffsetPermille.coerceIn(
                    MIN_REAR_HEIGHT_OFFSET,
                    MAX_REAR_HEIGHT_OFFSET,
                ),
                viewScalePermille = safe.viewScalePermille.coerceIn(
                    MIN_REAR_VIEW_SCALE,
                    MAX_REAR_VIEW_SCALE,
                ),
            )
        }
    }

    companion object {
        const val MIN_HORIZONTAL = -450
        const val MAX_HORIZONTAL = 450
        const val MIN_HEIGHT_OFFSET = -100
        const val MAX_HEIGHT_OFFSET = 120
        const val MIN_VIEW_SCALE = 850
        const val MAX_VIEW_SCALE = 2000
        const val MIN_SIDE_HORIZONTAL = -450
        const val MAX_SIDE_HORIZONTAL = 450
        const val MIN_SIDE_HEIGHT_OFFSET = -80
        const val MAX_SIDE_HEIGHT_OFFSET = 100
        const val MIN_SIDE_VIEW_SCALE = 850
        const val MAX_SIDE_VIEW_SCALE = 2000
        const val MIN_REAR_HORIZONTAL = -350
        const val MAX_REAR_HORIZONTAL = 350
        const val MIN_REAR_HEIGHT_OFFSET = -100
        const val MAX_REAR_HEIGHT_OFFSET = 120
        const val MIN_REAR_VIEW_SCALE = 850
        const val MAX_REAR_VIEW_SCALE = 1500
        const val DEFAULT_VERTICAL_CENTER_RATIO = 0.52f
        const val MIN_VERTICAL_CENTER_RATIO = 0.4f
        const val MAX_VERTICAL_CENTER_RATIO = 0.62f

        val DEFAULT_LEFT = ElectronicMirrorProjectionSpec(
            horizontalPermille = -120,
            heightOffsetPermille = 100,
            viewScalePermille = 1350,
        )
        val DEFAULT_RIGHT = ElectronicMirrorProjectionSpec(
            horizontalPermille = 120,
            heightOffsetPermille = 100,
            viewScalePermille = 1350,
        )
        val DEFAULT_REAR = ElectronicMirrorProjectionSpec(
            horizontalPermille = 0,
            heightOffsetPermille = 120,
            viewScalePermille = 1500,
        )
        fun defaultFor(viewpoint: ElectronicMirrorViewpoint): ElectronicMirrorProjectionSpec =
            when (viewpoint) {
                ElectronicMirrorViewpoint.LEFT -> DEFAULT_LEFT
                ElectronicMirrorViewpoint.RIGHT -> DEFAULT_RIGHT
                ElectronicMirrorViewpoint.REAR -> DEFAULT_REAR
            }
    }
}

/**
 * Camera producer profile. Camera identity, Surface output, strip count and visible tile are kept
 * separate so selecting another view never silently changes the AVMCamera output channel.
 */
data class ElectronicMirrorSurfaceProfile(
    val cameraTag: String = DEFAULT_CAMERA_TAG,
    val surfaceIndex: Int = DEFAULT_SURFACE_INDEX,
    val tileCount: Int = DEFAULT_TILE_COUNT,
    val qualityWidth: Int = DEFAULT_QUALITY_WIDTH,
    val sourceWidth: Int = DEFAULT_SOURCE_WIDTH,
    val sourceHeight: Int = DEFAULT_SOURCE_HEIGHT,
) {
    val bufferWidth: Int
        get() {
            val safeSourceWidth = sourceWidth.coerceIn(MIN_SOURCE_WIDTH, MAX_SOURCE_WIDTH)
            val requested = qualityWidth.coerceIn(MIN_QUALITY_WIDTH, MAX_QUALITY_WIDTH) *
                tileCount.coerceIn(1, MAX_TILE_COUNT)
            return requested.coerceIn(MIN_BUFFER_WIDTH, safeSourceWidth)
        }

    val bufferHeight: Int
        get() {
            val safeSourceWidth = sourceWidth.coerceIn(MIN_SOURCE_WIDTH, MAX_SOURCE_WIDTH)
            val safeSourceHeight = sourceHeight.coerceIn(MIN_SOURCE_HEIGHT, MAX_SOURCE_HEIGHT)
            return ((safeSourceHeight.toLong() * bufferWidth + safeSourceWidth / 2L) / safeSourceWidth)
                .toInt()
                .coerceIn(MIN_BUFFER_HEIGHT, safeSourceHeight)
        }

    fun sanitized(): ElectronicMirrorSurfaceProfile = copy(
        cameraTag = cameraTag.trim().ifEmpty { DEFAULT_CAMERA_TAG },
        surfaceIndex = surfaceIndex.coerceIn(MIN_SURFACE_INDEX, MAX_SURFACE_INDEX),
        tileCount = tileCount.coerceIn(1, MAX_TILE_COUNT),
        qualityWidth = qualityWidth.coerceIn(MIN_QUALITY_WIDTH, MAX_QUALITY_WIDTH),
        sourceWidth = sourceWidth.coerceIn(MIN_SOURCE_WIDTH, MAX_SOURCE_WIDTH),
        sourceHeight = sourceHeight.coerceIn(MIN_SOURCE_HEIGHT, MAX_SOURCE_HEIGHT),
    )

    companion object {
        const val DEFAULT_CAMERA_TAG = "pano_h"
        const val DEFAULT_SURFACE_INDEX = 0
        const val DEFAULT_TILE_COUNT = 4
        const val DEFAULT_QUALITY_WIDTH = 750
        const val DEFAULT_SOURCE_WIDTH = 7680
        const val DEFAULT_SOURCE_HEIGHT = 1300
        const val MIN_SURFACE_INDEX = 0
        const val MAX_SURFACE_INDEX = 15
        const val MAX_TILE_COUNT = 8
        const val MIN_QUALITY_WIDTH = 480
        const val MAX_QUALITY_WIDTH = 1280
        const val MIN_SOURCE_WIDTH = 640
        const val MAX_SOURCE_WIDTH = 8192
        const val MIN_SOURCE_HEIGHT = 160
        const val MAX_SOURCE_HEIGHT = 2160
        private const val MIN_BUFFER_WIDTH = 640
        private const val MIN_BUFFER_HEIGHT = 160

        fun panorama(surfaceIndex: Int): ElectronicMirrorSurfaceProfile =
            ElectronicMirrorSurfaceProfile(surfaceIndex = surfaceIndex).sanitized()

    }
}

data class ElectronicMirrorConfig(
    val enabled: Boolean = false,
    val displayTarget: ElectronicMirrorDisplayTarget = ElectronicMirrorDisplayTarget.CENTER,
    val leftTurnEnabled: Boolean = true,
    val rightTurnEnabled: Boolean = true,
    val reverseEnabled: Boolean = false,
    val fps: Int = DEFAULT_FPS,
    val clusterGeometry: ElectronicMirrorGeometry = ElectronicMirrorGeometry.DEFAULT_CLUSTER,
    val centerGeometry: ElectronicMirrorGeometry = ElectronicMirrorGeometry.DEFAULT_CENTER,
    val leftProjection: ElectronicMirrorProjectionSpec = ElectronicMirrorProjectionSpec.DEFAULT_LEFT,
    val rightProjection: ElectronicMirrorProjectionSpec = ElectronicMirrorProjectionSpec.DEFAULT_RIGHT,
    val rearProjection: ElectronicMirrorProjectionSpec = ElectronicMirrorProjectionSpec.DEFAULT_REAR,
    val leftSource: ElectronicMirrorCameraSource = ElectronicMirrorCameraSource.SOURCE_A,
    val rightSource: ElectronicMirrorCameraSource = ElectronicMirrorCameraSource.SOURCE_B,
    val rearSource: ElectronicMirrorCameraSource = ElectronicMirrorCameraSource.SOURCE_C,
    val streamingRearEnabled: Boolean = false,
    val streamingRearVisible: Boolean = true,
    val streamingRearDisplayTarget: ElectronicMirrorDisplayTarget =
        ElectronicMirrorDisplayTarget.CENTER,
    val streamingRearClusterGeometry: ElectronicMirrorGeometry =
        ElectronicMirrorGeometry.DEFAULT_STREAMING_CLUSTER,
    val streamingRearCenterGeometry: ElectronicMirrorGeometry =
        ElectronicMirrorGeometry.DEFAULT_STREAMING_CENTER,
    val panoramaSurfaceIndex: Int = ElectronicMirrorSurfaceProfile.DEFAULT_SURFACE_INDEX,
) {
    fun geometryFor(target: ElectronicMirrorDisplayTarget = displayTarget): ElectronicMirrorGeometry =
        when (target) {
            ElectronicMirrorDisplayTarget.CLUSTER -> clusterGeometry
            ElectronicMirrorDisplayTarget.CENTER -> centerGeometry
        }

    fun projectionFor(viewpoint: ElectronicMirrorViewpoint): ElectronicMirrorProjectionSpec =
        when (viewpoint) {
            ElectronicMirrorViewpoint.LEFT -> leftProjection
            ElectronicMirrorViewpoint.RIGHT -> rightProjection
            ElectronicMirrorViewpoint.REAR -> rearProjection
        }

    fun streamingRearGeometryFor(
        target: ElectronicMirrorDisplayTarget = streamingRearDisplayTarget,
    ): ElectronicMirrorGeometry = when (target) {
        ElectronicMirrorDisplayTarget.CLUSTER -> streamingRearClusterGeometry
        ElectronicMirrorDisplayTarget.CENTER -> streamingRearCenterGeometry
    }

    fun withProjection(
        viewpoint: ElectronicMirrorViewpoint,
        projection: ElectronicMirrorProjectionSpec,
    ): ElectronicMirrorConfig = when (viewpoint) {
        ElectronicMirrorViewpoint.LEFT -> copy(leftProjection = projection.sanitizedFor(viewpoint))
        ElectronicMirrorViewpoint.RIGHT -> copy(rightProjection = projection.sanitizedFor(viewpoint))
        ElectronicMirrorViewpoint.REAR -> copy(rearProjection = projection.sanitizedFor(viewpoint))
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
            clusterGeometry = clusterGeometry.sanitizedSide(),
            centerGeometry = centerGeometry.sanitizedSide(),
            leftProjection = leftProjection.sanitizedFor(ElectronicMirrorViewpoint.LEFT),
            rightProjection = rightProjection.sanitizedFor(ElectronicMirrorViewpoint.RIGHT),
            rearProjection = rearProjection.sanitizedFor(ElectronicMirrorViewpoint.REAR),
            streamingRearClusterGeometry = streamingRearClusterGeometry.sanitizedStreaming(),
            streamingRearCenterGeometry = streamingRearCenterGeometry.sanitizedStreaming(),
            leftSource = if (sourcesAreUnique) leftSource else ElectronicMirrorCameraSource.SOURCE_A,
            rightSource = if (sourcesAreUnique) rightSource else ElectronicMirrorCameraSource.SOURCE_B,
            rearSource = if (sourcesAreUnique) rearSource else ElectronicMirrorCameraSource.SOURCE_C,
            panoramaSurfaceIndex = panoramaSurfaceIndex.coerceIn(
                ElectronicMirrorSurfaceProfile.MIN_SURFACE_INDEX,
                ElectronicMirrorSurfaceProfile.MAX_SURFACE_INDEX,
            ),
        )
    }

    companion object {
        const val DEFAULT_FPS = 20
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
    val projection: ElectronicMirrorProjectionSpec,
    val surfaceProfile: ElectronicMirrorSurfaceProfile,
    val tileIndex: Int = 0,
    val mirrorHorizontally: Boolean = false,
) {
    val helperSurfaceIndex: Int
        get() = surfaceProfile.surfaceIndex

    val previewBufferWidth: Int
        get() = surfaceProfile.bufferWidth

    val previewBufferHeight: Int
        get() = surfaceProfile.bufferHeight
}

data class ElectronicMirrorRuntimeStatus(
    val active: Boolean = false,
    val message: String = "待机",
    val viewpoint: ElectronicMirrorViewpoint? = null,
    val target: ElectronicMirrorDisplayTarget? = null,
)

/** One physical-button press always converges to a simple visible/hidden toggle. */
internal fun ElectronicMirrorConfig.toggledStreamingRearFromShortcut(): ElectronicMirrorConfig {
    val currentlyVisible = streamingRearEnabled && streamingRearVisible
    return copy(
        streamingRearEnabled = true,
        streamingRearVisible = !currentlyVisible,
    ).sanitized()
}

/** Prevents cluster projection from competing with an active electronic-mirror surface. */
object ElectronicMirrorRuntimeGuard {
    @Volatile
    var isActive: Boolean = false
        internal set
}
