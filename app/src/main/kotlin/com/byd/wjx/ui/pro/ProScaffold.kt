package com.byd.wjx.ui.pro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.dashboard.DashboardViewModel
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.GlassStroke
import com.byd.wjx.ui.theme.GlassWhite
import com.byd.wjx.ui.theme.LocalSuperToyBoxDarkTheme
import com.byd.wjx.ui.theme.LocalAppWallpaper
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCoral
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class ProNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun ProAppScaffold(
    navItems: List<ProNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    showNavigation: Boolean = true,
    animateBackground: Boolean = false,
    content: @Composable PaddingValues.() -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val vehicleState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        ProDynamicBackground(
            scene = selectedRoute,
            animated = animateBackground,
        )
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            ProTopStatusBar(state = vehicleState)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val baseDensity = LocalDensity.current
                // Keep the information density suitable for 720p car displays.
                val contentScale = (maxHeight.value / 860f).coerceIn(0.74f, 1f)
                val compactDensity = remember(baseDensity.density, baseDensity.fontScale, contentScale) {
                    Density(
                        density = baseDensity.density * contentScale,
                        fontScale = baseDensity.fontScale,
                    )
                }
                CompositionLocalProvider(LocalDensity provides compactDensity) {
                    PaddingValues(0.dp).content()
                }
            }
            if (showNavigation) {
                ProBottomNavigation(
                    items = navItems,
                    selectedRoute = selectedRoute,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun ProTopStatusBar(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    var clock by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = formatClock()
            delay(30_000L)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(GlassWhite.copy(alpha = 0.72f))
            .border(0.5.dp, CardBorder.copy(alpha = 0.72f), RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp),
    ) {
        val compact = maxWidth < 980.dp
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ViewInAr,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "超级玩具箱",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            StatusDivider()
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (state.vehicleDataConnected) Color(0xFF15CB55) else Color(0xFFFF6B6B)),
            )
            Text(
                text = if (state.vehicleDataConnected) "车辆在线" else "车辆离线",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
            Text(
                text = "${state.exteriorTemp?.let { "$it°C" } ?: "--°C"}  外",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            StatusDivider()
            Icon(Icons.Outlined.Air, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
            Text(
                text = "${state.insideTemp?.let { "$it°C" } ?: "--°C"}  内",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            StatusDivider()
            Text(clock, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!compact) {
                Text(
                    text = freshnessText(state.lastVehicleDataAt),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatusDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(CardBorder.copy(alpha = 0.72f)),
    )
}

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
    val routeAccent = navAccent(scene)

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

            val gridColor = Color.White.copy(
                alpha = if (darkTheme) 0.025f else 0.04f,
            )
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
                drawPath(path, gridColor.copy(alpha = gridColor.alpha * 0.72f), style = Stroke(width = 1f))
            }
        }
    }
}

@Composable
private fun ProBottomNavigation(
    items: List<ProNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(GlassWhite.copy(alpha = 0.78f))
            .padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val selectedTint = navAccent(item.route)
            val tint by animateColorAsState(
                targetValue = if (selected) selectedTint else TextPrimary,
                animationSpec = tween(220),
                label = "nav-${item.route}",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onNavigate(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(if (selected) selectedTint else Color.Transparent),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun navAccent(route: String): Color = when (route) {
    "sound" -> SceneViolet
    "lyrics" -> SceneAmber
    "automation" -> SceneBlue
    "settings" -> SceneCoral
    else -> SceneAmber
}

@Composable
fun ProPageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (eyebrow.isNotBlank()) {
                Text(
                    text = eyebrow,
                    color = SceneBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ProGlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassWhite.copy(alpha = 0.80f), shape)
            .border(1.dp, GlassStroke.copy(alpha = 0.76f), shape),
    ) {
        content()
    }
}

@Composable
fun ProSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun ProStatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) AccentGreen else TextMuted
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = if (active) 0.14f else 0.10f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(text, color = if (active) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun freshnessText(timestamp: Long?): String {
    val age = timestamp?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: return "等待更新"
    return when {
        age < 10_000L -> "刚刚更新"
        age < 60_000L -> "${age / 1_000L} 秒前"
        age < 3_600_000L -> "${age / 60_000L} 分钟前"
        else -> "数据待刷新"
    }
}

private fun formatClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
