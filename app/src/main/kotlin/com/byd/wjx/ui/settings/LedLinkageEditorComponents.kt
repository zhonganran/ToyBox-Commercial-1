package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

internal data class LinkageRuleEditorState<T>(
    val rule: T,
    val isNew: Boolean,
)

@Composable
internal fun LedLinkageRuleRow(
    title: String,
    detail: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移规则", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移规则", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "编辑规则",
                tint = AccentBlue,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "删除规则",
                tint = AccentOrange,
                modifier = Modifier.size(19.dp),
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = bydSwitchColors(),
            modifier = Modifier.size(width = 46.dp, height = 30.dp),
        )
    }
}

@Composable
internal fun <T> LedLinkageChoiceField(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    showLabelAbove: Boolean = false,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (showLabelAbove) {
            Text(label, color = TextSecondary, fontSize = 11.sp)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    if (!showLabelAbove) {
                        Text(label, color = TextMuted, fontSize = 10.sp)
                    }
                    Text(
                        selectedTitle,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = "展开$label",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = { Text(title, color = TextPrimary, fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}
