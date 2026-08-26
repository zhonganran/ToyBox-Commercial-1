package com.byd.wjx.led.ipixelfa

internal object IpixelFaReconnectPolicy {
    const val CONNECT_SETTLE_MS = 5_000L
    private val delaysMs = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    fun delayForAttempt(attempt: Int): Long = delaysMs[attempt.coerceIn(0, delaysMs.lastIndex)]

    fun canSchedule(
        manualDisconnect: Boolean,
        preferences: IpixelFaPreferenceSnapshot,
        disconnectedAddress: String,
    ): Boolean = canAttempt(manualDisconnect, preferences) &&
        preferences.deviceAddress.equals(disconnectedAddress, ignoreCase = true)

    fun canAttempt(
        manualDisconnect: Boolean,
        preferences: IpixelFaPreferenceSnapshot,
    ): Boolean = !manualDisconnect && preferences.enabled && preferences.deviceAddress.isNotBlank()

    fun stillTargets(
        preferences: IpixelFaPreferenceSnapshot,
        expectedAddress: String,
    ): Boolean = preferences.enabled &&
        preferences.deviceAddress.isNotBlank() &&
        preferences.deviceAddress.equals(expectedAddress, ignoreCase = true)
}
