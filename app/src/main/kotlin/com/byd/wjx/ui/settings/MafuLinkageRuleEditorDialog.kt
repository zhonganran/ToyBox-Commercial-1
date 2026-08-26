package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.mafu.MafuLinkageRule
import com.byd.wjx.led.mafu.MafuLinkageStep
import com.byd.wjx.led.mafu.MafuRemoteModel
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun MafuLinkageRuleEditorDialog(
    initialRule: MafuLinkageRule,
    remoteModel: MafuRemoteModel,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (MafuLinkageRule) -> Boolean,
) {
    var draft by remember(initialRule.id, remoteModel) {
        mutableStateOf(initialRule.normalized(remoteModel))
    }
    var saveError by remember(initialRule.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CardSurfaceElevated,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (isNew) "新建联动规则" else "编辑联动规则",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LedLinkageChoiceField(
                        label = "触发类型",
                        selected = draft.triggerType,
                        options = LedLinkageTriggerType.entries.map { it to it.title },
                    ) { type ->
                        draft = draft.copy(
                            triggerType = type,
                            conditionValue = LedLinkageCatalog.options(type).first().value,
                        )
                    }
                    LedLinkageChoiceField(
                        label = "触发条件",
                        selected = draft.conditionValue,
                        options = LedLinkageCatalog.options(draft.triggerType).map { it.value to it.title },
                    ) { draft = draft.copy(conditionValue = it) }

                    HorizontalDivider(color = CardBorder)
                    Text(
                        "联动节目（条件满足时持续播放）",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val step = draft.steps.firstOrNull() ?: MafuLinkageStep(remoteModel.supportedKeys.first())
                    MafuStepEditor(
                        step = step,
                        remoteModel = remoteModel,
                        onChange = { draft = draft.copy(steps = listOf(it)) },
                    )
                }

                RuleEditorActions(
                    saveError = saveError,
                    onDismiss = onDismiss,
                    onSave = { saveError = !onSave(draft.normalized(remoteModel)) },
                )
            }
        }
    }
}

@Composable
private fun MafuStepEditor(
    step: MafuLinkageStep,
    remoteModel: MafuRemoteModel,
    onChange: (MafuLinkageStep) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("选择要持续播放的遥控节目", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        LedLinkageChoiceField(
            label = "遥控按键",
            selected = step.remoteKey,
            options = remoteModel.supportedKeys.map {
                it to "${it.title} · 节目 ${remoteModel.programId(it)}"
            },
            showLabelAbove = true,
        ) { onChange(step.copy(remoteKey = it)) }
    }
}
