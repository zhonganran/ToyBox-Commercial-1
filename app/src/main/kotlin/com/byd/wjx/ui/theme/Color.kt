package com.byd.wjx.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SuperToyBoxColors(
    val backgroundStart: Color,
    val backgroundMiddle: Color,
    val backgroundEnd: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val chartGrid: Color,
    val navBarBackground: Color,
    val navIndicator: Color,
    val glassSurface: Color,
    val glassStroke: Color,
)

internal val LightSuperToyBoxColors = SuperToyBoxColors(
    backgroundStart = Color(0xFFF5FAFA),
    backgroundMiddle = Color(0xFFE9F4F7),
    backgroundEnd = Color(0xFFD9E9EE),
    cardSurface = Color(0xF7FFFFFF),
    cardSurfaceElevated = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE0E8EE),
    textPrimary = Color(0xFF10202A),
    textSecondary = Color(0xFF50606D),
    textMuted = Color(0xFF82909B),
    chartGrid = Color(0xFFDCE7ED),
    navBarBackground = Color(0xF4FFFFFF),
    navIndicator = Color(0xFFE1F6EE),
    glassSurface = Color(0xCCFFFFFF),
    glassStroke = Color(0xA6FFFFFF),
)

internal val DarkSuperToyBoxColors = SuperToyBoxColors(
    backgroundStart = Color(0xFF091017),
    backgroundMiddle = Color(0xFF101B24),
    backgroundEnd = Color(0xFF172631),
    cardSurface = Color(0xF21A2732),
    cardSurfaceElevated = Color(0xFF22313D),
    cardBorder = Color(0xFF3B4C59),
    textPrimary = Color(0xFFF3F7FA),
    textSecondary = Color(0xFFC3CED7),
    textMuted = Color(0xFF91A2AF),
    chartGrid = Color(0xFF31434F),
    navBarBackground = Color(0xF216222C),
    navIndicator = Color(0xFF25483F),
    glassSurface = Color(0xD91A2732),
    glassStroke = Color(0x593F5362),
)

internal val LocalSuperToyBoxColors = staticCompositionLocalOf { LightSuperToyBoxColors }
val LocalSuperToyBoxDarkTheme = staticCompositionLocalOf { false }

val NavyDark: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.backgroundStart
val NavyDeep: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.backgroundMiddle
val NavyMid: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.backgroundEnd

val CardSurface: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.cardSurface
val CardSurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.cardSurfaceElevated
val CardBorder: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.cardBorder

// Accent colors
val AccentGreen = Color(0xFF17C789)
val AccentGreenDark = Color(0xFF0FA66E)
val AccentBlue = Color(0xFF4B8DFF)
val AccentOrange = Color(0xFFFF9D3D)
val AccentTeal = Color(0xFF21C7B7)
val AccentPurple = Color(0xFF8B5CF6)
val AccentContent = Color(0xFF08231B)

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.textPrimary
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.textSecondary
val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.textMuted

// SOC traffic light
val SocGreen = Color(0xFF4ADE80)
val SocYellow = Color(0xFFFBBF24)
val SocRed = Color(0xFFEF4444)

// Consumption rating
val ConsumptionGood = Color(0xFF4ADE80)
val ConsumptionMid = Color(0xFFFBBF24)
val ConsumptionBad = Color(0xFFEF4444)

// Charts
val ChartGrid: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.chartGrid
val ChartLine = Color(0xFF17C789)
val ChartLineSecondary = Color(0xFF4B8DFF)

// Navigation
val NavBarBackground: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.navBarBackground
val NavIndicator: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.navIndicator

// Super Toy Box scene palette. These colors are intentionally shared by the
// full-screen canvases so each section feels distinct without fragmenting the
// product into unrelated themes.
val SceneCyan = Color(0xFF35DCD6)
val SceneSky = Color(0xFF68B8FF)
val SceneBlue = Color(0xFF376DF4)
val SceneViolet = Color(0xFF7657F5)
val ScenePink = Color(0xFFFF6FA8)
val SceneCoral = Color(0xFFFF8B73)
val SceneAmber = Color(0xFFFFB54A)
val SceneMint = Color(0xFF25D9AE)
val GlassWhite: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.glassSurface
val GlassStroke: Color
    @Composable @ReadOnlyComposable get() = LocalSuperToyBoxColors.current.glassStroke
