package com.byd.wjx.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AdbRescueSettingsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guardToggleRendersDisabledState() {
        composeRule.setContent {
            MaterialTheme {
                AdbGuardToggleCard(
                    enabled = false,
                    controlsEnabled = true,
                    onEnabledChange = {},
                )
            }
        }

        composeRule.onNodeWithText("开机自动守护").assertIsDisplayed()
        composeRule.onNodeWithText("当前不会在后台自动开启 ADB").assertIsDisplayed()
    }
}
