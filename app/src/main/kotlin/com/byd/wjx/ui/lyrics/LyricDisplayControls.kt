package com.byd.wjx.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.lyrics.LyricSolidColorPalette
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun LyricDisplayHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("歌词显示", color = TextPrimary, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("管理中控桌面与仪表盘歌词", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DetailToggleRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurfaceElevated.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (expanded) AccentGreen else CardSurface,
                contentColor = if (expanded) AccentContent else TextPrimary,
            ),
            modifier = Modifier.height(38.dp),
        ) {
            Text(if (expanded) "收起调节" else "展开调节", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
internal fun LyricColorControls(
    textColor: Int,
    highlightColor: Int,
    enabled: Boolean,
    onTextColorChange: (Int) -> Unit,
    onHighlightColorChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SolidColorPaletteRow(
            title = "歌词颜色",
            selectedColor = textColor,
            enabled = enabled,
            onSelect = onTextColorChange,
            modifier = Modifier.weight(1f),
        )
        SolidColorPaletteRow(
            title = "播放行高亮",
            selectedColor = highlightColor,
            enabled = enabled,
            onSelect = onHighlightColorChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SolidColorPaletteRow(
    title: String,
    selectedColor: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LyricSolidColorPalette.colors.forEach { swatch ->
                val selected = selectedColor == swatch.color
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(swatch.color))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) AccentGreen else CardBorder,
                                shape = CircleShape,
                            )
                            .clickable(enabled = enabled) { onSelect(swatch.color) },
                    )
                    Text(
                        text = swatch.label,
                        color = if (selected) AccentGreen else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PresetChipRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (id, label) ->
                val selected = id == selectedId
                Button(
                    onClick = { onSelect(id) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AccentGreen else CardSurfaceElevated,
                        contentColor = if (selected) AccentContent else TextPrimary,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = com.byd.wjx.ui.components.bydSwitchColors(),
        )
    }
}

@Composable
internal fun LyricSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
                inactiveTrackColor = TextMuted.copy(alpha = 0.24f),
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = TextMuted.copy(alpha = 0.35f),
            ),
        )
    }
}
