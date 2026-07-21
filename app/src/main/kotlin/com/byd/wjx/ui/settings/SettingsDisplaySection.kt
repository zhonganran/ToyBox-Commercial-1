package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.cluster.ClusterMode
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.ClusterProjectionPreferences
import com.byd.wjx.cluster.CENTER_OFFSET_PCT
import com.byd.wjx.cluster.MAX_OFFSET_PCT
import com.byd.wjx.cluster.MAX_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_OFFSET_PCT
import com.byd.wjx.cluster.MIN_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_SCALE_PCT
import com.byd.wjx.cluster.MAX_SCALE_PCT
import com.byd.wjx.cluster.DEFAULT_SCALE_PCT
import com.byd.wjx.cluster.NAVI_PACKAGE
import com.byd.wjx.cluster.ProjectionLayout
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutBinding
import com.byd.wjx.cluster.KeyShortcutPressType
import com.byd.wjx.cluster.KeyShortcutSettings
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContactSupport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.byd.wjx.cluster.DEFAULT_TRIGGER_KEYCODE
import com.byd.wjx.cluster.SteeringWheelKeyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import com.byd.wjx.R
import com.byd.wjx.audio.ExteriorChannelMode
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.*
import java.io.File
import java.net.URL

@Composable
internal fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val appRotationConfig by viewModel.appRotationConfig.collectAsStateWithLifecycle()
    val keyShortcutAllowed = state.activation.activated || state.activation.trialActive
    val projectionPreferences = remember(context) { ClusterProjectionPreferences(context) }
    val projectionSnapshot = remember(projectionPreferences) { projectionPreferences.snapshot() }
    val exteriorSettings = remember(context) { ExteriorHailingSettings(context) }
    val keyShortcutSettings = remember(context) { KeyShortcutSettings(context) }
    val soundLibrary = remember(context) { SoundLibrary(context.applicationContext) }
    val carProfile = state.carProfile
    var exteriorChannelMode by remember { mutableStateOf(exteriorSettings.channelMode) }
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ClusterEntryPoint::class.java)
    }
    var keyShortcutBindings by remember { mutableStateOf(keyShortcutSettings.load()) }
    var keyShortcutEditor by remember { mutableStateOf<KeyShortcutEditorState?>(null) }
    var keyShortcutLearning by remember { mutableStateOf(false) }
    var localSounds by remember { mutableStateOf(soundLibrary.listSounds()) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "麦克风权限已允许" else "未允许麦克风权限，实时喊话无法启动", Toast.LENGTH_SHORT).show()
    }
    var enabled by remember {
        mutableStateOf(projectionSnapshot.enabled)
    }
    var widthPct by remember {
        mutableIntStateOf(projectionSnapshot.widthPct)
    }
    var heightPct by remember {
        mutableIntStateOf(projectionSnapshot.heightPct)
    }
    var offsetXPct by remember {
        mutableIntStateOf(projectionSnapshot.offsetXPct)
    }
    var offsetYPct by remember {
        mutableIntStateOf(projectionSnapshot.offsetYPct)
    }
    var scalePct by remember {
        mutableIntStateOf(projectionSnapshot.scalePct)
    }
    var targetPkg by remember {
        mutableStateOf(projectionSnapshot.targetPackage)
    }
    var targetLabel by remember {
        mutableStateOf(
            projectionSnapshot.targetLabel
                ?: resolveAppLabel(context, targetPkg)
        )
    }
    var pickingApp by remember { mutableStateOf(false) }
    var learning by remember { mutableStateOf(false) }
    var triggerKey by remember {
        mutableIntStateOf(projectionSnapshot.triggerKeyCode)
    }
    var projectionLayout by remember {
        mutableStateOf(projectionSnapshot.layout)
    }
    var pickingRotationApp by remember { mutableStateOf(false) }

    SectionHeader(text = "应用显示")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ScreenRotation,
                    contentDescription = null,
                    tint = AccentGreen,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("应用横屏", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (appRotationConfig.targets.isEmpty()) "未选择应用" else "已选择 ${appRotationConfig.targets.size} 个应用",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = appRotationConfig.enabled,
                    onCheckedChange = viewModel::setAppRotationEnabled,
                    colors = bydSwitchColors(),
                )
            }

            appRotationConfig.targets.forEach { target ->
                HorizontalDivider(color = CardBorder.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(target.label, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                        Text(target.packageName, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { viewModel.removeAppRotationTarget(target.packageName) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除 ${target.label}",
                            tint = TextSecondary,
                        )
                    }
                }
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.7f))
            TextButton(
                onClick = { pickingRotationApp = true },
                modifier = Modifier.align(Alignment.End).padding(horizontal = 6.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = AccentGreen)
                Spacer(Modifier.width(6.dp))
                Text("添加应用", color = AccentGreen)
            }
        }
    }

    if (pickingRotationApp) {
        AppLaunchPickerDialog(
            currentPackage = "",
            onDismiss = { pickingRotationApp = false },
            onSelect = { packageName, label ->
                viewModel.addAppRotationTarget(packageName, label)
                viewModel.setAppRotationEnabled(true)
                pickingRotationApp = false
            },
        )
    }

    SectionHeader(text = "车外音效")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = AccentGreen,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("车外通道", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    carProfile?.let(exteriorSettings::channelModeLabel) ?: "自动识别中",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip("自动", exteriorChannelMode == ExteriorChannelMode.AUTO) {
                    exteriorChannelMode = ExteriorChannelMode.AUTO
                    exteriorSettings.channelMode = ExteriorChannelMode.AUTO
                    viewModel.syncLockSoundNow("exterior_channel_auto")
                }
                UnitChip("A", exteriorChannelMode == ExteriorChannelMode.CHANNEL_0) {
                    exteriorChannelMode = ExteriorChannelMode.CHANNEL_0
                    exteriorSettings.channelMode = ExteriorChannelMode.CHANNEL_0
                    viewModel.syncLockSoundNow("exterior_channel_a")
                }
                UnitChip("B", exteriorChannelMode == ExteriorChannelMode.CHANNEL_1) {
                    exteriorChannelMode = ExteriorChannelMode.CHANNEL_1
                    exteriorSettings.channelMode = ExteriorChannelMode.CHANNEL_1
                    viewModel.syncLockSoundNow("exterior_channel_b")
                }
            }
        }
    }

    KeyShortcutSection(
        bindings = keyShortcutBindings,
        activationAllowed = keyShortcutAllowed,
        onAdd = {
            if (!keyShortcutAllowed) {
                Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                return@KeyShortcutSection
            }
            localSounds = soundLibrary.listSounds()
            keyShortcutEditor = KeyShortcutEditorState()
        },
        onEdit = {
            localSounds = soundLibrary.listSounds()
            keyShortcutEditor = it.toEditorState()
        },
        onEnabledChanged = { binding, checked ->
            if (checked && !keyShortcutAllowed) {
                keyShortcutBindings = keyShortcutSettings.upsert(binding.copy(enabled = false))
                Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                return@KeyShortcutSection
            }
            keyShortcutBindings = keyShortcutSettings.upsert(binding.copy(enabled = checked))
            SteeringWheelKeyService.resetShortcutRuntimeState()
            ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
        },
        onDelete = { binding ->
            keyShortcutBindings = keyShortcutSettings.delete(binding.id)
            SteeringWheelKeyService.resetShortcutRuntimeState()
        },
    )

    keyShortcutEditor?.let { editor ->
        KeyShortcutEditorDialog(
            editor = editor,
            sounds = localSounds,
            onEditorChanged = { keyShortcutEditor = it },
            onLearnKey = {
                ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
                keyShortcutLearning = true
            },
            onDismiss = { keyShortcutEditor = null },
            onSave = { next ->
                val binding = next.toBinding()
                if (binding.enabled && !keyShortcutAllowed) {
                    Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                    return@KeyShortcutEditorDialog
                }
                keyShortcutBindings = keyShortcutSettings.upsert(binding)
                SteeringWheelKeyService.resetShortcutRuntimeState()
                keyShortcutEditor = null
                if (
                    binding.actionType in setOf(KeyShortcutActionType.RealtimeHailing, KeyShortcutActionType.Xiaozhi) &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
                val message = if (enabled && binding.keyCode == triggerKey) {
                    "已保存；该按键也用于仪表投射，快捷操作会优先执行"
                } else {
                    "按键快捷操作已保存"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (keyShortcutLearning) {
        LearnButtonDialog(
            onSave = { code ->
                keyShortcutEditor = keyShortcutEditor?.copy(keyCode = code)
                keyShortcutLearning = false
            },
            onDismiss = { keyShortcutLearning = false },
            preferLongPress = keyShortcutEditor?.pressType == KeyShortcutPressType.Long,
        )
    }

    SectionHeader(text = stringResource(R.string.settings_display_mirror_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = AccentGreen,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_display_mirror_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.settings_display_mirror_desc),
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange@{ requested ->
                    val allowed = state.activation.activated || state.activation.trialActive
                    if (requested && !allowed) {
                        enabled = false
                        projectionPreferences.setEnabled(false)
                        Toast.makeText(context, "按键映射到仪表盘需要激活后使用", Toast.LENGTH_SHORT).show()
                        return@onChange
                    }
                    enabled = requested
                    projectionPreferences.setEnabled(requested)
                    if (!requested) {
                        ClusterProjectionManager.setMode(
                            context,
                            ClusterMode.OFF,
                            entryPoint.helperClient(),
                            entryPoint.helperBootstrap(),
                        )
                        ClusterProjectionManager.stopDuplicateProjectionFallback()
                    }
                    // Turning the switch on self-enables our a11y key filter via the daemon, so star
                    // control works on a clean install with no ADB (DiLink has no a11y settings UI).
                    if (requested) {
                        ClusterProjectionManager.enableStarControl(
                            entryPoint.helperClient(), entryPoint.helperBootstrap())
                        if (projectionLayout == ProjectionLayout.DUPLICATE) {
                            ClusterProjectionManager.prepareDuplicateProjection(context)
                        }
                    }
                },
                colors = bydSwitchColors(),
            )
        }
    }

    // Trigger-button row — only meaningful while the feature (and thus the a11y service) is on, so
    // learning is gated on `enabled`. Tapping opens the learn dialog.
    if (enabled) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable {
                    ClusterProjectionManager.enableStarControl(entryPoint.helperClient(), entryPoint.helperBootstrap())
                    learning = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = AccentGreen,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_display_button_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(steeringButtonLabel(triggerKey), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                Text(stringResource(R.string.settings_display_button_change), color = AccentGreen, fontSize = 13.sp)
            }
        }
    }

    if (learning) {
        LearnButtonDialog(
            onSave = { code ->
                triggerKey = code
                ClusterProjectionManager.setTriggerKeyCode(context, code)
                learning = false
            },
            onDismiss = { learning = false },
        )
    }

    SectionHeader(text = stringResource(R.string.settings_display_projection_layout_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip(
                    stringResource(R.string.settings_display_projection_layout_independent),
                    projectionLayout == ProjectionLayout.INDEPENDENT,
                ) {
                    projectionLayout = ProjectionLayout.INDEPENDENT
                    projectionPreferences.setLayout(ProjectionLayout.INDEPENDENT)
                    ClusterProjectionManager.stopDuplicateProjectionFallback()
                    ClusterProjectionManager.reproject(
                        context, entryPoint.helperClient(), entryPoint.helperBootstrap())
                }
                UnitChip(
                    stringResource(R.string.settings_display_projection_layout_duplicate),
                    projectionLayout == ProjectionLayout.DUPLICATE,
                ) {
                    projectionLayout = ProjectionLayout.DUPLICATE
                    projectionPreferences.setLayout(ProjectionLayout.DUPLICATE)
                    if (enabled) {
                        ClusterProjectionManager.prepareDuplicateProjection(context)
                    }
                    ClusterProjectionManager.reproject(
                        context, entryPoint.helperClient(), entryPoint.helperBootstrap())
                }
            }
            Text(
                text = if (projectionLayout == ProjectionLayout.DUPLICATE) {
                    stringResource(R.string.settings_display_projection_layout_duplicate_desc)
                } else {
                    stringResource(R.string.settings_display_projection_layout_independent_desc)
                },
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }

    // App picker only applies to independent mode. Duplicate mode mirrors whatever is already on
    // the centre screen, so showing the picker there would imply a target-app choice that is ignored.
    if (projectionLayout == ProjectionLayout.INDEPENDENT) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { pickingApp = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = AccentGreen,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_display_app_title), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(targetLabel, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }

    if (pickingApp && projectionLayout == ProjectionLayout.INDEPENDENT) {
        AppLaunchPickerDialog(
            currentPackage = targetPkg,
            onDismiss = { pickingApp = false },
            onSelect = { newPkg, newLabel ->
                targetPkg = newPkg
                targetLabel = newLabel
                projectionPreferences.setTarget(newPkg, newLabel)
                pickingApp = false
            },
        )
    }

    if (projectionLayout == ProjectionLayout.INDEPENDENT) {
        SectionHeader(text = stringResource(R.string.settings_display_size_header))
        // Persist the new size and re-apply it live. reproject() is a no-op unless we are actively
        // projecting, so a tweak while OFF just lands in prefs and shows on the next star press.
        val applyGeometry: () -> Unit = {
            projectionPreferences.setGeometry(widthPct, heightPct, offsetXPct, offsetYPct, scalePct)
            ClusterProjectionManager.reproject(
                context, entryPoint.helperClient(), entryPoint.helperBootstrap())
        }
        // Defaults (100/100 size, centered, scale 100) reproduce the plain fullscreen projection, so
        // cars without a native mini zone (e.g. Leopard 3) need no tuning. The offset sliders only
        // matter once the window is smaller than the panel; on Sea Lion 07 they let the user move the
        // window into the native mini-cluster zone (#48).
        ClusterSizeSlider(
            stringResource(R.string.settings_display_size_width), widthPct, enabled,
            { widthPct = it }, applyGeometry,
            description = stringResource(R.string.settings_display_size_width_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_size_height), heightPct, enabled,
            { heightPct = it }, applyGeometry,
            description = stringResource(R.string.settings_display_size_height_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_offset_x), offsetXPct, enabled,
            { offsetXPct = it }, applyGeometry, MIN_OFFSET_PCT, MAX_OFFSET_PCT,
            description = stringResource(R.string.settings_display_offset_x_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_offset_y), offsetYPct, enabled,
            { offsetYPct = it }, applyGeometry, MIN_OFFSET_PCT, MAX_OFFSET_PCT,
            description = stringResource(R.string.settings_display_offset_y_desc),
        )
        ClusterSizeSlider(
            stringResource(R.string.settings_display_scale), scalePct, enabled,
            { scalePct = it }, applyGeometry, MIN_SCALE_PCT, MAX_SCALE_PCT,
            description = stringResource(R.string.settings_display_scale_desc),
        )
    } else {
        Text(
            text = stringResource(R.string.settings_display_duplicate_fullscreen_note),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
