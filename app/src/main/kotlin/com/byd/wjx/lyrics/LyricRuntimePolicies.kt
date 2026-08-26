package com.byd.wjx.lyrics

internal data class LyricOverlayTargets(
    val desktop: Boolean,
    val cluster: Boolean,
) {
    val any: Boolean
        get() = desktop || cluster
}

internal fun resolveLyricOverlayTargets(
    lyricsAllowed: Boolean,
    desktopEnabled: Boolean,
    clusterEnabled: Boolean,
    allowOnOtherApps: Boolean,
    homeForeground: Boolean,
    cameraActive: Boolean,
): LyricOverlayTargets = LyricOverlayTargets(
    desktop = lyricsAllowed && desktopEnabled && shouldKeepDesktopLyricsVisible(
        allowOnOtherApps = allowOnOtherApps,
        homeForeground = homeForeground,
        cameraActive = cameraActive,
    ),
    cluster = lyricsAllowed && clusterEnabled,
)

internal fun shouldKeepDesktopLyricsVisible(
    allowOnOtherApps: Boolean,
    homeForeground: Boolean,
    cameraActive: Boolean,
): Boolean = allowOnOtherApps || homeForeground || cameraActive

internal object LyricLoadRetryPolicy {
    private val retryDelaysMs = longArrayOf(3_000L, 10_000L, 30_000L, 90_000L)

    fun delayAfterFailure(failedAttempts: Int): Long? =
        retryDelaysMs.getOrNull(failedAttempts - 1)
}

internal object LyricPermissionRetryPolicy {
    private val retryDelaysMs = longArrayOf(5_000L, 15_000L, 30_000L)

    fun delayAfterFailure(failedAttempts: Int): Long? =
        retryDelaysMs.getOrNull(failedAttempts - 1)
}
