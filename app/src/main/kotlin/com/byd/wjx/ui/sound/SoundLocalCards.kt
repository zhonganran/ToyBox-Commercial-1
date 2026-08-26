package com.byd.wjx.ui.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.LocalSuperToyBoxDarkTheme
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun ImportSoundCard(
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
internal fun EmptySoundState(onRefresh: () -> Unit) {
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
            Text("点击导入音效，添加 MP3、WAV 等音频文件", color = TextMuted, fontSize = 12.sp)
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
    val darkTheme = LocalSuperToyBoxDarkTheme.current
    val palette = remember(sound.file.absolutePath, darkTheme) {
        if (darkTheme) darkLocalSoundCardPalette(sound.file.absolutePath) else sceneCardPalette(sound.file.absolutePath)
    }
    val titleColor = if (darkTheme) Color(0xFFD7DEE4) else TextPrimary
    val secondaryColor = if (darkTheme) Color(0xFFA5B1BA) else TextSecondary
    val iconColor = if (darkTheme) Color(0xFFC2CBD2) else TextPrimary
    val accentColor = if (darkTheme) palette.accent.copy(alpha = 0.78f) else palette.accent
    val idleBorderColor = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.70f)
    val cardOverlay = if (darkTheme) {
        listOf(
            Color(0xFF11161A).copy(alpha = 0.78f),
            Color(0xFF11161A).copy(alpha = 0.64f),
            palette.top.copy(alpha = 0.42f),
            palette.bottom.copy(alpha = 0.52f),
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.84f),
            Color.White.copy(alpha = 0.68f),
            palette.top.copy(alpha = 0.40f),
            palette.bottom.copy(alpha = 0.20f),
        )
    }
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
            .border(if (isPlaying) 2.dp else 1.dp, if (isPlaying) accentColor else idleBorderColor, shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(backgroundArtwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (darkTheme) 0.38f else 0.74f,
            modifier = Modifier
                .fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        cardOverlay,
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
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = if (darkTheme) 0.20f else 0.34f),
                                accentColor.copy(alpha = if (darkTheme) 0.68f else 0.92f),
                            ),
                        ),
                    ),
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
                            tint = if (darkTheme) Color(0xFFE2E7EA) else Color.White,
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
                    color = titleColor,
                    fontSize = if (featured) 19.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = if (featured) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (featured) 22.sp else 17.sp,
                )
                Spacer(Modifier.height(if (featured) 4.dp else 3.dp))
                SoundWaveform(
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth().height(if (featured) 24.dp else 14.dp),
                )
                Spacer(Modifier.height(if (featured) 3.dp else 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(if (featured) 32.dp else 26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (darkTheme) 0.09f else 0.48f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "停止" else "播放",
                            tint = iconColor,
                            modifier = Modifier.size(if (featured) 20.dp else 16.dp),
                        )
                    }
                    Text(
                        text = if (isPlaying) "播放中" else "${sound.ext.uppercase()} · ${sound.sizeLabel}",
                        color = if (isPlaying) accentColor else secondaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = iconColor.copy(alpha = 0.74f), modifier = Modifier.size(if (featured) 19.dp else 17.dp))
                    Icon(Icons.Outlined.DragIndicator, contentDescription = "长按排序", tint = iconColor.copy(alpha = 0.68f), modifier = Modifier.size(if (featured) 19.dp else 17.dp))
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
internal fun SwipeDeleteSoundCard(
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

private data class SoundCardPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val accent: Color,
)

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

private fun darkLocalSoundCardPalette(seed: String): SoundCardPalette {
    val palettes = listOf(
        SoundCardPalette(Color(0xFF26313A), Color(0xFF1E272E), Color(0xFF171D22), Color(0xFF7F9CAD)),
        SoundCardPalette(Color(0xFF332B32), Color(0xFF282329), Color(0xFF1D191E), Color(0xFFAD879D)),
        SoundCardPalette(Color(0xFF25332F), Color(0xFF1E2926), Color(0xFF171F1D), Color(0xFF7FA697)),
        SoundCardPalette(Color(0xFF343029), Color(0xFF28251F), Color(0xFF1D1B17), Color(0xFFAE9A77)),
        SoundCardPalette(Color(0xFF2B2D39), Color(0xFF22242E), Color(0xFF191A22), Color(0xFF9199B5)),
        SoundCardPalette(Color(0xFF352B29), Color(0xFF292220), Color(0xFF1E1918), Color(0xFFB18E85)),
        SoundCardPalette(Color(0xFF29333A), Color(0xFF20282E), Color(0xFF181E22), Color(0xFF829EAD)),
        SoundCardPalette(Color(0xFF302C37), Color(0xFF26232D), Color(0xFF1B1920), Color(0xFF9E91AF)),
    )
    return palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
}
