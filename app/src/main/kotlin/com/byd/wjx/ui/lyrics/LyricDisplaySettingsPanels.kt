package com.byd.wjx.ui.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.ui.pro.ProGlassPanel
import com.byd.wjx.ui.pro.ProSectionTitle
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import kotlin.math.roundToInt

internal data class LyricPanelState(
    val selected: LyricPreviewType,
    val featureAllowed: Boolean,
    val desktopActive: Boolean,
    val clusterActive: Boolean,
    val expanded: Boolean,
    val allowOtherApps: Boolean,
    val desktopFontScale: Float,
    val clusterLineCount: Int,
    val clusterFontScale: Float,
    val clusterWidthScale: Float,
    val clusterAlpha: Float,
    val clusterX: Float,
    val clusterY: Float,
    val syncOffset: Float,
    val karaokeEnabled: Boolean,
    val fontStyleId: String,
    val motionPresetId: String,
    val colorPresetId: String,
) {
    val selectedActive: Boolean
        get() = when (selected) {
            LyricPreviewType.DesktopLyric -> desktopActive
            LyricPreviewType.ClusterLyric -> clusterActive
        }
}

internal data class LyricPanelActions(
    val onDesktopEnabledChange: (Boolean) -> Unit,
    val onClusterEnabledChange: (Boolean) -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
    val onAllowOtherAppsChange: (Boolean) -> Unit,
    val onOpenPlaybackPermission: () -> Unit,
    val onResetPosition: () -> Unit,
    val onNudgeCluster: (dx: Int, dy: Int) -> Unit,
    val onDesktopFontScaleChange: (Float) -> Unit,
    val onClusterLineCountChange: (Int) -> Unit,
    val onClusterFontScaleChange: (Float) -> Unit,
    val onClusterWidthScaleChange: (Float) -> Unit,
    val onClusterAlphaChange: (Float) -> Unit,
    val onClusterXChange: (Float) -> Unit,
    val onClusterYChange: (Float) -> Unit,
    val onSyncOffsetChange: (Float) -> Unit,
    val onKaraokeChange: (Boolean) -> Unit,
    val onFontStyleChange: (String) -> Unit,
    val onMotionPresetChange: (String) -> Unit,
    val onColorPresetChange: (String) -> Unit,
)

