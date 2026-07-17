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
internal fun LocalSoundTab(
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
    var dragIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
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
        var dragIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }

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
internal fun SoundWaveform(color: Color, modifier: Modifier = Modifier) {
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
    var offsetX by remember(sound.file.absolutePath) { androidx.compose.runtime.mutableFloatStateOf(0f) }
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
internal fun remoteSoundArtworkRes(sound: RemoteSound): Int = soundBackgroundRes(
    name = sound.name,
    category = sound.category,
    seed = sound.id xor sound.name.hashCode().toLong(),
)
