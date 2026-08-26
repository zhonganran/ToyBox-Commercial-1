package com.byd.wjx.ui.lyrics

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.lyrics.LyricSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class LyricDisplayUiState(
    val featureAllowed: Boolean = false,
    val desktopEnabled: Boolean = false,
    val clusterEnabled: Boolean = false,
    val expanded: Boolean = false,
    val allowOtherApps: Boolean = false,
    val desktopAdjustment: LyricAdjustmentState = defaultLyricAdjustmentState(),
    val clusterAdjustment: LyricAdjustmentState = defaultLyricAdjustmentState(),
    val clusterX: Float = 50f,
    val clusterY: Float = 50f,
) {
    val anyConfigured: Boolean
        get() = desktopEnabled || clusterEnabled

    fun panelState(): LyricPanelState = LyricPanelState(
        featureAllowed = featureAllowed,
        desktopActive = featureAllowed && desktopEnabled,
        clusterActive = featureAllowed && clusterEnabled,
        expanded = expanded,
        allowOtherApps = allowOtherApps,
        desktopAdjustment = desktopAdjustment,
        clusterAdjustment = clusterAdjustment,
        clusterX = clusterX,
        clusterY = clusterY,
    )
}

internal enum class LyricModeChangeResult {
    APPLIED,
    ACTIVATION_REQUIRED,
    OVERLAY_PERMISSION_REQUIRED,
}

internal fun decideLyricModeChange(
    enabled: Boolean,
    featureAllowed: Boolean,
    overlayPermissionGranted: Boolean,
): LyricModeChangeResult = when {
    !enabled -> LyricModeChangeResult.APPLIED
    !featureAllowed -> LyricModeChangeResult.ACTIVATION_REQUIRED
    !overlayPermissionGranted -> LyricModeChangeResult.OVERLAY_PERMISSION_REQUIRED
    else -> LyricModeChangeResult.APPLIED
}

