package com.byd.wjx.ui.pro

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.byd.wjx.ui.theme.LocalAppWallpaper
import com.byd.wjx.ui.theme.LocalSuperToyBoxDarkTheme
import kotlin.math.max

@Composable
fun ProDynamicBackground(
    modifier: Modifier = Modifier,
    scene: String = "home",
    animated: Boolean = true,
) {
    val motion = if (animated) {
        val transition = rememberInfiniteTransition(label = "scene-background")
        val value by transition.animateFloat(
            initialValue = -0.08f,
            targetValue = 0.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14_000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scene-motion",
        )
        value
    } else {
        0f
    }
    val darkTheme = LocalSuperToyBoxDarkTheme.current
    val wallpaper = LocalAppWallpaper.current
    val routeAccent = proNavigationAccent(scene)

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(wallpaper.drawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrimColors = if (darkTheme) {
                val alpha = if (wallpaper.darkArtwork) 0.42f else 0.66f
                listOf(
                    Color(0xFF06131C).copy(alpha = alpha),
                    Color(0xFF102631).copy(alpha = (alpha - 0.08f).coerceAtLeast(0f)),
                    Color(0xFF111722).copy(alpha = (alpha + 0.04f).coerceAtMost(1f)),
                )
            } else {
                val alpha = if (wallpaper.darkArtwork) 0.50f else 0.12f
                listOf(
                    Color.White.copy(alpha = alpha),
                    Color.White.copy(alpha = (alpha - 0.05f).coerceAtLeast(0f)),
                    Color(0xFFF4FAFC).copy(alpha = (alpha + 0.08f).coerceAtMost(1f)),
                )
            }
            drawRect(brush = Brush.verticalGradient(scrimColors))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        routeAccent.copy(alpha = if (darkTheme) 0.10f else 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * (0.48f + motion), size.height * 0.34f),
                    radius = max(size.width, size.height) * 0.56f,
                ),
            )
            val gridColor = Color.White.copy(alpha = if (darkTheme) 0.025f else 0.04f)
            repeat(9) { index ->
                val y = size.height * (0.12f + index * 0.075f)
                val path = Path().apply {
                    moveTo(0f, y)
                    cubicTo(
                        size.width * 0.28f,
                        y - size.height * (0.035f + motion),
                        size.width * 0.67f,
                        y + size.height * (0.045f - motion),
                        size.width,
                        y - size.height * 0.018f,
                    )
                }
                drawPath(path, gridColor, style = Stroke(width = 1f))
            }
            repeat(15) { index ->
                val x = size.width * (index / 14f)
                val path = Path().apply {
                    moveTo(x, size.height * 0.07f)
                    cubicTo(
                        x - size.width * 0.04f,
                        size.height * 0.32f,
                        x + size.width * 0.04f,
                        size.height * 0.60f,
                        x,
                        size.height * 0.88f,
                    )
                }
                drawPath(
                    path,
                    gridColor.copy(alpha = gridColor.alpha * 0.72f),
                    style = Stroke(width = 1f),
                )
            }
        }
    }
}
