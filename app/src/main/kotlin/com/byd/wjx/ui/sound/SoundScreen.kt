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
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
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
    var pendingDelete by remember { mutableStateOf<SoundLibrary.SoundItem?>(null) }
    var onlineSearchVisible by remember { mutableStateOf(false) }

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
                onDownload = viewModel::download,
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
                        .background(CardSurface.copy(alpha = 0.68f)),
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
                            else CardSurface.copy(alpha = 0.68f),
                        )
                        .border(
                            1.dp,
                            if (onlineSearchVisible) SceneBlue.copy(alpha = 0.58f)
                            else CardBorder,
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
                    .background(CardSurface.copy(alpha = 0.68f)),
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
            .background(CardSurface.copy(alpha = 0.66f))
            .border(1.dp, CardBorder, RoundedCornerShape(999.dp))
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
internal fun MessageBar(message: String) {
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
