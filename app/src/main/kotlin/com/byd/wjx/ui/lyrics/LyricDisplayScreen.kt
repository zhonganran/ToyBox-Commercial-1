package com.byd.wjx.ui.lyrics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** Shared settings for the desktop and instrument-cluster lyric overlays. */
@Composable
fun LyricDisplayScreen(
    showHeader: Boolean = true,
    scrollEnabled: Boolean = true,
    onEnabledStateChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activationManager = remember(context.applicationContext) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ClusterEntryPoint::class.java)
            .activationManager()
    }
    val lyricSettings = remember(context.applicationContext) { LyricSettings(context.applicationContext) }
    val panelPreferences = remember(context.applicationContext) { LyricDisplayPanelPreferences(context) }

    var desktopEnabled by remember { mutableStateOf(lyricSettings.desktopEnabled) }
    var clusterEnabled by remember { mutableStateOf(lyricSettings.clusterEnabled) }
    var expanded by remember {
        mutableStateOf(
            panelPreferences.initialExpanded(
                LyricDisplayPanel.LYRIC,
                lyricSettings.desktopEnabled || lyricSettings.clusterEnabled,
            ),
        )
    }
    var allowOtherApps by remember { mutableStateOf(lyricSettings.allowOnOtherApps) }
    var desktopTouchThrough by remember { mutableStateOf(lyricSettings.desktopTouchThrough) }
    var sharedLineCount by remember { mutableIntStateOf(lyricSettings.sharedLineCount) }
    var sharedFontScale by remember { mutableFloatStateOf(lyricSettings.sharedFontScale) }
    var sharedTextColor by remember { mutableIntStateOf(lyricSettings.sharedTextColor) }
    var sharedHighlightColor by remember { mutableIntStateOf(lyricSettings.sharedHighlightColor) }
    var sharedFontStyleId by remember { mutableStateOf(lyricSettings.sharedFontStyleId) }
    var sharedMotionPresetId by remember { mutableStateOf(lyricSettings.sharedMotionPresetId) }
    var clusterWidthScale by remember { mutableFloatStateOf(lyricSettings.clusterWidthScale) }
    var clusterX by remember { mutableFloatStateOf(lyricSettings.clusterXPct.toFloat()) }
    var clusterY by remember { mutableFloatStateOf(lyricSettings.clusterYPct.toFloat()) }
    var syncOffset by remember { mutableFloatStateOf(lyricSettings.syncOffsetMs.toFloat()) }
    var featureAllowed by remember {
        mutableStateOf(activationManager.canUse(PremiumFeature.DESKTOP_LYRICS))
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        panelPreferences.setExpanded(LyricDisplayPanel.LYRIC, value)
    }

    fun notifyEnabledState() {
        onEnabledStateChanged?.invoke(desktopEnabled || clusterEnabled)
    }

    fun refreshState() {
        featureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
        desktopEnabled = lyricSettings.desktopEnabled
        clusterEnabled = lyricSettings.clusterEnabled
        allowOtherApps = lyricSettings.allowOnOtherApps
        desktopTouchThrough = lyricSettings.desktopTouchThrough
        sharedLineCount = lyricSettings.sharedLineCount
        sharedFontScale = lyricSettings.sharedFontScale
        sharedTextColor = lyricSettings.sharedTextColor
        sharedHighlightColor = lyricSettings.sharedHighlightColor
        sharedFontStyleId = lyricSettings.sharedFontStyleId
        sharedMotionPresetId = lyricSettings.sharedMotionPresetId
        clusterWidthScale = lyricSettings.clusterWidthScale
        clusterX = lyricSettings.clusterXPct.toFloat()
        clusterY = lyricSettings.clusterYPct.toFloat()
        syncOffset = lyricSettings.syncOffsetMs.toFloat()
        notifyEnabledState()
        if (!featureAllowed || (!desktopEnabled && !clusterEnabled)) setExpanded(false)
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun setModeEnabled(desktop: Boolean, enabled: Boolean) {
        featureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
        if (enabled && !featureAllowed) {
            Toast.makeText(context, "歌词组件需要激活后使用", Toast.LENGTH_SHORT).show()
            if (desktop) lyricSettings.desktopEnabled = false else lyricSettings.clusterEnabled = false
            refreshState()
            return
        }
        if (enabled && !Settings.canDrawOverlays(context)) {
            if (desktop) lyricSettings.desktopEnabled = false else lyricSettings.clusterEnabled = false
            refreshState()
            requestOverlayPermission()
            return
        }
        if (desktop) lyricSettings.desktopEnabled = enabled else lyricSettings.clusterEnabled = enabled
        refreshState()
        setExpanded(desktopEnabled || clusterEnabled)
    }

    LaunchedEffect(lyricSettings) {
        refreshState()
    }

    LaunchedEffect(activationManager) {
        while (true) {
            featureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
            delay(5_000L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollModifier = if (scrollEnabled) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .then(scrollModifier)
            .padding(horizontal = 58.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeader) LyricDisplayHeader()

        LyricSettingsPanel(
            state = LyricPanelState(
                featureAllowed = featureAllowed,
                desktopActive = featureAllowed && desktopEnabled,
                clusterActive = featureAllowed && clusterEnabled,
                expanded = expanded,
                allowOtherApps = allowOtherApps,
                desktopTouchThrough = desktopTouchThrough,
                sharedLineCount = sharedLineCount,
                sharedFontScale = sharedFontScale,
                sharedTextColor = sharedTextColor,
                sharedHighlightColor = sharedHighlightColor,
                sharedFontStyleId = sharedFontStyleId,
                sharedMotionPresetId = sharedMotionPresetId,
                clusterWidthScale = clusterWidthScale,
                clusterX = clusterX,
                clusterY = clusterY,
                syncOffset = syncOffset,
            ),
            actions = LyricPanelActions(
                onDesktopEnabledChange = { setModeEnabled(desktop = true, enabled = it) },
                onClusterEnabledChange = { setModeEnabled(desktop = false, enabled = it) },
                onExpandedChange = ::setExpanded,
                onAllowOtherAppsChange = {
                    lyricSettings.allowOnOtherApps = it
                    allowOtherApps = lyricSettings.allowOnOtherApps
                },
                onDesktopTouchThroughChange = {
                    lyricSettings.desktopTouchThrough = it
                    desktopTouchThrough = lyricSettings.desktopTouchThrough
                },
                onOpenPlaybackPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onResetDesktopPosition = { lyricSettings.resetDesktopPosition() },
                onResetClusterPosition = {
                    lyricSettings.resetClusterPosition()
                    clusterX = lyricSettings.clusterXPct.toFloat()
                    clusterY = lyricSettings.clusterYPct.toFloat()
                },
                onSharedLineCountChange = {
                    lyricSettings.sharedLineCount = it
                    sharedLineCount = lyricSettings.sharedLineCount
                },
                onSharedFontScaleChange = {
                    lyricSettings.sharedFontScale = it
                    sharedFontScale = lyricSettings.sharedFontScale
                },
                onSharedTextColorChange = {
                    lyricSettings.sharedTextColor = it
                    sharedTextColor = lyricSettings.sharedTextColor
                },
                onSharedHighlightColorChange = {
                    lyricSettings.sharedHighlightColor = it
                    sharedHighlightColor = lyricSettings.sharedHighlightColor
                },
                onSharedFontStyleChange = {
                    lyricSettings.sharedFontStyleId = it
                    sharedFontStyleId = lyricSettings.sharedFontStyleId
                },
                onSharedMotionPresetChange = {
                    lyricSettings.sharedMotionPresetId = it
                    sharedMotionPresetId = lyricSettings.sharedMotionPresetId
                },
                onClusterWidthScaleChange = {
                    lyricSettings.clusterWidthScale = it
                    clusterWidthScale = lyricSettings.clusterWidthScale
                },
                onClusterXChange = {
                    lyricSettings.clusterXPct = it.roundToInt()
                    clusterX = lyricSettings.clusterXPct.toFloat()
                },
                onClusterYChange = {
                    lyricSettings.clusterYPct = it.roundToInt()
                    clusterY = lyricSettings.clusterYPct.toFloat()
                },
                onSyncOffsetChange = {
                    val snapped = (it / 50f).roundToInt() * 50
                    lyricSettings.syncOffsetMs = snapped
                    syncOffset = lyricSettings.syncOffsetMs.toFloat()
                },
            ),
        )
    }
}
