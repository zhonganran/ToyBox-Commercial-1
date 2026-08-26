package com.byd.wjx.ui.pro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.dashboard.DashboardViewModel

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
        ProDynamicBackground(scene = selectedRoute, animated = animateBackground)
        Column(modifier = Modifier.fillMaxSize()) {
            ProTopStatusBar(state = vehicleState)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val baseDensity = LocalDensity.current
                val contentScale = (maxHeight.value / DESIGN_HEIGHT_DP).coerceIn(0.74f, 1f)
                val compactDensity = remember(
                    baseDensity.density,
                    baseDensity.fontScale,
                    contentScale,
                ) {
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

private const val DESIGN_HEIGHT_DP = 860f
