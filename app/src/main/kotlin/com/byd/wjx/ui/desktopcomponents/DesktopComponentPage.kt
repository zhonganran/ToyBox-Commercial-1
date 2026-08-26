package com.byd.wjx.ui.desktopcomponents

import androidx.compose.ui.graphics.Color

internal enum class DesktopComponentPage(
    val label: String,
    val description: String,
    val colors: List<Color>,
) {
    Lyrics(
        label = "歌词组件",
        description = "桌面歌词与仪表盘歌词分别调节",
        colors = listOf(Color(0xFF2375B7), Color(0xFF123F83)),
    ),
    QuickCard(
        label = "桌面卡片",
        description = "车辆控制（含电滑门）与车外音效",
        colors = listOf(Color(0xFF1A5960), Color(0xFF0C3138)),
    ),
    ClusterCard(
        label = "仪表卡片",
        description = "自定义转速、温度与车辆数据",
        colors = listOf(Color(0xFF8A4B24), Color(0xFF183F58)),
    ),
    ClusterCard2(
        label = "仪表卡片2",
        description = "第二组独立车辆数据",
        colors = listOf(Color(0xFF245C4B), Color(0xFF17384F)),
    ),
}
