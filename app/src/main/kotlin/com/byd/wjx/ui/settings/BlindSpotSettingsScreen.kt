package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.camera.blindspot.BlindSpotCalibration
import com.byd.wjx.camera.blindspot.BlindSpotCalibrationViewport
import com.byd.wjx.camera.blindspot.BlindSpotCrop
import com.byd.wjx.camera.blindspot.BlindSpotDisplayTarget
import com.byd.wjx.camera.blindspot.BlindSpotDewarpProjection
import com.byd.wjx.camera.blindspot.BlindSpotPreferenceSnapshot
import com.byd.wjx.camera.blindspot.BlindSpotPreferences
import com.byd.wjx.camera.blindspot.BlindSpotRuntimeState
import com.byd.wjx.camera.blindspot.BlindSpotScaleMode
import com.byd.wjx.camera.blindspot.BlindSpotSide
import com.byd.wjx.camera.blindspot.BlindSpotSideConfig
import com.byd.wjx.camera.blindspot.BlindSpotStatusCode
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

private enum class BlindSpotSettingsTab { CALIBRATION, TRIGGERS }

@Composable
fun BlindSpotSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val appState by viewModel.uiState.collectAsStateWithLifecycle()
    val activationAllowed = appState.activation.activated || appState.activation.trialActive
    val preferences = remember(context) { BlindSpotPreferences(context) }
    val initialSnapshot = remember(preferences) { preferences.snapshot() }
    var persistedSnapshot by remember(preferences) { mutableStateOf(initialSnapshot) }
    var snapshot by remember(preferences) { mutableStateOf(initialSnapshot) }
    var selectedSide by rememberSaveable { mutableStateOf(BlindSpotSide.LEFT) }
    var selectedTab by rememberSaveable { mutableStateOf(BlindSpotSettingsTab.CALIBRATION) }
    var editingCrop by rememberSaveable { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(BlindSpotCalibrationViewport()) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var resumed by remember { mutableStateOf(true) }
    val runtimeStatus by BlindSpotRuntimeState.status.collectAsStateWithLifecycle()
    val cropEdit by BlindSpotRuntimeState.calibrationCropEdit.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val overlayPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { overlayGranted = Settings.canDrawOverlays(context) }
    val appPermissionSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            appPermissionSettings.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else if (!Settings.canDrawOverlays(context)) {
            overlayPermission.launch(overlayPermissionIntent(context.packageName))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    BlindSpotRuntimeState.stopCalibration()
                }
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    cameraGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    overlayGranted = Settings.canDrawOverlays(context)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            BlindSpotRuntimeState.stopCalibration()
        }
    }

    LaunchedEffect(cropEdit?.revision) {
        val edit = cropEdit ?: return@LaunchedEffect
        val config = snapshot.side(edit.side)
        val next = config.copy(
            calibration = config.calibration.withCrop(edit.corrected, edit.crop),
        )
        snapshot = snapshot.withSide(next)
        BlindSpotRuntimeState.publishCalibrationPreview(next)
    }

    val currentConfig = snapshot.side(selectedSide)
    val persistedConfig = persistedSnapshot.side(selectedSide)
    val hasUnsavedChanges = currentConfig != persistedConfig
    val previewReady = activationAllowed && snapshot.enabled && cameraGranted && overlayGranted &&
        resumed && viewport.width > 0 && viewport.height > 0
    LaunchedEffect(selectedSide, previewReady, viewport, editingCrop) {
        if (previewReady) {
            BlindSpotRuntimeState.requestCalibration(
                side = selectedSide,
                left = viewport.left,
                top = viewport.top,
                width = viewport.width,
                height = viewport.height,
                editingCrop = editingCrop,
            )
        } else {
            BlindSpotRuntimeState.stopCalibration()
        }
    }

    LaunchedEffect(previewReady, currentConfig) {
        if (previewReady) BlindSpotRuntimeState.publishCalibrationPreview(currentConfig)
    }

    val controlsEnabled = activationAllowed && snapshot.enabled
    fun updateCurrent(next: BlindSpotSideConfig) {
        snapshot = snapshot.withSide(next)
        BlindSpotRuntimeState.publishCalibrationPreview(next)
    }

    fun saveCurrent() {
        persistSide(preferences, currentConfig)
        val saved = preferences.snapshot().side(selectedSide)
        persistedSnapshot = persistedSnapshot.withSide(saved)
        snapshot = snapshot.withSide(saved)
        editingCrop = false
        BlindSpotRuntimeState.publishCalibrationPreview(saved)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BlindSpotHeader(
            enabled = snapshot.enabled,
            activationAllowed = activationAllowed,
            status = blindSpotStatusLabel(runtimeStatus.code),
            statusWarning = runtimeStatus.code in setOf(
                BlindSpotStatusCode.CAMERA_PERMISSION_REQUIRED,
                BlindSpotStatusCode.OVERLAY_PERMISSION_REQUIRED,
                BlindSpotStatusCode.TELEMETRY_UNAVAILABLE,
                BlindSpotStatusCode.CAMERA_UNAVAILABLE,
            ),
            onBack = onBack,
            onEnabledChange = { enabled ->
                preferences.setEnabled(enabled)
                snapshot = snapshot.copy(enabled = enabled)
                if (enabled && !cameraGranted) {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                } else if (enabled && !overlayGranted) {
                    overlayPermission.launch(overlayPermissionIntent(context.packageName))
                }
            },
        )

        if (!cameraGranted || !overlayGranted) {
            PermissionBar(
                activationAllowed = activationAllowed,
                cameraGranted = cameraGranted,
                overlayGranted = overlayGranted,
                onCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                onOverlay = {
                    overlayPermission.launch(overlayPermissionIntent(context.packageName))
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BlindSpotPreviewPane(
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
                selectedSide = selectedSide,
                crop = currentConfig.calibration.crop(currentConfig.calibration.dewarpEnabled),
                stageLabel = when {
                    editingCrop && currentConfig.calibration.dewarpEnabled -> "框选模式 · 鱼眼矫正后源画面"
                    editingCrop -> "框选模式 · 原始源画面"
                    else -> "最终效果预览"
                },
                editingCrop = editingCrop,
                hasUnsavedChanges = hasUnsavedChanges,
                previewReady = previewReady,
                statusLabel = blindSpotPreviewHint(
                    activationAllowed,
                    snapshot.enabled,
                    cameraGranted,
                    overlayGranted,
                    runtimeStatus.code,
                ),
                onSideSelected = {
                    selectedSide = it
                    editingCrop = false
                },
                onViewportChanged = { next ->
                    if (viewport != next) viewport = next
                },
                onReset = {
                    val reset = currentConfig.copy(
                        calibration = BlindSpotPreferences.defaultCalibration(selectedSide),
                    )
                    updateCurrent(reset)
                    editingCrop = true
                },
                onEditCrop = { editingCrop = true },
                onPreviewFinal = { editingCrop = false },
                onSave = ::saveCurrent,
                resetEnabled = controlsEnabled,
                saveEnabled = controlsEnabled && hasUnsavedChanges,
            )

            Column(
                modifier = Modifier.width(390.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BlindSpotTabSelector(selectedTab) { selectedTab = it }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (selectedTab) {
                        BlindSpotSettingsTab.CALIBRATION -> CalibrationControls(
                            config = currentConfig,
                            otherIndexes = when (selectedSide) {
                                BlindSpotSide.LEFT -> setOf(
                                    snapshot.right.previewIndex,
                                    snapshot.rear.previewIndex,
                                )
                                BlindSpotSide.RIGHT -> setOf(
                                    snapshot.left.previewIndex,
                                    snapshot.rear.previewIndex,
                                )
                                BlindSpotSide.REAR -> setOf(
                                    snapshot.left.previewIndex,
                                    snapshot.right.previewIndex,
                                )
                                BlindSpotSide.NONE -> emptySet()
                            },
                            rearStreamEnabled = snapshot.rearStreamEnabled,
                            enabled = controlsEnabled,
                            onChanged = ::updateCurrent,
                            onRearStreamEnabledChange = {
                                preferences.setRearStreamEnabled(it)
                                snapshot = snapshot.copy(rearStreamEnabled = it)
                            },
                        )
                        BlindSpotSettingsTab.TRIGGERS -> TriggerControls(
                            snapshot = snapshot,
                            enabled = controlsEnabled,
                            onSnapshotChanged = { snapshot = it },
                            preferences = preferences,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun BlindSpotHeader(
    enabled: Boolean,
    activationAllowed: Boolean,
    status: String,
    statusWarning: Boolean,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp).background(CardSurface, RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置", tint = TextPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("电子后视镜", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("直接在实时画面上拖动选区，四角可缩放", color = TextSecondary, fontSize = 13.sp)
        }
        Text(
            status,
            color = if (statusWarning) AccentOrange else AccentGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    (if (statusWarning) AccentOrange else AccentGreen).copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(if (enabled) "已开启" else "未开启", color = TextSecondary, fontSize = 13.sp)
        Switch(
            checked = enabled,
            enabled = activationAllowed,
            onCheckedChange = onEnabledChange,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun PermissionBar(
    activationAllowed: Boolean,
    cameraGranted: Boolean,
    overlayGranted: Boolean,
    onCamera: () -> Unit,
    onOverlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, AccentOrange.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("实时校准需要摄像头和悬浮窗权限", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (!cameraGranted) {
            Button(onClick = onCamera, enabled = activationAllowed, shape = RoundedCornerShape(8.dp)) {
                Text("授权摄像头")
            }
        }
        if (!overlayGranted) {
            Button(onClick = onOverlay, enabled = activationAllowed, shape = RoundedCornerShape(8.dp)) {
                Text("授权悬浮窗")
            }
        }
    }
}

@Composable
private fun BlindSpotPreviewPane(
    modifier: Modifier,
    selectedSide: BlindSpotSide,
    crop: BlindSpotCrop,
    stageLabel: String,
    editingCrop: Boolean,
    hasUnsavedChanges: Boolean,
    previewReady: Boolean,
    statusLabel: String,
    onSideSelected: (BlindSpotSide) -> Unit,
    onViewportChanged: (BlindSpotCalibrationViewport) -> Unit,
    onReset: () -> Unit,
    onEditCrop: () -> Unit,
    onPreviewFinal: () -> Unit,
    onSave: () -> Unit,
    resetEnabled: Boolean,
    saveEnabled: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceButton("左后画面", selectedSide == BlindSpotSide.LEFT) {
                onSideSelected(BlindSpotSide.LEFT)
            }
            ChoiceButton("右后画面", selectedSide == BlindSpotSide.RIGHT) {
                onSideSelected(BlindSpotSide.RIGHT)
            }
            ChoiceButton("后流媒体", selectedSide == BlindSpotSide.REAR) {
                onSideSelected(BlindSpotSide.REAR)
            }
            Spacer(Modifier.weight(1f))
            Text(stageLabel, color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val sourceAspect = 1920f / 1300f
            val stageModifier = if (maxWidth / sourceAspect <= maxHeight) {
                Modifier.fillMaxWidth().aspectRatio(sourceAspect)
            } else {
                Modifier.fillMaxHeight().aspectRatio(sourceAspect)
            }
            Box(
                modifier = stageModifier
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                    .onGloballyPositioned { coordinates ->
                        val point = coordinates.positionOnScreen()
                        onViewportChanged(
                            BlindSpotCalibrationViewport(
                                side = selectedSide,
                                left = point.x.roundToInt(),
                                top = point.y.roundToInt(),
                                width = coordinates.size.width,
                                height = coordinates.size.height,
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (editingCrop) CropGuide(crop)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (previewReady) "正在连接实时画面" else statusLabel,
                        color = if (previewReady) AccentGreen else TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (previewReady) {
                        Text("车辆保持 P/N 挡并静止", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (hasUnsavedChanges) "未保存修改" else "已保存",
                    color = if (hasUnsavedChanges) AccentOrange else AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "位置 ${crop.leftPct}%, ${crop.topPct}% · 大小 ${crop.widthPct}% × ${crop.heightPct}%",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = onReset,
                enabled = resetEnabled,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重置当前侧")
            }
            OutlinedButton(
                onClick = if (editingCrop) onPreviewFinal else onEditCrop,
                enabled = resetEnabled,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Icon(
                    if (editingCrop) Icons.Outlined.Visibility else Icons.Outlined.CropFree,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (editingCrop) "查看效果" else "重新框选")
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存画面设置")
            }
        }
    }
}

@Composable
private fun CropGuide(crop: BlindSpotCrop) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = size.width * crop.leftPct / 100f
        val top = size.height * crop.topPct / 100f
        val right = size.width * (crop.leftPct + crop.widthPct) / 100f
        val bottom = size.height * (crop.topPct + crop.heightPct) / 100f
        val dim = Color.Black.copy(alpha = 0.58f)
        val accent = Color(0xFF20D99A)

        drawRect(dim, size = Size(size.width, top))
        drawRect(dim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(dim, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(dim, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))
        drawRect(
            color = accent,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 3.dp.toPx()),
        )
        listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom),
        ).forEach { point ->
            drawCircle(accent, radius = 8.dp.toPx(), center = point)
            drawCircle(Color.White, radius = 3.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun BlindSpotTabSelector(
    selected: BlindSpotSettingsTab,
    onSelected: (BlindSpotSettingsTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardSurface, RoundedCornerShape(8.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabButton("画面校准", selected == BlindSpotSettingsTab.CALIBRATION, Modifier.weight(1f)) {
            onSelected(BlindSpotSettingsTab.CALIBRATION)
        }
        TabButton("触发规则", selected == BlindSpotSettingsTab.TRIGGERS, Modifier.weight(1f)) {
            onSelected(BlindSpotSettingsTab.TRIGGERS)
        }
    }
}

@Composable
private fun CalibrationControls(
    config: BlindSpotSideConfig,
    otherIndexes: Set<Int>,
    rearStreamEnabled: Boolean,
    enabled: Boolean,
    onChanged: (BlindSpotSideConfig) -> Unit,
    onRearStreamEnabledChange: (Boolean) -> Unit,
) {
    var geometryExpanded by rememberSaveable(config.side) {
        mutableStateOf(config.side == BlindSpotSide.REAR)
    }
    var advancedExpanded by rememberSaveable(config.side) { mutableStateOf(false) }
    val calibration = config.calibration
    fun updateCalibration(next: BlindSpotCalibration) = onChanged(config.copy(calibration = next))

    if (config.side == BlindSpotSide.REAR) {
        SettingsSwitchRow(
            title = "后流媒体显示",
            description = "非 R 挡持续显示，倒车时自动让出给原车倒车影像",
            checked = rearStreamEnabled,
            enabled = enabled,
            onCheckedChange = onRearStreamEnabledChange,
        )
        HorizontalDivider(color = CardBorder)
    }

    ControlSectionTitle("输出位置", "当前侧画面显示在哪里")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceButton(
            label = "中控屏",
            selected = config.target == BlindSpotDisplayTarget.CENTER,
            enabled = enabled,
        ) { onChanged(config.copy(target = BlindSpotDisplayTarget.CENTER)) }
        ChoiceButton(
            label = "仪表盘",
            selected = config.target == BlindSpotDisplayTarget.CLUSTER,
            enabled = enabled,
        ) { onChanged(config.copy(target = BlindSpotDisplayTarget.CLUSTER)) }
    }

    ControlSectionTitle(
        "摄像头通道",
        if (config.side == BlindSpotSide.REAR) {
            "原车倒车后摄默认通道 1，可在实车逐个测试；三路通道不能重复"
        } else {
            "左后、右后、后流媒体三路通道不能重复"
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (BlindSpotPreferences.MIN_PREVIEW_INDEX..BlindSpotPreferences.MAX_PREVIEW_INDEX)
            .toList()
            .chunked(4)
            .forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { index ->
                        Box(modifier = Modifier.weight(1f)) {
                            ChoiceButton(
                                label = index.toString(),
                                selected = config.previewIndex == index,
                                enabled = enabled && index !in otherIndexes,
                                modifier = Modifier.fillMaxWidth(),
                            ) { onChanged(config.copy(previewIndex = index)) }
                        }
                    }
                }
            }
    }

    HorizontalDivider(color = CardBorder)
    SettingsSwitchRow(
        title = "鱼眼矫正",
        description = if (calibration.dewarpEnabled) "框选的是矫正后画面" else "框选的是原始画面",
        checked = calibration.dewarpEnabled,
        enabled = enabled,
        onCheckedChange = { updateCalibration(calibration.copy(dewarpEnabled = it)) },
    )
    IntSettingSlider(
        title = "旋转角度",
        value = calibration.rotationDegrees,
        valueLabel = "${calibration.rotationDegrees}°",
        range = -BlindSpotPreferences.MAX_ROTATION_DEGREES..
            BlindSpotPreferences.MAX_ROTATION_DEGREES,
        step = 5,
        enabled = enabled,
        onValueChange = { updateCalibration(calibration.copy(rotationDegrees = it)) },
    )
    SettingsSwitchRow(
        title = "水平镜像",
        description = "按后视镜习惯左右翻转最终画面",
        checked = calibration.mirrorHorizontally,
        enabled = enabled,
        onCheckedChange = { updateCalibration(calibration.copy(mirrorHorizontally = it)) },
    )
    ControlSectionTitle("填充方式", "控制选中画面如何铺满显示区域")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BlindSpotScaleMode.entries.forEach { mode ->
            ChoiceButton(
                label = when (mode) {
                    BlindSpotScaleMode.FIT -> "完整"
                    BlindSpotScaleMode.FILL -> "铺满"
                    BlindSpotScaleMode.STRETCH -> "拉伸"
                },
                selected = calibration.scaleMode == mode,
                enabled = enabled,
            ) { updateCalibration(calibration.copy(scaleMode = mode)) }
        }
    }

    ExpandableControlSection(
        title = "显示位置与大小",
        subtitle = if (config.side == BlindSpotSide.REAR) {
            "独立调节后流媒体的宽度、高度与位置"
        } else {
            "只影响转向时弹出的窗口"
        },
        expanded = geometryExpanded,
        onToggle = { geometryExpanded = !geometryExpanded },
    ) {
        IntSettingSlider(
            "窗口宽度",
            config.widthPct,
            "${config.widthPct}%",
            BlindSpotPreferences.MIN_WINDOW_PCT..BlindSpotPreferences.MAX_WINDOW_PCT,
            5,
            enabled,
        ) { onChanged(config.copy(widthPct = it)) }
        IntSettingSlider(
            "窗口高度",
            config.heightPct,
            "${config.heightPct}%",
            BlindSpotPreferences.MIN_WINDOW_PCT..BlindSpotPreferences.MAX_WINDOW_PCT,
            5,
            enabled,
        ) { onChanged(config.copy(heightPct = it)) }
        IntSettingSlider("水平位置", config.xPct / 10, "${config.xPct / 10}%", 0..100, 5, enabled) {
            onChanged(config.copy(xPct = it * 10))
        }
        IntSettingSlider("垂直位置", config.yPct / 10, "${config.yPct / 10}%", 0..100, 5, enabled) {
            onChanged(config.copy(yPct = it * 10))
        }
    }

    ExpandableControlSection(
        title = "鱼眼高级参数",
        subtitle = if (calibration.dewarpEnabled) {
            "视场角、投影和光学中心"
        } else {
            "展开后可开启鱼眼矫正并调节参数"
        },
        expanded = advancedExpanded,
        onToggle = { advancedExpanded = !advancedExpanded },
        enabled = enabled,
    ) {
        if (!calibration.dewarpEnabled) {
            SettingsSwitchRow(
                title = "启用鱼眼矫正",
                description = "开启后立即在左侧实时预览矫正效果",
                checked = false,
                enabled = enabled,
                onCheckedChange = {
                    updateCalibration(calibration.copy(dewarpEnabled = it))
                },
            )
            HorizontalDivider(color = CardBorder)
        }
        val dewarpControlsEnabled = enabled && calibration.dewarpEnabled
        IntSettingSlider(
            "视场角",
            calibration.dewarpFovDegrees,
            "${calibration.dewarpFovDegrees}°",
            BlindSpotPreferences.MIN_DEWARP_FOV_DEGREES..
                BlindSpotPreferences.MAX_DEWARP_FOV_DEGREES,
            1,
            dewarpControlsEnabled,
        ) { updateCalibration(calibration.copy(dewarpFovDegrees = it)) }
        ControlSectionTitle("投影方式", "圆柱适合广角，透视更接近普通相机")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceButton(
                label = "圆柱投影",
                selected = calibration.dewarpProjection == BlindSpotDewarpProjection.CYLINDRICAL,
                enabled = dewarpControlsEnabled,
            ) { updateCalibration(calibration.copy(dewarpProjection = BlindSpotDewarpProjection.CYLINDRICAL)) }
            ChoiceButton(
                label = "透视投影",
                selected = calibration.dewarpProjection == BlindSpotDewarpProjection.RECTILINEAR,
                enabled = dewarpControlsEnabled,
            ) { updateCalibration(calibration.copy(dewarpProjection = BlindSpotDewarpProjection.RECTILINEAR)) }
        }
        IntSettingSlider(
            "光学中心 X",
            calibration.dewarpCenterXPct,
            "${calibration.dewarpCenterXPct}%",
            BlindSpotPreferences.MIN_DEWARP_CENTER_PCT..BlindSpotPreferences.MAX_DEWARP_CENTER_PCT,
            1,
            dewarpControlsEnabled,
        ) { updateCalibration(calibration.copy(dewarpCenterXPct = it)) }
        IntSettingSlider(
            "光学中心 Y",
            calibration.dewarpCenterYPct,
            "${calibration.dewarpCenterYPct}%",
            BlindSpotPreferences.MIN_DEWARP_CENTER_PCT..BlindSpotPreferences.MAX_DEWARP_CENTER_PCT,
            1,
            dewarpControlsEnabled,
        ) { updateCalibration(calibration.copy(dewarpCenterYPct = it)) }
    }
}

@Composable
private fun TriggerControls(
    snapshot: BlindSpotPreferenceSnapshot,
    enabled: Boolean,
    onSnapshotChanged: (BlindSpotPreferenceSnapshot) -> Unit,
    preferences: BlindSpotPreferences,
) {
    ControlSectionTitle("速度范围", "转向影像只在设定车速内触发")
    IntSettingSlider(
        "最低车速",
        snapshot.minSpeedKmh,
        "${snapshot.minSpeedKmh} km/h",
        BlindSpotPreferences.MIN_SPEED_KMH..snapshot.maxSpeedKmh,
        5,
        enabled,
    ) {
        preferences.setSpeedRange(it, snapshot.maxSpeedKmh)
        onSnapshotChanged(snapshot.copy(minSpeedKmh = it))
    }
    IntSettingSlider(
        "最高车速",
        snapshot.maxSpeedKmh,
        "${snapshot.maxSpeedKmh} km/h",
        snapshot.minSpeedKmh..BlindSpotPreferences.MAX_SPEED_KMH,
        5,
        enabled,
    ) {
        preferences.setSpeedRange(snapshot.minSpeedKmh, it)
        onSnapshotChanged(snapshot.copy(maxSpeedKmh = it))
    }
    HorizontalDivider(color = CardBorder)
    SettingsSwitchRow(
        "盲区预警高亮",
        "雷达发现同侧目标时提示画面边缘",
        snapshot.bsdGlow,
        enabled,
    ) {
        preferences.setBsdGlow(it)
        onSnapshotChanged(snapshot.copy(bsdGlow = it))
    }
    SettingsSwitchRow(
        "仅有盲区目标时显示",
        "打开转向灯后，有雷达目标才弹出画面",
        snapshot.bsdOnly,
        enabled,
    ) {
        preferences.setBsdOnly(it)
        onSnapshotChanged(snapshot.copy(bsdOnly = it))
    }
    SettingsSwitchRow(
        "盲区目标主动弹窗",
        "不打转向灯时，雷达发现左右盲区目标也会显示对应画面",
        snapshot.bsdAutoPopup,
        enabled,
    ) {
        preferences.setBsdAutoPopup(it)
        onSnapshotChanged(snapshot.copy(bsdAutoPopup = it))
    }
    if (snapshot.bsdAutoPopup) {
        IntSettingSlider(
            "自动弹窗冷却",
            snapshot.bsdAutoCooldownSeconds,
            "${snapshot.bsdAutoCooldownSeconds} 秒",
            BlindSpotPreferences.MIN_BSD_AUTO_COOLDOWN_SECONDS..
                BlindSpotPreferences.MAX_BSD_AUTO_COOLDOWN_SECONDS,
            BlindSpotPreferences.BSD_AUTO_COOLDOWN_STEP_SECONDS,
            enabled,
        ) {
            preferences.setBsdAutoCooldownSeconds(it)
            onSnapshotChanged(snapshot.copy(bsdAutoCooldownSeconds = it))
        }
    }
    SettingsSwitchRow(
        "急转弯辅助显示",
        "方向盘转角达到阈值时显示对应侧画面",
        snapshot.sharpTurnEnabled,
        enabled,
    ) {
        preferences.setSharpTurnEnabled(it)
        onSnapshotChanged(snapshot.copy(sharpTurnEnabled = it))
    }
    if (snapshot.sharpTurnEnabled) {
        IntSettingSlider(
            "方向盘转角阈值",
            snapshot.sharpTurnAngleDeg,
            "${snapshot.sharpTurnAngleDeg}°",
            BlindSpotPreferences.MIN_SHARP_TURN_ANGLE_DEG..
                BlindSpotPreferences.MAX_SHARP_TURN_ANGLE_DEG,
            10,
            enabled,
        ) {
            preferences.setSharpTurnAngleDeg(it)
            onSnapshotChanged(snapshot.copy(sharpTurnAngleDeg = it))
        }
    }
}

@Composable
private fun ExpandableControlSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = if (enabled) AccentGreen else TextMuted,
            )
        }
        if (expanded && enabled) {
            HorizontalDivider(color = CardBorder)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun IntSettingSlider(
    title: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val safeRange = if (range.first < range.last) range else range.first..(range.first + step)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = if (enabled) TextPrimary else TextMuted, fontSize = 13.sp)
            Text(valueLabel, color = if (enabled) AccentGreen else TextMuted, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(safeRange).toFloat(),
            onValueChange = { raw ->
                val next = (((raw.roundToInt() - safeRange.first) / step) * step + safeRange.first)
                    .coerceIn(range)
                onValueChange(next)
            },
            valueRange = safeRange.first.toFloat()..safeRange.last.toFloat(),
            steps = ((safeRange.last - safeRange.first) / step - 1).coerceAtLeast(0),
            enabled = enabled && range.first < range.last,
            colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) TextPrimary else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun ControlSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) AccentGreen else CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AccentGreen.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (selected) AccentGreen else TextPrimary,
            disabledContentColor = TextMuted,
        ),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (selected) CardSurfaceElevated else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) AccentGreen else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun persistSide(preferences: BlindSpotPreferences, config: BlindSpotSideConfig) {
    val current = preferences.snapshot()
    when (config.side) {
        BlindSpotSide.LEFT -> preferences.setPreviewIndexes(
            config.previewIndex,
            current.right.previewIndex,
            current.rear.previewIndex,
        )
        BlindSpotSide.RIGHT -> preferences.setPreviewIndexes(
            current.left.previewIndex,
            config.previewIndex,
            current.rear.previewIndex,
        )
        BlindSpotSide.REAR -> preferences.setPreviewIndexes(
            current.left.previewIndex,
            current.right.previewIndex,
            config.previewIndex,
        )
        BlindSpotSide.NONE -> Unit
    }
    preferences.setTarget(config.side, config.target)
    preferences.setGeometry(config.side, config.widthPct, config.heightPct, config.xPct, config.yPct)
    preferences.setCalibration(config.side, config.calibration)
}

private fun BlindSpotPreferenceSnapshot.withSide(config: BlindSpotSideConfig) = when (config.side) {
    BlindSpotSide.LEFT -> copy(left = config)
    BlindSpotSide.RIGHT -> copy(right = config)
    BlindSpotSide.REAR -> copy(rear = config)
    BlindSpotSide.NONE -> this
}

private fun blindSpotStatusLabel(code: BlindSpotStatusCode): String = when (code) {
    BlindSpotStatusCode.DISABLED -> "功能未运行"
    BlindSpotStatusCode.WAITING -> "等待车辆信号"
    BlindSpotStatusCode.READY -> "准备就绪"
    BlindSpotStatusCode.SHOWING_LEFT -> "正在预览左侧"
    BlindSpotStatusCode.SHOWING_RIGHT -> "正在预览右侧"
    BlindSpotStatusCode.SHOWING_REAR -> "正在显示后流媒体"
    BlindSpotStatusCode.SHOWING_BOTH -> "正在显示两侧"
    BlindSpotStatusCode.SHOWING_MULTIPLE -> "正在显示多路画面"
    BlindSpotStatusCode.CAMERA_PERMISSION_REQUIRED -> "需要摄像头权限"
    BlindSpotStatusCode.OVERLAY_PERMISSION_REQUIRED -> "需要悬浮窗权限"
    BlindSpotStatusCode.TELEMETRY_UNAVAILABLE -> "车辆信号不可用"
    BlindSpotStatusCode.CAMERA_UNAVAILABLE -> "摄像头暂不可用"
    BlindSpotStatusCode.REVERSE_GUARD -> "倒车影像优先"
    BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE -> "原车影像正在使用"
}

private fun blindSpotPreviewHint(
    activationAllowed: Boolean,
    enabled: Boolean,
    cameraGranted: Boolean,
    overlayGranted: Boolean,
    status: BlindSpotStatusCode,
): String = when {
    !activationAllowed -> "当前授权不可用"
    !enabled -> "开启电子后视镜后可实时校准"
    !cameraGranted -> "请先授权摄像头"
    !overlayGranted -> "请先授权悬浮窗"
    status == BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE -> "请先退出原车影像"
    status == BlindSpotStatusCode.REVERSE_GUARD -> "请退出 R 挡"
    else -> "车辆保持 P/N 挡并静止"
}

private fun overlayPermissionIntent(packageName: String) = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:$packageName"),
)
