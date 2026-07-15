package com.byd.wjx.ui.automation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AutomationSceneHeader(
    filter: RuleFilter,
    onFilterChanged: (RuleFilter) -> Unit,
    onOpenJournal: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("智能联动", color = TextPrimary, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = SceneBlue, modifier = Modifier.size(31.dp))
            }
            Text("让车辆状态自动触发你需要的动作", color = TextPrimary.copy(alpha = 0.78f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AutoChip("全部", filter == RuleFilter.ALL) { onFilterChanged(RuleFilter.ALL) }
            AutoChip("启用", filter == RuleFilter.ENABLED) { onFilterChanged(RuleFilter.ENABLED) }
            AutoChip("停用", filter == RuleFilter.DISABLED) { onFilterChanged(RuleFilter.DISABLED) }
            IconButton(
                onClick = onOpenJournal,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardSurface.copy(alpha = 0.68f)),
            ) {
                Icon(Icons.Outlined.History, contentDescription = "执行记录", tint = TextPrimary)
            }
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SceneBlue, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                modifier = Modifier.height(58.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(8.dp))
                Text("新建联动", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun FeaturedAutomationRule(
    rule: RuleEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trigger = remember(rule.triggers) { TriggerDef.listFromJson(rule.triggers).firstOrNull() }
    val enterAction = remember(rule.actions) { ActionDef.listFromJson(rule.actions).firstOrNull() }
    val exitAction = remember(rule.exitActions) { ActionDef.listFromJson(rule.exitActions).firstOrNull() }
    val action = enterAction ?: exitAction
    val shape = RoundedCornerShape(46.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.88f))
            .border(2.dp, SceneCoral.copy(alpha = 0.58f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AutomationNodeIcon(
            icon = triggerIcon(trigger),
            colors = listOf(ScenePink, SceneCoral),
            label = trigger?.value?.take(2)?.uppercase() ?: "IF",
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(triggerSummary(trigger), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(rule.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
        Text("→", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Light)
        AutomationNodeIcon(
            icon = actionIcon(action),
            colors = listOf(SceneCyan, SceneBlue),
            label = "",
        )
        Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (enterAction == null && exitAction != null) "退出时：${actionSummary(action)}" else actionSummary(action),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (rule.fireOncePerTrip) "单次" else "循环", color = TextSecondary, fontSize = 10.sp)
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (rule.enabled) Color(0xFF1DCB72) else TextMuted))
                Text(if (rule.enabled) "运行中" else "已停用", color = if (rule.enabled) Color(0xFF13AE5E) else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutomationNodeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: List<Color>,
    label: String,
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        if (label.isNotBlank()) {
            Text(
                label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
            )
        }
    }
}

@Composable
internal fun AutomationConnector(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * 0.65f
            val path = Path().apply {
                moveTo(size.width * 0.08f, y)
                cubicTo(size.width * 0.24f, y, size.width * 0.33f, size.height * 0.12f, size.width * 0.50f, size.height * 0.12f)
                cubicTo(size.width * 0.67f, size.height * 0.12f, size.width * 0.76f, y, size.width * 0.92f, y)
            }
            drawPath(path, Color.White.copy(alpha = 0.78f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            repeat(9) { index ->
                val x = size.width * (0.10f + index * 0.10f)
                drawCircle(
                    color = listOf(SceneMint, SceneBlue, SceneViolet, ScenePink)[index % 4],
                    radius = 4.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
        Text(
            "300ms",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(SceneBlue.copy(alpha = 0.78f))
                .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

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
    val accent = listOf(SceneMint, SceneBlue, SceneAmber, SceneCyan, SceneViolet)[((rule.id % 5) + 5).toInt() % 5]
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
            AutomationNodeIcon(triggerIcon(trigger), listOf(accent, accent.copy(alpha = 0.68f)), trigger?.value?.take(1) ?: "")
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(triggerSummary(trigger), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text(
                    if (enterAction == null && action != null) "否则  ${actionSummary(action)}" else "→  ${actionSummary(action)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = TextPrimary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("复制") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rule.cooldownSeconds > 0) SceneInfoPill("冷却 ${rule.cooldownSeconds} 秒", Icons.Outlined.Timer)
            if (rule.repeatIntervalSeconds > 0) SceneInfoPill("每 ${rule.repeatIntervalSeconds} 秒重复", Icons.Outlined.Sync)
            if (rule.requirePark) SceneInfoPill("需要 P 挡", Icons.Outlined.Lock)
            if (rule.fireOncePerTrip) SceneInfoPill("每程一次", Icons.Outlined.Sync)
            if (exitActions.isNotEmpty()) SceneInfoPill("退出时 ${exitActions.size} 个动作", Icons.Outlined.History)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TextPrimary.copy(alpha = 0.10f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rule.lastTriggeredAt?.let { "最后触发 ${formatRelativeTime(it, LocalContext.current)}" } ?: "尚未触发",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (rule.enabled) Color(0xFF1CCB70) else TextMuted))
            Spacer(Modifier.width(5.dp))
            Text(if (rule.enabled) "运行中" else "已停用", color = if (rule.enabled) Color(0xFF11AE5B) else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() }, colors = bydSwitchColors())
        }
    }
}

@Composable
private fun SceneInfoPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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

@Composable
internal fun AutomationSummaryStrip(
    total: Int,
    enabled: Int,
    logs: List<RuleLogEntity>,
    modifier: Modifier = Modifier,
) {
    val todayCutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
    val todayCount = logs.count { it.triggeredAt >= todayCutoff }
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.82f))
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutomationStat(Icons.Outlined.CheckCircle, "已启用 $enabled 条", "共 $total 条规则", Modifier.weight(1f))
        SummaryDivider()
        AutomationStat(Icons.Outlined.Bolt, "今日触发 $todayCount 次", "实时记录", Modifier.weight(1f))
        SummaryDivider()
        AutomationStat(Icons.Outlined.Shield, "监听正常", "服务在线", Modifier.weight(1f))
        SummaryDivider()
        AutomationStat(Icons.Outlined.Timer, "动作刷新 300ms", "实时响应", Modifier.weight(1f))
    }
}

@Composable
private fun AutomationStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).height(42.dp).background(TextPrimary.copy(alpha = 0.12f)))
}

