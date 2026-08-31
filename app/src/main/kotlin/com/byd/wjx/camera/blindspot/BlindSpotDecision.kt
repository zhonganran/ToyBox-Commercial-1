package com.byd.wjx.camera.blindspot

enum class BlindSpotSide { NONE, LEFT, RIGHT, REAR }

data class BlindSpotInput(
    val blink: Int?,
    val speedKmh: Float?,
    val steeringAngleDeg: Float?,
    val gearIsReverse: Boolean,
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

enum class BlindSpotTriggerSource { NONE, MANUAL, TURN, RADAR, REAR }

data class BlindSpotRequest(
    val sides: Set<BlindSpotSide>,
    val source: BlindSpotTriggerSource,
)

fun blindSpotRequest(
    turnSides: Set<BlindSpotSide>,
    radarSides: Set<BlindSpotSide> = emptySet(),
    rearStreamEnabled: Boolean,
    manualSide: BlindSpotSide = BlindSpotSide.NONE,
): BlindSpotRequest {
    if (manualSide != BlindSpotSide.NONE) {
        return BlindSpotRequest(setOf(manualSide), BlindSpotTriggerSource.MANUAL)
    }
    val requestedTurns = turnSides.filterTo(linkedSetOf()) {
        it == BlindSpotSide.LEFT || it == BlindSpotSide.RIGHT
    }
    if (requestedTurns.isNotEmpty()) {
        return BlindSpotRequest(requestedTurns, BlindSpotTriggerSource.TURN)
    }
    val requestedRadar = radarSides.filterTo(linkedSetOf()) {
        it == BlindSpotSide.LEFT || it == BlindSpotSide.RIGHT
    }
    if (requestedRadar.isNotEmpty()) {
        return BlindSpotRequest(requestedRadar, BlindSpotTriggerSource.RADAR)
    }
    return if (rearStreamEnabled) {
        BlindSpotRequest(setOf(BlindSpotSide.REAR), BlindSpotTriggerSource.REAR)
    } else {
        BlindSpotRequest(emptySet(), BlindSpotTriggerSource.NONE)
    }
}

fun blindSpotRequestedSides(
    turnSides: Set<BlindSpotSide>,
    radarSides: Set<BlindSpotSide> = emptySet(),
    rearStreamEnabled: Boolean,
    manualSide: BlindSpotSide = BlindSpotSide.NONE,
): Set<BlindSpotSide> = blindSpotRequest(
    turnSides = turnSides,
    radarSides = radarSides,
    rearStreamEnabled = rearStreamEnabled,
    manualSide = manualSide,
).sides

data class BlindSpotBsdFilteredState(
    val leftActive: Boolean?,
    val rightActive: Boolean?,
)

data class BlindSpotBsdRawState(
    val leftRaw: Int?,
    val rightRaw: Int?,
)

class BlindSpotBsdSignalFilter(
    private val activeSamplesRequired: Int = BLIND_SPOT_BSD_ACTIVE_SAMPLES,
    private val inactiveSamplesRequired: Int = BLIND_SPOT_BSD_INACTIVE_SAMPLES,
    private val unavailableGraceMs: Long = BLIND_SPOT_BSD_UNAVAILABLE_GRACE_MS,
) {
    private val left = SideFilter()
    private val right = SideFilter()

    fun update(leftActive: Boolean?, rightActive: Boolean?, now: Long) = BlindSpotBsdFilteredState(
        leftActive = left.update(leftActive, now),
        rightActive = right.update(rightActive, now),
    )

    fun reset() {
        left.reset()
        right.reset()
    }

    private inner class SideFilter {
        private var stableActive = false
        private var activeSamples = 0
        private var inactiveSamples = 0
        private var unavailableSince: Long? = null

        fun update(sample: Boolean?, now: Long): Boolean? = when (sample) {
            true -> {
                unavailableSince = null
                inactiveSamples = 0
                activeSamples++
                if (activeSamples >= activeSamplesRequired.coerceAtLeast(1)) {
                    stableActive = true
                    activeSamples = 0
                }
                stableActive
            }
            false -> {
                unavailableSince = null
                activeSamples = 0
                inactiveSamples++
                if (inactiveSamples >= inactiveSamplesRequired.coerceAtLeast(1)) {
                    stableActive = false
                    inactiveSamples = 0
                }
                stableActive
            }
            null -> {
                val missingSince = unavailableSince ?: now.also { unavailableSince = it }
                if (now - missingSince >= unavailableGraceMs.coerceAtLeast(0L)) {
                    stableActive = false
                    activeSamples = 0
                    inactiveSamples = 0
                    null
                } else if (stableActive) {
                    true
                } else {
                    null
                }
            }
        }

        fun reset() {
            stableActive = false
            activeSamples = 0
            inactiveSamples = 0
            unavailableSince = null
        }
    }
}

class BlindSpotAutoPopupGate {
    private val sideStates = mapOf(
        BlindSpotSide.LEFT to SideState(),
        BlindSpotSide.RIGHT to SideState(),
    )

    fun update(
        enabled: Boolean,
        eligible: Boolean,
        leftActive: Boolean?,
        rightActive: Boolean?,
        suppressed: Boolean,
        cooldownMs: Long,
        now: Long,
    ): Set<BlindSpotSide> {
        if (!enabled || !eligible) {
            reset()
            return emptySet()
        }
        return buildSet {
            if (updateSide(BlindSpotSide.LEFT, leftActive, suppressed, cooldownMs, now)) {
                add(BlindSpotSide.LEFT)
            }
            if (updateSide(BlindSpotSide.RIGHT, rightActive, suppressed, cooldownMs, now)) {
                add(BlindSpotSide.RIGHT)
            }
        }
    }

    fun reset() {
        sideStates.values.forEach(SideState::reset)
    }

    fun onDisplayed(sides: Set<BlindSpotSide>) {
        sides.forEach { side -> sideStates[side]?.takeIf { it.requested }?.displayed = true }
    }

    fun cooldownRemainingMs(side: BlindSpotSide, now: Long): Long = sideStates[side]
        ?.let { (it.blockedUntil - now).coerceAtLeast(0L) }
        ?: 0L

    private fun updateSide(
        side: BlindSpotSide,
        active: Boolean?,
        suppressed: Boolean,
        cooldownMs: Long,
        now: Long,
    ): Boolean {
        val state = sideStates.getValue(side)
        if (active == null) {
            state.clearSession()
            return false
        }
        if (!active) {
            if (state.requested && state.displayed) {
                state.blockedUntil = now + cooldownMs.coerceAtLeast(0L)
            }
            state.clearSession()
            return false
        }
        if (!state.requested && !suppressed && now >= state.blockedUntil) {
            state.requested = true
        }
        return state.requested && !suppressed
    }

    private class SideState {
        var requested = false
        var displayed = false
        var blockedUntil = 0L

        fun clearSession() {
            requested = false
            displayed = false
        }

        fun reset() {
            clearSession()
            blockedUntil = 0L
        }
    }
}

fun blindSpotAutoPopupEligible(
    gear: Int?,
    speedKmh: Float?,
    minSpeedKmh: Int,
    maxSpeedKmh: Int,
    telemetryAgeMs: Long,
): Boolean = gear != null && gear != BLIND_SPOT_GEAR_REVERSE &&
    telemetryAgeMs <= BLIND_SPOT_WATCHDOG_MS &&
    minSpeedKmh in BlindSpotPreferences.MIN_SPEED_KMH..maxSpeedKmh &&
    maxSpeedKmh <= BlindSpotPreferences.MAX_SPEED_KMH &&
    speedKmh != null && speedKmh in minSpeedKmh.toFloat()..maxSpeedKmh.toFloat()

fun rearStreamArmed(enabled: Boolean, gear: Int?): Boolean =
    enabled && gear != null && gear != BLIND_SPOT_GEAR_REVERSE

fun blindSpotManualPreviewAllowed(
    side: BlindSpotSide,
    gear: Int?,
    speedKmh: Float?,
): Boolean = when {
    side == BlindSpotSide.NONE -> false
    gear == BLIND_SPOT_GEAR_PARK -> true
    gear == BLIND_SPOT_GEAR_NEUTRAL ->
        speedKmh != null && speedKmh <= BLIND_SPOT_MANUAL_PREVIEW_MAX_SPEED_KMH
    else -> false
}

const val BLIND_SPOT_WATCHDOG_MS = 750L
const val BLIND_SPOT_WARM_HYSTERESIS_KMH = 5
const val BLIND_SPOT_GEAR_REVERSE = 2
const val BLIND_SPOT_GEAR_DRIVE = 4
const val BLIND_SPOT_GEAR_PARK = 1
const val BLIND_SPOT_GEAR_NEUTRAL = 3
const val BLIND_SPOT_MANUAL_PREVIEW_MAX_SPEED_KMH = 1f
const val BLIND_SPOT_STALL_MS = 1_500L
const val BLIND_SPOT_TELEMETRY_LOST_MS = 3_000L
const val BLIND_SPOT_MAX_READ_FAILURES = 2
const val BLIND_SPOT_BSD_ACTIVE_SAMPLES = 2
const val BLIND_SPOT_BSD_INACTIVE_SAMPLES = 2
const val BLIND_SPOT_BSD_UNAVAILABLE_GRACE_MS = 600L

fun decideBlindSpot(input: BlindSpotInput): BlindSpotDecision {
    val speed = input.speedKmh
    val speedRangeValid = input.minSpeedKmh in BlindSpotPreferences.MIN_SPEED_KMH..input.maxSpeedKmh &&
        input.maxSpeedKmh <= BlindSpotPreferences.MAX_SPEED_KMH
    val primary = when (input.blink) {
        BLINK_LEFT -> BlindSpotSide.LEFT
        BLINK_RIGHT -> BlindSpotSide.RIGHT
        else -> BlindSpotSide.NONE
    }
    val warm = !input.gearIsReverse && speedRangeValid && speed != null &&
        primary != BlindSpotSide.NONE &&
        speed >= input.minSpeedKmh - BLIND_SPOT_WARM_HYSTERESIS_KMH &&
        speed <= input.maxSpeedKmh + BLIND_SPOT_WARM_HYSTERESIS_KMH
    if (
        input.gearIsReverse ||
        input.telemetryAgeMs > BLIND_SPOT_WATCHDOG_MS ||
        !speedRangeValid || speed == null || speed !in input.minSpeedKmh.toFloat()..input.maxSpeedKmh.toFloat()
    ) {
        return BlindSpotDecision(emptySet(), warm)
    }

    if (primary == BlindSpotSide.NONE) return BlindSpotDecision(emptySet(), warm)
    val matchingBsd = when (primary) {
        BlindSpotSide.LEFT -> input.leftBsdActive
        BlindSpotSide.RIGHT -> input.rightBsdActive
        BlindSpotSide.NONE, BlindSpotSide.REAR -> null
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
): Boolean = enabled && gear != null && gear != BLIND_SPOT_GEAR_REVERSE && speedKmh != null &&
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
    val isValid: Boolean get() = turnTelemetryValid
    val turnTelemetryValid: Boolean get() = blink != null && speedKmh != null && gear != null
    val rearTelemetryValid: Boolean get() = gear != null
}

data class BlindSpotTelemetryState(
    val ageMs: Long,
    val turnMustClose: Boolean,
    val rearMustClose: Boolean,
    val lastValid: BlindSpotSample?,
)

class BlindSpotTelemetryGate {
    private var lastTurnValidAt = 0L
    private var lastRearValidAt = 0L
    private var armedAt = 0L
    private var readFailures = 0
    private var lastValid: BlindSpotSample? = null

    fun reset(now: Long) {
        lastTurnValidAt = 0L
        lastRearValidAt = 0L
        armedAt = now
        readFailures = 0
        lastValid = null
    }

    fun onSample(sample: BlindSpotSample?, now: Long): BlindSpotTelemetryState {
        if (sample == null) readFailures++ else readFailures = 0
        if (sample?.turnTelemetryValid == true) {
            lastTurnValidAt = now
            lastValid = sample
        }
        if (sample?.rearTelemetryValid == true) lastRearValidAt = now
        val ageMs = if (lastTurnValidAt == 0L) Long.MAX_VALUE else now - lastTurnValidAt
        val turnSilentFor = if (lastTurnValidAt == 0L) now - armedAt else now - lastTurnValidAt
        val rearSilentFor = if (lastRearValidAt == 0L) now - armedAt else now - lastRearValidAt
        return BlindSpotTelemetryState(
            ageMs = ageMs,
            turnMustClose = readFailures >= BLIND_SPOT_MAX_READ_FAILURES ||
                turnSilentFor > BLIND_SPOT_TELEMETRY_LOST_MS,
            rearMustClose = readFailures >= BLIND_SPOT_MAX_READ_FAILURES ||
                rearSilentFor > BLIND_SPOT_TELEMETRY_LOST_MS,
            lastValid = lastValid,
        )
    }
}

private const val BLINK_LEFT = 2
private const val BLINK_RIGHT = 4
private const val BLIND_SPOT_BSD_MIN_RAW = 0
private const val BLIND_SPOT_BSD_ACTIVE_RAW = 2
