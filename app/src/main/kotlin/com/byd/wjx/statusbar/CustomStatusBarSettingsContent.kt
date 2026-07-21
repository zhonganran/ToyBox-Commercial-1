package com.byd.wjx.statusbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun CustomStatusBarSettingsContent(
    viewModel: CustomStatusBarSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerVisible by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshOverlayPermission()
        onPauseOrDispose { }
    }

    StatusBarSettingsCard {
        StatusBarToggleRow(
            title = "替换原生状态栏",
            subtitle = "进入360全景时自动隐藏，通知栏和快捷设置仍可下拉",
            checked = state.config.enabled,
            onCheckedChange = viewModel::setEnabled,
        )
        HorizontalDivider(color = CardBorder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("悬浮窗权限", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.overlayPermissionGranted) "已允许" else "尚未允许，状态栏不会显示",
                    color = if (state.overlayPermissionGranted) AccentGreen else AccentOrange,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = viewModel::requestOverlayPermission,
                enabled = !state.permissionBusy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(
                    if (state.overlayPermissionGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Add,
                    contentDescription = null,
                )
                Spacer(Modifier.size(6.dp))
                Text(if (state.overlayPermissionGranted) "重新检查" else if (state.permissionBusy) "授权中" else "拉起授权")
            }
        }
    }

    StatusBarPreview(state.config)

    StatusBarSettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("从左到右显示", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("新增内容追加到最右侧", color = TextSecondary, fontSize = 12.sp)
            }
            Button(onClick = { pickerVisible = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(5.dp))
                Text("添加")
            }
        }
        if (state.config.items.isEmpty()) {
            Text("未添加显示内容", color = TextMuted, fontSize = 13.sp)
        } else {
            state.config.items.forEachIndexed { index, item ->
                StatusBarOrderedRow(
                    item = item,
                    index = index,
                    count = state.config.items.size,
                    onMove = viewModel::moveItem,
                    onDelete = viewModel::removeItem,
                )
            }
        }
    }

    StatusBarSettingsCard {
        Text("显示样式", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        StatusBarSlider(
            title = "大小",
            valueLabel = "${state.config.textSizeSp.roundToInt()} sp",
            value = state.config.textSizeSp,
            valueRange = CUSTOM_STATUS_BAR_MIN_TEXT_SP..CUSTOM_STATUS_BAR_MAX_TEXT_SP,
            onValueChange = viewModel::setTextSizeSp,
        )
        StatusBarSlider(
            title = "内容间距",
            valueLabel = "${state.config.itemSpacingDp.roundToInt()} dp",
            value = state.config.itemSpacingDp,
            valueRange = CUSTOM_STATUS_BAR_MIN_ITEM_SPACING_DP..CUSTOM_STATUS_BAR_MAX_ITEM_SPACING_DP,
            onValueChange = viewModel::setItemSpacingDp,
        )
        StatusBarSlider(
            title = "左右位置",
            valueLabel = "${state.config.horizontalOffsetDp.roundToInt()} dp",
            value = state.config.horizontalOffsetDp,
            valueRange = CUSTOM_STATUS_BAR_MIN_HORIZONTAL_OFFSET_DP..CUSTOM_STATUS_BAR_MAX_HORIZONTAL_OFFSET_DP,
            onValueChange = viewModel::setHorizontalOffsetDp,
        )
        StatusBarSlider(
            title = "上下位置",
            valueLabel = "${state.config.verticalOffsetDp.roundToInt()} dp",
            value = state.config.verticalOffsetDp,
            valueRange = CUSTOM_STATUS_BAR_MIN_VERTICAL_OFFSET_DP..CUSTOM_STATUS_BAR_MAX_VERTICAL_OFFSET_DP,
            onValueChange = viewModel::setVerticalOffsetDp,
        )
        StatusBarSlider(
            title = "状态栏高度",
            valueLabel = "${state.config.heightDp.roundToInt()} dp",
            value = state.config.heightDp,
            valueRange = CUSTOM_STATUS_BAR_MIN_HEIGHT_DP..CUSTOM_STATUS_BAR_MAX_HEIGHT_DP,
            onValueChange = viewModel::setHeightDp,
        )
        StatusBarSlider(
            title = "背景透明度",
            valueLabel = "${(state.config.backgroundOpacity * 100).roundToInt()}%",
            value = state.config.backgroundOpacity,
            valueRange = 0f..1f,
            onValueChange = viewModel::setBackgroundOpacity,
        )
        StatusBarIconStylePicker(state.config.iconStyle, viewModel::setIconStyle)
        StatusBarFontPicker(state.config.fontStyle, viewModel::setFontStyle)
        StatusBarColorPicker(state.config.textColorArgb, viewModel::setTextColorArgb)
    }

    if (pickerVisible) {
        StatusBarItemPicker(
            selected = state.config.items.toSet(),
            onSelect = { item ->
                viewModel.addItem(item)
                pickerVisible = false
            },
            onDismiss = { pickerVisible = false },
        )
    }
}