@Composable
internal fun LyricSettingsPanel(
    state: LyricPanelState,
    actions: LyricPanelActions,
) {
    val desktopSelected = state.selected == LyricPreviewType.DesktopLyric
    ProSectionTitle(
        title = if (desktopSelected) "桌面歌词设置" else "仪表盘歌词设置",
        subtitle = if (desktopSelected) {
            "透明歌词支持拖动、长按关闭和播放行高亮"
        } else {
            "通过方向键和滑块精调仪表盘歌词位置与大小"
        },
    )
    ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (desktopSelected) {
                SwitchRow(
                    title = "中控桌面歌词",
                    subtitle = when {
                        !state.featureAllowed -> "需要激活后使用，试用期内可直接开启"
                        state.desktopActive -> "播放时显示 5 行透明歌词，暂停后自动隐藏"
                        else -> "开启后在桌面透明显示歌词"
                    },
                    checked = state.desktopActive,
                    onCheckedChange = actions.onDesktopEnabledChange,
                )
            } else {
                SwitchRow(
                    title = "仪表盘投屏歌词",
                    subtitle = when {
                        !state.featureAllowed -> "需要激活后使用，试用期内可直接开启"
                        state.clusterActive -> "已投到仪表盘，当前显示 ${state.clusterLineCount} 行歌词"
                        else -> "开启后在仪表盘显示，可自定义 1 至 5 行歌词"
                    },
                    checked = state.clusterActive,
                    onCheckedChange = actions.onClusterEnabledChange,
                )
            }

            if (state.selectedActive) {
                DetailToggleRow(
                    title = "歌词显示调节",
                    subtitle = "权限、位置、大小、颜色和播放行高亮",
                    expanded = state.expanded,
                    onToggle = { actions.onExpandedChange(!state.expanded) },
                )
                if (state.expanded) {
                    if (desktopSelected && state.desktopActive) {
                        SwitchRow(
                            title = "允许显示在其他应用",
                            subtitle = "关闭时只在桌面/本应用显示，打开后导航、音乐等界面也可显示",
                            checked = state.allowOtherApps,
                            enabled = state.desktopActive,
                            onCheckedChange = actions.onAllowOtherAppsChange,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryLyricButton(
                            text = "播放信息权限",
                            onClick = actions.onOpenPlaybackPermission,
                        )
                        SecondaryLyricButton(
                            text = "重置歌词位置",
                            onClick = actions.onResetPosition,
                        )
                    }
                    if (!desktopSelected && state.clusterActive) {
                        ClusterLyricNudgeControls(
                            enabled = state.clusterActive,
                            onLeft = { actions.onNudgeCluster(-2, 0) },
                            onRight = { actions.onNudgeCluster(2, 0) },
                            onUp = { actions.onNudgeCluster(0, -2) },
                            onDown = { actions.onNudgeCluster(0, 2) },
                        )
                    }
                    if (desktopSelected && state.desktopActive) {
                        LyricSlider(
                            label = "中控歌词大小",
                            value = state.desktopFontScale,
                            valueText = "${(state.desktopFontScale * 100).roundToInt()}%",
                            range = LyricSettings.FONT_MIN..LyricSettings.FONT_MAX,
                            enabled = state.desktopActive,
                            onValueChange = actions.onDesktopFontScaleChange,
                        )
                    }
                    if (!desktopSelected && state.clusterActive) {
                        PresetChipRow(
                            title = "仪表歌词行数",
                            options = (LyricSettings.CLUSTER_LINE_COUNT_MIN..LyricSettings.CLUSTER_LINE_COUNT_MAX)
                                .map { it.toString() to "${it}行" },
                            selectedId = state.clusterLineCount.toString(),
                            enabled = state.clusterActive,
                            onSelect = { value ->
                                actions.onClusterLineCountChange(
                                    value.toIntOrNull() ?: LyricSettings.CLUSTER_LINE_COUNT_DEFAULT,
                                )
                            },
                        )
                        LyricSlider(
                            label = "仪表歌词大小",
                            value = state.clusterFontScale,
                            valueText = "${(state.clusterFontScale * 100).roundToInt()}%",
                            range = LyricSettings.CLUSTER_FONT_MIN..LyricSettings.CLUSTER_FONT_MAX,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterFontScaleChange,
                        )
                        LyricSlider(
                            label = "仪表歌词宽度",
                            value = state.clusterWidthScale,
                            valueText = "${(state.clusterWidthScale * 100).roundToInt()}%",
                            range = LyricSettings.CLUSTER_WIDTH_MIN..LyricSettings.CLUSTER_WIDTH_MAX,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterWidthScaleChange,
                        )
                        LyricSlider(
                            label = "仪表不透明度",
                            value = state.clusterAlpha,
                            valueText = "${(state.clusterAlpha * 100).roundToInt()}%",
                            range = LyricSettings.ALPHA_MIN..LyricSettings.ALPHA_MAX,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterAlphaChange,
                        )
                        LyricSlider(
                            label = "仪表水平位置",
                            value = state.clusterX,
                            valueText = "${state.clusterX.roundToInt()}%",
                            range = 0f..100f,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterXChange,
                        )
                        LyricSlider(
                            label = "仪表垂直位置",
                            value = state.clusterY,
                            valueText = "${state.clusterY.roundToInt()}%",
                            range = 0f..100f,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterYChange,
                        )
                    }
                    LyricSlider(
                        label = "歌词同步微调",
                        value = state.syncOffset,
                        valueText = LyricSettings.formatOffset(state.syncOffset.roundToInt()),
                        range = LyricSettings.SYNC_MIN_MS.toFloat()..LyricSettings.SYNC_MAX_MS.toFloat(),
                        enabled = state.selectedActive,
                        onValueChange = actions.onSyncOffsetChange,
                    )
                    SwitchRow(
                        title = "播放行高亮",
                        subtitle = "当前播放歌词使用高亮色，其余歌词保持清晰显示",
                        checked = state.karaokeEnabled,
                        enabled = state.selectedActive,
                        onCheckedChange = actions.onKaraokeChange,
                    )
                    PresetChipRow(
                        title = "字体风格",
                        options = LyricStylePresets.fonts.map { it.id to it.label },
                        selectedId = state.fontStyleId,
                        enabled = state.selectedActive,
                        onSelect = actions.onFontStyleChange,
                    )
                    PresetChipRow(
                        title = "滚动动效",
                        options = LyricStylePresets.motions.map { it.id to it.label },
                        selectedId = state.motionPresetId,
                        enabled = state.selectedActive,
                        onSelect = actions.onMotionPresetChange,
                    )
                    ColorPresetChipRow(
                        selectedId = state.colorPresetId,
                        enabled = state.selectedActive,
                        onSelect = actions.onColorPresetChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryLyricButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = LyricControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
        ),
        modifier = Modifier.height(42.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val LyricControlShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
