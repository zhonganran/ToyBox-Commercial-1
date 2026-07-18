package com.byd.wjx.lyrics

import android.graphics.Typeface

data class LyricFontPreset(
    val id: String,
    val label: String,
    val typefaceFamily: String,
    val typefaceStyle: Int = Typeface.NORMAL,
    val letterSpacingEm: Float,
    val lineSpacingExtraDp: Float,
    val shadowRadiusDp: Float,
    val shadowDyDp: Float,
    val shadowColor: Int = 0x99000000.toInt(),
    val textSkewX: Float = 0f,
    val strokeWidthDp: Float = 0f,
    val strokeColor: Int = 0xAA000000.toInt(),
)

data class LyricMotionPreset(
    val id: String,
    val label: String,
    val currentScale: Float,
    val sideScale: Float,
    val sideAlpha: Float,
    val scrollDurationMs: Long,
    val easing: String,
    val glowRadiusDp: Float = 0f,
    val glowAlpha: Float = 0f,
    val highlightStrength: Float = 0.72f,
    val sideOffsetDp: Float = 0f,
    val tiltDegrees: Float = 0f,
    val currentOffsetDp: Float = 0f,
    val spotlightAlpha: Float = 0f,
)

data class LyricDisplayStyle(
    val font: LyricFontPreset,
    val motion: LyricMotionPreset,
    val sungColor: Int,
    val currentColor: Int,
    val upcomingColor: Int,
    val karaokeEnabled: Boolean,
)

data class LyricSolidColor(
    val id: String,
    val label: String,
    val color: Int,
)

object LyricSolidColorPalette {
    val colors = listOf(
        LyricSolidColor("white", "白", 0xFFFFFFFF.toInt()),
        LyricSolidColor("silver", "银", 0xFFC7D2E0.toInt()),
        LyricSolidColor("red", "红", 0xFFFF5252.toInt()),
        LyricSolidColor("deep_orange", "朱", 0xFFFF7043.toInt()),
        LyricSolidColor("orange", "橙", 0xFFFFA726.toInt()),
        LyricSolidColor("yellow", "黄", 0xFFFFD54F.toInt()),
        LyricSolidColor("lime", "柠", 0xFFCDDC39.toInt()),
        LyricSolidColor("green", "绿", 0xFF66BB6A.toInt()),
        LyricSolidColor("mint", "青", 0xFF26C6A3.toInt()),
        LyricSolidColor("cyan", "蓝青", 0xFF26C6DA.toInt()),
        LyricSolidColor("blue", "蓝", 0xFF42A5F5.toInt()),
        LyricSolidColor("indigo", "靛", 0xFF5C6BC0.toInt()),
        LyricSolidColor("purple", "紫", 0xFFAB47BC.toInt()),
        LyricSolidColor("pink", "粉", 0xFFEC6F9E.toInt()),
    )

    fun normalize(color: Int, fallback: Int): Int =
        colors.firstOrNull { it.color == color }?.color ?: fallback
}

object LyricStylePresets {
    val fonts = listOf(
        LyricFontPreset("soft", "轻柔", "sans-serif-light", letterSpacingEm = 0.03f, lineSpacingExtraDp = 11f, shadowRadiusDp = 4f, shadowDyDp = 1f),
        LyricFontPreset("balanced", "经典", "sans-serif-medium", letterSpacingEm = 0.01f, lineSpacingExtraDp = 8f, shadowRadiusDp = 3f, shadowDyDp = 1f, shadowColor = 0x00000000),
        LyricFontPreset("elegant", "雅宋", "serif", letterSpacingEm = 0.02f, lineSpacingExtraDp = 10f, shadowRadiusDp = 2f, shadowDyDp = 1f, shadowColor = 0x77000000),
        LyricFontPreset("rounded", "圆润", "sans-serif", letterSpacingEm = 0.05f, lineSpacingExtraDp = 12f, shadowRadiusDp = 5f, shadowDyDp = 1.5f, shadowColor = 0x55000000),
        LyricFontPreset("vivid", "张力", "sans-serif-black", letterSpacingEm = 0.04f, lineSpacingExtraDp = 9f, shadowRadiusDp = 6f, shadowDyDp = 2f, shadowColor = 0xAA000000.toInt()),
        LyricFontPreset("poetic", "诗意", "serif", typefaceStyle = Typeface.ITALIC, letterSpacingEm = 0.06f, lineSpacingExtraDp = 14f, shadowRadiusDp = 3f, shadowDyDp = 1f),
        LyricFontPreset("compact", "紧凑", "sans-serif-condensed", typefaceStyle = Typeface.BOLD, letterSpacingEm = 0.02f, lineSpacingExtraDp = 6f, shadowRadiusDp = 4f, shadowDyDp = 1f),
        LyricFontPreset("slim", "细长", "sans-serif-thin", letterSpacingEm = 0.10f, lineSpacingExtraDp = 13f, shadowRadiusDp = 3f, shadowDyDp = 1f),
        LyricFontPreset("neon", "霓虹", "sans-serif-medium", letterSpacingEm = 0.05f, lineSpacingExtraDp = 12f, shadowRadiusDp = 10f, shadowDyDp = 0f, shadowColor = 0xCC000000.toInt(), strokeWidthDp = 1.2f),
        LyricFontPreset("caption", "字幕", "sans-serif", typefaceStyle = Typeface.BOLD, letterSpacingEm = 0f, lineSpacingExtraDp = 7f, shadowRadiusDp = 2f, shadowDyDp = 2f, shadowColor = 0xCC000000.toInt(), strokeWidthDp = 1.6f),
        LyricFontPreset("italic", "斜体", "sans-serif", typefaceStyle = Typeface.ITALIC, letterSpacingEm = 0.04f, lineSpacingExtraDp = 11f, shadowRadiusDp = 5f, shadowDyDp = 1.5f, textSkewX = -0.18f),
    )