@Composable
private fun StatusBarPreview(config: CustomStatusBarConfig) {
    val previewConfig = config.copy(horizontalOffsetDp = config.horizontalOffsetDp / PREVIEW_OFFSET_SCALE)
    val previewState = CustomStatusBarRenderState(
        config = previewConfig,
        items = config.items.map { item ->
            CustomStatusBarDisplayItem(
                type = item,
                text = item.label,
                iconOnly = item in PREVIEW_ICON_ONLY_ITEMS,
                indicatorState = SystemIndicatorState.ACTIVE.takeIf { item in PREVIEW_ICON_ONLY_ITEMS },
                level = 2.takeIf { item == CustomStatusBarItem.WIFI },
                weatherCode = 0.takeIf { item == CustomStatusBarItem.WEATHER },
            )
        },
    )
    StatusBarSettingsCard {
        Text("效果预览", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(config.heightDp.dp.coerceAtMost(96.dp))
                .clip(RoundedCornerShape(5.dp)),
        ) {
            CustomStatusBarContent(previewState)
        }
    }
}

@Composable
private fun StatusBarOrderedRow(
    item: CustomStatusBarItem,
    index: Int,
    count: Int,
    onMove: (CustomStatusBarItem, Int) -> Unit,
    onDelete: (CustomStatusBarItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBorder.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            color = AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.size(26.dp),
        )
        Text(item.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(item, -1) }, enabled = index > 0) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "左移${item.label}")
        }
        IconButton(onClick = { onMove(item, 1) }, enabled = index < count - 1) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "右移${item.label}")
        }
        IconButton(onClick = { onDelete(item) }) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除${item.label}", tint = AccentOrange)
        }
    }
}

@Composable
private fun StatusBarItemPicker(
    selected: Set<CustomStatusBarItem>,
    onSelect: (CustomStatusBarItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = CustomStatusBarItem.entries.filterNot(selected::contains)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加显示内容") },
        text = {
            if (available.isEmpty()) {
                Text("所有可用信息都已添加", color = TextSecondary)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                    CustomStatusBarItemCategory.entries.forEach { category ->
                        val categoryItems = available.filter { it.category == category }
                        if (categoryItems.isNotEmpty()) {
                            item(key = "header_${category.name}") {
                                Text(
                                    category.label,
                                    color = AccentBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 3.dp),
                                )
                            }
                            items(categoryItems, key = CustomStatusBarItem::id) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item.label, color = TextPrimary, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { onSelect(item) }) { Text("添加") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        containerColor = CardSurface,
    )
}

@Composable
private fun StatusBarFontPicker(
    selected: CustomStatusBarFontStyle,
    onSelect: (CustomStatusBarFontStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("字体", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomStatusBarFontStyle.entries.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.label, fontFamily = option.previewFontFamily) },
                )
            }
        }
    }
}

@Composable
private fun StatusBarIconStylePicker(
    selected: CustomStatusBarIconStyle,
    onSelect: (CustomStatusBarIconStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("图标样式", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomStatusBarIconStyle.entries.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun StatusBarColorPicker(
    selectedColor: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("文字和图标颜色", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            STATUS_BAR_COLORS.forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(option.argb))
                            .border(
                                width = if (selectedColor == option.argb) 3.dp else 1.dp,
                                color = if (selectedColor == option.argb) AccentGreen else CardBorder,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(option.argb) },
                    )
                    Text(option.label, color = TextMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusBarSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(valueLabel, color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun StatusBarToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = bydSwitchColors())
    }
}

@Composable
private fun StatusBarSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private val CustomStatusBarFontStyle.previewFontFamily: FontFamily
    get() = when (this) {
        CustomStatusBarFontStyle.DEFAULT -> FontFamily.Default
        CustomStatusBarFontStyle.SANS_SERIF -> FontFamily.SansSerif
        CustomStatusBarFontStyle.SERIF -> FontFamily.Serif
        CustomStatusBarFontStyle.MONOSPACE -> FontFamily.Monospace
    }

private data class StatusBarColorOption(val label: String, val argb: Int)

private val STATUS_BAR_COLORS = listOf(
    StatusBarColorOption("白", 0xFFFFFFFF.toInt()),
    StatusBarColorOption("黑", 0xFF111111.toInt()),
    StatusBarColorOption("青", 0xFF35DCD6.toInt()),
    StatusBarColorOption("绿", 0xFF4ADE80.toInt()),
    StatusBarColorOption("黄", 0xFFFBBF24.toInt()),
    StatusBarColorOption("蓝", 0xFF68B8FF.toInt()),
    StatusBarColorOption("粉", 0xFFFF6FA8.toInt()),
    StatusBarColorOption("红", 0xFFEF4444.toInt()),
)

private val PREVIEW_ICON_ONLY_ITEMS = setOf(
    CustomStatusBarItem.WIFI,
    CustomStatusBarItem.BLUETOOTH,
    CustomStatusBarItem.LOCATION,
    CustomStatusBarItem.USB,
    CustomStatusBarItem.MOBILE_NETWORK,
)

private const val PREVIEW_OFFSET_SCALE = 10f
