package com.byd.wjx.ui.sound

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.theme.AccentGreen
import kotlin.math.roundToInt

@Composable
internal fun LocalSoundTab(
    state: SoundUiState,
    onPlay: (SoundLibrary.SoundItem) -> Unit,
    onDelete: (SoundLibrary.SoundItem) -> Unit,
    onMove: (String, String) -> Unit,
    onRefresh: () -> Unit,
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
            )
        } else {
            CompactSoundGrid(
                state = state,
                onPlay = onPlay,
                onDelete = onDelete,
                onMove = onMove,
                onRefresh = onRefresh,
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
) {
    val density = LocalDensity.current
    var draggingPath by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var dragOffset by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    var dragIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val entryCount = state.sounds.size
    val pageCount = ((entryCount + soundMosaicSlots.lastIndex) / soundMosaicSlots.size).coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (state.sounds.isEmpty()) {
            item(key = "empty-sound-library") {
                EmptySoundState(onRefresh = onRefresh)
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
            }
        }
    }
}
