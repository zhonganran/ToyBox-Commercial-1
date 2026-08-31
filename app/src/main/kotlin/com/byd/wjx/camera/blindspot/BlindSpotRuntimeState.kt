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
    SHOWING_BOTH,
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

object BlindSpotRuntimeState {
    private val mutableStatus = MutableStateFlow(BlindSpotRuntimeStatus())
    private val mutableManualPreview = MutableStateFlow(BlindSpotSide.NONE)

    val status: StateFlow<BlindSpotRuntimeStatus> = mutableStatus.asStateFlow()
    val manualPreview: StateFlow<BlindSpotSide> = mutableManualPreview.asStateFlow()

    fun publish(code: BlindSpotStatusCode, detail: String = "") {
        val next = BlindSpotRuntimeStatus(code, detail)
        if (mutableStatus.value != next) mutableStatus.value = next
    }

    fun requestPreview(side: BlindSpotSide) {
        mutableManualPreview.value = side
    }
}
