package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.led.ipixelfa.IpixelFaLinkageRule
import com.byd.wjx.led.ipixelfa.IpixelFaLinkageRuleFactory
import com.byd.wjx.led.ipixelfa.IpixelFaPreset
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import java.util.UUID

@Composable
internal fun LedLinkageRulesSection(
    rules: List<IpixelFaLinkageRule>,
    currentPreset: IpixelFaPreset?,
    onSave: (IpixelFaLinkageRule) -> Boolean,
    onDelete: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var editorState by remember {
        mutableStateOf<LinkageRuleEditorState<IpixelFaLinkageRule>?>(null)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("智能联动规则", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "当前画面：${currentPreset?.title ?: "无"}",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    currentPreset?.let {
                        Spacer(Modifier.size(6.dp))
                        IpixelFaPresetPreview(
                            preset = it,
                            modifier = Modifier.widthIn(max = 180.dp),
                        )
                    }
                }
                Button(
                    onClick = {
                        editorState = LinkageRuleEditorState(
                            rule = IpixelFaLinkageRuleFactory.newRule(UUID.randomUUID().toString()),
                            isNew = true,
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("新建规则")
                }
            }

            if (rules.isEmpty()) {
                Text("暂无联动规则", color = TextMuted, fontSize = 12.sp)
            } else {
                rules.forEachIndexed { index, rule ->
                    if (index > 0) HorizontalDivider(color = CardBorder.copy(alpha = 0.65f))
                    LedLinkageRuleRow(
                        title = rule.title,
                        detail = rule.steps.firstOrNull()?.preset?.title ?: "未选择画面",
                        enabled = rule.enabled,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        onEdit = { editorState = LinkageRuleEditorState(rule, false) },
                        onDelete = { onDelete(rule.id) },
                        onEnabledChange = { onEnabledChange(rule.id, it) },
                        onMoveUp = { onMove(rule.id, -1) },
                        onMoveDown = { onMove(rule.id, 1) },
                    )
                }
            }
        }
    }

    editorState?.let { editor ->
        IpixelFaLinkageRuleEditorDialog(
            initialRule = editor.rule,
            isNew = editor.isNew,
            onDismiss = { editorState = null },
            onSave = {
                if (onSave(it)) {
                    editorState = null
                    true
                } else {
                    false
                }
            },
        )
    }
}
