package com.byd.wjx.ui.automation

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.byd.wjx.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.data.automation.ScheduleSpec
import com.byd.wjx.data.automation.minuteToHHmm
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomationScreen(
    viewModel: AutomationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filtered = remember(state.rules, state.filter) {
        when (state.filter) {
            RuleFilter.ALL -> state.rules
            RuleFilter.ENABLED -> state.rules.filter { it.enabled }
            RuleFilter.DISABLED -> state.rules.filter { !it.enabled }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 22.dp),
    ) {
        AutomationSceneHeader(
            filter = state.filter,
            onFilterChanged = viewModel::setFilter,
            onOpenJournal = { viewModel.showJournal() },
            onCreate = { viewModel.openNewRule() },
        )
        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            LinkageEmptyState(
                hasRules = state.rules.isNotEmpty(),
                onCreate = { viewModel.openNewRule() },
            )
        } else {
            val featuredRule = filtered.firstOrNull { it.enabled } ?: filtered.first()
            FeaturedAutomationRule(
                rule = featuredRule,
                onClick = { viewModel.openEditRule(featuredRule) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.42f)
                    .height(116.dp),
            )
            AutomationConnector(modifier = Modifier.fillMaxWidth().height(54.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(198.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(filtered, key = { it.id }) { rule ->
                    SceneRuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleEnabled(rule) },
                        onClick = { viewModel.openEditRule(rule) },
                        onEdit = { viewModel.openEditRule(rule) },
                        onDuplicate = { viewModel.duplicateRule(rule) },
                        onDelete = { viewModel.requestDelete(rule.id) },
                        modifier = Modifier.width(354.dp).fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            AutomationSummaryStrip(
                total = state.rules.size,
                enabled = state.rules.count { it.enabled },
                logs = state.logs,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            RecentAutomationLogs(
                logs = state.logs.take(3),
                onOpenJournal = { viewModel.showJournal() },
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
    }

    // Editor dialog
    if (state.showEditor) {
        EditorDialog(
            editing = state.editing,
            places = state.places,
            editorError = state.editorError,
            onUpdate = { viewModel.updateEditing(it) },
            onSave = { viewModel.saveRule() },
            onTestAction = { viewModel.executeNow(it) },
            onDismiss = { viewModel.closeEditor() }
        )
    }

    // Journal dialog
    if (state.showJournal) {
        JournalDialog(
            logs = state.logs,
            onDismiss = { viewModel.hideJournal() }
        )
    }

    // Delete confirmation
    state.showDeleteConfirm?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.automation_delete_confirm_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.automation_delete_confirm_text), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.automation_delete_button), color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }
}

@Composable
private fun AutomationSceneHeader(
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
                    .background(Color.White.copy(alpha = 0.42f)),
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
private fun FeaturedAutomationRule(
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
            .background(Color.White.copy(alpha = 0.78f))
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
private fun AutomationConnector(modifier: Modifier = Modifier) {
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
private fun SceneRuleCard(
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
            .background(Color.White.copy(alpha = 0.78f))
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
private fun AutomationSummaryStrip(
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
            .background(Color.White.copy(alpha = 0.68f))
            .border(1.dp, Color.White.copy(alpha = 0.84f), shape)
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
private fun RecentAutomationLogs(
    logs: List<RuleLogEntity>,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.72f), shape)
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
                            .background(Color.White.copy(alpha = 0.70f))
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
private fun LinkageToolbar(
    total: Int,
    enabled: Int,
    filtered: Int,
    filter: RuleFilter,
    compact: Boolean,
    onFilterChanged: (RuleFilter) -> Unit,
    onOpenJournal: () -> Unit,
    onCreate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (compact) {
                LinkageToolbarTitle()
                LinkageToolbarActions(
                    onOpenJournal = onOpenJournal,
                    onCreate = onCreate,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinkageToolbarTitle(modifier = Modifier.weight(1f))
                    LinkageToolbarActions(onOpenJournal = onOpenJournal, onCreate = onCreate)
                }
            }

            if (compact) {
                LinkageMetricsRow(total, enabled, filtered, Modifier.fillMaxWidth())
                LinkageFiltersRow(filter, onFilterChanged, Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinkageMetricsRow(total, enabled, filtered, Modifier.weight(3f))
                    LinkageFiltersRow(filter, onFilterChanged, Modifier.weight(2.1f))
                }
            }
        }
    }
}

@Composable
private fun LinkageToolbarTitle(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("智能联动规则", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text(
            "按条件自动执行动作，车外音效、通知、应用启动等都从这里管理",
            fontSize = 13.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun LinkageToolbarActions(
    onOpenJournal: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onOpenJournal,
            colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated, contentColor = TextPrimary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            modifier = Modifier
                .height(48.dp)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        ) {
            Text(stringResource(R.string.automation_journal_button), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.automation_create_button), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LinkageMetricsRow(
    total: Int,
    enabled: Int,
    filtered: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkageMetricTile("全部", "$total", AccentBlue, Modifier.weight(1f))
        LinkageMetricTile("启用", "$enabled", AccentGreen, Modifier.weight(1f))
        LinkageMetricTile("当前", "$filtered", AccentOrange, Modifier.weight(1f))
    }
}

@Composable
private fun LinkageFiltersRow(
    filter: RuleFilter,
    onFilterChanged: (RuleFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutoChip(stringResource(R.string.automation_filter_all), filter == RuleFilter.ALL) {
            onFilterChanged(RuleFilter.ALL)
        }
        AutoChip(stringResource(R.string.automation_filter_active), filter == RuleFilter.ENABLED) {
            onFilterChanged(RuleFilter.ENABLED)
        }
        AutoChip(stringResource(R.string.automation_filter_disabled), filter == RuleFilter.DISABLED) {
            onFilterChanged(RuleFilter.DISABLED)
        }
    }
}

@Composable
private fun LinkageMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CardSurfaceElevated.copy(alpha = 0.86f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LinkageEmptyState(
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

// --- Rule Card ---

@Composable
private fun RuleCard(
    rule: RuleEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val triggers = remember(rule.triggers) { TriggerDef.listFromJson(rule.triggers) }
    val actions = remember(rule.actions) { ActionDef.listFromJson(rule.actions) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) CardSurfaceElevated.copy(alpha = 0.96f) else CardSurface.copy(alpha = 0.68f)
        ),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (rule.enabled) AccentGreen.copy(alpha = 0.24f) else CardBorder.copy(alpha = 0.78f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp, 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: dot + name + toggle + menu
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (rule.enabled) AccentGreen else Color.Transparent)
                        .border(1.5.dp, if (rule.enabled) AccentGreen else TextSecondary, CircleShape)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        rule.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        "${triggers.size} 个条件 · ${actions.size} 个动作",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
                RuleStateBadge(enabled = rule.enabled)
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggle() },
                    colors = bydSwitchColors(),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.MoreVert, "menu", tint = TextMuted, modifier = Modifier.size(21.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_menu_edit)) },
                            onClick = { menuExpanded = false; onEdit() },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_menu_duplicate)) },
                            onClick = { menuExpanded = false; onDuplicate() },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.automation_menu_delete), color = Color(0xFFEF4444)) },
                            onClick = { menuExpanded = false; onDelete() },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            // Trigger → Action summary
            val logicAndLabel = stringResource(R.string.automation_rule_logic_and)
            val logicOrLabel = stringResource(R.string.automation_rule_logic_or)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(14.dp))
                    .border(1.dp, CardBorder.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        triggers.forEachIndexed { i, t ->
                            if (i > 0) {
                                withStyle(SpanStyle(color = TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)) {
                                    append(if (rule.triggerLogic == "AND") logicAndLabel else logicOrLabel)
                                }
                            }
                            if (t.kind == "time_range" || t.kind == "button_press") {
                                // Non-param triggers carry their readable label in displayName.
                                withStyle(SpanStyle(color = AccentBlue)) { append(t.displayName) }
                            } else {
                                withStyle(SpanStyle(color = AccentBlue)) { append(t.displayName.substringBefore(" ")) }
                                append(" ")
                                withStyle(SpanStyle(color = AccentOrange)) { append(t.operator) }
                                append(" ")
                                withStyle(SpanStyle(color = AccentGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                                    append(t.value)
                                }
                            }
                        }
                        withStyle(SpanStyle(color = TextMuted)) { append("  →  ") }
                        withStyle(SpanStyle(color = AccentTeal)) {
                            append(actions.joinToString(", ") { it.displayName })
                        }
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            val triggeredLabel = stringResource(R.string.automation_rule_triggered_count, rule.triggerCount)
            val localContext = LocalContext.current
            val lastLabel = rule.lastTriggeredAt?.let { ts ->
                " · " + stringResource(R.string.automation_rule_last_trigger, formatRelativeTime(ts, localContext))
            } ?: ""
            val cooldownLabel = " · " + stringResource(R.string.automation_rule_cooldown, rule.cooldownSeconds)
            Text(triggeredLabel + lastLabel + cooldownLabel, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun RuleStateBadge(enabled: Boolean) {
    val color = if (enabled) AccentGreen else TextMuted
    Text(
        if (enabled) "启用" else "停用",
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

// --- Editor Dialog ---

@Composable
private fun EditorDialog(
    editing: EditingRule,
    places: List<PlaceEntity>,
    editorError: String?,
    onUpdate: (EditingRule.() -> EditingRule) -> Unit,
    onSave: () -> Unit,
    onTestAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var repeatIntervalInput by rememberSaveable(editing.id, editing.isNew) {
        mutableStateOf(editing.repeatIntervalSeconds.toString())
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.88f)
                .background(CardSurface.copy(alpha = 0.98f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(24.dp))
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editing.isNew) stringResource(R.string.automation_editor_new_rule_title) else editing.name,
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, "close", tint = TextMuted)
                }
            }
            HorizontalDivider(color = CardBorder)

            // Two columns
            Row(modifier = Modifier.weight(1f)) {
                // Left: Name + Triggers
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp, 12.dp)
                ) {
                    OutlinedTextField(
                        value = editing.name,
                        onValueChange = { v -> onUpdate { copy(name = v) } },
                        placeholder = { Text(stringResource(R.string.automation_rule_name_placeholder), color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentGreen
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(14.dp))
                    SectionHeader(stringResource(R.string.automation_section_when))

                    // AND/OR toggle
                    Row {
                        LogicChip(stringResource(R.string.automation_logic_and), editing.triggerLogic == "AND") {
                            onUpdate { copy(triggerLogic = "AND") }
                        }
                        LogicChip(stringResource(R.string.automation_logic_or), editing.triggerLogic == "OR") {
                            onUpdate { copy(triggerLogic = "OR") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    editing.triggers.forEachIndexed { idx, trigger ->
                        TriggerRow(
                            index = idx,
                            trigger = trigger,
                            places = places,
                            onUpdate = { newTrigger ->
                                onUpdate {
                                    copy(triggers = triggers.toMutableList().apply { set(idx, newTrigger) })
                                }
                            },
                            onMoveUp = if (idx > 0) {
                                { onUpdate { copy(triggers = triggers.moveItem(idx, up = true)) } }
                            } else null,
                            onMoveDown = if (idx < editing.triggers.lastIndex) {
                                { onUpdate { copy(triggers = triggers.moveItem(idx, up = false)) } }
                            } else null,
                            onDelete = {
                                onUpdate {
                                    copy(triggers = triggers.toMutableList().apply { removeAt(idx) })
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (editing.triggers.size < 5) {
                        AddTriggerButton(
                            places = places,
                            onAddParam = {
                                val p = TRIGGER_PARAMS.first()
                                onUpdate {
                                    copy(triggers = triggers + TriggerDef(p.param, p.chineseName, ">", "0", p.localizedName(context)))
                                }
                            },
                            onAddPlace = { place ->
                                onUpdate { copy(triggers = triggers + newPlaceTrigger(place, context)) }
                            },
                            onAddTimeOfDay = {
                                onUpdate { copy(triggers = triggers + newTimeOfDayTrigger(context)) }
                            },
                            onAddSchedule = {
                                onUpdate { copy(triggers = triggers + newScheduleTrigger()) }
                            },
                            onAddInterval = {
                                onUpdate { copy(triggers = triggers + newIntervalTrigger()) }
                            },
                            onAddBoardVehicle = {
                                onUpdate { copy(triggers = triggers + newBoardVehicleTrigger()) }
                            },
                            onAddServiceStart = {
                                onUpdate { copy(triggers = triggers + newServiceStartTrigger(context)) }
                            },
                            onAddNetworkAvailable = {
                                onUpdate { copy(triggers = triggers + newNetworkAvailableTrigger(context)) }
                            },
                            onAddButtonPress = {
                                onUpdate { copy(triggers = triggers + newButtonPressTrigger(1)) }
                            }
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(CardBorder)
                )

                // Right: Actions + Settings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp, 12.dp)
                ) {
                    SectionHeader(stringResource(R.string.automation_section_then))

                    editing.actions.forEachIndexed { idx, action ->
                        ActionRow(
                            index = idx,
                            action = action,
                            places = places,
                            onUpdate = { newAction ->
                                onUpdate {
                                    copy(actions = actions.toMutableList().apply { set(idx, newAction) })
                                }
                            },
                            onTest = onTestAction,
                            onMoveUp = if (idx > 0) {
                                { onUpdate { copy(actions = actions.moveItem(idx, up = true)) } }
                            } else null,
                            onMoveDown = if (idx < editing.actions.lastIndex) {
                                { onUpdate { copy(actions = actions.moveItem(idx, up = false)) } }
                            } else null,
                            onDelete = {
                                onUpdate {
                                    copy(actions = actions.toMutableList().apply { removeAt(idx) })
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (editing.actions.size < 10) {
                        AddActionButton(
                            onAddParam = {
                                val a = ACTION_COMMANDS.first()
                                onUpdate { copy(actions = actions + ActionDef(a.command, a.localizedName(context))) }
                            },
                            onAddNotification = { silent ->
                                onUpdate { copy(actions = actions + newNotificationAction(silent, context)) }
                            },
                            onAddAppLaunch = {
                                onUpdate { copy(actions = actions + newAppLaunchAction(context)) }
                            },
                            onAddCall = {
                                onUpdate { copy(actions = actions + newCallAction(context)) }
                            },
                            onAddNavigate = {
                                onUpdate { copy(actions = actions + newNavigateAction(context)) }
                            },
                            onAddUrl = {
                                onUpdate { copy(actions = actions + newUrlAction(context)) }
                            },
                            onAddDelay = {
                                onUpdate { copy(actions = actions + newDelayAction(context)) }
                            },
                            onAddMediaVolume = {
                                onUpdate { copy(actions = actions + newMediaVolumeAction(context)) }
                            },
                            onAddOutsideSound = {
                                onUpdate { copy(actions = actions + newOutsideSoundAction(context)) }
                            },
                            onAddStopOutsideSound = {
                                onUpdate { copy(actions = actions + newStopOutsideSoundAction()) }
                            },
                            onAddGoHome = {
                                onUpdate { copy(actions = actions + newGoHomeAction()) }
                            },
                            onAddHttp = {
                                onUpdate { copy(actions = actions + newHttpAction()) }
                            },
                            onAddPlayMusic = {
                                onUpdate { copy(actions = actions + newPlayMusicAction()) }
                            },
                            onAddStopMusic = {
                                onUpdate { copy(actions = actions + newStopMusicAction()) }
                            },
                            onAddPlayVideo = {
                                onUpdate { copy(actions = actions + newPlayVideoAction()) }
                            },
                            onAddStopVideo = {
                                onUpdate { copy(actions = actions + newStopVideoAction()) }
                            },
                            onAddSentryMode = {
                                onUpdate { copy(actions = actions + newSentryModeAction()) }
                            },
                            onAddAutoDoor = {
                                onUpdate { copy(actions = actions + newAutoDoorAction()) }
                            }
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.automation_section_on_exit))
                    Text(
                        stringResource(R.string.automation_section_on_exit_hint),
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    editing.exitActions.forEachIndexed { idx, action ->
                        ActionRow(
                            index = idx,
                            action = action,
                            places = places,
                            onUpdate = { newAction ->
                                onUpdate {
                                    copy(exitActions = exitActions.toMutableList().apply { set(idx, newAction) })
                                }
                            },
                            onTest = onTestAction,
                            onMoveUp = if (idx > 0) {
                                { onUpdate { copy(exitActions = exitActions.moveItem(idx, up = true)) } }
                            } else null,
                            onMoveDown = if (idx < editing.exitActions.lastIndex) {
                                { onUpdate { copy(exitActions = exitActions.moveItem(idx, up = false)) } }
                            } else null,
                            onDelete = {
                                onUpdate {
                                    copy(exitActions = exitActions.toMutableList().apply { removeAt(idx) })
                                }
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (editing.exitActions.size < 10) {
                        AddActionButton(
                            onAddParam = {
                                val a = ACTION_COMMANDS.first()
                                onUpdate { copy(exitActions = exitActions + ActionDef(a.command, a.localizedName(context))) }
                            },
                            onAddNotification = { silent ->
                                onUpdate { copy(exitActions = exitActions + newNotificationAction(silent, context)) }
                            },
                            onAddAppLaunch = {
                                onUpdate { copy(exitActions = exitActions + newAppLaunchAction(context)) }
                            },
                            onAddCall = {
                                onUpdate { copy(exitActions = exitActions + newCallAction(context)) }
                            },
                            onAddNavigate = {
                                onUpdate { copy(exitActions = exitActions + newNavigateAction(context)) }
                            },
                            onAddUrl = {
                                onUpdate { copy(exitActions = exitActions + newUrlAction(context)) }
                            },
                            onAddDelay = {
                                onUpdate { copy(exitActions = exitActions + newDelayAction(context)) }
                            },
                            onAddMediaVolume = {
                                onUpdate { copy(exitActions = exitActions + newMediaVolumeAction(context)) }
                            },
                            onAddOutsideSound = {
                                onUpdate { copy(exitActions = exitActions + newOutsideSoundAction(context)) }
                            },
                            onAddStopOutsideSound = {
                                onUpdate { copy(exitActions = exitActions + newStopOutsideSoundAction()) }
                            },
                            onAddGoHome = {
                                onUpdate { copy(exitActions = exitActions + newGoHomeAction()) }
                            },
                            onAddHttp = {
                                onUpdate { copy(exitActions = exitActions + newHttpAction()) }
                            },
                            onAddPlayMusic = {
                                onUpdate { copy(exitActions = exitActions + newPlayMusicAction()) }
                            },
                            onAddStopMusic = {
                                onUpdate { copy(exitActions = exitActions + newStopMusicAction()) }
                            },
                            onAddPlayVideo = {
                                onUpdate { copy(exitActions = exitActions + newPlayVideoAction()) }
                            },
                            onAddStopVideo = {
                                onUpdate { copy(exitActions = exitActions + newStopVideoAction()) }
                            },
                            onAddSentryMode = {
                                onUpdate { copy(exitActions = exitActions + newSentryModeAction()) }
                            },
                            onAddAutoDoor = {
                                onUpdate { copy(exitActions = exitActions + newAutoDoorAction()) }
                            },
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.automation_section_settings))

                    // Cooldown
                    SettingRow(stringResource(R.string.automation_setting_cooldown)) {
                        OutlinedTextField(
                            value = editing.cooldownSeconds.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { sec ->
                                    onUpdate {
                                        copy(
                                            cooldownSeconds = sec.coerceIn(
                                                AUTOMATION_COOLDOWN_MIN_SECONDS,
                                                AUTOMATION_COOLDOWN_MAX_SECONDS
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.width(70.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentGreen
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.automation_setting_unit_sec), fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.width(6.dp))
                        Text("0-60", fontSize = 11.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))

                    SettingRow(stringResource(R.string.automation_setting_repeat_interval)) {
                        OutlinedTextField(
                            value = repeatIntervalInput,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all(Char::isDigit)) {
                                    repeatIntervalInput = value.take(4)
                                    val seconds = repeatIntervalInput.toIntOrNull() ?: 0
                                    onUpdate {
                                        copy(repeatIntervalSeconds = seconds.coerceIn(0, AUTOMATION_REPEAT_MAX_SECONDS))
                                    }
                                }
                            },
                            modifier = Modifier.width(78.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentGreen,
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("秒", fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.width(6.dp))
                        Text("0=关闭", fontSize = 11.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))

                    // Require park
                    SettingRow(stringResource(R.string.automation_setting_park_only)) {
                        Switch(
                            checked = editing.requirePark,
                            onCheckedChange = { v -> onUpdate { copy(requirePark = v) } },
                            colors = bydSwitchColors(),
                        )
                    }

                    // Confirm before execute
                    SettingRow(stringResource(R.string.automation_setting_confirm_before)) {
                        Switch(
                            checked = editing.confirmBeforeExecute,
                            onCheckedChange = { v -> onUpdate { copy(confirmBeforeExecute = v) } },
                            colors = bydSwitchColors(),
                        )
                    }

                    // Fire once per trip
                    SettingRow(stringResource(R.string.automation_setting_once_per_trip)) {
                        Switch(
                            checked = editing.fireOncePerTrip,
                            onCheckedChange = { v -> onUpdate { copy(fireOncePerTrip = v) } },
                            colors = bydSwitchColors(),
                        )
                    }
                }
            }

            // Footer
            HorizontalDivider(color = CardBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp, 10.dp)
            ) {
                editorError?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    ) { Text(stringResource(R.string.automation_cancel_button)) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                        enabled = editing.name.isNotBlank() && editing.triggers.isNotEmpty() && editing.actions.isNotEmpty()
                    ) { Text(stringResource(R.string.automation_save_button), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// --- Trigger Row ---

/** Up/down reorder arrows shared by trigger and action rows. A null callback = boundary, shown dimmed and disabled. */
@Composable
private fun ReorderArrows(onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
    IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Outlined.KeyboardArrowUp, "上移",
            tint = TextSecondary.copy(alpha = if (onMoveUp != null) 1f else 0.25f), modifier = Modifier.size(18.dp))
    }
    IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Outlined.KeyboardArrowDown, "下移",
            tint = TextSecondary.copy(alpha = if (onMoveDown != null) 1f else 0.25f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TriggerRow(
    index: Int,
    trigger: TriggerDef,
    places: List<PlaceEntity>,
    onUpdate: (TriggerDef) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .border(1.dp, CardBorder.copy(alpha = 0.74f), RoundedCornerShape(14.dp))
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted,
            modifier = Modifier.width(16.dp))

        when (trigger.kind) {
            "place_enter", "place_exit" -> PlaceTriggerControls(trigger, places, onUpdate)
            "time_of_day" -> TimeOfDayTriggerControls(trigger, onUpdate)
            "time_range" -> ScheduleTriggerControls(trigger, onUpdate)
            "interval" -> IntervalTriggerControls(trigger, onUpdate)
            "service_start" -> ServiceStartTriggerControls()
            "network_available" -> NetworkAvailableTriggerControls()
            "board_vehicle" -> SimpleEventTriggerControls("上车开门")
            "button_press" -> ButtonPressTriggerControls(trigger, onUpdate)
            else -> ParamTriggerControls(trigger, onUpdate)
        }

        Spacer(Modifier.weight(1f))

        ReorderArrows(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Close, "delete", tint = Color(0xFFEF4444).copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ParamTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit
) {
    val context = LocalContext.current
    // Param dropdown
    CatalogDropdown(
        selected = TRIGGER_PARAMS.find { it.param == trigger.param }?.localizedName(context) ?: trigger.param,
        items = TRIGGER_PARAMS.map { it.localizedName(context) },
        categories = TRIGGER_PARAMS.map { it.localizedCategory(context) },
        modifier = Modifier.width(150.dp),
        onSelect = { idx ->
            val p = TRIGGER_PARAMS[idx]
            onUpdate(trigger.copy(param = p.param, chineseName = p.chineseName,
                displayName = "${p.localizedName(context)} ${trigger.operator} ${trigger.value}"))
        }
    )
    Spacer(Modifier.width(4.dp))

    // Operator dropdown
    var opExpanded by remember { mutableStateOf(false) }
    Box {
        Text(
            trigger.operator,
            fontSize = 13.sp, color = AccentOrange, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { opExpanded = true }
                .padding(8.dp, 6.dp)
                .width(30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        DropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
            OPERATORS.forEach { op ->
                DropdownMenuItem(
                    text = { Text(op, fontWeight = FontWeight.Bold) },
                    onClick = {
                        opExpanded = false
                        onUpdate(trigger.copy(operator = op))
                    }
                )
            }
        }
    }
    Spacer(Modifier.width(4.dp))

    // Value: enum dropdown or text input with unit
    val paramOption = TRIGGER_PARAMS.find { it.param == trigger.param }
    if (paramOption?.enumValues != null) {
        // Enum dropdown
        var enumExpanded by remember { mutableStateOf(false) }
        val enumLabel = paramOption.enumValues.find { it.first == trigger.value }?.second ?: trigger.value
        Box {
            Text(
                enumLabel,
                fontSize = 13.sp, color = AccentGreen,
                modifier = Modifier
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { enumExpanded = true }
                    .padding(8.dp, 6.dp)
            )
            DropdownMenu(expanded = enumExpanded, onDismissRequest = { enumExpanded = false }) {
                paramOption.enumValues.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 13.sp) },
                        onClick = {
                            enumExpanded = false
                            onUpdate(trigger.copy(value = value, operator = "=="))
                        }
                    )
                }
            }
        }
    } else {
        // Numeric input
        OutlinedTextField(
            value = trigger.value,
            onValueChange = { v -> onUpdate(trigger.copy(value = v)) },
            modifier = Modifier.width(70.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen, unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentGreen
            ),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
        if (paramOption?.unit?.isNotEmpty() == true) {
            Spacer(Modifier.width(4.dp))
            Text(paramOption.unit, fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun PlaceTriggerControls(
    trigger: TriggerDef,
    places: List<PlaceEntity>,
    onUpdate: (TriggerDef) -> Unit
) {
    val placeExists = places.any { it.id == trigger.placeId }
    val isStale = trigger.placeId != null && !placeExists

    Icon(
        Icons.Outlined.Place,
        contentDescription = null,
        tint = TextMuted,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(4.dp))

    if (isStale || (trigger.placeId == null && places.isEmpty())) {
        // Show warning when the referenced place was deleted
        Text(
            stringResource(R.string.automation_trigger_place_deleted),
            fontSize = 13.sp,
            color = Color(0xFFEF4444),
            modifier = Modifier
                .background(Color(0xFFEF4444).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(8.dp, 6.dp)
        )
    } else {
        // Place-name dropdown
        var placeExpanded by remember { mutableStateOf(false) }
        val deletedPlaceholder = stringResource(R.string.automation_trigger_place_deleted_placeholder)
        Box(modifier = Modifier.width(150.dp)) {
            Text(
                trigger.placeName ?: deletedPlaceholder,
                fontSize = 13.sp, color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { placeExpanded = true }
                    .padding(8.dp, 7.dp),
                maxLines = 1
            )
            val enterPrefix = stringResource(R.string.automation_trigger_place_enter_prefix)
            val exitPrefix = stringResource(R.string.automation_trigger_place_exit_prefix)
            DropdownMenu(expanded = placeExpanded, onDismissRequest = { placeExpanded = false }) {
                places.forEach { place ->
                    DropdownMenuItem(
                        text = { Text(place.name, fontSize = 13.sp) },
                        onClick = {
                            placeExpanded = false
                            val kindLabel = if (trigger.kind == "place_enter") enterPrefix else exitPrefix
                            onUpdate(trigger.copy(
                                placeId = place.id,
                                placeName = place.name,
                                displayName = "$kindLabel «${place.name}»"
                            ))
                        }
                    )
                }
            }
        }
    }
    Spacer(Modifier.width(4.dp))

    // Kind toggle pill: enter / exit.
    val isEnter = trigger.kind == "place_enter"
    val enterLabel = stringResource(R.string.automation_trigger_place_enter_label)
    val exitLabel = stringResource(R.string.automation_trigger_place_exit_label)
    val enterPrefix2 = stringResource(R.string.automation_trigger_place_enter_prefix)
    val exitPrefix2 = stringResource(R.string.automation_trigger_place_exit_prefix)
    val label = if (isEnter) enterLabel else exitLabel
    Box(
        modifier = Modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable {
                val newKind = if (isEnter) "place_exit" else "place_enter"
                val kindLabel = if (newKind == "place_enter") enterPrefix2 else exitPrefix2
                onUpdate(trigger.copy(
                    kind = newKind,
                    displayName = "$kindLabel «${trigger.placeName ?: "?"}»"
                ))
            }
            .padding(8.dp, 6.dp)
    ) {
        Text(label, color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeOfDayTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit
) {
    val dayLabel = stringResource(R.string.automation_trigger_day)
    val nightLabel = stringResource(R.string.automation_trigger_night)
    val dawnLabel = stringResource(R.string.automation_trigger_dawn)
    val duskLabel = stringResource(R.string.automation_trigger_dusk)
    val phases = listOf("DAY" to dayLabel, "NIGHT" to nightLabel, "DAWN" to dawnLabel, "DUSK" to duskLabel)
    val current = phases.find { it.first == trigger.value.uppercase() } ?: phases[1]
    var expanded by remember { mutableStateOf(false) }

    Text(stringResource(R.string.automation_trigger_time_of_day_label), fontSize = 12.sp, color = TextMuted)
    Spacer(Modifier.width(4.dp))
    Box {
        Text(
            current.second,
            fontSize = 13.sp,
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(8.dp, 6.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            phases.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onUpdate(trigger.copy(value = value, displayName = label))
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduleTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit
) {
    val context = LocalContext.current
    val spec = remember(trigger.value) {
        ScheduleSpec.fromJson(trigger.value) ?: ScheduleSpec(8 * 60, 10 * 60, emptySet())
    }
    val isExact = spec.isExact

    fun push(newSpec: ScheduleSpec) {
        onUpdate(trigger.copy(value = newSpec.toJson(), displayName = scheduleDisplayName(context, newSpec)))
    }

    fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            currentMinute / 60, currentMinute % 60, true
        ).show()
    }

    val dayLabels = listOf(
        1 to stringResource(R.string.automation_day_mon),
        2 to stringResource(R.string.automation_day_tue),
        3 to stringResource(R.string.automation_day_wed),
        4 to stringResource(R.string.automation_day_thu),
        5 to stringResource(R.string.automation_day_fri),
        6 to stringResource(R.string.automation_day_sat),
        7 to stringResource(R.string.automation_day_sun),
    )

    Column {
        // Mode toggle: exact vs range
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = isExact,
                onClick = { if (!isExact) push(spec.copy(toMinute = spec.fromMinute)) },
                label = { Text(stringResource(R.string.automation_schedule_mode_exact), fontSize = 12.sp) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !isExact,
                onClick = {
                    // Open a 2h window so "from" and "to" differ when switching to range.
                    if (isExact) push(spec.copy(toMinute = (spec.fromMinute + 120) % 1440))
                },
                label = { Text(stringResource(R.string.automation_schedule_mode_range), fontSize = 12.sp) },
            )
        }
        Spacer(Modifier.height(6.dp))
        // Time picker(s)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isExact) {
                Text(stringResource(R.string.automation_schedule_time_label) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.fromMinute)) {
                    pickTime(spec.fromMinute) { push(spec.copy(fromMinute = it, toMinute = it)) }
                }
            } else {
                Text(stringResource(R.string.automation_schedule_from) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.fromMinute)) {
                    pickTime(spec.fromMinute) { push(spec.copy(fromMinute = it)) }
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.automation_schedule_to) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.toMinute)) {
                    pickTime(spec.toMinute) { push(spec.copy(toMinute = it)) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Days of week (none selected = every day)
        Text(stringResource(R.string.automation_schedule_days_label), fontSize = 11.sp, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Row {
            dayLabels.forEach { (d, label) ->
                DayChip(
                    label = label,
                    selected = d in spec.days,
                    onClick = {
                        val newDays = if (d in spec.days) spec.days - d else spec.days + d
                        push(spec.copy(days = newDays))
                    },
                )
                Spacer(Modifier.width(3.dp))
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp, 6.dp)
    )
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) AccentGreen else TextMuted,
        modifier = Modifier
            .background(if (selected) AccentGreen.copy(alpha = 0.15f) else CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) AccentGreen else CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    )
}

/** Readable schedule label; first token is the time so the rule summary stays compact. */
private fun scheduleDisplayName(context: android.content.Context, spec: ScheduleSpec): String {
    val time = if (spec.isExact) {
        minuteToHHmm(spec.fromMinute)
    } else {
        "${minuteToHHmm(spec.fromMinute)}-${minuteToHHmm(spec.toMinute)}"
    }
    val days = scheduleDaysShort(context, spec.days)
    return if (days.isEmpty()) time else "$time $days"
}

private fun scheduleDaysShort(context: android.content.Context, days: Set<Int>): String {
    if (days.isEmpty() || days.size == 7) return ""
    val labels = mapOf(
        1 to R.string.automation_day_mon,
        2 to R.string.automation_day_tue,
        3 to R.string.automation_day_wed,
        4 to R.string.automation_day_thu,
        5 to R.string.automation_day_fri,
        6 to R.string.automation_day_sat,
        7 to R.string.automation_day_sun,
    )
    return days.sorted().joinToString(",") { context.getString(labels.getValue(it)) }
}

@Composable
private fun IntervalTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Timer, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("每", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = trigger.value,
            onValueChange = { value ->
                value.toIntOrNull()?.let { seconds ->
                    val safe = seconds.coerceIn(15, 300)
                    onUpdate(trigger.copy(value = safe.toString(), displayName = "每 $safe 秒"))
                }
            },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentGreen,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.width(6.dp))
        Text("秒（15-300）", fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun SimpleEventTriggerControls(label: String) {
    Icon(Icons.Outlined.Bolt, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, fontSize = 13.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
}

@Composable
private fun ServiceStartTriggerControls() {
    Icon(
        Icons.Outlined.PlayArrow,
        contentDescription = null,
        tint = AccentGreen,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        stringResource(R.string.automation_trigger_service_start),
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun NetworkAvailableTriggerControls() {
    Icon(
        Icons.Outlined.Wifi,
        contentDescription = null,
        tint = AccentGreen,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        stringResource(R.string.automation_trigger_internet),
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ButtonPressTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit,
) {
    Icon(
        Icons.Outlined.Apps,
        contentDescription = null,
        tint = AccentGreen,
        modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text("桌面按钮", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.width(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { number ->
            AutoChip(
                label = number.toString(),
                selected = trigger.value.toIntOrNull() == number,
            ) {
                onUpdate(newButtonPressTrigger(number))
            }
        }
    }
}

// --- Action Row ---

@Composable
private fun ActionRow(
    index: Int,
    action: ActionDef,
    places: List<PlaceEntity>,
    onUpdate: (ActionDef) -> Unit,
    onTest: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .border(1.dp, AccentTeal.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentTeal,
            modifier = Modifier.width(16.dp))

        when (action.kind) {
            "notification_silent", "notification_sound" ->
                NotificationActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "app_launch" ->
                AppLaunchActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "call" ->
                CallActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "navigate" ->
                NavigateActionControls(action = action, places = places, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "url" ->
                UrlActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "delay" ->
                DelayActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "media_volume" ->
                MediaVolumeActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "outside_sound" ->
                OutsideSoundActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "play_music", "play_video" ->
                AutomationMediaActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "sentry_mode" ->
                SentryModeActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "http" ->
                HttpActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "stop_outside_sound", "stop_music", "stop_video", "go_home" ->
                SimpleActionControls(action = action, modifier = Modifier.weight(1f))
            "auto_door" ->
                AutoDoorActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            else -> // "param" (default)
                ParamActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
        }

        // Fire this vehicle command immediately for live testing.
        // Only for param (vehicle) actions; result is shown via Toast + logcat status line.
        if (action.kind == "param" && action.command.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onTest(action.command) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.PlayArrow, "立即执行", tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        ReorderArrows(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Close, "delete", tint = Color(0xFFEF4444).copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ParamActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    CatalogDropdown(
        selected = ACTION_COMMANDS.find { it.command == action.command }?.localizedName(context) ?: action.displayName,
        items = ACTION_COMMANDS.map { it.localizedName(context) },
        categories = ACTION_COMMANDS.map { it.localizedCategory(context) },
        modifier = modifier,
        onSelect = { idx ->
            val a = ACTION_COMMANDS[idx]
            onUpdate(ActionDef(a.command, a.localizedName(context)))
        }
    )
}

// Delay option keys — labels are resolved at runtime via stringResource
private val DELAY_OPTION_MS = listOf(500L, 1000L, 2000L, 3000L, 5000L, 10000L)

@Composable
private fun DelayActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMs = action.payload?.toLongOrNull() ?: 1000L
    val label0_5s = stringResource(R.string.automation_delay_0_5s)
    val label1s = stringResource(R.string.automation_delay_1s)
    val label2s = stringResource(R.string.automation_delay_2s)
    val label3s = stringResource(R.string.automation_delay_3s)
    val label5s = stringResource(R.string.automation_delay_5s)
    val label10s = stringResource(R.string.automation_delay_10s)
    val delayLabels = listOf(
        500L to label0_5s,
        1000L to label1s,
        2000L to label2s,
        3000L to label3s,
        5000L to label5s,
        10000L to label10s
    )
    // Pre-build display names for onClick lambdas (stringResource cannot be called in non-Composable onClick)
    val delayDisplayNames = delayLabels.associate { (ms, lbl) ->
        ms to stringResource(R.string.automation_delay_display_name, lbl)
    }
    val currentLabel = delayLabels.find { it.first == currentMs }?.second ?: "${currentMs}ms"
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Pause,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.automation_action_delay_label), fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.width(6.dp))
        Box {
            Text(
                currentLabel,
                fontSize = 13.sp,
                color = AccentTeal,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(8.dp, 6.dp)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                delayLabels.forEach { (ms, label) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onUpdate(action.copy(
                                payload = ms.toString(),
                                displayName = delayDisplayNames[ms] ?: label
                            ))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaVolumeActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Max volume is device-dependent; read it once from the head unit's AudioManager.
    val maxVolume = remember {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    }
    val current = (action.payload?.toIntOrNull() ?: 2).coerceIn(0, maxVolume)
    val label = stringResource(R.string.automation_action_media_volume_label)
    val namePrefix = stringResource(R.string.automation_action_media_volume)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = current.toFloat(),
            onValueChange = { v ->
                val lvl = v.toInt().coerceIn(0, maxVolume)
                onUpdate(action.copy(payload = lvl.toString(), displayName = "$namePrefix: $lvl"))
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = (maxVolume - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$current/$maxVolume",
            fontSize = 13.sp,
            color = AccentTeal,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
private fun OutsideSoundActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sounds = remember { SoundLibrary(context.applicationContext).listSounds() }
    val selectedPath = action.outsideSoundPath()
    val selected = sounds.firstOrNull { it.file.absolutePath == selectedPath }
    val selectedName = selected?.name
        ?: action.outsideSoundName().takeIf(String::isNotBlank)
        ?: stringResource(R.string.automation_action_outside_sound_empty)
    val isMissing = selectedPath.isNotBlank() && selected == null
    val loop = action.outsideSoundLoop()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.automation_action_outside_sound_label), fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    selectedName,
                    fontSize = 13.sp,
                    color = if (isMissing || sounds.isEmpty()) Color(0xFFEF4444) else AccentTeal,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, if (isMissing) Color(0xFFEF4444).copy(alpha = 0.45f) else CardBorder, RoundedCornerShape(10.dp))
                        .clickable { if (sounds.isNotEmpty()) expanded = true }
                        .padding(8.dp, 7.dp),
                    maxLines = 1
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxHeight(0.45f)
                ) {
                    sounds.forEach { sound ->
                        DropdownMenuItem(
                            text = { Text("${sound.name} · ${sound.sizeLabel}", fontSize = 13.sp) },
                            onClick = {
                                expanded = false
                                onUpdate(action.withOutsideSound(sound))
                            }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurface.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("循环播放", color = TextSecondary, fontSize = 12.sp)
            Switch(
                checked = loop,
                onCheckedChange = { onUpdate(action.withOutsideSoundLoop(it)) },
                colors = bydSwitchColors(),
                modifier = Modifier.size(width = 44.dp, height = 24.dp)
            )
        }
    }
}

@Composable
private fun AutomationMediaActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isVideo = action.kind == "play_video"
    val selectedName = action.automationMediaFileName().ifBlank {
        if (isVideo) "选择视频" else "选择音乐"
    }
    val volume = action.automationMediaVolume()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val fileName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank {
                if (isVideo) "已选视频" else "已选音乐"
            }
        onUpdate(action.withAutomationMedia(uri.toString(), fileName))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isVideo) "视频" else "音乐", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(
                selectedName,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { launcher.launch(arrayOf(if (isVideo) "video/*" else "audio/*")) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                color = if (action.automationMediaPath().isBlank()) TextMuted else AccentTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("音量", color = TextSecondary, fontSize = 12.sp)
            Slider(
                value = volume.toFloat(),
                onValueChange = { onUpdate(action.withAutomationMediaVolume(it.toInt())) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("$volume%", color = AccentTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
        }
        if (!isVideo) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("循环播放", color = TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = action.automationMediaLoop(),
                    onCheckedChange = { onUpdate(action.withAutomationMediaLoop(it)) },
                    colors = bydSwitchColors(),
                )
            }
        }
    }
}

@Composable
private fun SentryModeActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = action.payload != "off"
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text("哨兵模式", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        AutoChip(label = "开启", selected = enabled) { onUpdate(action.withSentryMode(true)) }
        Spacer(Modifier.width(6.dp))
        AutoChip(label = "关闭", selected = !enabled) { onUpdate(action.withSentryMode(false)) }
    }
}

@Composable
private fun AutoDoorActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = action.autoDoorActionOption() ?: AUTO_DOOR_ACTIONS.first()
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Bolt,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.automation_action_auto_door_label), fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            Text(
                selected.label,
                fontSize = 13.sp,
                color = AccentTeal,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(8.dp, 7.dp),
                maxLines = 1
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AUTO_DOOR_ACTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onUpdate(action.withAutoDoorAction(option))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleActionControls(
    action: ActionDef,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Bolt, null, tint = AccentTeal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(action.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HttpActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            Text(
                action.httpMethod(),
                color = AccentTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(CardSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                methods.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onUpdate(action.withHttp(method, action.httpUrl()))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        OutlinedTextField(
            value = action.httpUrl(),
            onValueChange = { onUpdate(action.withHttp(action.httpMethod(), it)) },
            placeholder = { Text("https://", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentTeal,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Catalog Dropdown (with category headers) ---

@Composable
private fun CatalogDropdown(
    selected: String,
    items: List<String>,
    categories: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Text(
            selected,
            fontSize = 13.sp, color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(8.dp, 7.dp),
            maxLines = 1
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxHeight(0.5f)
        ) {
            var lastCat = ""
            items.forEachIndexed { idx, item ->
                val cat = categories[idx]
                if (cat != lastCat) {
                    lastCat = cat
                    DropdownMenuItem(
                        text = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted) },
                        onClick = {},
                        enabled = false
                    )
                }
                DropdownMenuItem(
                    text = { Text(item, fontSize = 13.sp) },
                    onClick = { expanded = false; onSelect(idx) }
                )
            }
        }
    }
}

// --- Journal Dialog ---

@Composable
private fun JournalDialog(logs: List<RuleLogEntity>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .fillMaxHeight(0.78f)
                .background(CardSurface.copy(alpha = 0.98f), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(22.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.automation_journal_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, "close", tint = TextMuted)
                }
            }
            HorizontalDivider(color = CardBorder)

            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.automation_journal_empty), color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: RuleLogEntity) {
    val context = LocalContext.current
    val borderColor = if (log.success) AccentGreen else Color(0xFFEF4444)
    val bgColor = if (log.success) AccentGreen.copy(alpha = 0.05f) else Color(0xFFEF4444).copy(alpha = 0.05f)

    val actionsText = remember(log.actionsResult) {
        try {
            val arr = JSONArray(log.actionsResult)
            (0 until arr.length()).joinToString(", ") {
                val obj = arr.getJSONObject(it)
                obj.optString("displayName", obj.optString("command", ""))
            }
        } catch (_: Exception) { log.actionsResult }
    }

    val reasonText = remember(log.actionsResult) {
        try {
            val arr = JSONArray(log.actionsResult)
            (0 until arr.length()).mapNotNull {
                val obj = arr.getJSONObject(it)
                obj.optString("reason", "").ifEmpty { null }
            }.firstOrNull()
        } catch (_: Exception) { null }
    }

    val snapshotText = remember(log.triggersSnapshot) {
        try {
            val obj = JSONObject(log.triggersSnapshot)
            obj.keys().asSequence().joinToString(" · ") { key ->
                val paramName = TRIGGER_PARAMS.find { it.param == key }?.localizedName(context) ?: key
                "$paramName: ${obj.get(key)}"
            }
        } catch (_: Exception) { "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
    ) {
        // Left color stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(borderColor)
        )
        // Content
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(log.ruleName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(
                    formatRelativeTime(log.triggeredAt, LocalContext.current),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted
                )
            }
            Text("→ $actionsText", fontSize = 12.sp, color = AccentTeal)
            if (reasonText != null) {
                Text(reasonText, fontSize = 11.sp, color = AccentOrange)
            }
            if (snapshotText.isNotEmpty()) {
                Text(snapshotText, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

// --- Shared Composables ---

@Composable
private fun AutoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(44.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = AccentContent,
            containerColor = CardSurfaceElevated.copy(alpha = 0.86f),
            labelColor = TextSecondary
        ),
        shape = RoundedCornerShape(999.dp),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = CardBorder.copy(alpha = 0.58f),
            selectedBorderColor = Color.Transparent,
            enabled = true, selected = selected
        )
    )
}

@Composable
private fun LogicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) AccentGreen else CardSurface,
                RoundedCornerShape(10.dp)
            )
            .border(if (selected) 0.dp else 1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp, 5.dp)
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) AccentContent else TextSecondary
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AccentGreen,
        modifier = Modifier
            .background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun AddActionButton(
    onAddParam: () -> Unit,
    onAddNotification: (silent: Boolean) -> Unit,
    onAddAppLaunch: () -> Unit,
    onAddCall: () -> Unit,
    onAddNavigate: () -> Unit,
    onAddUrl: () -> Unit,
    onAddDelay: () -> Unit,
    onAddMediaVolume: () -> Unit,
    onAddOutsideSound: () -> Unit,
    onAddStopOutsideSound: () -> Unit,
    onAddGoHome: () -> Unit,
    onAddHttp: () -> Unit,
    onAddPlayMusic: () -> Unit,
    onAddStopMusic: () -> Unit,
    onAddPlayVideo: () -> Unit,
    onAddStopVideo: () -> Unit,
    onAddSentryMode: () -> Unit,
    onAddAutoDoor: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showOverlayPrompt by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceElevated.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                .border(1.dp, AccentTeal.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .clickable { menuExpanded = true }
                .padding(12.dp, 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.automation_add_action_button), fontSize = 12.sp, color = TextMuted)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_dplus_command), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddParam() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_notification_silent), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddNotification(true) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_notification_sound), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddNotification(false)
                    if (!android.provider.Settings.canDrawOverlays(context)) {
                        showOverlayPrompt = true
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_app_launch), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddAppLaunch() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_call), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddCall() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_navigate), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddNavigate() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_url), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddUrl() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_delay), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddDelay() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_media_volume), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddMediaVolume() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_outside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddOutsideSound() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_outside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddStopOutsideSound() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_go_home), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddGoHome() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_http), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddHttp() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_play_music), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddPlayMusic() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_music), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddStopMusic() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_play_video), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddPlayVideo() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_video), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddStopVideo() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_sentry_mode), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddSentryMode() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_auto_door), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddAutoDoor() }
            )
        }
    }

    if (showOverlayPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPrompt = false },
            containerColor = CardSurface,
            title = { Text(stringResource(R.string.automation_overlay_permission_title), color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    stringResource(R.string.automation_overlay_permission_text),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPrompt = false
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }) {
                    Text(stringResource(R.string.automation_overlay_open_settings), color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPrompt = false }) {
                    Text(stringResource(R.string.automation_overlay_later), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun NotificationActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val silent = action.kind == "notification_silent"
    val title = action.notificationTitle()
    val text = action.notificationText()
    val preview = when {
        title.isNotBlank() && text.isNotBlank() -> "$title — $text"
        title.isNotBlank() -> title
        text.isNotBlank() -> text
        else -> stringResource(R.string.automation_tap_to_configure)
    }

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (silent) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (title.isBlank() && text.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        NotificationEditDialog(
            initialTitle = title,
            initialText = text,
            silent = silent,
            onDismiss = { editing = false },
            onSave = { newTitle, newText ->
                onUpdate(action.withNotification(newTitle, newText))
                editing = false
            }
        )
    }
}

@Composable
private fun NotificationEditDialog(
    initialTitle: String,
    initialText: String,
    silent: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var titleText by rememberSaveable { mutableStateOf(initialTitle) }
    var bodyText by rememberSaveable { mutableStateOf(initialText) }
    val canSave = titleText.trim().isNotBlank() && titleText.trim().length <= 40

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text(
                text = if (silent) stringResource(R.string.automation_action_notification_silent) else stringResource(R.string.automation_action_notification_sound),
                color = TextPrimary,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { if (it.length <= 40) titleText = it },
                    label = { Text(stringResource(R.string.automation_notification_title_label)) },
                    singleLine = true,
                    isError = titleText.isNotEmpty() && !canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { if (it.length <= 200) bodyText = it },
                    label = { Text(stringResource(R.string.automation_notification_body_label)) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canSave) onSave(titleText.trim(), bodyText.trim()) }, enabled = canSave) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

// --- App Launch Action Controls ---

@Composable
private fun AppLaunchActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var pendingMinimize by remember(action) { mutableStateOf(action.appLaunchMinimize()) }
    val pkg = action.appLaunchPackageName()
    val label = action.appLaunchLabel()
    val preview = when {
        label.isNotBlank() -> label
        pkg.isNotBlank() -> pkg
        else -> stringResource(R.string.automation_tap_to_pick_app)
    }

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (label.isBlank() && pkg.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        AppLaunchPickerDialog(
            currentPackage = pkg,
            showMinimizeToggle = true,
            initialMinimize = pendingMinimize,
            onMinimizeChanged = { pendingMinimize = it },
            onDismiss = {
                pendingMinimize = action.appLaunchMinimize()
                editing = false
            },
            onSelect = { newPkg, newLabel ->
                onUpdate(action.withAppLaunch(newPkg, newLabel, pendingMinimize))
                editing = false
            },
        )
    }
}

// --- Call Action Controls ---

@Composable
private fun CallActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val phone = action.callPhone()
    val name = action.callName()
    val preview = when {
        name.isNotBlank() && phone.isNotBlank() -> "$name — $phone"
        phone.isNotBlank() -> phone
        else -> stringResource(R.string.automation_tap_to_set_phone)
    }

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Call,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (phone.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        CallEditDialog(
            initialPhone = phone,
            initialName = name,
            initialAutoDial = action.callAutoDial(),
            onDismiss = { editing = false },
            onSave = { newPhone, newName, newAutoDial ->
                onUpdate(action.withCall(newPhone, newName, newAutoDial))
                editing = false
            }
        )
    }
}

@Composable
private fun CallEditDialog(
    initialPhone: String,
    initialName: String,
    initialAutoDial: Boolean,
    onDismiss: () -> Unit,
    onSave: (phone: String, name: String, autoDial: Boolean) -> Unit
) {
    var phoneText by rememberSaveable { mutableStateOf(initialPhone) }
    var nameText by rememberSaveable { mutableStateOf(initialName) }
    var autoDial by remember { mutableStateOf(initialAutoDial) }
    val trimmedPhone = phoneText.trim()
    val canSave = trimmedPhone.isNotBlank() && trimmedPhone.length in 5..20

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.automation_call_dialog_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.automation_call_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text(stringResource(R.string.automation_call_phone_label)) },
                    singleLine = true,
                    isError = phoneText.isNotBlank() && !canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoDial = !autoDial }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = autoDial,
                        onCheckedChange = { autoDial = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentGreen,
                            uncheckedColor = TextMuted,
                            checkmarkColor = AccentContent
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(stringResource(R.string.automation_call_auto_dial_label), color = TextPrimary, fontSize = 13.sp)
                        Text(
                            stringResource(R.string.automation_call_auto_dial_hint),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSave) onSave(trimmedPhone, nameText.trim(), autoDial) },
                enabled = canSave
            ) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

// --- Navigate Action Controls ---

@Composable
private fun NavigateActionControls(
    action: ActionDef,
    places: List<PlaceEntity>,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val name = action.navigateName()
    val preview = if (name.isNotBlank()) name else stringResource(R.string.automation_tap_to_pick_place)

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Navigation,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (name.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        NavigateEditDialog(
            action = action,
            places = places,
            onDismiss = { editing = false },
            onSave = { lat, lon, placeName ->
                onUpdate(action.withNavigate(lat, lon, placeName))
                editing = false
            }
        )
    }
}

@Composable
private fun NavigateEditDialog(
    action: ActionDef,
    places: List<PlaceEntity>,
    onDismiss: () -> Unit,
    onSave: (lat: Double, lon: Double, name: String) -> Unit
) {
    // Preselect by name match, then by lat/lon proximity, otherwise null
    val initialPlace = remember(action, places) {
        val actionName = action.navigateName()
        val actionLat = action.navigateLat()
        val actionLon = action.navigateLon()
        places.find { it.name == actionName }
            ?: if (actionLat != null && actionLon != null) {
                places.find { Math.abs(it.lat - actionLat) < 0.0001 && Math.abs(it.lon - actionLon) < 0.0001 }
            } else null
    }
    var selectedPlace by remember { mutableStateOf(initialPlace) }
    var placeExpanded by remember { mutableStateOf(false) }

    val canSave = selectedPlace != null && places.isNotEmpty()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.automation_navigate_dialog_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            if (places.isEmpty()) {
                Text(
                    text = stringResource(R.string.automation_navigate_no_places_hint),
                    fontSize = 13.sp,
                    color = TextMuted
                )
            } else {
                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = selectedPlace?.name ?: stringResource(R.string.automation_navigate_pick_place_placeholder),
                            fontSize = 13.sp,
                            color = if (selectedPlace == null) TextMuted else TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .clickable { placeExpanded = true }
                                .padding(12.dp, 10.dp),
                            maxLines = 1
                        )
                        DropdownMenu(expanded = placeExpanded, onDismissRequest = { placeExpanded = false }) {
                            places.forEach { place ->
                                DropdownMenuItem(
                                    text = { Text(place.name, fontSize = 13.sp) },
                                    onClick = {
                                        placeExpanded = false
                                        selectedPlace = place
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = selectedPlace
                    if (canSave && p != null) onSave(p.lat, p.lon, p.name)
                },
                enabled = canSave
            ) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

// --- URL Action Controls ---

@Composable
private fun UrlActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val url = action.urlString()
    val preview = if (url.isNotBlank()) url else stringResource(R.string.automation_tap_to_set_url)

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (url.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        UrlEditDialog(
            initialUrl = url,
            initialMinimize = action.urlMinimize(),
            onDismiss = { editing = false },
            onSave = { newUrl, newMinimize ->
                onUpdate(action.withUrl(newUrl, newMinimize))
                editing = false
            }
        )
    }
}

@Composable
private fun UrlEditDialog(
    initialUrl: String,
    initialMinimize: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    var minimize by remember { mutableStateOf(initialMinimize) }
    val trimmed = urlText.trim()
    val urlValid = trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.+"))
    val canSave = trimmed.isNotBlank() && urlValid

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen,
        errorBorderColor = Color(0xFFEF4444),
        errorLabelColor = Color(0xFFEF4444)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.automation_url_dialog_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(stringResource(R.string.automation_url_field_label)) },
                    singleLine = true,
                    isError = urlText.isNotBlank() && !urlValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { minimize = !minimize }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = minimize,
                        onCheckedChange = { minimize = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentGreen,
                            uncheckedColor = CardBorder,
                            checkmarkColor = TextPrimary
                        )
                    )
                    Text(
                        stringResource(R.string.automation_url_minimize_label),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canSave) onSave(trimmed, minimize) }, enabled = canSave) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AddTriggerButton(
    places: List<PlaceEntity>,
    onAddParam: () -> Unit,
    onAddPlace: (PlaceEntity) -> Unit,
    onAddTimeOfDay: () -> Unit,
    onAddSchedule: () -> Unit,
    onAddInterval: () -> Unit,
    onAddBoardVehicle: () -> Unit,
    onAddServiceStart: () -> Unit,
    onAddNetworkAvailable: () -> Unit,
    onAddButtonPress: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceElevated.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .clickable { menuExpanded = true }
                .padding(12.dp, 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.automation_add_condition_button), fontSize = 12.sp, color = TextMuted)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_param), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddParam() }
            )
            val firstPlace = places.firstOrNull()
            DropdownMenuItem(
                text = {
                    if (firstPlace != null) {
                        Text(stringResource(R.string.automation_trigger_type_place), fontSize = 13.sp)
                    } else {
                        Column {
                            Text(stringResource(R.string.automation_trigger_type_place), fontSize = 13.sp, color = TextSecondary)
                            Text(stringResource(R.string.automation_trigger_type_place_empty_hint), fontSize = 11.sp, color = TextMuted)
                        }
                    }
                },
                onClick = {
                    if (firstPlace != null) {
                        menuExpanded = false
                        onAddPlace(firstPlace)
                    }
                },
                enabled = firstPlace != null
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_time_of_day), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddTimeOfDay()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_schedule), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddSchedule()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_interval), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddInterval()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_board_vehicle), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddBoardVehicle()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_service_start), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddServiceStart()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_internet), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddNetworkAvailable()
                }
            )
            DropdownMenuItem(
                text = { Text("桌面按钮", fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddButtonPress()
                }
            )
        }
    }
}

