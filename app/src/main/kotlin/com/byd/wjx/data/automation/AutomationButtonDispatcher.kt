package com.byd.wjx.data.automation

import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class AutomationButtonDispatcher @Inject constructor(
    private val automationEngine: AutomationEngine,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun fire(buttonId: Int, onResult: (matched: Int) -> Unit) {
        applicationScope.launch {
            val matched = runCatching { automationEngine.onButtonPress(buttonId) }
                .onFailure {
                    WjxFileLogger.w(TAG, "button dispatch failed id=$buttonId: ${it.message}", it)
                }
                .getOrDefault(0)
            withContext(Dispatchers.Main.immediate) {
                onResult(matched)
            }
        }
    }

    private companion object {
        const val TAG = "AutomationButtonDispatcher"
    }
}
