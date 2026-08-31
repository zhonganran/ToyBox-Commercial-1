package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.camera.blindspot.BlindSpotCalibration
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
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun BlindSpotSettingsSection(activationAllowed: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { BlindSpotPreferences(context) }
    var snapshot by remember(preferences) { mutableStateOf(preferences.snapshot()) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val runtimeStatus by BlindSpotRuntimeState.status.collectAsStateWithLifecycle()
    val previewSide by BlindSpotRuntimeState.manualPreview.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                BlindSpotRuntimeState.requestPreview(BlindSpotSide.NONE)
            } else if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                overlayGranted = Settings.canDrawOverlays(context)
                snapshot = preferences.snapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            BlindSpotRuntimeState.requestPreview(BlindSpotSide.NONE)
        }
    }

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
        if (granted && snapshot.enabled && !Settings.canDrawOverlays(context)) {
            overlayPermission.launch(overlayPermissionIntent(context.packageName))
        } else if (!granted) {
            appPermissionSettings.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    SectionHeader(text = stringResource(R.string.settings_blind_spot_header))
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = AccentGreen)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_blind_spot_title),
                        color = if (activationAllowed) TextPrimary else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_blind_spot_desc),
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = snapshot.enabled,
                    enabled = activationAllowed,
                    onCheckedChange = { value ->
                        preferences.setEnabled(value)
                        snapshot = snapshot.copy(enabled = value)
                        if (value && !cameraGranted) {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        } else if (value && !overlayGranted) {
                            overlayPermission.launch(overlayPermissionIntent(context.packageName))
                        } else if (!value) {
                            BlindSpotRuntimeState.requestPreview(BlindSpotSide.NONE)
                        }
                    },
                    colors = bydSwitchColors(),
                )
            }

            val statusIsError = runtimeStatus.code in setOf(
                BlindSpotStatusCode.CAMERA_PERMISSION_REQUIRED,
                BlindSpotStatusCode.OVERLAY_PERMISSION_REQUIRED,
                BlindSpotStatusCode.TELEMETRY_UNAVAILABLE,
                BlindSpotStatusCode.CAMERA_UNAVAILABLE,
            )
            Text(
                text = blindSpotStatusText(runtimeStatus.code, runtimeStatus.detail),
                color = if (statusIsError) AccentOrange else AccentGreen,
                fontSize = 12.sp,
            )
            PermissionButtons(
                activationAllowed = activationAllowed,
                cameraGranted = cameraGranted,
                overlayGranted = overlayGranted,
                onCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                onOverlay = {
                    overlayPermission.launch(overlayPermissionIntent(context.packageName))
                },
            )

            HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
            Text(
                stringResource(R.string.settings_blind_spot_trigger_rules),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            BlindSpotSlider(
                label = stringResource(R.string.settings_blind_spot_min_speed),
                description = stringResource(R.string.settings_blind_spot_min_speed_desc),
                value = snapshot.minSpeedKmh,
                valueLabel = "${snapshot.minSpeedKmh} km/h",
                range = BlindSpotPreferences.MIN_SPEED_KMH..snapshot.maxSpeedKmh,
                stepSize = 5,
                enabled = snapshot.enabled && activationAllowed,
                onValueChange = { snapshot = snapshot.copy(minSpeedKmh = it) },
                onFinished = {
                    preferences.setSpeedRange(snapshot.minSpeedKmh, snapshot.maxSpeedKmh)
                },
            )
            BlindSpotSlider(
                label = stringResource(R.string.settings_blind_spot_max_speed),
                description = stringResource(R.string.settings_blind_spot_max_speed_desc),
                value = snapshot.maxSpeedKmh,
                valueLabel = "${snapshot.maxSpeedKmh} km/h",
                range = snapshot.minSpeedKmh..BlindSpotPreferences.MAX_SPEED_KMH,
                stepSize = 5,
                enabled = snapshot.enabled && activationAllowed,
                onValueChange = { snapshot = snapshot.copy(maxSpeedKmh = it) },
                onFinished = {
                    preferences.setSpeedRange(snapshot.minSpeedKmh, snapshot.maxSpeedKmh)
                },
            )
            BlindSpotToggle(
                title = stringResource(R.string.settings_blind_spot_warning),
                description = stringResource(R.string.settings_blind_spot_warning_desc),
                checked = snapshot.bsdGlow,
                enabled = snapshot.enabled && activationAllowed,
                onCheckedChange = {
                    preferences.setBsdGlow(it)
                    snapshot = snapshot.copy(bsdGlow = it)
                },
            )
            BlindSpotToggle(
                title = stringResource(R.string.settings_blind_spot_bsd_only),
                description = stringResource(R.string.settings_blind_spot_bsd_only_desc),
                checked = snapshot.bsdOnly,
                enabled = snapshot.enabled && activationAllowed,
                onCheckedChange = {
                    preferences.setBsdOnly(it)
                    snapshot = snapshot.copy(bsdOnly = it)
                },
            )
            BlindSpotToggle(
                title = stringResource(R.string.settings_blind_spot_sharp_turn),
                description = stringResource(R.string.settings_blind_spot_sharp_turn_desc),
                checked = snapshot.sharpTurnEnabled,
                enabled = snapshot.enabled && activationAllowed,
                onCheckedChange = {
                    preferences.setSharpTurnEnabled(it)
                    snapshot = snapshot.copy(sharpTurnEnabled = it)
                },
            )
            if (snapshot.sharpTurnEnabled) {
                BlindSpotSlider(
                    label = stringResource(R.string.settings_blind_spot_sharp_turn_angle),
                    description = stringResource(R.string.settings_blind_spot_sharp_turn_angle_desc),
                    value = snapshot.sharpTurnAngleDeg,
                    valueLabel = "${snapshot.sharpTurnAngleDeg}°",
                    range = BlindSpotPreferences.MIN_SHARP_TURN_ANGLE_DEG..
                        BlindSpotPreferences.MAX_SHARP_TURN_ANGLE_DEG,
                    stepSize = 10,
                    enabled = snapshot.enabled && activationAllowed,
                    onValueChange = { snapshot = snapshot.copy(sharpTurnAngleDeg = it) },
                    onFinished = { preferences.setSharpTurnAngleDeg(snapshot.sharpTurnAngleDeg) },
                )
            }

            HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
            SideEditor(
                title = stringResource(R.string.settings_blind_spot_left_settings),
                config = snapshot.left,
                otherIndex = snapshot.right.previewIndex,
                enabled = snapshot.enabled && activationAllowed,
                onConfigChanged = { next ->
                    persistSide(preferences, next)
                    snapshot = snapshot.withSide(next)
                },
                onResetCalibration = {
                    preferences.resetCalibration(BlindSpotSide.LEFT)
                    snapshot = snapshot.withSide(
                        snapshot.left.copy(
                            calibration = BlindSpotPreferences.defaultCalibration(BlindSpotSide.LEFT),
                        ),
                    )
                },
            )

            HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
            SideEditor(
                title = stringResource(R.string.settings_blind_spot_right_settings),
                config = snapshot.right,
                otherIndex = snapshot.left.previewIndex,
                enabled = snapshot.enabled && activationAllowed,
                onConfigChanged = { next ->
                    persistSide(preferences, next)
                    snapshot = snapshot.withSide(next)
                },
                onResetCalibration = {
                    preferences.resetCalibration(BlindSpotSide.RIGHT)
                    snapshot = snapshot.withSide(
                        snapshot.right.copy(
                            calibration = BlindSpotPreferences.defaultCalibration(BlindSpotSide.RIGHT),
                        ),
                    )
                },
            )

            HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cameraswitch, contentDescription = null, tint = AccentGreen)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        stringResource(R.string.settings_blind_spot_live_preview),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_blind_spot_live_preview_desc),
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UnitChip(
                    label = stringResource(R.string.settings_blind_spot_preview_left),
                    selected = previewSide == BlindSpotSide.LEFT,
                    enabled = snapshot.enabled && activationAllowed && cameraGranted && overlayGranted,
                    onClick = { BlindSpotRuntimeState.requestPreview(BlindSpotSide.LEFT) },
                )
                UnitChip(
                    label = stringResource(R.string.settings_blind_spot_preview_right),
                    selected = previewSide == BlindSpotSide.RIGHT,
                    enabled = snapshot.enabled && activationAllowed && cameraGranted && overlayGranted,
                    onClick = { BlindSpotRuntimeState.requestPreview(BlindSpotSide.RIGHT) },
                )
                UnitChip(
                    label = stringResource(R.string.settings_blind_spot_preview_stop),
                    selected = previewSide == BlindSpotSide.NONE,
                    enabled = snapshot.enabled && activationAllowed,
                    onClick = { BlindSpotRuntimeState.requestPreview(BlindSpotSide.NONE) },
                )
            }
            Text(
                stringResource(R.string.settings_blind_spot_hint),
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SideEditor(
    title: String,
    config: BlindSpotSideConfig,
    otherIndex: Int,
    enabled: Boolean,
    onConfigChanged: (BlindSpotSideConfig) -> Unit,
    onResetCalibration: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.settings_blind_spot_display_target),
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UnitChip(
                label = stringResource(R.string.settings_blind_spot_target_center),
                selected = config.target == BlindSpotDisplayTarget.CENTER,
                enabled = enabled,
                onClick = { onConfigChanged(config.copy(target = BlindSpotDisplayTarget.CENTER)) },
            )
            UnitChip(
                label = stringResource(R.string.settings_blind_spot_target_cluster),
                selected = config.target == BlindSpotDisplayTarget.CLUSTER,
                enabled = enabled,
                onClick = { onConfigChanged(config.copy(target = BlindSpotDisplayTarget.CLUSTER)) },
            )
        }
        PreviewIndexPicker(
            label = stringResource(R.string.settings_blind_spot_camera_index),
            selected = config.previewIndex,
            other = otherIndex,
            enabled = enabled,
            onSelected = { onConfigChanged(config.copy(previewIndex = it)) },
        )
        GeometryEditor(config, enabled, onConfigChanged)
        Text(
            stringResource(R.string.settings_blind_spot_calibration),
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            stringResource(R.string.settings_blind_spot_calibration_desc),
            color = TextSecondary,
            fontSize = 12.sp,
        )
        CalibrationEditor(config, enabled, onConfigChanged)
        Button(
            onClick = onResetCalibration,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(stringResource(R.string.settings_blind_spot_reset_calibration))
        }
    }
}

