package com.byd.wjx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    background = NavyDark,
    surface = CardSurface,
    surfaceVariant = CardSurfaceElevated,
    primary = AccentGreen,
    secondary = AccentBlue,
    tertiary = AccentTeal,
    error = SocRed,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = AccentContent,
    onSecondary = AccentContent,
    outline = CardBorder,
    surfaceTint = Color.Transparent
)

@Composable
fun SuperToyBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = SuperToyBoxTypography,
        content = content
    )
}
