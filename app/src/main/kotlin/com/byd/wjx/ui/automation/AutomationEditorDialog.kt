package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun EditorDialog(
    editing: EditingRule,
    editorError: String?,
    onUpdate: (EditingRule.() -> EditingRule) -> Unit,
    onSave: () -> Unit,
    onTestAction: (ActionDef) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.88f)
                .background(CardSurface.copy(alpha = 0.98f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(24.dp)),
        ) {
            AutomationEditorTitleBar(editing = editing, onDismiss = onDismiss)
            HorizontalDivider(color = CardBorder)
            Row(modifier = Modifier.weight(1f)) {
                AutomationEditorTriggerColumn(
                    editing = editing,
                    onUpdate = onUpdate,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(CardBorder),
                )
                AutomationEditorActionColumn(
                    editing = editing,
                    onUpdate = onUpdate,
                    onTestAction = onTestAction,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = CardBorder)
            AutomationEditorFooter(
                editing = editing,
                editorError = editorError,
                onDismiss = onDismiss,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun AutomationEditorTitleBar(editing: EditingRule, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (editing.isNew) {
                stringResource(R.string.automation_editor_new_rule_title)
            } else {
                editing.name
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = TextMuted)
        }
    }
}

@Composable
private fun AutomationEditorFooter(
    editing: EditingRule,
    editorError: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        editorError?.let { error ->
            Text(
                text = error,
                color = Color(0xFFEF4444),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
            ) {
                Text(stringResource(R.string.automation_cancel_button))
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = AccentContent,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp),
                enabled = editing.hasMinimumSaveFields(),
            ) {
                Text(
                    stringResource(R.string.automation_save_button),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