@Composable
private fun GeometryEditor(
    config: BlindSpotSideConfig,
    enabled: Boolean,
    onConfigChanged: (BlindSpotSideConfig) -> Unit,
) {
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_window_width),
        description = stringResource(R.string.settings_blind_spot_window_width_desc),
        value = config.widthPct,
        valueLabel = "${config.widthPct}%",
        range = BlindSpotPreferences.MIN_WINDOW_PCT..BlindSpotPreferences.MAX_WINDOW_PCT,
        stepSize = 5,
        enabled = enabled,
        onValueChange = { onConfigChanged(config.copy(widthPct = it)) },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_window_height),
        description = stringResource(R.string.settings_blind_spot_window_height_desc),
        value = config.heightPct,
        valueLabel = "${config.heightPct}%",
        range = BlindSpotPreferences.MIN_WINDOW_PCT..BlindSpotPreferences.MAX_WINDOW_PCT,
        stepSize = 5,
        enabled = enabled,
        onValueChange = { onConfigChanged(config.copy(heightPct = it)) },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_horizontal_position),
        description = stringResource(R.string.settings_blind_spot_horizontal_position_desc),
        value = config.xPct / 10,
        valueLabel = "${config.xPct / 10}%",
        range = 0..100,
        stepSize = 5,
        enabled = enabled,
        onValueChange = { onConfigChanged(config.copy(xPct = it * 10)) },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_vertical_position),
        description = stringResource(R.string.settings_blind_spot_vertical_position_desc),
        value = config.yPct / 10,
        valueLabel = "${config.yPct / 10}%",
        range = 0..100,
        stepSize = 5,
        enabled = enabled,
        onValueChange = { onConfigChanged(config.copy(yPct = it * 10)) },
    )
}

