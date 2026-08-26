package com.byd.wjx.desktopcard

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.clustercard.ClusterCardRepository
import com.byd.wjx.clustercard.ClusterCardSlot
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.lyrics.LyricSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Owns desktop-card toggles and state observation for lyrics and both cluster cards. */
class DesktopQuickCardFeatureController @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val clusterCardRepository: ClusterCardRepository,
) {
    private val appContext = context.applicationContext
    private val lyricSettings = LyricSettings(appContext)
    private val lyricPrefs = appContext.getSharedPreferences(LyricSettings.PREFS_NAME, Context.MODE_PRIVATE)
    private var listener: ((DesktopQuickCardFeatureState) -> Unit)? = null
    private var lyricPreferenceListenerRegistered = false
    private var clusterCardStateJob: Job? = null

    private val lyricPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        publishCurrentState()
    }

    fun start(scope: CoroutineScope, onStateChanged: (DesktopQuickCardFeatureState) -> Unit) {
        if (listener != null) return
        listener = onStateChanged
        lyricPrefs.registerOnSharedPreferenceChangeListener(lyricPreferenceListener)
        lyricPreferenceListenerRegistered = true
        clusterCardStateJob = scope.launch {
            combine(clusterCardRepository.config, clusterCardRepository.config2) { primary, secondary ->
                currentState(
                    primaryEnabled = primary.enabled,
                    secondaryEnabled = secondary.enabled,
                )
            }.collect(onStateChanged)
        }
        publishCurrentState()
    }

    fun stop() {
        if (lyricPreferenceListenerRegistered) {
            lyricPrefs.unregisterOnSharedPreferenceChangeListener(lyricPreferenceListener)
            lyricPreferenceListenerRegistered = false
        }
        clusterCardStateJob?.cancel()
        clusterCardStateJob = null
        listener = null
    }

    fun currentState(): DesktopQuickCardFeatureState = currentState(
        primaryEnabled = clusterCardRepository.config.value.enabled,
        secondaryEnabled = clusterCardRepository.config2.value.enabled,
    )

    fun toggle(item: DesktopQuickCardCommand): DesktopQuickCardFeatureResult = when (item.kind) {
        DesktopQuickCardCommandKind.DESKTOP_LYRICS -> toggleLyrics(cluster = false)
        DesktopQuickCardCommandKind.CLUSTER_LYRICS -> toggleLyrics(cluster = true)
        DesktopQuickCardCommandKind.CLUSTER_CARD -> toggleClusterCard(item.payload)
        else -> DesktopQuickCardFeatureResult(
            success = false,
            state = currentState(),
            message = "该功能不属于桌面组件开关",
        )
    }

    private fun toggleLyrics(cluster: Boolean): DesktopQuickCardFeatureResult {
        val currentlyEnabled = if (cluster) lyricSettings.clusterEnabled else lyricSettings.desktopEnabled
        val enabled = !currentlyEnabled
        val label = if (cluster) "仪表歌词" else "桌面歌词"
        val blockReason = lyricToggleBlockReason(
            enabling = enabled,
            featureAllowed = !enabled || activationManager.canUse(PremiumFeature.DESKTOP_LYRICS),
            overlayPermissionGranted = Settings.canDrawOverlays(appContext),
            label = label,
        )
        if (blockReason != null) {
            WjxFileLogger.w(TAG, "$label toggle blocked: $blockReason")
            return DesktopQuickCardFeatureResult(false, currentState(), blockReason)
        }

        if (cluster) lyricSettings.clusterEnabled = enabled else lyricSettings.desktopEnabled = enabled
        val state = currentState()
        WjxFileLogger.i(TAG, "$label enabled=$enabled from desktop card")
        return DesktopQuickCardFeatureResult(
            success = true,
            state = state,
            message = if (enabled) "${label}已开启" else "${label}已关闭",
        )
    }

    private fun toggleClusterCard(payload: String): DesktopQuickCardFeatureResult {
        val slot = clusterCardSlot(payload)
        val currentConfig = clusterCardRepository.current(slot)
        val enabled = !currentConfig.enabled
        if (enabled && !Settings.canDrawOverlays(appContext)) {
            val message = "请先允许悬浮窗权限"
            WjxFileLogger.w(TAG, "${slot.label} toggle blocked: overlay permission missing")
            return DesktopQuickCardFeatureResult(false, currentState(), message)
        }

        clusterCardRepository.setEnabled(slot, enabled)
        val state = currentState()
        val hasMetrics = clusterCardRepository.current(slot).metricIds.isNotEmpty()
        val message = when {
            !enabled -> "${slot.label}已关闭"
            hasMetrics -> "${slot.label}已开启"
            else -> "${slot.label}已开启，请先添加显示项目"
        }
        WjxFileLogger.i(TAG, "${slot.label} enabled=$enabled from desktop card")
        return DesktopQuickCardFeatureResult(true, state, message)
    }

    private fun publishCurrentState() {
        listener?.invoke(currentState())
    }

    private fun currentState(
        primaryEnabled: Boolean,
        secondaryEnabled: Boolean,
    ) = DesktopQuickCardFeatureState(
        clusterCardEnabled = primaryEnabled,
        clusterCard2Enabled = secondaryEnabled,
        desktopLyricsEnabled = lyricSettings.desktopEnabled,
        clusterLyricsEnabled = lyricSettings.clusterEnabled,
    )

    private companion object {
        const val TAG = "DesktopQuickCardFeature"
    }
}

data class DesktopQuickCardFeatureState(
    val clusterCardEnabled: Boolean,
    val clusterCard2Enabled: Boolean,
    val desktopLyricsEnabled: Boolean,
    val clusterLyricsEnabled: Boolean,
)

data class DesktopQuickCardFeatureResult(
    val success: Boolean,
    val state: DesktopQuickCardFeatureState,
    val message: String,
)

internal fun clusterCardSlot(payload: String): ClusterCardSlot =
    if (payload == "secondary") ClusterCardSlot.SECONDARY else ClusterCardSlot.PRIMARY

internal fun lyricToggleBlockReason(
    enabling: Boolean,
    featureAllowed: Boolean,
    overlayPermissionGranted: Boolean,
    label: String,
): String? = when {
    !enabling -> null
    !featureAllowed -> "${label}需要激活后使用"
    !overlayPermissionGranted -> "请先允许悬浮窗权限"
    else -> null
}
