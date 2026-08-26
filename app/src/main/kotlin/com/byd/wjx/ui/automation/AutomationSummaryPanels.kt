package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AutomationSummaryStrip(
    total: Int,
    enabled: Int,
    logs: List<RuleLogEntity>,
    modifier: Modifier = Modifier,
) {
    val todayCutoff = System.currentTimeMillis() - DAY_MS
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
        AutomationStat(
            Icons.Outlined.CheckCircle,
            "已启用 $enabled 条",
            "共 $total 条规则",
            Modifier.weight(1f),
        )
        SummaryDivider()
        AutomationStat(
            Icons.Outlined.Bolt,
            "今日触发 $todayCount 次",
            "实时记录",
            Modifier.weight(1f),
        )
        SummaryDivider()
        AutomationStat(
            Icons.Outlined.Shield,
            "监听正常",
            "服务在线",
            Modifier.weight(1f),
        )
        SummaryDivider()
        AutomationStat(
            Icons.Outlined.Timer,
            "动作刷新 300ms",
            "实时响应",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun AutomationStat(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
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
            Text(
                "最近触发记录",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            Text("查看全部  ›", color = TextSecondary, fontSize = 11.sp)
        }
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("暂无触发记录", color = TextSecondary, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                logs.forEach { log ->
                    RecentAutomationLogItem(log = log, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentAutomationLogItem(log: RuleLogEntity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
            Icon(
                if (log.success) Icons.Outlined.CheckCircle else Icons.Outlined.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            log.ruleName,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (log.success) "执行成功" else "执行失败",
            color = if (log.success) Color(0xFF10AE5B) else Color(0xFFDE4F57),
            fontSize = 9.sp,
        )
    }
}

private const val DAY_MS = 24L * 60L * 60L * 1_000L
