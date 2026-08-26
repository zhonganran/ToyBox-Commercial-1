package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun LedLinkSettingsSection(
    activationAllowed: Boolean,
    faViewModel: IpixelFaSettingsViewModel = hiltViewModel(),
    mafuViewModel: MafuSettingsViewModel = hiltViewModel(),
) {
    val navigationViewModel: LedLinkSettingsViewModel = hiltViewModel()
    val selectedTab by navigationViewModel.selectedTab.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!activationAllowed) {
            LedLinkActivationRequiredCard()
            return@Column
        }

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = CardSurfaceElevated,
            contentColor = AccentBlue,
            divider = {},
        ) {
            LedProtocolSettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { navigationViewModel.selectTab(tab) },
                    text = {
                        Text(
                            tab.title,
                            fontWeight = if (selectedTab == tab) {
                                FontWeight.ExtraBold
                            } else {
                                FontWeight.Medium
                            },
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            LedProtocolSettingsTab.FA -> IpixelFaSettingsPane(faViewModel)
            LedProtocolSettingsTab.MAFU -> MafuSettingsPane(mafuViewModel)
        }
    }
}

@Composable
private fun LedLinkActivationRequiredCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, SocRed.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(SocRed.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = SocRed)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "LED 联动需要激活后使用",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "请先在授权激活页面完成永久激活",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

internal enum class LedProtocolSettingsTab(val title: String) {
    FA("FA 标准"),
    MAFU("马夫 LED"),
}
