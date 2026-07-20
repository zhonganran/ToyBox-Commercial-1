package com.byd.wjx.ui.desktopcomponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.lyrics.LyricDisplayScreen
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun DesktopComponentsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 58.dp, end = 58.dp, top = 18.dp, bottom = 12.dp),
        ) {
            Text(
                text = "歌词组件",
                color = TextPrimary,
                fontSize = 32.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "桌面歌词与仪表盘歌词共用一套显示设置",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        LyricDisplayScreen(
            showHeader = false,
            scrollEnabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
