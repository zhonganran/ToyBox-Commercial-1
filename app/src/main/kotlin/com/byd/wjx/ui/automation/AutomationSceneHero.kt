package com.byd.wjx.ui.automation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCoral
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun AutomationSceneHeader(
    filter: RuleFilter,
    onFilterChanged: (RuleFilter) -> Unit,
    onOpenJournal: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "智能联动",
                    color = TextPrimary,
                    fontSize = 38.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = SceneBlue,
                    modifier = Modifier.size(31.dp),
                )
            }
            Text(
                "让车辆状态自动触发你需要的动作",
                color = TextPrimary.copy(alpha = 0.78f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = SceneBlue,
                    contentColor = Color.White,
                ),
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
    val exitOnly = enterAction == null && exitAction != null
    val unplugEvent = isChargeGunUnplugEvent(trigger, exitOnly)
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
            icon = automationTriggerIcon(trigger),
            colors = listOf(ScenePink, SceneCoral),
            label = trigger?.value?.take(2)?.uppercase() ?: "IF",
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                automationTriggerSummary(trigger, exitOnly),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Text(rule.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
        Text("→", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Light)
        AutomationNodeIcon(
            icon = automationActionIcon(action),
            colors = listOf(SceneCyan, SceneBlue),
            label = "",
        )
        Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (exitOnly && !unplugEvent) {
                    "退出时：${automationActionSummary(action)}"
                } else {
                    automationActionSummary(action)
                },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (rule.fireOncePerTrip) "单次" else "循环",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (rule.enabled) Color(0xFF1DCB72) else TextMuted),
                )
                Text(
                    if (rule.enabled) "运行中" else "已停用",
                    color = if (rule.enabled) Color(0xFF13AE5E) else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun AutomationNodeIcon(icon: ImageVector, colors: List<Color>, label: String) {
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
                cubicTo(
                    size.width * 0.24f,
                    y,
                    size.width * 0.33f,
                    size.height * 0.12f,
                    size.width * 0.50f,
                    size.height * 0.12f,
                )
                cubicTo(
                    size.width * 0.67f,
                    size.height * 0.12f,
                    size.width * 0.76f,
                    y,
                    size.width * 0.92f,
                    y,
                )
            }
            drawPath(
                path,
                Color.White.copy(alpha = 0.78f),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
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