private fun newPlaceTrigger(place: PlaceEntity, context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "Place",
        chineseName = "位置",
        operator = "==",
        value = "enter",
        displayName = localized("进入「${place.name}」", "进入「${place.name}」", "进入「${place.name}」", context),
        kind = "place_enter",
        placeId = place.id,
        placeName = place.name
    )
}

private fun newServiceStartTrigger(context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "ServiceStart",
        chineseName = "服务启动",
        operator = "==",
        value = "true",
        displayName = localized("超级玩具箱启动", "超级玩具箱启动", "超级玩具箱启动", context),
        kind = "service_start"
    )
}

private fun newNetworkAvailableTrigger(context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "NetworkAvailable",
        chineseName = "网络可用",
        operator = "==",
        value = "true",
        displayName = localized("网络可用", "网络可用", "网络可用", context),
        kind = "network_available"
    )
}

private fun newIntervalTrigger(): TriggerDef = TriggerDef(
    param = "Interval",
    chineseName = "定时间隔",
    operator = "==",
    value = "60",
    displayName = "每 60 秒",
    kind = "interval",
)

private fun newBoardVehicleTrigger(): TriggerDef = TriggerDef(
    param = "BoardVehicle",
    chineseName = "上车开门",
    operator = "==",
    value = "true",
    displayName = "上车开门",
    kind = "board_vehicle",
)

