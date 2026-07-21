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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.ThemeMode
import java.io.File
import kotlin.math.max

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
        val iconSize = 82.dp
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
    val visibleColumns = if (commands.isEmpty() && sounds.isEmpty()) {
        2
    } else {
        max(commands.size.coerceAtMost(DESKTOP_QUICK_CARD_COLUMNS), sounds.size.coerceAtMost(DESKTOP_QUICK_CARD_COLUMNS))
            .coerceAtLeast(1)
    }
    val cellSize = layout.cellSizeDp.dp
    val cardGap = layout.gridGapDp.dp
    val shape = RoundedCornerShape(14.dp)
    val maxContentHeight = (configuration.screenHeightDp * 0.70f).dp

    Column(
        modifier = Modifier
            .width(DesktopQuickCardLayout.widthForColumns(layout, visibleColumns).dp)
            .background(CardSurface.copy(alpha = 0.68f), shape)
            .border(1.dp, CardBorder.copy(alpha = 0.82f), shape)
            .padding(layout.contentPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(cardGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(CardSurfaceElevated.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "收起卡片",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(cardGap),
        ) {
            if (commands.isEmpty() && sounds.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        "尚未添加控制或音效",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (commands.isNotEmpty()) {
                DesktopQuickCardGridContent(
                    count = commands.size,
                    gap = cardGap,
                ) { index ->
                    val item = commands[index]
                    DesktopQuickCardTile(
                        label = item.label,
                        icon = commandIcon(item),
                        accent = AccentGreen,
                        active = state.activeCommand == item.command,
                        cellSize = cellSize,
                        onClick = { onCommandClick(item.command) },
                    )
                }
            }

            if (commands.isNotEmpty() && sounds.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.8f), thickness = 1.dp)
                }
            }

            if (sounds.isNotEmpty()) {
                DesktopQuickCardGridContent(
                    count = sounds.size,
                    gap = cardGap,
                ) { index ->
                    val (path, label) = sounds[index]
                    DesktopQuickCardTile(
                        label = label,
                        icon = Icons.Outlined.MusicNote,
                        accent = AccentOrange,
                        active = state.activeSoundPath == path,
                        cellSize = cellSize,
                        onClick = { onSoundClick(path) },
                    )
                }
            }
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
    icon: ImageVector,
    accent: Color,
    active: Boolean,
    cellSize: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .size(cellSize)
            .background(
                color = if (active) accent.copy(alpha = 0.32f) else CardSurfaceElevated.copy(alpha = 0.72f),
                shape = shape,
            )
            .border(
                width = if (active) 1.5.dp else 0.7.dp,
                color = if (active) accent else CardBorder.copy(alpha = 0.8f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(cellSize * 0.38f)
                .background(accent.copy(alpha = if (active) 0.24f else 0.13f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(cellSize * 0.23f),
            )
        }
        Text(
            text = label,
            color = if (active) accent else TextPrimary,
            fontSize = (11f * stateScale(cellSize)).coerceIn(10f, 15f).sp,
            lineHeight = (13f * stateScale(cellSize)).coerceIn(12f, 17f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

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
