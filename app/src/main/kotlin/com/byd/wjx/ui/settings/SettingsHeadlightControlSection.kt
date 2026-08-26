package com.byd.wjx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.vehicle.HeadlightAvailability
import com.byd.wjx.data.vehicle.HeadlightMode
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun HeadlightControlSection(
    availability: HeadlightAvailability,
    mode: HeadlightMode?,
    loading: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onModeSelected: (HeadlightMode) -> Unit,
) {
    val controlsEnabled = availability == HeadlightAvailability.SUPPORTED && !loading
    SectionHeader(text = "灯光控制")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.LightMode, contentDescription = null, tint = SceneAmber)
                Column(modifier = Modifier.weight(1f)) {
                    Text("前照灯模式", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        message,
                        color = if (availability == HeadlightAvailability.UNAVAILABLE) SocRed else TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AccentGreen,
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新灯光状态", tint = AccentGreen)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeadlightMode.entries.forEach { option ->
                    Box(modifier = Modifier.weight(1f)) {
                        UnitChip(
                            label = option.label,
                            selected = mode == option,
                            enabled = controlsEnabled,
                            onClick = { onModeSelected(option) },
                        )
                    }
                }
            }
        }
    }
}
