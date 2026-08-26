package com.byd.wjx.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.domain.insight.LocalInsightMetric
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.GlassStroke
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun LocalInsightPanel(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val insight = state.localInsight
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier.clip(shape).background(CardSurface.copy(alpha = 0.90f))
            .border(1.dp, GlassStroke, shape)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        val compact = maxWidth < 900.dp
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            InsightHeader(state)
            Text(
                text = if (state.localInsightLoading) {
                    "正在分析近期行程、充电和电池数据"
                } else {
                    insight.summary.ifBlank { "本地数据积累中" }
                },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                state.localInsightLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("所有分析均在车机本地完成，不上传车辆数据", color = TextSecondary, fontSize = 11.sp)
                }

                compact -> CompactInsightContent(insight.metrics, insight.recommendations)
                else -> WideInsightContent(insight.metrics, insight.recommendations)
            }
        }
    }
}

@Composable
private fun InsightHeader(state: DashboardUiState) {
    val insight = state.localInsight
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Insights, contentDescription = null, tint = SceneBlue, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (state.localInsightLoading) "本地洞察" else "本地洞察 · ${insight.title}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        val statusColor = if (insight.hasEnoughData) AccentGreen else AccentBlue
        Text(
            text = when {
                state.localInsightLoading -> "分析中"
                insight.hasEnoughData -> "本地分析完成"
                else -> "数据积累中"
            },
            color = statusColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CompactInsightContent(
    metrics: List<LocalInsightMetric>,
    recommendations: List<String>,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.take(6).chunked(3).forEach { rowMetrics ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowMetrics.forEach { metric -> LocalInsightMetricItem(metric, Modifier.weight(1f)) }
                repeat(3 - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        recommendations.take(2).forEach { InsightRecommendation(it) }
    }
}

@Composable
private fun WideInsightContent(
    metrics: List<LocalInsightMetric>,
    recommendations: List<String>,
) {
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
            metrics.take(6).forEach { metric ->
                LocalInsightMetricItem(metric, Modifier.weight(1f))
            }
        }
        if (recommendations.isNotEmpty()) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(TextPrimary.copy(alpha = 0.10f)))
            Column(
                modifier = Modifier.width(310.dp).padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                recommendations.take(2).forEach { InsightRecommendation(it) }
            }
        }
    }
}

@Composable
private fun LocalInsightMetricItem(metric: LocalInsightMetric, modifier: Modifier = Modifier) {
    val color = insightToneColor(metric.tone)
    Column(modifier = modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                metric.label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Text(
            metric.value,
            color = TextPrimary,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            metric.detail,
            color = TextSecondary,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InsightRecommendation(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(AccentGreen.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            color = TextPrimary,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
