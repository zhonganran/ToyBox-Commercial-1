package com.byd.wjx.ui.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.lyrics.LyricSettings
import com.byd.wjx.lyrics.LyricStylePresets
import com.byd.wjx.overlay.AutoDoorFloatingSettings
import com.byd.wjx.overlay.SoundFloatingSettings
import com.byd.wjx.ui.pro.ProGlassPanel
import com.byd.wjx.ui.pro.ProSectionTitle
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.widget.WidgetPreferences
import kotlin.math.roundToInt

internal data class SoundPanelState(
    val enabled: Boolean,
    val expanded: Boolean,
    val iconStyle: String,
    val iconAlpha: Float,
    val iconScale: Float,
    val customIconAvailable: Boolean,
    val customIconBitmap: ImageBitmap?,
)

internal data class SoundPanelActions(
    val onEnabledChange: (Boolean) -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
    val onIconStyleChange: (String) -> Unit,
    val onPickCustomIcon: () -> Unit,
    val onAlphaChange: (Float) -> Unit,
    val onScaleChange: (Float) -> Unit,
    val onResetPosition: () -> Unit,
    val onResetIcon: () -> Unit,
)

@Composable
internal fun SoundFloatingSettingsPanel(
    state: SoundPanelState,
    actions: SoundPanelActions,
) {
    ProSectionTitle(
        title = "音效悬浮窗设置",
        subtitle = "图标样式、透明度、大小和桌面位置",
    )
    ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SwitchRow(
                title = "音效悬浮窗",
                subtitle = if (state.enabled) {
                    "已在桌面显示，点击展开后可播放音效"
                } else {
                    "开启后显示桌面音效入口"
                },
                checked = state.enabled,
                onCheckedChange = actions.onEnabledChange,
            )
            if (state.enabled) {
                DetailToggleRow(
                    title = "音效悬浮窗调节",
                    subtitle = "图标样式、透明度、大小和位置",
                    expanded = state.expanded,
                    onToggle = { actions.onExpandedChange(!state.expanded) },
                )
                if (state.expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "音效悬浮窗图标",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FloatingIconOption(
                                label = "经典",
                                selected = state.iconStyle == SoundFloatingSettings.ICON_STYLE_CLASSIC,
                                enabled = state.enabled,
                                onClick = {
                                    actions.onIconStyleChange(SoundFloatingSettings.ICON_STYLE_CLASSIC)
                                },
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.sound_float_face),
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            FloatingIconOption(
                                label = "工具箱",
                                selected = state.iconStyle == SoundFloatingSettings.ICON_STYLE_TOOLBOX,
                                enabled = state.enabled,
                                onClick = {
                                    actions.onIconStyleChange(SoundFloatingSettings.ICON_STYLE_TOOLBOX)
                                },
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.sound_float_toolbox),
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            FloatingIconOption(
                                label = "自定义",
                                selected = state.iconStyle == SoundFloatingSettings.ICON_STYLE_CUSTOM,
                                enabled = state.enabled,
                                onClick = {
                                    if (state.customIconAvailable) {
                                        actions.onIconStyleChange(SoundFloatingSettings.ICON_STYLE_CUSTOM)
                                    } else {
                                        actions.onPickCustomIcon()
                                    }
                                },
                            ) {
                                if (state.customIconBitmap != null) {
                                    Image(
                                        bitmap = state.customIconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier.size(46.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Text(
                                        text = "上传",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Button(
                                onClick = actions.onPickCustomIcon,
                                enabled = state.enabled,
                                shape = DesktopControlShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardSurfaceElevated,
                                    contentColor = TextPrimary,
                                ),
                                modifier = Modifier.height(42.dp),
                            ) {
                                Text(
                                    text = "选择自定义图标",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    WidgetSlider(
                        label = "图标透明度",
                        value = state.iconAlpha,
                        valueText = "${(state.iconAlpha * 100).toInt()}%",
                        range = SoundFloatingSettings.ICON_ALPHA_MIN..SoundFloatingSettings.ICON_ALPHA_MAX,
                        enabled = state.enabled,
                        onValueChange = actions.onAlphaChange,
                    )
                    WidgetSlider(
                        label = "图标大小",
                        value = state.iconScale,
                        valueText = "${(state.iconScale * 100).toInt()}%",
                        range = SoundFloatingSettings.ICON_SCALE_MIN..SoundFloatingSettings.ICON_SCALE_MAX,
                        enabled = state.enabled,
                        onValueChange = actions.onScaleChange,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryDesktopButton(
                            text = "重置音效悬浮窗位置",
                            onClick = actions.onResetPosition,
                        )
                        SecondaryDesktopButton(
                            text = "恢复经典图标",
                            enabled = state.enabled,
                            onClick = actions.onResetIcon,
                        )
                    }
                }
            }
        }
    }
}

internal data class LyricPanelState(
    val selected: DesktopPreviewType,
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
            DesktopPreviewType.DesktopLyric -> desktopActive
            DesktopPreviewType.ClusterLyric -> clusterActive
            else -> false
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
    val desktopSelected = state.selected == DesktopPreviewType.DesktopLyric
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
                        SecondaryDesktopButton(
                            text = "播放信息权限",
                            onClick = actions.onOpenPlaybackPermission,
                        )
                        SecondaryDesktopButton(
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
                        WidgetSlider(
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
                        WidgetSlider(
                            label = "仪表歌词大小",
                            value = state.clusterFontScale,
                            valueText = "${(state.clusterFontScale * 100).roundToInt()}%",
                            range = LyricSettings.CLUSTER_FONT_MIN..LyricSettings.CLUSTER_FONT_MAX,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterFontScaleChange,
                        )
                        WidgetSlider(
                            label = "仪表歌词宽度",
                            value = state.clusterWidthScale,
                            valueText = "${(state.clusterWidthScale * 100).roundToInt()}%",
                            range = LyricSettings.CLUSTER_WIDTH_MIN..LyricSettings.CLUSTER_WIDTH_MAX,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterWidthScaleChange,
                        )
                        WidgetSlider(
                            label = "仪表不透明度",
                            value = state.clusterAlpha,
                            valueText = "${(state.clusterAlpha * 100).roundToInt()}%",
                            range = LyricSettings.ALPHA_MIN..LyricSettings.ALPHA_MAX,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterAlphaChange,
                        )
                        WidgetSlider(
                            label = "仪表水平位置",
                            value = state.clusterX,
                            valueText = "${state.clusterX.roundToInt()}%",
                            range = 0f..100f,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterXChange,
                        )
                        WidgetSlider(
                            label = "仪表垂直位置",
                            value = state.clusterY,
                            valueText = "${state.clusterY.roundToInt()}%",
                            range = 0f..100f,
                            enabled = state.clusterActive,
                            onValueChange = actions.onClusterYChange,
                        )
                    }
                    WidgetSlider(
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

internal data class UtilityPanelState(
    val selected: DesktopPreviewType,
    val autoDoorEnabled: Boolean,
    val autoDoorExpanded: Boolean,
    val autoDoorLocked: Boolean,
    val autoDoorAlpha: Float,
    val autoDoorScale: Float,
    val widgetEnabled: Boolean,
    val widgetExpanded: Boolean,
    val widgetAlpha: Float,
    val widgetScale: Float,
    val widgetButtonsEnabled: Boolean,
    val leftTapApp: WidgetPreferences.LeftTapAppState,
)

internal data class UtilityPanelActions(
    val onAutoDoorEnabledChange: (Boolean) -> Unit,
    val onAutoDoorExpandedChange: (Boolean) -> Unit,
    val onAutoDoorLockedChange: (Boolean) -> Unit,
    val onAutoDoorAlphaChange: (Float) -> Unit,
    val onAutoDoorScaleChange: (Float) -> Unit,
    val onResetAutoDoorPosition: () -> Unit,
    val onWidgetEnabledChange: (Boolean) -> Unit,
    val onWidgetExpandedChange: (Boolean) -> Unit,
    val onWidgetAlphaChange: (Float) -> Unit,
    val onWidgetScaleChange: (Float) -> Unit,
    val onLeftTapZoningChange: (Boolean) -> Unit,
    val onWidgetButtonsChange: (Boolean) -> Unit,
    val onChooseLeftTapApp: () -> Unit,
    val onResetWidgetPosition: () -> Unit,
)

@Composable
internal fun UtilitySettingsPanel(
    state: UtilityPanelState,
    actions: UtilityPanelActions,
) {
    val batterySelected = state.selected == DesktopPreviewType.Battery
    ProSectionTitle(
        title = if (batterySelected) "电量小组件设置" else "自动门设置",
        subtitle = if (batterySelected) {
            "显示电量、续航和快捷入口，透明度与大小实时生效"
        } else {
            "设置桌面自动门控制卡片的固定状态、透明度和大小"
        },
    )
    ProGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (batterySelected) {
                BatteryWidgetSettings(state, actions)
            } else {
                AutoDoorWidgetSettings(state, actions)
            }
        }
    }
}

@Composable
private fun AutoDoorWidgetSettings(
    state: UtilityPanelState,
    actions: UtilityPanelActions,
) {
    SwitchRow(
        title = "自动门小组件",
        subtitle = if (state.autoDoorEnabled) {
            "已固定在桌面，点击左右卡片控制电滑门"
        } else {
            "开启后在桌面显示左右电滑门控制卡片"
        },
        checked = state.autoDoorEnabled,
        onCheckedChange = actions.onAutoDoorEnabledChange,
    )
    if (!state.autoDoorEnabled) return

    DetailToggleRow(
        title = "自动门小组件调节",
        subtitle = "透明度、大小、固定状态和位置",
        expanded = state.autoDoorExpanded,
        onToggle = { actions.onAutoDoorExpandedChange(!state.autoDoorExpanded) },
    )
    if (!state.autoDoorExpanded) return

    SwitchRow(
        title = "固定在桌面",
        subtitle = if (state.autoDoorLocked) {
            "已固定，长按左侧车辆图也可解锁"
        } else {
            "已解锁，可拖动左侧车辆图调整位置"
        },
        checked = state.autoDoorLocked,
        enabled = state.autoDoorEnabled,
        onCheckedChange = actions.onAutoDoorLockedChange,
    )
    WidgetSlider(
        label = "自动门透明度",
        value = state.autoDoorAlpha,
        valueText = "${(state.autoDoorAlpha * 100).toInt()}%",
        range = AutoDoorFloatingSettings.ALPHA_MIN..AutoDoorFloatingSettings.ALPHA_MAX,
        enabled = state.autoDoorEnabled,
        onValueChange = actions.onAutoDoorAlphaChange,
    )
    WidgetSlider(
        label = "自动门大小",
        value = state.autoDoorScale,
        valueText = "${(state.autoDoorScale * 100).toInt()}%",
        range = AutoDoorFloatingSettings.SCALE_MIN..AutoDoorFloatingSettings.SCALE_MAX,
        enabled = state.autoDoorEnabled,
        onValueChange = actions.onAutoDoorScaleChange,
    )
    SecondaryDesktopButton(
        text = "重置自动门位置",
        enabled = state.autoDoorEnabled,
        onClick = actions.onResetAutoDoorPosition,
    )
}

@Composable
private fun BatteryWidgetSettings(
    state: UtilityPanelState,
    actions: UtilityPanelActions,
) {
    SwitchRow(
        title = "电量小组件",
        subtitle = if (state.widgetEnabled) "已显示在桌面" else "开启后显示车辆状态小组件",
        checked = state.widgetEnabled,
        onCheckedChange = actions.onWidgetEnabledChange,
    )
    if (!state.widgetEnabled) return

    DetailToggleRow(
        title = "电量小组件调节",
        subtitle = "透明度、大小、点击区域和快捷按钮",
        expanded = state.widgetExpanded,
        onToggle = { actions.onWidgetExpandedChange(!state.widgetExpanded) },
    )
    if (!state.widgetExpanded) return

    WidgetSlider(
        label = "不透明度",
        value = state.widgetAlpha,
        valueText = "${(state.widgetAlpha * 100).toInt()}%",
        range = 0.3f..1.0f,
        enabled = state.widgetEnabled,
        onValueChange = actions.onWidgetAlphaChange,
    )
    WidgetSlider(
        label = "大小",
        value = state.widgetScale,
        valueText = "${(state.widgetScale * 100).toInt()}%",
        range = WidgetPreferences.SCALE_MIN..WidgetPreferences.SCALE_MAX,
        enabled = state.widgetEnabled,
        onValueChange = actions.onWidgetScaleChange,
    )
    SwitchRow(
        title = "点击区域",
        subtitle = "左侧 1/3 打开指定应用，其余区域打开超级玩具箱",
        checked = state.leftTapApp.enabled,
        enabled = state.widgetEnabled,
        onCheckedChange = actions.onLeftTapZoningChange,
    )
    SwitchRow(
        title = "桌面快捷按钮",
        subtitle = if (state.widgetButtonsEnabled) {
            "单击小组件展开 1-4 按钮，双击进入超级玩具箱"
        } else {
            "开启后可用 1-4 按钮触发智能联动规则"
        },
        checked = state.widgetButtonsEnabled,
        enabled = state.widgetEnabled,
        onCheckedChange = actions.onWidgetButtonsChange,
    )
    Button(
        onClick = actions.onChooseLeftTapApp,
        enabled = state.widgetEnabled && state.leftTapApp.enabled,
        shape = DesktopControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        Text(
            text = "左侧点击应用：${state.leftTapApp.label}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Button(
        onClick = actions.onResetWidgetPosition,
        enabled = state.widgetEnabled,
        shape = DesktopControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentGreen,
            contentColor = AccentContent,
        ),
        modifier = Modifier.height(42.dp),
    ) {
        Text(
            text = "重置小组件位置",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryDesktopButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = DesktopControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CardSurfaceElevated,
            contentColor = TextPrimary,
        ),
        modifier = Modifier.height(42.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val DesktopControlShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
