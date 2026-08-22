package com.byd.wjx.electronicmirror

enum class ElectronicMirrorSide { NONE, LEFT, RIGHT }

data class ElectronicMirrorInput(
    val turnSignal: Int?,
    val speedKmh: Float?,
    val gearIsReverse: Boolean,
    val thresholdKmh: Int,
    val telemetryAgeMs: Long,
)

data class ElectronicMirrorDecision(
    val show: ElectronicMirrorSide,
    val cameraWarm: Boolean,
)

const val ELECTRONIC_MIRROR_WATCHDOG_MS = 750L
const val ELECTRONIC_MIRROR_WARM_HYSTERESIS_KMH = 5
const val ELECTRONIC_MIRROR_GEAR_REVERSE = 2

fun decideElectronicMirror(input: ElectronicMirrorInput): ElectronicMirrorDecision {
    val speed = input.speedKmh
    val warm = !input.gearIsReverse && speed != null &&
        speed >= input.thresholdKmh - ELECTRONIC_MIRROR_WARM_HYSTERESIS_KMH
    val show = when {
        input.gearIsReverse -> ElectronicMirrorSide.NONE
        input.telemetryAgeMs > ELECTRONIC_MIRROR_WATCHDOG_MS -> ElectronicMirrorSide.NONE
        speed == null || speed < input.thresholdKmh -> ElectronicMirrorSide.NONE
        input.turnSignal == TURN_SIGNAL_LEFT -> ElectronicMirrorSide.LEFT
        input.turnSignal == TURN_SIGNAL_RIGHT -> ElectronicMirrorSide.RIGHT
        else -> ElectronicMirrorSide.NONE
    }
    return ElectronicMirrorDecision(show, warm)
}

fun electronicMirrorArmed(
    enabled: Boolean,
    gear: Int?,
    speedKmh: Int?,
    thresholdKmh: Int,
): Boolean = enabled && gear != ELECTRONIC_MIRROR_GEAR_REVERSE && speedKmh != null &&
    speedKmh >= thresholdKmh - ELECTRONIC_MIRROR_WARM_HYSTERESIS_KMH

data class ElectronicMirrorSample(
    val turnSignal: Int?,
    val speedKmh: Float?,
    val gear: Int?,
) {
    val isValid: Boolean get() = turnSignal != null && speedKmh != null && gear != null
}

data class ElectronicMirrorTelemetryState(
    val ageMs: Long,
    val mustClose: Boolean,
    val lastValid: ElectronicMirrorSample?,
)

const val ELECTRONIC_MIRROR_TELEMETRY_LOST_MS = 3_000L
const val ELECTRONIC_MIRROR_MAX_READ_FAILURES = 2

class ElectronicMirrorTelemetryGate {
    private var lastValidAt = 0L
    private var armedAt = 0L
    private var readFailures = 0
    private var lastValid: ElectronicMirrorSample? = null

    fun reset(now: Long) {
        lastValidAt = 0L
        armedAt = now
        readFailures = 0
        lastValid = null
    }

    fun onSample(sample: ElectronicMirrorSample?, now: Long): ElectronicMirrorTelemetryState {
        if (sample == null) readFailures++ else readFailures = 0
        if (sample != null && sample.isValid) {
            lastValidAt = now
            lastValid = sample
        }
        val ageMs = if (lastValidAt != 0L) now - lastValidAt else Long.MAX_VALUE
        val silentFor = if (lastValidAt != 0L) now - lastValidAt else now - armedAt
        return ElectronicMirrorTelemetryState(
            ageMs = ageMs,
            mustClose = readFailures >= ELECTRONIC_MIRROR_MAX_READ_FAILURES ||
                silentFor > ELECTRONIC_MIRROR_TELEMETRY_LOST_MS,
            lastValid = lastValid,
        )
    }
}

private const val TURN_SIGNAL_LEFT = 2
private const val TURN_SIGNAL_RIGHT = 4
