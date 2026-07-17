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
internal fun OnlineSoundTab(
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

internal data class SoundCardPalette(
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

internal fun sceneCardPalette(seed: String): SoundCardPalette {
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
