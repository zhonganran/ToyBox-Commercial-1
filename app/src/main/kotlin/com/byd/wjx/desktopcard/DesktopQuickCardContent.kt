package com.byd.wjx.desktopcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.ThemeMode
import java.io.File

data class DesktopQuickCardRenderState(
    val config: DesktopQuickCardConfig = DesktopQuickCardConfig(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val floatingIcon: DesktopQuickCardIcon = DesktopQuickCardIconCatalog.default,
    val expanded: Boolean = false,
    val activeCommand: String? = null,
    val activeSoundPath: String? = null,
)

@Composable
fun DesktopQuickCardContent(
    state: DesktopQuickCardRenderState,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onCommandClick: (String) -> Unit,
    onSoundClick: (String) -> Unit,
) {
    if (!state.expanded) {
        val iconSize = DESKTOP_QUICK_CARD_COLLAPSED_ICON_DP.dp
        Box(
            modifier = Modifier
                .size(iconSize)
                .clickable(onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) {
            DesktopQuickCardIconImage(
                icon = state.floatingIcon,
                contentDescription = "展开车辆控制与音效",
                modifier = Modifier.size(iconSize),
            )
        }
        return
    }

    val configuration = LocalConfiguration.current
    val layout = DesktopQuickCardLayout.calculate(
        screenWidthDp = configuration.screenWidthDp.toFloat(),
        screenHeightDp = configuration.screenHeightDp.toFloat(),
    )
    val commands = state.config.functionCommands.mapNotNull(DesktopQuickCardCommandCatalog.byCommand::get)
    val sounds = state.config.soundPaths.map { path -> path to File(path).nameWithoutExtension }
    val tiles = buildList {
        commands.forEach { item ->
            add(
                DesktopQuickCardTileModel(
                    label = item.label,
                    subtitle = if (state.activeCommand == item.command) "执行中" else item.category,
                    icon = commandIcon(item),
                    accent = AccentGreen,
                    active = state.activeCommand == item.command,
                    onClick = { onCommandClick(item.command) },
                ),
            )
        }
        sounds.forEach { (path, label) ->
            add(
                DesktopQuickCardTileModel(
                    label = label,
                    subtitle = if (state.activeSoundPath == path) "播放中" else "车外音效",
                    icon = Icons.Outlined.MusicNote,
                    accent = AccentOrange,
                    active = state.activeSoundPath == path,
                    onClick = { onSoundClick(path) },
                ),
            )
        }
    }
    val visibleColumns = if (tiles.isEmpty()) {
        2
    } else {
        tiles.size.coerceIn(1, DESKTOP_QUICK_CARD_COLUMNS)
    }
    val cellSize = layout.cellSizeDp.dp
    val cardGap = layout.gridGapDp.dp
    val maxContentHeight = (configuration.screenHeightDp * 0.62f).dp
    val cardWidth = DesktopQuickCardLayout.widthForColumns(layout, visibleColumns).dp
    val expandedIconSize = DESKTOP_QUICK_CARD_EXPANDED_ICON_DP.dp

    Box(
        modifier = Modifier.width(cardWidth),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = layout.contentPaddingDp.dp,
                    top = expandedIconSize + 7.dp,
                    end = layout.contentPaddingDp.dp,
                    bottom = layout.contentPaddingDp.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(cardGap),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(cardGap),
            ) {
                if (tiles.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(QUICK_CARD_GLASS_SURFACE, QUICK_CARD_TILE_SHAPE)
                            .border(0.8.dp, QUICK_CARD_GLASS_STROKE, QUICK_CARD_TILE_SHAPE)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.86f),
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            "尚未添加控制或音效",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (tiles.isNotEmpty()) {
                    DesktopQuickCardGridContent(
                        count = tiles.size,
                        gap = cardGap,
                    ) { index ->
                        val item = tiles[index]
                        DesktopQuickCardTile(
                            label = item.label,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            accent = item.accent,
                            active = item.active,
                            cellSize = cellSize,
                            onClick = item.onClick,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(expandedIconSize)
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            DesktopQuickCardIconImage(
                icon = state.floatingIcon,
                contentDescription = "收起车辆控制与音效",
                modifier = Modifier.size(expandedIconSize),
            )
        }
    }
}

@Composable
private fun DesktopQuickCardGridContent(
    count: Int,
    gap: Dp,
    content: @Composable (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        (0 until count).chunked(DESKTOP_QUICK_CARD_COLUMNS).forEach { rowIndices ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (index in rowIndices) content(index)
            }
        }
    }
}

@Composable
private fun DesktopQuickCardTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    active: Boolean,
    cellSize: Dp,
    onClick: () -> Unit,
) {
    val tileHeight = cellSize * 0.74f
    val scale = stateScale(cellSize)
    val foreground = if (active) accent else Color.White.copy(alpha = 0.90f)
    Box(
        modifier = Modifier
            .width(cellSize)
            .height(tileHeight)
            .clip(QUICK_CARD_TILE_SHAPE)
            .background(
                color = if (active) accent.copy(alpha = 0.18f) else QUICK_CARD_GLASS_SURFACE,
                shape = QUICK_CARD_TILE_SHAPE,
            )
            .border(
                width = if (active) 1.1.dp else 0.8.dp,
                color = if (active) accent.copy(alpha = 0.82f) else QUICK_CARD_GLASS_STROKE,
                shape = QUICK_CARD_TILE_SHAPE,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size((17f * scale).coerceIn(16f, 20f).dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.96f),
                    fontSize = (10f * scale).coerceIn(9.5f, 10.5f).sp,
                    lineHeight = (11.5f * scale).coerceIn(11f, 12.5f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = if (active) accent.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.58f),
                    fontSize = (8.5f * scale).coerceIn(8f, 9.5f).sp,
                    lineHeight = (10f * scale).coerceIn(9f, 11f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(4.dp)
                .background(
                    if (active) accent else Color.White.copy(alpha = 0.34f),
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}

private data class DesktopQuickCardTileModel(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val active: Boolean,
    val onClick: () -> Unit,
)

private fun stateScale(cellSize: Dp): Float = cellSize.value / 88f

private fun commandIcon(item: DesktopQuickCardCommand): ImageVector = when (item.category) {
    "电滑门" -> Icons.Outlined.DirectionsCar
    "车窗" -> Icons.Outlined.Air
    "空调" -> Icons.Outlined.AcUnit
    "座椅" -> Icons.Outlined.DirectionsCar
    "后视镜" -> Icons.Outlined.WbSunny
    "灯光" -> Icons.Outlined.LightMode
    "门锁" -> Icons.Outlined.Lock
    "天窗" -> Icons.Outlined.WbSunny
    "车身" -> Icons.Outlined.DirectionsCar
    else -> Icons.Outlined.Settings
}

private val QUICK_CARD_GLASS_SURFACE = Color(0x8A071018)
private val QUICK_CARD_GLASS_STROKE = Color(0x35FFFFFF)
private val QUICK_CARD_TILE_SHAPE = RoundedCornerShape(8.dp)
