package com.byd.wjx.data.automation

/** UI boundary for rules that require explicit user confirmation before execution. */
interface AutomationConfirmationPresenter {
    fun show(
        ruleName: String,
        actionsSummary: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ): Boolean
}
