package com.byd.wjx.led.mafu

internal object MafuReconnectPolicy {
    const val MAX_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 5_000L
    const val CONNECT_SETTLE_MS = 12_000L

    fun canSchedule(
        manualDisconnect: Boolean,
        preferences: MafuPreferenceSnapshot,
        disconnectedAddress: String,
    ): Boolean = canAttempt(manualDisconnect, preferences) &&
        preferences.deviceAddress.equals(disconnectedAddress, ignoreCase = true)

    fun canAttempt(
        manualDisconnect: Boolean,
        preferences: MafuPreferenceSnapshot,
    ): Boolean = !manualDisconnect && preferences.enabled && preferences.deviceAddress.isNotBlank()

    fun stillTargets(
        preferences: MafuPreferenceSnapshot,
        expectedAddress: String,
    ): Boolean = canAttempt(manualDisconnect = false, preferences) &&
        preferences.deviceAddress.equals(expectedAddress, ignoreCase = true)
}
