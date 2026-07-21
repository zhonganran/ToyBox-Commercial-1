package com.byd.wjx.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.pro.ProGlassPanel
import com.byd.wjx.ui.pro.ProSectionTitle
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

internal data class LyricPanelState(
    val featureAllowed: Boolean,
    val desktopActive: Boolean,
    val clusterActive: Boolean,
    val expanded: Boolean,
    val allowOtherApps: Boolean,
    val desktopTouchThrough: Boolean,
    val sharedLineCount: Int,
    val desktopFontScale: Float,
    val clusterFontScale: Float,
    val sharedTextColor: Int,
    val sharedHighlightColor: Int,
    val sharedFontStyleId: String,
    val sharedMotionPresetId: String,
    val clusterX: Float,
    val clusterY: Float,
    val syncOffset: Float,
) {
    val anyActive: Boolean get() = desktopActive || clusterActive
}

internal data class LyricPanelActions(
    val onDesktopEnabledChange: (Boolean) -> Unit,
    val onClusterEnabledChange: (Boolean) -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
    val onAllowOtherAppsChange: (Boolean) -> Unit,
    val onDesktopTouchThroughChange: (Boolean) -> Unit,
    val onOpenPlaybackPermission: () -> Unit,
    val onResetClusterPosition: () -> Unit,
    val onSharedLineCountChange: (Int) -> Unit,
    val onDesktopFontScaleChange: (Float) -> Unit,
    val onClusterFontScaleChange: (Float) -> Unit,
    val onSharedTextColorChange: (Int) -> Unit,
    val onSharedHighlightColorChange: (Int) -> Unit,
    val onSharedFontStyleChange: (String) -> Unit,
    val onSharedMotionPresetChange: (String) -> Unit,
    val onClusterXChange: (Float) -> Unit,
    val onClusterYChange: (Float) -> Unit,
    val onSyncOffsetChange: (Float) -> Unit,
)

