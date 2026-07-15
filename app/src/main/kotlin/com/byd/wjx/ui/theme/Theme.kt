package com.byd.wjx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColorScheme = lightColorScheme(
    background = LightSuperToyBoxColors.backgroundStart,
    surface = LightSuperToyBoxColors.cardSurface,
    surfaceVariant = LightSuperToyBoxColors.cardSurfaceElevated,
    primary = AccentGreen,
    secondary = AccentBlue,
    tertiary = AccentTeal,
    error = SocRed,
    onBackground = LightSuperToyBoxColors.textPrimary,
    onSurface = LightSuperToyBoxColors.textPrimary,
    onPrimary = AccentContent,
    onSecondary = AccentContent,
    outline = LightSuperToyBoxColors.cardBorder,
    surfaceTint = Color.Transparent
)

private val DarkColorScheme = darkColorScheme(
    background = DarkSuperToyBoxColors.backgroundStart,
    surface = DarkSuperToyBoxColors.cardSurface,
    surfaceVariant = DarkSuperToyBoxColors.cardSurfaceElevated,
    primary = AccentGreen,
    secondary = AccentBlue,
    tertiary = AccentTeal,
    error = SocRed,
    onBackground = DarkSuperToyBoxColors.textPrimary,
    onSurface = DarkSuperToyBoxColors.textPrimary,
    onPrimary = AccentContent,
    onSecondary = AccentContent,
    outline = DarkSuperToyBoxColors.cardBorder,
    surfaceTint = Color.Transparent,
)

enum class ThemeMode(val storedValue: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

@Composable
fun SuperToyBoxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val appColors = if (darkTheme) DarkSuperToyBoxColors else LightSuperToyBoxColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val baseDensity = LocalDensity.current
    val readableDensity = remember(baseDensity.density, baseDensity.fontScale) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * APP_FONT_SCALE,
        )
    }

    CompositionLocalProvider(
        LocalSuperToyBoxColors provides appColors,
        LocalSuperToyBoxDarkTheme provides darkTheme,
        LocalDensity provides readableDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SuperToyBoxTypography,
            content = content,
        )
    }
}

private const val APP_FONT_SCALE = 1.08f
