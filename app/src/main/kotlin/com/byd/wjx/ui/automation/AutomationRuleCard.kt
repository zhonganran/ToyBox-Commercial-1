package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SceneRuleCard(
    rule: RuleEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val triggers = remember(rule.triggers) { TriggerDef.listFromJson(rule.triggers) }
    val actions = remember(rule.actions) { ActionDef.listFromJson(rule.actions) }
    val exitActions = remember(rule.exitActions) { ActionDef.listFromJson(rule.exitActions) }
    val trigger = triggers.firstOrNull()
    val enterAction = actions.firstOrNull()
    val action = enterAction ?: exitActions.firstOrNull()
    val exitOnly = enterAction == null && exitActions.isNotEmpty()
    val unplugEvent = isChargeGunUnplugEvent(trigger, exitOnly)
    val accentColors = listOf(SceneMint, SceneBlue, SceneAmber, SceneCyan, SceneViolet)
    val accent = accentColors[((rule.id % accentColors.size) + accentColors.size).toInt() % accentColors.size]
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.88f))
            .border(1.dp, accent.copy(alpha = 0.58f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AutomationNodeIcon(
                automationTriggerIcon(trigger),
                listOf(accent, accent.copy(alpha = 0.68f)),
                trigger?.value?.take(1) ?: "",
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    automationTriggerSummary(trigger, exitOnly),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                )
                Text(
                    if (exitOnly && !unplugEvent && action != null) {
                        "否则  ${automationActionSummary(action)}"
                    } else {
                        "→  ${automationActionSummary(action)}"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = TextPrimary)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("复制") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rule.cooldownSeconds > 0) {
                SceneInfoPill("冷却 ${rule.cooldownSeconds} 秒", Icons.Outlined.Timer)
            }
            if (rule.repeatIntervalSeconds > 0) {
                SceneInfoPill("每 ${rule.repeatIntervalSeconds} 秒重复", Icons.Outlined.Sync)
            }
            if (rule.requirePark) SceneInfoPill("需要 P 挡", Icons.Outlined.Lock)
            if (rule.fireOncePerTrip) SceneInfoPill("每程一次", Icons.Outlined.Sync)
            if (exitActions.isNotEmpty()) {
                SceneInfoPill("退出时 ${exitActions.size} 个动作", Icons.Outlined.History)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TextPrimary.copy(alpha = 0.10f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rule.lastTriggeredAt?.let {
                    "最后触发 ${formatRelativeTime(it, LocalContext.current)}"
                } ?: "尚未触发",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (rule.enabled) Color(0xFF1CCB70) else TextMuted),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (rule.enabled) "运行中" else "已停用",
                color = if (rule.enabled) Color(0xFF11AE5B) else TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                colors = bydSwitchColors(),
            )
        }
    }
}

@Composable
private fun SceneInfoPill(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextPrimary.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
        Text(text, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
    }
}
