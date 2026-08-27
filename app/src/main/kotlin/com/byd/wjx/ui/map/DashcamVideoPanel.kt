package com.byd.wjx.ui.map

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.byd.wjx.data.dashcam.DashcamVideoClip
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun DashcamVideoPanel(
    trip: TripEntity,
    clips: List<DashcamVideoClip>,
    initialClipUri: String? = null,
    initialPositionMs: Long = 0L,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (clips.isEmpty()) return

    var currentIndex by remember(clips, initialClipUri, initialPositionMs) {
        mutableIntStateOf(
            clips.indexOfFirst { it.uri == initialClipUri }
                .takeIf { it >= 0 }
                ?: 0,
        )
    }
    val currentClip = clips[currentIndex.coerceIn(clips.indices)]
    val requestedPositionMs = if (currentClip.uri == initialClipUri) initialPositionMs else 0L
    val playbackKey = remember(currentClip.uri, requestedPositionMs) {
        VideoPlaybackKey(currentClip.uri, requestedPositionMs)
    }
    val estimatedDurationMs = (currentClip.endTs - currentClip.startTs).coerceAtLeast(1L)
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var playbackError by remember(currentClip.uri, requestedPositionMs) { mutableStateOf<String?>(null) }
    var prepared by remember(currentClip.uri, requestedPositionMs) { mutableStateOf(false) }
    var playing by remember(currentClip.uri, requestedPositionMs) { mutableStateOf(false) }
    var durationMs by remember(currentClip.uri, requestedPositionMs) {
        mutableLongStateOf(estimatedDurationMs)
    }
    var currentPositionMs by remember(currentClip.uri, requestedPositionMs) {
        mutableLongStateOf(requestedPositionMs.coerceIn(0L, estimatedDurationMs - 1L))
    }
    var pendingSeekMs by remember(currentClip.uri, requestedPositionMs) {
        mutableLongStateOf(currentPositionMs)
    }
    var seeking by remember(currentClip.uri, requestedPositionMs) { mutableStateOf(false) }

    LaunchedEffect(videoViewRef, currentClip.uri, requestedPositionMs) {
        while (isActive) {
            val videoView = videoViewRef
            if (prepared && !seeking && videoView != null) {
                runCatching {
                    currentPositionMs = videoView.currentPosition.toLong().coerceAtLeast(0L)
                    playing = videoView.isPlaying
                }
            }
            delay(250L)
        }
    }

    val videoContent: @Composable (Modifier) -> Unit = { videoModifier ->
        Box(
            modifier = videoModifier.clip(RoundedCornerShape(6.dp)).background(Color.Black),
        ) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        videoViewRef = this
                        val controls = MediaController(context)
                        setMediaController(controls)
                        controls.setAnchorView(this)
                    }
                },
                update = { videoView ->
                    videoViewRef = videoView
                    if (videoView.tag != playbackKey) {
                        playbackError = null
                        prepared = false
                        playing = false
                        videoView.stopPlayback()
                        videoView.tag = playbackKey
                        videoView.setVideoURI(Uri.parse(currentClip.uri))
                        videoView.setOnPreparedListener { player ->
                            durationMs = player.duration.toLong()
                                .takeIf { it > 0L }
                                ?: estimatedDurationMs
                            val seekPosition = requestedPositionMs.coerceIn(
                                0L,
                                (durationMs - 1L).coerceAtLeast(0L),
                            )
                            currentPositionMs = seekPosition
                            pendingSeekMs = seekPosition
                            player.seekTo(seekPosition.toInt())
                            player.start()
                            prepared = true
                            playing = true
                        }
                        videoView.setOnCompletionListener {
                            if (currentIndex < clips.lastIndex) {
                                currentIndex += 1
                            } else {
                                currentPositionMs = durationMs
                                playing = false
                            }
                        }
                        videoView.setOnErrorListener { _, _, _ ->
                            playbackError = "这段录像无法播放，请检查录像格式或存储卡"
                            prepared = false
                            playing = false
                            true
                        }
                    }
                },
                onRelease = VideoView::stopPlayback,
                modifier = Modifier.fillMaxSize(),
            )
            playbackError?.let { message ->
                Text(
                    message,
                    color = AccentOrange,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(CardSurfaceElevated.copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }

    val controlsContent: @Composable (Modifier) -> Unit = { controlsModifier ->
        Column(
            modifier = controlsModifier,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = {
                        videoViewRef?.let { videoView ->
                            if (videoView.isPlaying) videoView.pause() else videoView.start()
                            playing = videoView.isPlaying
                        }
                    },
                    enabled = prepared,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "暂停录像" else "播放录像",
                        tint = if (prepared) AccentGreen else TextSecondary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val shownPositionMs = if (seeking) pendingSeekMs else currentPositionMs
                    Slider(
                        value = shownPositionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { value ->
                            seeking = true
                            pendingSeekMs = value.toLong().coerceIn(0L, durationMs)
                        },
                        onValueChangeFinished = {
                            val seekPosition = pendingSeekMs.coerceIn(
                                0L,
                                (durationMs - 1L).coerceAtLeast(0L),
                            )
                            videoViewRef?.seekTo(seekPosition.toInt())
                            currentPositionMs = seekPosition
                            seeking = false
                        },
                        enabled = prepared && durationMs > 0L,
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGreen,
                            activeTrackColor = AccentGreen,
                            inactiveTrackColor = CardBorder,
                        ),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            formatPlaybackDuration(shownPositionMs),
                            color = TextSecondary,
                            fontSize = 9.sp,
                        )
                        Text(
                            formatPlaybackDuration(durationMs),
                            color = TextSecondary,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { currentIndex -= 1 },
                    enabled = currentIndex > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "上一段录像")
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "第 ${currentIndex + 1}/${clips.size} 段",
                        color = AccentGreen,
                        fontSize = 12.sp,
                    )
                    Text(
                        "${formatDashcamTime(currentClip.startTs)} - ${formatDashcamTime(currentClip.endTs)}",
                        color = TextSecondary,
                        fontSize = 9.sp,
                    )
                }
                IconButton(
                    onClick = { currentIndex += 1 },
                    enabled = currentIndex < clips.lastIndex,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "下一段录像")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(clips, key = { _, clip -> clip.uri }) { index, clip ->
                    FilterChip(
                        selected = currentIndex == index,
                        onClick = { currentIndex = index },
                        label = { Text(formatDashcamClock(clip.startTs), fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.24f),
                        ),
                    )
                }
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = CardSurfaceElevated,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("行程录像", color = TextPrimary, fontSize = 16.sp)
                    Text(
                        "${formatDashcamTime(trip.startTs)} - " +
                            (trip.endTs?.let(::formatDashcamTime) ?: "进行中") +
                            " · ${clips.size} 段",
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭录像", tint = TextPrimary)
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (maxWidth >= 520.dp) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        videoContent(Modifier.weight(1.1f).fillMaxHeight())
                        controlsContent(Modifier.weight(0.9f).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        videoContent(
                            Modifier.fillMaxWidth().weight(1f)
                                .heightIn(min = 80.dp, max = 380.dp),
                        )
                        controlsContent(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private data class VideoPlaybackKey(
    val uri: String,
    val initialPositionMs: Long,
)

private fun formatPlaybackDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }
}

private fun formatDashcamTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))

private fun formatDashcamClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(timestamp))
