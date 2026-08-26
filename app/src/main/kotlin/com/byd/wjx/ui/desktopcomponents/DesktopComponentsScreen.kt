package com.byd.wjx.ui.desktopcomponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.clustercard.ClusterCardSettingsContent
import com.byd.wjx.clustercard.ClusterCardSettingsViewModel
import com.byd.wjx.clustercard.ClusterCardSlot
import com.byd.wjx.desktopcard.DesktopQuickCardSettingsContent
import com.byd.wjx.desktopcard.DesktopQuickCardSettingsViewModel
import com.byd.wjx.ui.lyrics.LyricDisplayScreen
import com.byd.wjx.ui.lyrics.LyricDisplayViewModel
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun DesktopComponentsScreen(
    quickCardViewModel: DesktopQuickCardSettingsViewModel = hiltViewModel(),
    clusterCardViewModel: ClusterCardSettingsViewModel = hiltViewModel(),
    lyricViewModel: LyricDisplayViewModel = hiltViewModel(),
) {
    val quickCardState by quickCardViewModel.uiState.collectAsStateWithLifecycle()
    val clusterCardState by clusterCardViewModel.uiState(ClusterCardSlot.PRIMARY).collectAsStateWithLifecycle()
    val clusterCard2State by clusterCardViewModel.uiState(ClusterCardSlot.SECONDARY).collectAsStateWithLifecycle()
    val lyricState by lyricViewModel.uiState.collectAsStateWithLifecycle()
    var selectedPage by rememberSaveable { mutableStateOf(DesktopComponentPage.Lyrics) }

    LifecycleResumeEffect(Unit) {
        lyricViewModel.refresh()
        quickCardViewModel.refresh()
        clusterCardViewModel.refresh()
        onPauseOrDispose { }
    }

    val enabledMap = mapOf(
        DesktopComponentPage.Lyrics to lyricState.anyConfigured,
        DesktopComponentPage.QuickCard to quickCardState.config.enabled,
        DesktopComponentPage.ClusterCard to clusterCardState.config.enabled,
        DesktopComponentPage.ClusterCard2 to clusterCard2State.config.enabled,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 58.dp, end = 58.dp, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "桌面组件",
                    color = TextPrimary,
                    fontSize = 32.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "整个页面可以上下滑动，选择组件后在下方直接调节",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            DesktopComponentPreviewStage(
                selected = selectedPage,
                enabledMap = enabledMap,
                onSelected = { selectedPage = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )
        }

        when (selectedPage) {
            DesktopComponentPage.Lyrics -> LyricDisplayScreen(
                showHeader = false,
                scrollEnabled = false,
                viewModel = lyricViewModel,
                modifier = Modifier.fillMaxWidth(),
            )

            DesktopComponentPage.QuickCard -> DesktopComponentSettingsContainer {
                DesktopQuickCardSettingsContent(quickCardViewModel)
            }
            DesktopComponentPage.ClusterCard -> DesktopComponentSettingsContainer {
                ClusterCardSettingsContent(
                    slot = ClusterCardSlot.PRIMARY,
                    viewModel = clusterCardViewModel,
                )
            }
            DesktopComponentPage.ClusterCard2 -> DesktopComponentSettingsContainer {
                ClusterCardSettingsContent(
                    slot = ClusterCardSlot.SECONDARY,
                    viewModel = clusterCardViewModel,
                )
            }
        }
    }
}

@Composable
private fun DesktopComponentSettingsContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
    }
}
