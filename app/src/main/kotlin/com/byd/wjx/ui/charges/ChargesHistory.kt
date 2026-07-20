package com.byd.wjx.ui.charges

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ChargesMonthHeader(
    month: ChargesMonthGroup,
    expanded: Boolean,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(CardSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "▼" else "▶", color = AccentGreen, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(month.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${month.sessionCount}",
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp)
            )
            Text(
                stringResource(R.string.trips_kwh_decimal_value, month.totalKwh),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(88.dp)
            )
            Text(
                "%.2f %s".format(month.totalCost, currencySymbol),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
internal fun ChargesDayHeader(
    day: ChargesDayGroup,
    expanded: Boolean,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(CardSurface.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "▼" else "▶", color = AccentBlue, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(day.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${day.sessionCount}",
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp)
            )
            Text(
                stringResource(R.string.trips_kwh_decimal_value, day.totalKwh),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(88.dp)
            )
            Text(
                "%.2f %s".format(day.totalCost, currencySymbol),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
internal fun ChargesColumnHeaders(currencySymbol: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.charges_col_time), color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(100.dp))
        Text("电量", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(80.dp))
        Text(stringResource(R.string.charges_col_kwh), color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        Text(currencySymbol.lowercase(), color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
    }
    HorizontalDivider(
        color = CardBorder.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 36.dp, end = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChargeRow(
    charge: ChargeEntity,
    currencySymbol: String,
    onLongClick: () -> Unit,
) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    val startTime = timeFmt.format(Date(charge.startTs))

    val socText = when {
        charge.socStart != null && charge.socEnd != null -> "${charge.socStart}% → ${charge.socEnd}%"
        charge.socStart != null -> "${charge.socStart}% → —"
        else -> "—"
    }

    val kwh = charge.kwhCharged?.let { "%.1f".format(it) } ?: "—"
    val cost = charge.cost?.let { "%.2f".format(it) } ?: "—"

    val typeLabel = when (charge.gunState) {
        2 -> "慢充"
        3, 4 -> "快充"
        else -> formatChargeType(charge.type)
    }
    val typeColor = when (typeLabel) {
        "快充" -> AccentOrange
        else -> AccentBlue
    }

    Column(
        modifier = Modifier.combinedClickable(
            onClick = { /* no-op for regular click */ },
            onLongClick = onLongClick,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge + time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(CardSurfaceElevated, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        typeLabel,
                        color = typeColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    startTime,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                socText,
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(80.dp)
            )
            Text(
                kwh,
                color = AccentGreen,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
            Text(
                cost,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
        }
        HorizontalDivider(
            color = CardBorder.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 36.dp, end = 12.dp)
        )
    }
}
