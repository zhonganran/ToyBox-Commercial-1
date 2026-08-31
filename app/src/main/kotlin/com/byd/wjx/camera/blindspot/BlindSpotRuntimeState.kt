package com.byd.wjx.camera.blindspot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BlindSpotStatusCode {
    DISABLED,
    WAITING,
    READY,
    SHOWING_LEFT,
    SHOWING_RIGHT,
    SHOWING_REAR,
    SHOWING_BOTH,
    SHOWING_MULTIPLE,
    CAMERA_PERMISSION_REQUIRED,
    OVERLAY_PERMISSION_REQUIRED,
    TELEMETRY_UNAVAILABLE,
    CAMERA_UNAVAILABLE,
    REVERSE_GUARD,
    FACTORY_CAMERA_ACTIVE,
}

data class BlindSpotRuntimeStatus(
    val code: BlindSpotStatusCode = BlindSpotStatusCode.DISABLED,
    val detail: String = "",
)

data class BlindSpotCalibrationViewport(
    val side: BlindSpotSide = BlindSpotSide.NONE,
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val editingCrop: Boolean = false,
) {
    val active: Boolean
        get() = side != BlindSpotSide.NONE && width > 0 && height > 0
}

data class BlindSpotCalibrationCropEdit(
    val side: BlindSpotSide,
    val corrected: Boolean,
    val crop: BlindSpotCrop,
    val committed: Boolean,
    val revision: Long,
)

data class BlindSpotCalibrationPreviewUpdate(
    val config: BlindSpotSideConfig,
    val revision: Long,
)

internal fun blindSpotSnapshotWithCalibrationDraft(
    snapshot: BlindSpotPreferenceSnapshot,
    viewport: BlindSpotCalibrationViewport,
    draft: BlindSpotSideConfig?,
): BlindSpotPreferenceSnapshot {
    if (!viewport.active || draft == null || draft.side != viewport.side) return snapshot
    return when (draft.side) {
        BlindSpotSide.LEFT -> snapshot.copy(left = draft)
        BlindSpotSide.RIGHT -> snapshot.copy(right = draft)
        BlindSpotSide.REAR -> snapshot.copy(rear = draft)
        BlindSpotSide.NONE -> snapshot
    }
}

object BlindSpotRuntimeState {
    private val mutableStatus = MutableStateFlow(BlindSpotRuntimeStatus())
    private val mutableManualPreview = MutableStateFlow(BlindSpotSide.NONE)
    private val mutableCalibrationViewport = MutableStateFlow(BlindSpotCalibrationViewport())
    private val mutableCalibrationCropEdit = MutableStateFlow<BlindSpotCalibrationCropEdit?>(null)
    private val mutableCalibrationPreviewUpdate =
        MutableStateFlow<BlindSpotCalibrationPreviewUpdate?>(null)

    val status: StateFlow<BlindSpotRuntimeStatus> = mutableStatus.asStateFlow()
    val manualPreview: StateFlow<BlindSpotSide> = mutableManualPreview.asStateFlow()
    val calibrationViewport: StateFlow<BlindSpotCalibrationViewport> =
        mutableCalibrationViewport.asStateFlow()
    val calibrationCropEdit: StateFlow<BlindSpotCalibrationCropEdit?> =
        mutableCalibrationCropEdit.asStateFlow()
    val calibrationPreviewUpdate: StateFlow<BlindSpotCalibrationPreviewUpdate?> =
        mutableCalibrationPreviewUpdate.asStateFlow()

    fun publish(code: BlindSpotStatusCode, detail: String = "") {
        val next = BlindSpotRuntimeStatus(code, detail)
        if (mutableStatus.value != next) mutableStatus.value = next
    }

    fun requestPreview(side: BlindSpotSide) {
        mutableManualPreview.value = side
    }

    fun requestCalibration(
        side: BlindSpotSide,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        editingCrop: Boolean,
    ) {
        mutableManualPreview.value = BlindSpotSide.NONE
        mutableCalibrationViewport.value = BlindSpotCalibrationViewport(
            side = side,
            left = left,
            top = top,
            width = width,
            height = height,
            editingCrop = editingCrop,
        )
    }

    fun stopCalibration() {
        mutableManualPreview.value = BlindSpotSide.NONE
        mutableCalibrationViewport.value = BlindSpotCalibrationViewport()
        mutableCalibrationCropEdit.value = null
        mutableCalibrationPreviewUpdate.value = null
    }

    fun publishCalibrationPreview(config: BlindSpotSideConfig) {
        if (config.side == BlindSpotSide.NONE) return
        val revision = (mutableCalibrationPreviewUpdate.value?.revision ?: 0L) + 1L
        mutableCalibrationPreviewUpdate.value = BlindSpotCalibrationPreviewUpdate(config, revision)
    }

    internal fun publishCalibrationCropEdit(
        side: BlindSpotSide,
        corrected: Boolean,
        crop: BlindSpotCrop,
        committed: Boolean,
    ) {
        val revision = (mutableCalibrationCropEdit.value?.revision ?: 0L) + 1L
        mutableCalibrationCropEdit.value = BlindSpotCalibrationCropEdit(
            side = side,
            corrected = corrected,
            crop = crop,
            committed = committed,
            revision = revision,
        )
    }
}
