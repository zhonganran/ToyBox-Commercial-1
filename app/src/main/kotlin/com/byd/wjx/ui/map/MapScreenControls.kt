package com.byd.wjx.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun PeriodSelector(
    period: MapPeriod,
    onPeriodSelected: (MapPeriod) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PeriodChip("日", period == MapPeriod.DAY) { onPeriodSelected(MapPeriod.DAY) }
        PeriodChip("周", period == MapPeriod.WEEK) { onPeriodSelected(MapPeriod.WEEK) }
        PeriodChip("月", period == MapPeriod.MONTH) { onPeriodSelected(MapPeriod.MONTH) }
        PeriodChip("年", period == MapPeriod.YEAR) { onPeriodSelected(MapPeriod.YEAR) }
        PeriodChip("全部", period == MapPeriod.ALL) { onPeriodSelected(MapPeriod.ALL) }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(40.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = CardBorder.copy(alpha = 0.55f),
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}
