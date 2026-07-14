package com.byd.wjx.ui.sound

import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.data.sound.RemoteSound
import com.byd.wjx.data.sound.RemoteSoundCategory
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.storage.SoundDestination
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCoral
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SoundScreen(
    viewModel: SoundViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSound(uri)
    }
    var pendingDownload by remember { mutableStateOf<RemoteSound?>(null) }
    var pendingDelete by remember { mutableStateOf<SoundLibrary.SoundItem?>(null) }
    var onlineSearchVisible by remember { mutableStateOf(false) }

    pendingDownload?.let { sound ->
        DownloadDestinationDialog(
            soundName = sound.name,
            onDismiss = { pendingDownload = null },
            onSelect = { destination ->
                pendingDownload = null
                viewModel.download(sound, destination)
            },
        )
    }

    pendingDelete?.let { sound ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除音效", color = TextPrimary) },
            text = {
                Text(
                    "确定永久删除“${sound.name}”吗？删除后无法恢复。" +
                        "如果智能联动或按键快捷操作正在使用它，删除后相关动作将无法播放，请重新选择音效。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(sound)
                    },
                ) {
                    Text("删除", color = Color(0xFFCC3348), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardSurfaceElevated,
        )
    }

    if (state.audioTuningPanelVisible) {
        AudioTuningSidePanel(
            playbackVolumePercent = state.playbackVolumePercent,
            realtimeVolumePercent = state.realtimeVolumePercent,
            realtimeNoiseSuppressionPercent = state.realtimeNoiseSuppressionPercent,
            realtimeMicGainPercent = state.realtimeMicGainPercent,
            onDismiss = viewModel::toggleAudioTuningPanel,
            onPlaybackVolumeChanged = viewModel::setPlaybackVolumePercent,
            onRealtimeVolumeChanged = viewModel::setRealtimeVolumePercent,
            onNoiseSuppressionChanged = viewModel::setRealtimeNoiseSuppressionPercent,
            onReset = viewModel::resetAudioTuning,
        )
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2200L)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 18.dp),
    ) {
        SoundSceneHeader(
            selectedTab = state.selectedTab,
            hailingRunning = state.realtimeHailingRunning,
            onlineSearchVisible = onlineSearchVisible,
            onTabSelected = { tab ->
                if (tab != SoundTab.Online) onlineSearchVisible = false
                viewModel.selectTab(tab)
            },
            onToggleHailing = viewModel::toggleRealtimeHailing,
            onOpenTuning = viewModel::toggleAudioTuningPanel,
            onOpenSearch = { onlineSearchVisible = !onlineSearchVisible },
            onRefresh = {
                viewModel.refresh()
                if (state.selectedTab == SoundTab.Online) viewModel.refreshOnline()
            },
        )
        Spacer(Modifier.height(10.dp))
        state.message?.let { message ->
            MessageBar(message = message)
            Spacer(Modifier.height(8.dp))
        }
        when (state.selectedTab) {
            SoundTab.Local -> LocalSoundTab(
                state = state,
                onPlay = viewModel::play,
                onDelete = { pendingDelete = it },
                onMove = viewModel::moveSound,
                onRefresh = viewModel::refresh,
                onImport = { importLauncher.launch(arrayOf("audio/*")) },
            )
            SoundTab.Online -> OnlineSoundTab(
                state = state,
                searchVisible = onlineSearchVisible,
                onQueryChange = viewModel::updateRemoteQuery,
                onSearch = viewModel::searchOnline,
                onCategorySelected = viewModel::selectRemoteCategory,
                onPreview = viewModel::preview,
                onDownload = { pendingDownload = it },
                onLoadMore = viewModel::loadMoreOnline,
                onRefresh = { viewModel.refreshOnline() },
            )
        }
    }
}

