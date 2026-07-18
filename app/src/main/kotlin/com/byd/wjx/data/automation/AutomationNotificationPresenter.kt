package com.byd.wjx.data.automation

/** Optional rich presentation for audible automation notifications. */
interface AutomationNotificationPresenter {
    suspend fun show(title: String, text: String): Boolean
}