@Composable
private fun CalibrationEditor(
    config: BlindSpotSideConfig,
    enabled: Boolean,
    onConfigChanged: (BlindSpotSideConfig) -> Unit,
) {
    val calibration = config.calibration
    fun update(next: BlindSpotCalibration) = onConfigChanged(config.copy(calibration = next))
    val correctedCrop = calibration.dewarpEnabled
    val crop = calibration.crop(correctedCrop)
    fun updateCrop(next: BlindSpotCrop) =
        update(calibration.withCrop(correctedCrop, next))
    Text(
        text = stringResource(
            if (correctedCrop) {
                R.string.settings_blind_spot_crop_stage_corrected
            } else {
                R.string.settings_blind_spot_crop_stage_raw
            },
        ),
        color = AccentGreen,
        fontSize = 12.sp,
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_crop_left),
        description = stringResource(R.string.settings_blind_spot_crop_left_desc),
        value = crop.leftPct,
        valueLabel = "${crop.leftPct}%",
        range = 0..(100 - crop.widthPct),
        stepSize = 1,
        enabled = enabled && crop.widthPct < 100,
        onValueChange = { updateCrop(crop.copy(leftPct = it)) },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_crop_top),
        description = stringResource(R.string.settings_blind_spot_crop_top_desc),
        value = crop.topPct,
        valueLabel = "${crop.topPct}%",
        range = 0..(100 - crop.heightPct),
        stepSize = 1,
        enabled = enabled && crop.heightPct < 100,
        onValueChange = { updateCrop(crop.copy(topPct = it)) },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_crop_width),
        description = stringResource(R.string.settings_blind_spot_crop_width_desc),
        value = crop.widthPct,
        valueLabel = "${crop.widthPct}%",
        range = BlindSpotPreferences.MIN_CROP_PCT..100,
        stepSize = 5,
        enabled = enabled,
        onValueChange = {
            updateCrop(
                crop.copy(
                    widthPct = it,
                    leftPct = crop.leftPct.coerceAtMost(100 - it),
                ),
            )
        },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_crop_height),
        description = stringResource(R.string.settings_blind_spot_crop_height_desc),
        value = crop.heightPct,
        valueLabel = "${crop.heightPct}%",
        range = BlindSpotPreferences.MIN_CROP_PCT..100,
        stepSize = 5,
        enabled = enabled,
        onValueChange = {
            updateCrop(
                crop.copy(
                    heightPct = it,
                    topPct = crop.topPct.coerceAtMost(100 - it),
                ),
            )
        },
    )
    BlindSpotSlider(
        label = stringResource(R.string.settings_blind_spot_rotation),
        description = stringResource(R.string.settings_blind_spot_rotation_desc),
        value = calibration.rotationDegrees,
        valueLabel = "${calibration.rotationDegrees}°",
        range = -BlindSpotPreferences.MAX_ROTATION_DEGREES..
            BlindSpotPreferences.MAX_ROTATION_DEGREES,
        stepSize = 5,
        enabled = enabled,
        onValueChange = { update(calibration.copy(rotationDegrees = it)) },
    )
    BlindSpotToggle(
        title = stringResource(R.string.settings_blind_spot_mirror),
        description = stringResource(R.string.settings_blind_spot_mirror_desc),
        checked = calibration.mirrorHorizontally,
        enabled = enabled,
        onCheckedChange = { update(calibration.copy(mirrorHorizontally = it)) },
    )
    BlindSpotToggle(
        title = stringResource(R.string.settings_blind_spot_dewarp),
        description = stringResource(R.string.settings_blind_spot_dewarp_desc),
        checked = calibration.dewarpEnabled,
        enabled = enabled,
        onCheckedChange = { update(calibration.copy(dewarpEnabled = it)) },
    )
    if (calibration.dewarpEnabled) {
        BlindSpotSlider(
            label = stringResource(R.string.settings_blind_spot_dewarp_fov),
            description = stringResource(R.string.settings_blind_spot_dewarp_fov_desc),
            value = calibration.dewarpFovDegrees,
            valueLabel = "${calibration.dewarpFovDegrees}°",
            range = BlindSpotPreferences.MIN_DEWARP_FOV_DEGREES..
                BlindSpotPreferences.MAX_DEWARP_FOV_DEGREES,
            stepSize = 1,
            enabled = enabled,
            onValueChange = { update(calibration.copy(dewarpFovDegrees = it)) },
        )
        Text(
            stringResource(R.string.settings_blind_spot_dewarp_projection),
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 13.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UnitChip(
                label = stringResource(R.string.settings_blind_spot_projection_cylindrical),
                selected = calibration.dewarpProjection == BlindSpotDewarpProjection.CYLINDRICAL,
                enabled = enabled,
                onClick = {
                    update(
                        calibration.copy(
                            dewarpProjection = BlindSpotDewarpProjection.CYLINDRICAL,
                        ),
                    )
                },
            )
            UnitChip(
                label = stringResource(R.string.settings_blind_spot_projection_rectilinear),
                selected = calibration.dewarpProjection == BlindSpotDewarpProjection.RECTILINEAR,
                enabled = enabled,
                onClick = {
                    update(
                        calibration.copy(
                            dewarpProjection = BlindSpotDewarpProjection.RECTILINEAR,
                        ),
                    )
                },
            )
        }
        BlindSpotSlider(
            label = stringResource(R.string.settings_blind_spot_dewarp_center_x),
            description = stringResource(R.string.settings_blind_spot_dewarp_center_x_desc),
            value = calibration.dewarpCenterXPct,
            valueLabel = "${calibration.dewarpCenterXPct}%",
            range = BlindSpotPreferences.MIN_DEWARP_CENTER_PCT..
                BlindSpotPreferences.MAX_DEWARP_CENTER_PCT,
            stepSize = 1,
            enabled = enabled,
            onValueChange = { update(calibration.copy(dewarpCenterXPct = it)) },
        )
        BlindSpotSlider(
            label = stringResource(R.string.settings_blind_spot_dewarp_center_y),
            description = stringResource(R.string.settings_blind_spot_dewarp_center_y_desc),
            value = calibration.dewarpCenterYPct,
            valueLabel = "${calibration.dewarpCenterYPct}%",
            range = BlindSpotPreferences.MIN_DEWARP_CENTER_PCT..
                BlindSpotPreferences.MAX_DEWARP_CENTER_PCT,
            stepSize = 1,
            enabled = enabled,
            onValueChange = { update(calibration.copy(dewarpCenterYPct = it)) },
        )
    }
    Text(
        stringResource(R.string.settings_blind_spot_scale_mode),
        color = if (enabled) TextPrimary else TextMuted,
        fontSize = 13.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BlindSpotScaleMode.entries.forEach { mode ->
            UnitChip(
                label = when (mode) {
                    BlindSpotScaleMode.FIT -> stringResource(R.string.settings_blind_spot_scale_fit)
                    BlindSpotScaleMode.FILL -> stringResource(R.string.settings_blind_spot_scale_fill)
                    BlindSpotScaleMode.STRETCH -> stringResource(R.string.settings_blind_spot_scale_stretch)
                },
                selected = calibration.scaleMode == mode,
                enabled = enabled,
                onClick = { update(calibration.copy(scaleMode = mode)) },
            )
        }
    }
}

