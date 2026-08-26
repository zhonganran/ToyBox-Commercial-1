package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun ReorderArrows(onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
    IconButton(
        onClick = { onMoveUp?.invoke() },
        enabled = onMoveUp != null,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            Icons.Outlined.KeyboardArrowUp,
            contentDescription = "上移",
            tint = TextSecondary.copy(alpha = if (onMoveUp != null) 1f else 0.25f),
            modifier = Modifier.size(18.dp),
        )
    }
    IconButton(
        onClick = { onMoveDown?.invoke() },
        enabled = onMoveDown != null,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = "下移",
            tint = TextSecondary.copy(alpha = if (onMoveDown != null) 1f else 0.25f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun AutoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(44.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = AccentContent,
            containerColor = CardSurfaceElevated.copy(alpha = 0.86f),
            labelColor = TextSecondary,
        ),
        shape = RoundedCornerShape(999.dp),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = CardBorder.copy(alpha = 0.58f),
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
internal fun AutomationLogicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) AccentGreen else CardSurface, RoundedCornerShape(10.dp))
            .border(if (selected) 0.dp else 1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) AccentContent else TextSecondary,
        )
    }
}

@Composable
internal fun AutomationEditorSectionHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AccentGreen,
        modifier = Modifier
            .background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
internal fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
