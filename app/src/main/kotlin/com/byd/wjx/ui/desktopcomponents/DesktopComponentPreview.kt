package com.byd.wjx.ui.desktopcomponents

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import kotlin.math.abs

@Composable
internal fun DesktopComponentPreviewStage(
    selected: DesktopComponentPage,
    enabledMap: Map<DesktopComponentPage, Boolean>,
    onSelected: (DesktopComponentPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = DesktopComponentPage.entries
    val selectedIndex = pages.indexOf(selected)
    val previewCount = minOf(3, pages.size)
    val previewStart = (selectedIndex - 1).coerceIn(0, pages.size - previewCount)
    val previewPages = pages.subList(previewStart, previewStart + previewCount)
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier.pointerInput(selected) {
            detectHorizontalDragGestures(
                onDragStart = { horizontalDrag = 0f },
                onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                onDragCancel = { horizontalDrag = 0f },
                onDragEnd = {
                    if (abs(horizontalDrag) >= 64f) {
                        val currentIndex = pages.indexOf(selected)
                        val targetIndex = if (horizontalDrag < 0f) {
                            (currentIndex + 1).coerceAtMost(pages.lastIndex)
                        } else {
                            (currentIndex - 1).coerceAtLeast(0)
                        }
                        onSelected(pages[targetIndex])
                    }
                    horizontalDrag = 0f
                },
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            previewPages.forEach { page ->
                val isSelected = page == selected
                DesktopComponentPreviewCard(
                    page = page,
                    enabled = enabledMap[page] == true,
                    selected = isSelected,
                    onClick = { onSelected(page) },
                    modifier = Modifier
                        .weight(if (isSelected) 1.18f else 0.82f)
                        .fillMaxHeight(if (isSelected) 1f else 0.88f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                Box(
                    modifier = Modifier
                        .width(if (page == selected) 22.dp else 10.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (page == selected) Color.White else Color.White.copy(alpha = 0.46f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun DesktopComponentPreviewCard(
    page: DesktopComponentPage,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (selected) 22.dp else 16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(page.colors), shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = Color.White.copy(alpha = if (selected) 0.88f else 0.32f),
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (selected) 0.06f else 0.02f),
                        0.72f to Color.Black.copy(alpha = if (selected) 0.10f else 0.18f),
                        1f to Color.Black.copy(alpha = if (selected) 0.28f else 0.36f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selected) 24.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    page.label,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = if (selected) 18.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (enabled) "已开启" else "未开启",
                    color = if (enabled) Color(0xFF65F0C2) else Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    DesktopComponentPage.Lyrics -> CombinedLyricPreview(selected)
                    DesktopComponentPage.QuickCard -> QuickCardPreview(selected)
                    DesktopComponentPage.ClusterCard,
                    DesktopComponentPage.ClusterCard2,
                    -> ClusterCardPreview(selected)
                }
            }
            Text(
                page.description,
                color = Color.White.copy(alpha = 0.74f),
                fontSize = if (selected) 12.sp else 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CombinedLyricPreview(selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (selected) 10.dp else 7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PreviewBadge(Icons.Outlined.Subtitles, "桌面歌词", AccentGreen, selected)
            PreviewBadge(Icons.Outlined.DirectionsCar, "仪表歌词", AccentBlue, selected)
        }
        listOf("穿过人海 别停下来", "去更远的地方", "看更多的风景").forEachIndexed { index, line ->
            Text(
                text = line,
                color = if (index == 1) Color.White else Color.White.copy(alpha = 0.56f),
                fontSize = if (selected && index == 1) 19.sp else if (selected) 14.sp else 11.sp,
                fontWeight = if (index == 1) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickCardPreview(selected: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (selected) 7.dp else 5.dp)) {
        QuickCardGlassPreviewTile(Icons.Outlined.ToggleOn, "车辆控制", "已开启", AccentGreen, selected)
        QuickCardGlassPreviewTile(Icons.Outlined.DirectionsCar, "左右滑门", "点击控制", Color.White, selected)
        QuickCardGlassPreviewTile(Icons.Outlined.MusicNote, "车外音效", "点击播放", AccentOrange, selected)
    }
}

@Composable
private fun ClusterCardPreview(selected: Boolean) {
    Column(
        modifier = Modifier
            .width(if (selected) 230.dp else 170.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xC80A151E))
            .border(0.8.dp, Color(0x7893C5E0), RoundedCornerShape(10.dp))
            .padding(if (selected) 12.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (selected) 8.dp else 5.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (selected) 8.dp else 5.dp)) {
                repeat(2) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (selected) 38.dp else 28.dp)
                            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(6.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.62f),
                            modifier = Modifier.size(if (selected) 17.dp else 13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "自定义",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = if (selected) 9.sp else 7.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCardGlassPreviewTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    accent: Color,
    selected: Boolean,
) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .width(if (selected) 94.dp else 72.dp)
            .height(if (selected) 62.dp else 50.dp)
            .clip(shape)
            .background(Color(0x8A071018), shape)
            .border(0.8.dp, Color.White.copy(alpha = 0.20f), shape)
            .padding(horizontal = if (selected) 8.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (selected) 6.dp else 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent.copy(alpha = 0.92f),
            modifier = Modifier.size(if (selected) 19.dp else 15.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = if (selected) 10.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = if (selected) 8.sp else 7.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewBadge(
    icon: ImageVector,
    label: String,
    accent: Color,
    selected: Boolean,
) {
    Column(
        modifier = Modifier
            .width(if (selected) 92.dp else 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(vertical = if (selected) 12.dp else 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(if (selected) 24.dp else 19.dp),
        )
        Text(
            label,
            color = Color.White,
            fontSize = if (selected) 11.sp else 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
