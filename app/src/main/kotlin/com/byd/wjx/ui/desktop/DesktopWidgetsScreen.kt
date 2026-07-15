package com.byd.wjx.ui.desktop

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.overlay.AutoDoorFloatingService
import com.byd.wjx.overlay.AutoDoorFloatingSettings
import com.byd.wjx.overlay.SoundFloatingService
import com.byd.wjx.overlay.SoundFloatingSettings
import com.byd.wjx.ui.widget.WidgetController
import com.byd.wjx.ui.widget.WidgetPreferences
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DesktopWidgetsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activationManager = remember(context.applicationContext) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ClusterEntryPoint::class.java)
            .activationManager()
    }
    val soundFloatingSettings = remember { SoundFloatingSettings(context) }
    val autoDoorFloatingSettings = remember { AutoDoorFloatingSettings(context) }
    val widgetPrefs = remember { WidgetPreferences(context) }
    val lyricSettings = remember { LyricSettings(context) }
    val panelPreferences = remember(context.applicationContext) {
        DesktopWidgetPanelPreferences(context)
    }
    var soundFloatingEnabled by remember { mutableStateOf(soundFloatingSettings.enabled) }
    var autoDoorFloatingEnabled by remember { mutableStateOf(autoDoorFloatingSettings.enabled) }
    var autoDoorFloatingAlpha by remember { mutableFloatStateOf(autoDoorFloatingSettings.alpha) }
    var autoDoorFloatingScale by remember { mutableFloatStateOf(autoDoorFloatingSettings.scale) }
    var autoDoorFloatingLocked by remember { mutableStateOf(autoDoorFloatingSettings.locked) }
    var lyricDesktopEnabled by remember { mutableStateOf(lyricSettings.desktopEnabled) }
    var lyricAllowOtherApps by remember { mutableStateOf(lyricSettings.allowOnOtherApps) }
    var lyricClusterEnabled by remember { mutableStateOf(lyricSettings.clusterEnabled) }
    var soundFloatingControlsExpanded by remember {
        mutableStateOf(
            panelPreferences.initialExpanded(DesktopWidgetPanel.SOUND, soundFloatingSettings.enabled),
        )
    }
    var autoDoorControlsExpanded by remember {
        mutableStateOf(
            panelPreferences.initialExpanded(
                DesktopWidgetPanel.AUTO_DOOR,
                autoDoorFloatingSettings.enabled,
            ),
        )
    }
    var lyricControlsExpanded by remember {
        mutableStateOf(
            panelPreferences.initialExpanded(
                DesktopWidgetPanel.LYRIC,
                lyricSettings.desktopEnabled || lyricSettings.clusterEnabled,
            ),
        )
    }
    var widgetControlsExpanded by remember {
        mutableStateOf(
            panelPreferences.initialExpanded(DesktopWidgetPanel.BATTERY, widgetPrefs.isEnabled()),
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
    var lyricFeatureAllowed by remember { mutableStateOf(activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)) }
    var soundIconStyle by remember { mutableStateOf(soundFloatingSettings.iconStyle) }
    var soundIconAlpha by remember { mutableFloatStateOf(soundFloatingSettings.iconAlpha) }
    var soundIconScale by remember { mutableFloatStateOf(soundFloatingSettings.iconScale) }
    var customSoundIconAvailable by remember { mutableStateOf(soundFloatingSettings.hasCustomIcon()) }
    var customSoundIconVersion by remember { mutableIntStateOf(0) }
    val widgetEnabled by widgetPrefs.enabledFlow().collectAsStateWithLifecycle(initialValue = widgetPrefs.isEnabled())
    val widgetAlpha by widgetPrefs.alphaFlow().collectAsStateWithLifecycle(initialValue = widgetPrefs.getAlpha())
    val widgetScale by widgetPrefs.scaleFlow().collectAsStateWithLifecycle(initialValue = widgetPrefs.getScale())
    val widgetButtonsEnabled by widgetPrefs.buttonsEnabledFlow().collectAsStateWithLifecycle(initialValue = widgetPrefs.isButtonsEnabled())
    val leftTapApp by widgetPrefs.leftTapAppFlow().collectAsStateWithLifecycle(
        initialValue = WidgetPreferences.LeftTapAppState(
            enabled = widgetPrefs.isLeftTapZoningEnabled(),
            packageName = widgetPrefs.getLeftTapAppPackage(),
            label = widgetPrefs.getLeftTapAppLabel(),
        ),
    )
    var showLeftTapPicker by remember { mutableStateOf(false) }
    val customSoundIconBitmap = remember(customSoundIconAvailable, customSoundIconVersion) {
        runCatching {
            BitmapFactory.decodeFile(soundFloatingSettings.customIconPath)?.asImageBitmap()
        }.getOrNull()
    }
    fun setSoundFloatingControlsExpanded(value: Boolean) {
        soundFloatingControlsExpanded = value
        panelPreferences.setExpanded(DesktopWidgetPanel.SOUND, value)
    }
    fun setAutoDoorControlsExpanded(value: Boolean) {
        autoDoorControlsExpanded = value
        panelPreferences.setExpanded(DesktopWidgetPanel.AUTO_DOOR, value)
    }
    fun setLyricControlsExpanded(value: Boolean) {
        lyricControlsExpanded = value
        panelPreferences.setExpanded(DesktopWidgetPanel.LYRIC, value)
    }
    fun setWidgetControlsExpanded(value: Boolean) {
        widgetControlsExpanded = value
        panelPreferences.setExpanded(DesktopWidgetPanel.BATTERY, value)
    }
    val lyricDesktopActive = lyricFeatureAllowed && lyricDesktopEnabled
    val lyricClusterActive = lyricFeatureAllowed && lyricClusterEnabled
    val refreshSoundFloating = {
        if (soundFloatingEnabled && Settings.canDrawOverlays(context)) {
            SoundFloatingService.refresh(context)
        }
    }
    val refreshAutoDoorFloating = {
        if (autoDoorFloatingEnabled && Settings.canDrawOverlays(context)) {
            AutoDoorFloatingService.refreshAppearance(context)
        }
    }
    fun refreshLyricFeatureAllowed(): Boolean {
        lyricFeatureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
        return lyricFeatureAllowed
    }
    fun showLyricActivationRequired() {
        Toast.makeText(context, "桌面歌词需要激活后使用", Toast.LENGTH_SHORT).show()
    }
    fun selectSoundFloatingIcon(style: String) {
        soundFloatingSettings.iconStyle = style
        soundIconStyle = soundFloatingSettings.iconStyle
        customSoundIconAvailable = soundFloatingSettings.hasCustomIcon()
        refreshSoundFloating()
    }
    val soundIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && soundFloatingSettings.saveCustomIcon(uri)) {
            customSoundIconAvailable = soundFloatingSettings.hasCustomIcon()
            customSoundIconVersion += 1
            soundIconStyle = soundFloatingSettings.iconStyle
            refreshSoundFloating()
        }
    }
    fun nudgeClusterLyric(dx: Int, dy: Int) {
        lyricSettings.clusterXPct = (lyricSettings.clusterXPct + dx).coerceIn(0, 100)
        lyricSettings.clusterYPct = (lyricSettings.clusterYPct + dy).coerceIn(0, 100)
        lyricClusterX = lyricSettings.clusterXPct.toFloat()
        lyricClusterY = lyricSettings.clusterYPct.toFloat()
    }

    LaunchedEffect(activationManager) {
        while (true) {
            lyricFeatureAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
            delay(5_000L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val overlayGranted = Settings.canDrawOverlays(context)
                if (overlayGranted) {
                    SoundFloatingService.startIfAllowed(context)
                    AutoDoorFloatingService.startIfAllowed(context)
                    if (widgetPrefs.isEnabled()) {
                        WidgetController.attach(context)
                    }
                }
                soundFloatingEnabled = soundFloatingSettings.enabled
                if (!soundFloatingSettings.enabled) setSoundFloatingControlsExpanded(false)
                autoDoorFloatingEnabled = autoDoorFloatingSettings.enabled
                autoDoorFloatingAlpha = autoDoorFloatingSettings.alpha
                autoDoorFloatingScale = autoDoorFloatingSettings.scale
                autoDoorFloatingLocked = autoDoorFloatingSettings.locked
                if (!autoDoorFloatingSettings.enabled) setAutoDoorControlsExpanded(false)
                soundIconStyle = soundFloatingSettings.iconStyle
                soundIconAlpha = soundFloatingSettings.iconAlpha
                soundIconScale = soundFloatingSettings.iconScale
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
                if (!widgetPrefs.isEnabled()) setWidgetControlsExpanded(false)
                customSoundIconAvailable = soundFloatingSettings.hasCustomIcon()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                WidgetController.setPreviewMode(context, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            WidgetController.setPreviewMode(context, false)
        }
    }

    var selectedPreview by remember { mutableStateOf(DesktopPreviewType.DesktopLyric) }
    fun requestOverlayPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    fun setSoundFloatingEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(context)) {
            soundFloatingSettings.enabled = true
            soundFloatingEnabled = true
            setSoundFloatingControlsExpanded(true)
            requestOverlayPermission()
            return
        }
        soundFloatingSettings.enabled = enabled
        soundFloatingEnabled = enabled
        setSoundFloatingControlsExpanded(enabled)
        if (enabled) SoundFloatingService.start(context) else SoundFloatingService.hide(context)
    }
    fun setAutoDoorFloatingEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(context)) {
            autoDoorFloatingSettings.enabled = true
            autoDoorFloatingEnabled = true
            setAutoDoorControlsExpanded(true)
            requestOverlayPermission()
            return
        }
        autoDoorFloatingSettings.enabled = enabled
        autoDoorFloatingEnabled = enabled
        setAutoDoorControlsExpanded(enabled)
        if (enabled) AutoDoorFloatingService.start(context) else AutoDoorFloatingService.hide(context)
    }
    fun setBatteryWidgetEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(context)) {
            widgetPrefs.setEnabled(true)
            setWidgetControlsExpanded(true)
            requestOverlayPermission()
            return
        }
        widgetPrefs.setEnabled(enabled)
        setWidgetControlsExpanded(enabled)
        if (enabled) WidgetController.attach(context) else WidgetController.detach()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 58.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DesktopSceneHeader()

        DesktopPreviewStage(
            selected = selectedPreview,
            onSelected = { selectedPreview = it },
            soundEnabled = soundFloatingEnabled,
            desktopLyricEnabled = lyricDesktopActive,
            clusterLyricEnabled = lyricClusterActive,
            batteryEnabled = widgetEnabled,
            doorEnabled = autoDoorFloatingEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(406.dp),
        )

        DesktopQuickControlPanel(
            selected = selectedPreview,
            soundEnabled = soundFloatingEnabled,
            desktopLyricEnabled = lyricDesktopActive,
            clusterLyricEnabled = lyricClusterActive,
            batteryEnabled = widgetEnabled,
            doorEnabled = autoDoorFloatingEnabled,
            soundAlpha = soundIconAlpha,
            soundScale = soundIconScale,
            desktopLyricScale = lyricFontScale,
            clusterLyricScale = lyricClusterFontScale,
            clusterLyricAlpha = lyricClusterAlpha,
            batteryAlpha = widgetAlpha,
            batteryScale = widgetScale,
            doorAlpha = autoDoorFloatingAlpha,
            doorScale = autoDoorFloatingScale,
            onToggle = { enabled ->
                when (selectedPreview) {
                    DesktopPreviewType.Sound -> setSoundFloatingEnabled(enabled)
                    DesktopPreviewType.DesktopLyric -> setDesktopLyricEnabled(enabled)
                    DesktopPreviewType.ClusterLyric -> setClusterLyricEnabled(enabled)
                    DesktopPreviewType.Battery -> setBatteryWidgetEnabled(enabled)
                    DesktopPreviewType.Door -> setAutoDoorFloatingEnabled(enabled)
                }
            },
            onPrimaryValueChange = { value ->
                when (selectedPreview) {
                    DesktopPreviewType.Sound -> {
                        soundFloatingSettings.iconScale = value
                        soundIconScale = soundFloatingSettings.iconScale
                        refreshSoundFloating()
                    }
                    DesktopPreviewType.DesktopLyric -> {
                        lyricSettings.fontScale = value
                        lyricFontScale = lyricSettings.fontScale
                    }
                    DesktopPreviewType.ClusterLyric -> {
                        lyricSettings.clusterFontScale = value
                        lyricClusterFontScale = lyricSettings.clusterFontScale
                    }
                    DesktopPreviewType.Battery -> widgetPrefs.setScale(value)
                    DesktopPreviewType.Door -> {
                        autoDoorFloatingSettings.scale = value
                        autoDoorFloatingScale = autoDoorFloatingSettings.scale
                        refreshAutoDoorFloating()
                    }
                }
            },
            onSecondaryValueChange = { value ->
                when (selectedPreview) {
                    DesktopPreviewType.Sound -> {
                        soundFloatingSettings.iconAlpha = value
                        soundIconAlpha = soundFloatingSettings.iconAlpha
                        refreshSoundFloating()
                    }
                    DesktopPreviewType.DesktopLyric -> Unit
                    DesktopPreviewType.ClusterLyric -> {
                        lyricSettings.clusterAlpha = value
                        lyricClusterAlpha = lyricSettings.clusterAlpha
                    }
                    DesktopPreviewType.Battery -> widgetPrefs.setAlpha(value)
                    DesktopPreviewType.Door -> {
                        autoDoorFloatingSettings.alpha = value
                        autoDoorFloatingAlpha = autoDoorFloatingSettings.alpha
                        refreshAutoDoorFloating()
                    }
                }
            },
            onReset = {
                when (selectedPreview) {
                    DesktopPreviewType.Sound -> {
                        soundFloatingSettings.x = Int.MIN_VALUE
                        soundFloatingSettings.y = Int.MIN_VALUE
                        refreshSoundFloating()
                    }
                    DesktopPreviewType.DesktopLyric -> lyricSettings.resetDesktopPosition()
                    DesktopPreviewType.ClusterLyric -> {
                        lyricSettings.resetClusterPosition()
                        lyricClusterX = lyricSettings.clusterXPct.toFloat()
                        lyricClusterY = lyricSettings.clusterYPct.toFloat()
                    }
                    DesktopPreviewType.Battery -> widgetPrefs.resetPosition()
                    DesktopPreviewType.Door -> {
                        autoDoorFloatingSettings.resetPosition()
                        refreshAutoDoorFloating()
                    }
                }
            },
        )

        when (selectedPreview) {
            DesktopPreviewType.Sound -> {
                SoundFloatingSettingsPanel(
                    state = SoundPanelState(
                        enabled = soundFloatingEnabled,
                        expanded = soundFloatingControlsExpanded,
                        iconStyle = soundIconStyle,
                        iconAlpha = soundIconAlpha,
                        iconScale = soundIconScale,
                        customIconAvailable = customSoundIconAvailable,
                        customIconBitmap = customSoundIconBitmap,
                    ),
                    actions = SoundPanelActions(
                        onEnabledChange = ::setSoundFloatingEnabled,
                        onExpandedChange = ::setSoundFloatingControlsExpanded,
                        onIconStyleChange = ::selectSoundFloatingIcon,
                        onPickCustomIcon = { soundIconPicker.launch("image/*") },
                        onAlphaChange = {
                            soundFloatingSettings.iconAlpha = it
                            soundIconAlpha = soundFloatingSettings.iconAlpha
                            refreshSoundFloating()
                        },
                        onScaleChange = {
                            soundFloatingSettings.iconScale = it
                            soundIconScale = soundFloatingSettings.iconScale
                            refreshSoundFloating()
                        },
                        onResetPosition = {
                            soundFloatingSettings.x = Int.MIN_VALUE
                            soundFloatingSettings.y = Int.MIN_VALUE
                            refreshSoundFloating()
                        },
                        onResetIcon = {
                            soundFloatingSettings.resetIcon()
                            soundIconStyle = soundFloatingSettings.iconStyle
                            soundIconAlpha = soundFloatingSettings.iconAlpha
                            soundIconScale = soundFloatingSettings.iconScale
                            customSoundIconAvailable = soundFloatingSettings.hasCustomIcon()
                            refreshSoundFloating()
                        },
                    ),
                )
            }

            DesktopPreviewType.DesktopLyric,
            DesktopPreviewType.ClusterLyric,
            -> {
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
                                DesktopPreviewType.DesktopLyric -> lyricSettings.resetDesktopPosition()
                                DesktopPreviewType.ClusterLyric -> {
                                    lyricSettings.resetClusterPosition()
                                    lyricClusterX = lyricSettings.clusterXPct.toFloat()
                                    lyricClusterY = lyricSettings.clusterYPct.toFloat()
                                }
                                else -> Unit
                            }
                        },
                        onNudgeCluster = { dx, dy -> nudgeClusterLyric(dx, dy) },
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

            DesktopPreviewType.Battery,
            DesktopPreviewType.Door,
            -> {
                UtilitySettingsPanel(
                    state = UtilityPanelState(
                        selected = selectedPreview,
                        autoDoorEnabled = autoDoorFloatingEnabled,
                        autoDoorExpanded = autoDoorControlsExpanded,
                        autoDoorLocked = autoDoorFloatingLocked,
                        autoDoorAlpha = autoDoorFloatingAlpha,
                        autoDoorScale = autoDoorFloatingScale,
                        widgetEnabled = widgetEnabled,
                        widgetExpanded = widgetControlsExpanded,
                        widgetAlpha = widgetAlpha,
                        widgetScale = widgetScale,
                        widgetButtonsEnabled = widgetButtonsEnabled,
                        leftTapApp = leftTapApp,
                    ),
                    actions = UtilityPanelActions(
                        onAutoDoorEnabledChange = ::setAutoDoorFloatingEnabled,
                        onAutoDoorExpandedChange = ::setAutoDoorControlsExpanded,
                        onAutoDoorLockedChange = {
                            autoDoorFloatingSettings.locked = it
                            autoDoorFloatingLocked = autoDoorFloatingSettings.locked
                            refreshAutoDoorFloating()
                        },
                        onAutoDoorAlphaChange = {
                            autoDoorFloatingSettings.alpha = it
                            autoDoorFloatingAlpha = autoDoorFloatingSettings.alpha
                            refreshAutoDoorFloating()
                        },
                        onAutoDoorScaleChange = {
                            autoDoorFloatingSettings.scale = it
                            autoDoorFloatingScale = autoDoorFloatingSettings.scale
                            refreshAutoDoorFloating()
                        },
                        onResetAutoDoorPosition = {
                            autoDoorFloatingSettings.resetPosition()
                            if (autoDoorFloatingEnabled && Settings.canDrawOverlays(context)) {
                                AutoDoorFloatingService.refresh(context)
                            }
                        },
                        onWidgetEnabledChange = ::setBatteryWidgetEnabled,
                        onWidgetExpandedChange = ::setWidgetControlsExpanded,
                        onWidgetAlphaChange = {
                            if (widgetEnabled) WidgetController.setPreviewMode(context, true)
                            widgetPrefs.setAlpha(it)
                        },
                        onWidgetScaleChange = {
                            if (widgetEnabled) WidgetController.setPreviewMode(context, true)
                            widgetPrefs.setScale(it)
                        },
                        onLeftTapZoningChange = widgetPrefs::setLeftTapZoningEnabled,
                        onWidgetButtonsChange = widgetPrefs::setButtonsEnabled,
                        onChooseLeftTapApp = { showLeftTapPicker = true },
                        onResetWidgetPosition = {
                            widgetPrefs.resetPosition()
                            if (widgetEnabled && Settings.canDrawOverlays(context)) {
                                WidgetController.detach()
                                WidgetController.attach(context)
                            }
                        },
                    ),
                )
            }
        }
    }

    if (showLeftTapPicker) {
        com.byd.wjx.ui.components.AppLaunchPickerDialog(
            currentPackage = leftTapApp.packageName,
            onDismiss = { showLeftTapPicker = false },
            onSelect = { pkg, label ->
                widgetPrefs.setLeftTapApp(pkg, label)
                showLeftTapPicker = false
            },
            showMinimizeToggle = false,
        )
    }
}
