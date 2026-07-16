package com.byd.wjx.ui.automation

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun AutomationMenuGroupHeader(text: String, color: Color) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        },
        onClick = {},
        enabled = false,
    )
}
