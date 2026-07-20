package com.byd.wjx.ui.charges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.ChartGrid
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun ChargesStatsPanel(
    periodSummary: ChargeSummary,
    currencySymbol: String,
    bmsLifetimeKm: Double?,
    bmsLifetimeKwh: Double?,
    nominalCapacityKwh: Double,
    sohSeries: List<Float>,
    cellDeltaSeries: List<Float>,
    batTempSeries: List<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel(stringResource(R.string.charges_stats_period_label))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            StatRow(stringResource(R.string.charges_stats_sessions), "${periodSummary.sessionCount}", TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow(stringResource(R.string.charges_col_kwh), "%.1f".format(periodSummary.totalKwh), AccentGreen)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow(stringResource(R.string.charges_stats_cost), "%.2f %s".format(periodSummary.totalCost, currencySymbol), TextPrimary)
        }

        SectionLabel(stringResource(R.string.charges_stats_lifetime_label))
        if (bmsLifetimeKwh != null) {
            val equiv = if (nominalCapacityKwh > 0) bmsLifetimeKwh / nominalCapacityKwh else 0.0
            val avgPer100 = if (bmsLifetimeKm != null && bmsLifetimeKm > 0)
                bmsLifetimeKwh / bmsLifetimeKm * 100.0 else null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                StatRow(stringResource(R.string.charges_stats_pumped_total), stringResource(R.string.charges_kwh_value, bmsLifetimeKwh), AccentGreen)
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(stringResource(R.string.charges_stats_equiv_cycles), "%.1f".format(equiv), TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(
                    stringResource(R.string.charges_stats_bms_mileage),
                    bmsLifetimeKm?.let { stringResource(R.string.trips_km_decimal_value, it) } ?: "-",
                    TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(
                    stringResource(R.string.charges_stats_per100km),
                    avgPer100?.let { stringResource(R.string.trips_kwh_decimal_value, it) } ?: "-",
                    AccentBlue
                )
            }

            if (sohSeries.size >= 2) {
                MiniLineChart(
                    series = sohSeries,
                    title = "健康度（%）",
                    lineColor = AccentGreen,
                    valueFormat = { "%.0f".format(it) }
                )
            }
        } else {
            PlaceholderBlock(stringResource(R.string.charges_lifetime_waiting))
        }

        SectionLabel(stringResource(R.string.charges_stats_dynamics_label))
        if (cellDeltaSeries.size >= 3) {
            MiniLineChart(
                series = cellDeltaSeries,
                title = stringResource(R.string.charges_chart_cell_delta_title),
                lineColor = AccentBlue,
                valueFormat = { "%.3f".format(it) }
            )
        } else {
            ChartPlaceholder(stringResource(R.string.charges_chart_cell_delta_placeholder, cellDeltaSeries.size))
        }
        if (batTempSeries.size >= 3) {
            MiniLineChart(
                series = batTempSeries,
                title = stringResource(R.string.charges_chart_bat_temp_title),
                lineColor = AccentOrange,
                valueFormat = { "%.1f".format(it) }
            )
        } else {
            ChartPlaceholder(stringResource(R.string.charges_chart_bat_temp_placeholder, batTempSeries.size))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun PlaceholderBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(text, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ChartPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MiniLineChart(
    series: List<Float>,
    title: String,
    lineColor: Color,
    valueFormat: (Float) -> String = { "%.2f".format(it) }
) {
    val minVal = series.min()
    val maxVal = series.max()
    val isFlat = maxVal - minVal < 0.005f
    val latest = series.last()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        // Title + latest value on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Text(
                valueFormat(latest),
                color = lineColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-axis labels: max on top, min on bottom. For a flat series we
            // collapse to one row centered vertically next to the line.
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .height(60.dp),
                verticalArrangement = if (isFlat) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                if (isFlat) {
                    Text(
                        valueFormat(latest),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        valueFormat(maxVal),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueFormat(minVal),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            val chartGridColor = ChartGrid
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val w = size.width
                val h = size.height
                val pad = 4.dp.toPx()

                // Top + bottom grid lines so the chart has a clear frame.
                drawLine(
                    color = chartGridColor,
                    start = Offset(pad, pad),
                    end = Offset(w - pad, pad),
                    strokeWidth = 0.5.dp.toPx()
                )
                drawLine(
                    color = chartGridColor,
                    start = Offset(pad, h - pad),
                    end = Offset(w - pad, h - pad),
                    strokeWidth = 0.5.dp.toPx()
                )

                val path = Path()
                series.forEachIndexed { index, value ->
                    val x = pad + (index.toFloat() / (series.size - 1)) * (w - pad * 2)
                    val y = if (isFlat) {
                        // Constant series: draw line at vertical center instead
                        // of pinning it to the bottom (range≈0 collapses y to h-pad).
                        h / 2f
                    } else {
                        val range = maxVal - minVal
                        pad + (1f - (value - minVal) / range) * (h - pad * 2)
                    }
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
internal fun ChargesChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(44.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected
        )
    )
}