@HiltViewModel
class LyricDisplayViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val settings = LyricSettings(appContext)
    private val panelPreferences = LyricDisplayPanelPreferences(appContext)
    private val lyricPreferences = appContext.getSharedPreferences(LyricSettings.PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(readState(featureAllowed = canUseLyrics()))
    internal val uiState: StateFlow<LyricDisplayUiState> = _uiState.asStateFlow()
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshSettings()
    }

    init {
        lyricPreferences.registerOnSharedPreferenceChangeListener(settingsListener)
        viewModelScope.launch {
            while (isActive) {
                delay(ACTIVATION_REFRESH_INTERVAL_MS)
                refreshAccess()
            }
        }
    }

    fun refresh() {
        _uiState.value = readState(featureAllowed = canUseLyrics())
    }

    internal fun setModeEnabled(
        target: LyricAdjustmentTarget,
        enabled: Boolean,
        overlayPermissionGranted: Boolean,
    ): LyricModeChangeResult {
        val featureAllowed = canUseLyrics()
        val result = decideLyricModeChange(
            enabled = enabled,
            featureAllowed = featureAllowed,
            overlayPermissionGranted = overlayPermissionGranted,
        )
        writeEnabled(target, enabled && result == LyricModeChangeResult.APPLIED)
        val next = readState(featureAllowed)
        setExpanded(next.anyConfigured && featureAllowed)
        return result
    }

    internal fun setExpanded(expanded: Boolean) {
        val allowed = expanded && _uiState.value.featureAllowed && _uiState.value.anyConfigured
        panelPreferences.setExpanded(LyricDisplayPanel.LYRIC, allowed)
        _uiState.value = readState(_uiState.value.featureAllowed)
    }

    internal fun setAllowOtherApps(enabled: Boolean) {
        settings.allowOnOtherApps = enabled
        refreshSettings()
    }

    internal fun resetClusterPosition() {
        settings.resetClusterPosition()
        refreshSettings()
    }

    internal fun setLineCount(target: LyricAdjustmentTarget, value: Int) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopLineCount = value
            LyricAdjustmentTarget.CLUSTER -> settings.clusterLineCount = value
        }
        refreshSettings()
    }

    internal fun setFontScale(target: LyricAdjustmentTarget, value: Float) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopFontScale = value
            LyricAdjustmentTarget.CLUSTER -> settings.clusterFontScale = value
        }
        refreshSettings()
    }

    internal fun setTextColor(target: LyricAdjustmentTarget, value: Int) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopTextColor = value
            LyricAdjustmentTarget.CLUSTER -> settings.clusterTextColor = value
        }
        refreshSettings()
    }

    internal fun setHighlightColor(target: LyricAdjustmentTarget, value: Int) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopHighlightColor = value
            LyricAdjustmentTarget.CLUSTER -> settings.clusterHighlightColor = value
        }
        refreshSettings()
    }

    internal fun setFontStyle(target: LyricAdjustmentTarget, value: String) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopFontStyleId = value
            LyricAdjustmentTarget.CLUSTER -> settings.clusterFontStyleId = value
        }
        refreshSettings()
    }

    internal fun setMotionPreset(target: LyricAdjustmentTarget, value: String) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopMotionPresetId = value
            LyricAdjustmentTarget.CLUSTER -> settings.clusterMotionPresetId = value
        }
        refreshSettings()
    }

    internal fun setClusterX(value: Float) {
        settings.clusterXPct = value.roundToInt()
        refreshSettings()
    }

    internal fun setClusterY(value: Float) {
        settings.clusterYPct = value.roundToInt()
        refreshSettings()
    }

    internal fun setSyncOffset(target: LyricAdjustmentTarget, value: Float) {
        val snapped = (value / SYNC_OFFSET_STEP_MS).roundToInt() * SYNC_OFFSET_STEP_MS.toInt()
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopSyncOffsetMs = snapped
            LyricAdjustmentTarget.CLUSTER -> settings.clusterSyncOffsetMs = snapped
        }
        refreshSettings()
    }

    override fun onCleared() {
        lyricPreferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onCleared()
    }

    private fun writeEnabled(target: LyricAdjustmentTarget, enabled: Boolean) {
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> settings.desktopEnabled = enabled
            LyricAdjustmentTarget.CLUSTER -> settings.clusterEnabled = enabled
        }
    }

    private fun refreshAccess() {
        val featureAllowed = canUseLyrics()
        if (featureAllowed != _uiState.value.featureAllowed) {
            _uiState.value = readState(featureAllowed)
        }
    }

    private fun refreshSettings() {
        _uiState.value = readState(_uiState.value.featureAllowed)
    }

    private fun readState(featureAllowed: Boolean): LyricDisplayUiState {
        val desktopEnabled = settings.desktopEnabled
        val clusterEnabled = settings.clusterEnabled
        val anyConfigured = desktopEnabled || clusterEnabled
        return LyricDisplayUiState(
            featureAllowed = featureAllowed,
            desktopEnabled = desktopEnabled,
            clusterEnabled = clusterEnabled,
            expanded = featureAllowed && panelPreferences.initialExpanded(
                LyricDisplayPanel.LYRIC,
                anyConfigured,
            ),
            allowOtherApps = settings.allowOnOtherApps,
            desktopAdjustment = settings.adjustmentState(LyricAdjustmentTarget.DESKTOP),
            clusterAdjustment = settings.adjustmentState(LyricAdjustmentTarget.CLUSTER),
            clusterX = settings.clusterXPct.toFloat(),
            clusterY = settings.clusterYPct.toFloat(),
        )
    }

    private fun LyricSettings.adjustmentState(target: LyricAdjustmentTarget): LyricAdjustmentState =
        when (target) {
            LyricAdjustmentTarget.DESKTOP -> LyricAdjustmentState(
                lineCount = desktopLineCount,
                fontScale = desktopFontScale,
                textColor = desktopTextColor,
                highlightColor = desktopHighlightColor,
                fontStyleId = desktopFontStyleId,
                motionPresetId = desktopMotionPresetId,
                syncOffset = desktopSyncOffsetMs.toFloat(),
            )
            LyricAdjustmentTarget.CLUSTER -> LyricAdjustmentState(
                lineCount = clusterLineCount,
                fontScale = clusterFontScale,
                textColor = clusterTextColor,
                highlightColor = clusterHighlightColor,
                fontStyleId = clusterFontStyleId,
                motionPresetId = clusterMotionPresetId,
                syncOffset = clusterSyncOffsetMs.toFloat(),
            )
        }

    private fun canUseLyrics(): Boolean = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)

    private companion object {
        const val ACTIVATION_REFRESH_INTERVAL_MS = 5_000L
        const val SYNC_OFFSET_STEP_MS = 50f
    }
}

private fun defaultLyricAdjustmentState(): LyricAdjustmentState = LyricAdjustmentState(
    lineCount = LyricSettings.LINE_COUNT_DEFAULT,
    fontScale = 1f,
    textColor = LyricSettings.DEFAULT_TEXT_COLOR,
    highlightColor = LyricSettings.DEFAULT_HIGHLIGHT_COLOR,
    fontStyleId = "soft",
    motionPresetId = "soft",
    syncOffset = 0f,
)
