package com.byd.wjx.xiaozhi.ui

import androidx.lifecycle.ViewModel
import com.byd.wjx.xiaozhi.domain.XiaozhiSessionManager
import com.byd.wjx.xiaozhi.wake.XiaozhiWakeController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class XiaozhiViewModel @Inject constructor(
    private val sessionManager: XiaozhiSessionManager,
    private val wakeController: XiaozhiWakeController,
) : ViewModel() {
    val uiState = sessionManager.state
    val wakeState = wakeController.state

    fun toggleAutomaticConversation() = sessionManager.toggleAutomaticConversation()
    fun startConversationAfterMicrophonePermission() {
        wakeController.reconcile("xiaozhi_permission_granted")
        sessionManager.startAutomaticConversation()
    }
    fun abortConversation() = sessionManager.abortConversation()
    fun disconnect() = sessionManager.disconnect()
    fun clearMessages() = sessionManager.clearMessages()

    fun saveConfiguration(websocketUrl: String, token: String, otaUrl: String) =
        sessionManager.saveManualConfig(websocketUrl, token, otaUrl)

    fun provisionOfficial(otaUrl: String) = sessionManager.provisionOfficial(otaUrl)
    fun clearConfiguration() = sessionManager.clearConfiguration()

    fun updateWakeWord(enabled: Boolean, phrase: String): String? = wakeController.update(enabled, phrase)
    fun setWakeEnabled(enabled: Boolean): String? = wakeController.setEnabled(enabled)
    fun reconcileWakeService() = wakeController.reconcile("xiaozhi_settings")
}
