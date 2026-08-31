package com.byd.wjx.camera.blindspot

enum class BlindSpotSide { NONE, LEFT, RIGHT }

data class BlindSpotInput(
    val blink: Int?,
    val speedKmh: Float?,
    val steeringAngleDeg: Float?,
    val gearIsReverse: Boolean,
    val gearIsForward: Boolean = true,
    val minSpeedKmh: Int,
    val maxSpeedKmh: Int,
    val telemetryAgeMs: Long,
    val bsdOnly: Boolean = false,
    val leftBsdActive: Boolean? = null,
    val rightBsdActive: Boolean? = null,
    val sharpTurnEnabled: Boolean = false,
    val sharpTurnAngleDeg: Int = BlindSpotPreferences.DEFAULT_SHARP_TURN_ANGLE_DEG,
)

data class BlindSpotDecision(
    val show: Set<BlindSpotSide>,
    val cameraWarm: Boolean,
)

const val BLIND_SPOT_WATCHDOG_MS = 750L
const val BLIND_SPOT_WARM_HYSTERESIS_KMH = 5
const val BLIND_SPOT_GEAR_REVERSE = 2
const val BLIND_SPOT_GEAR_DRIVE = 4
const val BLIND_SPOT_STALL_MS = 1_500L
const val BLIND_SPOT_TELEMETRY_LOST_MS = 3_000L
const val BLIND_SPOT_MAX_READ_FAILURES = 2

fun decideBlindSpot(input: BlindSpotInput): BlindSpotDecision {
    val speed = input.speedKmh
    val speedRangeValid = input.minSpeedKmh in BlindSpotPreferences.MIN_SPEED_KMH..input.maxSpeedKmh &&
        input.maxSpeedKmh <= BlindSpotPreferences.MAX_SPEED_KMH
    val primary = when (input.blink) {
        BLINK_LEFT -> BlindSpotSide.LEFT
        BLINK_RIGHT -> BlindSpotSide.RIGHT
        else -> BlindSpotSide.NONE
    }
    val warm = input.gearIsForward && !input.gearIsReverse && speedRangeValid && speed != null &&
        primary != BlindSpotSide.NONE &&
        speed >= input.minSpeedKmh - BLIND_SPOT_WARM_HYSTERESIS_KMH &&
        speed <= input.maxSpeedKmh + BLIND_SPOT_WARM_HYSTERESIS_KMH
    if (
        input.gearIsReverse || !input.gearIsForward ||
        input.telemetryAgeMs > BLIND_SPOT_WATCHDOG_MS ||
        !speedRangeValid || speed == null || speed !in input.minSpeedKmh.toFloat()..input.maxSpeedKmh.toFloat()
    ) {
        return BlindSpotDecision(emptySet(), warm)
    }

    if (primary == BlindSpotSide.NONE) return BlindSpotDecision(emptySet(), warm)
    val matchingBsd = when (primary) {
        BlindSpotSide.LEFT -> input.leftBsdActive
        BlindSpotSide.RIGHT -> input.rightBsdActive
        BlindSpotSide.NONE -> null
    }
    val visible = linkedSetOf<BlindSpotSide>()
    if (!input.bsdOnly || matchingBsd != false) visible += primary
    val angle = input.steeringAngleDeg
    if (
        input.sharpTurnEnabled && angle != null && angle.isFinite() &&
        input.sharpTurnAngleDeg in BlindSpotPreferences.MIN_SHARP_TURN_ANGLE_DEG..
            BlindSpotPreferences.MAX_SHARP_TURN_ANGLE_DEG
    ) {
        if (primary == BlindSpotSide.LEFT && angle >= input.sharpTurnAngleDeg) {
            visible += BlindSpotSide.RIGHT
        } else if (primary == BlindSpotSide.RIGHT && angle <= -input.sharpTurnAngleDeg) {
            visible += BlindSpotSide.LEFT
        }
    }
    return BlindSpotDecision(visible, warm)
}

internal fun blindSpotBsdActive(raw: Int?): Boolean? = raw
    ?.takeIf { it in BLIND_SPOT_BSD_MIN_RAW..BLIND_SPOT_BSD_ACTIVE_RAW }
    ?.let { it == BLIND_SPOT_BSD_ACTIVE_RAW }

fun blindSpotArmed(
    enabled: Boolean,
    gear: Int?,
    speedKmh: Int?,
    minSpeedKmh: Int,
    maxSpeedKmh: Int,
): Boolean = enabled && gear == BLIND_SPOT_GEAR_DRIVE && speedKmh != null &&
    minSpeedKmh <= maxSpeedKmh &&
    speedKmh >= minSpeedKmh - BLIND_SPOT_WARM_HYSTERESIS_KMH &&
    speedKmh <= maxSpeedKmh + BLIND_SPOT_WARM_HYSTERESIS_KMH

fun blindSpotFrameStalled(
    shown: Boolean,
    hasValidFrame: Boolean,
    lastFrameAt: Long,
    now: Long,
    stallMs: Long = BLIND_SPOT_STALL_MS,
): Boolean = shown && hasValidFrame && lastFrameAt > 0L && now - lastFrameAt >= stallMs

fun blindSpotFirstFrameTimedOut(
    shown: Boolean,
    hasValidFrame: Boolean,
    shownAt: Long,
    now: Long,
    timeoutMs: Long,
): Boolean = shown && !hasValidFrame && shownAt > 0L && now - shownAt >= timeoutMs

data class BlindSpotSample(
    val blink: Int?,
    val speedKmh: Float?,
    val gear: Int?,
    val steeringAngleDeg: Float? = null,
) {
    val isValid: Boolean get() = blink != null && speedKmh != null && gear != null
}

data class BlindSpotTelemetryState(
    val ageMs: Long,
    val mustClose: Boolean,
    val lastValid: BlindSpotSample?,
)

class BlindSpotTelemetryGate {
    private var lastValidAt = 0L
    private var armedAt = 0L
    private var readFailures = 0
    private var lastValid: BlindSpotSample? = null

    fun reset(now: Long) {
        lastValidAt = 0L
        armedAt = now
        readFailures = 0
        lastValid = null
    }

    fun onSample(sample: BlindSpotSample?, now: Long): BlindSpotTelemetryState {
        if (sample == null) readFailures++ else readFailures = 0
        if (sample?.isValid == true) {
            lastValidAt = now
            lastValid = sample
        }
        val ageMs = if (lastValidAt == 0L) Long.MAX_VALUE else now - lastValidAt
        val silentFor = if (lastValidAt == 0L) now - armedAt else now - lastValidAt
        return BlindSpotTelemetryState(
            ageMs = ageMs,
            mustClose = readFailures >= BLIND_SPOT_MAX_READ_FAILURES ||
                silentFor > BLIND_SPOT_TELEMETRY_LOST_MS,
            lastValid = lastValid,
        )
    }
}

private const val BLINK_LEFT = 2
private const val BLINK_RIGHT = 4
private const val BLIND_SPOT_BSD_MIN_RAW = 0
private const val BLIND_SPOT_BSD_ACTIVE_RAW = 2
