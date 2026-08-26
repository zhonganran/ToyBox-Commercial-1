package com.byd.wjx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutBinding
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun KeyShortcutSection(
    bindings: List<KeyShortcutBinding>,
    activationAllowed: Boolean,
    onAdd: () -> Unit,
    onEdit: (KeyShortcutBinding) -> Unit,
    onEnabledChanged: (KeyShortcutBinding, Boolean) -> Unit,
    onDelete: (KeyShortcutBinding) -> Unit,
) {
    SectionHeader(text = "按键快捷操作")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "方向盘快捷键",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (activationAllowed) "${bindings.size} 条规则" else "试用已结束，激活后可用",
                        color = if (activationAllowed) TextSecondary else SocRed,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("添加", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (bindings.isEmpty()) {
                Text("暂无快捷操作", color = TextSecondary, fontSize = 12.sp)
            } else {
                bindings.forEach { binding ->
                    KeyShortcutRow(
                        binding = binding,
                        onClick = { onEdit(binding) },
                        onEnabledChanged = { onEnabledChanged(binding, it) },
                        onDelete = { onDelete(binding) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyShortcutRow(
    binding: KeyShortcutBinding,
    onClick: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val keyLabel = steeringButtonLabel(binding.keyCode)
    Row(
        modifier = Modifier.fillMaxWidth().background(CardSurface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when (binding.actionType) {
                    KeyShortcutActionType.PlaySound -> "按键播放音效"
                    KeyShortcutActionType.RealtimeHailing -> "按键实时喊话"
                },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(keyLabel)
                    append(" · ")
                    append(binding.pressType.label)
                    if (binding.actionType == KeyShortcutActionType.PlaySound) {
                        append(" · ")
                        append(binding.soundName.ifBlank { "未选择音效" })
                    }
                },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = binding.enabled,
            onCheckedChange = onEnabledChanged,
            colors = bydSwitchColors(),
        )
        TextButton(onClick = onDelete) {
            Text("删除", color = Color(0xFFE85D5D), fontSize = 12.sp)
        }
    }
}
