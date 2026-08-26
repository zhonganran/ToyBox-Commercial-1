package com.byd.wjx.desktopcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun DesktopQuickCardIconSection(
    icons: List<DesktopQuickCardIcon>,
    selectedId: String,
    iconSizeDp: Float,
    maxIconSizeDp: Float,
    importBusy: Boolean,
    message: String?,
    onSelect: (String) -> Unit,
    onSizeChange: (Float) -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
) {
    DesktopQuickCardSettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("悬浮图标", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "内置 2 个图标；自定义图片数量不限，GIF 动图会保持动画",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Button(
                onClick = onImport,
                enabled = !importBusy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Text(if (importBusy) "导入中" else "添加")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(icons, key = DesktopQuickCardIcon::id) { icon ->
                DesktopQuickCardIconOption(
                    icon = icon,
                    selected = selectedId == icon.id,
                    onSelect = { onSelect(icon.id) },
                    onDelete = { onDelete(icon.id) },
                )
            }
        }
        HorizontalDivider(color = CardBorder)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("图标大小", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${iconSizeDp.roundToInt()} dp",
                    color = AccentGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = iconSizeDp.coerceIn(DESKTOP_QUICK_CARD_MIN_ICON_DP, maxIconSizeDp),
                onValueChange = onSizeChange,
                valueRange = DESKTOP_QUICK_CARD_MIN_ICON_DP..maxIconSizeDp,
                colors = SliderDefaults.colors(
                    thumbColor = AccentGreen,
                    activeTrackColor = AccentGreen,
                    inactiveTrackColor = CardBorder,
                ),
            )
        }
        message?.let {
            Text(
                it,
                color = if (it.contains("失败") || it.contains("无法")) AccentOrange else AccentGreen,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DesktopQuickCardIconOption(
    icon: DesktopQuickCardIcon,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .background(CardBorder.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AccentGreen else CardBorder,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onSelect)
                .padding(5.dp),
        ) {
            DesktopQuickCardIconImage(
                icon = icon,
                requestedSizeDp = 60f,
                contentDescription = icon.label,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            icon.label,
            color = if (selected) AccentGreen else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (icon.custom) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除${icon.label}",
                    tint = AccentOrange,
                    modifier = Modifier.size(13.dp),
                )
                Text("删除", color = AccentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.size(20.dp))
        }
    }
}