@Composable
private fun PermissionButtons(
    activationAllowed: Boolean,
    cameraGranted: Boolean,
    overlayGranted: Boolean,
    onCamera: () -> Unit,
    onOverlay: () -> Unit,
) {
    if (cameraGranted && overlayGranted) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!cameraGranted) {
            Button(onClick = onCamera, enabled = activationAllowed, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.settings_blind_spot_camera_permission))
            }
        }
        if (!overlayGranted) {
            Button(onClick = onOverlay, enabled = activationAllowed, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.settings_blind_spot_overlay_permission))
            }
        }
    }
}

@Composable
private fun blindSpotStatusText(code: BlindSpotStatusCode, detail: String): String {
    val base = when (code) {
        BlindSpotStatusCode.DISABLED -> stringResource(R.string.settings_blind_spot_status_disabled)
        BlindSpotStatusCode.WAITING -> stringResource(R.string.settings_blind_spot_status_waiting)
        BlindSpotStatusCode.READY -> stringResource(R.string.settings_blind_spot_status_ready)
        BlindSpotStatusCode.SHOWING_LEFT -> stringResource(R.string.settings_blind_spot_status_left)
        BlindSpotStatusCode.SHOWING_RIGHT -> stringResource(R.string.settings_blind_spot_status_right)
        BlindSpotStatusCode.SHOWING_BOTH -> stringResource(R.string.settings_blind_spot_status_both)
        BlindSpotStatusCode.CAMERA_PERMISSION_REQUIRED ->
            stringResource(R.string.settings_blind_spot_status_camera_permission)
        BlindSpotStatusCode.OVERLAY_PERMISSION_REQUIRED ->
            stringResource(R.string.settings_blind_spot_status_overlay_permission)
        BlindSpotStatusCode.TELEMETRY_UNAVAILABLE ->
            stringResource(R.string.settings_blind_spot_status_telemetry)
        BlindSpotStatusCode.CAMERA_UNAVAILABLE ->
            stringResource(R.string.settings_blind_spot_status_camera_error)
        BlindSpotStatusCode.REVERSE_GUARD -> stringResource(R.string.settings_blind_spot_status_reverse)
        BlindSpotStatusCode.FACTORY_CAMERA_ACTIVE ->
            stringResource(R.string.settings_blind_spot_status_factory_camera)
    }
    return if (detail.isBlank() || code in setOf(BlindSpotStatusCode.READY, BlindSpotStatusCode.WAITING)) {
        base
    } else {
        "$base：$detail"
    }
}

