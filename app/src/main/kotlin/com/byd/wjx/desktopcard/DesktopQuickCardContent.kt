package com.byd.wjx.desktopcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

data class DesktopQuickCardRenderState(
    val config: DesktopQuickCardConfig = DesktopQuickCardConfig(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeCommand: String? = null,
    val activeSoundPath: String? = null,
)

@Composable
fun DesktopQuickCardContent(
    state: DesktopQuickCardRenderState,
    onCommandClick: (String) -> Unit,
    onSoundClick: (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val layout = DesktopQuickCardLayout.calculate(
        screenWidthDp = configuration.screenWidthDp.toFloat(),
        screenHeightDp = configuration.screenHeightDp.toFloat(),
        scale = state.config.scale,
        sectionGapDp = state.config.sectionGapDp,
    )
    val cellSize = layout.cellSizeDp.dp
    val cardGap = layout.gridGapDp.dp
    val sectionGap = layout.sectionGapDp.dp
    val commands = state.config.functionCommands.mapNotNull(DesktopQuickCardCommandCatalog.byCommand::get)
    val sounds = state.config.soundPaths.map { path -> path to File(path).nameWithoutExtension }

    Column(
        modifier = Modifier
            .width(layout.widthDp.dp)
            .background(CardSurface.copy(alpha = 0.96f), RoundedCornerShape(16.dp * state.config.scale))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp * state.config.scale))
            .padding(layout.contentPaddingDp.dp),
    ) {
        if (commands.isEmpty() && sounds.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "桌面卡片已开启",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "请在玩具箱中添加车辆控制或车外音效",
                    color = com.byd.wjx.ui.theme.TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (commands.isNotEmpty()) {
            DesktopQuickCardGridContent(
                count = commands.size,
                cellSize = cellSize,
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
                    .height(sectionGap.coerceAtLeast(if (state.config.dividerEnabled) 1.dp else 0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.config.dividerEnabled) {
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                }
            }
        }

        if (sounds.isNotEmpty()) {
            DesktopQuickCardGridContent(
                count = sounds.size,
                cellSize = cellSize,
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

@Composable
private fun DesktopQuickCardGridContent(
    count: Int,
    cellSize: Dp,
    gap: Dp,
    content: @Composable (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(DesktopQuickCardGrid.rowsFor(count)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(DESKTOP_QUICK_CARD_COLUMNS) { column ->
                    val index = row * DESKTOP_QUICK_CARD_COLUMNS + column
                    if (index < count) content(index) else Spacer(Modifier.size(cellSize))
                }
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
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .size(cellSize)
            .background(
                color = if (active) accent.copy(alpha = 0.28f) else CardSurfaceElevated.copy(alpha = 0.94f),
                shape = shape,
            )
            .border(
                width = if (active) 1.5.dp else 0.7.dp,
                color = if (active) accent else CardBorder,
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
                .background(accent.copy(alpha = if (active) 0.24f else 0.13f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(cellSize * 0.23f),
            )
        }
        Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            color = if (active) accent else TextPrimary,
            fontSize = (11f * stateScale(cellSize)).coerceIn(10f, 15f).sp,
            lineHeight = (13f * stateScale(cellSize)).coerceIn(12f, 17f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
