package com.byd.wjx.ui.lyrics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.lyrics.LyricSettings
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Settings for the desktop and instrument-cluster lyric overlays. */
@Composable
fun LyricDisplayScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activationManager = remember(context.applicationContext) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ClusterEntryPoint::class.java)
            .activationManager()
    }
    val lyricSettings = remember { LyricSettings(context) }
    val panelPreferences = remember(context.applicationContext) {
        LyricDisplayPanelPreferences(context)
    }

    var lyricDesktopEnabled by remember { mutableStateOf(lyricSettings.desktopEnabled) }
    var lyricAllowOtherApps by remember { mutableStateOf(lyricSettings.allowOnOtherApps) }
    var lyricClusterEnabled by remember { mutableStateOf(lyricSettings.clusterEnabled) }
    var lyricControlsExpanded by remember {
        mutableStateOf(
            panelPreferences.initialExpanded(
                LyricDisplayPanel.LYRIC,
                lyricSettings.desktopEnabled || lyricSettings.clusterEnabled,
            ),
        )
    }
    var lyricFontScale by remember { mutableFloatStateOf(lyricSettings.fontScale) }
    var lyricClusterFontScale by remember { mutableFloatStateOf(lyricSettings.clusterFontScale) }
    var lyricClusterLineCount by remember { mutableIntStateOf(lyricSettings.clusterLineCount) }
    var lyricClusterWidthScale by remember { mutableFloatStateOf(lyricSettings.clusterWidthScale) }
    var lyricClusterAlpha by remember { mutableFloatStateOf(lyricSettings.clusterAlpha) }
    var lyricClusterX by remember { mutableFloatStateOf(lyricSettings.clusterXPct.toFloat()) }
    var lyricClusterY by remember { mutableFloatStateOf(lyricSettings.clusterYPct.toFloat()) }
    var lyricSyncOffset by remember { mutableFloatStateOf(lyricSettings.syncOffsetMs.toFloat()) }
    var lyricFontStyleId by remember { mutableStateOf(lyricSettings.fontStyleId) }
    var lyricMotionPresetId by remember { mutableStateOf(lyricSettings.motionPresetId) }
    var lyricColorPresetId by remember { mutableStateOf(lyricSettings.colorPresetId) }
    var lyricKaraokeEnabled by remember { mutableStateOf(lyricSettings.karaokeEnabled) }
    var lyricFeatureAllowed by remember {
        mutableStateOf(activationManager.canUse(PremiumFeature.DESKTOP_LYRICS))
    }
    var selectedPreview by remember { mutableStateOf(LyricPreviewType.DesktopLyric) }

    fun setLyricControlsExpanded(value: Boolean) {
        lyricControlsExpanded = value
        panelPreferences.setExpanded(LyricDisplayPanel.LYRIC, value)
    }

    fun refreshLyricFeatureAllowed(): Boolean {
        lyricFeatureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
        return lyricFeatureAllowed
    }

    fun showLyricActivationRequired() {
        Toast.makeText(context, "桌面歌词需要激活后使用", Toast.LENGTH_SHORT).show()
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun setDesktopLyricEnabled(enabled: Boolean) {
        if (enabled && !refreshLyricFeatureAllowed()) {
            lyricSettings.desktopEnabled = false
            lyricDesktopEnabled = false
            showLyricActivationRequired()
            return
        }
        if (enabled && !Settings.canDrawOverlays(context)) {
            lyricSettings.desktopEnabled = false
            lyricDesktopEnabled = false
            requestOverlayPermission()
            return
        }
        lyricSettings.desktopEnabled = enabled
        lyricDesktopEnabled = lyricSettings.desktopEnabled
        setLyricControlsExpanded(lyricSettings.desktopEnabled || lyricSettings.clusterEnabled)
    }

    fun setClusterLyricEnabled(enabled: Boolean) {
        if (enabled && !refreshLyricFeatureAllowed()) {
            lyricSettings.clusterEnabled = false
            lyricClusterEnabled = false
            showLyricActivationRequired()
            return
        }
        if (enabled && !Settings.canDrawOverlays(context)) {
            lyricSettings.clusterEnabled = false
            lyricClusterEnabled = false
            requestOverlayPermission()
            return
        }
        lyricSettings.clusterEnabled = enabled
        lyricClusterEnabled = lyricSettings.clusterEnabled
        setLyricControlsExpanded(lyricSettings.desktopEnabled || lyricSettings.clusterEnabled)
    }

    fun nudgeClusterLyric(dx: Int, dy: Int) {
        lyricSettings.clusterXPct = (lyricSettings.clusterXPct + dx).coerceIn(0, 100)
        lyricSettings.clusterYPct = (lyricSettings.clusterYPct + dy).coerceIn(0, 100)
        lyricClusterX = lyricSettings.clusterXPct.toFloat()
        lyricClusterY = lyricSettings.clusterYPct.toFloat()
    }

    val lyricDesktopActive = lyricFeatureAllowed && lyricDesktopEnabled
    val lyricClusterActive = lyricFeatureAllowed && lyricClusterEnabled

    LaunchedEffect(activationManager) {
        while (true) {
            lyricFeatureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
            delay(5_000L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lyricFeatureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
                lyricDesktopEnabled = lyricSettings.desktopEnabled
                lyricAllowOtherApps = lyricSettings.allowOnOtherApps
                lyricClusterEnabled = lyricSettings.clusterEnabled
                lyricFontScale = lyricSettings.fontScale
                lyricClusterFontScale = lyricSettings.clusterFontScale
                lyricClusterLineCount = lyricSettings.clusterLineCount
                lyricClusterWidthScale = lyricSettings.clusterWidthScale
                lyricClusterAlpha = lyricSettings.clusterAlpha
                lyricClusterX = lyricSettings.clusterXPct.toFloat()
                lyricClusterY = lyricSettings.clusterYPct.toFloat()
                lyricSyncOffset = lyricSettings.syncOffsetMs.toFloat()
                lyricFontStyleId = lyricSettings.fontStyleId
                lyricMotionPresetId = lyricSettings.motionPresetId
                lyricColorPresetId = lyricSettings.colorPresetId
                lyricKaraokeEnabled = lyricSettings.karaokeEnabled
                if (!lyricFeatureAllowed || (!lyricSettings.desktopEnabled && !lyricSettings.clusterEnabled)) {
                    setLyricControlsExpanded(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 58.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LyricDisplayHeader()

        LyricPreviewStage(
            selected = selectedPreview,
            onSelected = { selectedPreview = it },
            desktopLyricEnabled = lyricDesktopActive,
            clusterLyricEnabled = lyricClusterActive,
            modifier = Modifier
                .fillMaxWidth()
                .height(406.dp),
        )

        LyricQuickControlPanel(
            selected = selectedPreview,
            desktopLyricEnabled = lyricDesktopActive,
            clusterLyricEnabled = lyricClusterActive,
            desktopLyricScale = lyricFontScale,
            clusterLyricScale = lyricClusterFontScale,
            clusterLyricAlpha = lyricClusterAlpha,
            onToggle = { enabled ->
                when (selectedPreview) {
                    LyricPreviewType.DesktopLyric -> setDesktopLyricEnabled(enabled)
                    LyricPreviewType.ClusterLyric -> setClusterLyricEnabled(enabled)
                }
            },
            onPrimaryValueChange = { value ->
                when (selectedPreview) {
                    LyricPreviewType.DesktopLyric -> {
                        lyricSettings.fontScale = value
                        lyricFontScale = lyricSettings.fontScale
                    }
                    LyricPreviewType.ClusterLyric -> {
                        lyricSettings.clusterFontScale = value
                        lyricClusterFontScale = lyricSettings.clusterFontScale
                    }
                }
            },
            onSecondaryValueChange = { value ->
                if (selectedPreview == LyricPreviewType.ClusterLyric) {
                    lyricSettings.clusterAlpha = value
                    lyricClusterAlpha = lyricSettings.clusterAlpha
                }
            },
            onReset = {
                when (selectedPreview) {
                    LyricPreviewType.DesktopLyric -> lyricSettings.resetDesktopPosition()
                    LyricPreviewType.ClusterLyric -> {
                        lyricSettings.resetClusterPosition()
                        lyricClusterX = lyricSettings.clusterXPct.toFloat()
                        lyricClusterY = lyricSettings.clusterYPct.toFloat()
                    }
                }
            },
        )

        LyricSettingsPanel(
            state = LyricPanelState(
                selected = selectedPreview,
                featureAllowed = lyricFeatureAllowed,
                desktopActive = lyricDesktopActive,
                clusterActive = lyricClusterActive,
                expanded = lyricControlsExpanded,
                allowOtherApps = lyricAllowOtherApps,
                desktopFontScale = lyricFontScale,
                clusterLineCount = lyricClusterLineCount,
                clusterFontScale = lyricClusterFontScale,
                clusterWidthScale = lyricClusterWidthScale,
                clusterAlpha = lyricClusterAlpha,
                clusterX = lyricClusterX,
                clusterY = lyricClusterY,
                syncOffset = lyricSyncOffset,
                karaokeEnabled = lyricKaraokeEnabled,
                fontStyleId = lyricFontStyleId,
                motionPresetId = lyricMotionPresetId,
                colorPresetId = lyricColorPresetId,
            ),
            actions = LyricPanelActions(
                onDesktopEnabledChange = ::setDesktopLyricEnabled,
                onClusterEnabledChange = ::setClusterLyricEnabled,
                onExpandedChange = ::setLyricControlsExpanded,
                onAllowOtherAppsChange = {
                    lyricSettings.allowOnOtherApps = it
                    lyricAllowOtherApps = lyricSettings.allowOnOtherApps
                },
                onOpenPlaybackPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onResetPosition = {
                    when (selectedPreview) {
                        LyricPreviewType.DesktopLyric -> lyricSettings.resetDesktopPosition()
                        LyricPreviewType.ClusterLyric -> {
                            lyricSettings.resetClusterPosition()
                            lyricClusterX = lyricSettings.clusterXPct.toFloat()
                            lyricClusterY = lyricSettings.clusterYPct.toFloat()
                        }
                    }
                },
                onNudgeCluster = ::nudgeClusterLyric,
                onDesktopFontScaleChange = {
                    lyricSettings.fontScale = it
                    lyricFontScale = lyricSettings.fontScale
                },
                onClusterLineCountChange = {
                    lyricSettings.clusterLineCount = it
                    lyricClusterLineCount = lyricSettings.clusterLineCount
                },
                onClusterFontScaleChange = {
                    lyricSettings.clusterFontScale = it
                    lyricClusterFontScale = lyricSettings.clusterFontScale
                },
                onClusterWidthScaleChange = {
                    lyricSettings.clusterWidthScale = it
                    lyricClusterWidthScale = lyricSettings.clusterWidthScale
                },
                onClusterAlphaChange = {
                    lyricSettings.clusterAlpha = it
                    lyricClusterAlpha = lyricSettings.clusterAlpha
                },
                onClusterXChange = {
                    lyricSettings.clusterXPct = it.roundToInt()
                    lyricClusterX = lyricSettings.clusterXPct.toFloat()
                },
                onClusterYChange = {
                    lyricSettings.clusterYPct = it.roundToInt()
                    lyricClusterY = lyricSettings.clusterYPct.toFloat()
                },
                onSyncOffsetChange = {
                    val snapped = (it / 50f).roundToInt() * 50
                    lyricSettings.syncOffsetMs = snapped
                    lyricSyncOffset = lyricSettings.syncOffsetMs.toFloat()
                },
                onKaraokeChange = {
                    lyricSettings.karaokeEnabled = it
                    lyricKaraokeEnabled = lyricSettings.karaokeEnabled
                },
                onFontStyleChange = {
                    lyricSettings.fontStyleId = it
                    lyricFontStyleId = lyricSettings.fontStyleId
                },
                onMotionPresetChange = {
                    lyricSettings.motionPresetId = it
                    lyricMotionPresetId = lyricSettings.motionPresetId
                },
                onColorPresetChange = {
                    lyricSettings.colorPresetId = it
                    lyricColorPresetId = lyricSettings.colorPresetId
                },
            ),
        )
    }
}