@Composable
internal fun RecentAutomationLogs(
    logs: List<RuleLogEntity>,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(CardSurface.copy(alpha = 0.72f))
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onOpenJournal)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("最近触发记录", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("查看全部  ›", color = TextSecondary, fontSize = 11.sp)
        }
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("暂无触发记录", color = TextSecondary, fontSize = 12.sp)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                logs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardSurfaceElevated.copy(alpha = 0.82f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.triggeredAt)),
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (log.success) SceneMint else Color(0xFFE85D65)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (log.success) Icons.Outlined.CheckCircle else Icons.Outlined.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        Text(log.ruleName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(if (log.success) "执行成功" else "执行失败", color = if (log.success) Color(0xFF10AE5B) else Color(0xFFDE4F57), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun triggerIcon(trigger: TriggerDef?): androidx.compose.ui.graphics.vector.ImageVector = when (trigger?.kind) {
    "place_enter", "place_exit" -> Icons.Outlined.Place
    "button_press" -> Icons.Outlined.Apps
    "network_available" -> Icons.Outlined.Wifi
    "interval" -> Icons.Outlined.Sync
    "board_vehicle", "service_start" -> Icons.Outlined.Bolt
    else -> when (trigger?.param) {
        "Gear" -> Icons.Outlined.Settings
        "Speed" -> Icons.Outlined.Timer
        "ChargingStatus" -> Icons.Outlined.Bolt
        else -> Icons.Outlined.Link
    }
}

private fun actionIcon(action: ActionDef?): androidx.compose.ui.graphics.vector.ImageVector = when (action?.kind) {
    "outside_sound" -> Icons.AutoMirrored.Outlined.VolumeUp
    "notification_silent", "notification_sound" -> Icons.Outlined.Notifications
    "app_launch" -> Icons.Outlined.Apps
    "navigate" -> Icons.Outlined.Navigation
    "auto_door" -> Icons.Outlined.Lock
    "play_music", "play_video" -> Icons.Outlined.PlayArrow
    "sentry_mode" -> Icons.Outlined.Shield
    else -> Icons.Outlined.Bolt
}

private fun triggerSummary(trigger: TriggerDef?): String = when {
    trigger == null -> "等待设置触发条件"
    trigger.kind == "place_enter" -> "进入 ${trigger.placeName ?: "地点"}"
    trigger.kind == "place_exit" -> "离开 ${trigger.placeName ?: "地点"}"
    trigger.kind == "interval" -> "每 ${trigger.value} 秒"
    trigger.kind == "board_vehicle" -> "上车开门"
    trigger.displayName.isNotBlank() -> trigger.displayName
    else -> "${trigger.chineseName} ${trigger.operator} ${trigger.value}"
}

private fun actionSummary(action: ActionDef?): String =
    action?.displayName?.takeIf { it.isNotBlank() } ?: "等待设置执行动作"

@Composable
internal fun LinkageEmptyState(
    hasRules: Boolean,
    onCreate: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.88f)),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasRules) "当前筛选下没有规则" else "还没有智能联动规则",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasRules) "切换筛选条件，或创建一条新的智能联动。" else "创建规则后，车辆状态变化时可以自动执行动作。",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.automation_create_button), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