@Composable
private fun BlindSpotSlider(
    label: String,
    description: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    stepSize: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onFinished: () -> Unit = {},
) {
    val safeRange = if (range.first < range.last) range else range.first..(range.first + stepSize)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
            Text(valueLabel, color = if (enabled) AccentGreen else TextMuted, fontSize = 13.sp)
        }
        Text(description, color = TextSecondary, fontSize = 12.sp)
        Slider(
            value = value.coerceIn(safeRange).toFloat(),
            onValueChange = { raw ->
                val snapped = ((raw.roundToInt() - safeRange.first) / stepSize) * stepSize + safeRange.first
                onValueChange(snapped.coerceIn(range))
            },
            onValueChangeFinished = onFinished,
            valueRange = safeRange.first.toFloat()..safeRange.last.toFloat(),
            steps = ((safeRange.last - safeRange.first) / stepSize - 1).coerceAtLeast(0),
            enabled = enabled && range.first < range.last,
            colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen),
        )
    }
}

@Composable
private fun BlindSpotToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun PreviewIndexPicker(
    label: String,
    selected: Int,
    other: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label：$selected", color = if (enabled) TextPrimary else TextMuted, fontSize = 13.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (BlindSpotPreferences.MIN_PREVIEW_INDEX..BlindSpotPreferences.MAX_PREVIEW_INDEX).forEach { index ->
                UnitChip(
                    label = index.toString(),
                    selected = index == selected,
                    enabled = enabled && index != other,
                    onClick = { onSelected(index) },
                )
            }
        }
    }
}

private fun persistSide(preferences: BlindSpotPreferences, config: BlindSpotSideConfig) {
    val current = preferences.snapshot()
    val other = current.side(if (config.side == BlindSpotSide.LEFT) BlindSpotSide.RIGHT else BlindSpotSide.LEFT)
    val leftIndex = if (config.side == BlindSpotSide.LEFT) config.previewIndex else other.previewIndex
    val rightIndex = if (config.side == BlindSpotSide.RIGHT) config.previewIndex else other.previewIndex
    preferences.setPreviewIndexes(leftIndex, rightIndex)
    preferences.setTarget(config.side, config.target)
    preferences.setGeometry(config.side, config.widthPct, config.heightPct, config.xPct, config.yPct)
    preferences.setCalibration(config.side, config.calibration)
}

private fun BlindSpotPreferenceSnapshot.withSide(config: BlindSpotSideConfig) = when (config.side) {
    BlindSpotSide.LEFT -> copy(left = config)
    BlindSpotSide.RIGHT -> copy(right = config)
    BlindSpotSide.NONE -> this
}

private fun overlayPermissionIntent(packageName: String) = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:$packageName"),
)