@Composable
internal fun LyricSettingsPanel(
    state: LyricPanelState,
    actions: LyricPanelActions,
) {
    var adjustmentTarget by remember(state.desktopActive, state.clusterActive) {
        mutableStateOf(if (state.desktopActive) AdjustmentTarget.DESKTOP else AdjustmentTarget.CLUSTER)
    }

    ProSectionTitle(
        title = "歌词显示设置",
        subtitle = "中控和仪表盘共用调节面板，大小与位置分别保存",
    )
    ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LyricModeToggle(
                    title = "桌面歌词",
                    subtitle = if (state.desktopActive) "已开启，可在桌面单指拖动" else "中控桌面显示",
                    checked = state.desktopActive,
                    enabled = state.featureAllowed,
                    onCheckedChange = actions.onDesktopEnabledChange,
                    modifier = Modifier.weight(1f),
                )
                LyricModeToggle(
                    title = "仪表盘歌词",
                    subtitle = if (state.clusterActive) "已开启，大小与位置独立保存" else "显示到仪表盘",
                    checked = state.clusterActive,
                    enabled = state.featureAllowed,
                    onCheckedChange = actions.onClusterEnabledChange,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!state.featureAllowed) {
                Text("歌词组件需要激活后使用，试用期内可直接开启", color = TextSecondary, fontSize = 12.sp)
            }

            if (state.anyActive) {
                DetailToggleRow(
                    title = "歌词显示调节",
                    subtitle = "颜色、字体和动效共用，中控与仪表盘的尺寸和位置互不影响",
                    expanded = state.expanded,
                    onToggle = { actions.onExpandedChange(!state.expanded) },
                )
                if (state.expanded) {
                    if (state.desktopActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SwitchRow(
                                title = "允许显示在其他应用",
                                subtitle = "关闭时只在桌面和本应用显示",
                                checked = state.allowOtherApps,
                                onCheckedChange = actions.onAllowOtherAppsChange,
                                modifier = Modifier.weight(1f),
                            )
                            SwitchRow(
                                title = "触摸穿透",
                                subtitle = if (state.desktopTouchThrough) {
                                    "已穿透，可操作下方应用"
                                } else {
                                    "关闭穿透后可单指拖动"
                                },
                                checked = state.desktopTouchThrough,
                                onCheckedChange = actions.onDesktopTouchThroughChange,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SecondaryLyricButton(
                            text = "播放信息权限",
                            onClick = actions.onOpenPlaybackPermission,
                            modifier = Modifier.weight(1f),
                        )
                        if (adjustmentTarget == AdjustmentTarget.CLUSTER && state.clusterActive) {
                            SecondaryLyricButton(
                                text = "重置仪表位置",
                                onClick = actions.onResetClusterPosition,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    PresetChipRow(
                        title = "调节对象",
                        options = buildList {
                            if (state.desktopActive) add(AdjustmentTarget.DESKTOP.id to "中控")
                            if (state.clusterActive) add(AdjustmentTarget.CLUSTER.id to "仪表盘")
                        },
                        selectedId = adjustmentTarget.id,
                        enabled = state.anyActive,
                        onSelect = { adjustmentTarget = AdjustmentTarget.fromId(it) },
                    )

                    when (adjustmentTarget) {
                        AdjustmentTarget.DESKTOP -> {
                            LyricSlider(
                                label = "中控歌词大小",
                                value = state.desktopFontScale,
                                valueText = "${(state.desktopFontScale * 100).roundToInt()}%",
                                range = LyricSettings.FONT_SCALE_MIN..LyricSettings.FONT_SCALE_MAX,
                                enabled = state.desktopActive,
                                onValueChange = actions.onDesktopFontScaleChange,
                            )
                            Text(
                                text = "中控歌词位置直接在悬浮歌词上拖动调节",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }

                        AdjustmentTarget.CLUSTER -> {
                            LyricSlider(
                                label = "仪表盘歌词大小",
                                value = state.clusterFontScale,
                                valueText = "${(state.clusterFontScale * 100).roundToInt()}%",
                                range = LyricSettings.FONT_SCALE_MIN..LyricSettings.FONT_SCALE_MAX,
                                enabled = state.clusterActive,
                                onValueChange = actions.onClusterFontScaleChange,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LyricSlider(
                                    label = "仪表水平位置",
                                    value = state.clusterX,
                                    valueText = "${state.clusterX.roundToInt()}%",
                                    range = 0f..100f,
                                    enabled = state.clusterActive,
                                    onValueChange = actions.onClusterXChange,
                                    modifier = Modifier.weight(1f),
                                )
                                LyricSlider(
                                    label = "仪表垂直位置",
                                    value = state.clusterY,
                                    valueText = "${state.clusterY.roundToInt()}%",
                                    range = 0f..100f,
                                    enabled = state.clusterActive,
                                    onValueChange = actions.onClusterYChange,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    PresetChipRow(
                        title = "歌词行数（中控与仪表盘共用）",
                        options = (LyricSettings.SHARED_LINE_COUNT_MIN..LyricSettings.SHARED_LINE_COUNT_MAX)
                            .map { it.toString() to "${it}行" },
                        selectedId = state.sharedLineCount.toString(),
                        enabled = state.anyActive,
                        onSelect = { value ->
                            actions.onSharedLineCountChange(
                                value.toIntOrNull() ?: LyricSettings.SHARED_LINE_COUNT_DEFAULT,
                            )
                        },
                    )

                    LyricColorControls(
                        textColor = state.sharedTextColor,
                        highlightColor = state.sharedHighlightColor,
                        enabled = state.anyActive,
                        onTextColorChange = actions.onSharedTextColorChange,
                        onHighlightColorChange = actions.onSharedHighlightColorChange,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PresetChipRow(
                            title = "字体风格",
                            options = LyricStylePresets.fonts.map { it.id to it.label },
                            selectedId = state.sharedFontStyleId,
                            enabled = state.anyActive,
                            onSelect = actions.onSharedFontStyleChange,
                            modifier = Modifier.weight(1f),
                        )
                        PresetChipRow(
                            title = "滚动动效",
                            options = LyricStylePresets.motions.map { it.id to it.label },
                            selectedId = state.sharedMotionPresetId,
                            enabled = state.anyActive,
                            onSelect = actions.onSharedMotionPresetChange,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    LyricSlider(
                        label = "歌词同步微调",
                        value = state.syncOffset,
                        valueText = LyricSettings.formatOffset(state.syncOffset.roundToInt()),
                        range = LyricSettings.SYNC_MIN_MS.toFloat()..LyricSettings.SYNC_MAX_MS.toFloat(),
                        enabled = state.anyActive,
                        onValueChange = actions.onSyncOffsetChange,
                    )
                }
            }
        }
    }
}

private enum class AdjustmentTarget(val id: String) {
    DESKTOP("desktop"),
    CLUSTER("cluster");

    companion object {
        fun fromId(id: String): AdjustmentTarget = values().firstOrNull { it.id == id } ?: DESKTOP
    }
}

@Composable
private fun LyricModeToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(CardSurfaceElevated.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(
                width = if (checked) 1.5.dp else 1.dp,
                color = if (checked) AccentGreen else CardBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun SecondaryLyricButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
        ),
        modifier = modifier.height(42.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
