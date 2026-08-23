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

/** Independent settings for the desktop and instrument-cluster lyric overlays. */
@Composable
fun LyricDisplayScreen(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    scrollEnabled: Boolean = true,
    onEnabledStateChanged: ((Boolean) -> Unit)? = null,
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

    fun readDesktopAdjustment() = LyricAdjustmentState(
        lineCount = lyricSettings.desktopLineCount,
        fontScale = lyricSettings.desktopFontScale,
        textColor = lyricSettings.desktopTextColor,
        highlightColor = lyricSettings.desktopHighlightColor,
        fontStyleId = lyricSettings.desktopFontStyleId,
        motionPresetId = lyricSettings.desktopMotionPresetId,
        syncOffset = lyricSettings.desktopSyncOffsetMs.toFloat(),
    )

    fun readClusterAdjustment() = LyricAdjustmentState(
        lineCount = lyricSettings.clusterLineCount,
        fontScale = lyricSettings.clusterFontScale,
        textColor = lyricSettings.clusterTextColor,
        highlightColor = lyricSettings.clusterHighlightColor,
        fontStyleId = lyricSettings.clusterFontStyleId,
        motionPresetId = lyricSettings.clusterMotionPresetId,
        syncOffset = lyricSettings.clusterSyncOffsetMs.toFloat(),
    )

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
    var desktopAdjustment by remember { mutableStateOf(readDesktopAdjustment()) }
    var clusterAdjustment by remember { mutableStateOf(readClusterAdjustment()) }
    var clusterX by remember { mutableFloatStateOf(lyricSettings.clusterXPct.toFloat()) }
    var clusterY by remember { mutableFloatStateOf(lyricSettings.clusterYPct.toFloat()) }
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
        desktopAdjustment = readDesktopAdjustment()
        clusterAdjustment = readClusterAdjustment()
        clusterX = lyricSettings.clusterXPct.toFloat()
        clusterY = lyricSettings.clusterYPct.toFloat()
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
                desktopAdjustment = desktopAdjustment,
                clusterAdjustment = clusterAdjustment,
                clusterX = clusterX,
                clusterY = clusterY,
            ),
            actions = LyricPanelActions(
                onDesktopEnabledChange = { setModeEnabled(desktop = true, enabled = it) },
                onClusterEnabledChange = { setModeEnabled(desktop = false, enabled = it) },
                onExpandedChange = ::setExpanded,
                onAllowOtherAppsChange = {
                    lyricSettings.allowOnOtherApps = it
                    allowOtherApps = lyricSettings.allowOnOtherApps
                },
                onOpenPlaybackPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onResetClusterPosition = {
                    lyricSettings.resetClusterPosition()
                    clusterX = lyricSettings.clusterXPct.toFloat()
                    clusterY = lyricSettings.clusterYPct.toFloat()
                },
                onLineCountChange = { target, value ->
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> {
                            lyricSettings.desktopLineCount = value
                            desktopAdjustment = readDesktopAdjustment()
                        }
                        LyricAdjustmentTarget.CLUSTER -> {
                            lyricSettings.clusterLineCount = value
                            clusterAdjustment = readClusterAdjustment()
                        }
                    }
                },
                onFontScaleChange = { target, value ->
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> {
                            lyricSettings.desktopFontScale = value
                            desktopAdjustment = readDesktopAdjustment()
                        }
                        LyricAdjustmentTarget.CLUSTER -> {
                            lyricSettings.clusterFontScale = value
                            clusterAdjustment = readClusterAdjustment()
                        }
                    }
                },
                onTextColorChange = { target, value ->
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> lyricSettings.desktopTextColor = value
                        LyricAdjustmentTarget.CLUSTER -> lyricSettings.clusterTextColor = value
                    }
                    desktopAdjustment = readDesktopAdjustment()
                    clusterAdjustment = readClusterAdjustment()
                },
                onHighlightColorChange = { target, value ->
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> lyricSettings.desktopHighlightColor = value
                        LyricAdjustmentTarget.CLUSTER -> lyricSettings.clusterHighlightColor = value
                    }
                    desktopAdjustment = readDesktopAdjustment()
                    clusterAdjustment = readClusterAdjustment()
                },
                onFontStyleChange = { target, value ->
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> lyricSettings.desktopFontStyleId = value
                        LyricAdjustmentTarget.CLUSTER -> lyricSettings.clusterFontStyleId = value
                    }
                    desktopAdjustment = readDesktopAdjustment()
                    clusterAdjustment = readClusterAdjustment()
                },
                onMotionPresetChange = { target, value ->
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> lyricSettings.desktopMotionPresetId = value
                        LyricAdjustmentTarget.CLUSTER -> lyricSettings.clusterMotionPresetId = value
                    }
                    desktopAdjustment = readDesktopAdjustment()
                    clusterAdjustment = readClusterAdjustment()
                },
                onClusterXChange = {
                    lyricSettings.clusterXPct = it.roundToInt()
                    clusterX = lyricSettings.clusterXPct.toFloat()
                },
                onClusterYChange = {
                    lyricSettings.clusterYPct = it.roundToInt()
                    clusterY = lyricSettings.clusterYPct.toFloat()
                },
                onSyncOffsetChange = { target, value ->
                    val snapped = (value / 50f).roundToInt() * 50
                    when (target) {
                        LyricAdjustmentTarget.DESKTOP -> lyricSettings.desktopSyncOffsetMs = snapped
                        LyricAdjustmentTarget.CLUSTER -> lyricSettings.clusterSyncOffsetMs = snapped
                    }
                    desktopAdjustment = readDesktopAdjustment()
                    clusterAdjustment = readClusterAdjustment()
                },
            ),
        )
    }
}
