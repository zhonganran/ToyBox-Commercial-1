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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.desktopcard.DesktopQuickCardSettingsContent
import com.byd.wjx.desktopcard.DesktopQuickCardSettingsViewModel
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.statusbar.CustomStatusBarSettingsContent
import com.byd.wjx.statusbar.CustomStatusBarSettingsViewModel
import com.byd.wjx.ui.lyrics.LyricDisplayScreen
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.abs

private enum class DesktopComponentPage(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val colors: List<Color>,
) {
    Lyrics(
        label = "歌词组件",
        description = "桌面歌词与仪表盘歌词共用一套样式",
        icon = Icons.Outlined.Subtitles,
        colors = listOf(Color(0xFF2375B7), Color(0xFF123F83)),
    ),
    QuickCard(
        label = "桌面卡片",
        description = "车辆控制（含电滑门）与车外音效",
        icon = Icons.Outlined.Apps,
        colors = listOf(Color(0xFF1A5960), Color(0xFF0C3138)),
    ),
    StatusBar(
        label = "个性状态栏",
        description = "自定义车辆、音乐和系统信息的显示位置",
        icon = Icons.Outlined.DashboardCustomize,
        colors = listOf(Color(0xFF315C85), Color(0xFF17364F)),
    ),
}

@Composable
fun DesktopComponentsScreen(
    quickCardViewModel: DesktopQuickCardSettingsViewModel = hiltViewModel(),
    statusBarViewModel: CustomStatusBarSettingsViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val quickCardState by quickCardViewModel.uiState.collectAsStateWithLifecycle()
    val statusBarState by statusBarViewModel.uiState.collectAsStateWithLifecycle()
    val lyricSettings = remember(context.applicationContext) { LyricSettings(context.applicationContext) }
    var selectedPage by rememberSaveable { mutableStateOf(DesktopComponentPage.Lyrics) }
    var lyricsEnabled by remember {
        mutableStateOf(lyricSettings.desktopEnabled || lyricSettings.clusterEnabled)
    }

    LifecycleResumeEffect(Unit) {
        lyricsEnabled = lyricSettings.desktopEnabled || lyricSettings.clusterEnabled
        quickCardViewModel.refresh()
        statusBarViewModel.refreshOverlayPermission()
        onPauseOrDispose { }
    }

    val enabledMap = mapOf(
        DesktopComponentPage.Lyrics to lyricsEnabled,
        DesktopComponentPage.QuickCard to quickCardState.config.enabled,
        DesktopComponentPage.StatusBar to statusBarState.config.enabled,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 58.dp, end = 58.dp, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "桌面组件",
                    color = TextPrimary,
                    fontSize = 32.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "整个页面可以上下滑动，选择组件后在下方直接调节",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            DesktopComponentPreviewStage(
                selected = selectedPage,
                enabledMap = enabledMap,
                onSelected = { selectedPage = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )
        }

        when (selectedPage) {
            DesktopComponentPage.Lyrics -> LyricDisplayScreen(
                showHeader = false,
                scrollEnabled = false,
                onEnabledStateChanged = { lyricsEnabled = it },
                modifier = Modifier.fillMaxWidth(),
            )

            DesktopComponentPage.QuickCard -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 58.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DesktopQuickCardSettingsContent(quickCardViewModel)
            }

            DesktopComponentPage.StatusBar -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 58.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CustomStatusBarSettingsContent(statusBarViewModel)
            }
        }
    }
}

@Composable
private fun DesktopComponentPreviewStage(
    selected: DesktopComponentPage,
    enabledMap: Map<DesktopComponentPage, Boolean>,
    onSelected: (DesktopComponentPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = DesktopComponentPage.entries
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
            pages.forEach { page ->
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
                    color = Color.White,
                    fontSize = if (selected) 18.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (enabled) "已开启" else "未开启",
                    color = if (enabled) Color(0xFF65F0C2) else Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    DesktopComponentPage.Lyrics -> CombinedLyricPreview(selected)
                    DesktopComponentPage.QuickCard -> QuickCardPreview(selected)
                    DesktopComponentPage.StatusBar -> PersonalizedStatusBarPreview(selected)
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
private fun PersonalizedStatusBarPreview(selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (selected) 48.dp else 38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(7.dp))
            .padding(horizontal = if (selected) 14.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.AccessTime,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(if (selected) 18.dp else 14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("18:26", color = Color.White, fontSize = if (selected) 13.sp else 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(if (selected) 16.dp else 8.dp))
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(if (selected) 18.dp else 14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("正在播放", color = Color.White, fontSize = if (selected) 12.sp else 9.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.BatteryChargingFull,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(if (selected) 18.dp else 14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("86%", color = Color.White, fontSize = if (selected) 13.sp else 10.sp, fontWeight = FontWeight.Bold)
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
        Text(label, color = Color.White, fontSize = if (selected) 11.sp else 9.sp, fontWeight = FontWeight.Bold)
    }
}
