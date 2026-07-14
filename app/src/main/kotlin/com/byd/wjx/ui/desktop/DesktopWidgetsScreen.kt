package com.byd.wjx.ui.desktop

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery6Bar
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.overlay.AutoDoorFloatingService
import com.byd.wjx.overlay.AutoDoorFloatingSettings
import com.byd.wjx.overlay.SoundFloatingService
import com.byd.wjx.overlay.SoundFloatingSettings
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.ui.pro.ProGlassPanel
import com.byd.wjx.ui.pro.ProInfoCard
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.pro.ProSectionTitle
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneSky
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.ui.widget.WidgetController
import com.byd.wjx.ui.widget.WidgetPreferences
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val controlsPrefs = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences(DESKTOP_CONTROLS_PREFS, Context.MODE_PRIVATE)
    }
    fun initialExpanded(key: String, enabled: Boolean): Boolean =
        enabled && controlsPrefs.getBoolean(key, true)
    var soundFloatingEnabled by remember { mutableStateOf(soundFloatingSettings.enabled) }
    var autoDoorFloatingEnabled by remember { mutableStateOf(autoDoorFloatingSettings.enabled) }
    var autoDoorFloatingAlpha by remember { mutableStateOf(autoDoorFloatingSettings.alpha) }
    var autoDoorFloatingScale by remember { mutableStateOf(autoDoorFloatingSettings.scale) }
    var autoDoorFloatingLocked by remember { mutableStateOf(autoDoorFloatingSettings.locked) }
    var lyricDesktopEnabled by remember { mutableStateOf(lyricSettings.desktopEnabled) }
    var lyricAllowOtherApps by remember { mutableStateOf(lyricSettings.allowOnOtherApps) }
    var lyricClusterEnabled by remember { mutableStateOf(lyricSettings.clusterEnabled) }
    var soundFloatingControlsExpanded by remember {
        mutableStateOf(initialExpanded(KEY_SOUND_CONTROLS_EXPANDED, soundFloatingSettings.enabled))
    }
    var autoDoorControlsExpanded by remember {
        mutableStateOf(initialExpanded(KEY_AUTO_DOOR_CONTROLS_EXPANDED, autoDoorFloatingSettings.enabled))
    }
    var lyricControlsExpanded by remember {
        mutableStateOf(initialExpanded(KEY_LYRIC_CONTROLS_EXPANDED, lyricSettings.desktopEnabled || lyricSettings.clusterEnabled))
    }
    var widgetControlsExpanded by remember {
        mutableStateOf(initialExpanded(KEY_WIDGET_CONTROLS_EXPANDED, widgetPrefs.isEnabled()))
    }
    var lyricFontScale by remember { mutableStateOf(lyricSettings.fontScale) }
    var lyricClusterFontScale by remember { mutableStateOf(lyricSettings.clusterFontScale) }
    var lyricClusterLineCount by remember { mutableStateOf(lyricSettings.clusterLineCount) }
    var lyricClusterWidthScale by remember { mutableStateOf(lyricSettings.clusterWidthScale) }
    var lyricClusterAlpha by remember { mutableStateOf(lyricSettings.clusterAlpha) }
    var lyricClusterX by remember { mutableStateOf(lyricSettings.clusterXPct.toFloat()) }
    var lyricClusterY by remember { mutableStateOf(lyricSettings.clusterYPct.toFloat()) }
    var lyricSyncOffset by remember { mutableStateOf(lyricSettings.syncOffsetMs.toFloat()) }
    var lyricFontStyleId by remember { mutableStateOf(lyricSettings.fontStyleId) }
    var lyricMotionPresetId by remember { mutableStateOf(lyricSettings.motionPresetId) }
    var lyricColorPresetId by remember { mutableStateOf(lyricSettings.colorPresetId) }
    var lyricKaraokeEnabled by remember { mutableStateOf(lyricSettings.karaokeEnabled) }
    var lyricFeatureAllowed by remember { mutableStateOf(activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)) }
    var soundIconStyle by remember { mutableStateOf(soundFloatingSettings.iconStyle) }
    var soundIconAlpha by remember { mutableStateOf(soundFloatingSettings.iconAlpha) }
    var soundIconScale by remember { mutableStateOf(soundFloatingSettings.iconScale) }
    var customSoundIconAvailable by remember { mutableStateOf(soundFloatingSettings.hasCustomIcon()) }
    var customSoundIconVersion by remember { mutableStateOf(0) }
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
        controlsPrefs.edit().putBoolean(KEY_SOUND_CONTROLS_EXPANDED, value).apply()
    }
    fun setAutoDoorControlsExpanded(value: Boolean) {
        autoDoorControlsExpanded = value
        controlsPrefs.edit().putBoolean(KEY_AUTO_DOOR_CONTROLS_EXPANDED, value).apply()
    }
    fun setLyricControlsExpanded(value: Boolean) {
        lyricControlsExpanded = value
        controlsPrefs.edit().putBoolean(KEY_LYRIC_CONTROLS_EXPANDED, value).apply()
    }
    fun setWidgetControlsExpanded(value: Boolean) {
        widgetControlsExpanded = value
        controlsPrefs.edit().putBoolean(KEY_WIDGET_CONTROLS_EXPANDED, value).apply()
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
    val selectedLyricActive = when (selectedPreview) {
        DesktopPreviewType.DesktopLyric -> lyricDesktopActive
        DesktopPreviewType.ClusterLyric -> lyricClusterActive
        else -> false
    }
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

        if (selectedPreview == DesktopPreviewType.Sound) {
            ProSectionTitle(
                title = "音效悬浮窗设置",
                subtitle = "图标样式、透明度、大小和桌面位置",
            )
            ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SwitchRow(
                    title = "音效悬浮窗",
                    subtitle = if (soundFloatingEnabled) "已在桌面显示，点击展开后可播放音效" else "开启后显示桌面音效入口",
                    checked = soundFloatingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            soundFloatingSettings.enabled = true
                            soundFloatingEnabled = true
                            setSoundFloatingControlsExpanded(true)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            soundFloatingSettings.enabled = enabled
                            soundFloatingEnabled = enabled
                            setSoundFloatingControlsExpanded(enabled)
                            if (enabled) SoundFloatingService.start(context) else SoundFloatingService.hide(context)
                        }
                    },
                )
                if (soundFloatingEnabled) {
                    DetailToggleRow(
                        title = "音效悬浮窗调节",
                        subtitle = "图标样式、透明度、大小和位置",
                        expanded = soundFloatingControlsExpanded,
                        onToggle = { setSoundFloatingControlsExpanded(!soundFloatingControlsExpanded) },
                    )
                    if (soundFloatingControlsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("音效悬浮窗图标", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                FloatingIconOption(
                                    label = "经典",
                                    selected = soundIconStyle == SoundFloatingSettings.ICON_STYLE_CLASSIC,
                                    enabled = soundFloatingEnabled,
                                    onClick = { selectSoundFloatingIcon(SoundFloatingSettings.ICON_STYLE_CLASSIC) },
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.sound_float_face),
                                        contentDescription = null,
                                        modifier = Modifier.size(46.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                FloatingIconOption(
                                    label = "工具箱",
                                    selected = soundIconStyle == SoundFloatingSettings.ICON_STYLE_TOOLBOX,
                                    enabled = soundFloatingEnabled,
                                    onClick = { selectSoundFloatingIcon(SoundFloatingSettings.ICON_STYLE_TOOLBOX) },
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.sound_float_toolbox),
                                        contentDescription = null,
                                        modifier = Modifier.size(46.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                FloatingIconOption(
                                    label = "自定义",
                                    selected = soundIconStyle == SoundFloatingSettings.ICON_STYLE_CUSTOM,
                                    enabled = soundFloatingEnabled,
                                    onClick = {
                                        if (customSoundIconAvailable) {
                                            selectSoundFloatingIcon(SoundFloatingSettings.ICON_STYLE_CUSTOM)
                                        } else {
                                            soundIconPicker.launch("image/*")
                                        }
                                    },
                                ) {
                                    if (customSoundIconBitmap != null) {
                                        Image(
                                            bitmap = customSoundIconBitmap,
                                            contentDescription = null,
                                            modifier = Modifier.size(46.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Text("上传", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Button(
                                    onClick = { soundIconPicker.launch("image/*") },
                                    enabled = soundFloatingEnabled,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CardSurfaceElevated,
                                        contentColor = TextPrimary,
                                    ),
                                    modifier = Modifier.height(42.dp),
                                ) {
                                    Text("选择自定义图标", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        WidgetSlider(
                            label = "图标透明度",
                            value = soundIconAlpha,
                            valueText = "${(soundIconAlpha * 100).toInt()}%",
                            range = SoundFloatingSettings.ICON_ALPHA_MIN..SoundFloatingSettings.ICON_ALPHA_MAX,
                            enabled = soundFloatingEnabled,
                            onValueChange = {
                                soundFloatingSettings.iconAlpha = it
                                soundIconAlpha = soundFloatingSettings.iconAlpha
                                refreshSoundFloating()
                            },
                        )
                        WidgetSlider(
                            label = "图标大小",
                            value = soundIconScale,
                            valueText = "${(soundIconScale * 100).toInt()}%",
                            range = SoundFloatingSettings.ICON_SCALE_MIN..SoundFloatingSettings.ICON_SCALE_MAX,
                            enabled = soundFloatingEnabled,
                            onValueChange = {
                                soundFloatingSettings.iconScale = it
                                soundIconScale = soundFloatingSettings.iconScale
                                refreshSoundFloating()
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    soundFloatingSettings.x = Int.MIN_VALUE
                                    soundFloatingSettings.y = Int.MIN_VALUE
                                    if (soundFloatingEnabled && Settings.canDrawOverlays(context)) {
                                        SoundFloatingService.refresh(context)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardSurfaceElevated,
                                    contentColor = TextPrimary,
                                ),
                            ) {
                                Text("重置音效悬浮窗位置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    soundFloatingSettings.resetIcon()
                                    soundIconStyle = soundFloatingSettings.iconStyle
                                    soundIconAlpha = soundFloatingSettings.iconAlpha
                                    soundIconScale = soundFloatingSettings.iconScale
                                    customSoundIconAvailable = soundFloatingSettings.hasCustomIcon()
                                    refreshSoundFloating()
                                },
                                enabled = soundFloatingEnabled,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardSurfaceElevated,
                                    contentColor = TextPrimary,
                                ),
                            ) {
                                Text("恢复经典图标", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
	                    }
	                }
            }
        }
    }
            }
        }

        if (selectedPreview == DesktopPreviewType.DesktopLyric || selectedPreview == DesktopPreviewType.ClusterLyric) {
            ProSectionTitle(
                title = if (selectedPreview == DesktopPreviewType.DesktopLyric) "桌面歌词设置" else "仪表盘歌词设置",
                subtitle = if (selectedPreview == DesktopPreviewType.DesktopLyric) {
                    "透明歌词支持拖动、长按关闭和播放行高亮"
                } else {
                    "通过方向键和滑块精调仪表盘歌词位置与大小"
                },
            )
            ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (selectedPreview == DesktopPreviewType.DesktopLyric) {
                SwitchRow(
                    title = "中控桌面歌词",
                    subtitle = when {
                        !lyricFeatureAllowed -> "需要激活后使用，试用期内可直接开启"
                        lyricDesktopActive -> "播放时显示 5 行透明歌词，暂停后自动隐藏"
                        else -> "开启后在桌面透明显示歌词"
                    },
                    checked = lyricDesktopActive,
                    onCheckedChange = { enabled ->
                        if (enabled && !refreshLyricFeatureAllowed()) {
                            lyricSettings.desktopEnabled = false
                            lyricDesktopEnabled = false
                            showLyricActivationRequired()
                        } else if (enabled && !Settings.canDrawOverlays(context)) {
                            lyricSettings.desktopEnabled = false
                            lyricDesktopEnabled = false
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            lyricSettings.desktopEnabled = enabled
                            lyricDesktopEnabled = lyricSettings.desktopEnabled
                            setLyricControlsExpanded(lyricSettings.desktopEnabled || lyricSettings.clusterEnabled)
                        }
                    },
                )
                }
                if (selectedPreview == DesktopPreviewType.ClusterLyric) {
                SwitchRow(
                    title = "仪表盘投屏歌词",
                    subtitle = when {
                        !lyricFeatureAllowed -> "需要激活后使用，试用期内可直接开启"
                        lyricClusterActive -> "已投到仪表盘，当前显示 ${lyricClusterLineCount} 行歌词"
                        else -> "开启后在仪表盘显示，可自定义 1 至 5 行歌词"
                    },
                    checked = lyricClusterActive,
                    onCheckedChange = { enabled ->
                        if (enabled && !refreshLyricFeatureAllowed()) {
                            lyricSettings.clusterEnabled = false
                            lyricClusterEnabled = false
                            showLyricActivationRequired()
                        } else if (enabled && !Settings.canDrawOverlays(context)) {
                            lyricSettings.clusterEnabled = false
                            lyricClusterEnabled = false
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            lyricSettings.clusterEnabled = enabled
                            lyricClusterEnabled = lyricSettings.clusterEnabled
                            setLyricControlsExpanded(lyricSettings.desktopEnabled || lyricSettings.clusterEnabled)
                        }
                    },
                )
                }
                if (selectedLyricActive) {
                    DetailToggleRow(
                        title = "歌词显示调节",
                        subtitle = "权限、位置、大小、颜色和播放行高亮",
                        expanded = lyricControlsExpanded,
                        onToggle = { setLyricControlsExpanded(!lyricControlsExpanded) },
                    )
                    if (lyricControlsExpanded) {
                        if (selectedPreview == DesktopPreviewType.DesktopLyric && lyricDesktopActive) {
                            SwitchRow(
                                title = "允许显示在其他应用",
                                subtitle = "关闭时只在桌面/本应用显示，打开后导航、音乐等界面也可显示",
                                checked = lyricAllowOtherApps,
                                enabled = lyricDesktopActive,
                                onCheckedChange = {
                                    lyricSettings.allowOnOtherApps = it
                                    lyricAllowOtherApps = lyricSettings.allowOnOtherApps
                                },
                            )
                        }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardSurfaceElevated,
                            contentColor = TextPrimary,
                        ),
                        modifier = Modifier.height(42.dp),
                    ) {
                        Text("播放信息权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
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
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardSurfaceElevated,
                            contentColor = TextPrimary,
                        ),
                        modifier = Modifier.height(42.dp),
                    ) {
                        Text("重置歌词位置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                        if (selectedPreview == DesktopPreviewType.ClusterLyric && lyricClusterActive) {
                            ClusterLyricNudgeControls(
                                enabled = lyricClusterActive,
                                onLeft = { nudgeClusterLyric(dx = -2, dy = 0) },
                                onRight = { nudgeClusterLyric(dx = 2, dy = 0) },
                                onUp = { nudgeClusterLyric(dx = 0, dy = -2) },
                                onDown = { nudgeClusterLyric(dx = 0, dy = 2) },
                            )
                        }
                        if (selectedPreview == DesktopPreviewType.DesktopLyric && lyricDesktopActive) {
                            WidgetSlider(
                                label = "中控歌词大小",
                                value = lyricFontScale,
                                valueText = "${(lyricFontScale * 100).roundToInt()}%",
                                range = LyricSettings.FONT_MIN..LyricSettings.FONT_MAX,
                                enabled = lyricDesktopActive,
                                onValueChange = {
                                    lyricSettings.fontScale = it
                                    lyricFontScale = lyricSettings.fontScale
                                },
                            )
                        }
                        if (selectedPreview == DesktopPreviewType.ClusterLyric && lyricClusterActive) {
                PresetChipRow(
                    title = "仪表歌词行数",
                    options = (LyricSettings.CLUSTER_LINE_COUNT_MIN..LyricSettings.CLUSTER_LINE_COUNT_MAX)
                        .map { it.toString() to "${it}行" },
                    selectedId = lyricClusterLineCount.toString(),
                    enabled = lyricClusterActive,
                    onSelect = { value ->
                        lyricSettings.clusterLineCount = value.toIntOrNull() ?: LyricSettings.CLUSTER_LINE_COUNT_DEFAULT
                        lyricClusterLineCount = lyricSettings.clusterLineCount
                    },
                )
                WidgetSlider(
                    label = "仪表歌词大小",
                    value = lyricClusterFontScale,
                    valueText = "${(lyricClusterFontScale * 100).roundToInt()}%",
                    range = LyricSettings.CLUSTER_FONT_MIN..LyricSettings.CLUSTER_FONT_MAX,
                    enabled = lyricClusterActive,
                    onValueChange = {
                        lyricSettings.clusterFontScale = it
                        lyricClusterFontScale = lyricSettings.clusterFontScale
                    },
                )
                WidgetSlider(
                    label = "仪表歌词宽度",
                    value = lyricClusterWidthScale,
                    valueText = "${(lyricClusterWidthScale * 100).roundToInt()}%",
                    range = LyricSettings.CLUSTER_WIDTH_MIN..LyricSettings.CLUSTER_WIDTH_MAX,
                    enabled = lyricClusterActive,
                    onValueChange = {
                        lyricSettings.clusterWidthScale = it
                        lyricClusterWidthScale = lyricSettings.clusterWidthScale
                    },
                )
                WidgetSlider(
                    label = "仪表不透明度",
                    value = lyricClusterAlpha,
                    valueText = "${(lyricClusterAlpha * 100).roundToInt()}%",
                    range = LyricSettings.ALPHA_MIN..LyricSettings.ALPHA_MAX,
                    enabled = lyricClusterActive,
                    onValueChange = {
                        lyricSettings.clusterAlpha = it
                        lyricClusterAlpha = lyricSettings.clusterAlpha
                    },
                )
                WidgetSlider(
                    label = "仪表水平位置",
                    value = lyricClusterX,
                    valueText = "${lyricClusterX.roundToInt()}%",
                    range = 0f..100f,
                    enabled = lyricClusterActive,
                    onValueChange = {
                        lyricSettings.clusterXPct = it.roundToInt()
                        lyricClusterX = lyricSettings.clusterXPct.toFloat()
                    },
                )
                WidgetSlider(
                    label = "仪表垂直位置",
                    value = lyricClusterY,
                    valueText = "${lyricClusterY.roundToInt()}%",
                    range = 0f..100f,
                    enabled = lyricClusterActive,
                    onValueChange = {
                        lyricSettings.clusterYPct = it.roundToInt()
                        lyricClusterY = lyricSettings.clusterYPct.toFloat()
                    },
                )
                        }
                WidgetSlider(
                    label = "歌词同步微调",
                    value = lyricSyncOffset,
                    valueText = LyricSettings.formatOffset(lyricSyncOffset.roundToInt()),
                    range = LyricSettings.SYNC_MIN_MS.toFloat()..LyricSettings.SYNC_MAX_MS.toFloat(),
                    enabled = selectedLyricActive,
                    onValueChange = {
                        val snapped = (it / 50f).roundToInt() * 50
                        lyricSettings.syncOffsetMs = snapped
                        lyricSyncOffset = lyricSettings.syncOffsetMs.toFloat()
                    },
                )
                SwitchRow(
                    title = "播放行高亮",
                    subtitle = "当前播放歌词使用高亮色，其余歌词保持清晰显示",
                    checked = lyricKaraokeEnabled,
                    enabled = selectedLyricActive,
                    onCheckedChange = {
                        lyricSettings.karaokeEnabled = it
                        lyricKaraokeEnabled = lyricSettings.karaokeEnabled
                    },
                )
                PresetChipRow(
                    title = "字体风格",
                    options = LyricStylePresets.fonts.map { it.id to it.label },
                    selectedId = lyricFontStyleId,
                    enabled = selectedLyricActive,
                    onSelect = { id ->
                        lyricSettings.fontStyleId = id
                        lyricFontStyleId = lyricSettings.fontStyleId
                    },
                )
                PresetChipRow(
                    title = "滚动动效",
                    options = LyricStylePresets.motions.map { it.id to it.label },
                    selectedId = lyricMotionPresetId,
                    enabled = selectedLyricActive,
                    onSelect = { id ->
                        lyricSettings.motionPresetId = id
                        lyricMotionPresetId = lyricSettings.motionPresetId
                    },
                )
                ColorPresetChipRow(
                    selectedId = lyricColorPresetId,
                    enabled = selectedLyricActive,
                    onSelect = { id ->
                        lyricSettings.colorPresetId = id
                        lyricColorPresetId = lyricSettings.colorPresetId
                    },
                )
                    }
                }
            }
        }
        }

        if (selectedPreview == DesktopPreviewType.Battery || selectedPreview == DesktopPreviewType.Door) {
            ProSectionTitle(
                title = if (selectedPreview == DesktopPreviewType.Battery) "电量小组件设置" else "自动门设置",
                subtitle = if (selectedPreview == DesktopPreviewType.Battery) {
                    "显示电量、续航和快捷入口，透明度与大小实时生效"
                } else {
                    "设置桌面自动门控制卡片的固定状态、透明度和大小"
                },
            )
            ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (selectedPreview == DesktopPreviewType.Door) {
                SwitchRow(
                    title = "自动门小组件",
                    subtitle = if (autoDoorFloatingEnabled) {
                        "已固定在桌面，点击左右卡片控制电滑门"
                    } else {
                        "开启后在桌面显示左右电滑门控制卡片"
                    },
                    checked = autoDoorFloatingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            autoDoorFloatingSettings.enabled = true
                            autoDoorFloatingEnabled = true
                            setAutoDoorControlsExpanded(true)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            autoDoorFloatingSettings.enabled = enabled
                            autoDoorFloatingEnabled = enabled
                            setAutoDoorControlsExpanded(enabled)
                            if (enabled) AutoDoorFloatingService.start(context) else AutoDoorFloatingService.hide(context)
                        }
                    },
                )
                if (autoDoorFloatingEnabled) {
                    DetailToggleRow(
                        title = "自动门小组件调节",
                        subtitle = "透明度、大小、固定状态和位置",
                        expanded = autoDoorControlsExpanded,
                        onToggle = { setAutoDoorControlsExpanded(!autoDoorControlsExpanded) },
                    )
                    if (autoDoorControlsExpanded) {
                        SwitchRow(
                            title = "固定在桌面",
                            subtitle = if (autoDoorFloatingLocked) {
                                "已固定，长按左侧车辆图也可解锁"
                            } else {
                                "已解锁，可拖动左侧车辆图调整位置"
                            },
                            checked = autoDoorFloatingLocked,
                            enabled = autoDoorFloatingEnabled,
                            onCheckedChange = {
                                autoDoorFloatingSettings.locked = it
                                autoDoorFloatingLocked = autoDoorFloatingSettings.locked
                                refreshAutoDoorFloating()
                            },
                        )
                        WidgetSlider(
                            label = "自动门透明度",
                            value = autoDoorFloatingAlpha,
                            valueText = "${(autoDoorFloatingAlpha * 100).toInt()}%",
                            range = AutoDoorFloatingSettings.ALPHA_MIN..AutoDoorFloatingSettings.ALPHA_MAX,
                            enabled = autoDoorFloatingEnabled,
                            onValueChange = {
                                autoDoorFloatingSettings.alpha = it
                                autoDoorFloatingAlpha = autoDoorFloatingSettings.alpha
                                refreshAutoDoorFloating()
                            },
                        )
                        WidgetSlider(
                            label = "自动门大小",
                            value = autoDoorFloatingScale,
                            valueText = "${(autoDoorFloatingScale * 100).toInt()}%",
                            range = AutoDoorFloatingSettings.SCALE_MIN..AutoDoorFloatingSettings.SCALE_MAX,
                            enabled = autoDoorFloatingEnabled,
                            onValueChange = {
                                autoDoorFloatingSettings.scale = it
                                autoDoorFloatingScale = autoDoorFloatingSettings.scale
                                refreshAutoDoorFloating()
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    autoDoorFloatingSettings.resetPosition()
                                    if (autoDoorFloatingEnabled && Settings.canDrawOverlays(context)) {
                                        AutoDoorFloatingService.refresh(context)
                                    }
                                },
                                enabled = autoDoorFloatingEnabled,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardSurfaceElevated,
                                    contentColor = TextPrimary,
                                ),
                                modifier = Modifier.height(42.dp),
                            ) {
                                Text("重置自动门位置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                }

                if (selectedPreview == DesktopPreviewType.Battery) {
                SwitchRow(
                    title = "电量小组件",
                    subtitle = if (widgetEnabled) "已显示在桌面" else "开启后显示车辆状态小组件",
                    checked = widgetEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            widgetPrefs.setEnabled(true)
                            setWidgetControlsExpanded(true)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            widgetPrefs.setEnabled(enabled)
                            setWidgetControlsExpanded(enabled)
                            if (enabled) WidgetController.attach(context) else WidgetController.detach()
                        }
                    },
                )
                if (widgetEnabled) {
                    DetailToggleRow(
                        title = "电量小组件调节",
                        subtitle = "透明度、大小、点击区域和快捷按钮",
                        expanded = widgetControlsExpanded,
                        onToggle = { setWidgetControlsExpanded(!widgetControlsExpanded) },
                    )
                    if (widgetControlsExpanded) {
                WidgetSlider(
                    label = "不透明度",
                    value = widgetAlpha,
                    valueText = "${(widgetAlpha * 100).toInt()}%",
                    range = 0.3f..1.0f,
                    enabled = widgetEnabled,
                    onValueChange = {
                        if (widgetEnabled) WidgetController.setPreviewMode(context, true)
                        widgetPrefs.setAlpha(it)
                    },
                )
                WidgetSlider(
                    label = "大小",
                    value = widgetScale,
                    valueText = "${(widgetScale * 100).toInt()}%",
                    range = WidgetPreferences.SCALE_MIN..WidgetPreferences.SCALE_MAX,
                    enabled = widgetEnabled,
                    onValueChange = {
                        if (widgetEnabled) WidgetController.setPreviewMode(context, true)
                        widgetPrefs.setScale(it)
                    },
                )
                SwitchRow(
                    title = "点击区域",
                    subtitle = "左侧 1/3 打开指定应用，其余区域打开超级玩具箱",
                    checked = leftTapApp.enabled,
                    enabled = widgetEnabled,
                    onCheckedChange = { widgetPrefs.setLeftTapZoningEnabled(it) },
                )
                SwitchRow(
                    title = "桌面快捷按钮",
                    subtitle = if (widgetButtonsEnabled) {
                        "单击小组件展开 1-4 按钮，双击进入超级玩具箱"
                    } else {
                        "开启后可用 1-4 按钮触发智能联动规则"
                    },
                    checked = widgetButtonsEnabled,
                    enabled = widgetEnabled,
                    onCheckedChange = { widgetPrefs.setButtonsEnabled(it) },
                )
                Button(
                    onClick = { showLeftTapPicker = true },
                    enabled = widgetEnabled && leftTapApp.enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurfaceElevated,
                        contentColor = TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                ) {
                    Text("左侧点击应用：${leftTapApp.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        widgetPrefs.resetPosition()
                        if (widgetEnabled && Settings.canDrawOverlays(context)) {
                            WidgetController.detach()
                            WidgetController.attach(context)
                        }
                    },
                    enabled = widgetEnabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = AccentContent,
                    ),
                    modifier = Modifier.height(42.dp),
                ) {
                    Text("重置小组件位置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                    }
                }
                }
            }
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

private enum class DesktopPreviewType(val label: String, val shortLabel: String) {
    Sound("音效悬浮窗", "音效"),
    DesktopLyric("桌面歌词", "桌面歌词"),
    ClusterLyric("仪表盘歌词", "仪表歌词"),
    Battery("电量小组件", "电量"),
    Door("自动门", "自动门"),
}

@Composable
private fun DesktopSceneHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("桌面组件", color = TextPrimary, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("把常用能力放在车机桌面", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DesktopPreviewStage(
    selected: DesktopPreviewType,
    onSelected: (DesktopPreviewType) -> Unit,
    soundEnabled: Boolean,
    desktopLyricEnabled: Boolean,
    clusterLyricEnabled: Boolean,
    batteryEnabled: Boolean,
    doorEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val types = DesktopPreviewType.entries
    val selectedIndex = types.indexOf(selected)
    val ordered = remember(selected) {
        List(types.size) { position ->
            types[(selectedIndex + position - 2 + types.size) % types.size]
        }
    }
    val enabledMap = mapOf(
        DesktopPreviewType.Sound to soundEnabled,
        DesktopPreviewType.DesktopLyric to desktopLyricEnabled,
        DesktopPreviewType.ClusterLyric to clusterLyricEnabled,
        DesktopPreviewType.Battery to batteryEnabled,
        DesktopPreviewType.Door to doorEnabled,
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ordered.forEachIndexed { index, type ->
                val isSelected = index == 2
                val heightFraction = when (index) {
                    2 -> 1f
                    1, 3 -> 0.88f
                    else -> 0.76f
                }
                val weight = when (index) {
                    2 -> 1.62f
                    1, 3 -> 1.12f
                    else -> 0.86f
                }
                DesktopPreviewCard(
                    type = type,
                    enabled = enabledMap[type] == true,
                    selected = isSelected,
                    onClick = { onSelected(type) },
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight(heightFraction),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            types.forEach { type ->
                Box(
                    modifier = Modifier
                        .width(if (type == selected) 22.dp else 10.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (type == selected) Color.White else Color.White.copy(alpha = 0.46f)),
                )
            }
        }
    }
}

@Composable
private fun DesktopPreviewCard(
    type: DesktopPreviewType,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var motionPhase by remember(type) { mutableFloatStateOf(STATIC_PREVIEW_PHASES[type.ordinal]) }
    LaunchedEffect(selected, type) {
        if (!selected) {
            motionPhase = STATIC_PREVIEW_PHASES[type.ordinal]
            return@LaunchedEffect
        }
        while (true) {
            delay(50L)
            motionPhase = (motionPhase + 0.0065f) % 1f
        }
    }
    val colors = when (type) {
        DesktopPreviewType.Sound -> listOf(Color(0xFF1A5960), Color(0xFF0C3138))
        DesktopPreviewType.DesktopLyric -> listOf(Color(0xFF2375B7), Color(0xFF174779))
        DesktopPreviewType.ClusterLyric -> listOf(Color(0xFF123F83), Color(0xFF071F4B))
        DesktopPreviewType.Battery -> listOf(Color(0xFF56B69D), Color(0xFF2D766A))
        DesktopPreviewType.Door -> listOf(Color(0xFF607489), Color(0xFF33485F))
    }
    val shape = RoundedCornerShape(if (selected) 22.dp else 16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(colors), shape)
            .border(if (selected) 2.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.88f else 0.32f), shape)
            .clickable(onClick = onClick),
    ) {
        DesktopCardBackground(
            type = type,
            selected = selected,
            motionPhase = motionPhase,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = if (selected) 0.02f else 0.08f),
                        0.72f to Color.Black.copy(alpha = if (selected) 0.12f else 0.20f),
                        1f to Color.Black.copy(alpha = if (selected) 0.28f else 0.34f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selected) 24.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type.label, color = Color.White, fontSize = if (selected) 18.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (enabled) "已开启" else "未开启",
                    color = if (enabled) Color(0xFF65F0C2) else Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (type) {
                    DesktopPreviewType.Sound -> SoundWidgetPreview(selected)
                    DesktopPreviewType.DesktopLyric -> LyricWidgetPreview(selected, cluster = false)
                    DesktopPreviewType.ClusterLyric -> LyricWidgetPreview(selected, cluster = true)
                    DesktopPreviewType.Battery -> BatteryWidgetPreview(selected)
                    DesktopPreviewType.Door -> DoorWidgetPreview(selected)
                }
            }
            if (selected) {
                Spacer(Modifier.height(10.dp))
                SoundPreviewPulse(motionPhase)
            }
        }
    }
}

@Composable
private fun DesktopCardBackground(
    type: DesktopPreviewType,
    selected: Boolean,
    motionPhase: Float,
    modifier: Modifier = Modifier,
) {
    val strength = if (selected) 1f else 0.48f
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minSide = size.minDimension
            val phaseAngle = motionPhase * (PI * 2.0).toFloat()

            when (type) {
                DesktopPreviewType.Sound -> {
                    val center = Offset(w * 0.50f, h * 0.58f)
                    repeat(5) { index ->
                        val progress = (motionPhase + index * 0.20f) % 1f
                        drawCircle(
                            color = SceneMint.copy(alpha = (1f - progress) * 0.20f * strength),
                            radius = minSide * (0.10f + progress * 0.38f),
                            center = center,
                            style = Stroke(width = (if (selected) 2f else 1.2f).dp.toPx()),
                        )
                    }
                    val wave = Path()
                    val samples = 42
                    repeat(samples + 1) { index ->
                        val x = w * index / samples
                        val y = h * 0.78f + sin(index * 0.58f + phaseAngle) * h * 0.035f * strength
                        if (index == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                    }
                    drawPath(wave, SceneCyan.copy(alpha = 0.46f * strength), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }

                DesktopPreviewType.DesktopLyric -> {
                    repeat(4) { band ->
                        val path = Path()
                        val samples = 36
                        repeat(samples + 1) { index ->
                            val x = w * index / samples
                            val y = h * (0.58f + band * 0.075f) +
                                sin(index * 0.34f + phaseAngle + band * 0.8f) * h * (0.045f + band * 0.006f) * strength
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path,
                            Color.White.copy(alpha = (0.18f - band * 0.025f) * strength),
                            style = Stroke((2.4f - band * 0.3f).dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    val sweepX = w * motionPhase
                    drawLine(
                        SceneCyan.copy(alpha = 0.34f * strength),
                        Offset(sweepX, h * 0.26f),
                        Offset(sweepX, h * 0.88f),
                        strokeWidth = 1.2f.dp.toPx(),
                    )
                }

                DesktopPreviewType.ClusterLyric -> {
                    val vanishing = Offset(w * 0.50f, h * 0.66f)
                    repeat(7) { index ->
                        val startX = w * index / 6f
                        drawLine(
                            SceneSky.copy(alpha = 0.18f * strength),
                            Offset(startX, h),
                            vanishing,
                            strokeWidth = 1.2f.dp.toPx(),
                        )
                    }
                    repeat(5) { index ->
                        val progress = (motionPhase + index * 0.20f) % 1f
                        val y = h * (0.66f + progress * 0.30f)
                        val halfWidth = w * progress * 0.42f
                        drawLine(
                            SceneCyan.copy(alpha = (0.30f - progress * 0.20f) * strength),
                            Offset(w * 0.50f - halfWidth, y),
                            Offset(w * 0.50f + halfWidth, y),
                            strokeWidth = 1.4f.dp.toPx(),
                        )
                    }
                    repeat(3) { index ->
                        drawArc(
                            color = SceneSky.copy(alpha = (0.18f - index * 0.04f) * strength),
                            startAngle = 202f,
                            sweepAngle = 136f,
                            useCenter = false,
                            topLeft = Offset(w * (0.20f - index * 0.04f), h * (0.24f - index * 0.04f)),
                            size = androidx.compose.ui.geometry.Size(w * (0.60f + index * 0.08f), h * (0.62f + index * 0.08f)),
                            style = Stroke(1.4f.dp.toPx()),
                        )
                    }
                }

                DesktopPreviewType.Battery -> {
                    val center = Offset(w * 0.50f, h * 0.56f)
                    val radius = minSide * 0.30f
                    repeat(48) { index ->
                        val angle = (index / 48f) * (PI * 2.0).toFloat() - (PI / 2.0).toFloat()
                        val activeDistance = ((index / 48f - motionPhase + 1f) % 1f)
                        val alpha = (0.10f + (1f - activeDistance).coerceIn(0f, 0.18f) * 2.2f) * strength
                        val start = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                        val end = Offset(center.x + cos(angle) * (radius + minSide * 0.035f), center.y + sin(angle) * (radius + minSide * 0.035f))
                        drawLine(SceneMint.copy(alpha = alpha), start, end, 1.5f.dp.toPx(), StrokeCap.Round)
                    }
                    drawCircle(
                        SceneMint.copy(alpha = (0.08f + 0.05f * (sin(phaseAngle) + 1f)) * strength),
                        radius = radius * (1.08f + 0.03f * sin(phaseAngle)),
                        center = center,
                    )
                }

                DesktopPreviewType.Door -> {
                    repeat(6) { index ->
                        val progress = (motionPhase + index * 0.16f) % 1f
                        val x = w * (0.12f + progress * 0.82f)
                        drawLine(
                            if (index % 2 == 0) SceneCyan.copy(alpha = 0.24f * strength) else SceneAmber.copy(alpha = 0.18f * strength),
                            Offset(x - w * 0.13f, h * (0.70f + index * 0.018f)),
                            Offset(x, h * (0.70f + index * 0.018f)),
                            2.dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                    drawLine(
                        SceneCyan.copy(alpha = 0.36f * strength),
                        Offset(w * (0.22f + motionPhase * 0.28f), h * 0.47f),
                        Offset(w * (0.47f + motionPhase * 0.28f), h * 0.47f),
                        2.4f.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        }

    }
}

private val STATIC_PREVIEW_PHASES = listOf(0.08f, 0.26f, 0.44f, 0.62f, 0.80f)

@Composable
private fun SoundWidgetPreview(selected: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(if (selected) 150.dp else 105.dp)) {
            repeat(4) { index ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f + index * 0.035f),
                    radius = size.minDimension * (0.18f + index * 0.10f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(if (selected) 76.dp else 58.dp)
                .clip(CircleShape)
                .background(SceneMint.copy(alpha = 0.84f))
                .border(2.dp, Color.White.copy(alpha = 0.68f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (selected) 38.dp else 28.dp))
        }
    }
}

@Composable
private fun LyricWidgetPreview(selected: Boolean, cluster: Boolean) {
    val lines = listOf("穿过人海 别停下来", "去更远的地方", "看更多的风景", "遇见更好的自己", "让故事继续")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (selected) 10.dp else 7.dp)) {
        if (cluster) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = Color.White.copy(alpha = 0.40f), modifier = Modifier.size(if (selected) 54.dp else 36.dp))
        }
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                color = if (index == 2) Color.White else Color.White.copy(alpha = 0.62f),
                fontSize = when {
                    selected && index == 2 -> 20.sp
                    selected -> 15.sp
                    index == 2 -> 14.sp
                    else -> 11.sp
                },
                fontWeight = if (index == 2) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BatteryWidgetPreview(selected: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(if (selected) 150.dp else 104.dp)) {
            drawArc(
                Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                SceneMint,
                startAngle = -90f,
                sweepAngle = 281f,
                useCenter = false,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOC", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp)
            Text("78%", color = Color.White, fontSize = if (selected) 34.sp else 25.sp, fontWeight = FontWeight.Light)
            Text("678 km", color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun DoorWidgetPreview(selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (selected) 10.dp else 7.dp)) {
        Image(
            painter = painterResource(R.drawable.auto_door_car_icon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(if (selected) 0.78f else 0.88f)
                .height(if (selected) 92.dp else 58.dp),
            contentScale = ContentScale.Fit,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("左门", "右门").forEach { label ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(label, color = Color.White.copy(alpha = 0.80f), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SoundPreviewPulse(motionPhase: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
        repeat(13) { index ->
            val pulse = ((sin((index * 0.72f + motionPhase * (PI * 2.0)).toFloat()) + 1f) * 0.5f)
            val h = size.height * (0.28f + pulse * 0.72f)
            val x = size.width * (index + 1) / 14f
            drawLine(Color.White.copy(alpha = 0.70f), Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun DesktopQuickControlPanel(
    selected: DesktopPreviewType,
    soundEnabled: Boolean,
    desktopLyricEnabled: Boolean,
    clusterLyricEnabled: Boolean,
    batteryEnabled: Boolean,
    doorEnabled: Boolean,
    soundAlpha: Float,
    soundScale: Float,
    desktopLyricScale: Float,
    clusterLyricScale: Float,
    clusterLyricAlpha: Float,
    batteryAlpha: Float,
    batteryScale: Float,
    doorAlpha: Float,
    doorScale: Float,
    onToggle: (Boolean) -> Unit,
    onPrimaryValueChange: (Float) -> Unit,
    onSecondaryValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val enabled = when (selected) {
        DesktopPreviewType.Sound -> soundEnabled
        DesktopPreviewType.DesktopLyric -> desktopLyricEnabled
        DesktopPreviewType.ClusterLyric -> clusterLyricEnabled
        DesktopPreviewType.Battery -> batteryEnabled
        DesktopPreviewType.Door -> doorEnabled
    }
    val primaryValue = when (selected) {
        DesktopPreviewType.Sound -> soundScale
        DesktopPreviewType.DesktopLyric -> desktopLyricScale
        DesktopPreviewType.ClusterLyric -> clusterLyricScale
        DesktopPreviewType.Battery -> batteryScale
        DesktopPreviewType.Door -> doorScale
    }
    val primaryRange = when (selected) {
        DesktopPreviewType.Sound -> SoundFloatingSettings.ICON_SCALE_MIN..SoundFloatingSettings.ICON_SCALE_MAX
        DesktopPreviewType.DesktopLyric -> LyricSettings.FONT_MIN..LyricSettings.FONT_MAX
        DesktopPreviewType.ClusterLyric -> LyricSettings.CLUSTER_FONT_MIN..LyricSettings.CLUSTER_FONT_MAX
        DesktopPreviewType.Battery -> WidgetPreferences.SCALE_MIN..WidgetPreferences.SCALE_MAX
        DesktopPreviewType.Door -> AutoDoorFloatingSettings.SCALE_MIN..AutoDoorFloatingSettings.SCALE_MAX
    }
    val secondaryValue = when (selected) {
        DesktopPreviewType.Sound -> soundAlpha
        DesktopPreviewType.DesktopLyric -> 0.85f
        DesktopPreviewType.ClusterLyric -> clusterLyricAlpha
        DesktopPreviewType.Battery -> batteryAlpha
        DesktopPreviewType.Door -> doorAlpha
    }
    val secondaryRange = when (selected) {
        DesktopPreviewType.Sound -> SoundFloatingSettings.ICON_ALPHA_MIN..SoundFloatingSettings.ICON_ALPHA_MAX
        DesktopPreviewType.DesktopLyric -> 0.3f..1f
        DesktopPreviewType.ClusterLyric -> LyricSettings.ALPHA_MIN..LyricSettings.ALPHA_MAX
        DesktopPreviewType.Battery -> 0.3f..1f
        DesktopPreviewType.Door -> AutoDoorFloatingSettings.ALPHA_MIN..AutoDoorFloatingSettings.ALPHA_MAX
    }

    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.70f))
            .border(1.dp, Color.White.copy(alpha = 0.88f), shape)
            .padding(horizontal = 30.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(selected.label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Switch(checked = enabled, onCheckedChange = onToggle, colors = com.byd.wjx.ui.components.bydSwitchColors())
            Text(if (enabled) "已开启" else "已关闭", color = if (enabled) AccentGreen else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        QuickSlider(
            label = if (selected == DesktopPreviewType.DesktopLyric || selected == DesktopPreviewType.ClusterLyric) "字体大小" else "组件大小",
            value = primaryValue,
            range = primaryRange,
            enabled = enabled,
            onValueChange = onPrimaryValueChange,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        if (selected == DesktopPreviewType.DesktopLyric) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("歌词颜色", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Color.White, SceneMint, SceneBlue, ScenePink, SceneAmber, SceneViolet).forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(color)
                                .border(if (index == 0) 2.dp else 1.dp, if (index == 0) SceneBlue else Color.White, RoundedCornerShape(9.dp)),
                        )
                    }
                }
                Text("仅桌面显示 · 透明背景", color = TextSecondary, fontSize = 11.sp)
            }
        } else {
            QuickSlider(
                label = "透明度",
                value = secondaryValue,
                range = secondaryRange,
                enabled = enabled,
                onValueChange = onSecondaryValueChange,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.width(1.dp).height(86.dp).background(TextPrimary.copy(alpha = 0.10f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onReset,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SceneViolet.copy(alpha = 0.78f), contentColor = Color.White),
                modifier = Modifier.width(126.dp).height(48.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                Text("重置位置", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Text("实时预览", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun QuickSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = SceneBlue,
                inactiveTrackColor = TextPrimary.copy(alpha = 0.10f),
            ),
        )
    }
}

@Composable
private fun DesktopSummaryCards(
    soundFloatingEnabled: Boolean,
    widgetEnabled: Boolean,
    autoDoorFloatingEnabled: Boolean,
    lyricFeatureAllowed: Boolean,
    lyricDesktopActive: Boolean,
    lyricClusterActive: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 900.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopSoundSummary(soundFloatingEnabled, Modifier.weight(1f))
                    DesktopWidgetSummary(widgetEnabled, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopDoorSummary(autoDoorFloatingEnabled, Modifier.weight(1f))
                    DesktopLyricSummary(
                        lyricFeatureAllowed,
                        lyricDesktopActive,
                        lyricClusterActive,
                        Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DesktopSoundSummary(soundFloatingEnabled, Modifier.weight(1f))
                DesktopWidgetSummary(widgetEnabled, Modifier.weight(1f))
                DesktopDoorSummary(autoDoorFloatingEnabled, Modifier.weight(1f))
                DesktopLyricSummary(
                    lyricFeatureAllowed,
                    lyricDesktopActive,
                    lyricClusterActive,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DesktopSoundSummary(enabled: Boolean, modifier: Modifier = Modifier) {
    ProInfoCard(
        title = "音效悬浮窗",
        value = if (enabled) "已开启" else "未开启",
        caption = "桌面快速播放音效和实时喊话",
        icon = Icons.Outlined.GraphicEq,
        tint = AccentGreen,
        modifier = modifier,
    )
}

@Composable
private fun DesktopWidgetSummary(enabled: Boolean, modifier: Modifier = Modifier) {
    ProInfoCard(
        title = "电量小组件",
        value = if (enabled) "已显示" else "未显示",
        caption = "电量、续航、能耗状态",
        icon = Icons.Outlined.Widgets,
        tint = AccentBlue,
        modifier = modifier,
    )
}

@Composable
private fun DesktopDoorSummary(enabled: Boolean, modifier: Modifier = Modifier) {
    ProInfoCard(
        title = "自动门",
        value = if (enabled) "已固定" else "未开启",
        caption = "桌面控制左右电滑门",
        icon = Icons.Outlined.Widgets,
        tint = AccentBlue,
        modifier = modifier,
    )
}

@Composable
private fun DesktopLyricSummary(
    featureAllowed: Boolean,
    desktopActive: Boolean,
    clusterActive: Boolean,
    modifier: Modifier = Modifier,
) {
    ProInfoCard(
        title = "桌面歌词",
        value = when {
            !featureAllowed -> "需激活"
            desktopActive && clusterActive -> "双屏"
            desktopActive -> "中控"
            clusterActive -> "仪表"
            else -> "未开启"
        },
        caption = "透明歌词和仪表投屏",
        icon = Icons.Outlined.Subtitles,
        tint = AccentGreen,
        modifier = modifier,
    )
}

@Composable
private fun DetailToggleRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurfaceElevated.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (expanded) AccentGreen else CardSurface,
                contentColor = if (expanded) AccentContent else TextPrimary,
            ),
            modifier = Modifier.height(38.dp),
        ) {
            Text(if (expanded) "收起调节" else "展开调节", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ClusterLyricNudgeControls(
    enabled: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("仪表位置微调", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NudgeButton("上", enabled, onUp, Modifier.weight(1f))
            NudgeButton("下", enabled, onDown, Modifier.weight(1f))
            NudgeButton("左", enabled, onLeft, Modifier.weight(1f))
            NudgeButton("右", enabled, onRight, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
        ),
        modifier = modifier.height(38.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PresetChipRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (id, label) ->
                val selected = id == selectedId
                Button(
                    onClick = { onSelect(id) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AccentGreen else CardSurfaceElevated,
                        contentColor = if (selected) AccentContent else TextPrimary,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ColorPresetChipRow(
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("歌词颜色", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LyricStylePresets.colors.forEach { preset ->
                val selected = preset.id == selectedId
                Button(
                    onClick = { onSelect(preset.id) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) {
                            androidx.compose.ui.graphics.Color(preset.currentColor)
                        } else {
                            CardSurfaceElevated
                        },
                        contentColor = if (selected) {
                            androidx.compose.ui.graphics.Color.Black
                        } else {
                            TextPrimary
                        },
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(preset.label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun FloatingIconOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.18f) else CardSurface, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (selected) AccentGreen else CardBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(label, color = if (selected) AccentGreen else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = com.byd.wjx.ui.components.bydSwitchColors(),
        )
    }
}

@Composable
private fun WidgetSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
                inactiveTrackColor = TextMuted.copy(alpha = 0.24f),
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = TextMuted.copy(alpha = 0.35f),
            ),
        )
    }
}

private const val DESKTOP_CONTROLS_PREFS = "desktop_widget_controls"
private const val KEY_SOUND_CONTROLS_EXPANDED = "sound_controls_expanded"
private const val KEY_AUTO_DOOR_CONTROLS_EXPANDED = "auto_door_controls_expanded"
private const val KEY_LYRIC_CONTROLS_EXPANDED = "lyric_controls_expanded"
private const val KEY_WIDGET_CONTROLS_EXPANDED = "widget_controls_expanded"
