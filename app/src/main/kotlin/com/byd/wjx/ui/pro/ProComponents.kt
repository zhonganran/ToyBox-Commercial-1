package com.byd.wjx.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.GlassStroke
import com.byd.wjx.ui.theme.GlassWhite
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun ProPageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (eyebrow.isNotBlank()) {
                Text(
                    eyebrow,
                    color = SceneBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                title,
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ProGlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.clip(shape).background(GlassWhite.copy(alpha = 0.80f), shape)
            .border(1.dp, GlassStroke.copy(alpha = 0.76f), shape),
    ) {
        content()
    }
}

@Composable
fun ProSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun ProStatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) AccentGreen else TextMuted
    Row(
        modifier = modifier.clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = if (active) 0.14f else 0.10f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
        Text(
            text,
            color = if (active) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
