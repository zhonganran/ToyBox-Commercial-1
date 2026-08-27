package com.byd.wjx.ui.map

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun MapPane(
    state: MapUiState,
    currentLocation: Location?,
    fitRequest: Int,
    locationRequest: Int,
    onFitRoutes: () -> Unit,
    onLocate: () -> Unit,
    onOpenDashcamAtTime: (DashcamPlaybackTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.68f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OsmdroidMapView(
                state = state,
                currentLocation = currentLocation,
                fitRequest = fitRequest,
                locationRequest = locationRequest,
                onOpenDashcamAtTime = onOpenDashcamAtTime,
            )
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardSurface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                }

                state.tripRoutes.isEmpty() -> EmptyMapHint(Modifier.align(Alignment.Center))
            }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapIconButton(
                    icon = Icons.Outlined.MyLocation,
                    contentDescription = "定位当前位置",
                    enabled = currentLocation != null,
                    onClick = onLocate,
                )
                MapIconButton(
                    icon = Icons.Outlined.CenterFocusStrong,
                    contentDescription = "显示全部轨迹",
                    enabled = state.tripRoutes.isNotEmpty(),
                    onClick = onFitRoutes,
                )
            }
        }
    }
}

@Composable
private fun MapIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(CardSurface.copy(alpha = 0.92f)),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) TextPrimary else TextMuted,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun EmptyMapHint(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(CardSurface.copy(alpha = 0.90f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "当前没有可显示的 GPS 轨迹",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("系统历史行程仍会显示在右侧记录中", color = TextSecondary, fontSize = 11.sp)
    }
}