    val motions = listOf(
        LyricMotionPreset("soft", "轻柔", currentScale = 1.16f, sideScale = 0.88f, sideAlpha = 0.50f, scrollDurationMs = 460L, easing = "sine", highlightStrength = 0.68f),
        LyricMotionPreset("classic", "经典", currentScale = 1.22f, sideScale = 0.80f, sideAlpha = 0.48f, scrollDurationMs = 300L, easing = "cubic", highlightStrength = 0.72f),
        LyricMotionPreset("cascade", "错落", currentScale = 1.20f, sideScale = 0.78f, sideAlpha = 0.42f, scrollDurationMs = 360L, easing = "quart", highlightStrength = 0.75f, sideOffsetDp = 34f),
        LyricMotionPreset("bounce", "弹跃", currentScale = 1.30f, sideScale = 0.72f, sideAlpha = 0.44f, scrollDurationMs = 420L, easing = "back", highlightStrength = 0.78f, sideOffsetDp = 18f),
        LyricMotionPreset("glow", "光晕", currentScale = 1.18f, sideScale = 0.76f, sideAlpha = 0.28f, scrollDurationMs = 360L, easing = "quart", glowRadiusDp = 18f, glowAlpha = 0.68f, highlightStrength = 0.90f),
        LyricMotionPreset("cinematic", "电影", currentScale = 1.36f, sideScale = 0.68f, sideAlpha = 0.25f, scrollDurationMs = 560L, easing = "quart", glowRadiusDp = 10f, glowAlpha = 0.35f, highlightStrength = 0.85f, sideOffsetDp = 46f, tiltDegrees = 4f),
        LyricMotionPreset("stage", "舞台", currentScale = 1.28f, sideScale = 0.70f, sideAlpha = 0.24f, scrollDurationMs = 260L, easing = "cubic", glowRadiusDp = 22f, glowAlpha = 0.78f, highlightStrength = 0.95f, sideOffsetDp = 58f, tiltDegrees = 7f),
        LyricMotionPreset("breathe", "呼吸", currentScale = 1.24f, sideScale = 0.86f, sideAlpha = 0.32f, scrollDurationMs = 620L, easing = "sine", glowRadiusDp = 16f, glowAlpha = 0.52f, highlightStrength = 0.88f),
        LyricMotionPreset("slide", "滑入", currentScale = 1.22f, sideScale = 0.74f, sideAlpha = 0.38f, scrollDurationMs = 340L, easing = "quart", highlightStrength = 0.78f, sideOffsetDp = 72f, currentOffsetDp = 22f),
        LyricMotionPreset("focus", "聚焦", currentScale = 1.42f, sideScale = 0.60f, sideAlpha = 0.20f, scrollDurationMs = 420L, easing = "cubic", glowRadiusDp = 20f, glowAlpha = 0.58f, highlightStrength = 0.92f),
        LyricMotionPreset("slash", "斜切", currentScale = 1.26f, sideScale = 0.70f, sideAlpha = 0.34f, scrollDurationMs = 300L, easing = "back", highlightStrength = 0.82f, sideOffsetDp = 54f, tiltDegrees = -8f, currentOffsetDp = -18f),
    )

    fun font(id: String): LyricFontPreset = fonts.firstOrNull { it.id == id } ?: fonts.first()
    fun motion(id: String): LyricMotionPreset = motions.firstOrNull { it.id == id } ?: motions.first()
}
