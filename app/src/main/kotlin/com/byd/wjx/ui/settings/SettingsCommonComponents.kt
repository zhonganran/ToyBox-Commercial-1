package com.byd.wjx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        color = AccentGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
internal fun UnitChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurfaceElevated,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = enabled,
            selected = selected,
        ),
    )
}
