package com.byd.wjx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.audio.ExteriorChannelMode
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.vehicle.CarProfile

@Composable
internal fun ExteriorSoundChannelSection(
    carProfile: CarProfile?,
    onChannelChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context) { ExteriorHailingSettings(context) }
    var channelMode by remember { mutableStateOf(settings.channelMode) }
    SectionHeader(text = "车外音效")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = AccentGreen)
            Column(modifier = Modifier.weight(1f)) {
                Text("车外通道", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    carProfile?.let(settings::channelModeLabel) ?: "自动识别中",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelModeChip("自动", ExteriorChannelMode.AUTO, channelMode) { selected ->
                    channelMode = selected
                    settings.channelMode = selected
                    onChannelChanged("exterior_channel_auto")
                }
                ChannelModeChip("A", ExteriorChannelMode.CHANNEL_0, channelMode) { selected ->
                    channelMode = selected
                    settings.channelMode = selected
                    onChannelChanged("exterior_channel_a")
                }
                ChannelModeChip("B", ExteriorChannelMode.CHANNEL_1, channelMode) { selected ->
                    channelMode = selected
                    settings.channelMode = selected
                    onChannelChanged("exterior_channel_b")
                }
            }
        }
    }
}

@Composable
private fun ChannelModeChip(
    label: String,
    mode: ExteriorChannelMode,
    selectedMode: ExteriorChannelMode,
    onSelected: (ExteriorChannelMode) -> Unit,
) {
    UnitChip(label, selectedMode == mode) { onSelected(mode) }
}
