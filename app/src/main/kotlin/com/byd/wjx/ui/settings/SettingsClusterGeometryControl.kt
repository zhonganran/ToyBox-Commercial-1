package com.byd.wjx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.byd.wjx.cluster.MAX_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_PROJECTION_PCT
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun ClusterSizeSlider(
    label: String,
    pct: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit,
    min: Int = MIN_PROJECTION_PCT,
    max: Int = MAX_PROJECTION_PCT,
    description: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
            Text(
                "$pct%",
                color = if (enabled) AccentGreen else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (description != null) {
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Slider(
            value = pct.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / 2 - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
            ),
        )
    }
}