private fun newButtonPressTrigger(buttonId: Int): TriggerDef {
    val number = buttonId.coerceIn(1, 4)
    return TriggerDef(
        param = "button",
        chineseName = "桌面按钮",
        operator = "==",
        value = number.toString(),
        displayName = "桌面按钮 $number",
        kind = "button_press",
    )
}

private fun newDelayAction(context: android.content.Context): ActionDef = ActionDef(
    command = "delay_1000",
    displayName = localized("延迟 1 秒", "延迟 1 秒", "延迟 1 秒", context),
    kind = "delay",
    payload = "1000"
)

private fun newMediaVolumeAction(context: android.content.Context): ActionDef = ActionDef(
    command = "media_volume",
    displayName = localized("媒体音量：2", "媒体音量：2", "媒体音量：2", context),
    kind = "media_volume",
    payload = "2"
)

private fun newTimeOfDayTrigger(context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "TimeOfDay",
        chineseName = "时间段",
        operator = "==",
        value = "NIGHT",
        displayName = localized("夜晚", "夜晚", "夜晚", context),
        kind = "time_of_day"
    )
}

private fun newScheduleTrigger(): TriggerDef {
    // Default: 08:00-10:00 window, every day. displayName carries only the time
    // (no days), so no context is needed here.
    val spec = ScheduleSpec(fromMinute = 8 * 60, toMinute = 10 * 60, days = emptySet())
    return TriggerDef(
        param = "Schedule",
        chineseName = "时间表",
        operator = "==",
        value = spec.toJson(),
        displayName = "${minuteToHHmm(spec.fromMinute)}-${minuteToHHmm(spec.toMinute)}",
        kind = "time_range"
    )
}

// --- Helpers ---

private fun formatRelativeTime(ts: Long, context: android.content.Context): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateSdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    return when {
        diff < 24 * 60 * 60 * 1000L -> sdf.format(Date(ts))
        diff < 48 * 60 * 60 * 1000L -> context.getString(R.string.automation_time_yesterday, sdf.format(Date(ts)))
        else -> dateSdf.format(Date(ts))
    }
}