@Composable
private fun AudioTuningSidePanel(
    playbackVolumePercent: Int,
    realtimeVolumePercent: Int,
    realtimeNoiseSuppressionPercent: Int,
    realtimeMicGainPercent: Int,
    onDismiss: () -> Unit,
    onPlaybackVolumeChanged: (Int) -> Unit,
    onRealtimeVolumeChanged: (Int) -> Unit,
    onNoiseSuppressionChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val panelInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                    .widthIn(min = 390.dp, max = 470.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardSurfaceElevated)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SceneBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = SceneBlue,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("声音调节", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text("音效播放与实时喊话", color = TextSecondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = TextPrimary)
                    }
                }

                SoundTuningSlider(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    label = "音效播放音量",
                    value = playbackVolumePercent,
                    min = ExteriorHailingSettings.MIN_VOLUME_PERCENT,
                    max = ExteriorHailingSettings.MAX_PLAYBACK_VOLUME_PERCENT,
                    color = SceneBlue,
                    onValueChanged = onPlaybackVolumeChanged,
                )
                SoundTuningSlider(
                    icon = Icons.Outlined.Mic,
                    label = "实时喊话音量",
                    value = realtimeVolumePercent,
                    min = ExteriorHailingSettings.MIN_VOLUME_PERCENT,
                    max = ExteriorHailingSettings.MAX_REALTIME_VOLUME_PERCENT,
                    color = ScenePink,
                    onValueChanged = onRealtimeVolumeChanged,
                )
                SoundTuningSlider(
                    icon = Icons.Outlined.GraphicEq,
                    label = "实时喊话降噪",
                    value = realtimeNoiseSuppressionPercent,
                    min = ExteriorHailingSettings.MIN_NOISE_SUPPRESSION_PERCENT,
                    max = ExteriorHailingSettings.MAX_NOISE_SUPPRESSION_PERCENT,
                    color = AccentGreen,
                    onValueChanged = onNoiseSuppressionChanged,
                )
                AutoMicSensitivityRow(value = realtimeMicGainPercent)

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurface,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text("恢复默认", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SoundTuningSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int,
    min: Int,
    max: Int,
    color: Color,
    onValueChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$value%", color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / 2 - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun AutoMicSensitivityRow(value: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = SceneViolet, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("麦克风灵敏度", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("自动 $value%", color = SceneViolet, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(SceneViolet.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value.coerceIn(0, 100) / 100f))
                    .fillMaxHeight()
                    .background(SceneViolet, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun DownloadDestinationDialog(
    soundName: String,
    onDismiss: () -> Unit,
    onSelect: (SoundDestination) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存音效", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    soundName,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadDestinationOption(
                    icon = Icons.Outlined.MusicNote,
                    title = "我的音效",
                    subtitle = "保存到 /sdcard/WJX，用于卡片、悬浮窗和智能联动",
                    color = AccentGreen,
                    onClick = { onSelect(SoundDestination.PERSONAL) },
                )
                DownloadDestinationOption(
                    icon = Icons.Outlined.DirectionsCar,
                    title = "车辆音效",
                    subtitle = "保存到 /sdcard/WJX/车辆音效，用于解锁、锁车、哨兵和自动泊车",
                    color = AccentBlue,
                    onClick = { onSelect(SoundDestination.VEHICLE) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardSurfaceElevated,
    )
}

@Composable
private fun DownloadDestinationOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SoundSceneHeader(
    selectedTab: SoundTab,
    hailingRunning: Boolean,
    onlineSearchVisible: Boolean,
    onTabSelected: (SoundTab) -> Unit,
    onToggleHailing: () -> Unit,
    onOpenTuning: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.weight(0.90f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(24.dp, 40.dp, 58.dp, 36.dp, 20.dp).forEachIndexed { index, barHeight ->
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    listOf(SceneViolet, SceneBlue, SceneCyan, ScenePink, SceneCoral)[index],
                                ),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("个性音效", color = TextPrimary, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Text("让每一次出发都有自己的声音", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            SoundTabSelector(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier
                    .weight(0.92f)
                    .height(64.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(19.dp))
            Text(
                text = if (selectedTab == SoundTab.Local) "我的音效" else "在线音效库",
                color = TextPrimary.copy(alpha = 0.82f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            if (selectedTab == SoundTab.Local || hailingRunning) {
                RealtimeHailingIconButton(
                    running = hailingRunning,
                    onToggle = onToggleHailing,
                )
            }
            if (selectedTab == SoundTab.Local) {
                IconButton(
                    onClick = onOpenTuning,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.36f)),
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "声音调节",
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            if (selectedTab == SoundTab.Online) {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (onlineSearchVisible) SceneBlue.copy(alpha = 0.16f)
                            else Color.White.copy(alpha = 0.36f),
                        )
                        .border(
                            1.dp,
                            if (onlineSearchVisible) SceneBlue.copy(alpha = 0.58f)
                            else Color.White.copy(alpha = 0.64f),
                            CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "搜索在线音效",
                        tint = if (onlineSearchVisible) SceneBlue else TextPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.36f)),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = TextPrimary, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun SoundTabSelector(
    selectedTab: SoundTab,
    onTabSelected: (SoundTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.66f), RoundedCornerShape(999.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoundTab.entries.forEach { tab ->
            SoundHeaderChip(
                label = tab.title,
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SoundHeaderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(SceneViolet, Color(0xFF4F82FF)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RealtimeHailingIconButton(
    running: Boolean,
    onToggle: () -> Unit,
) {
    Box(modifier = Modifier.size(46.dp)) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (running) Color(0xFFE85D76) else Color.White.copy(alpha = 0.36f))
                .border(
                    width = if (running) 2.dp else 1.dp,
                    color = if (running) ScenePink else Color.White.copy(alpha = 0.64f),
                    shape = CircleShape,
                ),
        ) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = if (running) "停止实时喊话" else "开始实时喊话",
                tint = if (running) Color.White else TextPrimary,
                modifier = Modifier.size(23.dp),
            )
        }
        if (running) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF314F))
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun MessageBar(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(message, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun LocalSoundTab(
    state: SoundUiState,
    onPlay: (SoundLibrary.SoundItem) -> Unit,
    onDelete: (SoundLibrary.SoundItem) -> Unit,
    onMove: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentGreen)
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 1000.dp) {
            WideSoundMosaic(
                state = state,
                onPlay = onPlay,
                onDelete = onDelete,
                onMove = onMove,
                onRefresh = onRefresh,
                onImport = onImport,
            )
        } else {
            CompactSoundGrid(
                state = state,
                onPlay = onPlay,
                onDelete = onDelete,
                onMove = onMove,
                onRefresh = onRefresh,
                onImport = onImport,
            )
        }
    }
}

private data class SoundMosaicSlot(
    val xFraction: Float,
    val y: androidx.compose.ui.unit.Dp,
    val widthFraction: Float,
    val height: androidx.compose.ui.unit.Dp,
    val featured: Boolean = false,
)

private val soundMosaicSlots = listOf(
    SoundMosaicSlot(0.00f, 0.dp, 0.38f, 142.dp, featured = true),
    SoundMosaicSlot(0.405f, 0.dp, 0.285f, 114.dp),
    SoundMosaicSlot(0.715f, 5.dp, 0.285f, 114.dp),
    SoundMosaicSlot(0.020f, 153.dp, 0.245f, 108.dp),
    SoundMosaicSlot(0.285f, 142.dp, 0.260f, 114.dp),
    SoundMosaicSlot(0.565f, 153.dp, 0.235f, 108.dp),
    SoundMosaicSlot(0.280f, 270.dp, 0.245f, 99.dp),
    SoundMosaicSlot(0.540f, 274.dp, 0.210f, 95.dp),
    SoundMosaicSlot(0.765f, 266.dp, 0.235f, 103.dp),
)

@Composable
private fun WideSoundMosaic(
    state: SoundUiState,
    onPlay: (SoundLibrary.SoundItem) -> Unit,
    onDelete: (SoundLibrary.SoundItem) -> Unit,
    onMove: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
) {
    val density = LocalDensity.current
    var draggingPath by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var dragOffset by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    var dragIndex by remember { androidx.compose.runtime.mutableStateOf(-1) }
    val entryCount = state.sounds.size + 1
    val pageCount = ((entryCount + soundMosaicSlots.lastIndex) / soundMosaicSlots.size).coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (state.sounds.isEmpty()) {
            item(key = "empty-sound-library") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmptySoundState(onRefresh = onRefresh)
                    ImportSoundCard(onClick = onImport, modifier = Modifier.fillMaxWidth().height(112.dp))
                }
            }
        } else {
            items(pageCount, key = { "sound-mosaic-page-$it" }) { pageIndex ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(374.dp),
                ) {
                    val pageWidthPx = with(density) { maxWidth.toPx() }
                    val pageStart = pageIndex * soundMosaicSlots.size
                    val pageEnd = minOf(pageStart + soundMosaicSlots.size, entryCount)

                    for (globalIndex in pageStart until pageEnd) {
                        val slotIndex = globalIndex - pageStart
                        val slot = soundMosaicSlots[slotIndex]
                        if (globalIndex < state.sounds.size) {
                            val sound = state.sounds[globalIndex]
                            val path = sound.file.absolutePath
                            val isDragging = draggingPath == path
                            SwipeDeleteSoundCard(
                                sound = sound,
                                isPlaying = state.playingPath == path,
                                onClick = { onPlay(sound) },
                                onDelete = { onDelete(sound) },
                                featured = slot.featured,
                                modifier = Modifier
                                    .width(maxWidth * slot.widthFraction)
                                    .height(slot.height)
                                    .offset(x = maxWidth * slot.xFraction, y = slot.y)
                                    .zIndex(if (isDragging) 2f else 0f)
                                    .offset {
                                        if (isDragging) {
                                            IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                        } else {
                                            IntOffset.Zero
                                        }
                                    }
                                    .pointerInput(path, state.sounds.size, pageWidthPx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingPath = path
                                                dragIndex = globalIndex
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                if (draggingPath == path) dragOffset += amount
                                            },
                                            onDragEnd = {
                                                if (draggingPath == path && dragIndex >= 0) {
                                                    val startSlot = soundMosaicSlots[slotIndex]
                                                    val startCenterX = pageWidthPx * (startSlot.xFraction + startSlot.widthFraction / 2f)
                                                    val startCenterY = with(density) { (startSlot.y + startSlot.height / 2f).toPx() }
                                                    val movedCenterX = startCenterX + dragOffset.x
                                                    val movedCenterY = startCenterY + dragOffset.y
                                                    val soundSlotsOnPage = (pageStart until minOf(pageEnd, state.sounds.size)).toList()
                                                    val targetIndex = soundSlotsOnPage.minByOrNull { candidateIndex ->
                                                        val candidate = soundMosaicSlots[candidateIndex - pageStart]
                                                        val candidateCenterX = pageWidthPx * (candidate.xFraction + candidate.widthFraction / 2f)
                                                        val candidateCenterY = with(density) { (candidate.y + candidate.height / 2f).toPx() }
                                                        val dx = candidateCenterX - movedCenterX
                                                        val dy = candidateCenterY - movedCenterY
                                                        dx * dx + dy * dy
                                                    }
                                                    if (targetIndex != null && targetIndex != globalIndex) {
                                                        onMove(path, state.sounds[targetIndex].file.absolutePath)
                                                    }
                                                }
                                                draggingPath = null
                                                dragIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingPath = null
                                                dragIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    },
                            )
                        } else {
                            ImportSoundCard(
                                onClick = onImport,
                                modifier = Modifier
                                    .width(maxWidth * slot.widthFraction)
                                    .height(slot.height)
                                    .offset(x = maxWidth * slot.xFraction, y = slot.y),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSoundGrid(
    state: SoundUiState,
    onPlay: (SoundLibrary.SoundItem) -> Unit,
    onDelete: (SoundLibrary.SoundItem) -> Unit,
    onMove: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1120.dp -> 4
            maxWidth >= 760.dp -> 3
            else -> 2
        }
        val spacing = 12.dp
        val density = LocalDensity.current
        val cellStepPx = with(density) {
            val cell = (maxWidth - spacing * (columns - 1)) / columns
            (cell + spacing).toPx()
        }
        var draggingPath by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        var dragOffset by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
        var dragIndex by remember { androidx.compose.runtime.mutableStateOf(-1) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (state.sounds.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptySoundState(onRefresh = onRefresh)
                }
            } else {
                itemsIndexed(
                    items = state.sounds,
                    key = { _, item -> item.file.absolutePath },
                    span = { index, _ -> GridItemSpan(if (columns >= 4 && index == 0) 2 else 1) },
                ) { index, sound ->
                    val path = sound.file.absolutePath
                    val isDragging = draggingPath == path
                    val featured = columns >= 4 && index == 0
                    SwipeDeleteSoundCard(
                        sound = sound,
                        isPlaying = state.playingPath == path,
                        onClick = { onPlay(sound) },
                        onDelete = { onDelete(sound) },
                        featured = featured,
                        modifier = Modifier
                            .height(if (featured) 142.dp else 114.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                if (isDragging) {
                                    IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                } else {
                                    IntOffset.Zero
                                }
                            }
                            .pointerInput(path, state.sounds.size, cellStepPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingPath = path
                                        dragIndex = index
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (draggingPath == path) dragOffset += amount
                                    },
                                    onDragEnd = {
                                        if (draggingPath == path && dragIndex >= 0 && cellStepPx > 0f) {
                                            val rowDelta = (dragOffset.y / cellStepPx).roundToInt()
                                            val colDelta = (dragOffset.x / cellStepPx).roundToInt()
                                            val fromRow = dragIndex / columns
                                            val fromCol = dragIndex % columns
                                            val targetRow = (fromRow + rowDelta).coerceAtLeast(0)
                                            val targetCol = (fromCol + colDelta).coerceIn(0, columns - 1)
                                            val targetIndex = (targetRow * columns + targetCol)
                                                .coerceIn(0, state.sounds.lastIndex)
                                            val target = state.sounds[targetIndex]
                                            if (target.file.absolutePath != path) {
                                                onMove(path, target.file.absolutePath)
                                            }
                                        }
                                        draggingPath = null
                                        dragIndex = -1
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggingPath = null
                                        dragIndex = -1
                                        dragOffset = Offset.Zero
                                    },
                                )
                            },
                    )
                }
                item {
                    ImportSoundCard(
                        onClick = onImport,
                        modifier = Modifier.height(114.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSoundCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(52.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x49345FD7))
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.72f),
                    cornerRadius = CornerRadius(52.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(12.dp.toPx(), 8.dp.toPx()),
                        ),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("导入音效", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("支持 MP3、WAV、AAC、M4A、OGG、FLAC", color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun EmptySoundState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("还没有本地音效", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("把 MP3、WAV 等音频放到 /sdcard/WJX，或去在线音效库下载", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("刷新列表", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LocalSoundCard(
    sound: SoundLibrary.SoundItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    featured: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = remember(sound.file.absolutePath) { sceneCardPalette(sound.file.absolutePath) }
    val artwork = remember(sound.name, sound.file.absolutePath) {
        soundArtworkRes(sound.name, sound.file.absolutePath)
    }
    val backgroundArtwork = remember(sound.name, sound.file.absolutePath) {
        soundBackgroundRes(
            name = sound.name,
            category = "",
            seed = sound.file.absolutePath.hashCode().toLong(),
        )
    }
    val shape = localSoundShape(featured)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(palette.top, palette.mid, palette.bottom)), shape)
            .border(if (isPlaying) 2.dp else 1.dp, if (isPlaying) palette.accent else Color.White.copy(alpha = 0.70f), shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(backgroundArtwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.74f,
            modifier = Modifier
                .fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.84f),
                            Color.White.copy(alpha = 0.68f),
                            palette.top.copy(alpha = 0.40f),
                            palette.bottom.copy(alpha = 0.20f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (featured) 20.dp else 12.dp,
                    vertical = if (featured) 10.dp else 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (featured) 16.dp else 9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (featured) 78.dp else 54.dp)
                    .clip(RoundedCornerShape(if (featured) 24.dp else 20.dp))
                    .background(Brush.linearGradient(listOf(palette.accent.copy(alpha = 0.34f), palette.accent.copy(alpha = 0.92f)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(artwork),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x5C11172A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (featured) 34.dp else 25.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = sound.name,
                    color = TextPrimary,
                    fontSize = if (featured) 19.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = if (featured) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (featured) 22.sp else 17.sp,
                )
                Spacer(Modifier.height(if (featured) 4.dp else 3.dp))
                SoundWaveform(
                    color = palette.accent,
                    modifier = Modifier.fillMaxWidth().height(if (featured) 24.dp else 14.dp),
                )
                Spacer(Modifier.height(if (featured) 3.dp else 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(if (featured) 32.dp else 26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "停止" else "播放",
                            tint = TextPrimary,
                            modifier = Modifier.size(if (featured) 20.dp else 16.dp),
                        )
                    }
                    Text(
                        text = if (isPlaying) "播放中" else "${sound.ext.uppercase()} · ${sound.sizeLabel}",
                        color = if (isPlaying) palette.accent else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = TextPrimary.copy(alpha = 0.74f), modifier = Modifier.size(if (featured) 19.dp else 17.dp))
                    Icon(Icons.Outlined.DragIndicator, contentDescription = "长按排序", tint = TextPrimary.copy(alpha = 0.68f), modifier = Modifier.size(if (featured) 19.dp else 17.dp))
                }
            }
        }
    }
}

@Composable
private fun SoundWaveform(color: Color, modifier: Modifier = Modifier) {
    val bars = listOf(0.28f, 0.52f, 0.34f, 0.78f, 0.46f, 0.92f, 0.58f, 0.42f, 0.70f, 0.36f, 0.62f, 0.30f, 0.52f, 0.24f)
    Canvas(modifier = modifier) {
        val step = size.width / bars.size
        bars.forEachIndexed { index, fraction ->
            val x = step * index + step / 2f
            val h = size.height * fraction
            drawLine(
                color = color.copy(alpha = 0.72f),
                start = Offset(x, (size.height - h) / 2f),
                end = Offset(x, (size.height + h) / 2f),
                strokeWidth = (step * 0.28f).coerceAtLeast(2f),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SwipeDeleteSoundCard(
    sound: SoundLibrary.SoundItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    featured: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 56.dp.toPx() }
    val deleteThresholdPx = with(density) { 42.dp.toPx() }
    var offsetX by remember(sound.file.absolutePath) { androidx.compose.runtime.mutableStateOf(0f) }
    val shape = localSoundShape(featured)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFB3263B), Color(0xFF4A121C))), shape),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "删除",
            tint = Color.White,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(18.dp),
        )
        LocalSoundCard(
            sound = sound,
            isPlaying = isPlaying,
            onClick = onClick,
            featured = featured,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(sound.file.absolutePath) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            val next = (offsetX + amount).coerceIn(-maxSwipePx, 0f)
                            if (next != offsetX || amount < 0f) {
                                change.consume()
                                offsetX = next
                            }
                        },
                        onDragEnd = {
                            if (-offsetX >= deleteThresholdPx) {
                                offsetX = 0f
                                onDelete()
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                    )
                },
        )
    }
}

private fun localSoundShape(featured: Boolean): RoundedCornerShape = RoundedCornerShape(
    topStart = if (featured) 72.dp else 52.dp,
    topEnd = if (featured) 46.dp else 60.dp,
    bottomEnd = if (featured) 72.dp else 44.dp,
    bottomStart = if (featured) 56.dp else 58.dp,
)

@DrawableRes
private fun soundArtworkRes(name: String, seed: String): Int {
    val normalized = name.lowercase()
    return when {
        "银河" in normalized || "星空" in normalized || "宇宙" in normalized -> R.drawable.sound_art_galaxy
        "机甲" in normalized || "机器人" in normalized || "启动" in normalized -> R.drawable.sound_art_mech
        "回家" in normalized || "欢迎" in normalized || "到家" in normalized -> R.drawable.sound_art_home
        "倒车" in normalized || "泊车" in normalized || "雷达" in normalized -> R.drawable.sound_art_reverse
        "节日" in normalized || "彩蛋" in normalized || "庆祝" in normalized -> R.drawable.sound_art_party
        "充电" in normalized || "电量" in normalized -> R.drawable.sound_art_charge
        "提示" in normalized || "铃" in normalized || "通知" in normalized -> R.drawable.sound_art_bell
        else -> listOf(
            R.drawable.sound_art_wave,
            R.drawable.sound_art_galaxy,
            R.drawable.sound_art_home,
            R.drawable.sound_art_party,
            R.drawable.sound_art_bell,
        )[(seed.hashCode() and Int.MAX_VALUE) % 5]
    }
}

@DrawableRes
private fun soundBackgroundRes(name: String, category: String, seed: Long): Int {
    val normalizedName = name.lowercase()
    val normalizedCategory = category.lowercase()
    val direct = when {
        listOf("能量", "充电", "电力", "电量", "提示", "铃", "通知").any(normalizedName::contains) -> R.drawable.sound_bg_energy_awaken
        listOf("银河", "星空", "宇宙", "星云").any(normalizedName::contains) -> R.drawable.sound_bg_galaxy_start
        listOf("机甲", "机器人", "机械", "启动").any(normalizedName::contains) -> R.drawable.sound_bg_mech_awaken
        listOf("星际", "飞船", "太空", "启程").any(normalizedName::contains) -> R.drawable.sound_bg_interstellar
        listOf("运动", "律动", "速度", "跑车").any(normalizedName::contains) -> R.drawable.sound_bg_sport_rhythm
        listOf("竹", "清风", "森林", "自然").any(normalizedName::contains) -> R.drawable.sound_bg_bamboo_breeze
        listOf("国风", "古风", "雅韵", "山水").any(normalizedName::contains) -> R.drawable.sound_bg_chinese_elegance
        listOf("海浪", "海洋", "潮汐", "浪花").any(normalizedName::contains) -> R.drawable.sound_bg_ocean_whisper
        listOf("雨夜", "白噪", "雨声", "睡眠").any(normalizedName::contains) -> R.drawable.sound_bg_rainy_noise
        listOf("未来", "城市", "科技", "赛博", "倒车", "泊车", "雷达", "哨兵").any(normalizedName::contains) -> R.drawable.sound_bg_future_city
        listOf("回家", "欢迎", "到家", "归家", "锁车", "解锁").any(normalizedName::contains) -> R.drawable.sound_bg_welcome_home
        listOf("节日", "庆祝", "欢庆", "烟花", "彩蛋", "新年").any(normalizedName::contains) -> R.drawable.sound_bg_festival
        else -> null
    }
    if (direct != null) return direct

    val candidates = when {
        "启动" in normalizedCategory -> listOf(
            R.drawable.sound_bg_galaxy_start,
            R.drawable.sound_bg_mech_awaken,
            R.drawable.sound_bg_interstellar,
            R.drawable.sound_bg_energy_awaken,
        )
        "氛围" in normalizedCategory -> listOf(
            R.drawable.sound_bg_bamboo_breeze,
            R.drawable.sound_bg_chinese_elegance,
            R.drawable.sound_bg_ocean_whisper,
            R.drawable.sound_bg_rainy_noise,
            R.drawable.sound_bg_future_city,
        )
        "趣味" in normalizedCategory -> listOf(
            R.drawable.sound_bg_sport_rhythm,
            R.drawable.sound_bg_future_city,
            R.drawable.sound_bg_festival,
        )
        "节日" in normalizedCategory -> listOf(
            R.drawable.sound_bg_festival,
            R.drawable.sound_bg_welcome_home,
        )
        "提示" in normalizedCategory -> listOf(
            R.drawable.sound_bg_energy_awaken,
            R.drawable.sound_bg_welcome_home,
            R.drawable.sound_bg_future_city,
        )
        else -> listOf(
            R.drawable.sound_bg_galaxy_start,
            R.drawable.sound_bg_mech_awaken,
            R.drawable.sound_bg_interstellar,
            R.drawable.sound_bg_sport_rhythm,
            R.drawable.sound_bg_bamboo_breeze,
            R.drawable.sound_bg_chinese_elegance,
            R.drawable.sound_bg_ocean_whisper,
            R.drawable.sound_bg_rainy_noise,
            R.drawable.sound_bg_future_city,
            R.drawable.sound_bg_energy_awaken,
            R.drawable.sound_bg_welcome_home,
            R.drawable.sound_bg_festival,
        )
    }
    return candidates[((seed and Long.MAX_VALUE) % candidates.size).toInt()]
}

@DrawableRes
private fun remoteSoundArtworkRes(sound: RemoteSound): Int = soundBackgroundRes(
    name = sound.name,
    category = sound.category,
    seed = sound.id xor sound.name.hashCode().toLong(),
)

@Composable
private fun OnlineSoundTab(
    state: SoundUiState,
    searchVisible: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onPreview: (RemoteSound) -> Unit,
    onDownload: (RemoteSound) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (searchVisible) {
            OnlineSearchBar(
                query = state.remoteQuery,
                loading = state.remoteLoading,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
            )
        }
        CategoryRow(
            categories = state.remoteCategories,
            selected = state.remoteCategory,
            onSelected = onCategorySelected,
        )
        state.remoteError?.let { error -> MessageBar(message = error) }
        OnlineSoundList(
            state = state,
            onPreview = onPreview,
            onDownload = onDownload,
            onLoadMore = onLoadMore,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OnlineSoundList(
    state: SoundUiState,
    onPreview: (RemoteSound) -> Unit,
    onDownload: (RemoteSound) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rowSpacing = 6.dp
        val cardHeight = ((maxHeight - rowSpacing * 2) / 3f).coerceIn(100.dp, 112.dp)
        val columns = when {
            maxWidth >= 1200.dp -> 6
            maxWidth >= 900.dp -> 5
            maxWidth >= 720.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            if (state.remoteLoading && state.remoteSounds.isEmpty()) {
                item {
                    Box(modifier = Modifier.height(180.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGreen)
                    }
                }
            } else if (state.remoteSounds.isEmpty()) {
                item {
                    EmptyOnlineState(onRefresh = onRefresh)
                }
            } else {
                items(
                    state.remoteSounds.chunked(columns),
                    key = { row -> row.joinToString("-") { it.id.toString() } },
                ) { rowSounds ->
                    Row(horizontalArrangement = Arrangement.spacedBy(rowSpacing), modifier = Modifier.fillMaxWidth()) {
                        rowSounds.forEach { sound ->
                            RemoteSoundCard(
                                sound = sound,
                                previewing = state.previewingRemoteId == sound.id,
                                previewLoading = sound.id in state.previewLoadingIds,
                                downloading = sound.id in state.downloadingIds,
                                onPreview = { onPreview(sound) },
                                onDownload = { onDownload(sound) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cardHeight),
                            )
                        }
                        repeat(columns - rowSounds.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    LoadMoreRow(
                        total = state.remoteTotal,
                        current = state.remoteSounds.size,
                        hasMore = state.remoteHasMore,
                        loading = state.remoteLoading,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineSearchBar(
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true,
                placeholder = { Text("搜索音效", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = CardBorder,
                    cursorColor = AccentGreen,
                ),
            )
            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentGreen),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = AccentContent, modifier = Modifier.size(21.dp))
            }
        }
        if (loading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentGreen, trackColor = CardSurface)
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<RemoteSoundCategory>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            CategoryChip(label = "全部", selected = selected == null, onClick = { onSelected(null) })
        }
        items(categories, key = { it.name }) { category ->
            CategoryChip(
                label = category.name,
                selected = selected == category.name,
                onClick = { onSelected(category.name) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        modifier = Modifier.height(38.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentBlue,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
private fun RemoteSoundCard(
    sound: RemoteSound,
    previewing: Boolean,
    previewLoading: Boolean,
    downloading: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = remember(sound.id) { cardPalette(sound.name + sound.id) }
    val artwork = remember(sound.id, sound.name, sound.category) { remoteSoundArtworkRes(sound) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.bottom, shape)
            .border(
                if (previewing) 2.dp else 1.dp,
                if (previewing) palette.accent else Color.White.copy(alpha = 0.48f),
                shape,
            ),
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.top.copy(alpha = 0.20f),
                            palette.mid.copy(alpha = 0.34f),
                            palette.bottom.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = sound.category.ifBlank { "未分类" },
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 96.dp)
                        .background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                PlayStateBadge(
                    playing = previewing,
                    loading = previewLoading,
                    palette = palette,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = sound.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SoundWaveform(
                    color = palette.accent,
                    modifier = Modifier.weight(1f).height(14.dp),
                )
                CardIconButton(
                    onClick = onPreview,
                    enabled = true,
                    palette = palette,
                    filled = false,
                ) {
                    when {
                        previewLoading -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        previewing -> Icon(Icons.Outlined.Stop, contentDescription = "停止试听", tint = palette.accent, modifier = Modifier.size(16.dp))
                        else -> Icon(Icons.Outlined.PlayArrow, contentDescription = "试听", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                CardIconButton(
                    onClick = onDownload,
                    enabled = !downloading,
                    palette = palette,
                    filled = true,
                ) {
                    if (downloading) {
                        CircularProgressIndicator(color = AccentContent, modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Download, contentDescription = "下载", tint = AccentContent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayStateBadge(
    playing: Boolean,
    loading: Boolean,
    palette: SoundCardPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(
                if (playing || loading) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f),
                RoundedCornerShape(999.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(11.dp), strokeWidth = 2.dp)
            playing -> PlayingBars(small = true, color = palette.accent)
            else -> Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun CardIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    palette: SoundCardPalette,
    filled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    when {
                        !enabled -> Color.White.copy(alpha = 0.10f)
                        filled -> palette.accent.copy(alpha = 0.96f)
                        else -> Color.White.copy(alpha = 0.16f)
                    },
                    RoundedCornerShape(7.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyOnlineState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = TextMuted, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("没有找到音效", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("重新加载", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LoadMoreRow(
    total: Int,
    current: Int,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasMore) {
            Button(
                onClick = onLoadMore,
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated, contentColor = TextPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text("加载更多 $current/$total")
            }
        } else {
            Text("已显示 $current/$total", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayingBars(small: Boolean = false, color: Color = AccentGreen) {
    val widths = if (small) 2.dp else 4.dp
    val heights = if (small) listOf(7.dp, 11.dp, 8.dp) else listOf(10.dp, 16.dp, 12.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (small) 1.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .size(width = widths, height = height)
                    .background(color, RoundedCornerShape(999.dp)),
            )
        }
    }
}

private data class SoundCardPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val accent: Color,
)

private fun cardPalette(seed: String): SoundCardPalette {
    val palettes = listOf(
        SoundCardPalette(Color(0xFF255F8F), Color(0xFF173A61), Color(0xFF10213B), Color(0xFF6BD7FF)),
        SoundCardPalette(Color(0xFF883D70), Color(0xFF542548), Color(0xFF241522), Color(0xFFFF8AC7)),
        SoundCardPalette(Color(0xFF357865), Color(0xFF205143), Color(0xFF102B25), Color(0xFF67F0C8)),
        SoundCardPalette(Color(0xFF8A6232), Color(0xFF59401F), Color(0xFF251A0E), Color(0xFFFFC66B)),
        SoundCardPalette(Color(0xFF5D68A6), Color(0xFF323B70), Color(0xFF151A35), Color(0xFFAEB8FF)),
        SoundCardPalette(Color(0xFF8A4136), Color(0xFF5A2824), Color(0xFF241413), Color(0xFFFF9A82)),
        SoundCardPalette(Color(0xFF2F6A8F), Color(0xFF28405D), Color(0xFF151B2C), Color(0xFF7FD2FF)),
        SoundCardPalette(Color(0xFF6E4D9B), Color(0xFF3F2F67), Color(0xFF1A1730), Color(0xFFD1A6FF)),
    )
    val index = (seed.hashCode() and Int.MAX_VALUE) % palettes.size
    return palettes[index]
}

private fun sceneCardPalette(seed: String): SoundCardPalette {
    val palettes = listOf(
        SoundCardPalette(Color(0xFFFFE4D0), Color(0xFFFFCFB0), Color(0xFFFFE7D7), Color(0xFFFF884F)),
        SoundCardPalette(Color(0xFFE1DCFF), Color(0xFFC8C0FF), Color(0xFFE8E3FF), Color(0xFF7357E8)),
        SoundCardPalette(Color(0xFFCDEFFF), Color(0xFFA8DEFF), Color(0xFFD7F3FF), Color(0xFF268FC8)),
        SoundCardPalette(Color(0xFFFFC8DE), Color(0xFFFFA9CE), Color(0xFFFFD5E7), Color(0xFFE64F92)),
        SoundCardPalette(Color(0xFFC2F3F2), Color(0xFF83DDDF), Color(0xFFD0F7F5), Color(0xFF139B9F)),
        SoundCardPalette(Color(0xFFD9C8FF), Color(0xFFB99AF6), Color(0xFFE6DAFF), Color(0xFF7647D4)),
        SoundCardPalette(Color(0xFFFFD7CF), Color(0xFFFFA896), Color(0xFFFFE1D9), Color(0xFFE85D4A)),
        SoundCardPalette(Color(0xFFC9E3FF), Color(0xFF99C7FF), Color(0xFFD8ECFF), Color(0xFF3979D5)),
    )
    return palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
}
