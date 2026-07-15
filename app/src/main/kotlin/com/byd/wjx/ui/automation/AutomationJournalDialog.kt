package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun JournalDialog(logs: List<RuleLogEntity>, onDismiss: () -> Unit) {
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


internal fun formatRelativeTime(ts: Long, context: android.content.Context): String {
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
