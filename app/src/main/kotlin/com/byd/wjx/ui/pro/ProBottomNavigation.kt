package com.byd.wjx.ui.pro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.GlassWhite
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCoral
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary

@Composable
internal fun ProBottomNavigation(
    items: List<ProNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp)
            .background(GlassWhite.copy(alpha = 0.78f))
            .padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val selectedTint = proNavigationAccent(item.route)
            val tint by animateColorAsState(
                targetValue = if (selected) selectedTint else TextPrimary,
                animationSpec = tween(220),
                label = "nav-${item.route}",
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onNavigate(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Box(
                    Modifier.width(72.dp).height(3.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(if (selected) selectedTint else Color.Transparent),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.label,
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

internal fun proNavigationAccent(route: String): Color = when (route) {
    "sound" -> SceneViolet
    "lyrics" -> SceneAmber
    "automation" -> SceneBlue
    "settings" -> SceneCoral
    else -> SceneAmber
}
