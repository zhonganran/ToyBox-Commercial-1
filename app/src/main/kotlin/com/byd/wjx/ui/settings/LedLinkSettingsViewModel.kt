package com.byd.wjx.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
internal class LedLinkSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val selectedTab: StateFlow<LedProtocolSettingsTab> = savedStateHandle.getStateFlow(
        SELECTED_TAB_KEY,
        LedProtocolSettingsTab.FA,
    )

    fun selectTab(tab: LedProtocolSettingsTab) {
        savedStateHandle[SELECTED_TAB_KEY] = tab
    }

    private companion object {
        const val SELECTED_TAB_KEY = "selected_led_protocol_tab"
    }
}
