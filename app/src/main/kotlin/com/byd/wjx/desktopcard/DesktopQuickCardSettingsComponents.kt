package com.byd.wjx.desktopcard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun DesktopQuickCardSettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun DesktopQuickCardToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = bydSwitchColors())
    }
}

@Composable
internal fun DesktopQuickCardItemSection(
    title: String,
    subtitle: String,
    count: Int,
    onAdd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DesktopQuickCardSettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$title（$count）", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("添加")
            }
        }
        if (count == 0) {
            Text("尚未添加$title", color = TextMuted, fontSize = 13.sp)
        } else {
            content()
        }
    }
}

@Composable
internal fun DesktopQuickCardOrderedRow(
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBorder.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            color = AccentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onUp, enabled = index > 0) {
            Icon(
                Icons.Outlined.KeyboardArrowUp,
                contentDescription = "上移",
                tint = if (index > 0) AccentBlue else TextMuted,
            )
        }
        IconButton(onClick = onDown, enabled = index < count - 1) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "下移",
                tint = if (index < count - 1) AccentBlue else TextMuted,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = AccentOrange)
        }
    }
}
